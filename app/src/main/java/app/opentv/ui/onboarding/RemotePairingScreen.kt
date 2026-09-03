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

import androidx.compose.ui.platform.LocalContext
import app.opentv.core.AppSettings
import app.opentv.pairing.ProvisionedSource

/**
 * Remote pairing screen connecting to a self-hosted provisioning server (e.g. on Synology NAS).
 *
 * Displays:
 * 1. 6-character ephemeral pairing code.
 * 2. QR code directing the phone/browser to the admin portal.
 * 3. Immediate real-time WebSocket receipt of M3U / Xtream credentials.
 */
@Composable
fun RemotePairingScreen(
    onReceived: (List<ProvisionedSource>) -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val savedServerUrl by settings.remotePairingServerUrl.collectAsState()
    val currentSources by graph.sourceRepository.observeAll().collectAsState(initial = emptyList())

    var showConfigDialog by remember { mutableStateOf(false) }
    var inputServerUrl by remember { mutableStateOf(savedServerUrl.ifBlank { AppSettings.DEFAULT_REMOTE_PAIRING_URL }) }

    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val client = remember { RemotePairingClient(scope) }
    val state by client.state.collectAsState()

    fun restartClient() {
        client.stop()
        if (savedServerUrl.isNotBlank()) {
            client.start(savedServerUrl, currentSources)
        }
    }

    DisposableEffect(savedServerUrl, currentSources) {
        if (savedServerUrl.isNotBlank()) {
            client.start(savedServerUrl, currentSources)
        }
        onDispose {
            client.stop()
            scope.cancel()
        }
    }

    LaunchedEffect(state) {
        (state as? RemotePairingClient.State.Received)?.let { onReceived(it.sources) }
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
                    text = "Configuration Received! Loading...",
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
                text = "Scan the QR code on your phone or open the web portal to enter your M3U or Xtream playlist details.",
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

private const val QR_SIZE_PX = 600
