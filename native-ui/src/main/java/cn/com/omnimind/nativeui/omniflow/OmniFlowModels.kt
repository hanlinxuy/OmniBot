package cn.com.omnimind.nativeui.omniflow

import androidx.compose.runtime.Immutable
import cn.com.omnimind.nativeui.JsonValues
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Presentation types for the OmniFlow execution center and run logs (batch
 * 5n, replacing `omniflow_execution_center_page.dart`, `run_log_detail_page.dart`
 * and their sheets). The app module maps the OmniFlow tool payloads into them.
 */
enum class OmniFlowTab { Functions, RunLogs }

enum class RunStatus { Succeeded, Running, Failed, Cancelled, Unknown }

/** One RunLog's `status` word, with `success: false` counting as a failure (Dart `_RunLogStatus`). */
fun runStatus(status: String?, success: Boolean? = null): RunStatus = when (status?.trim()?.lowercase()) {
    "running", "pending" -> RunStatus.Running
    "cancelled", "canceled" -> RunStatus.Cancelled
    "failed", "error" -> RunStatus.Failed
    "success", "succeeded", "completed" -> if (success == false) RunStatus.Failed else RunStatus.Succeeded
    else -> if (success == false) RunStatus.Failed else RunStatus.Unknown
}

@Immutable
data class TokenUsage(val prompt: Int? = null, val completion: Int? = null, val total: Int? = null, val cached: Int? = null) {
    val hasUsage: Boolean get() = prompt != null || completion != null || total != null || cached != null
}

@Immutable
data class RunLogSummary(
    val runId: String,
    val goal: String,
    val status: RunStatus,
    val startedAtMillis: Long? = null,
    val durationMs: Long? = null,
    val tokens: TokenUsage = TokenUsage(),
    val model: String? = null,
    val callCount: Int? = null,
    /** The Function this run replayed, if any. */
    val functionId: String? = null,
)

/** The GUI actions OmniFlow records; anything else shows its raw tool name. */
enum class StepAction { OpenApp, Tap, LongPress, EnterText, Swipe, PressKey, Wait, Other }

fun stepAction(tool: String): StepAction = when (tool) {
    "open_app" -> StepAction.OpenApp
    "click" -> StepAction.Tap
    "long_press" -> StepAction.LongPress
    "input_text", "type" -> StepAction.EnterText
    "swipe" -> StepAction.Swipe
    "press_key" -> StepAction.PressKey
    "wait" -> StepAction.Wait
    else -> StepAction.Other
}

/** What a step acted on: the package, the coordinates, the text typed (Dart `_actionTitle`). */
fun stepTarget(tool: String, args: Map<String, Any?>): String {
    fun text(key: String) = args[key]?.toString()?.trim().orEmpty()
    fun coordinates(): String {
        val x = text("x")
        val y = text("y")
        if (x.isNotEmpty() && y.isNotEmpty()) return "$x, $y"
        val swipe = listOf("x1", "y1", "x2", "y2").map(::text)
        return if (swipe.all { it.isNotEmpty() }) "${swipe[0]}, ${swipe[1]} → ${swipe[2]}, ${swipe[3]}" else ""
    }
    return when (stepAction(tool)) {
        StepAction.OpenApp -> text("package_name")
        StepAction.Tap, StepAction.LongPress -> coordinates()
        StepAction.EnterText -> text("text")
        StepAction.Swipe -> text("direction").ifEmpty { coordinates() }
        StepAction.PressKey -> text("key")
        StepAction.Wait -> args["duration_ms"]?.let { "$it ms" }.orEmpty()
        StepAction.Other -> ""
    }
}

enum class StepSource { Automatic, Human, Function, None }

fun stepSource(source: String?): StepSource = when (source?.trim()?.lowercase()) {
    "vlm" -> StepSource.Automatic
    "human_trajectory", "manual_recording" -> StepSource.Human
    "omniflow_replay", "function" -> StepSource.Function
    else -> StepSource.None
}

/** A screen position an action touched, in the screenshot's own pixels. */
@Immutable
data class ScreenPoint(val x: Float, val y: Float)

@Immutable
data class RunLogStep(
    val index: Int,
    val action: StepAction,
    val tool: String,
    val target: String,
    val source: StepSource,
    val summary: String,
    val thinking: String,
    val durationMs: Long?,
    val success: Boolean,
    val tokens: TokenUsage?,
    /** The screen before the action (else after); null when the run kept none. */
    val stateId: String?,
    val point: ScreenPoint?,
    val actionJson: String,
)

@Immutable
data class RunLogDetail(val summary: RunLogSummary, val steps: List<RunLogStep>)

/** A recorded screen: its screenshot and view tree. */
@Immutable
data class RunLogScreenState(val stateId: String, val screenshotPath: String, val app: String, val xml: String)

@Immutable
data class FunctionParameter(val name: String, val type: String, val description: String, val required: Boolean)

@Immutable
data class FunctionStep(val action: StepAction, val tool: String, val argsJson: String)

@Immutable
data class OmniFlowFunction(
    val id: String,
    val name: String,
    val description: String,
    val parameters: List<FunctionParameter>,
    val steps: List<FunctionStep>,
    val agentVisible: Boolean,
    /** The RunLog it was compiled from; enhancement needs it. */
    val sourceRunId: String,
)

/**
 * The Function [run] belongs to: the one it replayed, or the one compiled
 * from it (Dart `_linkedFunction`).
 */
fun linkedFunction(run: RunLogSummary, functions: List<OmniFlowFunction>): OmniFlowFunction? =
    functions.firstOrNull { function ->
        (!run.functionId.isNullOrEmpty() && function.id == run.functionId) ||
            (run.runId.isNotEmpty() && function.sourceRunId == run.runId)
    }

// ---- Run arguments -------------------------------------------------------

/** Why one argument was refused; the dialog shows it under the field. */
enum class ArgumentError { Required, NotANumber, NotAnInteger, NotABoolean, InvalidJson }

sealed interface ParsedArguments {
    data class Valid(val values: Map<String, Any?>) : ParsedArguments
    data class Invalid(val errors: Map<String, ArgumentError>) : ParsedArguments
}

/**
 * Turns the typed text into the schema's types. Dart ignored the Run tap
 * when a required field was empty (with no hint why), threw on malformed
 * JSON for objects and arrays, and read any boolean other than "true" as
 * false; each of those now names the field that needs fixing.
 */
fun parseFunctionArguments(parameters: List<FunctionParameter>, input: Map<String, String>): ParsedArguments {
    val values = LinkedHashMap<String, Any?>()
    val errors = LinkedHashMap<String, ArgumentError>()
    for (parameter in parameters) {
        val raw = input[parameter.name]?.trim().orEmpty()
        if (raw.isEmpty()) {
            if (parameter.required) errors[parameter.name] = ArgumentError.Required
            continue
        }
        when (val parsed = parseArgument(raw, parameter.type)) {
            is ArgumentError -> errors[parameter.name] = parsed
            else -> values[parameter.name] = parsed
        }
    }
    return if (errors.isEmpty()) ParsedArguments.Valid(values) else ParsedArguments.Invalid(errors)
}

private fun parseArgument(raw: String, type: String): Any? = when (type.trim().lowercase()) {
    "integer" -> raw.toLongOrNull() ?: ArgumentError.NotAnInteger
    "number" -> raw.toLongOrNull() ?: raw.toDoubleOrNull()?.takeIf { it.isFinite() } ?: ArgumentError.NotANumber
    "boolean" -> when (raw.lowercase()) {
        "true", "yes", "1" -> true
        "false", "no", "0" -> false
        else -> ArgumentError.NotABoolean
    }
    "object" -> runCatching { JsonValues.parseObject(raw) }.getOrElse { ArgumentError.InvalidJson }
    "array" -> runCatching { JsonValues.parseArray(raw) }.getOrElse { ArgumentError.InvalidJson }
    else -> raw
}

// ---- Paging --------------------------------------------------------------

/** One list page's continuation (Dart `_hasMore` / `_nextOffset`). */
@Immutable
data class PageCursor(val nextOffset: Int, val hasMore: Boolean)

fun pageCursor(hasMore: Boolean?, totalCount: Int?, nextOffset: Int?, offset: Int, fetched: Int, loaded: Int, pageSize: Int): PageCursor =
    PageCursor(
        nextOffset = nextOffset?.takeIf { it >= 0 } ?: (offset + fetched),
        hasMore = hasMore ?: totalCount?.let { loaded < it } ?: (fetched >= pageSize),
    )

/** Appends [next] after [current], dropping ids already shown (a page shifted by a new run). */
fun <T> mergeById(current: List<T>, next: List<T>, id: (T) -> String): List<T> {
    val seen = HashSet<String>()
    return (current + next).filter { item -> id(item).let { it.isEmpty() || seen.add(it) } }
}

// ---- Formatting ----------------------------------------------------------

fun formatRunDuration(durationMs: Long): String = when {
    durationMs < 1_000 -> "$durationMs ms"
    durationMs < 60_000 -> "${trimFixed(durationMs / 1000.0)} s"
    else -> "${durationMs / 60_000} m ${trimFixed((durationMs % 60_000) / 1000.0)} s"
}

fun formatRunTokens(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format(Locale.ROOT, "%.2fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.ROOT, "%.2fk", tokens / 1_000.0)
    else -> tokens.toString()
}

private val TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT)

fun formatRunTimestamp(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
    TIMESTAMP.format(Instant.ofEpochMilli(millis).atZone(zone))

private fun trimFixed(value: Double): String =
    String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')

// ---- Screenshots ---------------------------------------------------------

/**
 * Scales a recorded tap from screen pixels to the shown screenshot. Dart put
 * the marker at the raw device coordinates over a scaled-down image, so it
 * landed far from the real tap on any phone wider than the sheet.
 */
fun scaledPoint(point: ScreenPoint, imageWidth: Int, imageHeight: Int, shownWidth: Float, shownHeight: Float): ScreenPoint? {
    if (imageWidth <= 0 || imageHeight <= 0) return null
    if (point.x !in 0f..imageWidth.toFloat() || point.y !in 0f..imageHeight.toFloat()) return null
    return ScreenPoint(point.x * shownWidth / imageWidth, point.y * shownHeight / imageHeight)
}
