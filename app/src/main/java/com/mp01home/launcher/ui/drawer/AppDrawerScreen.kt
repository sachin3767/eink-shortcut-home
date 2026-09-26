package com.mp01home.launcher.ui.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mp01home.launcher.data.apps.AppInfo
import com.mp01home.launcher.ui.components.AddToHomeDialog

@Composable
fun AppDrawerScreen(
    onBack: () -> Unit,
    viewModel: AppDrawerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedApp by remember { mutableStateOf<AppInfo?>(null) }

    val filteredApps = remember(uiState.apps, uiState.searchQuery) {
        val query = uiState.searchQuery
        if (query.isBlank()) uiState.apps else uiState.apps.filter { it.label.contains(query, ignoreCase = true) }
    }

    fun launchIfSingleMatch() {
        filteredApps.singleOrNull()?.let { viewModel.launchApp(it.packageName) }
    }

    val rootFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }

    BackHandler(onBack = onBack)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .systemBarsPadding()
            .padding(20.dp)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> {
                        launchIfSingleMatch()
                        true
                    }
                    else -> {
                        val typedChar = event.nativeKeyEvent.unicodeChar
                        if (typedChar > 0) {
                            viewModel.onSearchQueryChanged(uiState.searchQuery + typedChar.toChar())
                            searchFocusRequester.requestFocus()
                            true
                        } else {
                            false
                        }
                    }
                }
            }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Text(
                    text = "← Home",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .padding(bottom = 16.dp)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onBack,
                            onLongClick = {}
                        )
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChanged,
                    placeholder = { Text("Search") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { launchIfSingleMatch() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                        .focusRequester(searchFocusRequester)
                        .onKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown &&
                                (event.key == Key.Enter || event.key == Key.NumPadEnter)
                            ) {
                                launchIfSingleMatch()
                                true
                            } else {
                                false
                            }
                        }
                )
            }
            if (filteredApps.isEmpty() && uiState.searchQuery.isNotBlank()) {
                item {
                    Text(
                        text = "No apps found",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            items(filteredApps, key = { it.packageName }) { app ->
                val interactionSource = remember { MutableInteractionSource() }
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { viewModel.launchApp(app.packageName) },
                            onLongClick = { selectedApp = app }
                        )
                )
            }
        }
    }

    selectedApp?.let { app ->
        val alreadyOnHome = uiState.homeApps.contains(app.packageName)
        AddToHomeDialog(
            appLabel = app.label,
            homeIsFull = viewModel.isHomeFull,
            alreadyOnHome = alreadyOnHome,
            onConfirmAdd = {
                viewModel.addToHome(app.packageName)
                selectedApp = null
            },
            onConfirmRemove = {
                viewModel.removeFromHome(app.packageName)
                selectedApp = null
            },
            onDismiss = { selectedApp = null }
        )
    }
}
