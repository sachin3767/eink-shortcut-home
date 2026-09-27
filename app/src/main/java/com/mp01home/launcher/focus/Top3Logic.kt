package com.mp01home.launcher.focus

/*
 * FOC-1 pure logic (no Android / Room types) so it can be unit-tested on the JVM.
 */

/** Number of daily priority slots (FOC-1). */
const val TOP3_SLOTS = 3

/** Android-free view of one priority, used by the carry-over planning below. */
data class PriorityItem(
    val slot: Int,
    val text: String,
    val linkedTaskId: String? = null,
    val done: Boolean = false
)

/** What the user may carry over from [fromDateIso]: the unfinished, non-blank items. */
data class CarryOverOffer(val fromDateIso: String, val items: List<PriorityItem>)

/**
 * Items from a previous day that are worth offering for carry-over: not done, not blank,
 * in slot order, at most [TOP3_SLOTS].
 */
fun carryOverCandidates(previous: List<PriorityItem>): List<PriorityItem> =
    previous
        .filter { !it.done && it.text.isNotBlank() }
        .sortedBy { it.slot }
        .take(TOP3_SLOTS)

/**
 * Whether the carry-over prompt should be shown today:
 * there is something to carry, and it has not already been answered for today.
 */
fun shouldOfferCarryOver(
    candidates: List<PriorityItem>,
    todayItems: List<PriorityItem>,
    answeredForDateIso: String?,
    todayIso: String
): Boolean {
    if (candidates.isEmpty()) return false
    if (answeredForDateIso == todayIso) return false
    // Nothing left to fill.
    if (freeSlots(todayItems).isEmpty()) return false
    // Don't offer items already present today (same text, case-insensitive).
    val existing = todayItems.map { it.text.trim().lowercase() }.toSet()
    return candidates.any { it.text.trim().lowercase() !in existing }
}

/** Slots 0..2 that are empty (no item or a blank item). */
fun freeSlots(todayItems: List<PriorityItem>): List<Int> {
    val used = todayItems.filter { it.text.isNotBlank() }.map { it.slot }.toSet()
    return (0 until TOP3_SLOTS).filter { it !in used }
}

/**
 * Places [candidates] into today's free slots, lowest slot first, skipping texts that already
 * exist today. Returns the new items (with their assigned slot, done = false).
 */
fun planCarryOver(candidates: List<PriorityItem>, todayItems: List<PriorityItem>): List<PriorityItem> {
    val existing = todayItems.filter { it.text.isNotBlank() }.map { it.text.trim().lowercase() }.toMutableSet()
    val free = freeSlots(todayItems).iterator()
    val result = mutableListOf<PriorityItem>()
    for (item in candidates) {
        val key = item.text.trim().lowercase()
        if (key in existing) continue
        if (!free.hasNext()) break
        result += item.copy(slot = free.next(), done = false, text = item.text.trim())
        existing += key
    }
    return result
}

/**
 * Normalises a user-supplied list (e.g. from the morning review) into at most 3 slotted
 * items: blank texts are dropped, the rest are compacted into slots 0, 1, 2 in order.
 */
fun normalizeTop3(texts: List<Pair<String, String?>>): List<PriorityItem> =
    texts
        .map { (text, linked) -> text.trim() to linked }
        .filter { it.first.isNotEmpty() }
        .take(TOP3_SLOTS)
        .mapIndexed { index, (text, linked) -> PriorityItem(slot = index, text = text, linkedTaskId = linked) }

/** Home-line label for a slot: "①", "②", "③". */
fun slotMarker(slot: Int): String = when (slot) {
    0 -> "①"
    1 -> "②"
    2 -> "③"
    else -> "•"
}
