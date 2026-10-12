package cn.com.omnimind.bot.omniflow

import android.content.Context
import cn.com.omnimind.bot.agent.HttpAgentLlmClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext

/**
 * One OmniFlow host tool call, as the execution center makes it: the Flutter
 * `tools/call` channel and the native pages (5n) both go through here, so a
 * plain `save_function` registers the stored RunLog the same way for both.
 */
internal object OmniFlowHostTools {
    const val SAVE_FUNCTION = "save_function"

    suspend fun call(context: Context, name: String, arguments: Map<String, Any?>, goal: String = ""): Map<String, Any?> {
        val modelClient = if (OmniFlowPluginRuntime.isEnabled()) {
            HttpAgentLlmClient(CoroutineScope(currentCoroutineContext())).asOmniFlowModelClient()
        } else {
            null
        }
        val registersStoredRunLog = name == SAVE_FUNCTION && arguments["function"] == null &&
            arguments["functions"] == null && arguments["run_log"] == null && arguments["enhance"] != true
        return if (registersStoredRunLog) {
            OmniFlowFunctionRegistration.saveRunLog(
                context = context.applicationContext,
                runId = arguments["run_id"]?.toString().orEmpty(),
                agentVisible = arguments["agent_visible"] != false,
                modelClient = modelClient,
            )
        } else {
            OmniFlow.callTool(
                context = context.applicationContext,
                toolCall = OmniFlow.ToolCall(name, arguments),
                goal = goal.ifBlank { name },
                modelClient = modelClient,
            ).payload
        }
    }
}
