package com.mp01home.launcher.sync

import com.mp01home.launcher.data.settings.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

class SyncedLabelTest {

    private val now = ZonedDateTime.of(2026, 9, 26, 14, 0, 0, 0, ZoneOffset.UTC)

    private fun millis(day: Int, hour: Int, minute: Int): Long =
        ZonedDateTime.of(2026, 9, day, hour, minute, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun neverSynced() {
        assertEquals("not synced", formatSyncedLabel(null, now = now, locale = Locale.US))
        assertEquals("not synced", formatSyncedLabel(0L, now = now, locale = Locale.US))
    }

    @Test
    fun sameDayShowsTimeOnly() {
        assertEquals("synced 10:42", formatSyncedLabel(millis(26, 10, 42), now = now, locale = Locale.US))
    }

    @Test
    fun twelveHourFormat() {
        assertEquals(
            "synced 1:05 PM",
            formatSyncedLabel(millis(26, 13, 5), TimeFormat.HOUR_12, now = now, locale = Locale.US)
        )
    }

    @Test
    fun otherDayShowsDate() {
        assertEquals("synced 24 Sep 09:30", formatSyncedLabel(millis(24, 9, 30), now = now, locale = Locale.US))
    }
}
