/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.delay
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
            refreshing = ui.syncingSourceId == src.id,
            onDismiss = { editingSource = null },
            onSave = { updated, resync ->
                viewModel.updateSource(updated, resync = resync)
                editingSource = null
            },
            onTest = { draft -> viewModel.testSource(draft) },
            onRefresh = { viewModel.refreshSource(src) },
            onDelete = {
                pendingRemove = src
                editingSource = null
            },
        )
    }

    pendingRemove?.let { source ->
        SettingsDialog(
            title = "Delete playlist?",
            titleColor = SettingsDanger,
            message = "\"${source.name}\" and its channels and VOD will be removed from OpenTV.",
            onDismiss = { pendingRemove = null },
            actions = {
                SettingsButton(
                    text = stringResource(R.string.common_cancel),
                    onClick = { pendingRemove = null },
                    style = SettingsButtonStyle.Secondary,
                )
                Spacer(Modifier.width(12.dp))
                SettingsButton(
                    text = "Delete",
                    onClick = {
                        viewModel.delete(source)
                        pendingRemove = null
                    },
                    style = SettingsButtonStyle.Danger,
                )
            },
        )
    }

    SettingsPage(
        title = stringResource(R.string.settings_providers_title),
        subtitle = stringResource(R.string.settings_providers_page_subtitle),
        onBack = onBack,
        actions = {
            SettingsButton(
                text = "Remote Edit / Pair",
                onClick = onOpenRemotePairing,
                style = SettingsButtonStyle.Secondary,
                icon = Icons.Filled.PhoneAndroid,
            )
            Spacer(Modifier.width(12.dp))
            SettingsButton(
                text = stringResource(R.string.providers_add),
                onClick = onAddSource,
                style = SettingsButtonStyle.Primary,
                icon = Icons.Filled.Add,
            )
        },
    ) {
        if (ui.sources.isEmpty()) {
            SettingsEmptyState(
                title = stringResource(R.string.providers_empty),
                icon = Icons.Filled.Dns,
            )
        } else {
            val enabledSources = remember(ui.sources) { ui.sources.filter { it.enabled } }
            val disabledSources = remember(ui.sources) { ui.sources.filterNot { it.enabled } }

            ProviderSection(
                title = "Enabled Playlists (${enabledSources.size})",
                icon = Icons.Filled.CheckCircle,
                sources = enabledSources,
                emptyMessage = "No playlists enabled. Turn on a playlist from the list below or add a new provider.",
                onEdit = { editingSource = it },
                onToggle = { source, enabled -> viewModel.setEnabled(source, enabled) },
                onDelete = { pendingRemove = it },
                onSetLiveFormat = { source, format -> viewModel.setLiveFormat(source, format) },
                onRefresh = { source -> viewModel.refreshSource(source) },
                syncingSourceId = ui.syncingSourceId,
                showMove = true,
                onMove = { source, delta -> viewModel.moveSource(source.id, delta) },
                focusFirst = true,
            )

            if (disabledSources.isNotEmpty()) {
                Spacer(Modifier.height(SettingsSpacing.SectionGap))
                ProviderSection(
                    title = "Disabled Playlists (${disabledSources.size})",
                    icon = Icons.Filled.Block,
                    sources = disabledSources,
                    onEdit = { editingSource = it },
                    onToggle = { source, enabled -> viewModel.setEnabled(source, enabled) },
                    onDelete = { pendingRemove = it },
                    onSetLiveFormat = { source, format -> viewModel.setLiveFormat(source, format) },
                    onRefresh = { source -> viewModel.refreshSource(source) },
                    syncingSourceId = ui.syncingSourceId,
                )
            }
        }
    }
}

/**
 * One section of the provider list. Enabled and disabled playlists are byte-identical apart from
 * their label/icon (and the enabled section's empty hint), so both call this.
 */
@Composable
private fun ProviderSection(
    title: String,
    icon: ImageVector,
    sources: List<Source>,
    onEdit: (Source) -> Unit,
    onToggle: (Source, Boolean) -> Unit,
    onDelete: (Source) -> Unit,
    onSetLiveFormat: (Source, LiveStreamFormat) -> Unit,
    onRefresh: (Source) -> Unit,
    syncingSourceId: Long?,
    emptyMessage: String? = null,
    showMove: Boolean = false,
    onMove: (Source, Int) -> Unit = { _, _ -> },
    focusFirst: Boolean = false,
) {
    val firstName = remember { FocusRequester() }
    if (focusFirst && sources.isNotEmpty()) {
        LaunchedEffect(sources.first().id) {
            delay(120)
            runCatching { firstName.requestFocus() }
        }
    }
    SettingsSection(title = title, icon = icon, collapsible = false) {
        if (sources.isEmpty() && emptyMessage != null) {
            SettingsEmptyState(title = emptyMessage)
        } else {
            sources.forEachIndexed { index, source ->
                if (index > 0) Spacer(Modifier.height(8.dp))
                ProviderRow(
                    nameFocus = if (focusFirst && index == 0) firstName else null,
                    source = source,
                    index = index,
                    count = sources.size,
                    showMove = showMove,
                    refreshing = syncingSourceId == source.id,
                    onMove = { onMove(source, it) },
                    onEdit = { onEdit(source) },
                    onToggle = { onToggle(source, it) },
                    onDelete = { onDelete(source) },
                    onSetLiveFormat = { onSetLiveFormat(source, it) },
                    onRefresh = { onRefresh(source) },
                )
            }
            val xtream = sources.filter { it.kind == SourceKind.XTREAM }
            if (xtream.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                xtream.forEach { source ->
                    StreamFormatLine(source = source, onSetLiveFormat = { onSetLiveFormat(source, it) })
                }
            }
        }
    }
}

/** A compact up/down reorder control for a playlist row. */
@Composable
private fun MoveButton(
    icon: ImageVector,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(SettingsSpacing.ControlHeight)
            .settingsFocus(shape = SettingsShape.Control, enabled = enabled, focusScale = 1.06f)
            .focusable(enabled)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size(18.dp),
        )
    }
}

private val NameColumnWidth = 440.dp

@Composable
private fun ProviderRow(
    source: Source,
    index: Int,
    count: Int,
    showMove: Boolean,
    refreshing: Boolean,
    onMove: (Int) -> Unit,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onSetLiveFormat: (LiveStreamFormat) -> Unit,
    onRefresh: () -> Unit,
    nameFocus: FocusRequester? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The name is the edit target; the reorder arrows sit right after it.
            Row(
                modifier = Modifier
                    .width(NameColumnWidth)
                    .then(if (nameFocus != null) Modifier.focusRequester(nameFocus) else Modifier)
                    .settingsFocus(shape = SettingsShape.Row)
                    .focusable()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onEdit,
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Kind Badge — genuinely semantic hues per provider kind.
                val kindColor = when (source.kind) {
                    SourceKind.XTREAM -> AppTheme.palette.info
                    SourceKind.M3U -> AppTheme.palette.success
                    SourceKind.STALKER -> AppTheme.palette.warning
                    SourceKind.PLEX -> AppTheme.palette.favourite
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
                        fontWeight = FontWeight.Medium,
                        color = kindColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(Modifier.weight(1f)) {
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = hostOf(source.url),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Edit playlist",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            // Reorder controls — only on the enabled list, where the order actually shows in the app.
            if (showMove) {
                Spacer(Modifier.width(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    MoveButton(
                        icon = Icons.Filled.KeyboardArrowUp,
                        enabled = index > 0,
                        contentDescription = "Move up",
                        onClick = { onMove(-1) },
                    )
                    MoveButton(
                        icon = Icons.Filled.KeyboardArrowDown,
                        enabled = index < count - 1,
                        contentDescription = "Move down",
                        onClick = { onMove(1) },
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            SettingsSwitch(
                checked = source.enabled,
                onToggle = onToggle,
            )
        }

    }
}

@Composable
private fun StreamFormatLine(
    source: Source,
    onSetLiveFormat: (LiveStreamFormat) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(NameColumnWidth)) {
            Text(
                text = source.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Stream format",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingsSegmented(
            options = listOf(
                stringResource(R.string.provider_stream_format_hls),
                stringResource(R.string.provider_stream_format_ts),
            ),
            selectedIndex = if (source.liveFormat == LiveStreamFormat.HLS) 0 else 1,
            onSelect = { index ->
                onSetLiveFormat(if (index == 0) LiveStreamFormat.HLS else LiveStreamFormat.MPEG_TS)
            },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EditSourceDialog(
    source: Source,
    refreshing: Boolean,
    onDismiss: () -> Unit,
    onSave: (Source, Boolean) -> Unit,
    onTest: suspend (Source) -> Result<String>,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
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

    SettingsDialog(
        title = "Edit playlist",
        onDismiss = onDismiss,
        content = {
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
                            SourceKind.PLEX -> "Plex Server Address"
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

            SettingsToggleRow(
                title = "Re-sync playlist and guide after saving",
                checked = resyncOnSave,
                onToggle = { resyncOnSave = it },
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SettingsButton(
                    text = "Test connection",
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
                    style = SettingsButtonStyle.Secondary,
                    enabled = !testing,
                )
                SettingsButton(
                    text = stringResource(R.string.provider_update),
                    onClick = onRefresh,
                    style = SettingsButtonStyle.Secondary,
                    enabled = !refreshing,
                )
                if (testing) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                testStatus?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppTheme.primary,
                    )
                }
            }
        },
        actions = {
            SettingsButton(
                text = stringResource(R.string.common_delete),
                onClick = onDelete,
                style = SettingsButtonStyle.Danger,
                icon = Icons.Filled.Delete,
            )
            Spacer(Modifier.weight(1f))
            SettingsButton(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss,
                style = SettingsButtonStyle.Secondary,
            )
            Spacer(Modifier.width(12.dp))
            SettingsButton(
                text = stringResource(R.string.common_save),
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
                style = SettingsButtonStyle.Primary,
            )
        },
    )
}

/**
 * The per-source HLS / MPEG-TS picker. A segmented control with a one-line hint on when to reach
 * for it.
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
        SettingsSegmented(
            options = listOf(
                stringResource(R.string.provider_stream_format_hls),
                stringResource(R.string.provider_stream_format_ts),
            ),
            selectedIndex = if (selected == LiveStreamFormat.HLS) 0 else 1,
            onSelect = { index -> onSelect(if (index == 0) LiveStreamFormat.HLS else LiveStreamFormat.MPEG_TS) },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.provider_stream_format_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Host only — the full URL carries the login and has no place on a settings list. */
private fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host ?: url }.getOrDefault(url)
