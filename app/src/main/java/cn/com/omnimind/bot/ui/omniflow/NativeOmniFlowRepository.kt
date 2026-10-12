package cn.com.omnimind.bot.ui.omniflow

import android.content.Context
import cn.com.omnimind.baselib.util.OmniLog
import cn.com.omnimind.bot.omniflow.OmniFlowHostTools
import cn.com.omnimind.bot.plugin.NativePluginRepository
import cn.com.omnimind.bot.plugin.OmniPluginState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The OmniFlow tools the native pages call (Dart `OmniFlowToolClient`), on
 * the same host path as the Flutter channel ([OmniFlowHostTools]).
 */
internal class NativeOmniFlowRepository(context: Context) {
    private val appContext = context.applicationContext
    private val plugins = NativePluginRepository(appContext)

    suspend fun plugin(): OmniPluginState? = plugins.get(PLUGIN_ID)

    suspend fun enablePlugin(): OmniPluginState = plugins.setEnabled(PLUGIN_ID, true)

    suspend fun listFunctions(limit: Int, offset: Int) = call("list_functions", mapOf("limit" to limit, "offset" to offset))

    suspend fun listRunLogs(limit: Int, offset: Int) = call("list_run_logs", mapOf("limit" to limit, "offset" to offset))

    suspend fun getFunction(functionId: String) = call("get_function", mapOf("function_id" to functionId))

    suspend fun getRunLog(runId: String) = call("get_run_log", mapOf("run_id" to runId))

    suspend fun getRunLogState(stateId: String) = call("get_run_log_state", mapOf("state_id" to stateId))

    /** Compiles a stored RunLog into a Function (the official writer). */
    suspend fun register(runId: String) = call(OmniFlowHostTools.SAVE_FUNCTION, mapOf("run_id" to runId))

    suspend fun deleteFunction(functionId: String) = call("delete_function", mapOf("function_id" to functionId))

    private suspend fun call(name: String, arguments: Map<String, Any?>, goal: String = ""): Map<String, Any?> =
        withContext(Dispatchers.IO) { OmniFlowHostTools.call(appContext, name, arguments, goal) }

    companion object {
        const val PLUGIN_ID = "com.omnimind.omni-vlm-lite"
    }
}

/**
 * Runs one Function replay at a time for the whole process. A replay drives
 * the screen; Dart let a second Run start while one was still going (both
 * then fought over the same screen) and showed no running state. Every
 * execution center page now shows the same running Function and disables Run.
 */
internal object OmniFlowReplays {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutableRunning = MutableStateFlow<String?>(null)
    val running: StateFlow<String?> = mutableRunning.asStateFlow()

    /** Starts [functionId] unless a replay is running; [onDone] gets the payload or the error, on the main thread. */
    fun start(context: Context, functionId: String, arguments: Map<String, Any?>, goal: String,
              onDone: (Result<Map<String, Any?>>) -> Unit): Boolean {
        if (!mutableRunning.compareAndSet(null, functionId)) return false
        val appContext = context.applicationContext
        scope.launch {
            val result = try {
                Result.success(OmniFlowHostTools.call(appContext, functionId, arguments, goal))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                OmniLog.w("OmniFlowReplays", "Replay failed: ${error.javaClass.simpleName}")
                Result.failure(error)
            } finally {
                mutableRunning.value = null
            }
            withContext(Dispatchers.Main) { onDone(result) }
        }
        return true
    }
}
