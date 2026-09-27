package com.mp01home.launcher.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PartialUpdateCounterTest {

    @Test
    fun triggersOnTheNthUpdateAndAfterResetStartsOver() {
        val c = PartialUpdateCounter(everyN = 3)
        assertFalse(c.note())
        assertFalse(c.note())
        assertTrue(c.note())
        // Stays due (without overflowing) until the full refresh resets it.
        assertTrue(c.note())
        assertEquals(3, c.count)
        c.reset()
        assertFalse(c.note())
        assertEquals(1, c.count)
    }

    @Test
    fun zeroDisables() {
        val c = PartialUpdateCounter(everyN = 0)
        repeat(100) { assertFalse(c.note()) }
        assertEquals(0, c.count)
    }

    @Test
    fun loweringThresholdTakesEffectImmediately() {
        val c = PartialUpdateCounter(everyN = 10)
        repeat(5) { c.note() }
        c.everyN = 5
        assertTrue(c.note())
    }
}
