package com.mp01home.launcher.distraction

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import android.provider.Settings
import android.util.Log
import java.time.LocalDate
import java.time.ZoneId

/**
 * DIS-2 / DIS-3: today's per-app foreground time from [UsageStatsManager] events.
 * Needs "Usage access" (PACKAGE_USAGE_STATS app-op), granted by the user in system settings.
 * Queries are synchronous; call from a background thread except for the single-package
 * check done by the launch gate.
 */
class UsageStatsRepository(private val context: Context) {

    fun hasAccess(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Fallback if the package-specific screen is missing on this ROM. */
    fun genericAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Foreground millis per package since local midnight, or null without usage access. */
    fun usageToday(nowMillis: Long = System.currentTimeMillis()): Map<String, Long>? {
        if (!hasAccess()) return null
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        return try {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val events = usm.queryEvents(start, nowMillis)
            val list = ArrayList<UsageEventLite>()
            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val type = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.FOREGROUND
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> UsageEventType.BACKGROUND
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.SCREEN_OFF
                    else -> null
                } ?: continue
                list += UsageEventLite(event.packageName ?: continue, type, event.timeStamp)
            }
            foregroundMillisByPackage(list, start, nowMillis)
        } catch (e: SecurityException) {
            Log.w(TAG, "usage query denied", e)
            null
        }
    }

    /** Today's millis for one package (null without access). */
    fun usageTodayFor(packageName: String): Long? = usageToday()?.let { it[packageName] ?: 0L }

    /** DIS-3 summary, excluding this launcher itself. */
    fun summaryToday(): ScreenTimeSummary? =
        usageToday()?.let { summarize(it, excluded = setOf(context.packageName)) }

    private companion object {
        const val TAG = "UsageStatsRepository"
    }
}
