package cn.com.omnimind.bot.omniflow.ui

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExecutionOverlayHostPolicyTest {
    @Test
    fun `only actual screen bounds contain the target`() {
        assertTrue(executionOverlayContainsPoint(100f, 2300f, 20, 2200, 1000, 200))
        assertFalse(executionOverlayContainsPoint(100f, 1800f, 20, 2200, 1000, 200))
        assertFalse(executionOverlayContainsPoint(10f, 2300f, 20, 2200, 1000, 200))
        assertFalse(executionOverlayContainsPoint(100f, 2300f, 20, 2200, 0, 200))
    }

    @Test
    fun `accessibility overlay is preferred on protected pages`() {
        val host = ExecutionOverlayHostPolicy.resolve(
            accessibilityServiceAvailable = true,
            applicationOverlayAllowed = true,
        )

        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            host?.windowType,
        )
    }

    @Test
    fun `application overlay remains available as fallback`() {
        val host = ExecutionOverlayHostPolicy.resolve(
            accessibilityServiceAvailable = false,
            applicationOverlayAllowed = true,
        )

        assertEquals(
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            host?.windowType,
        )
    }

    @Test
    fun `overlay is unavailable without either host`() {
        assertNull(
            ExecutionOverlayHostPolicy.resolve(
                accessibilityServiceAvailable = false,
                applicationOverlayAllowed = false,
            ),
        )
    }
}
