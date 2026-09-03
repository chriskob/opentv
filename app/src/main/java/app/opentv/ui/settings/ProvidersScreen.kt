/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onOpenRemotePairing: () -> Unit = {},
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
            .background(Color(0xFF10171E))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // Top Action Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_providers_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage connected IPTV playlists, Xtream codes servers, and portals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            ProviderActionButton(
                label = "Remote Edit / Pair",
                icon = Icons.Filled.PhoneAndroid,
                onClick = onOpenRemotePairing,
            )

            Spacer(Modifier.width(12.dp))

            ProviderActionButton(
                label = stringResource(R.string.providers_add),
                icon = Icons.Filled.Add,
                onClick = onAddSource,
            )

            Spacer(Modifier.width(14.dp))

            ProviderBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        if (ui.sources.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF18222C))
                    .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = null,
                        tint = Color(0xFF26C6DA),
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.providers_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth(),
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
    var focused by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF18222C),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp)),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Kind Badge
                val kindColor = when (source.kind) {
                    SourceKind.XTREAM -> Color(0xFF29B6F6)
                    SourceKind.M3U -> Color(0xFF66BB6A)
                    SourceKind.STALKER -> Color(0xFFFFA726)
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(kindColor.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = source.kind.name,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = kindColor,
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (focused) Color(0xFF10171E) else Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hostOf(source.url),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = if (focused) Color(0xFF37474F) else Color.White.copy(alpha = 0.65f),
                    )
                }

                if (confirming) {
                    Text(
                        text = stringResource(R.string.providers_remove_confirm),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF5350),
                    )
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onConfirmRemove) {
                        Text(
                            text = stringResource(R.string.providers_yes_remove),
                            color = Color(0xFFEF5350),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    TextButton(onClick = onCancelRemove) {
                        Text(stringResource(R.string.common_cancel))
                    }
                } else {
                    OutlinedButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    Spacer(Modifier.width(10.dp))
                    TextButton(onClick = onAskRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.common_remove), color = Color(0xFFEF5350))
                    }
                }
            }

            // Stream format picker for Xtream sources
            if (source.kind == SourceKind.XTREAM) {
                Spacer(Modifier.height(14.dp))
                StreamFormatSelector(selected = source.liveFormat, onSelect = onSetLiveFormat)
            }
        }
    }
}

@Composable
private fun ProviderActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF1E2833),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) Color(0xFF10171E) else Color(0xFF26C6DA),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (focused) Color(0xFF10171E) else Color.White,
        )
    }
}

@Composable
private fun ProviderBackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF1E2833),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = if (focused) Color(0xFF10171E) else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (focused) Color(0xFF10171E) else Color.White,
        )
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
