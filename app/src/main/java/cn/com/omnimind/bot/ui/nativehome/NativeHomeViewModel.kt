package cn.com.omnimind.bot.ui.nativehome

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.mcp.McpServerState
import cn.com.omnimind.nativeui.ConversationSummary
import cn.com.omnimind.nativeui.DrawerNotice
import cn.com.omnimind.nativeui.DrawerSearchResults
import cn.com.omnimind.nativeui.home.CopiedMessage
import cn.com.omnimind.nativeui.home.DrawerSearch
import cn.com.omnimind.nativeui.home.conversationClipboardText
import cn.com.omnimind.nativeui.LegacyDestination
import cn.com.omnimind.nativeui.LocalServiceDetails
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.WebQuickAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class NativeHomeViewModel(
    private val repository: NativeHomeRepository,
    private val webActions: NativeWebActionRepository,
    private val context: Context,
) : ViewModel() {
    private val mutableState = MutableStateFlow(repository.readPreferences())
    val state = mutableState.asStateFlow()
    private var historyJob: Job? = null
    private var serviceJob: Job? = null
    private var webJob: Job? = null

    init { observeHistory() }

    private fun observeHistory() {
        historyJob?.cancel()
        historyJob = viewModelScope.launch {
            try {
                repository.snapshots.collect { snapshot ->
                    mutableState.update {
                        snapshot.copy(
                            localServiceEnabled = it.localServiceEnabled,
                            localServiceBusy = it.localServiceBusy,
                            localService = it.localService,
                            busyConversationIds = it.busyConversationIds,
                            webActions = it.webActions,
                            busyWebAction = it.busyWebAction,
                            pendingDestination = it.pendingDestination,
                            error = it.error,
                            searchHits = it.searchHits,
                            drawerNotice = it.drawerNotice,
                        )
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            }
        }
    }

    fun refresh() {
        mutableState.update { it.copy(error = null) }
        if (historyJob?.isActive != true) observeHistory()
        repository.refresh()
        if (serviceJob?.isActive != true) runServiceOperation { repository.localServiceState() }
        if (webJob?.isActive != true) {
            webJob = viewModelScope.launch {
                try {
                    val actions = webActions.list()
                    mutableState.update { it.copy(webActions = actions) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    // Discovery failure must not remove known actions or invent a stopped state.
                    reportError(error)
                }
            }
        }
    }

    fun setLocalServiceEnabled(enabled: Boolean) = runServiceOperation { repository.setLocalServiceEnabled(enabled) }
    fun refreshLocalServiceToken() = runServiceOperation { repository.refreshLocalServiceToken() }

    private fun runServiceOperation(operation: suspend () -> McpServerState) {
        if (serviceJob?.isActive == true) return
        mutableState.update { it.copy(localServiceBusy = true) }
        serviceJob = viewModelScope.launch {
            try {
                val actual = operation()
                val endpoint = actual.host?.takeIf(String::isNotBlank)?.let { "http://$it:${actual.port}" }.orEmpty()
                mutableState.update {
                    it.copy(localServiceEnabled = actual.enabled, localService = LocalServiceDetails(endpoint, actual.token), error = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            } finally {
                mutableState.update { it.copy(localServiceBusy = false) }
            }
        }
    }

    fun setArchived(conversation: ConversationSummary, archived: Boolean) {
        if (conversation.id in mutableState.value.busyConversationIds) return
        mutableState.update { it.copy(busyConversationIds = it.busyConversationIds + conversation.id, error = null) }
        viewModelScope.launch {
            try {
                repository.setArchived(conversation.id, archived)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            } finally {
                mutableState.update { it.copy(busyConversationIds = it.busyConversationIds - conversation.id) }
            }
        }
    }

    fun setPinned(conversation: ConversationSummary, pinned: Boolean) =
        conversationOperation(conversation, DrawerNotice.PinFailed) { repository.setPinned(conversation.id, pinned) }

    fun rename(conversation: ConversationSummary, title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed == conversation.title) return
        conversationOperation(conversation, DrawerNotice.RenameFailed) { repository.rename(conversation.id, trimmed) }
    }

    fun delete(conversation: ConversationSummary) =
        conversationOperation(conversation, DrawerNotice.DeleteFailed, DrawerNotice.Deleted) { repository.delete(conversation.id) }

    /** The conversation's text messages as plain text on the clipboard. */
    fun copy(conversation: ConversationSummary) = conversationOperation(conversation, DrawerNotice.CopyFailed, DrawerNotice.Copied) {
        val messages = repository.messages(conversation).asReversed().mapNotNull { row ->
            if ((row["type"] as? Number)?.toInt() != 1) return@mapNotNull null
            val text = (row["content"] as? Map<*, *>)?.get("text")?.toString() ?: return@mapNotNull null
            CopiedMessage(fromUser = (row["user"] as? Number)?.toInt() == 1, text = text)
        }
        val labels = localizedContext()
        val text = conversationClipboardText(messages, labels.getString(R.string.omni_copy_user), labels.getString(R.string.omni_copy_assistant))
        check(text.isNotEmpty()) { "Nothing to copy" }
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: error("No clipboard")
        withContext(Dispatchers.Main) { clipboard.setPrimaryClip(ClipData.newPlainText(conversation.title, text)) }
    }

    /**
     * One drawer operation on [conversation]: the row is busy meanwhile and
     * the Room observer shows the result; [failure] and [success] notices
     * tell the user what happened. Dart showed rows as changed before the
     * write and put them back on failure.
     */
    private fun conversationOperation(conversation: ConversationSummary, failure: DrawerNotice, success: DrawerNotice? = null,
                                      operation: suspend () -> Unit) {
        if (conversation.id in mutableState.value.busyConversationIds) return
        mutableState.update { it.copy(busyConversationIds = it.busyConversationIds + conversation.id) }
        viewModelScope.launch {
            val notice = try {
                operation()
                success
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OmniLog.w(TAG, "Drawer operation failed: ${error.javaClass.simpleName}")
                failure
            }
            mutableState.update { it.copy(busyConversationIds = it.busyConversationIds - conversation.id, drawerNotice = notice ?: it.drawerNotice) }
        }
    }

    fun consumeDrawerNotice() = mutableState.update { it.copy(drawerNotice = null) }

    private val messageSearch = DrawerMessageSearch(repository::messages)
    private var searchJob: Job? = null

    /** Message-text search, debounced; titles and previews filter in the drawer at once. */
    fun search(query: String) {
        searchJob?.cancel()
        if (DrawerSearch.tokens(query).isEmpty()) {
            mutableState.update { it.copy(searchHits = null) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            val hits = messageSearch.search(query, mutableState.value.conversations)
            mutableState.update { it.copy(searchHits = DrawerSearchResults(query, hits)) }
        }
    }

    private fun localizedContext(): Context {
        val preferences = context.getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
        val locale = resolveNativeHomeLocale(preferences.getString("flutter.language_option", "system"))
        val configuration = android.content.res.Configuration(context.resources.configuration).apply { setLocale(locale) }
        return context.createConfigurationContext(configuration)
    }

    fun setSectionExpanded(key: String, expanded: Boolean) {
        viewModelScope.launch {
            try {
                repository.setSectionExpanded(key, expanded)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            }
        }
    }

    fun invokeWebAction(action: WebQuickAction, stop: Boolean) {
        if (mutableState.value.busyWebAction != null) return
        webJob?.cancel()
        mutableState.update { it.copy(busyWebAction = action.key, error = null) }
        webJob = viewModelScope.launch {
            try {
                val result = webActions.invoke(action, stop)
                when {
                    stop && !result.stopped -> showMessage(R.string.omni_web_stop_failed)
                    stop || result.code == "OPENED" -> Unit
                    result.code == "RUNTIME_MISSING" -> mutableState.update {
                        it.copy(pendingDestination = LegacyDestination.TerminalPackage(result.packageId))
                    }
                    result.code == "PROVIDER_REQUIRED" || result.code == "MODEL_REQUIRED" -> {
                        showMessage(R.string.omni_web_provider_required)
                        mutableState.update { it.copy(pendingDestination = LegacyDestination.Page.ModelProviders) }
                    }
                    result.code == "UNSUPPORTED_PROVIDER" -> showMessage(R.string.omni_web_unsupported_provider)
                    result.code == "URL_TIMEOUT" -> showMessage(R.string.omni_web_timeout)
                    result.code == "STOP_FAILED" -> showMessage(R.string.omni_web_stop_failed)
                    result.code == "BROWSER_UNAVAILABLE" -> showMessage(R.string.omni_web_browser_unavailable)
                    else -> showMessage(R.string.omni_web_open_failed)
                }
                val refreshed = webActions.list()
                mutableState.update { it.copy(webActions = refreshed) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                reportError(error)
            } finally {
                mutableState.update { it.copy(busyWebAction = null) }
            }
        }
    }

    fun consumeDestination() { mutableState.update { it.copy(pendingDestination = null) } }

    /** An entry from outside Home (a share from another app) asks for a page. */
    fun requestDestination(destination: LegacyDestination) {
        mutableState.update { it.copy(pendingDestination = destination) }
    }

    private fun showMessage(resource: Int) {
        mutableState.update { it.copy(error = localizedContext().getString(resource)) }
    }

    private fun reportError(error: Exception) {
        OmniLog.e(TAG, "Native home operation failed", error)
        mutableState.update { it.copy(loading = false) }
        showMessage(R.string.omni_operation_failed)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NativeHomeViewModel::class.java))
            val application = context.applicationContext
            @Suppress("UNCHECKED_CAST")
            return NativeHomeViewModel(NativeHomeRepository(application), NativeWebActionRepository(application), application) as T
        }
    }

    private companion object {
        const val TAG = "NativeHome"
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}
