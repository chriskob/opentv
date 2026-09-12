/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.opentv.ui.components.TvOutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import androidx.compose.material.icons.filled.Movie
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
            viewModel.saveAndSyncBatch(it.sources, it.deletedSourceIds)
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
                TvOutlinedTextField(
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

    // Atmosphere for free: one vertical gradient behind everything. A Brush is a single draw pass —
    // no blur, no shadow, no image, nothing that costs frames on a box that runs interpreted.
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(DashBgTop, DashBgBottom)))
    ) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Eyebrow, then state: the title carries what is happening, the line under it explains, and
        // nothing repeats itself.
        Text(
            text = "OPENTV  ·  REMOTE SETUP",
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 3.sp),
            fontWeight = FontWeight.Bold,
            color = DashMuted.copy(alpha = 0.75f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (isComplete) "You're all set" else "Setting up your TV",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = DashInk,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (isComplete)
                "Playlists, channels and the TV guide have been saved."
            else
                "Importing channels, organizing categories, and building the guide.",
            style = MaterialTheme.typography.bodyMedium,
            color = DashMuted,
        )

        Spacer(Modifier.height(34.dp))

        // Progress Cards Grid
        Row(
            // The row takes the tallest card's height and every card fills it, so all three match
            // exactly — whatever each one's contents are, and without a hardcoded height that could
            // clip on a smaller screen.
            Modifier.fillMaxWidth().widthIn(max = 1180.dp).height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Card 1: Playlists & Channels
            CardShell(
                accent = AccentChannels,
                active = progress.stage == RemoteProvisioningProgress.Stage.SYNCING_CHANNELS,
                modifier = Modifier.weight(1f),
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Dns,
                            contentDescription = null,
                            tint = AccentChannels,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "CHANNELS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                            fontWeight = FontWeight.Bold,
                            color = AccentChannels
                        )
                        Spacer(Modifier.weight(1f))
                        ProgressRing(
                            fraction = if (progress.channelsSkipped) null else progress.channelsFraction,
                            accent = AccentChannels,
                            label = ringLabel(progress.channelsFraction, progress.channelsSkipped),
                            muted = progress.channelsSkipped,
                            modifier = Modifier.size(52.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    StatValue(
                        value = if (progress.channelsSkipped) "Skipped" else "%,d".format(progress.channelsProcessed),
                        label = if (progress.channelsSkipped) "Channels — unchecked" else "Channels imported",
                        dimmed = progress.channelsSkipped,
                    )
                    if (!progress.channelsSkipped && progress.channelsTotal > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "%,d of %,d".format(progress.channelsProcessed, progress.channelsTotal),
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Per-playlist attribution. This used to read "Source: <playlist processed last>"
                    // beside the running total, which credited one playlist's channels to another — a
                    // playlist whose Channels box was unticked appeared to have imported them anyway.
                    if (progress.playlistSummaries.isNotEmpty()) {
                        Text(
                            text = progress.playlistSummaries.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF26C6DA)
                        )
                    } else if (progress.totalPlaylists > 0) {
                        Text(
                            text = "${progress.totalPlaylists} playlist(s) registered",
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    }
            }

            // Card 2: Movies & Shows
            CardShell(
                accent = AccentVod,
                active = progress.stage == RemoteProvisioningProgress.Stage.SYNCING_VOD,
                modifier = Modifier.weight(1f),
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = AccentVod,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "MOVIES & SHOWS",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                            fontWeight = FontWeight.Bold,
                            color = AccentVod
                        )
                        Spacer(Modifier.weight(1f))
                        ProgressRing(
                            fraction = progress.vodFraction,
                            accent = AccentVod,
                            label = ringLabel(progress.vodFraction, progress.moviesSkipped && progress.showsSkipped),
                            muted = progress.moviesSkipped && progress.showsSkipped,
                            modifier = Modifier.size(52.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            StatValue(
                                value = if (progress.moviesSkipped) "Skipped" else "%,d".format(progress.moviesProcessed),
                                label = if (progress.moviesSkipped) "Movies — unchecked" else "Movies",
                                dimmed = progress.moviesSkipped,
                            )
                        }
                        Column(Modifier.weight(1f)) {
                            StatValue(
                                value = if (progress.showsSkipped) "Skipped" else "%,d".format(progress.seriesProcessed),
                                label = if (progress.showsSkipped) "Shows — unchecked" else "Shows",
                                accent = AccentVod,
                                dimmed = progress.showsSkipped,
                            )
                        }
                    }

                    val vodFraction = progress.vodFraction
                    if (vodFraction != null && !progress.moviesSkipped) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "%,d of %,d titles".format(
                                progress.moviesProcessed + progress.seriesProcessed,
                                progress.moviesTotal + progress.seriesTotal,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    } else if (progress.moviesSkipped && progress.showsSkipped) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Nothing to import",
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    }
            }

            // Card 3: TV Guide & Timeline. Matching channels against the feeds is a percentage of
            // the channels the guide covers, so it gets a real ring too.
            CardShell(
                accent = AccentGuide,
                active = progress.stage == RemoteProvisioningProgress.Stage.SYNCING_EPG,
                modifier = Modifier.weight(1f),
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.LiveTv,
                            contentDescription = null,
                            tint = AccentGuide,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "TV GUIDE",
                            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.6.sp),
                            fontWeight = FontWeight.Bold,
                            color = AccentGuide
                        )
                        Spacer(Modifier.weight(1f))
                        ProgressRing(
                            fraction = progress.epgFraction,
                            accent = AccentGuide,
                            label = ringLabel(progress.epgFraction, false),
                            modifier = Modifier.size(52.dp),
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    StatValue(
                        value = "%,d".format(progress.epgProgrammesProcessed),
                        label = "Programs scheduled",
                    )
                    Text(
                        text = when {
                            progress.epgBarIsMatching ->
                                "Matching channels — %,d of %,d checked".format(
                                    progress.epgChannelsScanned,
                                    progress.epgChannelsToScan,
                                )
                            progress.epgChannelsMatched > 0 ->
                                "${progress.epgChannelsMatched} / ${progress.epgChannelsTotal} channels matched"
                            // "Up to date" is only true once every feed has been through. Said while
                            // one feed of two was still coming it read as "nothing to do" next to a
                            // ring sitting at 50%, and never named the fact that actually explained
                            // the ring: how many feeds had finished.
                            progress.epgFeedsTotal > 0 && progress.epgFeedsDone < progress.epgFeedsTotal ->
                                "Fetching feeds — %,d of %,d done".format(
                                    progress.epgFeedsDone,
                                    progress.epgFeedsTotal,
                                )
                            progress.epgFeedsTotal > 0 && progress.epgProgrammesProcessed == 0 ->
                                "Guide is up to date"
                            progress.epgFeedsTotal > 0 ->
                                "Feed ${progress.epgFeedsDone} of ${progress.epgFeedsTotal}"
                            else -> "Waiting for feeds"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = DashMuted
                    )

                    Spacer(Modifier.height(10.dp))

                    if (progress.timelineStartMillis > 0 && progress.timelineEndMillis > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Schedule,
                                contentDescription = null,
                                tint = AccentGuide,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "${dateFormat.format(Date(progress.timelineStartMillis))} → ${dateFormat.format(Date(progress.timelineEndMillis))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = DashMuted
                            )
                        }
                    } else if (progress.epgProgrammesProcessed > 0) {
                        // Only when it adds information: with zero programs arriving the big number
                        // above already says zero, so this line used to be the same figure twice.
                        Text(
                            text = "%,d programs so far".format(progress.epgProgrammesProcessed),
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    } else if (progress.epgFeedsTotal == 0) {
                        Text(
                            text = "Fetching the guide…",
                            style = MaterialTheme.typography.bodySmall,
                            color = DashMuted
                        )
                    }
            }
        }

        Spacer(Modifier.height(24.dp))

        // Status: a quiet pill with a state dot. The dot is deliberately not animated — an infinite
        // transition redraws every frame, and this screen runs while a big import is already loading
        // the box.
        Box(
            Modifier
                .widthIn(max = 1180.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DashSurface.copy(alpha = 0.8f))
                .border(1.dp, DashHairline, RoundedCornerShape(14.dp))
                .padding(horizontal = 20.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(9.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (isComplete) AccentGuide else AccentChannels)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = progress.statusMessage.ifBlank { "Working…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = DashInk.copy(alpha = 0.92f)
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
            // No indeterminate spinner here: it is an animation that never settles, redrawn every
            // frame for as long as the sync runs. The dot above already says work is in progress.
            Text(
                text = "Keep this screen open while OpenTV downloads channels and the guide.",
                style = MaterialTheme.typography.bodySmall,
                color = DashMuted
            )
        }
    }
    }
}

// ---------------------------------------------------------------------------------------------
// Dashboard dressing
// ---------------------------------------------------------------------------------------------

/**
 * The dashboard's palette: three section accents over a near-black gradient.
 *
 * Every value is a flat colour on purpose — no elevation, no blur, no shadow anywhere on this screen.
 * It renders on boxes that execute bytecode interpreted (the ANR trace that led here was all
 * `art::interpreter` frames doing text layout), where one shadow costs more than the rest of the
 * screen combined.
 */
private val DashBgTop = Color(0xFF0A0E14)
private val DashBgBottom = Color(0xFF121A24)
private val DashSurface = Color(0xFF141A22)
private val DashHairline = Color(0xFF232C38)
private val DashInk = Color(0xFFF4F7FA)
private val DashMuted = Color(0xFF9BA8B6)
private val AccentChannels = Color(0xFF22D3EE)
private val AccentVod = Color(0xFFC084FC)
private val AccentGuide = Color(0xFF34D399)

/** "42%", or a dash when there is no figure to show (skipped, or the provider's list is pending). */
private fun ringLabel(fraction: Float?, skipped: Boolean): String = when {
    skipped -> "—"
    fraction == null -> "…"
    else -> "%.0f%%".format(fraction * 100f)
}

/**
 * A card surface: flat fill, hairline outline, and a thin rule in the section's accent along the top
 * edge. The rule is the only decoration — it reads as deliberate design and costs one rectangle,
 * which is the trade this screen wants: shape and colour instead of shadows.
 */
@Composable
private fun CardShell(
    accent: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(20.dp))
            .background(DashSurface)
            .border(
                width = 1.dp,
                color = if (active) accent.copy(alpha = 0.5f) else DashHairline,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(accent.copy(alpha = if (active) 0.95f else 0.45f))
        )
        Column(
            Modifier.padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 22.dp),
            content = content,
        )
    }
}

/** A big numeral over a quiet label — the dashboard's unit of information. */
@Composable
private fun StatValue(
    value: String,
    label: String,
    accent: Color = DashInk,
    dimmed: Boolean = false,
) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = if (dimmed) DashMuted.copy(alpha = 0.45f) else accent,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = DashMuted,
        )
    }
}

/**
 * A determinate progress ring, drawn by hand.
 *
 * Canvas rather than a Material progress indicator: one draw pass, no animation loop, and it redraws
 * only when the fraction changes. A Material indicator animates towards its target, which on a screen
 * whose numbers move every few hundred channels means a recomposition every frame.
 */
@Composable
private fun ProgressRing(
    fraction: Float?,
    accent: Color,
    label: String,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = 5.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = DashHairline,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            if (fraction != null && fraction > 0f) {
                drawArc(
                    color = if (muted) accent.copy(alpha = 0.35f) else accent,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round),
                )
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (muted) DashMuted.copy(alpha = 0.6f) else DashInk,
        )
    }
}

private const val QR_SIZE_PX = 600
