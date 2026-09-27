package com.mp01home.launcher.mail

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Runs submitted suspend blocks one at a time, in submission order, on [scope].
 * Notification callbacks arrive on the main thread; this keeps post/remove events for the
 * same key from racing each other in Room.
 */
class SerialWorkQueue(scope: CoroutineScope, private val tag: String) {

    private val channel = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch {
            for (work in channel) {
                try {
                    work()
                } catch (e: Exception) {
                    Log.w(tag, "queued work failed", e)
                }
            }
        }
    }

    fun submit(work: suspend () -> Unit) {
        channel.trySend(work)
    }
}
