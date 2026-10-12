package cn.com.omnimind.bot.ui.omniflow

import cn.com.omnimind.nativeui.omniflow.RunStatus
import cn.com.omnimind.nativeui.omniflow.ScreenPoint
import cn.com.omnimind.nativeui.omniflow.StepAction
import cn.com.omnimind.nativeui.omniflow.StepSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OmniFlowPayloadsTest {
    @Test fun runMetricsFallBackLikeTheDartPage() {
        val run = OmniFlowPayloads.summary(mapOf(
            "run_id" to "r1", "goal" to "Send a message", "status" to "completed",
            "started_at_ms" to 1_000L, "finished_at_ms" to 4_500L,
            "diagnostics" to mapOf("token_usage" to mapOf("prompt_tokens" to 1200, "completion_tokens" to 300,
                "resolved_models" to listOf("vlm-a", "vlm-b")),
                "token_usage_by_call" to listOf(mapOf("n" to 1), mapOf("n" to 2))),
        ))
        assertEquals(RunStatus.Succeeded, run.status)
        assertEquals(3_500L, run.durationMs)
        // No total: prompt + completion.
        assertEquals(1_500, run.tokens.total)
        assertEquals("vlm-a, vlm-b", run.model)
        assertEquals(2, run.callCount)
    }

    @Test fun stepsCarryEvidenceUsageAndTheTapPoint() {
        val detail = OmniFlowPayloads.detail(mapOf(
            "run_id" to "r1", "status" to "success",
            "diagnostics" to mapOf("token_usage_by_step" to listOf(mapOf("step_index" to 0,
                "token_usage" to mapOf("total_tokens" to 42)))),
            "steps" to listOf(
                mapOf("step_index" to 0, "action" to mapOf("tool" to "click", "args" to mapOf("x" to "540", "y" to 1200)),
                    "observation" to mapOf("auxiliaries" to mapOf("state_id" to "s-before")),
                    "metadata" to mapOf("source" to "vlm", "summary" to "Tap send")),
                mapOf("action" to mapOf("type" to "wait"), "result" to mapOf("success" to false, "message" to "Timed out"),
                    "after_state_id" to "s-after"),
            ),
        ))
        val (tap, wait) = detail.steps
        assertEquals(StepAction.Tap, tap.action)
        assertEquals(StepSource.Automatic, tap.source)
        assertEquals("s-before", tap.stateId)
        assertEquals(ScreenPoint(540f, 1200f), tap.point)
        assertEquals(42, tap.tokens?.total)
        assertTrue(tap.actionJson.contains("\"tool\": \"click\""))
        assertFalse(wait.success)
        assertEquals("Timed out", wait.summary)
        assertEquals("s-after", wait.stateId)
        assertNull(wait.tokens)
    }

    @Test fun aFunctionReadsItsSchemaAndSteps() {
        val function = OmniFlowPayloads.functionDetail(mapOf("spec" to mapOf(
            "function_id" to "f1", "name" to "Send", "agent_visible" to false, "source_run_id" to "r1",
            "input_schema" to mapOf("properties" to mapOf("to" to mapOf("type" to "string", "description" to "Contact")),
                "required" to listOf("to")),
            "steps" to listOf(mapOf("action" to mapOf("tool" to "input_text", "args" to mapOf("text" to "{to}")))),
        )))!!
        assertEquals("f1", function.id)
        assertFalse(function.agentVisible)
        assertTrue(function.parameters.single().required)
        assertEquals(StepAction.EnterText, function.steps.single().action)
        assertEquals("""{"text":"{to}"}""", function.steps.single().argsJson)
    }

    @Test fun registrationAcceptsEveryResponseShape() {
        assertEquals("f1", registeredFunction(mapOf("functions" to listOf(mapOf("function_id" to "f1"))), "r1").id)
        assertEquals("f2", registeredFunction(mapOf("function_ids" to listOf("f2")), "r1").id)
        assertEquals("f3", registeredFunction(mapOf("function" to mapOf("function_id" to "f3", "name" to "N")), "r1").id)
        // The source run is filled in so the new Function can be enhanced.
        assertEquals("r1", registeredFunction(mapOf("function_ids" to listOf("f2")), "r1").sourceRunId)
    }

    @Test fun aFailedRegistrationKeepsTheToolMessage() {
        val error = runCatching { registeredFunction(mapOf("success" to false, "error_code" to "NO_STEPS"), "r1") }
        assertEquals("NO_STEPS", error.exceptionOrNull()?.message)
        val missing = runCatching { registeredFunction(mapOf("success" to true), "r1") }
        assertTrue(missing.isFailure)
    }

    @Test fun enhancementSendsTheFunctionWithoutItsUiProvenance() {
        val prompt = functionEnhancementPrompt(mapOf("function_id" to "f1", "source_run_id" to "r1", "name" to "Send"))
        assertTrue(prompt.contains("run_id=r1"))
        assertTrue(prompt.contains("""function: {"function_id":"f1","name":"Send"}"""))
    }

    @Test fun replayGoalNamesTheArguments() {
        assertEquals("Send\nTo a contact\n参数: {\"to\":\"Ann\"}", replayGoal("Send", "To a contact", "f1", mapOf("to" to "Ann")))
        assertEquals("f1", replayGoal("", "", "f1", emptyMap()))
    }
}
