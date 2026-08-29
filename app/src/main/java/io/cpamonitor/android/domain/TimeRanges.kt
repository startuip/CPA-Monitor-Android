package io.cpamonitor.android.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

enum class RangePreset(val label: String, val days: Long) {
    TODAY("今日", 1), WEEK("7天", 7), MONTH("30天", 30), CUSTOM("自定义", 0)
}

data class TimeRange(val fromMs: Long, val toMs: Long, val zoneId: String)

fun currentRange(
    preset: RangePreset,
    now: Instant = Instant.now(),
    zone: ZoneId = ZoneId.systemDefault(),
    customStart: LocalDate? = null,
    customEndInclusive: LocalDate? = null,
): TimeRange {
    val today = ZonedDateTime.ofInstant(now, zone).toLocalDate()
    val start = when (preset) {
        RangePreset.TODAY -> today
        RangePreset.WEEK -> today.minusDays(6)
        RangePreset.MONTH -> today.minusDays(29)
        RangePreset.CUSTOM -> customStart ?: today
    }
    val endExclusive = when (preset) {
        RangePreset.CUSTOM -> (customEndInclusive ?: start).plusDays(1)
        else -> today.plusDays(1)
    }
    return TimeRange(
        fromMs = start.atStartOfDay(zone).toInstant().toEpochMilli(),
        toMs = minOf(endExclusive.atStartOfDay(zone).toInstant(), now).toEpochMilli().coerceAtLeast(
            start.atStartOfDay(zone).toInstant().toEpochMilli() + 1,
        ),
        zoneId = zone.id,
    )
}

fun startOfTodayMs(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Long =
    ZonedDateTime.ofInstant(now, zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
