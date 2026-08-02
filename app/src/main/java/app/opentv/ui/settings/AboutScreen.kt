/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opentv.BuildConfig
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * About: what this is, what version it is, and how to get involved. The update check here is the
 * manual counterpart to the automatic one — someone who just heard a fix landed can pull it now
 * instead of waiting for the periodic check.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateLine by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }

        Spacer(Modifier.height(20.dp))

        Section(stringResource(R.string.about_version)) {
            Text("OpenTV ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
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
                            checking = false
                        }
                    },
                ) { Text(if (checking) stringResource(R.string.about_checking) else stringResource(R.string.about_check_updates)) }
                updateLine?.let {
                    Spacer(Modifier.width(16.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.about_what_is_title)) {
            Text(
                stringResource(R.string.about_what_is_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.about_built_open_title)) {
            Text(
                stringResource(R.string.about_built_open_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.about_support_title)) {
            Text(
                stringResource(R.string.about_support_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            LinkLine(stringResource(R.string.about_buy_coffee), "buymeacoffee.com/opentvproject")
        }

        Spacer(Modifier.height(16.dp))

        Section(stringResource(R.string.about_licence_links_title)) {
            LinkLine(stringResource(R.string.about_licence_label), "GNU General Public License v3.0")
            LinkLine(stringResource(R.string.about_source_code), "github.com/opentvproject/opentv")
            LinkLine(stringResource(R.string.about_report_bug), "github.com/opentvproject/opentv/issues")
            LinkLine(stringResource(R.string.about_install_page), "opentvproject.github.io/opentv")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Column(Modifier.widthIn(max = 760.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

@Composable
private fun LinkLine(label: String, value: String) {
    Row {
        Text(
            "$label:  ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
