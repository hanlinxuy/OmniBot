package cn.com.omnimind.bot.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import cn.com.omnimind.bot.agent.HttpAgentLlmClient
import cn.com.omnimind.bot.omniflow.OmniFlow
import cn.com.omnimind.bot.omniflow.OmniFlowPluginRuntime
import cn.com.omnimind.bot.omniflow.OmniFlowPythonRuntime
import cn.com.omnimind.bot.omniflow.asOmniFlowModelClient
import cn.com.omnimind.bot.plugin.official.OmniFlowManagementTools
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch

class DebugOmniFlowToolReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val appContext = context.applicationContext
        Log.i(TAG, "tool_call_received")
        scope.launch {
            val payload = runCatching {
                    val name = intent?.getStringExtra("name")?.trim().orEmpty()
                    require(name.isNotEmpty()) { "tool name is required" }
                    Log.i(TAG, "tool_call_execute name=$name")
                    val argumentsJson = intent?.getStringExtra("argumentsBase64")
                        ?.let { String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8) }
                    val arguments = argumentsJson?.let {
                        gson.fromJson<Map<String, Any?>>(it, mapType)
                    }.orEmpty()
                    when (name) {
                        OmniFlowManagementTools.GET_PYTHON_OVERRIDE -> {
                            val path = arguments["path"]?.toString()?.trim().orEmpty()
                            if (path.isEmpty()) {
                                val status = OmniFlowPythonRuntime.developerOverrideStatus(appContext)
                                mapOf(
                                    "success" to true,
                                    "override_enabled" to status.enabled,
                                    "android_install_directory" to status.androidRoot,
                                    "shell_install_directory" to status.shellRoot,
                                    "runtime_version" to status.runtimeVersion,
                                    "modified_files" to status.modifiedFiles,
                                    "editable_glob" to "omniflow/**/*.py",
                                )
                            } else {
                                OmniFlowPythonRuntime.readDeveloperOverride(appContext, path) +
                                    ("success" to true)
                            }
                        }
                        OmniFlowManagementTools.APPLY_PYTHON_OVERRIDE ->
                            OmniFlowPythonRuntime.applyDeveloperOverride(
                                appContext,
                                arguments["path"]?.toString().orEmpty(),
                                arguments["content"]?.toString().orEmpty(),
                            )
                        OmniFlowManagementTools.RELOAD_PYTHON_OVERRIDE ->
                            OmniFlowPythonRuntime.reloadDeveloperOverride(appContext)
                        else -> OmniFlow.callTool(
                            context = appContext,
                            toolCall = OmniFlow.ToolCall(name, arguments),
                            modelClient = if (OmniFlowPluginRuntime.isEnabled()) {
                                HttpAgentLlmClient(CoroutineScope(currentCoroutineContext()))
                                    .asOmniFlowModelClient()
                            } else {
                                null
                            },
                        ).payload
                    }
                }.getOrElse { error ->
                    linkedMapOf(
                        "success" to false,
                        "error_message" to (error.message ?: error.javaClass.simpleName),
                        "error_type" to error.javaClass.name,
                    )
                }
            File(appContext.filesDir, RESULT_FILE).writeText(gson.toJson(payload))
            Log.i(TAG, "tool_call_finished success=${payload["success"]}")
        }
    }

    private companion object {
        const val RESULT_FILE = "debug-omniflow-tool-result.json"
        const val TAG = "DebugOmniFlowToolReceiver"
        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
