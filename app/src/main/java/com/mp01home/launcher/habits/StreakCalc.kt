package com.mp01home.launcher.habits

import java.time.LocalDate

/*
 * EXT-2 pure logic (java.time only) so it can be unit-tested on the JVM.
 */

/**
 * Current streak ending today, or ending yesterday if today is not checked yet (the streak is
 * still alive until the day is over). 0 if neither today nor yesterday is done.
 */
fun currentStreak(doneDates: Set<LocalDate>, today: LocalDate): Int {
    var day = if (today in doneDates) today else today.minusDays(1)
    var count = 0
    while (day in doneDates) {
        count++
        day = day.minusDays(1)
    }
    return count
}

/** Longest run of consecutive done days ever. */
fun longestStreak(doneDates: Set<LocalDate>): Int {
    var best = 0
    for (date in doneDates) {
        if (date.minusDays(1) in doneDates) continue // not the start of a run
        var length = 0
        var d = date
        while (d in doneDates) {
            length++
            d = d.plusDays(1)
        }
        if (length > best) best = length
    }
    return best
}

/** Parses "yyyy-MM-dd" strings, ignoring malformed ones. */
fun parseIsoDates(isoDates: Collection<String>): Set<LocalDate> =
    isoDates.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

/**
 * The past [days] days ending with (and excluding) today as a strip of marks, oldest first:
 * "■" done, "□" missed. E.g. "■■□■■■".
 */
fun historyStrip(doneDates: Set<LocalDate>, today: LocalDate, days: Int = 6): String =
    (days downTo 1).joinToString("") { offset -> if (today.minusDays(offset.toLong()) in doneDates) "■" else "□" }

/** "5d" streak label, empty for 0. */
fun formatStreak(streak: Int): String = if (streak <= 0) "" else "${streak}d"
