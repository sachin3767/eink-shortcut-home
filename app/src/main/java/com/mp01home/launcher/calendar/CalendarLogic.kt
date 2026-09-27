package com.mp01home.launcher.calendar

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/*
 * Pure-JVM calendar logic (no Android imports) so it can be unit-tested:
 * meeting-URL extraction (CAL-4), countdown text (CAL-1), free/busy gaps (CAL-5),
 * leave-by target (CAL-6), week grouping (PLAN-3) and free-slot search (PLAN-4).
 */

private const val MINUTE = 60_000L
private const val HOUR = 60 * MINUTE

/** Half-open time range [startMillis, endMillis). */
data class TimeRange(val startMillis: Long, val endMillis: Long) {
    val durationMillis: Long get() = endMillis - startMillis
}

/** Android-free view of one cached event instance. */
data class CalEvent(
    val instanceId: Long,
    val eventId: Long,
    val title: String,
    val location: String?,
    val beginMillis: Long,
    val endMillis: Long,
    val allDay: Boolean,
    val meetingUrl: String?
)

// ---- CAL-4: meeting links ----------------------------------------------------------------

object MeetingLinks {

    /** Minutes before start from which the "Join" button is shown. */
    const val JOIN_WINDOW_MINUTES = 10L

    private val urlRegex = Regex("""(?i)\b(?:https?://)?[a-z0-9.-]+\.[a-z]{2,}(?:/[^\s<>"'()\[\]{}]*)?""")
    private val trailingJunk = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '>', '*', '\'', '"')

    private fun isMeetingHost(host: String, path: String): Boolean {
        val h = host.lowercase(Locale.ROOT)
        return when {
            h == "meet.google.com" -> path.length > 1
            h == "zoom.us" || h.endsWith(".zoom.us") || h == "zoomgov.com" || h.endsWith(".zoomgov.com") ->
                Regex("""^/(j|my|w|s|wc)/""", RegexOption.IGNORE_CASE).containsMatchIn(path)
            h == "teams.microsoft.com" -> path.startsWith("/l/meetup-join", ignoreCase = true) ||
                path.startsWith("/meet/", ignoreCase = true)
            h == "teams.live.com" -> path.startsWith("/meet/", ignoreCase = true)
            else -> false
        }
    }

    /**
     * First Google Meet / Zoom / Teams join URL found in [texts] (checked in order, e.g.
     * location then description). Handles HTML-ish descriptions (`&amp;`, quotes, tags) and
     * bare "meet.google.com/xxx" without a scheme. Returns an https URL or null.
     */
    fun extract(vararg texts: String?): String? {
        for (text in texts) {
            if (text.isNullOrBlank()) continue
            val normalized = text.replace("&amp;", "&")
            for (match in urlRegex.findAll(normalized)) {
                var candidate = match.value.trimEnd(*trailingJunk)
                if (!candidate.startsWith("http://", true) && !candidate.startsWith("https://", true)) {
                    candidate = "https://$candidate"
                }
                val afterScheme = candidate.substringAfter("://")
                val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
                val path = afterScheme.removePrefix(host)
                if (isMeetingHost(host, path)) {
                    return if (candidate.startsWith("http://", true)) "https://" + afterScheme else candidate
                }
            }
        }
        return null
    }

    /** CAL-4: show "Join" from [JOIN_WINDOW_MINUTES] before the start until the end. */
    fun shouldShowJoin(nowMillis: Long, beginMillis: Long, endMillis: Long, url: String?): Boolean =
        url != null && nowMillis >= beginMillis - JOIN_WINDOW_MINUTES * MINUTE && nowMillis < endMillis
}

// ---- CAL-1: countdown ----------------------------------------------------------------------

/** "in 25 min", "in 1 h 5 min", "in 3 h", "now · 12 min left", "ended". Minutes round up. */
fun countdownLabel(nowMillis: Long, beginMillis: Long, endMillis: Long): String {
    if (nowMillis >= endMillis) return "ended"
    if (nowMillis >= beginMillis) {
        val left = ceilMinutes(endMillis - nowMillis)
        return "now · ${formatDuration(left)} left"
    }
    return "in " + formatDuration(ceilMinutes(beginMillis - nowMillis))
}

private fun ceilMinutes(millis: Long): Long = (millis + MINUTE - 1) / MINUTE

/** 25 -> "25 min", 65 -> "1 h 5 min", 120 -> "2 h", 3000 -> "2 d 2 h". */
fun formatDuration(minutes: Long): String {
    val m = minutes.coerceAtLeast(0)
    val days = m / (24 * 60)
    val hours = (m % (24 * 60)) / 60
    val mins = m % 60
    return when {
        days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
        hours > 0 -> if (mins > 0) "$hours h $mins min" else "$hours h"
        else -> "$mins min"
    }
}

/**
 * Time text for an event: "10:30–11:00" today, "Mon 10:30–11:00" another day, "all day".
 */
fun eventTimeLabel(
    beginMillis: Long,
    endMillis: Long,
    allDay: Boolean,
    nowMillis: Long,
    zone: ZoneId,
    use24h: Boolean,
    locale: Locale = Locale.getDefault()
): String {
    if (allDay) return "all day"
    val fmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale)
    val begin = Instant.ofEpochMilli(beginMillis).atZone(zone)
    val end = Instant.ofEpochMilli(endMillis).atZone(zone)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val range = begin.format(fmt) + "–" + end.format(fmt)
    return when (begin.toLocalDate()) {
        today -> range
        today.plusDays(1) -> "tmrw $range"
        else -> begin.format(DateTimeFormatter.ofPattern("EEE", locale)) + " " + range
    }
}

fun formatClock(millis: Long, zone: ZoneId, use24h: Boolean, locale: Locale = Locale.getDefault()): String =
    Instant.ofEpochMilli(millis).atZone(zone)
        .format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))

/**
 * All-day instances are stored by CalendarContract as UTC midnights. Convert to the start of the
 * same calendar date in [zone] so they group into the right local day.
 */
fun allDayUtcToLocal(utcMillis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(zone).toInstant().toEpochMilli()

// ---- CAL-5: free / busy ----------------------------------------------------------------------

/** Sorts, clips to [from, to) and merges overlapping/adjacent ranges. */
fun mergeBusy(busy: List<TimeRange>, fromMillis: Long, toMillis: Long): List<TimeRange> {
    val clipped = busy
        .map { TimeRange(maxOf(it.startMillis, fromMillis), minOf(it.endMillis, toMillis)) }
        .filter { it.endMillis > it.startMillis }
        .sortedBy { it.startMillis }
    val merged = ArrayList<TimeRange>()
    for (r in clipped) {
        val last = merged.lastOrNull()
        if (last != null && r.startMillis <= last.endMillis) {
            merged[merged.lastIndex] = TimeRange(last.startMillis, maxOf(last.endMillis, r.endMillis))
        } else {
            merged += r
        }
    }
    return merged
}

/** Free gaps within [from, to) of at least [minGapMillis]. */
fun freeGaps(busy: List<TimeRange>, fromMillis: Long, toMillis: Long, minGapMillis: Long = 0L): List<TimeRange> {
    if (toMillis <= fromMillis) return emptyList()
    val gaps = ArrayList<TimeRange>()
    var cursor = fromMillis
    for (b in mergeBusy(busy, fromMillis, toMillis)) {
        if (b.startMillis > cursor) gaps += TimeRange(cursor, b.startMillis)
        cursor = maxOf(cursor, b.endMillis)
    }
    if (cursor < toMillis) gaps += TimeRange(cursor, toMillis)
    return gaps.filter { it.durationMillis >= minGapMillis && it.durationMillis > 0 }
}

data class StripSegment(val range: TimeRange, val busy: Boolean)

/** Alternating busy/free segments that exactly tile [from, to), for the block strip. */
fun stripSegments(busy: List<TimeRange>, fromMillis: Long, toMillis: Long): List<StripSegment> {
    if (toMillis <= fromMillis) return emptyList()
    val out = ArrayList<StripSegment>()
    var cursor = fromMillis
    for (b in mergeBusy(busy, fromMillis, toMillis)) {
        if (b.startMillis > cursor) out += StripSegment(TimeRange(cursor, b.startMillis), busy = false)
        out += StripSegment(b, busy = true)
        cursor = b.endMillis
    }
    if (cursor < toMillis) out += StripSegment(TimeRange(cursor, toMillis), busy = false)
    return out
}

/**
 * Strip window for [date]: [dayStartHour, dayEndHour) widened to whole hours covering any timed
 * event of that day, clamped to the date's midnight bounds.
 */
fun stripWindow(
    date: LocalDate,
    zone: ZoneId,
    busy: List<TimeRange>,
    dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    dayEndHour: Int = DEFAULT_DAY_END_HOUR
): TimeRange {
    val midnight = date.atStartOfDay(zone).toInstant().toEpochMilli()
    val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    var from = date.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
    var to = if (dayEndHour >= 24) nextMidnight else date.atTime(dayEndHour, 0).atZone(zone).toInstant().toEpochMilli()
    for (b in busy) {
        if (b.endMillis <= midnight || b.startMillis >= nextMidnight) continue
        if (b.startMillis < from) from = maxOf(midnight, floorToHour(b.startMillis, zone))
        if (b.endMillis > to) to = minOf(nextMidnight, ceilToHour(b.endMillis, zone))
    }
    return TimeRange(from, to)
}

private fun floorToHour(millis: Long, zone: ZoneId): Long =
    Instant.ofEpochMilli(millis).atZone(zone).withMinute(0).withSecond(0).withNano(0).toInstant().toEpochMilli()

private fun ceilToHour(millis: Long, zone: ZoneId): Long {
    val floor = floorToHour(millis, zone)
    return if (floor == millis) millis else floor + HOUR
}

/** "free now–10:30 · 12:00–14:00" (at most [maxGaps]); "no free time left today" if none. */
fun freeGapsLabel(
    gaps: List<TimeRange>,
    nowMillis: Long,
    zone: ZoneId,
    use24h: Boolean,
    maxGaps: Int = 2,
    locale: Locale = Locale.getDefault()
): String {
    if (gaps.isEmpty()) return "no free time left today"
    val parts = gaps.take(maxGaps).map { g ->
        val start = if (g.startMillis <= nowMillis) "now" else formatClock(g.startMillis, zone, use24h, locale)
        start + "–" + formatClock(g.endMillis, zone, use24h, locale)
    }
    val more = if (gaps.size > maxGaps) " +${gaps.size - maxGaps}" else ""
    return "free " + parts.joinToString(" · ") + more
}

const val DEFAULT_DAY_START_HOUR = 8
const val DEFAULT_DAY_END_HOUR = 20

// ---- PLAN-4: next free slot ------------------------------------------------------------------

/**
 * First slot of [durationMillis] that does not overlap [busy], starting no earlier than
 * [earliestMillis] rounded up to [roundMinutes], within working hours
 * [dayStartHour, dayEndHour) of each day, searching [maxDays] days. Null if none.
 */
fun findFreeSlot(
    busy: List<TimeRange>,
    earliestMillis: Long,
    durationMillis: Long,
    zone: ZoneId,
    dayStartHour: Int = DEFAULT_DAY_START_HOUR,
    dayEndHour: Int = DEFAULT_DAY_END_HOUR,
    maxDays: Int = 7,
    roundMinutes: Int = 15
): TimeRange? {
    if (durationMillis <= 0) return null
    val roundMillis = roundMinutes.coerceAtLeast(1) * MINUTE
    val earliest = ((earliestMillis + roundMillis - 1) / roundMillis) * roundMillis
    val firstDay = Instant.ofEpochMilli(earliest).atZone(zone).toLocalDate()
    for (i in 0 until maxDays) {
        val day = firstDay.plusDays(i.toLong())
        val dayStart = day.atTime(dayStartHour, 0).atZone(zone).toInstant().toEpochMilli()
        val dayEnd = if (dayEndHour >= 24) {
            day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            day.atTime(dayEndHour, 0).atZone(zone).toInstant().toEpochMilli()
        }
        val from = maxOf(dayStart, earliest)
        if (from >= dayEnd) continue
        val gap = freeGaps(busy, from, dayEnd, durationMillis).firstOrNull() ?: continue
        return TimeRange(gap.startMillis, gap.startMillis + durationMillis)
    }
    return null
}

/** Busy ranges for free/busy maths: timed events only (all-day events don't block time). */
fun busyRanges(events: List<CalEvent>): List<TimeRange> =
    events.filter { !it.allDay && it.endMillis > it.beginMillis }.map { TimeRange(it.beginMillis, it.endMillis) }

// ---- CAL-6: leave-by -------------------------------------------------------------------------

/**
 * The event to warn about: the earliest timed event with a non-blank location whose leave time
 * (begin − [leadMillis]) is still in the future. Returns the event and its leave time.
 */
fun pickLeaveByTarget(events: List<CalEvent>, nowMillis: Long, leadMillis: Long): Pair<CalEvent, Long>? =
    events.asSequence()
        .filter { !it.allDay && isPhysicalLocation(it.location) }
        .map { it to (it.beginMillis - leadMillis) }
        .filter { (_, leaveAt) -> leaveAt > nowMillis }
        .minByOrNull { (e, _) -> e.beginMillis }

private val anyUrlRegex = Regex("""(?i)(https?://\S+|\b[a-z0-9-]+(\.[a-z0-9-]+)*\.(com|us|net|org|io)/\S*)""")

/** True if [location] names a place to travel to (not blank and not just a video-call link). */
fun isPhysicalLocation(location: String?): Boolean {
    if (location.isNullOrBlank()) return false
    val withoutUrls = location.replace(anyUrlRegex, " ").trim().trimEnd(',', ';', '.').lowercase(Locale.ROOT)
    if (withoutUrls in virtualLocationNames) return false
    return withoutUrls.any { it.isLetterOrDigit() }
}

private val virtualLocationNames = setOf(
    "microsoft teams meeting", "microsoft teams", "teams", "teams meeting",
    "zoom", "zoom meeting", "google meet", "meet", "online", "video call", "phone", "call"
)

// ---- PLAN-3: week ----------------------------------------------------------------------------

data class WeekDay(
    val date: LocalDate,
    val events: List<CalEvent>,
    val taskCount: Int
)

/**
 * Seven [WeekDay]s from [startDate]. An event appears on every local day it overlaps (all-day
 * events first, then by start). [taskDueEpochDays] are due dates of open tasks; overdue tasks
 * count towards [startDate].
 */
fun buildWeek(
    startDate: LocalDate,
    zone: ZoneId,
    events: List<CalEvent>,
    taskDueEpochDays: List<Long?>,
    days: Int = 7
): List<WeekDay> {
    val startEpoch = startDate.toEpochDay()
    return (0 until days).map { i ->
        val date = startDate.plusDays(i.toLong())
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val to = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEvents = events
            .filter { it.endMillis > from && it.beginMillis < to || (it.beginMillis == it.endMillis && it.beginMillis in from until to) }
            .sortedWith(compareByDescending<CalEvent> { it.allDay }.thenBy { it.beginMillis })
        val epoch = date.toEpochDay()
        val tasks = taskDueEpochDays.count { due ->
            due != null && (due == epoch || (i == 0 && due < startEpoch))
        }
        WeekDay(date, dayEvents, tasks)
    }
}
