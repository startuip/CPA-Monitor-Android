package io.cpamonitor.android.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeRangesTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test
    fun todayUsesDeviceTimeZone() {
        val now = Instant.parse("2026-08-29T04:00:00Z")
        val range = currentRange(RangePreset.TODAY, now, shanghai)
        assertEquals(Instant.parse("2026-08-28T16:00:00Z").toEpochMilli(), range.fromMs)
        assertEquals(now.toEpochMilli(), range.toMs)
        assertEquals("Asia/Shanghai", range.zoneId)
    }

    @Test
    fun customRangeHonorsDstInsteadOfAssuming24HourDays() {
        val newYork = ZoneId.of("America/New_York")
        val range = currentRange(
            preset = RangePreset.CUSTOM,
            now = Instant.parse("2026-03-12T00:00:00Z"),
            zone = newYork,
            customStart = LocalDate.of(2026, 3, 8),
            customEndInclusive = LocalDate.of(2026, 3, 8),
        )
        assertEquals(23, Duration.ofMillis(range.toMs - range.fromMs).toHours())
    }

    @Test
    fun weekStartsSixCalendarDaysBeforeToday() {
        val now = Instant.parse("2026-08-29T04:00:00Z")
        val range = currentRange(RangePreset.WEEK, now, shanghai)
        val startDate = Instant.ofEpochMilli(range.fromMs).atZone(shanghai).toLocalDate()
        assertEquals(LocalDate.of(2026, 8, 23), startDate)
        assertTrue(range.toMs > range.fromMs)
    }
}

