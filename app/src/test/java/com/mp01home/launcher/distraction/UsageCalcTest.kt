package com.mp01home.launcher.distraction

import com.mp01home.launcher.distraction.UsageEventType.BACKGROUND
import com.mp01home.launcher.distraction.UsageEventType.FOREGROUND
import com.mp01home.launcher.distraction.UsageEventType.SCREEN_OFF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageCalcTest {

    private val min = 60_000L
    private fun e(pkg: String, type: UsageEventType, minute: Long) = UsageEventLite(pkg, type, minute * min)

    @Test
    fun simpleSessions() {
        val events = listOf(
            e("chrome", FOREGROUND, 10), e("chrome", BACKGROUND, 40),
            e("mail", FOREGROUND, 50), e("mail", BACKGROUND, 55)
        )
        val result = foregroundMillisByPackage(events, 0, 100 * min)
        assertEquals(30 * min, result["chrome"])
        assertEquals(5 * min, result["mail"])
    }

    @Test
    fun appOpenAtWindowStartAndStillOpenNow() {
        val events = listOf(e("reader", BACKGROUND, 5), e("chrome", FOREGROUND, 90))
        val result = foregroundMillisByPackage(events, 0, 100 * min)
        assertEquals(5 * min, result["reader"])
        assertEquals(10 * min, result["chrome"])
    }

    @Test
    fun newForegroundClosesPreviousAndScreenOffClosesAll() {
        val events = listOf(
            e("a", FOREGROUND, 0), e("b", FOREGROUND, 10), e("a", BACKGROUND, 11),
            e("b", BACKGROUND, 20), e("c", FOREGROUND, 30), e("x", SCREEN_OFF, 35)
        )
        val result = foregroundMillisByPackage(events, 0, 100 * min)
        assertEquals(10 * min, result["a"])
        assertEquals(10 * min, result["b"])
        assertEquals(5 * min, result["c"])
    }

    @Test
    fun summaryExcludesLauncherAndPicksTop() {
        val s = summarize(mapOf("launcher" to 90 * min, "chrome" to 50 * min, "mail" to 22 * min), setOf("launcher"))
        assertEquals(72 * min, s.totalMillis)
        assertEquals("chrome", s.topPackage)
    }

    @Test
    fun durationAndLine() {
        assertEquals("0m", formatDuration(0))
        assertEquals("12m", formatDuration(12 * min + 59_000))
        assertEquals("1h12m", formatDuration(72 * min))
        assertEquals("1h05m", formatDuration(65 * min))
        assertEquals(
            "today: 1h12m, top: Chrome",
            formatScreenTimeLine(ScreenTimeSummary(72 * min, "com.android.chrome", 50 * min), "Chrome")
        )
        assertEquals("today: 0m", formatScreenTimeLine(ScreenTimeSummary(0, null, 0), null))
    }

    @Test
    fun limitsCodecRoundTrip() {
        val limits = mapOf("b.pkg" to 30, "a.pkg" to 15, "zero" to 0)
        val raw = encodeLimits(limits)
        assertEquals("a.pkg=15,b.pkg=30", raw)
        assertEquals(mapOf("a.pkg" to 15, "b.pkg" to 30), decodeLimits(raw))
        assertEquals(emptyMap<String, Int>(), decodeLimits(null))
        assertEquals(mapOf("ok" to 5), decodeLimits("ok=5,bad,=3,x=abc,y=-1"))
    }

    @Test
    fun limitSteps() {
        assertEquals(5, nextLimitStep(0, up = true))
        assertEquals(0, nextLimitStep(5, up = false))
        assertEquals(0, nextLimitStep(0, up = false))
        assertEquals(180, nextLimitStep(180, up = true))
        assertEquals(30, nextLimitStep(25, up = true))
        assertEquals(20, nextLimitStep(25, up = false))
        assertEquals("off", formatLimit(0))
        assertEquals("1h30m", formatLimit(90))
    }

    @Test
    fun gateDecision() {
        assertEquals(GateDecision.Allow, decideGate("a", emptySet(), emptyMap(), null))
        assertEquals(GateDecision.Friction, decideGate("a", setOf("a"), mapOf("a" to 30), 10 * min))
        assertEquals(GateDecision.LimitReached(30 * min, 30), decideGate("a", emptySet(), mapOf("a" to 30), 30 * min))
        // Unknown usage (no permission) never blocks.
        assertEquals(GateDecision.Allow, decideGate("a", emptySet(), mapOf("a" to 30), null))
    }

    @Test
    fun reason() {
        assertFalse(isReasonAcceptable("  a "))
        assertTrue(isReasonAcceptable("reply to Sam"))
    }
}
