package cn.com.omnimind.bot.agent

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class CalendarDateTimeTest {
    @Test
    fun `explicit regional timezone preserves the supplied instant for create and update`() {
        val zone = ZoneId.of("Asia/Shanghai")
        for (value in listOf("2026-11-04T15:00:00+08:00", "2026-11-04T07:00:00Z")) {
            val parsed = parseCalendarDateTime(value, zone, explicitZone = true)
            assertEquals("Asia/Shanghai", parsed.zone.id)
            assertEquals(Instant.parse("2026-11-04T07:00:00Z"), parsed.toInstant())
        }
    }

    @Test
    fun `offset only input without explicit timezone retains existing offset`() {
        val parsed = parseCalendarDateTime(
            "2026-11-04T15:00:00+08:00", ZoneId.of("America/New_York"), explicitZone = false,
        )
        assertEquals("+08:00", parsed.zone.id)
        assertEquals(Instant.parse("2026-11-04T07:00:00Z"), parsed.toInstant())
    }

    @Test
    fun `local datetime uses selected regional timezone`() {
        val parsed = parseCalendarDateTime(
            "2026-11-04T15:30:00", ZoneId.of("Asia/Shanghai"), explicitZone = true,
        )
        assertEquals("Asia/Shanghai", parsed.zone.id)
        assertEquals(Instant.parse("2026-11-04T07:30:00Z"), parsed.toInstant())
    }
}
