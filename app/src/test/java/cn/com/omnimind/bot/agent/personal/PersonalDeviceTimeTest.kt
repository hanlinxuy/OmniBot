package cn.com.omnimind.bot.agent.personal

import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PersonalDeviceTimeTest {
    @Test fun localDateUsesDeviceZoneAndOneClockSample() {
        val instant = Instant.parse("2030-01-01T17:30:00Z")
        val result = PersonalDeviceContext(
            RuntimeEnvironment.getApplication(),
            Clock.fixed(instant, ZoneId.of("Asia/Shanghai"))
        ).environment()
        assertEquals("2030-01-02", result["localDate"])
        assertEquals("Asia/Shanghai", result["timezone"])
        assertEquals(28800, result["utcOffsetSeconds"])
        assertEquals(instant.toEpochMilli(), result["sampledAt"])
        assertEquals(instant, OffsetDateTime.parse(result["currentTime"] as String).toInstant())
    }

    @Test fun daylightSavingOffsetComesFromTheSampledInstant() {
        val zone = ZoneId.of("America/New_York")
        val samples = listOf("2030-03-10T06:59:00Z" to -18000, "2030-03-10T07:01:00Z" to -14400)
        for ((timestamp, expectedOffset) in samples) {
            val instant = Instant.parse(timestamp)
            val result = PersonalDeviceContext(RuntimeEnvironment.getApplication(), Clock.fixed(instant, zone)).environment()
            assertEquals(expectedOffset, result["utcOffsetSeconds"])
            assertEquals("2030-03-10", result["localDate"])
            assertEquals(instant, OffsetDateTime.parse(result["currentTime"] as String).toInstant())
        }
    }
}
