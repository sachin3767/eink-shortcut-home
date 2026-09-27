package com.mp01home.launcher.eink

/** User settings owned by the extras feature (UX-5, UX-6, EXT-3). */
data class ExtrasPrefs(
    /** UX-5: render the agenda to the lock-screen wallpaper. */
    val sleepScreenEnabled: Boolean = false,
    /** UX-6: refresh API override (AUTO = detect from Build.MANUFACTURER). */
    val vendorRefreshMode: VendorRefreshMode = VendorRefreshMode.AUTO,
    /** EXT-3: ordered package names of reading / e-reader apps shown on home. */
    val readingApps: List<String> = emptyList()
) {
    companion object {
        const val MAX_READING_APPS = 4
    }
}
