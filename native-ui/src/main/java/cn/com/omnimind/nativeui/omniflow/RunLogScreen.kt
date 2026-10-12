package cn.com.omnimind.nativeui.omniflow

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniEmptyState
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.components.OmniPage
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

/**
 * One RunLog (batch 5n): its outcome and metrics, the step timeline, each
 * step's evidence and the recorded screen it acted on.
 */
@Composable
fun RunLogScreen(state: RunLogPageState, actions: RunLogActions, onBack: () -> Unit) {
    OmniPage(
        stringResource(R.string.omni_rl_title), onBack,
        notice = state.notice?.let { omniFlowNoticeText(it) },
        onNoticeShown = actions.dismissNotice,
        actions = {
            OmniIconButton(R.drawable.omni_refresh_cw, stringResource(R.string.omni_of_refresh), actions.refresh,
                size = 20.dp, enabled = !state.loading)
        },
        bottomBar = { if (state.detail != null) FunctionBar(state, actions) },
    ) { insets ->
        val detail = state.detail
        when {
            detail == null && state.loading -> Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            detail == null -> OmniEmptyState(R.drawable.omni_triangle_alert, stringResource(R.string.omni_of_load_failed),
                Modifier.padding(insets), message = state.error, actionLabel = stringResource(R.string.omni_retry),
                onAction = actions.refresh)
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 24.dp),
            ) {
                item(key = "overview") { Overview(detail) }
                state.linkedFunction?.let { function ->
                    item(key = "linked") { LinkedFunction(function, actions.openFunction) }
                }
                item(key = "gap") { Spacer(Modifier.height(16.dp)) }
                if (detail.steps.isEmpty()) {
                    item(key = "empty") {
                        Text(stringResource(R.string.omni_rl_no_steps), fontSize = 13.sp,
                            color = LocalOmniPalette.current.secondaryText)
                    }
                }
                itemsIndexed(detail.steps, key = { _, step -> "step-${step.index}" }) { position, step ->
                    StepCard(step, isLast = position == detail.steps.lastIndex) { actions.openStep(step.index) }
                }
            }
        }
        StepSheet(state, actions)
        ScreenSheet(state, actions)
    }
}

@Composable
private fun FunctionBar(state: RunLogPageState, actions: RunLogActions) {
    val linked = state.linkedFunction != null
    // Only a finished, successful run compiles into a Function.
    if (!linked && state.detail?.summary?.status != RunStatus.Succeeded) return
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp, 8.dp, 16.dp, 12.dp)) {
        Button(if (linked) actions.openFunction else actions.register, Modifier.fillMaxWidth(),
            enabled = !state.registering, colors = ButtonDefaults.buttonColorsPrimary()) {
            if (state.registering) {
                CircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(stringResource(when {
                state.registering -> R.string.omni_of_registering
                linked -> R.string.omni_of_view_function
                else -> R.string.omni_of_register
            }), color = Color.White)
        }
    }
}

@Composable
private fun Overview(detail: RunLogDetail) {
    val palette = LocalOmniPalette.current
    val run = detail.summary
    val color = runStatusColor(run.status)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = if (palette.dark) .12f else .06f))
            .border(1.dp, color.copy(alpha = .22f), RoundedCornerShape(14.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OmniIcon(when (run.status) {
                RunStatus.Running -> R.drawable.omni_timer
                RunStatus.Failed, RunStatus.Cancelled -> R.drawable.omni_triangle_alert
                else -> R.drawable.omni_circle_check
            }, size = 18.dp, tint = color)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(when (run.status) {
                RunStatus.Running -> R.string.omni_rl_running
                RunStatus.Failed -> R.string.omni_rl_failed
                RunStatus.Cancelled -> R.string.omni_rl_cancelled
                else -> R.string.omni_rl_completed
            }), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
        }
        if (run.goal.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(run.goal, fontSize = 13.sp, lineHeight = 19.sp, color = palette.secondaryText)
        }
        Spacer(Modifier.height(10.dp))
        val metrics = buildList {
            add(stringResource(R.string.omni_rl_steps) to detail.steps.size.toString())
            run.startedAtMillis?.let { add(stringResource(R.string.omni_rl_started) to formatRunTimestamp(it)) }
            run.durationMs?.let { add(stringResource(R.string.omni_rl_duration) to formatRunDuration(it)) }
            run.model?.let { add(stringResource(R.string.omni_rl_model) to it) }
            run.callCount?.let { add(stringResource(R.string.omni_rl_calls) to it.toString()) }
            add(stringResource(R.string.omni_rl_tokens) to
                (run.tokens.total?.let(::formatRunTokens) ?: stringResource(R.string.omni_rl_unavailable)))
            run.tokens.prompt?.let { add(stringResource(R.string.omni_rl_prompt) to formatRunTokens(it)) }
            run.tokens.completion?.let { add(stringResource(R.string.omni_rl_completion) to formatRunTokens(it)) }
            run.tokens.cached?.let { add(stringResource(R.string.omni_rl_cached) to formatRunTokens(it)) }
        }
        MetricGrid(metrics)
    }
}

/** Label-over-value pairs, two per row. */
@Composable
private fun MetricGrid(metrics: List<Pair<String, String>>) {
    val palette = LocalOmniPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { (label, value) ->
                    Column(Modifier.weight(1f)) {
                        Text(label, fontSize = 11.sp, color = palette.tertiaryText)
                        Text(value, fontSize = 13.sp, color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LinkedFunction(function: OmniFlowFunction, onOpen: () -> Unit) {
    val palette = LocalOmniPalette.current
    Row(
        Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface)
            .clickable(role = Role.Button, onClick = onOpen).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OmniIcon(R.drawable.omni_workflow, size = 18.dp, tint = palette.accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.omni_rl_linked), fontSize = 11.sp, color = palette.tertiaryText)
            Text(function.name.ifEmpty { function.id }, fontSize = 14.sp, color = palette.text, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        OmniIcon(R.drawable.omni_chevron_right, size = 16.dp, tint = palette.tertiaryText)
    }
}

@Composable
private fun StepCard(step: RunLogStep, isLast: Boolean, onOpen: () -> Unit) {
    val palette = LocalOmniPalette.current
    val statusColor = runStatusColor(if (step.success) RunStatus.Succeeded else RunStatus.Failed)
    val title = stepTitle(step)
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // The timeline rail: a dot per step, joined until the last one.
        Column(Modifier.width(22.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.size(9.dp).clip(CircleShape).background(statusColor))
            if (!isLast) Box(Modifier.width(1.5.dp).weight(1f).background(palette.border))
        }
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f).padding(bottom = if (isLast) 0.dp else 10.dp).clip(RoundedCornerShape(12.dp))
                .background(palette.surface).clickable(role = Role.Button, onClick = onOpen).padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.omni_rl_step, step.index + 1), fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = palette.secondaryText)
                stepSourceLabel(step.source)?.let { source ->
                    Spacer(Modifier.width(6.dp))
                    Text(source, fontSize = 10.sp, color = palette.accent,
                        modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(palette.accent.copy(alpha = .1f))
                            .padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Spacer(Modifier.weight(1f))
                val trailing = listOfNotNull(step.durationMs?.let(::formatRunDuration),
                    step.tokens?.total?.let { stringResource(R.string.omni_of_tokens, formatRunTokens(it)) })
                if (trailing.isNotEmpty()) Text(trailing.joinToString(" · "), fontSize = 11.sp, color = palette.secondaryText)
                Spacer(Modifier.width(6.dp))
                OmniIcon(if (step.success) R.drawable.omni_circle_check else R.drawable.omni_triangle_alert,
                    size = 14.dp, tint = statusColor)
            }
            Spacer(Modifier.height(6.dp))
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp, color = palette.text,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (step.summary.isNotEmpty() && step.summary != title) {
                Spacer(Modifier.height(4.dp))
                Text(step.summary, fontSize = 12.sp, lineHeight = 16.sp, color = palette.secondaryText, maxLines = 2,
                    overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun stepTitle(step: RunLogStep): String {
    val action = stepActionLabel(step.action, step.tool)
    return if (step.target.isEmpty()) action else "$action · ${step.target}"
}

@Composable
private fun StepSheet(state: RunLogPageState, actions: RunLogActions) {
    val open = state.detail?.steps?.firstOrNull { it.index == state.openStep }
    val step = rememberLastNonNull(open) ?: return
    val palette = LocalOmniPalette.current
    OverlayBottomSheet(show = open != null, title = stringResource(R.string.omni_rl_step, step.index + 1),
        onDismissRequest = { actions.openStep(null) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            Text(stepTitle(step), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = palette.text)
            if (step.stateId != null) {
                SheetLabel(stringResource(R.string.omni_rl_evidence))
                TextButton(stringResource(R.string.omni_rl_screenshot), { actions.showScreen(step) },
                    enabled = !state.screenLoading, minHeight = 36.dp)
            }
            if (step.summary.isNotEmpty()) {
                SheetLabel(stringResource(R.string.omni_rl_decision))
                Text(step.summary, fontSize = 13.sp, lineHeight = 19.sp, color = palette.text)
            }
            if (step.thinking.isNotEmpty()) Thinking(step.thinking)
            step.tokens?.let { tokens ->
                SheetLabel(stringResource(R.string.omni_rl_usage))
                MetricGrid(listOfNotNull(
                    tokens.total?.let { stringResource(R.string.omni_rl_total) to formatRunTokens(it) },
                    tokens.prompt?.let { stringResource(R.string.omni_rl_prompt) to formatRunTokens(it) },
                    tokens.completion?.let { stringResource(R.string.omni_rl_completion) to formatRunTokens(it) },
                ))
            }
            SheetLabel(stringResource(R.string.omni_rl_action_details))
            CodeBlock(step.actionJson)
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** The model's raw reasoning, collapsed: it is long and rarely needed. */
@Composable
private fun Thinking(text: String) {
    val palette = LocalOmniPalette.current
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Row(Modifier.padding(top = 16.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button) { expanded = !expanded }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.omni_rl_thinking), Modifier.weight(1f), fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, color = palette.secondaryText)
        OmniIcon(if (expanded) R.drawable.omni_chevron_down else R.drawable.omni_chevron_right, size = 14.dp,
            tint = palette.tertiaryText)
    }
    AnimatedVisibility(expanded) {
        SelectionContainer { Text(text, fontSize = 12.sp, lineHeight = 18.sp, color = palette.secondaryText) }
    }
}

@Composable
private fun ScreenSheet(state: RunLogPageState, actions: RunLogActions) {
    val screen = rememberLastNonNull(state.screen) ?: return
    val palette = LocalOmniPalette.current
    val step = state.detail?.steps?.firstOrNull { it.index == state.openStep }
    OverlayBottomSheet(show = state.screen != null, title = stringResource(R.string.omni_rl_screenshot),
        onDismissRequest = actions.closeScreen) {
        Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).verticalScroll(rememberScrollState())) {
            Screenshot(screen.screenshotPath, step?.point)
            if (screen.app.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(screen.app, fontSize = 12.sp, fontFamily = FontFamily.Monospace, color = palette.secondaryText)
            }
            if (screen.xml.isNotBlank()) {
                SheetLabel(stringResource(R.string.omni_rl_view_tree))
                CodeBlock(screen.xml)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private class LoadedScreenshot(val image: ImageBitmap, val width: Int, val height: Int)

/** The recorded screen, downsampled for the sheet, with the tap marked where it really landed. */
@Composable
private fun Screenshot(path: String, point: ScreenPoint?) {
    val palette = LocalOmniPalette.current
    val loaded by produceState<LoadedScreenshot?>(null, path) {
        value = withContext(Dispatchers.IO) { decodeScreenshot(path) }
    }
    val shot = loaded
    if (shot == null) {
        Text(stringResource(R.string.omni_rl_screen_unavailable), fontSize = 13.sp, color = palette.secondaryText,
            modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    var shown by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).onSizeChanged { shown = it }) {
        Image(shot.image, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
        val marker = point?.let { scaledPoint(it, shot.width, shot.height, shown.width.toFloat(), shown.height.toFloat()) }
        if (marker != null && shown != IntSize.Zero) {
            val radius = 11.dp
            Box(Modifier.offset(with(density) { marker.x.toDp() } - radius, with(density) { marker.y.toDp() } - radius)
                .size(radius * 2).clip(CircleShape).background(Color(0x55FF3B30))
                .border(2.dp, Color(0xFFFF3B30), CircleShape))
        }
    }
}

private fun decodeScreenshot(path: String): LoadedScreenshot? {
    val file = File(path)
    if (path.isBlank() || !file.isFile) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= MAX_SCREENSHOT_WIDTH) sample *= 2
    val bitmap = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    // The marker scales against the full-size screen the coordinates were recorded on.
    return LoadedScreenshot(bitmap.asImageBitmap(), bounds.outWidth, bounds.outHeight)
}

private const val MAX_SCREENSHOT_WIDTH = 1080

@Composable
private fun SheetLabel(text: String) {
    Spacer(Modifier.height(16.dp))
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LocalOmniPalette.current.secondaryText)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun CodeBlock(text: String) {
    val palette = LocalOmniPalette.current
    SelectionContainer {
        Text(text, fontSize = 11.sp, lineHeight = 16.sp, fontFamily = FontFamily.Monospace, color = palette.text,
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.secondarySurface)
                .padding(12.dp))
    }
}
