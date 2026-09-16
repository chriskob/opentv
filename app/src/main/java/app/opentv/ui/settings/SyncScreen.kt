/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.sync.SyncServer
import app.opentv.ui.SyncViewModel
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme

/**
 * Local watch-history sync between two OpenTV devices on the same wifi.
 */
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = viewModel(),
) {
    var mode by remember { mutableStateOf(Mode.SHARE) }

    SettingsPage(
        title = stringResource(R.string.settings_sync_title),
        subtitle = stringResource(R.string.settings_sync_page_subtitle),
        onBack = onBack,
    ) {
        SettingsSegmented(
            options = listOf(
                stringResource(R.string.sync_share_from_here),
                stringResource(R.string.sync_receive),
                stringResource(R.string.sync_nas),
            ),
            selectedIndex = mode.ordinal,
            onSelect = { index -> mode = Mode.entries[index] },
        )

        Spacer(Modifier.height(20.dp))

        when (mode) {
            Mode.SHARE -> SharePane(viewModel)
            Mode.RECEIVE -> ReceivePane(viewModel)
            Mode.NAS -> NasPane(viewModel)
        }
    }
}

private enum class Mode { SHARE, RECEIVE, NAS }

@Composable
private fun SharePane(viewModel: SyncViewModel) {
    val state by viewModel.serverState.collectAsState()
    SettingsCard {
        Column(Modifier.widthIn(max = 640.dp)) {
            when (val s = state) {
                is SyncServer.State.Sharing -> {
                    Text(
                        stringResource(R.string.sync_ready_to_share),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = AppTheme.primary,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sync_on_other_device), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(12.dp))
                    Field(stringResource(R.string.sync_address), s.session.address)
                    Spacer(Modifier.height(8.dp))
                    Field(stringResource(R.string.sync_code), s.session.code)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sync_type_in_one_go, s.session.address, s.session.code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    SettingsButton(
                        text = stringResource(R.string.sync_stop_sharing),
                        onClick = { viewModel.stopSharing() },
                        style = SettingsButtonStyle.Secondary,
                    )
                }
                is SyncServer.State.Failed -> {
                    Text(s.reason, color = SettingsDanger)
                    Spacer(Modifier.height(12.dp))
                    SettingsButton(
                        text = stringResource(R.string.common_try_again),
                        onClick = { viewModel.startSharing() },
                        style = SettingsButtonStyle.Primary,
                    )
                }
                else -> {
                    Text(stringResource(R.string.sync_will_share), color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(16.dp))
                    SettingsButton(
                        text = stringResource(R.string.sync_start_sharing),
                        onClick = { viewModel.startSharing() },
                        style = SettingsButtonStyle.Primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivePane(viewModel: SyncViewModel) {
    val state by viewModel.receiveState.collectAsState()
    var entry by remember { mutableStateOf("") }

    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.Top,
    ) {
        SettingsCard(Modifier.weight(1f)) {
            Column(Modifier.widthIn(max = 640.dp)) {
                Text(
                    stringResource(R.string.sync_type_what_shows),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                TvOutlinedTextField(
                    value = entry,
                    onValueChange = { entry = it },
                    placeholder = { Text("e.g. 192.168.1.50:4242#ABCD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SettingsButton(
                        text = stringResource(R.string.sync_connect),
                        onClick = {
                            val address = entry.substringBefore('#')
                            val code = entry.substringAfter('#')
                            viewModel.receive(address, code)
                        },
                        style = SettingsButtonStyle.Primary,
                        enabled = entry.contains('#') && state !is SyncViewModel.ReceiveState.Connecting,
                    )
                    SettingsButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { entry = ""; viewModel.resetReceive() },
                        style = SettingsButtonStyle.Secondary,
                    )
                }
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is SyncViewModel.ReceiveState.Connecting ->
                        Text(stringResource(R.string.sync_connecting), color = MaterialTheme.colorScheme.onSurface)
                    is SyncViewModel.ReceiveState.Done -> Text(
                        stringResource(R.string.sync_synced_items, s.merged),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppTheme.primary,
                    )
                    is SyncViewModel.ReceiveState.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = SettingsDanger,
                    )
                    else -> Unit
                }
            }
        }

        SettingsCard(Modifier.width(IntrinsicSize.Min)) {
            NumPad(
                onKey = { if (entry.length < 40) entry += it },
                onBackspace = { if (entry.isNotEmpty()) entry = entry.dropLast(1) },
            )
        }
    }
}

@Composable
private fun NasPane(viewModel: SyncViewModel) {
    val nasState by viewModel.nasState.collectAsState()
    val autoSync by viewModel.nasAutoSync.collectAsState()
    val smbHost by viewModel.smbHost.collectAsState()
    val configured = smbHost.isNotBlank()

    SettingsCard {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(
                stringResource(R.string.sync_nas_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = AppTheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.sync_nas_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))

            if (!configured) {
                Text(
                    stringResource(R.string.sync_nas_needs_setup),
                    color = SettingsDanger,
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SettingsButton(
                        text = stringResource(R.string.sync_nas_sync_now),
                        onClick = { viewModel.syncNas() },
                        style = SettingsButtonStyle.Primary,
                        enabled = nasState !is SyncViewModel.NasState.Syncing,
                    )

                    when (val s = nasState) {
                        is SyncViewModel.NasState.Syncing ->
                            Text(stringResource(R.string.sync_nas_syncing), color = MaterialTheme.colorScheme.onSurface)
                        is SyncViewModel.NasState.Done ->
                            Text(s.message, color = AppTheme.primary)
                        is SyncViewModel.NasState.Failed ->
                            Text(s.message, color = SettingsDanger)
                        else -> Unit
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SettingsToggleRow(
                title = stringResource(R.string.sync_nas_auto),
                checked = autoSync,
                onToggle = { viewModel.setNasAutoSync(it) },
            )
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text(
            "$label:  ",
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = AppTheme.primary,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun NumPad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf<String?>("1", "2", "3"),
        listOf<String?>("4", "5", "6"),
        listOf<String?>("7", "8", "9"),
        listOf<String?>(".", "0", ":"),
        listOf<String?>("#", "⌫", null),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { key ->
                    if (key == null) {
                        Spacer(Modifier.size(54.dp))
                    } else {
                        NumKey(key) { if (key == "⌫") onBackspace() else onKey(key) }
                    }
                }
            }
        }
    }
}

@Composable
private fun NumKey(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(54.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f), SettingsShape.Control)
            .settingsFocus(shape = SettingsShape.Control, focusScale = 1.06f)
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
