package com.mp01home.launcher.eink

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mp01home.launcher.data.settings.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.extrasDataStore by preferencesDataStore(name = "mp01_extras")

/**
 * DataStore for [ExtrasPrefs]. Kept out of LauncherSettings on purpose (feature-private file);
 * included in the CORE-7 backup by [com.mp01home.launcher.backup.SettingsBackupCodec].
 */
class ExtrasStore(context: Context) {

    private val store = context.applicationContext.extrasDataStore

    private object Keys {
        val SLEEP_SCREEN = booleanPreferencesKey("sleep_screen_enabled")
        val VENDOR_MODE = stringPreferencesKey("vendor_refresh_mode")
        val READING_APPS = stringPreferencesKey("reading_apps")
    }

    val prefs: Flow<ExtrasPrefs> = store.data.map { p ->
        val d = ExtrasPrefs()
        ExtrasPrefs(
            sleepScreenEnabled = p[Keys.SLEEP_SCREEN] ?: d.sleepScreenEnabled,
            vendorRefreshMode = SettingsCodec.decodeEnum(p[Keys.VENDOR_MODE], d.vendorRefreshMode),
            readingApps = SettingsCodec.decodeOrderedList(p[Keys.READING_APPS]).take(ExtrasPrefs.MAX_READING_APPS)
        )
    }.distinctUntilChanged()

    suspend fun current(): ExtrasPrefs = prefs.first()

    suspend fun setSleepScreenEnabled(enabled: Boolean) {
        store.edit { it[Keys.SLEEP_SCREEN] = enabled }
    }

    suspend fun setVendorRefreshMode(mode: VendorRefreshMode) {
        store.edit { it[Keys.VENDOR_MODE] = mode.name }
    }

    suspend fun setReadingApps(packages: List<String>) {
        store.edit {
            it[Keys.READING_APPS] = SettingsCodec.encodeOrderedList(packages.take(ExtrasPrefs.MAX_READING_APPS))
        }
    }

    suspend fun addReadingApp(packageName: String) {
        store.edit { p ->
            val current = SettingsCodec.decodeOrderedList(p[Keys.READING_APPS])
            if (packageName in current || current.size >= ExtrasPrefs.MAX_READING_APPS) return@edit
            p[Keys.READING_APPS] = SettingsCodec.encodeOrderedList(current + packageName)
        }
    }

    suspend fun removeReadingApp(packageName: String) {
        store.edit { p ->
            val current = SettingsCodec.decodeOrderedList(p[Keys.READING_APPS])
            p[Keys.READING_APPS] = SettingsCodec.encodeOrderedList(current - packageName)
        }
    }

    /** Replaces everything (CORE-7 import). */
    suspend fun replaceAll(prefs: ExtrasPrefs) {
        store.edit {
            it[Keys.SLEEP_SCREEN] = prefs.sleepScreenEnabled
            it[Keys.VENDOR_MODE] = prefs.vendorRefreshMode.name
            it[Keys.READING_APPS] = SettingsCodec.encodeOrderedList(prefs.readingApps.take(ExtrasPrefs.MAX_READING_APPS))
        }
    }
}

/** Well-known reading / e-reader apps offered as one-tap suggestions (EXT-3). */
object ReadingAppSuggestions {
    val PACKAGES: List<String> = listOf(
        "com.amazon.kindle",
        "org.koreader.launcher",
        "com.google.android.apps.books",
        "com.kobobooks.android",
        "com.overdrive.mobile.android.libby",
        "org.readera",
        "com.flyersoft.moonreader",
        "com.foobnix.pdf.reader",
        "com.ideashower.readitlater.pro",
        "com.instapaper.android",
        "com.readwise.reader",
        "com.onyx.kreader",
        "com.devhd.feedly"
    )
}
