package com.mp01home.launcher.focus

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * FOC-3: switches Do Not Disturb (priority only) on while a focus phase runs and restores the
 * previous filter afterwards. Requires "Do Not Disturb access" (notification policy access),
 * which the user grants in system settings via [accessSettingsIntent].
 *
 * If DND was already on when focus started we leave it alone and do not restore anything.
 */
class DndController(private val context: Context, private val store: FocusStateStore) {

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(): Boolean = runCatching { notificationManager.isNotificationPolicyAccessGranted }.getOrDefault(false)

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** @return true if DND is now on because of us. */
    suspend fun enable(): Boolean {
        if (!hasAccess()) return false
        if (store.dndPreviousFilter() != null) return true // already applied
        val nm = notificationManager
        val current = nm.currentInterruptionFilter
        if (current != NotificationManager.INTERRUPTION_FILTER_ALL &&
            current != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        ) {
            return false // user already has DND on: not ours to restore
        }
        return try {
            store.setDndApplied(current)
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot enable DND", e)
            store.clearDndApplied()
            false
        }
    }

    /** Restores the filter we replaced, if any. Safe to call repeatedly. */
    suspend fun restore() {
        val previous = store.dndPreviousFilter() ?: return
        try {
            val nm = notificationManager
            // Only undo our own change: if the user switched DND mode meanwhile, respect it.
            if (hasAccess() && nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                val target = if (previous == NotificationManager.INTERRUPTION_FILTER_UNKNOWN) {
                    NotificationManager.INTERRUPTION_FILTER_ALL
                } else previous
                nm.setInterruptionFilter(target)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "cannot restore DND", e)
        } finally {
            store.clearDndApplied()
        }
    }

    private companion object {
        const val TAG = "DndController"
    }
}
