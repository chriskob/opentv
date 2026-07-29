/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Full-screen playback.
 *
 * The [PlayerController] is created once and tied to this composable's lifetime — not
 * recreated per channel. See that class for why that matters.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channelId: Long?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val scope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    val controller = remember { PlayerController(context, scope, graph.httpClient) }
    val state by controller.state.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            controller.release()
            scope.cancel()
        }
    }

    var variants by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var currentId by remember { mutableStateOf<Long?>(null) }

    fun tuneTo(channel: Channel) {
        currentId = channel.id
        scope.launch {
            val source = graph.sourceRepository.byId(channel.sourceId)
            controller.play(
                PlayerController.Request(
                    url = channel.streamUrl,
                    title = channel.displayName,
                    userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                    isLive = true,
                ),
                // A deliberate selection, not surfing — tune immediately.
                debounce = false,
            )
        }
    }

    LaunchedEffect(channelId) {
        val id = channelId ?: return@LaunchedEffect
        val channel = graph.catalogRepository.channel(id) ?: return@LaunchedEffect
        // Every quality of this logical channel, best first. One row in the list,
        // switchable here — quality is a playback decision, not four channels.
        variants = graph.catalogRepository.variants(channel)
        tuneTo(variants.firstOrNull { it.id == channel.id } ?: channel)
    }

    BackHandler {
        controller.stop()
        onBack()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = controller.player
                    useController = true
                    // Subtitles on by default when the stream carries them. Turning captions
                    // into a setting people have to find is a needless accessibility failure.
                    subtitleView?.setUserDefaultStyle()
                    subtitleView?.setUserDefaultTextSize()
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
            },
        )

        when (val current = state) {
            is PlayerController.State.Buffering -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(current.title, color = Color.White)
                    }
                }
            }

            is PlayerController.State.Error -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(48.dp),
                    ) {
                        Text(
                            current.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            current.message,
                            color = Color.White.copy(alpha = 0.85f),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { controller.retry() }) { Text("Try again") }
                    }
                }
            }

            else -> Unit
        }

        // Quality switch, top-right, only when this channel actually has variants.
        if (variants.size > 1) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                variants.forEach { variant ->
                    val selected = variant.id == currentId
                    TextButton(onClick = { if (!selected) tuneTo(variant) }) {
                        Text(
                            variant.qualityLabel.ifEmpty { "Standard" },
                            color = if (selected) MaterialTheme.colorScheme.primary
                            else Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
