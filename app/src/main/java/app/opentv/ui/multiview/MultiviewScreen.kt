/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.multiview

import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.R
import app.opentv.core.PipState
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Channel
import app.opentv.data.model.shownName
import app.opentv.player.PlaybackQueue
import app.opentv.player.PlayerController
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Minimal 2-up split-screen multiview.
 *
 * Two short-lived [PlayerController]s — never the shared `graph.livePlayer`, which must stay
 * single-owner for the guide/fullscreen handoff. Both panes are stopped + released on dispose
 * so no decoder or phantom audio survives Back.
 *
 * Only the focused pane is audible; d-pad Left/Right moves focus, Up/Down zaps the focused
 * pane through the [PlaybackQueue], OK opens the picker for the focused pane.
 */
@Composable
fun MultiviewScreen(
    channelAId: Long?,
    channelBId: Long?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { graph.settings }
    val scope = rememberCoroutineScope()

    // Multiview owns its decoders; PiP must not trigger while two streams are up.
    DisposableEffect(Unit) {
        val prevEligible = PipState.eligible
        PipState.eligible = false
        onDispose { PipState.eligible = prevEligible }
    }

    val controllerA = remember {
        PlayerController(
            context = context.applicationContext,
            scope = scope,
            httpClient = graph.streamingHttpClient,
        )
    }
    val controllerB = remember {
        PlayerController(
            context = context.applicationContext,
            scope = scope,
            httpClient = graph.streamingHttpClient,
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { controllerA.stop() }
            runCatching { controllerB.stop() }
            runCatching { controllerA.release() }
            runCatching { controllerB.release() }
        }
    }

    var channelA by remember { mutableStateOf<Channel?>(null) }
    var channelB by remember { mutableStateOf<Channel?>(null) }
    var focusedPane by remember { mutableIntStateOf(0) }
    var pickerForPane by remember { mutableStateOf<Int?>(null) }
    var swapFlash by remember { mutableIntStateOf(0) }

    val stateA by controllerA.state.collectAsState()
    val stateB by controllerB.state.collectAsState()

    fun tune(controller: PlayerController, channel: Channel, onResolved: (Channel) -> Unit) {
        scope.launch {
            val (source, url) = withContext(Dispatchers.IO) {
                val src = graph.sourceRepository.byId(channel.sourceId)
                val resolved = graph.catalogRepository.resolvePlaybackUrl(channel, src)
                src to resolved
            }
            onResolved(channel)
            settings.recordChannelWatched(channel.sourceId, channel.streamId)
            controller.play(
                PlayerController.Request(
                    url = url,
                    title = channel.shownName,
                    userAgent = source?.userAgent ?: "OpenTV/0.1 (Android)",
                    isLive = true,
                ),
                debounce = false,
            )
        }
    }

    suspend fun resolveChannel(id: Long): Channel? = withContext(Dispatchers.IO) {
        graph.catalogRepository.channel(id)
    }

    // Initial tune: A = current channel, B = explicit id, else previous, else first recent/queue.
    LaunchedEffect(channelAId, channelBId) {
        val a = channelAId?.let { resolveChannel(it) }
        if (a != null) {
            channelA = a
            tune(controllerA, a) { channelA = it }
        }
        var b: Channel? = channelBId?.takeIf { it > 0L }?.let { resolveChannel(it) }
        if (b == null) {
            val fallbackId = PlaybackQueue.previousChannelId.takeIf { it > 0L && it != a?.id }
                ?: settings.recentChannelRefs.value.firstOrNull { it.sourceId != a?.sourceId || it.streamId != a?.streamId }
                    ?.let { ref ->
                        withContext(Dispatchers.IO) {
                            graph.catalogRepository.channelsForRefs(listOf(ref)).firstOrNull()
                        }
                    }?.id
                ?: PlaybackQueue.items.firstOrNull { it.id != a?.id }?.id
            b = fallbackId?.let { resolveChannel(it) }
        }
        if (b != null) {
            channelB = b
            tune(controllerB, b) { channelB = it }
        } else {
            // Nothing sensible for pane B — open the picker immediately.
            pickerForPane = 1
        }
    }

    // Exactly one audible pane: the focused one.
    LaunchedEffect(focusedPane) {
        controllerA.player.volume = if (focusedPane == 0) 1f else 0f
        controllerB.player.volume = if (focusedPane == 1) 1f else 0f
    }

    fun zapFocused(direction: Int) {
        val items = PlaybackQueue.items
        if (items.isEmpty()) return
        val currentId = if (focusedPane == 0) channelA?.id else channelB?.id
        val idx = items.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
        val next = items[(idx + direction + items.size) % items.size]
        scope.launch {
            val ch = resolveChannel(next.id) ?: return@launch
            if (focusedPane == 0) tune(controllerA, ch) { channelA = it }
            else tune(controllerB, ch) { channelB = it }
        }
    }

    fun swapPanes() {
        val a = channelA
        val b = channelB
        if (a == null || b == null) return
        channelA = b
        channelB = a
        tune(controllerA, b) { channelA = it }
        tune(controllerB, a) { channelB = it }
        swapFlash++
    }

    BackHandler {
        if (pickerForPane != null) pickerForPane = null
        else onBack()
    }

    val focusA = remember { FocusRequester() }
    val focusB = remember { FocusRequester() }
    LaunchedEffect(focusedPane, swapFlash) {
        runCatching { if (focusedPane == 0) focusA.requestFocus() else focusB.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionLeft -> {
                        if (focusedPane == 1) {
                            focusedPane = 0
                            runCatching { focusA.requestFocus() }
                            true
                        } else false
                    }
                    Key.DirectionRight -> {
                        if (focusedPane == 0) {
                            focusedPane = 1
                            runCatching { focusB.requestFocus() }
                            true
                        } else false
                    }
                    Key.DirectionUp -> { zapFocused(1); true }
                    Key.DirectionDown -> { zapFocused(-1); true }
                    Key.Enter, Key.NumPadEnter -> {
                        pickerForPane = focusedPane
                        true
                    }
                    Key.MediaPlayPause -> {
                        val p = if (focusedPane == 0) controllerA.player else controllerB.player
                        p.playWhenReady = !p.playWhenReady
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MultiviewPane(
                modifier = Modifier.weight(1f),
                label = channelA?.shownName ?: stringResource(R.string.multiview_pane_a),
                focused = focusedPane == 0,
                state = stateA,
                controller = controllerA,
                focusRequester = focusA,
                onFocus = { focusedPane = 0 },
                onClick = {
                    focusedPane = 0
                    pickerForPane = 0
                },
            )
            MultiviewPane(
                modifier = Modifier.weight(1f),
                label = channelB?.shownName ?: stringResource(R.string.multiview_pane_b),
                focused = focusedPane == 1,
                state = stateB,
                controller = controllerB,
                focusRequester = focusB,
                onFocus = { focusedPane = 1 },
                onClick = {
                    focusedPane = 1
                    pickerForPane = 1
                },
            )
        }

        // Bottom hint bar.
        Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.Black.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                stringResource(R.string.multiview_hint),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    if (pickerForPane != null) {
        MultiviewChannelPicker(
            title = if (pickerForPane == 0) stringResource(R.string.multiview_pick_a)
            else stringResource(R.string.multiview_pick_b),
            excludeId = if (pickerForPane == 0) channelB?.id else channelA?.id,
            onSwap = {
                pickerForPane = null
                swapPanes()
            },
            onPick = { item ->
                val pane = pickerForPane
                pickerForPane = null
                scope.launch {
                    val ch = resolveChannel(item.id)
                    if (ch == null) {
                        Toast.makeText(context, context.getString(R.string.multiview_unavailable), Toast.LENGTH_SHORT).show()
                        return@launch
                    }
                    if (pane == 0) tune(controllerA, ch) { channelA = it }
                    else tune(controllerB, ch) { channelB = it }
                }
            },
            onDismiss = { pickerForPane = null },
        )
    }
}

@Composable
private fun MultiviewPane(
    modifier: Modifier,
    label: String,
    focused: Boolean,
    state: PlayerController.State,
    controller: PlayerController,
    focusRequester: FocusRequester,
    onFocus: () -> Unit,
    onClick: () -> Unit,
) {
    val borderColor = if (focused) AppTheme.primary else Color.White.copy(alpha = 0.18f)
    Box(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.DarkGray.copy(alpha = 0.4f))
            .border(
                width = if (focused) 3.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .focusRequester(focusRequester)
            .onFocusChanged { if (it.isFocused) onFocus() }
            .focusable()
            .clickable { onClick() },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                (android.view.LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    player = controller.player
                }
            },
            update = { pv ->
                if (pv.player != controller.player) pv.player = controller.player
            },
            onRelease = { pv -> pv.player = null },
        )

        when (state) {
            is PlayerController.State.Buffering -> {
                if (controller.player.playbackState != Player.STATE_READY) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(state.title, color = Color.White, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            is PlayerController.State.Error -> {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.message.ifEmpty { LocalContext.current.getString(R.string.multiview_unavailable) },
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = { controller.retry() }) {
                            Text(stringResource(R.string.multiview_retry), color = AppTheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            else -> Unit
        }

        // Channel label chip.
        Box(
            Modifier.align(Alignment.TopStart).padding(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(label, color = Color.White, style = MaterialTheme.typography.bodySmall, fontWeight = if (focused) FontWeight.Bold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun MultiviewChannelPicker(
    title: String,
    excludeId: Long?,
    onSwap: () -> Unit,
    onPick: (PlaybackQueue.Item) -> Unit,
    onDismiss: () -> Unit,
) {
    val items = remember { PlaybackQueue.items.filter { it.id != excludeId }.take(200) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            if (items.isEmpty()) {
                Text(
                    stringResource(R.string.multiview_empty),
                    color = Color.White.copy(alpha = 0.85f),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    items(items, key = { it.id }) { item ->
                        val num = item.number?.let { "$it · " } ?: ""
                        Text(
                            "$num${item.name}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onPick(item) }
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onSwap) {
                    Text(stringResource(R.string.multiview_swap), color = AppTheme.primary)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_done), color = AppTheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
