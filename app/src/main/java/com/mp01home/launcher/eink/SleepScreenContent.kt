package com.mp01home.launcher.eink

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One event as the sleep screen needs it (decoupled from Room). */
data class SleepEvent(
    val title: String,
    val begin: LocalDateTime,
    val end: LocalDateTime,
    val allDay: Boolean
)

data class SleepTask(val title: String, val overdue: Boolean)

data class SleepPriority(val text: String, val done: Boolean)

/** A line of the rendered sleep image. [heading] lines are drawn bold. */
data class SleepLine(val text: String, val heading: Boolean = false)

/**
 * UX-5: the text of the lock/sleep-screen agenda. Pure so it is unit-testable; the bitmap
 * renderer only draws these lines.
 */
object SleepScreenContent {

    const val MAX_EVENTS = 6
    const val MAX_TASKS = 6

    fun build(
        now: LocalDateTime,
        events: List<SleepEvent>,
        tasks: List<SleepTask>,
        top3: List<SleepPriority>,
        use24h: Boolean = true,
        locale: Locale = Locale.getDefault()
    ): List<SleepLine> {
        val timeFmt = DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mma", locale)
        val lines = mutableListOf<SleepLine>()
        lines += SleepLine(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale)), heading = true)

        val priorities = top3.filter { it.text.isNotBlank() }
        if (priorities.isNotEmpty()) {
            lines += SleepLine("")
            lines += SleepLine("TOP 3", heading = true)
            priorities.forEachIndexed { i, p ->
                lines += SleepLine("${i + 1}. ${if (p.done) "[x] " else ""}${p.text.trim()}")
            }
        }

        lines += SleepLine("")
        lines += SleepLine("AGENDA", heading = true)
        val remaining = events
            .filter { it.end.isAfter(now) && it.begin.toLocalDate() <= now.toLocalDate() }
            .sortedWith(compareBy({ !it.allDay }, { it.begin }))
        if (remaining.isEmpty()) {
            lines += SleepLine("No more events today")
        } else {
            remaining.take(MAX_EVENTS).forEach { e ->
                val time = if (e.allDay) "all day" else e.begin.format(timeFmt)
                lines += SleepLine("$time  ${e.title.trim().ifEmpty { "(no title)" }}")
            }
            if (remaining.size > MAX_EVENTS) lines += SleepLine("+${remaining.size - MAX_EVENTS} more")
        }

        lines += SleepLine("")
        val overdue = tasks.count { it.overdue }
        lines += SleepLine(if (overdue > 0) "TASKS  $overdue overdue" else "TASKS", heading = true)
        if (tasks.isEmpty()) {
            lines += SleepLine("Nothing due")
        } else {
            tasks.take(MAX_TASKS).forEach { t -> lines += SleepLine("[ ] ${t.title.trim()}") }
            if (tasks.size > MAX_TASKS) lines += SleepLine("+${tasks.size - MAX_TASKS} more")
        }
        return lines
    }

    /** Footer drawn at the bottom; kept out of [build] so the content hash ignores it. */
    fun footer(now: LocalDateTime, use24h: Boolean = true, locale: Locale = Locale.getDefault()): String =
        "updated " + now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mma", locale))
}
