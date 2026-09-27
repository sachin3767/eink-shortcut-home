package com.mp01home.launcher.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * CORE-2: battery percentage from the sticky ACTION_BATTERY_CHANGED broadcast.
 *
 * The broadcast fires often (voltage/temperature changes); [distinctUntilChanged] makes sure the
 * UI only redraws when the *percentage* changes (EINK-4). The receiver is registered only while
 * the flow is collected.
 */
fun batteryPercentFlow(context: Context): Flow<Int> = callbackFlow {
    val appContext = context.applicationContext
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            batteryPercentOf(intent)?.let { trySend(it) }
        }
    }
    val sticky: Intent? = ContextCompat.registerReceiver(
        appContext,
        receiver,
        IntentFilter(Intent.ACTION_BATTERY_CHANGED),
        ContextCompat.RECEIVER_NOT_EXPORTED
    )
    batteryPercentOf(sticky)?.let { trySend(it) }
    awaitClose { runCatching { appContext.unregisterReceiver(receiver) } }
}.distinctUntilChanged()

/** Reads the percentage from an ACTION_BATTERY_CHANGED intent, or null if unavailable. */
fun batteryPercentOf(intent: Intent?): Int? {
    if (intent == null) return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level >= 0 && scale > 0) (level * 100 / scale) else null
}
