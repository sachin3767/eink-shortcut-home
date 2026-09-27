package com.mp01home.launcher.eink

import android.os.Build
import android.view.View
import com.mp01home.launcher.util.VendorRefreshHook
import com.mp01home.launcher.util.VendorRefreshHooks

/**
 * UX-6 vendor refresh hooks. None of them links against a vendor SDK: everything is reflection,
 * so on other devices the lookups fail, the hook returns false and
 * [com.mp01home.launcher.util.EinkRefreshController] falls back to the generic black flash.
 * The Minimal Phone MP01 has no public refresh API (decision D3) and uses the generic path.
 */
object VendorRefresh {

    /** Onyx Boox: `EpdController.invalidate(view, UpdateMode.GC)`, then `repaintEveryThing(GC)`. */
    val onyx = VendorRefreshHook { view ->
        VendorRefreshHooks.onyx.fullRefresh(view) || onyxRepaintEverything()
    }

    private fun onyxRepaintEverything(): Boolean = runCatching {
        val controller = Class.forName("com.onyx.android.sdk.api.device.epd.EpdController")
        val modeClass = Class.forName("com.onyx.android.sdk.api.device.epd.UpdateMode")
        val gc = modeClass.getField("GC").get(null)
        controller.getMethod("repaintEveryThing", modeClass).invoke(null, gc)
        true
    }.getOrDefault(false)

    /**
     * Bigme: STUB. Bigme ships no public refresh SDK; its system full-refresh is a vendor key /
     * quick-setting. Returns false so the generic flash runs. Fill in if an API becomes known.
     */
    val bigme = VendorRefreshHook { _: View -> false }

    /** Hisense (A5/A7/A9): STUB, no public API known. Returns false → generic flash. */
    val hisense = VendorRefreshHook { _: View -> false }

    fun detectedVendor(): EinkVendor = VendorDetection.detect(Build.MANUFACTURER, Build.BRAND)

    /** Hooks for [vendor]; an empty list means "generic flash only". */
    fun hooksFor(vendor: EinkVendor): List<VendorRefreshHook> = when (vendor) {
        EinkVendor.ONYX -> listOf(onyx)
        EinkVendor.BIGME -> listOf(bigme)
        EinkVendor.HISENSE -> listOf(hisense)
        EinkVendor.GENERIC -> emptyList()
    }

    fun hooksFor(mode: VendorRefreshMode): List<VendorRefreshHook> =
        hooksFor(VendorDetection.resolve(mode, detectedVendor()))
}
