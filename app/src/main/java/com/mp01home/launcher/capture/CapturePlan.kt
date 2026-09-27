package com.mp01home.launcher.capture

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** CAP-1 target toggle: Task (Google Tasks), Keep (note via share intent), Event (calendar). */
enum class CaptureTarget(val short: String, val label: String) {
    TASK("T", "Task"),
    KEEP("K", "Keep"),
    EVENT("E", "Event")
}

/** What submitting a capture should do. Built by [planCapture]; executed by [CaptureExecutor]. */
sealed interface CapturePlan {
    /** TASK-4: add to the default Google Tasks list. Google Tasks due dates carry no time. */
    data class AddTask(val title: String, val due: LocalDate?) : CapturePlan

    /** KEEP-2: the raw text (dates are not stripped) shared to Keep. */
    data class KeepNote(val text: String) : CapturePlan

    /**
     * CAL-3 style insert. [beginMillis]/[endMillis] are null when no date was given (the calendar
     * app then picks its own default). Date without time → all-day event.
     */
    data class InsertEvent(
        val title: String,
        val beginMillis: Long?,
        val endMillis: Long?,
        val allDay: Boolean
    ) : CapturePlan
}

/** Default length of a timed event created from quick capture. */
const val DEFAULT_EVENT_MINUTES = 60L

/** Pure: turns the input text + target into a [CapturePlan], or null for blank input. */
fun planCapture(
    text: String,
    target: CaptureTarget,
    now: LocalDateTime,
    zone: ZoneId,
    dayFirst: Boolean = false
): CapturePlan? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    if (target == CaptureTarget.KEEP) return CapturePlan.KeepNote(trimmed)

    val parsed = NaturalDateParser.parse(trimmed, now, dayFirst)
    return when (target) {
        CaptureTarget.TASK -> CapturePlan.AddTask(parsed.title, parsed.date)
        CaptureTarget.EVENT -> {
            val date = parsed.date
            val time = parsed.time
            when {
                date == null -> CapturePlan.InsertEvent(parsed.title, null, null, allDay = false)
                time == null -> {
                    // All-day events are expressed in UTC midnight by CalendarContract.
                    val begin = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                    val end = date.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
                    CapturePlan.InsertEvent(parsed.title, begin, end, allDay = true)
                }
                else -> {
                    val start = date.atTime(time).atZone(zone)
                    CapturePlan.InsertEvent(
                        parsed.title,
                        start.toInstant().toEpochMilli(),
                        start.plusMinutes(DEFAULT_EVENT_MINUTES).toInstant().toEpochMilli(),
                        allDay = false
                    )
                }
            }
        }
        CaptureTarget.KEEP -> CapturePlan.KeepNote(trimmed)
    }
}

/**
 * Short preview of what the parser understood, shown under the capture field:
 * "→ today 15:00", "→ tomorrow", "→ Fri 2 Oct 09:30". Null when nothing was parsed.
 */
fun describeParsedWhen(
    parsed: ParsedCapture,
    today: LocalDate,
    use24h: Boolean = true,
    locale: Locale = Locale.getDefault()
): String? {
    val date = parsed.date ?: return null
    val day = when (date) {
        today -> "today"
        today.plusDays(1) -> "tomorrow"
        else -> {
            val pattern = if (date.year == today.year) "EEE d MMM" else "EEE d MMM yyyy"
            date.format(DateTimeFormatter.ofPattern(pattern, locale))
        }
    }
    val time = parsed.time?.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))
    return if (time != null) "$day $time" else day
}
