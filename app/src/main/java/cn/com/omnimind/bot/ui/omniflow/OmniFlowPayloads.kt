package cn.com.omnimind.bot.ui.omniflow

import cn.com.omnimind.nativeui.omniflow.FunctionParameter
import cn.com.omnimind.nativeui.omniflow.FunctionStep
import cn.com.omnimind.nativeui.omniflow.OmniFlowFunction
import cn.com.omnimind.nativeui.omniflow.RunLogDetail
import cn.com.omnimind.nativeui.omniflow.RunLogScreenState
import cn.com.omnimind.nativeui.omniflow.RunLogStep
import cn.com.omnimind.nativeui.omniflow.RunLogSummary
import cn.com.omnimind.nativeui.omniflow.ScreenPoint
import cn.com.omnimind.nativeui.omniflow.TokenUsage
import cn.com.omnimind.nativeui.omniflow.runStatus
import cn.com.omnimind.nativeui.omniflow.stepAction
import cn.com.omnimind.nativeui.omniflow.stepSource
import cn.com.omnimind.nativeui.omniflow.stepTarget
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Maps the OmniFlow tool payloads (`list_functions`, `list_run_logs`,
 * `get_run_log`, `get_run_log_state`) into the native-ui types, with the
 * fallbacks of `run_log_metrics.dart` and the Dart sheets.
 */
internal object OmniFlowPayloads {
    fun functions(payload: Map<String, Any?>): List<OmniFlowFunction> = maps(payload["functions"]).mapNotNull(::function)

    fun runLogs(payload: Map<String, Any?>): List<RunLogSummary> = maps(payload["runs"]).map(::summary)

    /** A Function from `get_function` (nested under `function` or `spec`) or a list row. */
    fun functionDetail(payload: Map<String, Any?>): OmniFlowFunction? {
        val nested = listOf("function", "spec").map { map(payload[it]) }.firstOrNull { it.isNotEmpty() }
        return function(nested ?: payload)
    }

    fun function(raw: Map<String, Any?>): OmniFlowFunction? {
        val id = text(raw["function_id"])
        if (id.isEmpty() && raw["steps"] == null && raw["input_schema"] == null) return null
        val schema = map(raw["input_schema"])
        val required = (schema["required"] as? List<*>)?.map { it.toString() }?.toSet().orEmpty()
        return OmniFlowFunction(
            id = id,
            name = text(raw["name"]),
            description = text(raw["description"]),
            parameters = map(schema["properties"]).map { (name, value) ->
                val property = map(value)
                FunctionParameter(name, text(property["type"]), text(property["description"]), name in required)
            },
            steps = maps(raw["steps"]).map { step ->
                val action = map(step["action"])
                val tool = text(action["tool"]).ifEmpty { text(action["type"]) }
                val args = map(action["args"])
                FunctionStep(stepAction(tool), tool, if (args.isEmpty()) "" else json(args))
            },
            agentVisible = raw["agent_visible"] != false,
            sourceRunId = text(raw["source_run_id"]),
        )
    }

    fun summary(raw: Map<String, Any?>): RunLogSummary {
        val diagnostics = map(raw["diagnostics"])
        val usage = map(diagnostics["token_usage"]).ifEmpty { map(raw["token_usage"]) }
        val startedAt = long(raw["started_at_ms"])
        val finishedAt = long(raw["finished_at_ms"])
        val byCall = maps(diagnostics["token_usage_by_call"] ?: raw["token_usage_by_call"])
        val byStep = maps(diagnostics["token_usage_by_step"] ?: raw["token_usage_by_step"])
        val resolvedModels = (usage["resolved_models"] ?: diagnostics["resolved_models"]) as? List<*>
        val model = listOf(usage["resolved_model"], usage["model"], diagnostics["resolved_model"], diagnostics["model"],
            resolvedModels?.mapNotNull { text(it).ifEmpty { null } }?.joinToString(", "))
            .map(::text).firstOrNull { it.isNotEmpty() }
        return RunLogSummary(
            runId = text(raw["run_id"]),
            goal = text(raw["goal"]),
            status = runStatus(text(raw["status"]), raw["success"] as? Boolean),
            startedAtMillis = startedAt,
            durationMs = long(diagnostics["duration_ms"] ?: raw["duration_ms"])
                ?: if (startedAt != null && finishedAt != null) (finishedAt - startedAt).coerceAtLeast(0) else null,
            tokens = tokens(usage),
            model = model,
            callCount = int(usage["call_count"]) ?: byCall.size.takeIf { it > 0 }
                ?: int(usage["step_count"]) ?: byStep.size.takeIf { it > 0 },
            functionId = text(raw["function_id"]).ifEmpty { null },
        )
    }

    fun detail(raw: Map<String, Any?>): RunLogDetail {
        val diagnostics = map(raw["diagnostics"])
        val byStep = maps(diagnostics["token_usage_by_step"] ?: raw["token_usage_by_step"])
        return RunLogDetail(summary(raw), maps(raw["steps"]).mapIndexed { index, step -> step(step, index, byStep) })
    }

    private fun step(step: Map<String, Any?>, index: Int, byStep: List<Map<String, Any?>>): RunLogStep {
        val action = map(step["action"])
        val result = map(step["result"])
        val metadata = map(step["metadata"])
        val tool = text(action["tool"]).ifEmpty { text(action["type"]) }
        val args = map(action["args"])
        // Official RunLogs keep state ids in observation.auxiliaries; older ones named them directly.
        val stateId = listOf(
            step["before_state_id"], map(map(step["observation"])["auxiliaries"])["state_id"],
            step["after_state_id"], map(map(step["next_observation"])["auxiliaries"])["state_id"],
        ).map(::text).firstOrNull { it.isNotEmpty() }
        val stepIndex = int(step["step_index"])
        val usage = map(metadata["token_usage"]).ifEmpty {
            byStep.firstOrNull { stepIndex != null && int(it["step_index"]) == stepIndex }?.let { map(it["token_usage"]) }.orEmpty()
        }
        val x = text(args["x"]).toFloatOrNull()
        val y = text(args["y"]).toFloatOrNull()
        return RunLogStep(
            index = index,
            action = stepAction(tool),
            tool = tool,
            target = stepTarget(tool, args),
            source = stepSource(text(metadata["source"])),
            summary = listOf(metadata["summary"], result["message"]).map(::text).firstOrNull { it.isNotEmpty() }.orEmpty(),
            thinking = text(metadata["thinking"]),
            durationMs = long(metadata["duration_ms"] ?: result["duration_ms"]),
            success = result["success"] != false,
            tokens = tokens(usage).takeIf { it.hasUsage },
            stateId = stateId,
            point = if (x != null && y != null) ScreenPoint(x, y) else null,
            actionJson = json(action, indent = true),
        )
    }

    fun screenState(raw: Map<String, Any?>): RunLogScreenState = RunLogScreenState(
        stateId = text(raw["state_id"]),
        screenshotPath = text(raw["screenshot_path"]),
        app = listOf(raw["package_name"], raw["activity_name"]).map(::text).filter { it.isNotEmpty() }.joinToString(" "),
        xml = raw["xml"]?.toString().orEmpty(),
    )

    /** Total tokens fall back to prompt + completion (Dart `RunLogTokenUsage.totalTokens`). */
    private fun tokens(usage: Map<String, Any?>): TokenUsage {
        val prompt = int(usage["prompt_tokens"] ?: usage["promptTokens"])
        val completion = int(usage["completion_tokens"] ?: usage["completionTokens"])
        val total = int(usage["total_tokens"] ?: usage["totalTokens"])
            ?: if (prompt == null && completion == null) null else (prompt ?: 0) + (completion ?: 0)
        return TokenUsage(prompt, completion, total, int(usage["cached_tokens"] ?: usage["cachedTokens"]))
    }

    /** `success: false` with its message (or code), else null (Dart `_runAction`). */
    fun failure(payload: Map<String, Any?>): String? = if (payload["success"] != false) null else
        text(payload["error_message"]).ifEmpty { text(payload["error_code"]) }.ifEmpty { "success=false" }

    fun map(value: Any?): Map<String, Any?> =
        (value as? Map<*, *>)?.entries?.associate { (key, nested) -> key.toString() to nested }.orEmpty()

    fun maps(value: Any?): List<Map<String, Any?>> = (value as? List<*>)?.filterIsInstance<Map<*, *>>()?.map(::map).orEmpty()

    fun text(value: Any?): String = value?.toString()?.trim().orEmpty()

    fun int(value: Any?): Int? = when (value) {
        is Int -> value
        is Number -> value.toDouble().takeIf { it.isFinite() }?.toInt()
        else -> value?.toString()?.trim()?.toIntOrNull()
    }

    private fun long(value: Any?): Long? = when (value) {
        is Long -> value
        is Number -> value.toDouble().takeIf { it.isFinite() }?.toLong()
        else -> value?.toString()?.trim()?.toLongOrNull()
    }

    fun json(value: Map<String, Any?>, indent: Boolean = false): String =
        (if (indent) PrettyJson else Json).encodeToString(JsonElement.serializer(), element(value))

    private fun element(value: Any?): JsonElement = when (value) {
        null -> JsonNull
        is Map<*, *> -> JsonObject(map(value).mapValues { (_, nested) -> element(nested) })
        is List<*> -> JsonArray(value.map(::element))
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }

    private val PrettyJson = Json { prettyPrint = true }
}
