package com.mp01home.launcher.tasks

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer

private val Context.tasksMetaStore by preferencesDataStore(name = "mp01_tasks_meta")

/** The user's task lists as last seen by a sync (TASK-5 picker), plus the default list id. */
data class TaskListCatalog(
    val lists: List<TaskListDto> = emptyList(),
    val defaultListId: String? = null
)

/** Feature-private, non-secret Tasks state (list catalog, default list id). */
class TasksMetaStore(private val context: Context) {

    private val listsKey = stringPreferencesKey("task_lists_json")
    private val defaultKey = stringPreferencesKey("default_list_id")
    private val listSerializer = ListSerializer(TaskListDto.serializer())

    val catalog: Flow<TaskListCatalog> = context.tasksMetaStore.data.map { prefs ->
        val lists = prefs[listsKey]?.let {
            runCatching { GoogleTasksClient.json.decodeFromString(listSerializer, it) }.getOrNull()
        }.orEmpty()
        TaskListCatalog(lists, prefs[defaultKey])
    }.distinctUntilChanged()

    suspend fun current(): TaskListCatalog = catalog.first()

    suspend fun save(lists: List<TaskListDto>, defaultListId: String?) {
        context.tasksMetaStore.edit { prefs ->
            prefs[listsKey] = GoogleTasksClient.json.encodeToString(listSerializer, lists)
            if (defaultListId != null) prefs[defaultKey] = defaultListId else prefs.remove(defaultKey)
        }
    }

    suspend fun clear() {
        context.tasksMetaStore.edit { it.clear() }
    }
}
