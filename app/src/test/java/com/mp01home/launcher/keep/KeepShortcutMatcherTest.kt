package com.mp01home.launcher.keep

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepShortcutMatcherTest {

    private val keep = listOf(
        KeepShortcutCandidate("new_photo_note", "New photo note"),
        KeepShortcutCandidate("new_audio_note", "New audio note"),
        KeepShortcutCandidate("new_list_note", "New list"),
        KeepShortcutCandidate("new_text_note", "New note")
    )

    @Test
    fun picksListAndNote() {
        assertEquals("new_list_note", KeepShortcutMatcher.pick(KeepItemKind.LIST, keep)?.id)
        assertEquals("new_text_note", KeepShortcutMatcher.pick(KeepItemKind.NOTE, keep)?.id)
    }

    @Test
    fun localisedLabels() {
        val de = listOf(KeepShortcutCandidate("a", "Neue Liste"), KeepShortcutCandidate("b", "Neue Notiz"))
        assertEquals("a", KeepShortcutMatcher.pick(KeepItemKind.LIST, de)?.id)
        assertEquals("b", KeepShortcutMatcher.pick(KeepItemKind.NOTE, de)?.id)
    }

    @Test
    fun noMatch() {
        assertNull(KeepShortcutMatcher.pick(KeepItemKind.LIST, listOf(KeepShortcutCandidate("x", "Drawing"))))
        assertNull(KeepShortcutMatcher.pick(KeepItemKind.NOTE, emptyList()))
    }
}
