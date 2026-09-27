package com.mp01home.launcher.digest

import java.time.LocalTime
import java.time.ZoneId

/** A posted notification reduced to what the digest rules need (Android-free). */
data class DigestCandidate(
    val key: String,
    val packageName: String,
    val postTimeMillis: Long,
    val category: String? = null,
    val isOngoing: Boolean = false,
    val isClearable: Boolean = true,
    val isGroupSummary: Boolean = false
)

sealed interface DigestDecision {
    /** Leave it alone and do not record it. */
    data object Ignore : DigestDecision

    /** Leave it in the shade and show it in the home digest. */
    data object Record : DigestDecision

    /** Snooze it for [durationMillis] (until the next digest time) and count it as held. */
    data class Hold(val durationMillis: Long, val releaseAtMillis: Long) : DigestDecision
}

/**
 * FOC-4 rules. Whitelisted apps are recorded for the home digest. When holding is on, every
 * other clearable notification is snoozed until the next digest time, except calls, alarms,
 * media, system/ongoing notifications, Gmail (MAIL-1 reads it) and our own. A notification
 * posted before the latest digest time is a release (the system re-posting a snoozed one, or
 * one already due) and is recorded, not snoozed again.
 */
object DigestPolicy {

    /** Tolerance so a snooze that expires a few ms before the digest time is not re-held. */
    const val RELEASE_TOLERANCE_MILLIS = 60_000L

    /** Extra delay after the digest time so the re-post lands on the "released" side. */
    const val SNOOZE_PADDING_MILLIS = 1_000L

    val EXEMPT_PACKAGES: Set<String> = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.server.telecom",
        "com.google.android.dialer",
        "com.android.dialer",
        "com.google.android.deskclock",
        "com.android.deskclock",
        "com.google.android.gm",
        "com.google.android.gm.lite"
    )

    /** Notification.CATEGORY_* values that must never be delayed. */
    val EXEMPT_CATEGORIES: Set<String> = setOf(
        "call", "missed_call", "alarm", "reminder", "event", "navigation", "transport",
        "service", "sys", "progress", "stopwatch", "err", "status", "location_sharing"
    )

    fun decide(
        candidate: DigestCandidate,
        ownPackage: String,
        whitelist: Set<String>,
        holdEnabled: Boolean,
        digestTimes: List<LocalTime>,
        nowMillis: Long,
        zone: ZoneId
    ): DigestDecision {
        if (candidate.packageName == ownPackage) return DigestDecision.Ignore
        if (candidate.isOngoing || !candidate.isClearable) return DigestDecision.Ignore

        if (candidate.packageName in whitelist) {
            return if (candidate.isGroupSummary) DigestDecision.Ignore else DigestDecision.Record
        }

        if (!holdEnabled || digestTimes.isEmpty()) return DigestDecision.Ignore
        if (candidate.packageName in EXEMPT_PACKAGES) return DigestDecision.Ignore
        if (candidate.category != null && candidate.category in EXEMPT_CATEGORIES) return DigestDecision.Ignore

        val previous = DigestSchedule.previousMillis(nowMillis + RELEASE_TOLERANCE_MILLIS, zone, digestTimes)
        if (previous != null && candidate.postTimeMillis < previous) {
            return if (candidate.isGroupSummary) DigestDecision.Ignore else DigestDecision.Record
        }

        val next = DigestSchedule.nextMillis(nowMillis, zone, digestTimes) ?: return DigestDecision.Ignore
        val duration = (next - nowMillis + SNOOZE_PADDING_MILLIS).coerceAtLeast(SNOOZE_PADDING_MILLIS)
        return DigestDecision.Hold(durationMillis = duration, releaseAtMillis = next)
    }
}
