package com.mp01home.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerPagingTest {

    @Test
    fun pageSizeFitsRowsAndIsAtLeastOne() {
        assertEquals(10, DrawerPaging.pageSize(availableHeightPx = 1000, rowHeightPx = 100))
        assertEquals(9, DrawerPaging.pageSize(availableHeightPx = 999, rowHeightPx = 100))
        assertEquals(1, DrawerPaging.pageSize(availableHeightPx = 10, rowHeightPx = 100))
        assertEquals(1, DrawerPaging.pageSize(availableHeightPx = 10, rowHeightPx = 0))
    }

    @Test
    fun pageCountAndSlices() {
        val items = (1..23).toList()
        assertEquals(3, DrawerPaging.pageCount(items.size, 10))
        assertEquals(1, DrawerPaging.pageCount(0, 10))
        assertEquals((1..10).toList(), DrawerPaging.page(items, 0, 10))
        assertEquals(listOf(21, 22, 23), DrawerPaging.page(items, 2, 10))
        // Out-of-range pages clamp instead of going blank.
        assertEquals(listOf(21, 22, 23), DrawerPaging.page(items, 9, 10))
        assertEquals((1..10).toList(), DrawerPaging.page(items, -1, 10))
        assertEquals(emptyList<Int>(), DrawerPaging.page(emptyList<Int>(), 0, 10))
    }

    @Test
    fun indexKeys() {
        assertEquals('C', DrawerPaging.indexKey("calendar"))
        assertEquals('E', DrawerPaging.indexKey("Écran"))
        assertEquals('#', DrawerPaging.indexKey("2FA"))
        assertEquals('#', DrawerPaging.indexKey(""))
        assertEquals(listOf('#', 'B', 'C'), DrawerPaging.letters(listOf("clock", "1Password", "Books", "Camera")))
    }

    @Test
    fun pageForKeyJumpsToFirstMatchOrNextLetter() {
        val labels = listOf("1Password", "Books", "Calendar", "Camera", "Gmail", "Keep", "Maps")
        assertEquals(0, DrawerPaging.pageForKey(labels, 'B', 3))
        assertEquals(1, DrawerPaging.pageForKey(labels, 'G', 3)) // index 4
        assertEquals(1, DrawerPaging.pageForKey(labels, 'H', 3)) // no H → Keep, index 5
        assertEquals(2, DrawerPaging.pageForKey(labels, 'Z', 3)) // past the end → last page
        assertEquals(0, DrawerPaging.pageForKey(labels, '#', 3))
        assertEquals(0, DrawerPaging.pageForKey(emptyList(), 'A', 3))
    }

    @Test
    fun searchRanksPrefixThenWordThenSubstring() {
        val labels = listOf("Google Maps", "Maps.me", "Amazon", "Émail")
        assertEquals(listOf("Maps.me", "Google Maps"), DrawerPaging.search(labels, "map") { it })
        assertEquals(listOf("Amazon"), DrawerPaging.search(labels, "maz") { it })
        assertEquals(listOf("Émail"), DrawerPaging.search(labels, "EMA") { it })
        assertEquals(labels, DrawerPaging.search(labels, "  ") { it })
    }
}
