package com.mp01home.launcher.mail

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.mp01home.launcher.appContainer

/**
 * The app's only NotificationListenerService (decision D1: Gmail via notifications only).
 * It holds no logic: it fans every event out to the [NotificationHandler]s registered in
 * [com.mp01home.launcher.AppContainer]. UI-free.
 */
class MailNotificationListener : NotificationListenerService() {

    private val handlers: List<NotificationHandler>
        get() = applicationContext.appContainer.notificationHandlers

    override fun onListenerConnected() {
        super.onListenerConnected()
        connected = this
        val active = runCatching { activeNotifications?.toList() }.getOrNull().orEmpty()
        dispatch { it.onSnapshot(active) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        dispatch { it.onPosted(sbn) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        if (sbn == null) return
        dispatch { it.onRemoved(sbn) }
    }

    override fun onListenerDisconnected() {
        if (connected === this) connected = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (connected === this) connected = null
        super.onDestroy()
    }

    private inline fun dispatch(block: (NotificationHandler) -> Unit) {
        for (handler in handlers) {
            try {
                block(handler)
            } catch (e: Exception) {
                Log.w(TAG, "handler ${handler.javaClass.simpleName} failed", e)
            }
        }
    }

    companion object {
        private const val TAG = "MailNotifListener"

        /** The bound listener instance, if connected (FOC-4 needs it to snooze). */
        @Volatile
        private var connected: MailNotificationListener? = null

        /**
         * FOC-4: snoozes an active notification for [durationMillis] (the system re-posts it
         * afterwards). Safe from any thread. False if the listener is not connected or it failed.
         */
        fun snooze(key: String, durationMillis: Long): Boolean {
            val listener = connected ?: return false
            return runCatching { listener.snoozeNotification(key, durationMillis) }
                .onFailure { Log.w(TAG, "snooze failed for $key", it) }
                .isSuccess
        }

        /** True if the user granted notification access to this app. */
        fun isAccessGranted(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        /** Opens the system screen where the user grants notification access. */
        fun accessSettingsIntent(): Intent =
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Asks the system to rebind the listener (e.g. after access was just granted). */
        fun requestRebind(context: Context) {
            runCatching {
                NotificationListenerService.requestRebind(ComponentName(context, MailNotificationListener::class.java))
            }
        }
    }
}
