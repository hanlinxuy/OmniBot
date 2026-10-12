package cn.com.omnimind.nativeui.omniflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.components.OmniConfirmDialog
import cn.com.omnimind.nativeui.components.OmniDialogActions
import cn.com.omnimind.nativeui.components.OmniEmptyState
import cn.com.omnimind.nativeui.components.OmniIcon
import cn.com.omnimind.nativeui.components.OmniIconButton
import cn.com.omnimind.nativeui.components.OmniPage
import cn.com.omnimind.nativeui.components.OmniTabRow
import cn.com.omnimind.nativeui.theme.LocalOmniPalette
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The OmniFlow execution center (batch 5n): reusable Functions and the Run
 * Logs they are compiled from. Presentation only; the host ViewModel talks
 * to the OmniFlow tools.
 */
@Composable
fun ExecutionCenterScreen(state: ExecutionCenterState, actions: ExecutionCenterActions, onBack: () -> Unit) {
    OmniPage(
        stringResource(R.string.omni_of_title), onBack,
        notice = state.notice?.let { omniFlowNoticeText(it) },
        onNoticeShown = actions.dismissNotice,
        actions = {
            OmniIconButton(R.drawable.omni_refresh_cw, stringResource(R.string.omni_of_refresh), actions.refresh,
                size = 20.dp, enabled = state.availability != OmniFlowAvailability.Loading)
        },
    ) { insets ->
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            when (state.availability) {
                OmniFlowAvailability.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                OmniFlowAvailability.Failed -> OmniEmptyState(R.drawable.omni_triangle_alert,
                    stringResource(R.string.omni_of_load_failed), message = state.availabilityError,
                    actionLabel = stringResource(R.string.omni_retry), onAction = actions.refresh)
                OmniFlowAvailability.NotInstalled -> OmniEmptyState(R.drawable.omni_box,
                    stringResource(R.string.omni_of_not_installed_title),
                    message = stringResource(R.string.omni_of_not_installed_body),
                    actionLabel = stringResource(R.string.omni_of_open_plugin), onAction = actions.openPlugin)
                OmniFlowAvailability.Disabled -> OmniEmptyState(R.drawable.omni_box,
                    stringResource(R.string.omni_of_disabled_title),
                    message = stringResource(R.string.omni_of_disabled_body),
                    actionLabel = stringResource(R.string.omni_of_enable), onAction = actions.enablePlugin,
                    actionEnabled = !state.enablingPlugin)
                OmniFlowAvailability.Ready -> {
                    Spacer(Modifier.height(8.dp))
                    OmniTabRow(
                        listOf(stringResource(R.string.omni_of_tab_functions), stringResource(R.string.omni_of_tab_runs)),
                        state.tab.ordinal, { actions.setTab(OmniFlowTab.entries[it]) },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    when (state.tab) {
                        OmniFlowTab.Functions -> FunctionsTab(state, actions)
                        OmniFlowTab.RunLogs -> RunLogsTab(state, actions)
                    }
                }
            }
        }
        FunctionSheet(state, actions)
        ArgumentsDialog(state.arguments, actions)
        val deleting = state.confirmDelete
        OmniConfirmDialog(
            show = deleting != null,
            title = stringResource(R.string.omni_of_delete_title),
            summary = stringResource(R.string.omni_of_delete_body, deleting?.displayName().orEmpty()),
            confirmText = stringResource(R.string.omni_agent_delete),
            onConfirm = actions.confirmDelete,
            onDismiss = { actions.requestDelete(null) },
        )
        OmniConfirmDialog(
            show = state.accessibilityNeeded,
            title = stringResource(R.string.omni_of_a11y_title),
            summary = stringResource(R.string.omni_of_a11y_body),
            confirmText = stringResource(R.string.omni_of_a11y_open),
            onConfirm = actions.openAccessibilitySettings,
            onDismiss = actions.dismissAccessibility,
        )
    }
}

@Composable
private fun <T> PagedList(
    list: OmniFlowList<T>,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    key: (T) -> String,
    empty: @Composable () -> Unit,
    row: @Composable (T) -> Unit,
) {
    when {
        !list.loaded && list.items.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        list.error != null && list.items.isEmpty() -> OmniEmptyState(R.drawable.omni_triangle_alert,
            stringResource(R.string.omni_of_load_failed), message = list.error,
            actionLabel = stringResource(R.string.omni_retry), onAction = onRefresh)
        list.items.isEmpty() -> empty()
        else -> PullToRefresh(
            isRefreshing = list.loading && list.loaded, onRefresh = onRefresh,
            pullToRefreshState = rememberPullToRefreshState(), modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(list.items, key = key) { row(it) }
                if (list.hasMore) item(key = "load-more") {
                    Box(Modifier.fillMaxWidth().heightIn(min = 52.dp), contentAlignment = Alignment.Center) {
                        if (list.loading) CircularProgressIndicator(size = 18.dp, strokeWidth = 2.dp)
                        else TextButton(stringResource(R.string.omni_of_load_more), onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun FunctionsTab(state: ExecutionCenterState, actions: ExecutionCenterActions) {
    PagedList(
        state.functions, actions.refresh, { actions.loadMore(OmniFlowTab.Functions) }, key = { it.id },
        empty = {
            OmniEmptyState(R.drawable.omni_workflow, stringResource(R.string.omni_of_functions_empty),
                message = stringResource(R.string.omni_of_functions_empty_hint))
        },
    ) { function -> FunctionRow(function, state.runningFunctionId, actions) }
}

@Composable
private fun FunctionRow(function: OmniFlowFunction, runningId: String?, actions: ExecutionCenterActions) {
    val palette = LocalOmniPalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface)
            .clickable(role = Role.Button) { actions.openFunction(function) }
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(function.displayName(), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = palette.text,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (function.description.isNotEmpty() && function.description != function.name) {
                Spacer(Modifier.height(3.dp))
                Text(function.description, fontSize = 12.sp, color = palette.secondaryText, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.omni_of_function_meta, function.steps.size, function.parameters.size),
                fontSize = 11.sp, color = palette.tertiaryText)
        }
        RunButton(function, runningId, actions)
    }
}

/** Run, or progress while this Function replays; disabled while another one does. */
@Composable
private fun RunButton(function: OmniFlowFunction, runningId: String?, actions: ExecutionCenterActions) {
    if (runningId == function.id) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.omni_of_running), fontSize = 13.sp, color = LocalOmniPalette.current.secondaryText)
        }
    } else {
        TextButton(stringResource(R.string.omni_of_run), { actions.run(function) }, enabled = runningId == null,
            colors = ButtonDefaults.textButtonColorsPrimary(), minHeight = 34.dp)
    }
}

@Composable
private fun RunLogsTab(state: ExecutionCenterState, actions: ExecutionCenterActions) {
    PagedList(
        state.runLogs, actions.refresh, { actions.loadMore(OmniFlowTab.RunLogs) }, key = { it.runId },
        empty = {
            OmniEmptyState(R.drawable.omni_receipt_text, stringResource(R.string.omni_of_runs_empty),
                message = stringResource(R.string.omni_of_runs_empty_hint))
        },
    ) { run ->
        RunLogRow(run, linkedFunction(run, state.functions.items), run.runId in state.registeringRunIds, actions)
    }
}

@Composable
private fun RunLogRow(run: RunLogSummary, linked: OmniFlowFunction?, registering: Boolean, actions: ExecutionCenterActions) {
    val palette = LocalOmniPalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface)
            .clickable(role = Role.Button) { actions.openRunLog(run.runId) }
            .padding(start = 14.dp, top = 12.dp, end = 6.dp, bottom = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(run.goal.ifEmpty { run.runId }, Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text(runStatusLabel(run.status), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = runStatusColor(run.status))
            Spacer(Modifier.width(8.dp))
        }
        Spacer(Modifier.height(5.dp))
        Text(runMeta(run), fontSize = 11.sp, lineHeight = 16.sp, color = palette.secondaryText,
            modifier = Modifier.padding(end = 8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(run.runId, Modifier.weight(1f), fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                color = palette.tertiaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                registering -> Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(size = 14.dp, strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.omni_of_registering), fontSize = 13.sp, color = palette.secondaryText)
                }
                linked != null -> TextButton(stringResource(R.string.omni_of_view_function), { actions.openFunction(linked) },
                    minHeight = 34.dp)
                run.status == RunStatus.Succeeded -> TextButton(stringResource(R.string.omni_of_register),
                    { actions.registerRunLog(run) }, colors = ButtonDefaults.textButtonColorsPrimary(), minHeight = 34.dp)
                else -> Spacer(Modifier.height(46.dp))
            }
        }
    }
}

@Composable
private fun runMeta(run: RunLogSummary): String = buildList {
    run.startedAtMillis?.let { add(formatRunTimestamp(it)) }
    run.durationMs?.let { add(formatRunDuration(it)) }
    add(run.tokens.total?.let { stringResource(R.string.omni_of_tokens, formatRunTokens(it)) }
        ?: stringResource(R.string.omni_of_tokens_unavailable))
    run.model?.let(::add)
    run.callCount?.let { add(stringResource(R.string.omni_of_calls, it)) }
}.joinToString(" · ")

@Composable
private fun FunctionSheet(state: ExecutionCenterState, actions: ExecutionCenterActions) {
    // Keeps the last Function so the sheet does not empty while it slides away.
    val shown = rememberLastNonNull(state.sheet) ?: return
    val function = shown.function
    val palette = LocalOmniPalette.current
    OverlayBottomSheet(show = state.sheet != null, title = function.displayName(), onDismissRequest = actions.closeFunction) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())) {
            if (function.id.isNotEmpty()) {
                Text(function.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.tertiaryText)
                Spacer(Modifier.height(6.dp))
            }
            Text(
                stringResource(R.string.omni_of_function_meta, function.steps.size, function.parameters.size) + " · " +
                    stringResource(if (function.agentVisible) R.string.omni_of_agent_visible else R.string.omni_of_agent_hidden),
                fontSize = 12.sp, color = palette.secondaryText,
            )
            if (function.description.isNotEmpty() && function.description != function.name) {
                Spacer(Modifier.height(10.dp))
                Text(function.description, fontSize = 14.sp, lineHeight = 20.sp, color = palette.text)
            }
            if (shown.syncing) {
                Spacer(Modifier.height(10.dp))
                CircularProgressIndicator(size = 16.dp, strokeWidth = 2.dp)
            }
            if (shown.syncFailed) {
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OmniIcon(R.drawable.omni_triangle_alert, size = 14.dp, tint = palette.secondaryText)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.omni_of_sync_failed), Modifier.weight(1f), fontSize = 12.sp,
                        color = palette.secondaryText)
                    TextButton(stringResource(R.string.omni_retry), actions.retryFunction, minHeight = 32.dp)
                }
            }
            SheetSection(stringResource(R.string.omni_of_parameters))
            if (function.parameters.isEmpty()) SheetHint(stringResource(R.string.omni_of_no_parameters))
            function.parameters.forEach { parameter ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(parameter.name, fontSize = 13.sp, fontFamily = FontFamily.Monospace, color = palette.text)
                        if (parameter.description.isNotEmpty()) {
                            Text(parameter.description, fontSize = 12.sp, color = palette.secondaryText)
                        }
                    }
                    Text(listOfNotNull(parameter.type.ifEmpty { null },
                        if (parameter.required) stringResource(R.string.omni_of_required) else null).joinToString(" · "),
                        fontSize = 11.sp, color = palette.tertiaryText)
                }
            }
            SheetSection(stringResource(R.string.omni_of_steps))
            if (function.steps.isEmpty()) SheetHint(stringResource(R.string.omni_of_no_steps))
            function.steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(22.dp).clip(RoundedCornerShape(11.dp)).background(palette.accent.copy(alpha = .12f)),
                        contentAlignment = Alignment.Center) {
                        Text("${index + 1}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = palette.accent)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stepActionLabel(step.action, step.tool), fontSize = 13.sp, color = palette.text)
                        Text(listOf(step.tool, step.argsJson).filter { it.isNotEmpty() }.joinToString("  "),
                            fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = palette.tertiaryText,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OmniIconButton(R.drawable.omni_trash_2, stringResource(R.string.omni_agent_delete),
                { actions.requestDelete(function) }, size = 20.dp)
            TextButton(stringResource(R.string.omni_of_enhance), { actions.enhance(function) }, Modifier.weight(1f))
            TextButton(stringResource(R.string.omni_of_run), { actions.run(function) }, Modifier.weight(1f),
                enabled = state.runningFunctionId == null, colors = ButtonDefaults.textButtonColorsPrimary())
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ArgumentsDialog(request: ArgumentsRequest?, actions: ExecutionCenterActions) {
    val shown = rememberLastNonNull(request) ?: return
    OverlayDialog(show = request != null, title = stringResource(R.string.omni_of_arguments_title),
        summary = shown.function.displayName(), onDismissRequest = actions.dismissArguments) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            shown.function.parameters.forEach { parameter ->
                Column {
                    TextField(
                        shown.values[parameter.name].orEmpty(), { actions.setArgument(parameter.name, it) },
                        label = if (parameter.required) "${parameter.name} *" else parameter.name,
                        useLabelAsPlaceholder = true, singleLine = parameter.type !in setOf("object", "array"),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val error = shown.errors[parameter.name]
                    val hint = error?.let { argumentErrorText(it) } ?: parameter.description.ifEmpty { null }
                    if (hint != null) {
                        Text(hint, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                            color = if (error != null) MiuixTheme.colorScheme.error else LocalOmniPalette.current.secondaryText)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        OmniDialogActions(actions.dismissArguments, stringResource(R.string.omni_of_arguments_start), actions.confirmArguments)
    }
}

@Composable
private fun SheetSection(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LocalOmniPalette.current.text)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SheetHint(text: String) {
    Text(text, fontSize = 12.sp, color = LocalOmniPalette.current.tertiaryText, modifier = Modifier.padding(vertical = 6.dp))
}

@Composable
private fun OmniFlowFunction.displayName(): String =
    name.ifEmpty { id.ifEmpty { stringResource(R.string.omni_of_unnamed) } }

@Composable
private fun argumentErrorText(error: ArgumentError): String = stringResource(when (error) {
    ArgumentError.Required -> R.string.omni_of_arg_required
    ArgumentError.NotAnInteger -> R.string.omni_of_arg_integer
    ArgumentError.NotANumber -> R.string.omni_of_arg_number
    ArgumentError.NotABoolean -> R.string.omni_of_arg_boolean
    ArgumentError.InvalidJson -> R.string.omni_of_arg_json
})

/** The latest non-null [value], so an overlay keeps its content while it animates out. */
@Composable
internal fun <T : Any> rememberLastNonNull(value: T?): T? {
    var last by remember { mutableStateOf(value) }
    if (value != null) last = value
    return last
}
