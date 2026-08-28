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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.sync.SyncServer
import app.opentv.ui.SyncViewModel

/**
 * Local watch-history sync between two OpenTV devices on the same wifi.
 */
@Composable
fun SyncScreen(
    onBack: () -> Unit,
    viewModel: SyncViewModel = viewModel(),
) {
    var mode by remember { mutableStateOf(Mode.SHARE) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_sync_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Sync watch-progress across devices on your local network",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            SyncBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeChip(stringResource(R.string.sync_share_from_here), mode == Mode.SHARE) { mode = Mode.SHARE }
            ModeChip(stringResource(R.string.sync_receive), mode == Mode.RECEIVE) { mode = Mode.RECEIVE }
            ModeChip(stringResource(R.string.sync_nas), mode == Mode.NAS) { mode = Mode.NAS }
        }

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
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(24.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            when (val s = state) {
                is SyncServer.State.Sharing -> {
                    Text(stringResource(R.string.sync_ready_to_share), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF26C6DA))
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.sync_on_other_device), color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    Field(stringResource(R.string.sync_address), s.session.address)
                    Spacer(Modifier.height(8.dp))
                    Field(stringResource(R.string.sync_code), s.session.code)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.sync_type_in_one_go, s.session.address, s.session.code),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.stopSharing() }) { Text(stringResource(R.string.sync_stop_sharing)) }
                }
                is SyncServer.State.Failed -> {
                    Text(s.reason, color = Color(0xFFEF5350))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { viewModel.startSharing() }) { Text(stringResource(R.string.common_try_again)) }
                }
                else -> {
                    Text(stringResource(R.string.sync_will_share), color = Color.White)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { viewModel.startSharing() }) { Text(stringResource(R.string.sync_start_sharing)) }
                }
            }
        }
    }
}

@Composable
private fun ReceivePane(viewModel: SyncViewModel) {
    val state by viewModel.receiveState.collectAsState()
    var entry by remember { mutableStateOf("") }

    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF18222C))
                .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                .padding(24.dp),
        ) {
            Column(Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.sync_type_what_shows), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF26C6DA))
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF10171E))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        if (entry.isEmpty()) "e.g. 192.168.1.50:4242#ABCD" else entry,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (entry.isEmpty()) Color.White.copy(alpha = 0.35f) else Color.White,
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        enabled = entry.contains('#') && state !is SyncViewModel.ReceiveState.Connecting,
                        onClick = {
                            val address = entry.substringBefore('#')
                            val code = entry.substringAfter('#')
                            viewModel.receive(address, code)
                        },
                    ) { Text(stringResource(R.string.sync_connect)) }
                    OutlinedButton(onClick = { entry = ""; viewModel.resetReceive() }) { Text(stringResource(R.string.common_cancel)) }
                }
                Spacer(Modifier.height(12.dp))
                when (val s = state) {
                    is SyncViewModel.ReceiveState.Connecting -> Text(stringResource(R.string.sync_connecting), color = Color.White)
                    is SyncViewModel.ReceiveState.Done -> Text(
                        stringResource(R.string.sync_synced_items, s.merged),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF26C6DA),
                    )
                    is SyncViewModel.ReceiveState.Failed -> Text(
                        s.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFEF5350),
                    )
                    else -> Unit
                }
            }
        }

        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF18222C))
                .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                .padding(20.dp),
        ) {
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

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(24.dp),
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(stringResource(R.string.sync_nas_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF26C6DA))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.sync_nas_desc), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(16.dp))

            if (!configured) {
                Text(
                    stringResource(R.string.sync_nas_needs_setup),
                    color = Color(0xFFEF5350),
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        enabled = nasState !is SyncViewModel.NasState.Syncing,
                        onClick = { viewModel.syncNas() },
                    ) { Text(stringResource(R.string.sync_nas_sync_now)) }

                    when (val s = nasState) {
                        is SyncViewModel.NasState.Syncing ->
                            Text(stringResource(R.string.sync_nas_syncing), color = Color.White)
                        is SyncViewModel.NasState.Done ->
                            Text(s.message, color = Color(0xFF26C6DA))
                        is SyncViewModel.NasState.Failed ->
                            Text(s.message, color = Color(0xFFEF5350))
                        else -> Unit
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sync_nas_auto),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = autoSync, onCheckedChange = { viewModel.setNasAutoSync(it) })
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String) {
    Row {
        Text("$label:  ", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium, color = Color.White)
        Text(value, style = MaterialTheme.typography.titleMedium, color = Color(0xFF26C6DA), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> Color(0xFFF0F4F8)
        selected -> Color(0xFF1E2F3E)
        else -> Color(0xFF18222C)
    }
    val fg = if (focused) Color(0xFF10171E) else if (selected) Color(0xFF26C6DA) else Color.White
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
        color = fg,
        modifier = Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
                else if (selected) Modifier.border(1.dp, Color(0xFF26C6DA), RoundedCornerShape(10.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(10.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun NumPad(onKey: (String) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf("123", "456", "789", ".0:", "#⌫")
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { line ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                line.forEach { ch ->
                    NumKey(ch.toString()) {
                        if (ch == '⌫') onBackspace() else onKey(ch.toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun NumKey(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val bg = if (focused) Color(0xFFF0F4F8) else Color(0xFF10171E)
    val fg = if (focused) Color(0xFF10171E) else Color.White
    Box(
        Modifier
            .size(54.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(8.dp)),
            )
            .focusable()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun SyncBackButton(onClick: () -> Unit) {
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
