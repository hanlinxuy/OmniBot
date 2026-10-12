package cn.com.omnimind.nativeui.omniflow

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.theme.LocalOmniPalette

@Composable
internal fun omniFlowNoticeText(notice: OmniFlowNotice): String =
    if (notice.arg != null) stringResource(notice.text, notice.arg) else stringResource(notice.text)

@Composable
internal fun runStatusLabel(status: RunStatus): String = stringResource(when (status) {
    RunStatus.Succeeded -> R.string.omni_of_status_succeeded
    RunStatus.Running -> R.string.omni_of_status_running
    RunStatus.Failed -> R.string.omni_of_status_failed
    RunStatus.Cancelled -> R.string.omni_of_status_cancelled
    RunStatus.Unknown -> R.string.omni_of_status_unknown
})

/** The Dart run-log colors, one shade per theme. */
@Composable
internal fun runStatusColor(status: RunStatus): Color {
    val palette = LocalOmniPalette.current
    return when (status) {
        RunStatus.Succeeded -> if (palette.dark) Color(0xFF63D98A) else Color(0xFF2F8F4E)
        RunStatus.Running -> if (palette.dark) Color(0xFFFFD166) else Color(0xFFB27F00)
        RunStatus.Failed -> if (palette.dark) Color(0xFFFF7A7A) else Color(0xFFDC2626)
        RunStatus.Cancelled, RunStatus.Unknown -> palette.tertiaryText
    }
}

/** The action's name; an unknown tool shows as itself. */
@Composable
internal fun stepActionLabel(action: StepAction, tool: String): String = when (action) {
    StepAction.OpenApp -> stringResource(R.string.omni_rl_action_open_app)
    StepAction.Tap -> stringResource(R.string.omni_rl_action_tap)
    StepAction.LongPress -> stringResource(R.string.omni_rl_action_long_press)
    StepAction.EnterText -> stringResource(R.string.omni_rl_action_text)
    StepAction.Swipe -> stringResource(R.string.omni_rl_action_swipe)
    StepAction.PressKey -> stringResource(R.string.omni_rl_action_key)
    StepAction.Wait -> stringResource(R.string.omni_rl_action_wait)
    StepAction.Other -> tool.ifEmpty { stringResource(R.string.omni_rl_action_other) }
}

@Composable
internal fun stepSourceLabel(source: StepSource): String? = when (source) {
    StepSource.Automatic -> stringResource(R.string.omni_rl_source_auto)
    StepSource.Human -> stringResource(R.string.omni_rl_source_human)
    StepSource.Function -> stringResource(R.string.omni_rl_source_function)
    StepSource.None -> null
}
