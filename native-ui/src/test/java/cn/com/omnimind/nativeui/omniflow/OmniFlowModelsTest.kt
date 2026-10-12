package cn.com.omnimind.nativeui.omniflow

import cn.com.omnimind.nativeui.LegacyDestination
import cn.com.omnimind.nativeui.nativeDestinationForRoute
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OmniFlowModelsTest {
    private fun parameter(name: String, type: String, required: Boolean = false) = FunctionParameter(name, type, "", required)

    @Test fun argumentsTakeTheSchemaTypes() {
        val parsed = parseFunctionArguments(
            listOf(parameter("count", "integer"), parameter("ratio", "number"), parameter("on", "boolean"),
                parameter("filter", "object"), parameter("note", "string")),
            mapOf("count" to "3", "ratio" to "0.5", "on" to "No", "filter" to """{"a":1}""", "note" to " hi "),
        )
        assertEquals(ParsedArguments.Valid(mapOf("count" to 3L, "ratio" to 0.5, "on" to false,
            "filter" to mapOf("a" to 1), "note" to "hi")), parsed)
    }

    @Test fun badArgumentsNameTheFieldInsteadOfFailingSilently() {
        // Dart ignored the tap on an empty required field and threw on bad JSON; "maybe" became false.
        val parsed = parseFunctionArguments(
            listOf(parameter("app", "string", required = true), parameter("filter", "object"), parameter("on", "boolean"),
                parameter("count", "integer"), parameter("optional", "string")),
            mapOf("filter" to "{oops", "on" to "maybe", "count" to "2.5"),
        )
        assertEquals(ParsedArguments.Invalid(mapOf("app" to ArgumentError.Required, "filter" to ArgumentError.InvalidJson,
            "on" to ArgumentError.NotABoolean, "count" to ArgumentError.NotAnInteger)), parsed)
    }

    @Test fun anObjectArgumentMustBeAnObject() {
        val parsed = parseFunctionArguments(listOf(parameter("filter", "object")), mapOf("filter" to "[1]"))
        assertEquals(ParsedArguments.Invalid(mapOf("filter" to ArgumentError.InvalidJson)), parsed)
    }

    @Test fun statusWordsAndTheSuccessFlag() {
        assertEquals(RunStatus.Succeeded, runStatus("completed"))
        assertEquals(RunStatus.Failed, runStatus("completed", success = false))
        assertEquals(RunStatus.Running, runStatus("pending"))
        assertEquals(RunStatus.Cancelled, runStatus("canceled"))
        assertEquals(RunStatus.Failed, runStatus(null, success = false))
        assertEquals(RunStatus.Unknown, runStatus("queued"))
    }

    @Test fun stepTargetsReadTheActionArguments() {
        assertEquals("com.tencent.mm", stepTarget("open_app", mapOf("package_name" to "com.tencent.mm")))
        assertEquals("540, 1200", stepTarget("click", mapOf("x" to 540, "y" to 1200)))
        assertEquals("1, 2 → 3, 4", stepTarget("swipe", mapOf("x1" to 1, "y1" to 2, "x2" to 3, "y2" to 4)))
        assertEquals("up", stepTarget("swipe", mapOf("direction" to "up", "x1" to 1)))
        assertEquals("800 ms", stepTarget("wait", mapOf("duration_ms" to 800)))
        assertEquals("", stepTarget("scroll_to", mapOf("x" to 1)))
        assertEquals(StepAction.Other, stepAction("scroll_to"))
    }

    @Test fun pagingFollowsTheExplicitCursorThenTheTotal() {
        assertEquals(PageCursor(40, false), pageCursor(false, null, 40, 20, 20, 40, 20))
        assertEquals(PageCursor(30, true), pageCursor(null, 50, null, 20, 10, 30, 20))
        assertEquals(PageCursor(20, true), pageCursor(null, null, -1, 0, 20, 20, 20))
        assertEquals(PageCursor(5, false), pageCursor(null, null, null, 0, 5, 5, 20))
    }

    @Test fun aShiftedPageDoesNotRepeatRows() {
        assertEquals(listOf("a", "b", "c"), mergeById(listOf("a", "b"), listOf("b", "c")) { it })
    }

    @Test fun aRunLinksToTheFunctionItReplayedOrProduced() {
        fun function(id: String, source: String) = OmniFlowFunction(id, "", "", emptyList(), emptyList(), true, source)
        val functions = listOf(function("f1", "r1"), function("f2", ""))
        assertEquals("f1", linkedFunction(RunLogSummary("r1", "", RunStatus.Succeeded), functions)?.id)
        assertEquals("f2", linkedFunction(RunLogSummary("r9", "", RunStatus.Succeeded, functionId = "f2"), functions)?.id)
        assertNull(linkedFunction(RunLogSummary("r9", "", RunStatus.Succeeded), functions))
    }

    @Test fun theTapMarkerScalesWithTheScreenshot() {
        // Dart drew it at the raw 1080-px coordinates over a ~360-dp image.
        assertEquals(ScreenPoint(180f, 400f), scaledPoint(ScreenPoint(540f, 1200f), 1080, 2400, 360f, 800f))
        assertNull(scaledPoint(ScreenPoint(2000f, 10f), 1080, 2400, 360f, 800f))
    }

    @Test fun formatting() {
        assertEquals("950 ms", formatRunDuration(950))
        assertEquals("1.5 s", formatRunDuration(1500))
        assertEquals("2 m 5.25 s", formatRunDuration(125_250))
        assertEquals("1.20k", formatRunTokens(1200))
        assertEquals("3.00M", formatRunTokens(3_000_000))
        assertEquals("2026-10-12 08:00:00", formatRunTimestamp(1_791_792_000_000, ZoneOffset.UTC))
    }

    @Test fun inAppRoutesToTheNativePages() {
        assertEquals(LegacyDestination.OmniFlow(OmniFlowTab.RunLogs), nativeDestinationForRoute("/task/omniflow?tab=run_logs"))
        assertEquals(LegacyDestination.OmniFlow(functionId = "fn 1"), nativeDestinationForRoute("/task/omniflow?functionId=fn%201"))
        assertEquals(LegacyDestination.OmniFlow(OmniFlowTab.RunLogs), nativeDestinationForRoute("/task/run_logs"))
        assertEquals(LegacyDestination.RunLog("run 7"), nativeDestinationForRoute("/task/run_log/run%207"))
        // Already native before 5n, but tool cards still opened Flutter for it.
        assertEquals(LegacyDestination.ScheduledTasks(alarms = true), nativeDestinationForRoute("/task/scheduled_tasks?tab=alarm"))
        assertNull(nativeDestinationForRoute("/task/run_log/"))
        assertNull(nativeDestinationForRoute("/home/termux_setting"))
    }
}
