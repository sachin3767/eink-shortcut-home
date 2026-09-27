package com.mp01home.launcher.keep

/** KEEP-1: which kind of Keep item to create. */
enum class KeepItemKind { NOTE, LIST }

/** A launcher shortcut published by Keep (id + short label), Android-free for tests. */
data class KeepShortcutCandidate(val id: String, val label: String?)

/**
 * Picks Keep's own "New note" / "New list" app shortcut. Keep does not document its shortcut
 * ids, so match on id and label, skipping photo/audio/drawing variants.
 */
object KeepShortcutMatcher {

    private val LIST_WORDS = listOf("list", "checklist", "check_list", "todo", "liste", "lista")
    private val NOTE_WORDS = listOf("note", "text", "notiz", "nota")
    private val EXCLUDED_WORDS = listOf("photo", "image", "camera", "audio", "voice", "record", "drawing", "draw", "sketch")

    fun pick(kind: KeepItemKind, candidates: List<KeepShortcutCandidate>): KeepShortcutCandidate? {
        fun haystack(c: KeepShortcutCandidate) = (c.id + " " + c.label.orEmpty()).lowercase()
        val usable = candidates.filter { c -> EXCLUDED_WORDS.none { it in haystack(c) } }
        val lists = usable.filter { c -> LIST_WORDS.any { it in haystack(c) } }
        return when (kind) {
            KeepItemKind.LIST -> lists.firstOrNull()
            KeepItemKind.NOTE -> usable.firstOrNull { c -> c !in lists && NOTE_WORDS.any { it in haystack(c) } }
        }
    }
}
