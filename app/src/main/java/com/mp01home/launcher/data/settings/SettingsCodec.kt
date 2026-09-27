package com.mp01home.launcher.data.settings

import java.time.LocalTime

/**
 * Pure encode/decode helpers for DataStore values (kept free of Android types so they are
 * unit-testable on the JVM).
 */
internal object SettingsCodec {

    /** Ordered lists are stored comma-joined (Preferences string sets are unordered). */
    fun decodeOrderedList(raw: String?): List<String> =
        raw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }?.distinct() ?: emptyList()

    fun encodeOrderedList(values: List<String>): String =
        values.map { it.trim() }.filter { it.isNotEmpty() && !it.contains(',') }.distinct().joinToString(",")

    /** "HH:mm" → LocalTime, null if malformed. */
    fun decodeTime(raw: String?): LocalTime? = raw?.let {
        runCatching { LocalTime.parse(it.trim()) }.getOrNull()
    }

    fun encodeTime(time: LocalTime): String = "%02d:%02d".format(time.hour, time.minute)

    fun decodeTimes(raw: String?): List<LocalTime>? =
        raw?.let { decodeOrderedList(it).mapNotNull(::decodeTime).distinct().sorted() }

    fun encodeTimes(times: List<LocalTime>): String =
        times.distinct().sorted().joinToString(",") { encodeTime(it) }

    fun decodeLongSet(raw: Set<String>?): Set<Long> =
        raw?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

    inline fun <reified E : Enum<E>> decodeEnum(raw: String?, default: E): E =
        raw?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    inline fun <reified E : Enum<E>> decodeEnumSet(raw: Set<String>?, default: Set<E>): Set<E> =
        raw?.mapNotNull { name -> enumValues<E>().firstOrNull { it.name == name } }?.toSet() ?: default

    fun clampSyncMinutes(minutes: Int): Int =
        minutes.coerceIn(LauncherSettings.MIN_SYNC_MINUTES, LauncherSettings.MAX_SYNC_MINUTES)

    fun clampFontScale(scale: Float): Float =
        scale.coerceIn(LauncherSettings.MIN_FONT_SCALE, LauncherSettings.MAX_FONT_SCALE)
}
