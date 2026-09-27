package com.mp01home.launcher.data.apps

import android.content.Intent
import android.content.pm.PackageManager

/**
 * CORE-3 defaults: Calendar, Tasks, Gmail, Keep — each resolved to an installed package.
 * Google apps are preferred; Calendar and mail fall back to the system's default app for the
 * matching category. Missing apps are simply skipped.
 */
object DockDefaults {
    const val GOOGLE_CALENDAR = "com.google.android.calendar"
    const val GOOGLE_TASKS = "com.google.android.apps.tasks"
    const val GMAIL = "com.google.android.gm"
    const val GOOGLE_KEEP = "com.google.android.keep"

    fun resolve(pm: PackageManager): List<String> = listOfNotNull(
        installed(pm, GOOGLE_CALENDAR) ?: defaultForCategory(pm, Intent.CATEGORY_APP_CALENDAR),
        installed(pm, GOOGLE_TASKS),
        installed(pm, GMAIL) ?: defaultForCategory(pm, Intent.CATEGORY_APP_EMAIL),
        installed(pm, GOOGLE_KEEP)
    ).distinct()

    /** [packageName] if it is installed and launchable, else null. */
    fun installed(pm: PackageManager, packageName: String): String? =
        packageName.takeIf { pm.getLaunchIntentForPackage(it) != null }

    private fun defaultForCategory(pm: PackageManager, category: String): String? {
        val intent = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, category)
        val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
            ?: return null
        // "android" = the disambiguation chooser, i.e. no single default.
        return if (pkg == "android") null else installed(pm, pkg)
    }
}
