package cn.com.omnimind.bot.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AndroidDeviceMcpServerTest {
    @Test
    fun `public MCP surface exposes Android device tools only`() {
        assertTrue(
            AndroidDeviceMcpServer.publicToolNames.containsAll(
                setOf(
                    "run_gui",
                    "run_function",
                    "list_functions",
                    "register_function",
                    "context_apps_query",
                    "context_display_get",
                    "file_transfer",
                    "schedule_task_create",
                    "schedule_task_list",
                    "schedule_task_update",
                    "schedule_task_delete",
                    "timer_create",
                    "alarm_reminder_create",
                    "alarm_reminder_list",
                    "alarm_reminder_delete",
                    "calendar_list",
                    "calendar_event_create",
                    "calendar_event_list",
                    "calendar_event_update",
                    "calendar_event_delete",
                    "context_capabilities",
                    "contacts_query",
                    "contacts_create",
                    "contacts_update",
                    "contacts_delete",
                    "context_notifications_query",
                    "context_app_usage_query",
                    "context_location_get",
                    "context_media_query",
                    "context_documents_query",
                    "context_file_import",
                    "context_sms_query",
                    "context_call_log_query",
                    "context_environment_get",
                ),
            ),
        )
        assertFalse(AndroidDeviceMcpServer.publicToolNames.any { it in setOf(
            "context_time_now",
            "browser_use",
            "file_read",
            "file_write",
            "file_edit",
            "file_list",
            "file_search",
            "file_stat",
            "file_move",
            "skills_list",
            "skills_read",
            "music_playback_control",
            "memory_search",
            "memory_write_daily",
            "memory_upsert_longterm",
            "memory_rollup_day",
            "memory_load",
            "subagent_dispatch",
        ) })
        assertFalse(AndroidDeviceMcpServer.publicToolNames.any { it.startsWith("device_") })
        assertTrue(AndroidDeviceMcpServer.MCP_INSTRUCTIONS.contains("Android device"))
        assertFalse(AndroidDeviceMcpServer.MCP_INSTRUCTIONS.contains("browser/internet"))
    }

    @Test
    fun `missing default plugin explains how to enable phone control`() = runBlocking {
        var message = ""
        try {
            AndroidDeviceMcpServer.requireDefaultPluginEnabled(
                isEnabled = { false },
                inspect = { null },
            )
        } catch (error: IllegalStateException) {
            message = error.message.orEmpty()
        }

        assertTrue(message.contains("插件市场"))
        assertTrue(message.contains("安装并启用"))
    }

    @Test
    fun `disabled installed default plugin explains how to enable it`() = runBlocking {
        var message = ""
        try {
            AndroidDeviceMcpServer.requireDefaultPluginEnabled(
                isEnabled = { false },
                inspect = {
                    AndroidDeviceMcpServer.DefaultPluginStatus(
                        installed = true,
                        enabled = false,
                    )
                },
            )
        } catch (error: IllegalStateException) {
            message = error.message.orEmpty()
        }

        assertTrue(message.contains("启用插件"))
        assertTrue(message.contains("无障碍服务"))
    }

    @Test
    fun `ready runtime skips plugin inspection`() = runBlocking {
        var inspectionCount = 0

        AndroidDeviceMcpServer.requireDefaultPluginEnabled(
            isEnabled = { true },
            inspect = {
                inspectionCount += 1
                null
            },
        )

        assertEquals(0, inspectionCount)
    }
    @Test
    fun `GUI receipt retains completion ahead of large trace without changing success`() {
        val payload = linkedMapOf<String, Any?>(
            "success" to true, "status" to "succeeded", "run_id" to "gui-owned",
            "steps" to List(26) { mapOf("metadata" to mapOf("thinking" to "x".repeat(2000))) },
            "function_resolution" to mapOf("diagnostics" to "x".repeat(12000)),
            "done_reason" to "finished", "finished_content" to "Saved and reopened the target alarm.",
            "final_state" to mapOf("activity_name" to "com.android.BBKClock.Timer"),
        )
        val result = AndroidDeviceMcpServer.guiResultContext(payload)
        assertEquals(payload["finished_content"], result["finished_content"])
        assertEquals(payload["success"], result["success"])
        assertEquals(payload["final_state"], result["final_state"])
        assertFalse(result.containsKey("steps"))
        assertFalse(result.containsKey("function_resolution"))
        assertTrue(result.toString().length < 2000)
        assertTrue(result["result_interpretation"].toString().contains("not independent"))
        assertTrue(result["result_interpretation"].toString().contains("not evidence of the selected tab"))
        assertEquals(true, result["trace_in_run_log"])
    }

    @Test
    fun `GUI receipt preserves failure and permission action evidence`() {
        val denied = mapOf("action" to mapOf("tool" to "click", "args" to mapOf(
            "node_resource_id" to "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button")),
            "result" to mapOf("success" to true))
        val failure = mapOf("step_index" to 2, "action" to mapOf("tool" to "click"),
            "result" to mapOf("success" to false, "error" to "node_id_not_resolved"),
            "metadata" to mapOf("thinking" to "large private trace"))
        val payload = mapOf("success" to false, "status" to "failed", "run_id" to "gui-owned",
            "error_message" to "Needs user input", "done_reason" to "waiting_input",
            "payment_confirmation_blocked" to true, "steps" to listOf(denied, failure))
        val result = AndroidDeviceMcpServer.guiResultContext(payload)
        assertEquals(false, result["success"])
        assertEquals("waiting_input", result["done_reason"])
        assertEquals("Needs user input", result["error_message"])
        assertEquals(true, result["payment_confirmation_blocked"])
        assertTrue(result.containsKey("permission_action_notice"))
        assertEquals(listOf(failure.filterKeys { it != "metadata" }), result["failed_steps"])
        assertEquals(1, result["failed_steps_total"])
        assertEquals(false, AndroidDeviceMcpServer.guiResultContext(emptyMap())["trace_in_run_log"])
    }

}
