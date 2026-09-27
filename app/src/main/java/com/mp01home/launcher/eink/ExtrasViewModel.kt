package com.mp01home.launcher.eink

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.data.apps.AppInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Shared by the Extras settings slot (UX-5, UX-6, EXT-3) and the home ReadingSection (EXT-3).
 */
class ExtrasViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val store = container.extrasStore

    val prefs: StateFlow<ExtrasPrefs> = store.prefs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExtrasPrefs())

    /** EXT-3 configured reading apps with labels; uninstalled packages are dropped. */
    val readingApps: StateFlow<List<AppInfo>> = store.prefs
        .map { it.readingApps }
        .distinctUntilChanged()
        .map { packages ->
            packages.mapNotNull { pkg ->
                container.installedAppsRepository.resolveLabel(pkg)?.let { AppInfo(pkg, it) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    /** Launchable apps, loaded on demand by [loadInstalledApps] (settings only). */
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val detectedVendor: EinkVendor = VendorRefresh.detectedVendor()

    fun loadInstalledApps() {
        if (_installedApps.value.isNotEmpty()) return
        viewModelScope.launch {
            _installedApps.value = container.installedAppsRepository.getAllLaunchableApps()
        }
    }

    /** UX-5: turning on renders immediately; turning off restores the system lock screen. */
    fun setSleepScreenEnabled(enabled: Boolean) {
        viewModelScope.launch {
            store.setSleepScreenEnabled(enabled)
            if (enabled) {
                _message.value = "Updating lock screen…"
                val ok = container.sleepScreenUpdater.updateSafely(force = true)
                _message.value = if (ok) "Lock screen updated" else "Could not set the lock screen wallpaper"
            } else {
                container.sleepScreenUpdater.clear()
                _message.value = null
            }
        }
    }

    fun setVendorRefreshMode(mode: VendorRefreshMode) {
        viewModelScope.launch { store.setVendorRefreshMode(mode) }
    }

    fun addReadingApp(packageName: String) {
        viewModelScope.launch { store.addReadingApp(packageName) }
    }

    fun removeReadingApp(packageName: String) {
        viewModelScope.launch { store.removeReadingApp(packageName) }
    }

    fun launch(packageName: String) {
        container.appLauncher.launch(packageName)
    }

    /** EINK-5 / UX-6: runs the vendor hook (or the generic flash) right now. */
    fun testFullRefresh() {
        container.einkRefresh.requestFullRefresh()
    }
}
