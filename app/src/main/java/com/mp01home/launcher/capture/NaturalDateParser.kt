package com.mp01home.launcher.capture

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * Result of [NaturalDateParser.parse]: the capture text with every recognised date/time token
 * removed ([title]), plus the resolved [date] and optional [time].
 *
 * A [time] without an explicit date is resolved to today (or tomorrow if that time has already
 * passed), so whenever [time] is non-null [date] is too.
 */
data class ParsedCapture(
    val title: String,
    val date: LocalDate? = null,
    val time: LocalTime? = null
) {
    val dateTime: LocalDateTime? get() = if (date != null && time != null) date.atTime(time) else null
}

/**
 * CAP-2: a small, dependency-free natural-language date/time parser for quick capture.
 *
 * Understands (case-insensitive, anywhere in the text):
 * - days: `today`, `tonight` (20:00), `tomorrow`/`tmrw`/`tmr`, weekday names and abbreviations
 *   (`fri`, `thurs`, `monday`…; today counts, otherwise the next one), `next mon` (the Monday of
 *   *next* week), `this fri`, `next week` (next Monday)
 * - relative: `in 2 days`, `in 3 weeks`, `in a month`, `in 2 hours`, `in 30 min`
 * - dates: `3/10` (month/day, or day/month when `dayFirst`), `3/10/27`, `2026-10-03`,
 *   `oct 3`, `3rd october`, `october 3 2027` (past dates without a year roll to next year)
 * - times: `3pm`, `3 pm`, `3:30pm`, `15:30`, `9:30` (24 h), `at 5` (1–7 → pm), `noon`,
 *   `midnight`, `morning` (09:00), `evening` (18:00)
 * - the connectors `at`, `on`, `by`, `due`, `@` directly before a recognised token are dropped
 *
 * Pure JVM so it is unit-tested (see NaturalDateParserTest).
 */
object NaturalDateParser {

    fun parse(input: String, now: LocalDateTime, dayFirst: Boolean = false): ParsedCapture {
        val raw = input.trim().split(WHITESPACE).filter { it.isNotEmpty() }
        if (raw.isEmpty()) return ParsedCapture(title = "")
        val tokens = raw.map(::normalize)
        val consumed = BooleanArray(raw.size)
        val state = State(now.toLocalDate(), now)

        var i = 0
        while (i < tokens.size) {
            val used = matchAt(tokens, i, state, dayFirst)
            if (used > 0) {
                for (k in i until i + used) consumed[k] = true
                // Drop a connector ("at", "on", "by", "due", "@") right before the match.
                val prev = i - 1
                if (prev >= 0 && !consumed[prev] && tokens[prev] in CONNECTORS) consumed[prev] = true
                i += used
            } else {
                i++
            }
        }

        var date = state.date
        val time = state.time
        if (time != null && date == null) {
            date = if (time.isAfter(now.toLocalTime())) state.today else state.today.plusDays(1)
        }

        val title = raw.filterIndexed { idx, _ -> !consumed[idx] }
            .joinToString(" ")
            .trim()
            .trimEnd(',', ';', '-', ':', '–')
            .trim()
        return ParsedCapture(title = title.ifEmpty { input.trim() }, date = date, time = time)
    }

    /** True for locales that write day before month (`3/10` = 3 October). */
    fun isDayFirst(locale: Locale): Boolean = locale.country.uppercase(Locale.ROOT) !in MONTH_FIRST_COUNTRIES

    // ---------------------------------------------------------------------------------------

    private class State(val today: LocalDate, val now: LocalDateTime) {
        var date: LocalDate? = null
        var time: LocalTime? = null
    }

    /** Tries every matcher at [i]; returns the number of tokens consumed (0 = no match). */
    private fun matchAt(t: List<String>, i: Int, s: State, dayFirst: Boolean): Int {
        val tok = t[i]
        val next = t.getOrNull(i + 1)
        val prev = t.getOrNull(i - 1)

        // ---- relative: "in 2 days", "in an hour" ----
        if (tok == "in" && next != null) {
            val n = parseCount(next)
            val unit = t.getOrNull(i + 2)?.let(::unitOf)
            if (n != null && unit != null) {
                when (unit) {
                    ChronoUnit.MINUTES, ChronoUnit.HOURS -> {
                        if (s.date != null || s.time != null) return 0
                        val at = s.now.plus(n.toLong(), unit).truncatedTo(ChronoUnit.MINUTES)
                        s.date = at.toLocalDate(); s.time = at.toLocalTime()
                    }
                    else -> {
                        if (s.date != null) return 0
                        s.date = s.today.plus(n.toLong(), unit)
                    }
                }
                return 3
            }
        }

        // ---- day words ----
        if (s.date == null) {
            when (tok) {
                "today", "tdy" -> { s.date = s.today; return 1 }
                "tonight" -> {
                    s.date = s.today
                    if (s.time == null) s.time = LocalTime.of(20, 0)
                    return 1
                }
                "tomorrow", "tmrw", "tmr", "tomorow", "tommorow", "tommorrow" -> {
                    s.date = s.today.plusDays(1); return 1
                }
            }
            if (tok == "next" && next != null) {
                if (next == "week") {
                    s.date = s.today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)); return 2
                }
                val dow = WEEKDAYS[next]
                if (dow != null) { s.date = nextWeekday(s.today, dow); return 2 }
            }
            if (tok == "this" && next != null) {
                val dow = WEEKDAYS[next]
                if (dow != null) { s.date = s.today.with(TemporalAdjusters.nextOrSame(dow)); return 2 }
            }
            WEEKDAYS[tok]?.let { dow ->
                s.date = s.today.with(TemporalAdjusters.nextOrSame(dow)); return 1
            }

            // ---- numeric dates ----
            ISO_DATE.matchEntire(tok)?.let { m ->
                val d = safeDate(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
                if (d != null) { s.date = d; return 1 }
            }
            SLASH_DATE.matchEntire(tok)?.let { m ->
                val a = m.groupValues[1].toInt()
                val b = m.groupValues[2].toInt()
                val (month, day) = if (dayFirst) b to a else a to b
                val d = resolveDate(s.today, month, day, m.groupValues[3].takeIf { it.isNotEmpty() })
                if (d != null) { s.date = d; return 1 }
            }

            // ---- month names: "oct 3", "october 3rd 2027", "3 oct", "3rd of october" ----
            MONTHS[tok]?.let { month ->
                val day = next?.let(::parseDayOfMonth)
                if (day != null) {
                    val yearTok = t.getOrNull(i + 2)?.takeIf { YEAR.matches(it) }
                    val d = resolveDate(s.today, month, day, yearTok)
                    if (d != null) { s.date = d; return if (yearTok != null) 3 else 2 }
                }
            }
            parseDayOfMonth(tok)?.let { day ->
                var j = i + 1
                if (t.getOrNull(j) == "of") j++
                val month = t.getOrNull(j)?.let { MONTHS[it] }
                if (month != null) {
                    val yearTok = t.getOrNull(j + 1)?.takeIf { YEAR.matches(it) }
                    val d = resolveDate(s.today, month, day, yearTok)
                    if (d != null) {
                        s.date = d
                        return (j - i + 1) + if (yearTok != null) 1 else 0
                    }
                }
            }
        }

        // ---- times ----
        if (s.time == null) {
            when (tok) {
                "noon", "midday" -> { s.time = LocalTime.NOON; return 1 }
                "midnight" -> { s.time = LocalTime.MIDNIGHT; return 1 }
                "morning" -> { s.time = LocalTime.of(9, 0); return 1 }
                "evening" -> { s.time = LocalTime.of(18, 0); return 1 }
            }
            // "3pm", "3:30pm", "3:30 pm", "3 pm"
            TIME_AMPM.matchEntire(tok)?.let { m ->
                val time = amPm(m.groupValues[1].toInt(), m.groupValues[2], m.groupValues[3])
                if (time != null) { s.time = time; return 1 }
            }
            TIME_PLAIN.matchEntire(tok)?.let { m ->
                val hour = m.groupValues[1].toInt()
                val minute = m.groupValues[2].takeIf { it.isNotEmpty() }
                if (next != null && next in AM_PM_WORDS) {
                    val time = amPm(hour, minute.orEmpty(), next)
                    if (time != null) { s.time = time; return 2 }
                }
                if (minute != null) {
                    // "15:30", "9:30" → 24-hour clock.
                    if (hour in 0..23 && minute.toInt() in 0..59) {
                        s.time = LocalTime.of(hour, minute.toInt()); return 1
                    }
                } else if (prev == "at" || prev == "@") {
                    // "at 5" → 17:00 (1–7 are assumed afternoon), "at 9" → 09:00, "at 18" → 18:00.
                    if (hour in 0..23) {
                        s.time = LocalTime.of(if (hour in 1..7) hour + 12 else hour, 0); return 1
                    }
                }
            }
        }
        return 0
    }

    /** "next mon": the next such weekday that falls in a later (Mon–Sun) week than today. */
    private fun nextWeekday(today: LocalDate, dow: DayOfWeek): LocalDate {
        val candidate = today.with(TemporalAdjusters.next(dow))
        val thisWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val candidateMonday = candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return if (candidateMonday == thisWeekMonday) candidate.plusWeeks(1) else candidate
    }

    private fun amPm(hour: Int, minute: String, suffix: String): LocalTime? {
        if (hour !in 1..12) return null
        val min = if (minute.isEmpty()) 0 else minute.toInt()
        if (min !in 0..59) return null
        val pm = suffix.startsWith("p")
        val h = when {
            pm && hour != 12 -> hour + 12
            !pm && hour == 12 -> 0
            else -> hour
        }
        return LocalTime.of(h, min)
    }

    /** A date without a year that has already passed this year rolls over to next year. */
    private fun resolveDate(today: LocalDate, month: Int, day: Int, yearText: String?): LocalDate? {
        if (yearText != null) {
            val y = yearText.toInt().let { if (it < 100) 2000 + it else it }
            return safeDate(y, month, day)
        }
        val thisYear = safeDate(today.year, month, day)
        return when {
            thisYear == null -> safeDate(today.year + 1, month, day) // e.g. 29 Feb
            thisYear.isBefore(today) -> safeDate(today.year + 1, month, day)
            else -> thisYear
        }
    }

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: java.time.DateTimeException) {
            null
        }

    private fun parseDayOfMonth(tok: String): Int? =
        DAY_OF_MONTH.matchEntire(tok)?.groupValues?.get(1)?.toInt()?.takeIf { it in 1..31 }

    private fun parseCount(tok: String): Int? =
        tok.toIntOrNull()?.takeIf { it in 1..999 } ?: NUMBER_WORDS[tok]

    private fun unitOf(tok: String): ChronoUnit? = when (tok) {
        "min", "mins", "minute", "minutes" -> ChronoUnit.MINUTES
        "h", "hr", "hrs", "hour", "hours" -> ChronoUnit.HOURS
        "day", "days" -> ChronoUnit.DAYS
        "wk", "wks", "week", "weeks" -> ChronoUnit.WEEKS
        "month", "months" -> ChronoUnit.MONTHS
        else -> null
    }

    /** Lower-case and strip surrounding punctuation (keeps ':' '/' '-' inside dates/times). */
    private fun normalize(token: String): String =
        token.lowercase(Locale.ROOT).trim(',', '.', ';', '!', '?', '(', ')', '"', '\'')
            .removePrefix("@").ifEmpty { if (token == "@") "@" else "" }

    private val WHITESPACE = Regex("\\s+")
    private val ISO_DATE = Regex("(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val SLASH_DATE = Regex("(\\d{1,2})/(\\d{1,2})(?:/(\\d{2}|\\d{4}))?")
    private val YEAR = Regex("20\\d{2}")
    private val DAY_OF_MONTH = Regex("(\\d{1,2})(?:st|nd|rd|th)?")
    private val TIME_AMPM = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm|a|p|a\\.m|p\\.m)")
    private val TIME_PLAIN = Regex("(\\d{1,2})(?::(\\d{2}))?")
    private val AM_PM_WORDS = setOf("am", "pm", "a.m", "p.m")
    private val CONNECTORS = setOf("at", "on", "by", "due", "@")

    /** Countries writing month before day in short numeric dates. */
    private val MONTH_FIRST_COUNTRIES = setOf("US", "PH", "FM", "MH", "PW", "BZ")

    private val NUMBER_WORDS = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "couple" to 2
    )

    private val WEEKDAYS: Map<String, DayOfWeek> = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY, "saturday" to DayOfWeek.SATURDAY,
        "sun" to DayOfWeek.SUNDAY, "sunday" to DayOfWeek.SUNDAY
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9,
        "oct" to 10, "october" to 10, "nov" to 11, "november" to 11, "dec" to 12, "december" to 12
    )
}
