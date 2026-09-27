package com.mp01home.launcher.ui.drawer

import java.text.Normalizer
import java.util.Locale

/**
 * CORE-4 pure paging / index / search helpers for the app drawer (no scrolling on e-ink: the list
 * is cut into pages that exactly fit the available height).
 */
object DrawerPaging {

    /** Index key for labels that do not start with a Latin letter. */
    const val OTHER_KEY = '#'

    /** How many rows of [rowHeightPx] fit in [availableHeightPx]; always at least 1. */
    fun pageSize(availableHeightPx: Int, rowHeightPx: Int): Int {
        if (rowHeightPx <= 0) return 1
        return (availableHeightPx / rowHeightPx).coerceAtLeast(1)
    }

    fun pageCount(itemCount: Int, pageSize: Int): Int {
        val size = pageSize.coerceAtLeast(1)
        return if (itemCount <= 0) 1 else (itemCount + size - 1) / size
    }

    fun clampPage(page: Int, itemCount: Int, pageSize: Int): Int =
        page.coerceIn(0, pageCount(itemCount, pageSize) - 1)

    fun <T> page(items: List<T>, pageIndex: Int, pageSize: Int): List<T> {
        val size = pageSize.coerceAtLeast(1)
        val from = clampPage(pageIndex, items.size, size) * size
        if (from >= items.size) return emptyList()
        return items.subList(from, minOf(from + size, items.size))
    }

    /** 'A'..'Z' for labels starting with a (possibly accented) Latin letter, else [OTHER_KEY]. */
    fun indexKey(label: String): Char {
        val first = label.trimStart().firstOrNull() ?: return OTHER_KEY
        val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD)
            .firstOrNull()
            ?.uppercaseChar()
            ?: return OTHER_KEY
        return if (base in 'A'..'Z') base else OTHER_KEY
    }

    /** Index keys present in [labels], '#' first, then A–Z. */
    fun letters(labels: List<String>): List<Char> =
        labels.map(::indexKey).distinct().sortedWith(compareBy({ it != OTHER_KEY }, { it }))

    /**
     * Page holding the first label with index [key]. If there is none, the page of the first label
     * whose key sorts after it (or the last page).
     */
    fun pageForKey(labels: List<String>, key: Char, pageSize: Int): Int {
        val keys = labels.map(::indexKey)
        var index = keys.indexOf(key)
        if (index < 0) {
            index = keys.indexOfFirst { it != OTHER_KEY && key != OTHER_KEY && it > key }
            if (index < 0) index = labels.lastIndex
        }
        if (index < 0) return 0
        return index / pageSize.coerceAtLeast(1)
    }

    /**
     * Case- and accent-insensitive search. Labels that start with the query come first, then
     * labels with a word starting with it, then any other substring match; ties keep input order.
     */
    fun <T> search(items: List<T>, query: String, label: (T) -> String): List<T> {
        val q = fold(query.trim())
        if (q.isEmpty()) return items
        return items
            .mapNotNull { item ->
                val l = fold(label(item))
                val rank = when {
                    l.startsWith(q) -> 0
                    l.split(' ', '-', '_', '.').any { it.startsWith(q) } -> 1
                    l.contains(q) -> 2
                    else -> return@mapNotNull null
                }
                rank to item
            }
            .sortedBy { it.first }
            .map { it.second }
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase(Locale.ROOT)
}
