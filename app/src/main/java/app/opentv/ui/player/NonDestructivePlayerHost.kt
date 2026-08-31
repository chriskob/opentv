/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import android.view.LayoutInflater
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.R

private const val TRANSITION_DURATION_MS = 280

/**
 * Root host that renders the continuous, non-destructive video playback viewport.
 *
 * Traditional TV applications destroy or detach the video surface when navigating between
 * full-screen playback, the EPG channel guide, and menus, causing video black-outs, audio stutter,
 * and high codec churn on budget TV hardware.
 *
 * [NonDestructivePlayerHost] retains a single [PlayerView] surface in the composition and animates
 * its position, dimensions, corner radius, and elevation in place.
 */
@OptIn(UnstableApi::class)
@Composable
fun NonDestructivePlayerHost(
    exoPlayer: ExoPlayer?,
    state: TvMiniPlayerState,
    modifier: Modifier = Modifier,
    customMiniWidth: Dp? = null,
    customMiniHeight: Dp? = null,
    onMiniPlayerClick: (() -> Unit)? = { state.expandToFullscreen() },
    overlayContent: @Composable BoxScope.() -> Unit = {},
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.dp
    val screenHeight = configuration.screenHeightDp.dp

    // Resolve target geometry based on current viewport mode
    val targetGeometry = state.resolveGeometry(
        screenWidthDp = screenWidth,
        screenHeightDp = screenHeight,
        customMiniWidth = customMiniWidth ?: (screenWidth * 0.28f).coerceAtLeast(360.dp),
        customMiniHeight = customMiniHeight ?: (screenHeight * 0.28f).coerceAtLeast(202.dp),
    )

    // Smooth layout transitions
    val animatedWidth by animateDpAsState(
        targetValue = targetGeometry.width,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerWidth",
    )
    val animatedHeight by animateDpAsState(
        targetValue = targetGeometry.height,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerHeight",
    )
    val animatedHMargin by animateDpAsState(
        targetValue = targetGeometry.horizontalMargin,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerHMargin",
    )
    val animatedVMargin by animateDpAsState(
        targetValue = targetGeometry.verticalMargin,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerVMargin",
    )
    val animatedCornerRadius by animateDpAsState(
        targetValue = targetGeometry.cornerRadius,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerCornerRadius",
    )
    val animatedElevation by animateDpAsState(
        targetValue = targetGeometry.elevation,
        animationSpec = tween(TRANSITION_DURATION_MS, easing = FastOutSlowInEasing),
        label = "PlayerElevation",
    )

    // Sync volume with mini-preview mute state
    LaunchedEffect(exoPlayer, state.isMuted) {
        exoPlayer?.let { player ->
            player.volume = if (state.isMuted) 0f else 1f
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    LaunchedEffect(isFocused) {
        state.isFocused = isFocused
    }

    val shape = RoundedCornerShape(animatedCornerRadius)
    val focusBorderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color(0x33FFFFFF)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // LAYER 0: Persistent Video Viewport Container
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = targetGeometry.alignment,
        ) {
            Box(
                modifier = Modifier
                    .padding(
                        start = if (targetGeometry.alignment == Alignment.TopStart || targetGeometry.alignment == Alignment.BottomStart) animatedHMargin else 0.dp,
                        end = if (targetGeometry.alignment == Alignment.TopEnd || targetGeometry.alignment == Alignment.BottomEnd) animatedHMargin else 0.dp,
                        top = if (targetGeometry.alignment == Alignment.TopStart || targetGeometry.alignment == Alignment.TopEnd) animatedVMargin else 0.dp,
                        bottom = if (targetGeometry.alignment == Alignment.BottomStart || targetGeometry.alignment == Alignment.BottomEnd) animatedVMargin else 0.dp,
                    )
                    .size(width = animatedWidth, height = animatedHeight)
                    .shadow(elevation = animatedElevation, shape = shape, spotColor = focusBorderColor)
                    .clip(shape)
                    .background(Color.Black)
                    .border(
                        width = if (state.isMiniPlayer) targetGeometry.borderWidth else 0.dp,
                        color = if (state.isMiniPlayer) focusBorderColor else Color.Transparent,
                        shape = shape,
                    )
                    .then(
                        if (state.isMiniPlayer && onMiniPlayerClick != null) {
                            Modifier
                                .focusable(interactionSource = interactionSource)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = null,
                                    onClick = onMiniPlayerClick,
                                )
                        } else {
                            Modifier
                        },
                    ),
            ) {
                if (exoPlayer != null) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            (LayoutInflater.from(ctx).inflate(R.layout.view_player, null) as PlayerView).apply {
                                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                useController = false
                                player = exoPlayer
                            }
                        },
                        update = { view ->
                            if (view.player != exoPlayer) {
                                view.player = exoPlayer
                            }
                            view.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        },
                    )
                }
            }
        }

        // LAYER 1: Ambient Background Scrim for Overlays
        AnimatedVisibility(
            visible = state.isMiniPlayer,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize().zIndex(1f),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x990A0D14),
                                Color(0xF206080D),
                            ),
                        ),
                    ),
            )
        }

        // LAYER 2: Overlay Content (EPG, Channel List, Sidebars, HUD)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(2f),
        ) {
            overlayContent()
        }
    }
}
