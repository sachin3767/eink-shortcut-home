package com.mp01home.launcher.mail

import android.service.notification.StatusBarNotification

/**
 * Receives notifications from [MailNotificationListener]. Features (MAIL-1/2, FOC-4 digest)
 * implement this and register via `AppContainer.registerNotificationHandler(...)`.
 *
 * Callbacks run on the listener's main thread: filter by package quickly and hand real work
 * (Room writes) to `AppContainer.appScope`.
 */
interface NotificationHandler {
    /** Full set of currently active notifications (on connect / reconnect). */
    fun onSnapshot(active: List<StatusBarNotification>) {}

    fun onPosted(sbn: StatusBarNotification) {}

    fun onRemoved(sbn: StatusBarNotification) {}
}
