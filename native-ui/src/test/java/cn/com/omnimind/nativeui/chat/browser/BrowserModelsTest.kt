package cn.com.omnimind.nativeui.chat.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserModelsTest {
    @Test fun addressShowsTheHostThenTheRest() {
        assertEquals(AddressParts("example.com", "/a?b=1"), addressParts("https://www.example.com/a?b=1"))
        assertEquals(AddressParts("10.0.0.2:8080", ""), addressParts("http://10.0.0.2:8080/"))
        assertTrue(isBlankPage("about:blank"))
    }

    /** Fix (5m): Dart turned any text into `https://…`, so "best pizza" opened `https://best pizza`. */
    @Test fun typedWordsSearchAndHostsOpen() {
        assertEquals("https://example.com/x", addressTarget("example.com/x"))
        assertEquals("https://localhost:3000", addressTarget("localhost:3000"))
        assertEquals("http://a.b", addressTarget("http://a.b"))
        assertEquals("https://www.bing.com/search?q=best+pizza", addressTarget("best pizza"))
        assertNull(addressTarget("  "))
        // Non-web schemes are not opened from the bar.
        assertNull(addressTarget("javascript:alert(1)"))
    }

    @Test fun riskFollowsTheEngineRecommendation() {
        assertNull(browserRiskFor(false, "anything"))
        assertEquals(BrowserRisk.RateLimited, browserRiskFor(true, "wait_before_retrying_and_reduce_request_rate"))
        assertEquals(BrowserRisk.AccessDenied, browserRiskFor(true, "stop_automatic_retry_and_use_manual_access"))
        assertEquals(BrowserRisk.Challenge, browserRiskFor(true, null))
    }

    @Test fun theWindowStaysOnScreenAndInBounds() {
        val frame = defaultBrowserFrame(400f, 800f)
        assertEquals(BrowserWindowFrame(24f, 64f, 360f, 420f), frame)
        val tooBig = BrowserWindowFrame(-50f, 0f, 2000f, 2000f).clamped(400f, 800f)
        assertEquals(368f, tooBig.width, .01f)
        assertEquals(624f, tooBig.height, .01f)
        assertEquals(16f, tooBig.x, .01f)
        assertEquals(64f, tooBig.y, .01f)
    }
}
