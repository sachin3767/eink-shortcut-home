package com.mp01home.launcher.data.apps

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Hook for DIS-1 (friction gate) / DIS-2 (limits): return true if the launch was intercepted
 * (e.g. a gate screen was shown instead). The gate later calls
 * `launch(packageName, bypassInterceptor = true)` to actually open the app.
 */
fun interface LaunchInterceptor {
    fun intercept(packageName: String): Boolean
}

/** Single entry point for opening apps from Home, Dock and Drawer. */
class AppLauncher(private val context: Context) {

    @Volatile
    var interceptor: LaunchInterceptor? = null

    /** @return true if the app was started (or the launch was intercepted). */
    fun launch(packageName: String, bypassInterceptor: Boolean = false): Boolean {
        if (!bypassInterceptor && interceptor?.intercept(packageName) == true) return true
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return startActivitySafely(intent)
    }

    /** Starts any intent from a non-activity context; false if nothing can handle it. */
    fun startActivitySafely(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: Exception) {
        Log.w("AppLauncher", "cannot start $intent", e)
        false
    }
}
