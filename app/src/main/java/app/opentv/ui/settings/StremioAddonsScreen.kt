/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.StremioAddon
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class StremioAddonsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    val addons: StateFlow<List<StremioAddon>> = graph.settings.stremioAddons

    sealed interface Status {
        data object Idle : Status
        data object Checking : Status
        data class Added(val name: String) : Status
        data object Invalid : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Idle)
    val status: StateFlow<Status> = _status.asStateFlow()

    fun add(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) return

        _status.value = Status.Checking
        viewModelScope.launch {
            val name = withContext(Dispatchers.IO) { graph.stremioClient.fetchManifest(url) }
            _status.value = if (name != null) {
                graph.settings.addStremioAddon(StremioAddon(manifestUrl = url, name = name))
                Status.Added(name)
            } else {
                Status.Invalid
            }
        }
    }

    fun remove(addon: StremioAddon) {
        graph.settings.removeStremioAddon(addon.manifestUrl)
    }

    fun clearStatus() {
        _status.value = Status.Idle
    }
}

/**
 * Manage Stremio add-ons: paste a manifest URL, it's validated against the live add-on and stored on-device.
 */
@Composable
fun StremioAddonsScreen(onBack: () -> Unit) {
    val viewModel: StremioAddonsViewModel = viewModel()
    val addons by viewModel.addons.collectAsState()
    val status by viewModel.status.collectAsState()
    var input by remember { mutableStateOf("") }

    LaunchedEffect(status) {
        if (status is StremioAddonsViewModel.Status.Added) input = ""
    }

    SettingsPage(
        title = stringResource(R.string.settings_addons_title),
        subtitle = stringResource(R.string.settings_addons_page_subtitle),
        onBack = onBack,
    ) {
        SettingsSection(title = "Add Stremio Add-on", icon = Icons.Filled.Extension) {
            Text(
                text = stringResource(R.string.addons_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            TvOutlinedTextField(
                value = input,
                onValueChange = {
                    input = it
                    if (status !is StremioAddonsViewModel.Status.Idle) viewModel.clearStatus()
                },
                label = { Text(stringResource(R.string.addons_url_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SettingsButton(
                    text = stringResource(R.string.addons_add),
                    onClick = { viewModel.add(input) },
                    style = SettingsButtonStyle.Primary,
                    enabled = input.isNotBlank() && status !is StremioAddonsViewModel.Status.Checking,
                )

                Spacer(Modifier.width(14.dp))

                when (val s = status) {
                    is StremioAddonsViewModel.Status.Checking -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AppTheme.primary,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.addons_checking),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    is StremioAddonsViewModel.Status.Added ->
                        Text(
                            stringResource(R.string.addons_added, s.name),
                            style = MaterialTheme.typography.bodyMedium,
                            color = AppTheme.palette.success,
                        )
                    is StremioAddonsViewModel.Status.Invalid ->
                        Text(
                            stringResource(R.string.addons_invalid),
                            style = MaterialTheme.typography.bodyMedium,
                            color = SettingsDanger,
                        )
                    StremioAddonsViewModel.Status.Idle -> {}
                }
            }
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        if (addons.isEmpty()) {
            SettingsEmptyState(
                title = stringResource(R.string.addons_empty),
                icon = Icons.Filled.Extension,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                addons.forEach { addon ->
                    AddonRow(addon = addon, onRemove = { viewModel.remove(addon) })
                }
            }
        }
    }
}

@Composable
private fun AddonRow(addon: StremioAddon, onRemove: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(SettingsShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Card)
            .settingsFocus(shape = SettingsShape.Card)
            .focusable()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = addon.name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = hostOf(addon.manifestUrl),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        SettingsButton(
            text = stringResource(R.string.common_remove),
            onClick = onRemove,
            style = SettingsButtonStyle.Danger,
            icon = Icons.Filled.Delete,
        )
    }
}

private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
