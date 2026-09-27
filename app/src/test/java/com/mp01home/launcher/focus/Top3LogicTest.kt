package com.mp01home.launcher.focus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Top3LogicTest {

    private val yesterday = listOf(
        PriorityItem(0, "Send invoice", done = true),
        PriorityItem(1, "Call Sam", linkedTaskId = "t1"),
        PriorityItem(2, "  ")
    )

    @Test
    fun candidatesAreUnfinishedNonBlankInSlotOrder() {
        val c = carryOverCandidates(
            listOf(PriorityItem(2, "C"), PriorityItem(0, "A"), PriorityItem(1, "B", done = true))
        )
        assertEquals(listOf("A", "C"), c.map { it.text })
    }

    @Test
    fun offerOnlyWhenNotAnsweredAndRoomLeft() {
        val c = carryOverCandidates(yesterday)
        assertTrue(shouldOfferCarryOver(c, emptyList(), null, "2026-09-26"))
        assertFalse(shouldOfferCarryOver(c, emptyList(), "2026-09-26", "2026-09-26"))
        assertTrue(shouldOfferCarryOver(c, emptyList(), "2026-09-25", "2026-09-26"))
        val full = listOf(PriorityItem(0, "x"), PriorityItem(1, "y"), PriorityItem(2, "z"))
        assertFalse(shouldOfferCarryOver(c, full, null, "2026-09-26"))
        assertFalse(shouldOfferCarryOver(emptyList(), emptyList(), null, "2026-09-26"))
    }

    @Test
    fun noOfferWhenAllCandidatesAlreadyPresentToday() {
        val c = carryOverCandidates(yesterday)
        assertFalse(shouldOfferCarryOver(c, listOf(PriorityItem(0, "call sam")), null, "2026-09-26"))
    }

    @Test
    fun planFillsLowestFreeSlotsAndKeepsLink() {
        val today = listOf(PriorityItem(0, "Write spec"))
        val planned = planCarryOver(carryOverCandidates(yesterday), today)
        assertEquals(listOf(PriorityItem(1, "Call Sam", linkedTaskId = "t1")), planned)
    }

    @Test
    fun planStopsWhenFullAndSkipsDuplicates() {
        val candidates = listOf(PriorityItem(0, "A"), PriorityItem(1, "B"), PriorityItem(2, "C"))
        val today = listOf(PriorityItem(1, "b"), PriorityItem(2, "Other"))
        assertEquals(listOf(PriorityItem(0, "A")), planCarryOver(candidates, today))
    }

    @Test
    fun blankTodayItemsCountAsFree() {
        assertEquals(listOf(0, 2), freeSlots(listOf(PriorityItem(0, " "), PriorityItem(1, "x"))))
    }

    @Test
    fun normalizeCompactsAndCaps() {
        val result = normalizeTop3(listOf("" to null, " A " to "t", "B" to null, "C" to null, "D" to null))
        assertEquals(
            listOf(PriorityItem(0, "A", "t"), PriorityItem(1, "B"), PriorityItem(2, "C")),
            result
        )
    }
}
