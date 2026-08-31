/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.opentv.ui.theme.TvOverscanDefaults

/**
 * Supported viewport rendering modes for the unified non-destructive player.
 */
enum class TvViewportMode {
    /** Full-screen 100vw x 100vh playback with zero margins or corner rounding. */
    FULLSCREEN,

    /** Scaled 16:9 mini-player anchored to the top-right overscan boundary (standard TiviMate EPG). */
    MINI_PREVIEW_TOP_RIGHT,

    /** Scaled 16:9 mini-player anchored to the top-left overscan boundary. */
    MINI_PREVIEW_TOP_LEFT,

    /** Scaled 16:9 mini-player anchored to the bottom-right overscan boundary. */
    MINI_PREVIEW_BOTTOM_RIGHT,

    /** Invisible (video surface scaled to 0 or hidden when playing pure audio or backgrounded). */
    HIDDEN,
}

/**
 * Geometric layout specification for a specific [TvViewportMode].
 */
data class ViewportGeometry(
    val width: Dp,
    val height: Dp,
    val alignment: Alignment,
    val horizontalMargin: Dp,
    val verticalMargin: Dp,
    val cornerRadius: Dp,
    val elevation: Dp,
    val borderWidth: Dp,
)

/**
 * State holder that coordinates the non-destructive scaling and positioning of the video viewport.
 */
@Stable
class TvMiniPlayerState(
    initialMode: TvViewportMode = TvViewportMode.FULLSCREEN,
    initialMuted: Boolean = false,
) {
    /** The active viewport layout mode. */
    var mode: TvViewportMode by mutableStateOf(initialMode)
        private set

    /** Whether audio is muted when playing in mini-preview mode. */
    var isMuted: Boolean by mutableStateOf(initialMuted)
        private set

    /** Whether the mini-player itself currently holds D-pad focus. */
    var isFocused: Boolean by mutableStateOf(false)

    /** Returns true if currently rendering in full-screen mode. */
    val isFullscreen: Boolean
        get() = mode == TvViewportMode.FULLSCREEN

    /** Returns true if currently rendering in any mini-preview mode. */
    val isMiniPlayer: Boolean
        get() = mode == TvViewportMode.MINI_PREVIEW_TOP_RIGHT ||
                mode == TvViewportMode.MINI_PREVIEW_TOP_LEFT ||
                mode == TvViewportMode.MINI_PREVIEW_BOTTOM_RIGHT

    /** Transition the viewport to a new mode. */
    fun setViewportMode(newMode: TvViewportMode) {
        if (mode != newMode) {
            mode = newMode
        }
    }

    /** Expand mini-preview to full-screen. */
    fun expandToFullscreen() {
        setViewportMode(TvViewportMode.FULLSCREEN)
    }

    /** Shrink full-screen to top-right mini-preview. */
    fun shrinkToMiniPreview() {
        setViewportMode(TvViewportMode.MINI_PREVIEW_TOP_RIGHT)
    }

    /** Toggle between full-screen and top-right mini-preview. */
    fun toggleFullscreen() {
        if (isFullscreen) {
            shrinkToMiniPreview()
        } else {
            expandToFullscreen()
        }
    }

    /** Update audio muting state. */
    fun setMuteState(muted: Boolean) {
        isMuted = muted
    }

    /** Compute target geometric properties for the current mode. */
    fun resolveGeometry(
        screenWidthDp: Dp,
        screenHeightDp: Dp,
        customMiniWidth: Dp = TvOverscanDefaults.MiniPlayerWidth,
        customMiniHeight: Dp = TvOverscanDefaults.MiniPlayerHeight,
    ): ViewportGeometry {
        return when (mode) {
            TvViewportMode.FULLSCREEN -> ViewportGeometry(
                width = screenWidthDp,
                height = screenHeightDp,
                alignment = Alignment.Center,
                horizontalMargin = 0.dp,
                verticalMargin = 0.dp,
                cornerRadius = 0.dp,
                elevation = 0.dp,
                borderWidth = 0.dp,
            )
            TvViewportMode.MINI_PREVIEW_TOP_RIGHT -> ViewportGeometry(
                width = customMiniWidth,
                height = customMiniHeight,
                alignment = Alignment.TopEnd,
                horizontalMargin = TvOverscanDefaults.HorizontalMargin,
                verticalMargin = TvOverscanDefaults.VerticalMargin,
                cornerRadius = TvOverscanDefaults.MiniPlayerCornerRadius,
                elevation = 12.dp,
                borderWidth = if (isFocused) 2.5.dp else 1.dp,
            )
            TvViewportMode.MINI_PREVIEW_TOP_LEFT -> ViewportGeometry(
                width = customMiniWidth,
                height = customMiniHeight,
                alignment = Alignment.TopStart,
                horizontalMargin = TvOverscanDefaults.HorizontalMargin,
                verticalMargin = TvOverscanDefaults.VerticalMargin,
                cornerRadius = TvOverscanDefaults.MiniPlayerCornerRadius,
                elevation = 12.dp,
                borderWidth = if (isFocused) 2.5.dp else 1.dp,
            )
            TvViewportMode.MINI_PREVIEW_BOTTOM_RIGHT -> ViewportGeometry(
                width = customMiniWidth,
                height = customMiniHeight,
                alignment = Alignment.BottomEnd,
                horizontalMargin = TvOverscanDefaults.HorizontalMargin,
                verticalMargin = TvOverscanDefaults.VerticalMargin,
                cornerRadius = TvOverscanDefaults.MiniPlayerCornerRadius,
                elevation = 12.dp,
                borderWidth = if (isFocused) 2.5.dp else 1.dp,
            )
            TvViewportMode.HIDDEN -> ViewportGeometry(
                width = 0.dp,
                height = 0.dp,
                alignment = Alignment.Center,
                horizontalMargin = 0.dp,
                verticalMargin = 0.dp,
                cornerRadius = 0.dp,
                elevation = 0.dp,
                borderWidth = 0.dp,
            )
        }
    }
}

/**
 * Creates and remembers a [TvMiniPlayerState] instance across recompositions.
 */
@Composable
fun rememberTvMiniPlayerState(
    initialMode: TvViewportMode = TvViewportMode.FULLSCREEN,
    initialMuted: Boolean = false,
): TvMiniPlayerState = remember {
    TvMiniPlayerState(
        initialMode = initialMode,
        initialMuted = initialMuted,
    )
}
