package cn.com.omnimind.bot.ui.chat.browser

import cn.com.omnimind.nativeui.chat.browser.BrowserRisk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserSessionMapperTest {
    @Test fun anUnavailableSnapshotIsNoSession() {
        assertNull(BrowserSessionMapper.map(mapOf("available" to false, "workspaceId" to "conversation_1")))
    }

    @Test fun theEngineSnapshotMapsToTheSession() {
        val session = BrowserSessionMapper.map(mapOf(
            "available" to true, "workspaceId" to "conversation_7", "currentUrl" to "https://a.io/x", "title" to "A",
            "isLoading" to true, "canGoBack" to true, "riskChallengeDetected" to true,
            "recommendedNextAction" to "wait_before_retrying_and_reduce_request_rate",
            "tabs" to listOf(mapOf("tabId" to 1, "url" to "https://a.io/x", "isActive" to true), mapOf("url" to "no id")),
            "downloads" to listOf(mapOf("id" to "d1", "fileName" to "f.zip", "status" to "downloading", "progress" to 1.7,
                "canPause" to true, "errorMessage" to "")),
            "pendingDialog" to mapOf("requestId" to "r1", "type" to "prompt", "message" to "Name?", "defaultValue" to "x"),
            "permissionPrompt" to mapOf("requestId" to "p1", "origin" to "https://a.io", "resources" to listOf("camera")),
        ))!!
        assertEquals("conversation_7", session.workspaceId)
        assertTrue(session.loading && session.canGoBack)
        assertEquals(BrowserRisk.RateLimited, session.risk)
        // A tab without an id is dropped instead of crashing the overlay.
        assertEquals(1, session.tabs.size)
        assertEquals(1f, session.downloads.single().progress!!, .0001f)
        assertNull(session.downloads.single().error)
        assertTrue(session.dialog!!.isPrompt)
        assertEquals(listOf("camera"), session.permission!!.resources)
        assertNull(session.externalOpen)
    }
}
