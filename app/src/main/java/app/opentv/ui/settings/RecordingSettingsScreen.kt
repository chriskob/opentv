/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.recording.RecordingStorage
import app.opentv.recording.SmbClient
import app.opentv.recording.SmbConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Where recordings are written: this box's internal storage, or a NAS over SMB (Synology and the
 * like). The NAS credentials live only on this device, exactly like a provider's login.
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
    var status by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun persist() {
        settings.setSmbConfig(host, share, folder, user, password)
        settings.setRecordingTarget(target)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_recording_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = { persist(); onBack() }) { Text(stringResource(R.string.common_done)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.recset_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 720.dp),
        )

        Spacer(Modifier.height(20.dp))
        SectionCard(stringResource(R.string.recset_save_to)) {
            TargetRow(
                label = stringResource(R.string.recset_internal_label),
                subtitle = RecordingStorage.internalDir(context).absolutePath,
                selected = target == AppSettings.RecordingTarget.INTERNAL,
            ) { target = AppSettings.RecordingTarget.INTERNAL }
            Spacer(Modifier.height(8.dp))
            TargetRow(
                label = stringResource(R.string.recset_smb_label),
                subtitle = stringResource(R.string.recset_smb_subtitle),
                selected = target == AppSettings.RecordingTarget.SMB,
            ) { target = AppSettings.RecordingTarget.SMB }
        }

        if (target == AppSettings.RecordingTarget.SMB) {
            Spacer(Modifier.height(16.dp))
            SectionCard(stringResource(R.string.recset_nas_connection)) {
                Field(stringResource(R.string.recset_field_server), host, "192.168.1.10") { host = it }
                Field(stringResource(R.string.recset_field_share), share, "video") { share = it }
                Field(stringResource(R.string.recset_field_folder), folder, "OpenTV") { folder = it }
                Field(stringResource(R.string.recset_field_username), user, "") { user = it }
                Field(stringResource(R.string.recset_field_password), password, "") { password = it }

                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
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
                    ) { Text(if (testing) stringResource(R.string.recset_status_testing) else stringResource(R.string.recset_test_connection)) }
                }
                status?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = { persist(); status = context.getString(R.string.recset_status_saved) }) { Text(stringResource(R.string.common_save)) }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.widthIn(max = 720.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@Composable
private fun TargetRow(label: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (selected) Icons.Filled.RadioButtonChecked else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Field(label: String, value: String, placeholder: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}
