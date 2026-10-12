package cn.com.omnimind.bot.ui.omniflow

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.manager.AppPermissionAccess
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.omniflow.ArgumentsRequest
import cn.com.omnimind.nativeui.omniflow.ExecutionCenterActions
import cn.com.omnimind.nativeui.omniflow.ExecutionCenterState
import cn.com.omnimind.nativeui.omniflow.FunctionSheetState
import cn.com.omnimind.nativeui.omniflow.OmniFlowAvailability
import cn.com.omnimind.nativeui.omniflow.OmniFlowFunction
import cn.com.omnimind.nativeui.omniflow.OmniFlowList
import cn.com.omnimind.nativeui.omniflow.OmniFlowNotice
import cn.com.omnimind.nativeui.omniflow.OmniFlowTab
import cn.com.omnimind.nativeui.omniflow.ParsedArguments
import cn.com.omnimind.nativeui.omniflow.RunLogSummary
import cn.com.omnimind.nativeui.omniflow.mergeById
import cn.com.omnimind.nativeui.omniflow.pageCursor
import cn.com.omnimind.nativeui.omniflow.parseFunctionArguments
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The OmniFlow execution center (batch 5n). One per back-stack entry; the
 * tab survives rotation and process death.
 */
internal class NativeOmniFlowViewModel(
    context: Context,
    initialTab: OmniFlowTab,
    private val initialFunctionId: String?,
    private val savedState: SavedStateHandle,
) : ViewModel() {
    private val appContext = context.applicationContext
    private val repository = NativeOmniFlowRepository(appContext)
    private val permissions = AppPermissionAccess(appContext)
    private val mutableState = MutableStateFlow(ExecutionCenterState(tab = savedState.get<OmniFlowTab>(KEY_TAB) ?: initialTab))
    val state: StateFlow<ExecutionCenterState> = mutableState.asStateFlow()

    /** The payloads behind the rows: enhancement sends the whole Function back. */
    private val rawFunctions = HashMap<String, Map<String, Any?>>()
    private var functionsOffset = 0
    private var runLogsOffset = 0
    /** A replay waiting for the accessibility service; it continues when the page resumes. */
    private var pendingReplay: OmniFlowFunction? = null

    private val mutableEnhanceDraft = MutableStateFlow<String?>(null)
    /** One-shot: the route opens a new chat with this request drafted. */
    val enhanceDraft: StateFlow<String?> = mutableEnhanceDraft.asStateFlow()

    init {
        viewModelScope.launch {
            OmniFlowReplays.running.collect { id -> mutableState.update { it.copy(runningFunctionId = id) } }
        }
    }

    /** Until the plugin is ready, every entry checks it again. */
    fun load() {
        if (mutableState.value.availability != OmniFlowAvailability.Ready) loadAvailability()
    }

    /**
     * Back on the page: the plugin page may have installed or enabled
     * OmniFlow, and a replay may be waiting for the accessibility service.
     */
    fun resume() {
        load()
        val function = pendingReplay ?: return
        viewModelScope.launch {
            if (!permissions.awaitAccessibilityReady()) return@launch
            pendingReplay = null
            mutableState.update { it.copy(accessibilityNeeded = false) }
            collectArguments(function)
        }
    }

    fun consumeEnhanceDraft() {
        mutableEnhanceDraft.value = null
    }

    private fun loadAvailability() {
        mutableState.update { it.copy(availability = OmniFlowAvailability.Loading, availabilityError = null) }
        viewModelScope.launch {
            val plugin = try {
                repository.plugin()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(availability = OmniFlowAvailability.Failed, availabilityError = message(error)) }
                return@launch
            }
            val availability = when {
                plugin?.installed != true -> OmniFlowAvailability.NotInstalled
                !plugin.enabled -> OmniFlowAvailability.Disabled
                else -> OmniFlowAvailability.Ready
            }
            mutableState.update { it.copy(availability = availability) }
            if (availability == OmniFlowAvailability.Ready) refreshLists()
        }
    }

    private fun refreshLists() {
        loadFunctions(reset = true)
        // Run Log rows link to their Functions, so both lists load with the Run Logs tab.
        if (mutableState.value.tab == OmniFlowTab.RunLogs || mutableState.value.runLogs.loaded) loadRunLogs(reset = true)
    }

    private fun loadFunctions(reset: Boolean) {
        if (mutableState.value.functions.loading) return
        val offset = if (reset) 0 else functionsOffset
        mutableState.update { it.copy(functions = it.functions.copy(loading = true, error = if (reset) null else it.functions.error)) }
        viewModelScope.launch {
            try {
                val payload = repository.listFunctions(PAGE_SIZE, offset)
                val rows = OmniFlowPayloads.maps(payload["functions"])
                rows.forEach { raw -> OmniFlowPayloads.text(raw["function_id"]).takeIf { it.isNotEmpty() }?.let { rawFunctions[it] = raw } }
                val items = rows.mapNotNull(OmniFlowPayloads::function)
                val merged = if (reset) items else mergeById(mutableState.value.functions.items, items) { it.id }
                val cursor = pageCursor(payload["has_more"] as? Boolean, OmniFlowPayloads.int(payload["total_count"]),
                    OmniFlowPayloads.int(payload["next_offset"]), offset, items.size, merged.size, PAGE_SIZE)
                functionsOffset = cursor.nextOffset
                mutableState.update { it.copy(functions = OmniFlowList(merged, loaded = true, hasMore = cursor.hasMore)) }
                openInitialFunction()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(functions = it.functions.copy(loaded = true, loading = false, error = message(error))) }
            }
        }
    }

    private fun loadRunLogs(reset: Boolean) {
        if (mutableState.value.runLogs.loading) return
        val offset = if (reset) 0 else runLogsOffset
        mutableState.update { it.copy(runLogs = it.runLogs.copy(loading = true, error = if (reset) null else it.runLogs.error)) }
        viewModelScope.launch {
            try {
                val payload = repository.listRunLogs(PAGE_SIZE, offset)
                val items = OmniFlowPayloads.runLogs(payload)
                val merged = if (reset) items else mergeById(mutableState.value.runLogs.items, items) { it.runId }
                val cursor = pageCursor(payload["has_more"] as? Boolean, OmniFlowPayloads.int(payload["total_count"]),
                    OmniFlowPayloads.int(payload["next_offset"]), offset, items.size, merged.size, PAGE_SIZE)
                runLogsOffset = cursor.nextOffset
                mutableState.update { it.copy(runLogs = OmniFlowList(merged, loaded = true, hasMore = cursor.hasMore)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(runLogs = it.runLogs.copy(loaded = true, loading = false, error = message(error))) }
            }
        }
    }

    /**
     * Opens the Function a RunLog page or tool card named. Dart looked only
     * in the first loaded page, so an older Function never opened; it is
     * fetched directly when the list does not have it.
     */
    private fun openInitialFunction() {
        val functionId = initialFunctionId?.takeIf { it.isNotEmpty() } ?: return
        if (savedState.get<Boolean>(KEY_INITIAL_OPENED) == true) return
        savedState[KEY_INITIAL_OPENED] = true
        val listed = mutableState.value.functions.items.firstOrNull { it.id == functionId }
        if (listed != null) openFunction(listed) else openFunction(OmniFlowFunction(functionId, "", "", emptyList(), emptyList(), true, ""))
    }

    private fun openFunction(function: OmniFlowFunction, sync: Boolean = true) {
        mutableState.update { it.copy(sheet = FunctionSheetState(function, syncing = sync && function.id.isNotEmpty())) }
        if (sync && function.id.isNotEmpty()) syncFunction(function.id)
    }

    private fun syncFunction(functionId: String) {
        viewModelScope.launch {
            val detail = try {
                val payload = repository.getFunction(functionId)
                val nested = OmniFlowPayloads.map(payload["function"]).ifEmpty { OmniFlowPayloads.map(payload["spec"]) }
                    .ifEmpty { payload }
                rawFunctions[functionId] = (rawFunctions[functionId].orEmpty() + nested)
                OmniFlowPayloads.function(rawFunctions.getValue(functionId))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OmniLog.w(TAG, "get_function failed: ${error.javaClass.simpleName}")
                null
            }
            mutableState.update { state ->
                val sheet = state.sheet?.takeIf { it.function.id == functionId } ?: return@update state
                state.copy(sheet = if (detail != null) FunctionSheetState(detail) else sheet.copy(syncing = false, syncFailed = true))
            }
        }
    }

    private fun run(function: OmniFlowFunction) {
        if (function.id.isEmpty() || OmniFlowReplays.running.value != null) return
        if (!permissions.isAccessibilityReady()) {
            pendingReplay = function
            mutableState.update { it.copy(accessibilityNeeded = true) }
            return
        }
        collectArguments(function)
    }

    private fun collectArguments(function: OmniFlowFunction) {
        if (function.parameters.isEmpty()) replay(function, emptyMap())
        else mutableState.update { it.copy(arguments = ArgumentsRequest(function)) }
    }

    private fun confirmArguments() {
        val request = mutableState.value.arguments ?: return
        when (val parsed = parseFunctionArguments(request.function.parameters, request.values)) {
            is ParsedArguments.Invalid -> mutableState.update { it.copy(arguments = request.copy(errors = parsed.errors)) }
            is ParsedArguments.Valid -> {
                mutableState.update { it.copy(arguments = null) }
                replay(request.function, parsed.values)
            }
        }
    }

    private fun replay(function: OmniFlowFunction, arguments: Map<String, Any?>) {
        val goal = replayGoal(function.name, function.description, function.id, arguments)
        val started = OmniFlowReplays.start(appContext, function.id, arguments, goal) { result ->
            notice(result.fold(
                onSuccess = { payload ->
                    OmniFlowPayloads.failure(payload)?.let { OmniFlowNotice(R.string.omni_of_failed, it) }
                        ?: OmniFlowNotice(R.string.omni_of_run_done)
                },
                onFailure = { OmniFlowNotice(R.string.omni_of_failed, message(it)) },
            ))
            loadRunLogs(reset = true)
        }
        if (started) mutableState.update { it.copy(sheet = null) }
    }

    private fun enhance(function: OmniFlowFunction) {
        val raw = rawFunctions[function.id]
        if (raw == null || function.sourceRunId.isEmpty()) {
            notice(OmniFlowNotice(R.string.omni_of_no_source_run))
            return
        }
        mutableState.update { it.copy(sheet = null) }
        // Enhancement is an Agent turn: the chat shows its reasoning and tool activity like any request.
        mutableEnhanceDraft.value = functionEnhancementPrompt(raw)
    }

    private fun delete() {
        val function = mutableState.value.confirmDelete ?: return
        mutableState.update { it.copy(confirmDelete = null) }
        viewModelScope.launch {
            try {
                val payload = repository.deleteFunction(function.id)
                OmniFlowPayloads.failure(payload)?.let(::error)
                rawFunctions.remove(function.id)
                mutableState.update { state ->
                    state.copy(
                        sheet = state.sheet?.takeUnless { it.function.id == function.id },
                        functions = state.functions.copy(items = state.functions.items.filterNot { it.id == function.id }),
                        notice = OmniFlowNotice(R.string.omni_of_deleted),
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notice(OmniFlowNotice(R.string.omni_of_failed, message(error)))
            }
        }
    }

    /** Compiles a successful RunLog into a Function and opens it. */
    private fun register(run: RunLogSummary) {
        if (run.runId.isEmpty() || run.runId in mutableState.value.registeringRunIds) return
        mutableState.update { it.copy(registeringRunIds = it.registeringRunIds + run.runId) }
        viewModelScope.launch {
            try {
                val registration = registeredFunction(repository.register(run.runId), run.runId)
                rawFunctions[registration.id] = rawFunctions[registration.id].orEmpty() +
                    mapOf("function_id" to registration.id, "source_run_id" to registration.sourceRunId)
                val function = registration.copy(name = registration.name.ifEmpty { run.goal },
                    description = registration.description.ifEmpty { run.goal })
                mutableState.update { state ->
                    state.copy(
                        tab = OmniFlowTab.Functions,
                        functions = state.functions.copy(items = mergeById(listOf(function), state.functions.items) { it.id }),
                    )
                }
                savedState[KEY_TAB] = OmniFlowTab.Functions
                openFunction(function)
                loadFunctions(reset = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                notice(OmniFlowNotice(R.string.omni_of_failed, message(error)))
            } finally {
                mutableState.update { it.copy(registeringRunIds = it.registeringRunIds - run.runId) }
            }
        }
    }

    private fun notice(notice: OmniFlowNotice) = mutableState.update { it.copy(notice = notice) }

    private fun message(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    val actions = ExecutionCenterActions(
        setTab = { tab ->
            savedState[KEY_TAB] = tab
            mutableState.update { it.copy(tab = tab) }
            if (tab == OmniFlowTab.RunLogs && !mutableState.value.runLogs.loaded) loadRunLogs(reset = true)
        },
        refresh = {
            if (mutableState.value.availability == OmniFlowAvailability.Ready) refreshLists() else loadAvailability()
        },
        loadMore = { tab -> if (tab == OmniFlowTab.Functions) loadFunctions(reset = false) else loadRunLogs(reset = false) },
        enablePlugin = {
            mutableState.update { it.copy(enablingPlugin = true) }
            viewModelScope.launch {
                try {
                    repository.enablePlugin()
                    loadAvailability()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    notice(OmniFlowNotice(R.string.omni_of_failed, message(error)))
                } finally {
                    mutableState.update { it.copy(enablingPlugin = false) }
                }
            }
        },
        openFunction = { openFunction(it) },
        closeFunction = { mutableState.update { it.copy(sheet = null) } },
        retryFunction = {
            mutableState.value.sheet?.function?.let { function ->
                mutableState.update { it.copy(sheet = FunctionSheetState(function, syncing = true)) }
                syncFunction(function.id)
            }
        },
        run = ::run,
        setArgument = { name, value ->
            mutableState.update { state ->
                val request = state.arguments ?: return@update state
                state.copy(arguments = request.copy(values = request.values + (name to value), errors = request.errors - name))
            }
        },
        confirmArguments = ::confirmArguments,
        dismissArguments = { mutableState.update { it.copy(arguments = null) } },
        openAccessibilitySettings = { permissions.openAccessibilitySettings() },
        dismissAccessibility = {
            pendingReplay = null
            mutableState.update { it.copy(accessibilityNeeded = false) }
        },
        enhance = ::enhance,
        requestDelete = { function -> mutableState.update { it.copy(confirmDelete = function) } },
        confirmDelete = ::delete,
        registerRunLog = ::register,
        dismissNotice = { mutableState.update { it.copy(notice = null) } },
    )

    class Factory(private val context: Context, private val tab: OmniFlowTab, private val functionId: String?) :
        ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            NativeOmniFlowViewModel(context, tab, functionId, extras.createSavedStateHandle()) as T
    }

    private companion object {
        const val TAG = "NativeOmniFlow"
        const val PAGE_SIZE = 20
        const val KEY_TAB = "tab"
        const val KEY_INITIAL_OPENED = "initial_function_opened"
    }
}

/**
 * The Function a `save_function` call produced (Dart
 * `OmniFlowFunctionRegistrationResult`): the official bridge returns
 * `functions`/`function_ids`, older builds a single `function`.
 */
internal fun registeredFunction(payload: Map<String, Any?>, runId: String): OmniFlowFunction {
    OmniFlowPayloads.failure(payload)?.let(::error)
    val nested = OmniFlowPayloads.map(payload["function"]).ifEmpty { OmniFlowPayloads.maps(payload["functions"]).firstOrNull().orEmpty() }
    val firstId = (payload["function_ids"] as? List<*>)?.firstOrNull()
    val functionId = listOf(nested["function_id"], payload["function_id"], payload["registered_function_id"], firstId)
        .map(OmniFlowPayloads::text).firstOrNull { it.isNotEmpty() }
        ?: error("save_function returned no function_id")
    val function = OmniFlowPayloads.function(nested + ("function_id" to functionId))
        ?: OmniFlowFunction(functionId, "", "", emptyList(), emptyList(), true, runId)
    return if (function.sourceRunId.isEmpty()) function.copy(sourceRunId = runId) else function
}
