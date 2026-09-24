package com.mp01home.launcher.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InstalledAppsRepository(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager
    private val ownPackage: String get() = context.packageName

    suspend fun getAllLaunchableApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != ownPackage }
            .distinctBy { it.packageName }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    label = info.loadLabel(packageManager).toString()
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    fun launchApp(packageName: String) {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    suspend fun resolveLabel(packageName: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            packageManager.getApplicationInfo(packageName, 0).loadLabel(packageManager).toString()
        }.getOrNull()
    }
}
