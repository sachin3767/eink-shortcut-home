package com.mp01home.launcher.digest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DigestPolicyTest {

    private val zone = ZoneOffset.UTC
    private val times = listOf(LocalTime.of(12, 0), LocalTime.of(18, 0))
    private fun millis(h: Int, m: Int, s: Int = 0) =
        ZonedDateTime.of(2026, 9, 26, h, m, s, 0, zone).toInstant().toEpochMilli()

    private fun decide(
        c: DigestCandidate,
        whitelist: Set<String> = setOf("com.slack"),
        hold: Boolean = true,
        now: Long = millis(11, 0)
    ) = DigestPolicy.decide(c, "com.mp01home.launcher", whitelist, hold, times, now, zone)

    private fun cand(pkg: String = "com.game", post: Long = millis(11, 0), category: String? = null) =
        DigestCandidate(key = "k", packageName = pkg, postTimeMillis = post, category = category)

    @Test
    fun whitelistedIsRecorded() {
        assertEquals(DigestDecision.Record, decide(cand("com.slack")))
        assertEquals(DigestDecision.Record, decide(cand("com.slack"), hold = false))
        assertEquals(DigestDecision.Ignore, decide(cand("com.slack").copy(isGroupSummary = true)))
    }

    @Test
    fun otherAppsHeldUntilNextDigest() {
        val d = decide(cand())
        assertTrue(d is DigestDecision.Hold)
        d as DigestDecision.Hold
        assertEquals(millis(12, 0), d.releaseAtMillis)
        assertEquals(60 * 60 * 1000L + DigestPolicy.SNOOZE_PADDING_MILLIS, d.durationMillis)
    }

    @Test
    fun holdDisabledOrNoTimesIgnores() {
        assertEquals(DigestDecision.Ignore, decide(cand(), hold = false))
        assertEquals(
            DigestDecision.Ignore,
            DigestPolicy.decide(cand(), "own", emptySet(), true, emptyList(), millis(11, 0), zone)
        )
    }

    @Test
    fun exemptions() {
        assertEquals(DigestDecision.Ignore, decide(cand("com.mp01home.launcher")))
        assertEquals(DigestDecision.Ignore, decide(cand("com.google.android.gm")))
        assertEquals(DigestDecision.Ignore, decide(cand(category = "call")))
        assertEquals(DigestDecision.Ignore, decide(cand(category = "alarm")))
        assertEquals(DigestDecision.Ignore, decide(cand().copy(isOngoing = true)))
        assertEquals(DigestDecision.Ignore, decide(cand().copy(isClearable = false)))
    }

    @Test
    fun repostAfterDigestTimeIsReleasedNotReheld() {
        // Posted 11:00, snoozed, re-posted by the system at 12:00:01.
        assertEquals(DigestDecision.Record, decide(cand(post = millis(11, 0)), now = millis(12, 0, 1)))
        // Re-post a little early (clock skew) still counts as released.
        assertEquals(DigestDecision.Record, decide(cand(post = millis(11, 0)), now = millis(11, 59, 50)))
    }

    @Test
    fun newPostJustAfterDigestIsHeldUntilNext() {
        val d = decide(cand(post = millis(12, 0, 30)), now = millis(12, 0, 30))
        assertTrue(d is DigestDecision.Hold)
        assertEquals(millis(18, 0), (d as DigestDecision.Hold).releaseAtMillis)
    }

    @Test
    fun heldSummaryIsSnoozedToo() {
        assertTrue(decide(cand().copy(isGroupSummary = true)) is DigestDecision.Hold)
    }
}
