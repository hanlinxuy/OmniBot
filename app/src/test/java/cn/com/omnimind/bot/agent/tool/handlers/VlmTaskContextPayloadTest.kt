package cn.com.omnimind.bot.agent.tool.handlers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlmTaskContextPayloadTest {
    @Test
    fun `dispatched permanent permission denial is surfaced for failed and successful runs`() {
        val step = mapOf<String, Any?>(
            "action" to mapOf("tool" to "click", "args" to mapOf(
                "node_resource_id" to "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
            )),
            "result" to mapOf("success" to true),
        )
        val notice = permissionActionContext(listOf(step))
        assertTrue(notice["permission_action_notice"].toString().contains("does not establish"))
        for (success in listOf(false, true)) {
            val payload = buildVlmTaskContextPayload(
                requestedRunId = "r", goal = "read album", resultRunId = "r",
                success = success, doneReason = if (success) "finished" else "failed",
                content = "", finalStateId = null, extras = notice,
            )
            assertEquals(notice["permission_action_notice"], payload["permission_action_notice"])
        }
        assertTrue(permissionActionContext(listOf(step + ("result" to mapOf("success" to false)))).isEmpty())
        assertTrue(permissionActionContext(listOf(mapOf("action" to mapOf("tool" to "click"), "result" to mapOf("success" to true)))).isEmpty())
    }

    @Test
    fun `vlm result keeps goal run log and final state context`() {
        val payload = buildVlmTaskContextPayload(
            requestedRunId = "gui-request",
            goal = "添加联系人 Mom",
            resultRunId = "gui-request",
            success = true,
            doneReason = "finished",
            content = "已添加联系人 Mom",
            finalStateId = "state-final",
            finalState = mapOf(
                "state_id" to "state-final",
                "package_name" to "com.android.contacts",
                "activity_name" to ".Contacts",
                "display" to mapOf("width" to 1080, "height" to 2400),
            ),
            stepCount = 7,
        )

        assertEquals("vlm_task_result", payload["context_type"])
        assertEquals("添加联系人 Mom", payload["goal"])
        assertEquals("gui-request", payload["run_log_id"])
        assertEquals(7, payload["step_count"])
        assertEquals(7, payload["action_count"])
        assertEquals("state-final", payload["final_state_id"])
        assertEquals(true, payload["registration_available"])
        assertTrue(payload["registration_hint"].toString().contains("可注册为复用指令"))
        assertTrue(payload["next_agent_instruction"].toString().contains("run_log_id"))
        assertTrue(payload["next_agent_instruction"].toString().contains("Do not claim"))
    }

    @Test
    fun `vlm result reports automatic function registration without suggesting it again`() {
        val payload = buildVlmTaskContextPayload(
            requestedRunId = "gui-request",
            goal = "打开蓝牙",
            resultRunId = "gui-request",
            success = true,
            doneReason = "finished",
            content = "蓝牙已打开",
            finalStateId = "state-final",
            extras = mapOf(
                "auto_registered" to true,
                "registered_function_id" to "enable_bluetooth",
            ),
        )

        assertTrue(payload["registration_hint"].toString().contains("已自动注册"))
        assertTrue(payload["next_agent_instruction"].toString().contains("registered_function_id"))
    }
}
