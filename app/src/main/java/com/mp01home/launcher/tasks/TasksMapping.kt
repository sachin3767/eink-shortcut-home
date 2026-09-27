package com.mp01home.launcher.tasks

import com.mp01home.launcher.data.db.CachedTask
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException

/** Pure conversions between the Tasks REST model and the Room cache (unit-tested). */
object TasksMapping {

    /** Prefix of ids for tasks created offline that the server has not assigned an id to yet. */
    const val LOCAL_ID_PREFIX = "local:"

    /** Alias the Tasks API accepts for the user's default list. */
    const val DEFAULT_LIST_ALIAS = "@default"

    fun isLocalId(id: String): Boolean = id.startsWith(LOCAL_ID_PREFIX)

    /**
     * Google Tasks stores due dates as midnight UTC ("2026-09-26T00:00:00.000Z"); the time part is
     * discarded by the API, so only the calendar date is meaningful.
     */
    fun parseDueEpochDay(due: String?): Long? {
        if (due.isNullOrBlank() || due.length < 10) return null
        return try {
            LocalDate.parse(due.substring(0, 10)).toEpochDay()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    fun formatDue(date: LocalDate): String = "${date}T00:00:00.000Z"

    fun parseInstantMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            Instant.parse(value).toEpochMilli()
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /** RFC 3339 for local midnight of [date] minus one day (used as completedMin for PLAN-2). */
    fun completedMinFor(date: LocalDate, zone: ZoneId): String =
        date.minusDays(1).atStartOfDay(zone).toInstant().toString()

    fun toCached(dto: TaskDto, listId: String, nowMillis: Long): CachedTask = CachedTask(
        id = dto.id,
        listId = listId,
        title = dto.title.orEmpty().trim(),
        notes = dto.notes,
        dueEpochDay = parseDueEpochDay(dto.due),
        completed = dto.status == STATUS_COMPLETED,
        completedAtMillis = if (dto.status == STATUS_COMPLETED) parseInstantMillis(dto.completed) ?: nowMillis else null,
        position = dto.position,
        parentId = dto.parent,
        updatedAtMillis = nowMillis,
        dirty = false
    )

    /**
     * Server snapshot → rows to write: drops deleted tasks and tasks with un-pushed local changes
     * ([keepLocalIds]) so an optimistic edit is not overwritten before its op is flushed.
     */
    fun serverRows(
        items: List<TaskDto>,
        listId: String,
        keepLocalIds: Set<String>,
        nowMillis: Long
    ): List<CachedTask> = items.asSequence()
        .filter { !it.deleted && it.id !in keepLocalIds }
        .map { toCached(it, listId, nowMillis) }
        .toList()

    /**
     * TASK-5: which list ids feed home. Selected ids that still exist, or just the default list
     * when nothing (valid) is selected.
     */
    fun effectiveListIds(selected: Set<String>, existing: List<String>, defaultListId: String?): List<String> {
        val valid = existing.filter { it in selected }
        if (valid.isNotEmpty()) return valid
        return listOfNotNull(defaultListId ?: existing.firstOrNull())
    }

    const val STATUS_COMPLETED = "completed"
}

/** One line in the home tasks widget. */
data class HomeTaskRow(
    val task: CachedTask,
    val overdue: Boolean,
    /** Shown ticked: completed optimistically in this session and kept visible briefly. */
    val checked: Boolean
)

data class HomeTasksSelection(val rows: List<HomeTaskRow>, val hiddenCount: Int)

/**
 * TASK-2: picks what the home widget shows. Due-today tasks first (in list order), then overdue
 * ones (oldest first), up to [max] rows. [recentlyCompleted] tasks (ticked a moment ago) keep
 * their slot, shown checked, so a tap gives visible feedback and can be undone.
 */
fun selectHomeTasks(
    open: List<CachedTask>,
    recentlyCompleted: Collection<CachedTask>,
    todayEpochDay: Long,
    max: Int
): HomeTasksSelection {
    val openIds = open.mapTo(HashSet()) { it.id }
    val recent = recentlyCompleted.filter { it.id !in openIds && (it.dueEpochDay ?: Long.MAX_VALUE) <= todayEpochDay }
    val recentIds = recent.mapTo(HashSet()) { it.id }
    val all = (open.filter { !it.completed } + recent)
        .filter { (it.dueEpochDay ?: Long.MAX_VALUE) <= todayEpochDay }
        .sortedWith(
            compareBy<CachedTask>(
                { if (it.dueEpochDay == todayEpochDay) 0 else 1 },
                { it.dueEpochDay ?: Long.MAX_VALUE },
                { it.position ?: "" },
                { it.title }
            )
        )
    val rows = all.take(max).map {
        HomeTaskRow(
            task = it,
            overdue = (it.dueEpochDay ?: Long.MAX_VALUE) < todayEpochDay,
            checked = it.id in recentIds
        )
    }
    return HomeTasksSelection(rows, hiddenCount = (all.size - rows.size).coerceAtLeast(0))
}
