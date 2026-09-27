package com.mp01home.launcher.mail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mp01home.launcher.appContainer
import com.mp01home.launcher.sync.formatSyncedLabel
import com.mp01home.launcher.util.minuteTicker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MailUiState(
    val summary: MailSummary = MailSummary.EMPTY,
    val syncedLabel: String = "not synced",
    val keepInstalled: Boolean = false
)

/** Backs MailSection (MAIL-1/2/3, KEEP-1). Reads the Room cache only (EINK-7). */
class MailHomeViewModel(application: Application) : AndroidViewModel(application) {

    private val container = application.appContainer
    private val keepInstalled = container.keepActions.isInstalled()

    val state: StateFlow<MailUiState> = combine(
        container.mailRepository.summary,
        container.mailRepository.lastUpdateMillis,
        container.settingsRepository.settings.map { it.timeFormat }.distinctUntilChanged(),
        minuteTicker() // only so "synced 24 Sep 10:42" becomes correct after midnight
    ) { summary, lastMillis, timeFormat, _ ->
        MailUiState(
            summary = summary,
            syncedLabel = formatSyncedLabel(lastMillis, timeFormat),
            keepInstalled = keepInstalled
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MailUiState(keepInstalled = keepInstalled))

    fun openInbox() {
        container.mailActions.openInbox()
    }

    fun compose() {
        container.mailActions.compose()
    }

    fun newKeepNote() {
        container.keepActions.newNote()
    }

    fun newKeepList() {
        container.keepActions.newList()
    }
}
