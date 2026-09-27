package com.mp01home.launcher.distraction

/*
 * DIS-2 / DIS-3 pure logic (no Android types) so it can be unit-tested on the JVM.
 */

/** A foreground/background usage event, mapped from android.app.usage.UsageEvents.Event. */
data class UsageEventLite(val packageName: String, val type: UsageEventType, val timeMillis: Long)

enum class UsageEventType {
    /** ACTIVITY_RESUMED (a.k.a. MOVE_TO_FOREGROUND). */
    FOREGROUND,
    /** ACTIVITY_PAUSED / ACTIVITY_STOPPED (a.k.a. MOVE_TO_BACKGROUND). */
    BACKGROUND,
    /** SCREEN_NON_INTERACTIVE / DEVICE_SHUTDOWN: closes whatever is open. */
    SCREEN_OFF
}

/**
 * Foreground time per package between [windowStart] and [nowMillis] from a time-ordered event
 * stream. An app already in the foreground at [windowStart] (its first event is BACKGROUND)
 * is credited from [windowStart]; an app still open counts until [nowMillis]. Only one app is
 * treated as foreground at a time: a new FOREGROUND closes the previous app.
 */
fun foregroundMillisByPackage(
    events: List<UsageEventLite>,
    windowStart: Long,
    nowMillis: Long
): Map<String, Long> {
    val totals = HashMap<String, Long>()
    var openPkg: String? = null
    var openSince = windowStart
    val seen = HashSet<String>()

    fun add(pkg: String, from: Long, to: Long) {
        val start = maxOf(from, windowStart)
        val end = minOf(to, nowMillis)
        if (end > start) totals[pkg] = (totals[pkg] ?: 0L) + (end - start)
    }

    for (event in events.sortedBy { it.timeMillis }) {
        if (event.timeMillis > nowMillis) break
        when (event.type) {
            UsageEventType.FOREGROUND -> {
                val open = openPkg
                if (open != null && open != event.packageName) add(open, openSince, event.timeMillis)
                if (open != event.packageName) {
                    openPkg = event.packageName
                    openSince = event.timeMillis
                }
            }
            UsageEventType.BACKGROUND -> {
                if (openPkg == event.packageName) {
                    add(event.packageName, openSince, event.timeMillis)
                    openPkg = null
                } else if (openPkg == null && event.packageName !in seen) {
                    // Was already open when the window started.
                    add(event.packageName, windowStart, event.timeMillis)
                }
            }
            UsageEventType.SCREEN_OFF -> {
                openPkg?.let { add(it, openSince, event.timeMillis) }
                openPkg = null
            }
        }
        seen += event.packageName
    }
    openPkg?.let { add(it, openSince, nowMillis) }
    return totals
}

/** Today's screen-time summary. */
data class ScreenTimeSummary(val totalMillis: Long, val topPackage: String?, val topMillis: Long)

fun summarize(byPackage: Map<String, Long>, excluded: Set<String> = emptySet()): ScreenTimeSummary {
    val filtered = byPackage.filterKeys { it !in excluded }.filterValues { it > 0 }
    val top = filtered.maxByOrNull { it.value }
    return ScreenTimeSummary(
        totalMillis = filtered.values.sum(),
        topPackage = top?.key,
        topMillis = top?.value ?: 0L
    )
}

/** "0m", "12m", "1h12m", "1h05m" (minutes are zero-padded once hours appear). */
fun formatDuration(millis: Long): String {
    val totalMinutes = (millis.coerceAtLeast(0L) / 60_000L).toInt()
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return if (h == 0) "${m}m" else "${h}h${m.toString().padStart(2, '0')}m"
}

/** DIS-3: "today: 1h12m, top: Chrome" (or "today: 0m" when nothing was used). */
fun formatScreenTimeLine(summary: ScreenTimeSummary, topLabel: String?): String {
    val base = "today: ${formatDuration(summary.totalMillis)}"
    val label = topLabel ?: summary.topPackage
    return if (label != null && summary.topMillis >= 60_000L) "$base, top: $label" else base
}

// ---- DIS-2 per-app daily limits -------------------------------------------------------------

/** Encodes {pkg → minutes} as "pkg=30,pkg2=45" (sorted, positive minutes only). */
fun encodeLimits(limits: Map<String, Int>): String =
    limits.filter { it.value > 0 && it.key.isNotBlank() }
        .toSortedMap()
        .entries
        .joinToString(",") { "${it.key}=${it.value}" }

fun decodeLimits(raw: String?): Map<String, Int> {
    if (raw.isNullOrBlank()) return emptyMap()
    return raw.split(',')
        .mapNotNull { part ->
            val idx = part.lastIndexOf('=')
            if (idx <= 0) return@mapNotNull null
            val pkg = part.substring(0, idx).trim()
            val minutes = part.substring(idx + 1).trim().toIntOrNull() ?: return@mapNotNull null
            if (pkg.isEmpty() || minutes <= 0) null else pkg to minutes
        }
        .toMap()
}

/** Minute steps offered in settings for a daily limit (0 = no limit). */
val LIMIT_STEPS: List<Int> = listOf(0, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180)

fun nextLimitStep(current: Int, up: Boolean): Int {
    val idx = LIMIT_STEPS.indexOfFirst { it >= current }.let { if (it < 0) LIMIT_STEPS.lastIndex else it }
    val exact = LIMIT_STEPS.getOrNull(idx) == current
    return if (up) {
        LIMIT_STEPS[(if (exact) idx + 1 else idx).coerceAtMost(LIMIT_STEPS.lastIndex)]
    } else {
        LIMIT_STEPS[(idx - 1).coerceAtLeast(0)]
    }
}

fun formatLimit(minutes: Int): String = if (minutes <= 0) "off" else formatDuration(minutes * 60_000L)

/** What the gate should do for a launch. */
sealed interface GateDecision {
    data object Allow : GateDecision
    /** DIS-1: pause + reason, then allow. */
    data object Friction : GateDecision
    /** DIS-2: blocked for today. */
    data class LimitReached(val usedMillis: Long, val limitMinutes: Int) : GateDecision
}

fun decideGate(
    packageName: String,
    frictionApps: Set<String>,
    limits: Map<String, Int>,
    usedTodayMillis: Long?
): GateDecision {
    val limit = limits[packageName] ?: 0
    if (limit > 0 && usedTodayMillis != null && usedTodayMillis >= limit * 60_000L) {
        return GateDecision.LimitReached(usedTodayMillis, limit)
    }
    return if (packageName in frictionApps) GateDecision.Friction else GateDecision.Allow
}

/** DIS-1: the reason must be a real sentence fragment, not a single keystroke. */
const val MIN_REASON_LENGTH = 3

fun isReasonAcceptable(reason: String): Boolean = reason.trim().length >= MIN_REASON_LENGTH
