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
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Switch
import androidx.compose.ui.graphics.Brush
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.cardFocusBg
import app.opentv.ui.theme.dark
import app.opentv.ui.theme.light
import app.opentv.ui.theme.primary
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
import app.opentv.ui.components.TvOutlinedTextField
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

    pendingRemove?.let { source ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            containerColor = Color(0xFF161F28),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = "Delete Playlist",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${source.name}\"? Channels and VOD associated with this playlist will be removed from OpenTV.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFCFD8DC),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.delete(source)
                        pendingRemove = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F),
                    ),
                ) {
                    Text("Yes, Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemove = null }) {
                    Text(stringResource(R.string.common_cancel), color = Color(0xFFB0BEC5))
                }
            },
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

        Spacer(Modifier.height(20.dp))

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
                        tint = AppTheme.primary,
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

        val enabledSources = remember(ui.sources) { ui.sources.filter { it.enabled } }
        val disabledSources = remember(ui.sources) { ui.sources.filterNot { it.enabled } }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // ---- Section 1: Enabled Playlists
            item(key = "header_enabled") {
                ProviderSectionHeader(
                    title = "Enabled Playlists (${enabledSources.size})",
                    icon = Icons.Filled.CheckCircle,
                )
            }

            if (enabledSources.isEmpty()) {
                item(key = "empty_enabled") {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFF18222C))
                            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                    ) {
                        Text(
                            text = "No playlists enabled. Turn on a playlist from the list below or add a new provider.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF8B9BA8),
                        )
                    }
                }
            } else {
                items(enabledSources, key = { "enabled_${it.id}" }) { source ->
                    ProviderRow(
                        source = source,
                        onEdit = { editingSource = source },
                        onToggle = { enabled -> viewModel.setEnabled(source, enabled) },
                        onDelete = { pendingRemove = source },
                        onSetLiveFormat = { viewModel.setLiveFormat(source, it) },
                    )
                }
            }

            // ---- Section 2: Disabled Playlists
            if (disabledSources.isNotEmpty()) {
                item(key = "header_disabled") {
                    Spacer(Modifier.height(8.dp))
                    ProviderSectionHeader(
                        title = "Disabled Playlists (${disabledSources.size})",
                        icon = Icons.Filled.Block,
                    )
                }

                items(disabledSources, key = { "disabled_${it.id}" }) { source ->
                    ProviderRow(
                        source = source,
                        onEdit = { editingSource = source },
                        onToggle = { enabled -> viewModel.setEnabled(source, enabled) },
                        onDelete = { pendingRemove = source },
                        onSetLiveFormat = { viewModel.setLiveFormat(source, it) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSectionHeader(title: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = AppTheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF1E2D3C))
        )
    }
}

@Composable
private fun ProviderRow(
    source: Source,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSetLiveFormat: (LiveStreamFormat) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Provider Info & Edit card (primary clickable element)
            var infoFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { infoFocused = it.isFocused }
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (infoFocused) AppTheme.cardFocusBg else Color.Transparent)
                    .then(
                        if (infoFocused) Modifier.border(2.dp, AppTheme.primary, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (infoFocused) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(AppTheme.primary, RoundedCornerShape(2.dp))
                    )
                    Spacer(Modifier.width(10.dp))
                }

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

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hostOf(source.url),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = if (infoFocused) Color(0xFFB0BEC5) else Color.White.copy(alpha = 0.65f),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Visual edit indicator
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (infoFocused) AppTheme.primary.copy(alpha = 0.2f) else Color(0xFF263442).copy(alpha = 0.4f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                        tint = if (infoFocused) AppTheme.primary else Color(0xFF8B9BA8),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Edit",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = if (infoFocused) Color.White else Color(0xFF8B9BA8),
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Delete action button
            var deleteFocused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .onFocusChanged { deleteFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (deleteFocused) Color(0xFFD32F2F).copy(alpha = 0.25f)
                        else Color(0xFF221518).copy(alpha = 0.5f),
                    )
                    .then(
                        if (deleteFocused) Modifier.border(1.5.dp, Color(0xFFEF5350), RoundedCornerShape(8.dp))
                        else Modifier.border(0.75.dp, Color(0xFF5A2C2C), RoundedCornerShape(8.dp)),
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = if (deleteFocused) Color(0xFFFF5252) else Color(0xFFEF5350),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.common_delete),
                    color = if (deleteFocused) Color.White else Color(0xFFEF5350),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 13.sp),
                    fontWeight = if (deleteFocused) FontWeight.Bold else FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.width(12.dp))

            // Toggle switch
            Switch(
                checked = source.enabled,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = AppTheme.primary,
                    checkedTrackColor = AppTheme.dark.copy(alpha = 0.6f),
                    uncheckedThumbColor = Color(0xFF90A4AE),
                    uncheckedTrackColor = Color(0xFF263238),
                ),
            )
        }

        // Stream format picker for Xtream sources
        if (source.kind == SourceKind.XTREAM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Stream Format:",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = Color(0xFF8B9BA8),
                )
                FormatSegment(
                    label = "HLS (.m3u8)",
                    selected = source.liveFormat == LiveStreamFormat.HLS,
                    onClick = { onSetLiveFormat(LiveStreamFormat.HLS) },
                )
                FormatSegment(
                    label = "MPEG-TS (.ts)",
                    selected = source.liveFormat == LiveStreamFormat.MPEG_TS,
                    onClick = { onSetLiveFormat(LiveStreamFormat.MPEG_TS) },
                )
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
                if (focused) Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary))
                else Brush.linearGradient(listOf(Color(0xFF1E2833), Color(0xFF1E2833))),
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.light, RoundedCornerShape(10.dp))
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
            tint = if (focused) Color.White else AppTheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
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
                if (focused) Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary))
                else Brush.linearGradient(listOf(Color(0xFF1E2833), Color(0xFF1E2833))),
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.light, RoundedCornerShape(10.dp))
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
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
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
                TvOutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist / Provider Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                TvOutlinedTextField(
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
                    TvOutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TvOutlinedTextField(
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
                    TvOutlinedTextField(
                        value = mac,
                        onValueChange = { mac = it },
                        label = { Text("MAC Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                TvOutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text("Custom EPG / XMLTV URL (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                TvOutlinedTextField(
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

@Composable
private fun FormatSegment(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    focused -> AppTheme.primary
                    selected -> AppTheme.dark
                    else -> Color(0xFF1E2833)
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else if (selected) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                else Modifier.border(0.75.dp, Color(0xFF2C3E50), RoundedCornerShape(8.dp))
            )
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            color = when {
                focused -> Color.White
                selected -> AppTheme.light
                else -> Color(0xFFB0BEC5)
            }
        )
    }
}

/** Host only — the full URL carries the login and has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
