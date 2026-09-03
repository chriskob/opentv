/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Source
import app.opentv.pairing.QrCodes
import app.opentv.pairing.RemotePairingClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

import android.app.Activity
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.LinearProgressIndicator
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.RemoteProvisioningProgress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import app.opentv.core.AppSettings
import app.opentv.pairing.ProvisionedSource

/**
 * Remote pairing screen connecting to a self-hosted provisioning server (e.g. on Synology NAS).
 *
 * Displays:
 * 1. 6-character ephemeral pairing code.
 * 2. QR code directing the phone/browser to the admin portal.
 * 3. Immediate real-time WebSocket receipt of M3U / Xtream credentials.
 * 4. Step-by-step progress dashboard with live channel processing counters and EPG timeline.
 */
@Composable
fun RemotePairingScreen(
    onReceived: (List<ProvisionedSource>) -> Unit = {},
    viewModel: SourcesViewModel = viewModel(),
    onCancel: () -> Unit,
    onFinished: () -> Unit = onCancel,
) {
    val context = LocalContext.current

    // Keep the TV screen awake while pairing or syncing so ambient mode / screensaver never kicks in
    DisposableEffect(Unit) {
        var ctx = context
        var act: Activity? = null
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                act = ctx
                break
            }
            ctx = ctx.baseContext
        }
        act?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            act?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            viewModel.resetProvisioningProgress()
        }
    }

    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val savedServerUrl by settings.remotePairingServerUrl.collectAsState()
    val currentSources by graph.sourceRepository.observeAll().collectAsState(initial = emptyList())
    val syncProgress by viewModel.provisioningProgress.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var inputServerUrl by remember { mutableStateOf(savedServerUrl.ifBlank { AppSettings.DEFAULT_REMOTE_PAIRING_URL }) }

    val client = remember { RemotePairingClient() }
    val state by client.state.collectAsState()

    // Whenever current sources arrive or change, notify the client
    LaunchedEffect(currentSources) {
        client.updateSources(currentSources)
    }

    fun restartClient() {
        client.stop()
        if (savedServerUrl.isNotBlank()) {
            client.start(savedServerUrl, currentSources)
        }
    }

    DisposableEffect(savedServerUrl) {
        if (savedServerUrl.isNotBlank()) {
            client.start(savedServerUrl, currentSources)
        }
        onDispose {
            client.stop()
        }
    }

    LaunchedEffect(state) {
        (state as? RemotePairingClient.State.Received)?.let {
            viewModel.saveAndSyncBatch(it.sources)
            onReceived(it.sources)
        }
    }

    if (showConfigDialog) {
        ServerConfigDialog(
            currentUrl = inputServerUrl,
            onConfirm = { newUrl ->
                settings.setRemotePairingServerUrl(newUrl)
                inputServerUrl = newUrl
                showConfigDialog = false
                restartClient()
            },
            onDismiss = {
                if (savedServerUrl.isBlank()) {
                    onCancel()
                } else {
                    showConfigDialog = false
                }
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0D1117))
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        if (syncProgress != null) {
            ProvisioningProgressDashboard(
                progress = syncProgress!!,
                onDone = onFinished,
                onCancel = onCancel,
            )
        } else {
            when (val current = state) {
                is RemotePairingClient.State.Listening -> Listening(
                    session = current.session,
                    onCancel = onCancel,
                    onChangeServer = { showConfigDialog = true }
                )

                is RemotePairingClient.State.Connecting -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF26C6DA))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Connecting to Pairing Service...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = savedServerUrl,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }

                is RemotePairingClient.State.Failed -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.widthIn(max = 500.dp)
                ) {
                    Text(
                        text = "Connection Failed",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF87171)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = current.reason,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { restartClient() }) {
                            Text("Try Again")
                        }
                        OutlinedButton(onClick = { showConfigDialog = true }) {
                            Text("Change Server URL")
                        }
                        OutlinedButton(onClick = onCancel) {
                            Text("Back")
                        }
                    }
                }

                is RemotePairingClient.State.Received -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = Color(0xFF34D399))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Configuration Received! Starting Import...",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                }

                RemotePairingClient.State.Idle -> {
                    if (savedServerUrl.isBlank()) {
                        Text("No pairing server configured.", color = Color.White)
                    } else {
                        CircularProgressIndicator(color = Color(0xFF26C6DA))
                    }
                }
            }
        }
    }
}

@Composable
private fun Listening(
    session: RemotePairingClient.Session,
    onCancel: () -> Unit,
    onChangeServer: () -> Unit,
) {
    val qr = remember(session.webPortalUrl) { QrCodes.render(session.webPortalUrl, QR_SIZE_PX) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        if (qr != null) {
            Box(
                Modifier
                    .size(290.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF26C6DA), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = "Scan with phone camera",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(Modifier.widthIn(max = 520.dp)) {
            Text(
                text = "Remote Setup",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Scan the QR code on your phone or open the web portal to enter your playlist details (M3U, short URLs, or Xtream).",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(22.dp))
            Text(
                text = "PAIRING CODE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26C6DA)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.code.toCharArray().joinToString("  "),
                fontSize = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 4.sp
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "OR VISIT IN BROWSER:",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                color = Color.White.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = session.webPortalUrl,
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF26C6DA),
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedButton(onClick = onCancel) {
                    Text("Type on TV Instead")
                }
                TextButton(onClick = onChangeServer) {
                    Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Server Settings", color = Color.White.copy(alpha = 0.7f))
                }
            }
        }
    }
}

@Composable
private fun ServerConfigDialog(
    currentUrl: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote Pairing Server") },
        text = {
            Column {
                Text(
                    "Enter the base URL of your self-hosted OpenTV pairing service (e.g. running on your Synology NAS in Container Manager or via Cloudflare Tunnel).",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://192.168.1.100:3000") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ProvisioningProgressDashboard(
    progress: RemoteProvisioningProgress,
    onDone: () -> Unit,
    onCancel: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("EEE h:mm a", Locale.getDefault()) }
    val isComplete = progress.isComplete

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Title & Description Header
        Text(
            text = if (isComplete) "Setup & Sync Complete!" else "Setting Up Your TV Experience…",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isComplete) Color(0xFF34D399) else Color.White,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (isComplete)
                "Playlists, channels, and TV guide timeline have been configured and saved."
            else
                "Importing channels, organizing categories, and generating the guide timeline.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.7f),
        )

        Spacer(Modifier.height(28.dp))

        // Progress Cards Grid
        Row(
            Modifier.fillMaxWidth().widthIn(max = 840.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Card 1: Playlists & Channels
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161B22))
                    .border(
                        width = 1.dp,
                        color = if (progress.stage == RemoteProvisioningProgress.Stage.SYNCING_CHANNELS) Color(0xFF26C6DA) else Color(0xFF30363D),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = Color(0xFF29B6F6),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "CHANNELS & PLAYLISTS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF29B6F6)
                        )
                        Spacer(Modifier.weight(1f))
                        if (progress.stage == RemoteProvisioningProgress.Stage.SYNCING_CHANNELS) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF29B6F6)
                            )
                        } else if (progress.channelsProcessed > 0) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "%,d".format(progress.channelsProcessed),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Channels Imported",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(14.dp))

                    if (progress.currentPlaylistName.isNotBlank()) {
                        Text(
                            text = "Source: ${progress.currentPlaylistName}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF26C6DA)
                        )
                    } else if (progress.totalPlaylists > 0) {
                        Text(
                            text = "${progress.totalPlaylists} playlist(s) registered",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // Card 2: TV Guide & Timeline
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF161B22))
                    .border(
                        width = 1.dp,
                        color = if (progress.stage == RemoteProvisioningProgress.Stage.SYNCING_EPG) Color(0xFF66BB6A) else Color(0xFF30363D),
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(20.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "TV GUIDE & TIMELINE",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF66BB6A)
                        )
                        Spacer(Modifier.weight(1f))
                        if (progress.stage == RemoteProvisioningProgress.Stage.SYNCING_EPG) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF66BB6A)
                            )
                        } else if (progress.epgProgrammesProcessed > 0) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Text(
                        text = "%,d".format(progress.epgProgrammesProcessed),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (progress.epgChannelsMatched > 0)
                            "${progress.epgChannelsMatched} / ${progress.epgChannelsTotal} channels matched"
                        else
                            "Programmes Scheduled",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(Modifier.height(14.dp))

                    if (progress.timelineStartMillis > 0 && progress.timelineEndMillis > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = Color(0xFFFFA726),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${dateFormat.format(Date(progress.timelineStartMillis))} → ${dateFormat.format(Date(progress.timelineEndMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFFA726),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = "Awaiting guide parsing…",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Status Message Banner
        Box(
            Modifier
                .widthIn(max = 840.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1F242C))
                .border(0.5.dp, Color(0xFF38444D), RoundedCornerShape(10.dp))
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isComplete) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFF26C6DA)
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = progress.statusMessage.ifBlank { "Processing configuration…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        // Bottom Action Bar
        if (isComplete) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onDone,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Open TV Guide",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.height(48.dp)
                ) {
                    Text(
                        text = "Return to Settings",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFF26C6DA),
                trackColor = Color(0xFF21262D)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Please keep this screen open while OpenTV downloads channels and guide.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f)
            )
        }
    }
}

private const val QR_SIZE_PX = 600
