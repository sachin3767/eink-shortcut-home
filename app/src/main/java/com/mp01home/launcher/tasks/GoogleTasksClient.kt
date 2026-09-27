package com.mp01home.launcher.tasks

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

// ---- REST DTOs (https://developers.google.com/tasks/reference/rest) -------------------------

@Serializable
data class TaskListDto(
    val id: String,
    val title: String = "",
    val updated: String? = null
)

@Serializable
data class TaskListsPage(
    val items: List<TaskListDto> = emptyList(),
    val nextPageToken: String? = null
)

@Serializable
data class TaskDto(
    val id: String,
    val title: String? = null,
    val notes: String? = null,
    /** "needsAction" or "completed". */
    val status: String? = null,
    /** RFC 3339; only the date part is meaningful. */
    val due: String? = null,
    /** RFC 3339 completion time. */
    val completed: String? = null,
    val updated: String? = null,
    val parent: String? = null,
    val position: String? = null,
    val deleted: Boolean = false,
    val hidden: Boolean = false
)

@Serializable
data class TasksPage(
    val items: List<TaskDto> = emptyList(),
    val nextPageToken: String? = null
)

/** Non-2xx response from the Tasks API. */
class TasksHttpException(val code: Int, message: String) : IOException("Tasks API HTTP $code: $message") {
    /** Worth retrying later (server-side / rate limit). */
    val isTransient: Boolean get() = code == 408 || code == 429 || code >= 500
}

/**
 * Minimal Google Tasks v1 REST client (OkHttp + kotlinx.serialization). Android-free.
 *
 * [tokenProvider] returns an OAuth access token for the tasks scope (blocking calls are fine: every
 * request runs on [Dispatchers.IO]). On HTTP 401 the token is passed to [invalidateToken] and the
 * request is retried once with a fresh token.
 */
class GoogleTasksClient(
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String,
    private val invalidateToken: suspend (String) -> Unit,
    baseUrl: String = DEFAULT_BASE_URL
) {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    /** All task lists of the signed-in user. */
    suspend fun listTaskLists(): List<TaskListDto> {
        val out = mutableListOf<TaskListDto>()
        var pageToken: String? = null
        do {
            val url = base.newBuilder()
                .addPathSegments("users/@me/lists")
                .addQueryParameter("maxResults", "100")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build()
            val page = json.decodeFromString(TaskListsPage.serializer(), execute("GET", url, null))
            out += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrEmpty())
        return out
    }

    /** One list; `"@default"` resolves the user's default list. */
    suspend fun getTaskList(listId: String): TaskListDto {
        val url = base.newBuilder()
            .addPathSegments("users/@me/lists")
            .addPathSegment(listId)
            .build()
        return json.decodeFromString(TaskListDto.serializer(), execute("GET", url, null))
    }

    /**
     * Every task in [listId]: open tasks plus completed ones (including hidden/cleared ones) that
     * were completed at or after [completedMin] (RFC 3339), if given.
     */
    suspend fun listTasks(listId: String, completedMin: String? = null): List<TaskDto> {
        val out = mutableListOf<TaskDto>()
        var pageToken: String? = null
        do {
            val url = tasksUrl(listId)
                .addQueryParameter("maxResults", "100")
                .addQueryParameter("showCompleted", "true")
                .addQueryParameter("showHidden", "true")
                .apply {
                    completedMin?.let { addQueryParameter("completedMin", it) }
                    pageToken?.let { addQueryParameter("pageToken", it) }
                }
                .build()
            val page = json.decodeFromString(TasksPage.serializer(), execute("GET", url, null))
            out += page.items
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrEmpty())
        return out
    }

    /** Creates a task at the top of [listId]. [dueRfc3339] from [TasksMapping.formatDue]. */
    suspend fun insertTask(listId: String, title: String, dueRfc3339: String?, notes: String?): TaskDto {
        val body = buildJsonObject {
            put("title", title)
            if (notes != null) put("notes", notes)
            if (dueRfc3339 != null) put("due", dueRfc3339)
        }
        return json.decodeFromString(TaskDto.serializer(), execute("POST", tasksUrl(listId).build(), body))
    }

    /** PATCH with an arbitrary partial body. */
    suspend fun patchTask(listId: String, taskId: String, body: JsonObject): TaskDto {
        val url = tasksUrl(listId).addPathSegment(taskId).build()
        return json.decodeFromString(TaskDto.serializer(), execute("PATCH", url, body))
    }

    /** Marks a task completed / not completed (TASK-3). */
    suspend fun setCompleted(listId: String, taskId: String, completed: Boolean): TaskDto =
        patchTask(listId, taskId, completionPatch(completed))

    suspend fun deleteTask(listId: String, taskId: String) {
        execute("DELETE", tasksUrl(listId).addPathSegment(taskId).build(), null)
    }

    // -----------------------------------------------------------------------------------------

    private fun tasksUrl(listId: String): HttpUrl.Builder =
        base.newBuilder().addPathSegment("lists").addPathSegment(listId).addPathSegment("tasks")

    private suspend fun execute(method: String, url: HttpUrl, body: JsonObject?): String {
        val token = tokenProvider()
        val first = call(method, url, body, token)
        if (first.code != 401) return first.unwrap()
        invalidateToken(token)
        return call(method, url, body, tokenProvider()).unwrap()
    }

    private class RawResponse(val code: Int, val body: String) {
        fun unwrap(): String {
            if (code !in 200..299) throw TasksHttpException(code, body.take(300))
            return body.ifEmpty { "{}" }
        }
    }

    private suspend fun call(method: String, url: HttpUrl, body: JsonObject?, token: String): RawResponse =
        withContext(Dispatchers.IO) {
            val requestBody = body?.toString()?.toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .method(method, requestBody)
                .build()
            http.newCall(request).execute().use { response ->
                RawResponse(response.code, response.body?.string().orEmpty())
            }
        }

    companion object {
        const val DEFAULT_BASE_URL = "https://tasks.googleapis.com/tasks/v1/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        internal val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

        /** PATCH body for completing (status=completed) or re-opening a task (completed=null). */
        fun completionPatch(completed: Boolean): JsonObject = buildJsonObject {
            if (completed) {
                put("status", "completed")
            } else {
                put("status", "needsAction")
                put("completed", JsonNull)
            }
        }
    }
}

/** Body stored in [com.mp01home.launcher.data.db.PendingTaskOp.payloadJson] for a queued CREATE. */
@Serializable
data class TaskCreatePayload(
    val title: String,
    val notes: String? = null,
    @SerialName("dueEpochDay") val dueEpochDay: Long? = null
)
