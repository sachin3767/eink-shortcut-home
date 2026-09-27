package com.mp01home.launcher.focus

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.mp01home.launcher.MainActivity

/** FOC-2: a single quiet notification when a focus or break phase ends. */
class FocusNotifier(private val context: Context) {

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Focus timer", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Focus and break phase endings"
                    enableVibration(true)
                }
            )
        }
    }

    fun notifyTransition(transition: PhaseTransition, breakMinutes: Int) {
        if (!canPost()) return
        val (title, text) = when (transition.from to transition.to) {
            FocusPhase.FOCUS to FocusPhase.BREAK -> "Focus done" to "Break: $breakMinutes min"
            FocusPhase.BREAK to FocusPhase.IDLE -> "Break over" to "Start another focus session when ready"
            FocusPhase.FOCUS to FocusPhase.IDLE -> "Focus done" to "Nice work"
            else -> return
        }
        ensureChannel()
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    private companion object {
        const val CHANNEL_ID = "focus_timer"
        const val NOTIFICATION_ID = 4201
    }
}
