package cn.com.omnimind.bot.agent

import android.content.Intent
import android.provider.AlarmClock
import org.robolectric.RuntimeEnvironment
import org.mockito.Mockito
import org.mockito.ArgumentCaptor
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import org.robolectric.Shadows.shadowOf
import android.content.Context
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
class AgentAlarmRepeatDaysTest {
    @Test fun validatesBothEntryRepresentations() {
        assertEquals(listOf(1, 7), parseAlarmRepeatDays(listOf(1L, 7L)))
        assertEquals(listOf(1, 7), parseAlarmRepeatDays(Json.parseToJsonElement("[1,7]")))
        assertEquals(emptyList<Int>(), parseAlarmRepeatDays(null))
        listOf("[0]", "[8]", "[1,1]", "[1.5]", "[\"1\"]", "true").forEach {
            assertThrows(IllegalArgumentException::class.java) { parseAlarmRepeatDays(Json.parseToJsonElement(it)) }
        }
    }

    @Test fun rejectsRepeatForExactAlarmBeforeScheduling() {
        val context = RuntimeEnvironment.getApplication()
        val request = AgentAlarmCreateRequest("exact_alarm", "test", "2030-01-01T07:00:00", null, null, true, false, listOf(1))
        assertThrows(IllegalArgumentException::class.java) { AgentAlarmToolService(context).createReminder(request) }
    }

    @Test fun clockIntentUsesAndroidWeekdaysAndDoesNotClaimCreation() {
        val context = Mockito.mock(Context::class.java)
        val packageManager = Mockito.mock(PackageManager::class.java)
        val resolved = ResolveInfo().apply { activityInfo = android.content.pm.ActivityInfo().apply { packageName = "clock.test"; name = "ClockActivity"; applicationInfo = android.content.pm.ApplicationInfo().apply { packageName = "clock.test" } } }
        Mockito.`when`(context.packageManager).thenReturn(packageManager)
        Mockito.`when`(packageManager.resolveActivity(Mockito.any(Intent::class.java), Mockito.anyInt())).thenReturn(resolved)
        val request = AgentAlarmCreateRequest("clock_app", "test", "2030-01-01T07:00:00", null, null, true, false, listOf(1, 7))
        val result = AgentAlarmToolService(context).createReminder(request)
        val captor = ArgumentCaptor.forClass(Intent::class.java)
        Mockito.verify(context).startActivity(captor.capture())
        val intent = captor.value
        assertEquals(arrayListOf(Calendar.MONDAY, Calendar.SUNDAY), intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS))
        assertFalse(result["creationVerified"] as Boolean)
    }
}
