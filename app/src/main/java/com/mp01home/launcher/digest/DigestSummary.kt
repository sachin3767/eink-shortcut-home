package com.mp01home.launcher.digest

import java.time.LocalTime
import java.time.ZoneId

/** A cached digest row, Android/Room-free (mapped from `DigestNotification`). */
data class DigestRow(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String?,
    val postedAtMillis: Long,
    val released: Boolean
)

/** One app in the home digest: "Slack  4  Re: deploy". */
data class DigestGroup(
    val packageName: String,
    val appLabel: String,
    val count: Int,
    val latestTitle: String,
    val latestMillis: Long,
    val keys: List<String>
)

data class DigestView(
    /** Visible groups, newest first. */
    val groups: List<DigestGroup>,
    /** Notifications still held back until [nextReleaseMillis]. */
    val heldCount: Int,
    val heldApps: Int,
    val nextReleaseMillis: Long?
) {
    val totalVisible: Int get() = groups.sumOf { it.count }

    companion object {
        val EMPTY = DigestView(emptyList(), 0, 0, null)
    }
}

/** FOC-4: pure batching of cached rows into what DigestSection shows. */
object DigestSummarizer {

    fun summarize(rows: List<DigestRow>, nowMillis: Long, zone: ZoneId, digestTimes: List<LocalTime>): DigestView {
        val previous = DigestSchedule.previousMillis(nowMillis, zone, digestTimes)
        val (visible, held) = rows.partition { row ->
            row.released || (previous != null && row.postedAtMillis < previous) || digestTimes.isEmpty()
        }
        val groups = visible
            .groupBy { it.packageName }
            .map { (pkg, appRows) ->
                val sorted = appRows.sortedByDescending { it.postedAtMillis }
                val latest = sorted.first()
                DigestGroup(
                    packageName = pkg,
                    appLabel = latest.appLabel.ifBlank { pkg },
                    count = sorted.size,
                    latestTitle = latest.title.ifBlank { latest.text.orEmpty() },
                    latestMillis = latest.postedAtMillis,
                    keys = sorted.map { it.key }
                )
            }
            .sortedByDescending { it.latestMillis }
        return DigestView(
            groups = groups,
            heldCount = held.size,
            heldApps = held.map { it.packageName }.distinct().size,
            nextReleaseMillis = DigestSchedule.nextMillis(nowMillis, zone, digestTimes)
        )
    }
}
