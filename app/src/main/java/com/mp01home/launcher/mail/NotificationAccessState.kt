package com.mp01home.launcher.mail

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 * Whether notification access is granted, re-checked each time the screen resumes (the user
 * grants it in system settings and comes back; the system binds the listener itself).
 */
@Composable
fun rememberNotificationAccessGranted(): Boolean {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val granted = remember(resumed) { MailNotificationListener.isAccessGranted(context) }
    return granted
}

/** Opens the system "Notification access" screen. */
fun openNotificationAccessSettings(context: Context) {
    runCatching { context.startActivity(MailNotificationListener.accessSettingsIntent()) }
}
