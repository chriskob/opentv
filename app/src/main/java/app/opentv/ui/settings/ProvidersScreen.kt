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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel
import kotlinx.coroutines.launch

/**
 * The provider list: what's connected, with the plumbing to add another, edit settings, or remove one.
 */
@Composable
fun ProvidersScreen(
    onAddSource: () -> Unit,
    onBack: () -> Unit,
    viewModel: SourcesViewModel = viewModel(),
) {
    val ui by viewModel.ui.collectAsState()
    var pendingRemove by remember { mutableStateOf<Source?>(null) }
    var editingSource by remember { mutableStateOf<Source?>(null) }

    editingSource?.let { src ->
        EditSourceDialog(
            source = src,
            onDismiss = { editingSource = null },
            onSave = { updated, resync ->
                viewModel.updateSource(updated, resync = resync)
                editingSource = null
            },
            onTest = { draft -> viewModel.testSource(draft) },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_providers_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onAddSource) { Text(stringResource(R.string.providers_add)) }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
        }

        Spacer(Modifier.height(16.dp))

        if (ui.sources.isEmpty()) {
            Text(
                stringResource(R.string.providers_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 760.dp),
        ) {
            items(ui.sources, key = { it.id }) { source ->
                ProviderRow(
                    source = source,
                    confirming = pendingRemove?.id == source.id,
                    onEdit = { editingSource = source },
                    onAskRemove = { pendingRemove = source },
                    onCancelRemove = { pendingRemove = null },
                    onConfirmRemove = {
                        viewModel.delete(source)
                        pendingRemove = null
                    },
                    onSetLiveFormat = { viewModel.setLiveFormat(source, it) },
                )
            }
        }
    }
}

@Composable
private fun ProviderRow(
    source: Source,
    confirming: Boolean,
    onEdit: () -> Unit,
    onAskRemove: () -> Unit,
    onCancelRemove: () -> Unit,
    onConfirmRemove: () -> Unit,
    onSetLiveFormat: (LiveStreamFormat) -> Unit,
) {
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(source.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${source.kind.name} · ${hostOf(source.url)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (confirming) {
                    Text(
                        stringResource(R.string.providers_remove_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onConfirmRemove) { Text(stringResource(R.string.providers_yes_remove)) }
                    TextButton(onClick = onCancelRemove) { Text(stringResource(R.string.common_cancel)) }
                } else {
                    OutlinedButton(onClick = onEdit) { Text("Edit") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = onAskRemove) { Text(stringResource(R.string.common_remove)) }
                }
            }

            // Stream format is an Xtream-panel concept — M3U sources carry their own URLs, so the
            // choice is meaningless there and hidden.
            if (source.kind == SourceKind.XTREAM) {
                Spacer(Modifier.height(14.dp))
                StreamFormatSelector(selected = source.liveFormat, onSelect = onSetLiveFormat)
            }
        }
    }
}

@Composable
private fun EditSourceDialog(
    source: Source,
    onDismiss: () -> Unit,
    onSave: (Source, Boolean) -> Unit,
    onTest: suspend (Source) -> Result<String>,
) {
    var name by remember { mutableStateOf(source.name) }
    var url by remember { mutableStateOf(source.url) }
    var username by remember { mutableStateOf(source.username ?: "") }
    var password by remember { mutableStateOf(source.password ?: "") }
    var mac by remember { mutableStateOf(source.macAddress ?: "") }
    var epgUrl by remember { mutableStateOf(source.epgUrl ?: "") }
    var userAgent by remember { mutableStateOf(source.userAgent) }
    var liveFormat by remember { mutableStateOf(source.liveFormat) }
    var resyncOnSave by remember { mutableStateOf(false) }

    var testStatus by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Playlist Settings") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist / Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = {
                        Text(
                            when (source.kind) {
                                SourceKind.XTREAM -> "Server Address"
                                SourceKind.M3U -> "Playlist URL"
                                SourceKind.STALKER -> "Portal URL"
                            }
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                if (source.kind == SourceKind.XTREAM) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    StreamFormatSelector(
                        selected = liveFormat,
                        onSelect = { liveFormat = it },
                    )
                } else if (source.kind == SourceKind.STALKER) {
                    OutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("MAC Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("Custom EPG / XMLTV URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent Header") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = resyncOnSave,
                        onCheckedChange = { resyncOnSave = it },
                    )
                    Text("Re-sync playlist and guide after saving", style = MaterialTheme.typography.bodyMedium)
                }

                if (testStatus != null) {
                    Text(
                        text = testStatus!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                OutlinedButton(
                    onClick = {
                        testing = true
                        testStatus = "Testing connection…"
                        val draft = source.copy(
                            name = name.ifBlank { source.name },
                            url = url,
                            username = username.takeIf { it.isNotBlank() },
                            password = password.takeIf { it.isNotBlank() },
                            macAddress = mac.takeIf { it.isNotBlank() },
                            epgUrl = epgUrl.takeIf { it.isNotBlank() },
                            userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
                            liveFormat = liveFormat,
                        )
                        scope.launch {
                            val res = onTest(draft)
                            testing = false
                            testStatus = res.getOrElse { it.message ?: "Connection failed." }
                        }
                    },
                    enabled = !testing,
                ) {
                    if (testing) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Test Connection")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val updated = source.copy(
                        name = name.ifBlank { source.name },
                        url = url,
                        username = username.takeIf { it.isNotBlank() },
                        password = password.takeIf { it.isNotBlank() },
                        macAddress = mac.takeIf { it.isNotBlank() },
                        epgUrl = epgUrl.takeIf { it.isNotBlank() },
                        userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
                        liveFormat = liveFormat,
                    )
                    onSave(updated, resyncOnSave)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

/**
 * The per-source HLS / MPEG-TS picker. A compact two-option segmented control (the selected
 * container is a filled button, the other outlined) with a one-line hint on when to reach for it.
 */
@Composable
private fun StreamFormatSelector(
    selected: LiveStreamFormat,
    onSelect: (LiveStreamFormat) -> Unit,
) {
    Column {
        Text(
            stringResource(R.string.provider_stream_format),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FormatSegment(
                label = stringResource(R.string.provider_stream_format_hls),
                selected = selected == LiveStreamFormat.HLS,
                onClick = { onSelect(LiveStreamFormat.HLS) },
            )
            FormatSegment(
                label = stringResource(R.string.provider_stream_format_ts),
                selected = selected == LiveStreamFormat.MPEG_TS,
                onClick = { onSelect(LiveStreamFormat.MPEG_TS) },
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.provider_stream_format_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One option of the stream-format control: filled when chosen, outlined otherwise. */
@Composable
private fun FormatSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

/** Host only — the full URL carries the login and has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
