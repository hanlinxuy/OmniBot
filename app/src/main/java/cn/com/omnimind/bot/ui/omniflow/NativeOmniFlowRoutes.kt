package cn.com.omnimind.bot.ui.omniflow

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.com.omnimind.nativeui.omniflow.ExecutionCenterScreen
import cn.com.omnimind.nativeui.omniflow.RunLogScreen

/** [onEnhance] opens a new chat with the enhancement request drafted; the user sends it. */
@Composable
internal fun NativeOmniFlowRoute(
    viewModel: NativeOmniFlowViewModel,
    onRunLog: (String) -> Unit,
    onEnhance: (String) -> Unit,
    onPlugin: (String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    // Back from the plugin page or the accessibility settings.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.resume() }
    val draft by viewModel.enhanceDraft.collectAsStateWithLifecycle()
    LaunchedEffect(draft) {
        draft?.let {
            viewModel.consumeEnhanceDraft()
            onEnhance(it)
        }
    }
    val actions = remember(viewModel, onRunLog, onPlugin) {
        viewModel.actions.copy(openRunLog = onRunLog, openPlugin = { onPlugin(NativeOmniFlowRepository.PLUGIN_ID) })
    }
    ExecutionCenterScreen(state, actions, onBack)
}

@Composable
internal fun NativeRunLogRoute(viewModel: NativeRunLogViewModel, onFunction: (String) -> Unit, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.load() }
    val functionId by viewModel.openFunction.collectAsStateWithLifecycle()
    LaunchedEffect(functionId) {
        functionId?.let {
            viewModel.consumeOpenFunction()
            onFunction(it)
        }
    }
    RunLogScreen(state, viewModel.actions, onBack)
}
