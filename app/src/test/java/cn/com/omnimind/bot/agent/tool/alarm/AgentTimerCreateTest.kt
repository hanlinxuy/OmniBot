package cn.com.omnimind.bot.agent

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.provider.AlarmClock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentCaptor
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AgentTimerCreateTest {
    @Test fun validatesDurationAtBothBoundaries() {
        assertEquals(1, parseTimerDuration(1L))
        assertEquals(Int.MAX_VALUE, parseTimerDuration(Json.parseToJsonElement("2147483647")))
        listOf("0", "-1", "1.5", "2147483648", "\"10\"", "true", "null").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseTimerDuration(Json.parseToJsonElement(it)) }
        }
        assertThrows(IllegalArgumentException::class.java) { parseTimerDuration(null) }
    }

    @Test fun dispatchesSystemTimerWithoutClaimingItIsRunning() {
        val context = Mockito.mock(Context::class.java)
        val pm = Mockito.mock(PackageManager::class.java)
        val resolved = ResolveInfo().apply { activityInfo = ActivityInfo().apply { packageName = "clock.test"; name = "ClockActivity"; applicationInfo = ApplicationInfo().apply { packageName = "clock.test" } } }
        Mockito.`when`(context.packageManager).thenReturn(pm)
        Mockito.`when`(pm.resolveActivity(Mockito.any(Intent::class.java), Mockito.anyInt())).thenReturn(resolved)
        val result = AgentAlarmToolService(context).createSystemTimer(1500, "测试")
        val captor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(context).startActivity(captor.capture())
        val intent = captor.value
        assertEquals(AlarmClock.ACTION_SET_TIMER, intent.action)
        assertEquals(1500, intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0))
        assertEquals("测试", intent.getStringExtra(AlarmClock.EXTRA_MESSAGE))
        assertTrue(intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false))
        assertFalse(result["creationVerified"] as Boolean)
    }

    @Test fun agentDefinitionRegistersTimer() {
        val function = AgentToolDefinitions.timerCreateTool.getValue("function").jsonObject
        assertEquals("timer_create", function.getValue("name").jsonPrimitive.content)
        val duration = function.getValue("parameters").jsonObject.getValue("properties").jsonObject.getValue("durationSeconds").jsonObject
        assertEquals("integer", duration.getValue("type").jsonPrimitive.content)
    }
}
