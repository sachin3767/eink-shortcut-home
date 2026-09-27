package com.mp01home.launcher.eink

import org.junit.Assert.assertEquals
import org.junit.Test

class VendorDetectionTest {

    @Test
    fun detectsKnownVendors() {
        assertEquals(EinkVendor.ONYX, VendorDetection.detect("ONYX", "Onyx"))
        assertEquals(EinkVendor.ONYX, VendorDetection.detect("Qualcomm", "BOOX"))
        assertEquals(EinkVendor.BIGME, VendorDetection.detect("Bigme", null))
        assertEquals(EinkVendor.HISENSE, VendorDetection.detect("Hisense", "HiSense"))
    }

    @Test
    fun unknownDevicesUseGenericFallback() {
        assertEquals(EinkVendor.GENERIC, VendorDetection.detect("Minimal Company", "MP01"))
        assertEquals(EinkVendor.GENERIC, VendorDetection.detect(null, null))
    }

    @Test
    fun overrideWinsOverDetection() {
        assertEquals(EinkVendor.ONYX, VendorDetection.resolve(VendorRefreshMode.AUTO, EinkVendor.ONYX))
        assertEquals(EinkVendor.GENERIC, VendorDetection.resolve(VendorRefreshMode.GENERIC, EinkVendor.ONYX))
        assertEquals(EinkVendor.BIGME, VendorDetection.resolve(VendorRefreshMode.BIGME, EinkVendor.GENERIC))
    }
}
