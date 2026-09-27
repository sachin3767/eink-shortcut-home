package com.mp01home.launcher.digest

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** FOC-4: pure digest-time arithmetic (DST-safe via ZonedDateTime). */
object DigestSchedule {

    /** First digest time strictly after [now], or null if [times] is empty. */
    fun next(now: ZonedDateTime, times: List<LocalTime>): ZonedDateTime? {
        if (times.isEmpty()) return null
        val today = now.toLocalDate()
        return listOf(today, today.plusDays(1))
            .flatMap { day -> times.map { ZonedDateTime.of(day, it, now.zone) } }
            .filter { it.isAfter(now) }
            .minOrNull()
    }

    /** Latest digest time at or before [now], or null if [times] is empty. */
    fun previous(now: ZonedDateTime, times: List<LocalTime>): ZonedDateTime? {
        if (times.isEmpty()) return null
        val today = now.toLocalDate()
        return listOf(today.minusDays(1), today)
            .flatMap { day -> times.map { ZonedDateTime.of(day, it, now.zone) } }
            .filter { !it.isAfter(now) }
            .maxOrNull()
    }

    fun nextMillis(nowMillis: Long, zone: ZoneId, times: List<LocalTime>): Long? =
        next(Instant.ofEpochMilli(nowMillis).atZone(zone), times)?.toInstant()?.toEpochMilli()

    fun previousMillis(nowMillis: Long, zone: ZoneId, times: List<LocalTime>): Long? =
        previous(Instant.ofEpochMilli(nowMillis).atZone(zone), times)?.toInstant()?.toEpochMilli()
}
