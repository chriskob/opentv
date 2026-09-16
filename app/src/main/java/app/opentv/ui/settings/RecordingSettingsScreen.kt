/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.core.isIgnoringBatteryOptimizations
import app.opentv.core.requestIgnoreBatteryOptimizations
import app.opentv.recording.RecordingStorage
import app.opentv.recording.SmbClient
import app.opentv.recording.SmbConfig
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where recordings are written: this box's internal storage, a NAS over SMB (Synology and the
 * like), or a plugged-in USB / external drive. The NAS credentials live only on this device,
 * exactly like a provider's login; the USB drive is addressed through a folder the user grants
 * with the system picker (Storage Access Framework), which needs no storage permission.
 */
@Composable
fun RecordingSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ServiceLocator.get(context).settings }
    val scope = rememberCoroutineScope()

    var target by remember { mutableStateOf(settings.recordingTarget.value) }
    var host by remember { mutableStateOf(settings.smbHost.value) }
    var share by remember { mutableStateOf(settings.smbShare.value) }
    var folder by remember { mutableStateOf(settings.smbFolder.value) }
    var user by remember { mutableStateOf(settings.smbUser.value) }
    var password by remember { mutableStateOf(settings.smbPassword.value) }
    var usbTree by remember { mutableStateOf(settings.usbTreeUri.value) }
    var usbLabel by remember { mutableStateOf(settings.usbFolderLabel.value) }
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    // Recording behaviour. These apply the moment they're changed so they hold whether the user
    // leaves via Done or Save.
    var padStart by remember { mutableStateOf(settings.recordPadStartMinutes.value) }
    var padEnd by remember { mutableStateOf(settings.recordPadEndMinutes.value) }
    var autoSwitch by remember { mutableStateOf(settings.recordAutoSwitch.value) }
    var livePause by remember { mutableStateOf(settings.livePauseEnabled.value) }

    // The SAF folder picker. On a granted tree we take a persistable read/write permission so the
    // grant (and thus playback of what we record there) survives restarts, then remember the folder.
    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri: Uri? ->
        if (uri != null) {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.takePersistableUriPermission(uri, flags) }
            val label = DocumentFile.fromTreeUri(context, uri)?.name
                ?: uri.lastPathSegment ?: context.getString(R.string.recset_usb_label)
            settings.setUsbTree(uri.toString(), label)
            usbTree = uri.toString()
            usbLabel = label
            target = AppSettings.RecordingTarget.USB
            status = context.getString(R.string.recset_usb_saving_to, label)
        }
    }

    fun persist() {
        settings.setSmbConfig(host, share, folder, user, password)
        // Don't commit USB as the destination unless a folder has actually been granted, so
        // leaving the screen without picking one can never strand recordings with nowhere to go.
        val safeTarget =
            if (target == AppSettings.RecordingTarget.USB && usbTree == null) settings.recordingTarget.value
            else target
        settings.setRecordingTarget(safeTarget)
    }

    SettingsPage(
        title = stringResource(R.string.settings_recording_title),
        subtitle = stringResource(R.string.settings_recording_page_subtitle),
        onBack = { persist(); onBack() },
    ) {
        SettingsSection(title = stringResource(R.string.recset_background_title)) {
            BackgroundStatus()
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))
        SettingsSection(title = stringResource(R.string.recset_behaviour_title)) {
            SettingsStepperRow(
                title = stringResource(R.string.recset_pad_start),
                subtitle = stringResource(R.string.recset_pad_start_desc),
                value = "$padStart ${stringResource(R.string.recset_minutes_short)}",
                canDecrement = padStart > 0,
                canIncrement = padStart < 30,
                onDecrement = {
                    val next = (padStart - 1).coerceAtLeast(0)
                    padStart = next
                    settings.setRecordPadding(next, padEnd)
                },
                onIncrement = {
                    val next = (padStart + 1).coerceAtMost(30)
                    padStart = next
                    settings.setRecordPadding(next, padEnd)
                },
            )
            SettingsStepperRow(
                title = stringResource(R.string.recset_pad_end),
                subtitle = stringResource(R.string.recset_pad_end_desc),
                value = "$padEnd ${stringResource(R.string.recset_minutes_short)}",
                canDecrement = padEnd > 0,
                canIncrement = padEnd < 60,
                onDecrement = {
                    val next = (padEnd - 1).coerceAtLeast(0)
                    padEnd = next
                    settings.setRecordPadding(padStart, next)
                },
                onIncrement = {
                    val next = (padEnd + 1).coerceAtMost(60)
                    padEnd = next
                    settings.setRecordPadding(padStart, next)
                },
            )
            SettingsToggleRow(
                title = stringResource(R.string.recset_autoswitch),
                subtitle = stringResource(R.string.recset_autoswitch_desc),
                checked = autoSwitch,
                onToggle = { autoSwitch = it; settings.setRecordAutoSwitch(it) },
            )
            SettingsToggleRow(
                title = stringResource(R.string.recset_livepause),
                subtitle = stringResource(R.string.recset_livepause_desc),
                checked = livePause,
                onToggle = { livePause = it; settings.setLivePauseEnabled(it) },
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))
        SettingsSection(title = stringResource(R.string.recset_save_to)) {
            SettingsChoiceRow(
                title = stringResource(R.string.recset_internal_label),
                subtitle = RecordingStorage.internalDir(context).absolutePath,
                selected = target == AppSettings.RecordingTarget.INTERNAL,
                onSelect = { target = AppSettings.RecordingTarget.INTERNAL },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.recset_smb_label),
                subtitle = stringResource(R.string.recset_smb_subtitle),
                selected = target == AppSettings.RecordingTarget.SMB,
                onSelect = { target = AppSettings.RecordingTarget.SMB },
            )
            SettingsChoiceRow(
                title = stringResource(R.string.recset_usb_label),
                subtitle = stringResource(R.string.recset_usb_subtitle),
                selected = target == AppSettings.RecordingTarget.USB,
                onSelect = { target = AppSettings.RecordingTarget.USB },
            )
        }

        // Shown for every destination, not just SMB, so Save confirmations and USB/internal
        // messages are actually visible.
        status?.let {
            Spacer(Modifier.height(10.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        if (target == AppSettings.RecordingTarget.SMB) {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = stringResource(R.string.recset_nas_connection)) {
                Field(stringResource(R.string.recset_field_server), host, "192.168.1.10") { host = it }
                Field(stringResource(R.string.recset_field_share), share, "video") { share = it }
                Field(stringResource(R.string.recset_field_folder), folder, "OpenTV") { folder = it }
                Field(stringResource(R.string.recset_field_username), user, "") { user = it }
                Field(stringResource(R.string.recset_field_password), password, "", isPassword = true) { password = it }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SettingsButton(
                        text = if (testing) stringResource(R.string.recset_status_testing) else stringResource(R.string.recset_test_connection),
                        enabled = !testing && host.isNotBlank() && share.isNotBlank(),
                        onClick = {
                            testing = true
                            status = context.getString(R.string.recset_status_testing)
                            val cfg = SmbConfig(host.trim(), share.trim(), folder.trim(), user, password)
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    runCatching { SmbClient.test(cfg) }
                                }
                                testing = false
                                status = result.fold(
                                    onSuccess = { context.getString(R.string.recset_status_connected) },
                                    onFailure = { context.getString(R.string.recset_status_failed, it.message ?: it.javaClass.simpleName) },
                                )
                            }
                        },
                    )
                }
            }
        }

        if (target == AppSettings.RecordingTarget.USB) {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = stringResource(R.string.recset_usb_section)) {
                val label = usbLabel
                if (usbTree != null && label != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.recset_usb_saving_to, label),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    Text(
                        stringResource(R.string.recset_usb_none),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(12.dp))
                SettingsButton(
                    text = if (usbTree != null) stringResource(R.string.recset_usb_change)
                    else stringResource(R.string.recset_usb_choose),
                    style = SettingsButtonStyle.Primary,
                    onClick = {
                        runCatching { folderPicker.launch(null) }
                            .onFailure { status = context.getString(R.string.recset_usb_no_picker) }
                    },
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.recset_usb_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (target == AppSettings.RecordingTarget.USB && usbTree == null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.recset_usb_need_folder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        if (target == AppSettings.RecordingTarget.INTERNAL) {
            Spacer(Modifier.height(16.dp))
            SettingsSection(title = stringResource(R.string.recset_storage_title)) {
                StorageInfo()
            }
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))
        SettingsButton(
            text = stringResource(R.string.common_save),
            enabled = target != AppSettings.RecordingTarget.USB || usbTree != null,
            style = SettingsButtonStyle.Primary,
            onClick = { persist(); status = context.getString(R.string.recset_status_saved) },
        )
    }
}

/** Used-by-recordings and free-space readout for the box's internal storage. */
@Composable
private fun StorageInfo() {
    val context = LocalContext.current
    var used by remember { mutableLongStateOf(-1L) }
    var free by remember { mutableLongStateOf(-1L) }
    var count by remember { androidx.compose.runtime.mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val (u, c, f) = withContext(Dispatchers.IO) {
            val dir = RecordingStorage.internalDir(context)
            val files = dir.listFiles()?.filter { it.isFile } ?: emptyList()
            Triple(files.sumOf { it.length() }, files.size, dir.usableSpace)
        }
        used = u; count = c; free = f
    }

    if (used < 0) {
        Text(stringResource(R.string.recset_storage_reading), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(
            stringResource(R.string.recset_storage_used, formatBytes(used), count),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.recset_storage_free, formatBytes(free)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.1f GB", mb / 1024.0) else String.format("%.0f MB", mb)
}

/**
 * Whether OpenTV is allowed to keep recording in the background (exempt from battery optimisation).
 * Reads *Allowed* or *Not allowed — Fix*; tapping Fix opens the system dialog to grant it. The
 * status is re-read every time the screen resumes, so it flips to Allowed the moment the user
 * comes back from granting it.
 */
@Composable
private fun BackgroundStatus() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var allowed by remember { mutableStateOf(context.isIgnoringBatteryOptimizations()) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val now = context.isIgnoringBatteryOptimizations()
                // Just came back from granting it: say so out loud so it's unmistakable.
                if (now && !allowed) {
                    Toast.makeText(context, context.getString(R.string.recset_background_toast), Toast.LENGTH_LONG).show()
                }
                allowed = now
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (allowed) {
        // A plain, unmistakable "it's live" confirmation box — the thing the user is looking for
        // when they come back from the system dialog. Green is a semantic success state here.
        Row(
            Modifier
                .fillMaxWidth()
                .clip(SettingsShape.Row)
                .background(AppTheme.palette.successContainer)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = AppTheme.palette.success,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recset_background_on_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.palette.success,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.recset_background_on_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    } else {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(14.dp))
                Text(
                    stringResource(R.string.recset_background_not_allowed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                SettingsButton(
                    text = stringResource(R.string.recset_background_fix),
                    onClick = { context.requestIgnoreBatteryOptimizations() },
                    style = SettingsButtonStyle.Primary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.recset_background_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    placeholder: String,
    isPassword: Boolean = false,
    onChange: (String) -> Unit,
) {
    var revealed by remember { mutableStateOf(false) }
    TvOutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        // A recording password is a real credential (a NAS login) — don't paint it on a living-room
        // screen. Masked by default, with an eye to reveal it if you need to check what you typed.
        visualTransformation = if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
        trailingIcon = if (!isPassword) null else {
            {
                SettingsIconButton(
                    icon = if (revealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    onClick = { revealed = !revealed },
                    contentDescription = stringResource(
                        if (revealed) R.string.recset_pw_hide else R.string.recset_pw_show,
                    ),
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
