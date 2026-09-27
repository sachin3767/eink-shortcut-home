package com.mp01home.launcher.capture

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.speech.RecognizerIntent
import android.util.Log
import com.mp01home.launcher.data.apps.AppLauncher
import com.mp01home.launcher.tasks.GoogleTasksAuth
import com.mp01home.launcher.tasks.TasksActions
import kotlinx.coroutines.CancellationException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Intents used by quick capture (KEEP-2, CAL-3 style insert, CAP-3). */
object CaptureIntents {

    const val KEEP_PACKAGE = "com.google.android.keep"

    /**
     * KEEP-2: share [text] straight into Google Keep (new note). Falls back to the system share
     * chooser when Keep is not installed.
     */
    fun keepNote(context: Context, text: String): Intent {
        val send = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text)
        val toKeep = Intent(send).setPackage(KEEP_PACKAGE)
        return if (toKeep.resolveActivity(context.packageManager) != null) {
            toKeep
        } else {
            Intent.createChooser(send, "Save note")
        }
    }

    /** Opens the calendar app's new-event editor pre-filled from [plan]. */
    fun insertEvent(plan: CapturePlan.InsertEvent): Intent =
        Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).apply {
            putExtra(CalendarContract.Events.TITLE, plan.title)
            plan.beginMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, it) }
            plan.endMillis?.let { putExtra(CalendarContract.EXTRA_EVENT_END_TIME, it) }
            if (plan.allDay) putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        }

    /** CAP-3: free-form speech recognition. */
    fun speech(prompt: String = "Capture"): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
}

/** Result of submitting a capture; [success] clears the input. */
data class CaptureOutcome(val success: Boolean, val message: String)

/** Executes a [CapturePlan] (CAP-1). */
class CaptureExecutor(
    private val context: Context,
    private val appLauncher: AppLauncher,
    private val tasks: TasksActions,
    private val tasksAuth: GoogleTasksAuth
) {
    suspend fun execute(plan: CapturePlan): CaptureOutcome = when (plan) {
        is CapturePlan.AddTask -> addTask(plan)
        is CapturePlan.KeepNote -> {
            val ok = appLauncher.startActivitySafely(CaptureIntents.keepNote(context, plan.text))
            if (ok) CaptureOutcome(true, "Sent to Keep") else CaptureOutcome(false, "No app to save notes")
        }
        is CapturePlan.InsertEvent -> {
            val ok = appLauncher.startActivitySafely(CaptureIntents.insertEvent(plan))
            if (ok) CaptureOutcome(true, "Opened new event") else CaptureOutcome(false, "No calendar app")
        }
    }

    private suspend fun addTask(plan: CapturePlan.AddTask): CaptureOutcome = try {
        tasks.addTask(plan.title, plan.due)
        val due = plan.due?.let { " · " + shortDate(it) }.orEmpty()
        val suffix = if (tasksAuth.isSignedIn) "" else " (sign in to sync)"
        CaptureOutcome(true, "Task added$due$suffix")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("CaptureExecutor", "addTask failed", e)
        CaptureOutcome(false, "Could not add task")
    }

    private fun shortDate(date: LocalDate): String {
        val today = LocalDate.now()
        return when (date) {
            today -> "today"
            today.plusDays(1) -> "tomorrow"
            else -> date.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
        }
    }
}
