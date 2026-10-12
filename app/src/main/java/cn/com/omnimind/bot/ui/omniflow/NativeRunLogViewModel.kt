package cn.com.omnimind.bot.ui.omniflow

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import cn.com.omnimind.nativeui.R
import cn.com.omnimind.nativeui.omniflow.OmniFlowFunction
import cn.com.omnimind.nativeui.omniflow.OmniFlowNotice
import cn.com.omnimind.nativeui.omniflow.RunLogActions
import cn.com.omnimind.nativeui.omniflow.RunLogPageState
import cn.com.omnimind.nativeui.omniflow.RunLogSummary
import cn.com.omnimind.nativeui.omniflow.linkedFunction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One RunLog page (batch 5n). */
internal class NativeRunLogViewModel(context: Context, private val runId: String) : ViewModel() {
    private val repository = NativeOmniFlowRepository(context)
    private val mutableState = MutableStateFlow(RunLogPageState())
    val state: StateFlow<RunLogPageState> = mutableState.asStateFlow()
    private var loaded = false

    fun load() {
        if (loaded) return
        loaded = true
        refresh()
    }

    private fun refresh() {
        mutableState.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val linked = async { findLinkedFunction() }
                val detail = OmniFlowPayloads.detail(repository.getRunLog(runId))
                mutableState.update { it.copy(loading = false, detail = detail, linkedFunction = linked.await()(detail.summary)) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(loading = false, error = message(error)) }
            }
        }
    }

    /**
     * Pages through the Functions until one links to this run. Dart searched
     * only the first 100, so an older run never showed its Function.
     */
    private suspend fun findLinkedFunction(): (RunLogSummary) -> OmniFlowFunction? {
        val functions = ArrayList<OmniFlowFunction>()
        var offset = 0
        repeat(MAX_PAGES) {
            val payload = repository.listFunctions(PAGE_SIZE, offset)
            val page = OmniFlowPayloads.functions(payload)
            functions += page
            if (functions.any { it.sourceRunId == runId } || page.size < PAGE_SIZE || payload["has_more"] == false) {
                return { summary -> linkedFunction(summary, functions) }
            }
            offset = OmniFlowPayloads.int(payload["next_offset"]) ?: (offset + page.size)
        }
        return { summary -> linkedFunction(summary, functions) }
    }

    private fun register() {
        if (mutableState.value.registering) return
        mutableState.update { it.copy(registering = true) }
        viewModelScope.launch {
            try {
                val function = registeredFunction(repository.register(runId), runId)
                mutableState.update { it.copy(linkedFunction = function) }
                mutableOpenFunction.value = function.id
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableState.update { it.copy(notice = OmniFlowNotice(R.string.omni_of_failed, message(error))) }
            } finally {
                mutableState.update { it.copy(registering = false) }
            }
        }
    }

    private val mutableOpenFunction = MutableStateFlow<String?>(null)
    /** One-shot: the route opens the execution center on this Function. */
    val openFunction: StateFlow<String?> = mutableOpenFunction.asStateFlow()

    fun consumeOpenFunction() {
        mutableOpenFunction.value = null
    }

    private fun message(error: Throwable): String = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName

    val actions = RunLogActions(
        refresh = ::refresh,
        register = ::register,
        openFunction = { mutableOpenFunction.value = mutableState.value.linkedFunction?.id },
        openStep = { index -> mutableState.update { it.copy(openStep = index, screen = null) } },
        showScreen = { step ->
            val stateId = step.stateId
            if (stateId != null && !mutableState.value.screenLoading) {
                mutableState.update { it.copy(screenLoading = true) }
                viewModelScope.launch {
                    try {
                        val screen = OmniFlowPayloads.screenState(repository.getRunLogState(stateId))
                        mutableState.update { it.copy(screen = screen) }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        mutableState.update { it.copy(notice = OmniFlowNotice(R.string.omni_of_failed, message(error))) }
                    } finally {
                        mutableState.update { it.copy(screenLoading = false) }
                    }
                }
            }
        },
        closeScreen = { mutableState.update { it.copy(screen = null) } },
        dismissNotice = { mutableState.update { it.copy(notice = null) } },
    )

    class Factory(private val context: Context, private val runId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            NativeRunLogViewModel(context, runId) as T
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 10
    }
}
