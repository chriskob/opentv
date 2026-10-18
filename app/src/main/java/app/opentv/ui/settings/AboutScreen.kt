/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.BuildConfig
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme
import app.opentv.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * About: the version and the update check, and nothing else.
 *
 * It used to carry a "what it is" paragraph and the license, source-code and bug-tracker links.
 * The maintainer trimmed the page to the two things a TV remote can act on (2026-10-18): what
 * this build is, and whether a newer one exists. The repository is where the rest lives.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateLine by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    // The cursor lands on "Check for updates" the first time focus enters the page. The sections
    // below are not collapsible, so this button is the page's first control.
    val checkFocus = remember { FocusRequester() }
    var grabbedFocus by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .onFocusChanged { state ->
                if (!state.hasFocus) {
                    grabbedFocus = false
                } else if (!grabbedFocus) {
                    grabbedFocus = true
                    runCatching { checkFocus.requestFocus() }
                }
            },
    ) {
        SettingsPage(
            title = stringResource(R.string.about_title),
            subtitle = stringResource(R.string.settings_about_page_subtitle),
            onBack = onBack,
        ) {
            SettingsSection(title = stringResource(R.string.about_version), collapsible = false) {
                Text(
                    text = "OpenTV ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsButton(
                        text = if (checking) stringResource(R.string.about_checking)
                        else stringResource(R.string.about_check_updates),
                        onClick = {
                            if (checking) return@SettingsButton
                            checking = true
                            updateLine = null
                            scope.launch {
                                val graph = ServiceLocator.get(context)
                                val update = runCatching {
                                    UpdateChecker(graph.httpClient, BuildConfig.VERSION_NAME).check()
                                }.getOrNull()
                                updateLine = when {
                                    update != null -> context.getString(R.string.about_update_available, update.versionName)
                                    else -> context.getString(R.string.about_up_to_date)
                                }
                                if (update != null) {
                                    app.opentv.update.UpdateHub.state.value =
                                        app.opentv.update.UpdateUiState.Available(update)
                                }
                                checking = false
                                // Keep the cursor on the button. The button used to be disabled
                                // while checking, which made it un-focusable and threw focus back to
                                // the settings menu. When a dialog is showing it owns focus instead.
                                if (update == null) runCatching { checkFocus.requestFocus() }
                            }
                        },
                        style = SettingsButtonStyle.Secondary,
                        modifier = Modifier.focusRequester(checkFocus),
                    )
                    updateLine?.let {
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
