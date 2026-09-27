package com.mp01home.launcher.eink

import java.util.Locale

/** UX-6: which e-ink panel family's refresh API to use. */
enum class EinkVendor { ONYX, BIGME, HISENSE, GENERIC }

/** UX-6 user setting: AUTO detects from Build.MANUFACTURER / BRAND, the rest force a vendor. */
enum class VendorRefreshMode { AUTO, ONYX, BIGME, HISENSE, GENERIC }

/** Pure device detection so it can be unit-tested; pass Build.MANUFACTURER / Build.BRAND. */
object VendorDetection {

    fun detect(manufacturer: String?, brand: String?): EinkVendor {
        val id = listOfNotNull(manufacturer, brand).joinToString(" ").lowercase(Locale.ROOT)
        return when {
            "onyx" in id || "boox" in id -> EinkVendor.ONYX
            "bigme" in id -> EinkVendor.BIGME
            "hisense" in id -> EinkVendor.HISENSE
            else -> EinkVendor.GENERIC
        }
    }

    fun resolve(mode: VendorRefreshMode, detected: EinkVendor): EinkVendor = when (mode) {
        VendorRefreshMode.AUTO -> detected
        VendorRefreshMode.ONYX -> EinkVendor.ONYX
        VendorRefreshMode.BIGME -> EinkVendor.BIGME
        VendorRefreshMode.HISENSE -> EinkVendor.HISENSE
        VendorRefreshMode.GENERIC -> EinkVendor.GENERIC
    }
}
