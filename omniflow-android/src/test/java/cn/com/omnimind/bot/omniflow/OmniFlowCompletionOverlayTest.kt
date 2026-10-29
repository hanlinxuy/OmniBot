package cn.com.omnimind.bot.omniflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlinx.coroutines.runBlocking

class OmniFlowCompletionOverlayTest {
    @Test
    fun `successful VLM completion dismisses controls immediately`() {
        assertEquals(
            0L,
            completionOverlayVisibleMs("vlm", mapOf("success" to true)),
        )
    }

    @Test
    fun `failed VLM completion also dismisses controls immediately`() {
        assertEquals(
            0L,
            completionOverlayVisibleMs("vlm", mapOf("success" to false)),
        )
    }

    @Test
    fun `function replay keeps the short completion state`() {
        assertEquals(
            900L,
            completionOverlayVisibleMs("function", mapOf("success" to true)),
        )
    }

    @Test
    fun `manual completion is persisted as successful completion`() {
        val result = manualCompletionResult(
            runId = "gui-1",
            startedAtMs = 1_000L,
            source = "vlm",
            functionId = "run_gui",
            finishedAtMs = 2_500L,
        )

        assertEquals(true, result["success"])
        assertEquals("succeeded", result["status"])
        assertEquals("manual_finished", result["done_reason"])
        assertEquals(1_500L, result["duration_ms"])
    }

    @Test
    fun `screenshot hides controls and restores after capture failure`() = runBlocking {
        val events = mutableListOf<String>()
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                withScreenshotControlsHidden(true, { events += "hide" }, { events += "show" }) {
                    events += "capture"
                    error("capture failed")
                }
            }
        }
        assertEquals(listOf("hide", "capture", "show"), events)
    }

    @Test
    fun `observation without screenshot leaves controls untouched`() = runBlocking {
        val events = mutableListOf<String>()
        withScreenshotControlsHidden(false, { events += "hide" }, { events += "show" }) {
            events += "observe"
        }
        assertEquals(listOf("observe"), events)
    }
}
