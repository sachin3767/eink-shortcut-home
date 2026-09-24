package com.mp01home.launcher.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.LocalDateTime

/**
 * Emits the current time once immediately, then once per minute boundary.
 * eInk panels ghost/flash on redraw, so the UI must never tick more often than this.
 */
fun minuteTicker(): Flow<LocalDateTime> = flow {
    while (true) {
        val now = LocalDateTime.now()
        emit(now)
        val secondsToNextMinute = 60 - now.second
        delay(secondsToNextMinute * 1000L)
    }
}
