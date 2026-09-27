package com.mp01home.launcher.review

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mp01home.launcher.appContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime

private val Context.reviewStateStore by preferencesDataStore(name = "mp01_review_state")

/** Feature-private state for PLAN-1/2: the day each review was last shown automatically. */
class ReviewStateStore(context: Context) {

    private val store = context.applicationContext.reviewStateStore

    private object Keys {
        val LAST_MORNING = stringPreferencesKey("last_morning_shown")
        val LAST_EVENING = stringPreferencesKey("last_evening_shown")
    }

    suspend fun lastShown(kind: ReviewKind): LocalDate? {
        val raw = store.data.first()[keyFor(kind)] ?: return null
        return runCatching { LocalDate.parse(raw) }.getOrNull()
    }

    suspend fun markShown(kind: ReviewKind, date: LocalDate) {
        store.edit { it[keyFor(kind)] = date.toString() }
    }

    private fun keyFor(kind: ReviewKind) = when (kind) {
        ReviewKind.MORNING -> Keys.LAST_MORNING
        ReviewKind.EVENING -> Keys.LAST_EVENING
    }
}

/**
 * Evaluates [ReviewTrigger] against the current settings and, if a review is due, records it as
 * shown and returns it. Serialised so two quick resumes cannot both open a review.
 */
object ReviewAutoLauncher {

    private val mutex = Mutex()

    suspend fun claimDueReview(context: Context, now: LocalDateTime = LocalDateTime.now()): ReviewKind? =
        mutex.withLock {
            val settings = context.appContainer.settingsRepository.current()
            val store = ReviewStateStore(context)
            val schedule = ReviewSchedule(
                morningEnabled = settings.morningReviewEnabled,
                morningTime = settings.morningReviewTime,
                eveningEnabled = settings.eveningReviewEnabled,
                eveningTime = settings.eveningReviewTime
            )
            val due = ReviewTrigger.reviewToShow(
                now = now,
                schedule = schedule,
                lastMorningShown = store.lastShown(ReviewKind.MORNING),
                lastEveningShown = store.lastShown(ReviewKind.EVENING)
            ) ?: return@withLock null
            store.markShown(due, now.toLocalDate())
            due
        }
}
