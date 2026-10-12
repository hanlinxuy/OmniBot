package cn.com.omnimind.bot.ui.chat.browser

import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.agent.LiveAgentBrowserSessionManager
import cn.com.omnimind.nativeui.chat.browser.BrowserActions
import cn.com.omnimind.nativeui.chat.browser.BrowserOverlay
import cn.com.omnimind.nativeui.chat.browser.BrowserSession
import cn.com.omnimind.nativeui.chat.browser.BrowserSheet
import cn.com.omnimind.nativeui.chat.browser.BrowserSheets
import cn.com.omnimind.nativeui.chat.browser.BrowserWindowFrame
import cn.com.omnimind.nativeui.chat.browser.DownloadAction
import cn.com.omnimind.nativeui.chat.browser.clamped
import cn.com.omnimind.nativeui.chat.browser.defaultBrowserFrame
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The window's state: the session it shows, whether it is open, where it sits and which sheet is up. */
internal data class BrowserWindowState(
    val session: BrowserSession? = null,
    val open: Boolean = false,
    val frame: BrowserWindowFrame? = null,
    val sheet: BrowserSheet? = null,
)

/**
 * The agent browser for one native chat page (batch 5m). It follows only
 * this conversation's engine (`conversation_<id>`), runs every command on
 * that engine, and survives rotation; the WebView itself stays with the
 * engine. Dart followed whichever engine ran last.
 */
internal class NativeBrowserViewModel(private val savedState: SavedStateHandle) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserWindowState(open = savedState.get<Boolean>(KEY_OPEN) == true))
    val state: StateFlow<BrowserWindowState> = mutableState.asStateFlow()
    private var workspaceId: String? = null
    private val mutableError = MutableStateFlow<String?>(null)
    /** A failed command's message, shown once as a toast. */
    val error: StateFlow<String?> = mutableError.asStateFlow()

    private val listener: (String, Map<String, Any?>) -> Unit = { id, snapshot ->
        if (id == workspaceId) apply(snapshot)
    }

    init {
        LiveAgentBrowserSessionManager.addSnapshotListener(listener)
    }

    override fun onCleared() {
        LiveAgentBrowserSessionManager.removeSnapshotListener(listener)
    }

    /** Follows [conversationId]'s browser; null until a new conversation is created. */
    fun follow(conversationId: Long?) {
        val id = conversationId?.let { "conversation_$it" }
        if (id == workspaceId) return
        workspaceId = id
        if (id == null) {
            mutableState.update { it.copy(session = null, open = false, sheet = null) }
            return
        }
        apply(LiveAgentBrowserSessionManager.snapshotFor(id))
    }

    private fun apply(snapshot: Map<String, Any?>) {
        val session = BrowserSessionMapper.map(snapshot)
        mutableState.update {
            // No tabs left (closed by the user or the agent): the window has nothing to show.
            if (session == null) it.copy(session = null, open = false, sheet = null) else it.copy(session = session)
        }
        if (session == null) savedState[KEY_OPEN] = false
    }

    fun toggle() {
        if (state.value.session == null) return
        val open = !state.value.open
        savedState[KEY_OPEN] = open
        mutableState.update { it.copy(open = open, sheet = null) }
    }

    fun close() {
        savedState[KEY_OPEN] = false
        mutableState.update { it.copy(open = false, sheet = null) }
    }

    fun consumeError() {
        mutableError.value = null
    }

    fun place(frame: BrowserWindowFrame) = mutableState.update { it.copy(frame = frame) }

    fun showSheet(sheet: BrowserSheet?) = mutableState.update { it.copy(sheet = sheet) }

    val actions = BrowserActions(
        navigate = { url -> call("navigate", "url" to url) },
        reload = { call("reload") },
        stop = { call("stopLoading") },
        back = { call("goBack") },
        forward = { call("goForward") },
        newTab = { call("newTab") },
        selectTab = { id -> call("selectTab", "tabId" to id) },
        closeTab = { id -> call("closeTab", "tabId" to id) },
        toggleBookmark = { call("toggleBookmark") },
        removeBookmark = { url -> call("removeBookmark", "url" to url) },
        clearHistory = { call("clearHistory") },
        toggleDesktopMode = { call("toggleDesktopMode") },
        resolveDialog = { id, accept, value -> call("resolveDialog", "requestId" to id, "accept" to accept, "promptValue" to value) },
        answerPermission = { id, grant -> call(if (grant) "grantPermission" else "denyPermission", "requestId" to id) },
        answerExternalOpen = { id, open -> call(if (open) "confirmExternalOpen" else "cancelExternalOpen", "requestId" to id) },
        download = { id, action ->
            val method = when (action) {
                DownloadAction.Pause -> "pauseDownload"
                DownloadAction.Resume -> "resumeDownload"
                DownloadAction.Cancel -> "cancelDownload"
                DownloadAction.Retry -> "retryDownload"
                DownloadAction.Open -> "openDownloadedFile"
                DownloadAction.OpenFolder -> "openDownloadLocation"
                DownloadAction.Delete -> "deleteDownload"
            }
            call(method, "taskId" to id, "deleteFile" to (action == DownloadAction.Delete))
        },
    )

    /** Runs a host command on this conversation's engine; the result snapshot refreshes the window. */
    private fun call(method: String, vararg arguments: Pair<String, Any?>) {
        val id = workspaceId ?: return
        viewModelScope.launch {
            try {
                @Suppress("UNCHECKED_CAST")
                val result = LiveAgentBrowserSessionManager.handleCall(id, method, arguments.toMap()) as? Map<String, Any?>
                if (result != null && id == workspaceId) apply(result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OmniLog.w(TAG, "Browser command $method failed: ${error.javaClass.simpleName}")
                mutableError.value = error.message ?: error.javaClass.simpleName
            }
        }
    }

    class Factory : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            NativeBrowserViewModel(extras.createSavedStateHandle()) as T
    }

    private companion object {
        const val TAG = "NativeBrowser"
        const val KEY_OPEN = "open"
    }
}

/**
 * The browser window and its sheets over the chat transcript. The WebView
 * host is created once per conversation and released when the window closes
 * or the page leaves, never on a snapshot.
 */
@Composable
internal fun NativeBrowserLayer(viewModel: NativeBrowserViewModel, containerWidth: Float, containerHeight: Float) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeError()
        }
    }
    val session = state.session ?: return
    if (!state.open) return
    val frame = (state.frame ?: defaultBrowserFrame(containerWidth, containerHeight)).clamped(containerWidth, containerHeight)
    BrowserOverlay(
        session = session,
        frame = frame,
        onFrameChange = { viewModel.place(it.clamped(containerWidth, containerHeight)) },
        actions = viewModel.actions,
        onClose = viewModel::close,
        onOpenSheet = viewModel::showSheet,
    ) { modifier -> AgentWebViewHost(session.workspaceId, modifier) }
    BrowserSheets(state.sheet, session, viewModel.actions, viewModel::showSheet)
}

/** Lends the engine's active tab to this composition, keyed only by the conversation. */
@Composable
private fun AgentWebViewHost(workspaceId: String, modifier: Modifier) {
    val context = LocalContext.current
    val container = remember(workspaceId) { FrameLayout(context) }
    DisposableEffect(workspaceId) {
        LiveAgentBrowserSessionManager.attachTabOf(workspaceId, container, context)
        onDispose { LiveAgentBrowserSessionManager.detachTabOf(workspaceId, container) }
    }
    AndroidView(factory = { container }, modifier = modifier)
}
