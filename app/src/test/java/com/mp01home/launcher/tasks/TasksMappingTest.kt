package com.mp01home.launcher.tasks

import com.mp01home.launcher.data.db.CachedTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class TasksMappingTest {

    private val today = LocalDate.of(2026, 9, 26)
    private val todayDay = today.toEpochDay()

    @Test
    fun dueDateRoundTrip() {
        assertEquals("2026-09-26T00:00:00.000Z", TasksMapping.formatDue(today))
        assertEquals(todayDay, TasksMapping.parseDueEpochDay("2026-09-26T00:00:00.000Z"))
        assertNull(TasksMapping.parseDueEpochDay(null))
        assertNull(TasksMapping.parseDueEpochDay("garbage"))
    }

    @Test
    fun dtoToCached() {
        val dto = TaskDto(
            id = "t1", title = " Send invoice ", status = "completed",
            due = "2026-09-25T00:00:00.000Z", completed = "2026-09-26T08:30:00.000Z", position = "0001"
        )
        val row = TasksMapping.toCached(dto, "L1", nowMillis = 42L)
        assertEquals("Send invoice", row.title)
        assertEquals("L1", row.listId)
        assertEquals(todayDay - 1, row.dueEpochDay)
        assertTrue(row.completed)
        assertEquals(java.time.Instant.parse("2026-09-26T08:30:00Z").toEpochMilli(), row.completedAtMillis)
        assertFalse(row.dirty)
        val open = TasksMapping.toCached(TaskDto(id = "t2", status = "needsAction"), "L1", 42L)
        assertFalse(open.completed)
        assertNull(open.completedAtMillis)
        assertNull(open.dueEpochDay)
    }

    @Test
    fun serverRowsSkipDeletedAndPendingLocalEdits() {
        val rows = TasksMapping.serverRows(
            listOf(TaskDto("a"), TaskDto("b", deleted = true), TaskDto("c")),
            "L", keepLocalIds = setOf("c"), nowMillis = 1L
        )
        assertEquals(listOf("a"), rows.map { it.id })
    }

    @Test
    fun effectiveLists() {
        assertEquals(listOf("D"), TasksMapping.effectiveListIds(emptySet(), listOf("A", "D"), "D"))
        assertEquals(listOf("A"), TasksMapping.effectiveListIds(setOf("A", "gone"), listOf("A", "D"), "D"))
        assertEquals(listOf("A"), TasksMapping.effectiveListIds(setOf("gone"), listOf("A", "D"), null))
        assertEquals(emptyList<String>(), TasksMapping.effectiveListIds(emptySet(), emptyList(), null))
    }

    @Test
    fun completedMin() {
        assertEquals("2026-09-24T22:00:00Z", TasksMapping.completedMinFor(today, ZoneOffset.ofHours(2)))
    }

    @Test
    fun completionPatchBodies() {
        assertEquals("""{"status":"completed"}""", GoogleTasksClient.completionPatch(true).toString())
        assertEquals("""{"status":"needsAction","completed":null}""", GoogleTasksClient.completionPatch(false).toString())
    }

    @Test
    fun createPayloadRoundTrip() {
        val p = TaskCreatePayload("Call Sam", dueEpochDay = todayDay)
        val json = GoogleTasksClient.json.encodeToString(TaskCreatePayload.serializer(), p)
        assertEquals(p, GoogleTasksClient.json.decodeFromString(TaskCreatePayload.serializer(), json))
    }

    @Test
    fun decodesApiPage() {
        val body = """{"kind":"tasks#tasks","nextPageToken":"n","items":[{"kind":"tasks#task","id":"x","title":"T","status":"needsAction","due":"2026-09-26T00:00:00.000Z","links":[]}]}"""
        val page = GoogleTasksClient.json.decodeFromString(TasksPage.serializer(), body)
        assertEquals("n", page.nextPageToken)
        assertEquals("x", page.items.single().id)
    }

    private fun task(id: String, due: Long?, position: String? = null, completed: Boolean = false) =
        CachedTask(id = id, listId = "L", title = id, dueEpochDay = due, position = position, completed = completed)

    @Test
    fun homeSelectionPutsTodayFirstThenOverdue() {
        val open = listOf(
            task("old", todayDay - 5),
            task("yesterday", todayDay - 1),
            task("today2", todayDay, "2"),
            task("today1", todayDay, "1"),
            task("future", todayDay + 1)
        )
        val sel = selectHomeTasks(open, emptyList(), todayDay, max = 3)
        assertEquals(listOf("today1", "today2", "old"), sel.rows.map { it.task.id })
        assertEquals(listOf(false, false, true), sel.rows.map { it.overdue })
        assertEquals(1, sel.hiddenCount)
    }

    @Test
    fun recentlyCompletedStaysVisibleChecked() {
        val ticked = task("done", todayDay, "0")
        val sel = selectHomeTasks(listOf(task("a", todayDay, "1")), listOf(ticked), todayDay, max = 4)
        assertEquals(listOf("done", "a"), sel.rows.map { it.task.id })
        assertEquals(listOf(true, false), sel.rows.map { it.checked })
        assertEquals(0, sel.hiddenCount)
    }
}
