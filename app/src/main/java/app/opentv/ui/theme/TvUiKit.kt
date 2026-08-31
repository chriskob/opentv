/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A small toolkit of ten-foot-UI building blocks that the screens share.
 *
 * Everything here is deliberately self-contained and additive: it composes with the existing
 * Material3 theme (which already carries OpenTV's living-room palette — near-black surfaces and
 * the cyan `#26C6DA` accent) and with the single shared [androidx.media3.exoplayer.ExoPlayer]
 * the app already uses, so adding these never spawns a second decoder or forces a layout rethink.
 *
 * The three pieces map directly onto the TiviMate-class requirements:
 *
 *  - [safeTvOverscan] — standard ~5% overscan margin for older panels.
 *  - [tvFocusScale] / [tvFocusGlow] / [TvPanel] — 10-foot focus feedback and translucent surfaces.
 *  - [VideoGuideShell] + [rememberTvRemote] — the EPG overlay that keeps the live video rendering
 *    underneath (scaled to a mini-preview) while the d-pad drives zap / sidebar / OSD / dismiss.
 */

/**
 * Applies the standard TV overscan safe margin: `fraction` (default 5%) of the screen on every
 * edge, so focus rings and text never sit in the zone older sets crop. Use it on a screen's
 * outermost container, never on individual cards (that would double-pad).
 */
@Composable
fun Modifier.safeTvOverscan(fraction: Float = 0.05f): Modifier {
    val configuration = LocalConfiguration.current
    val horizontal = (configuration.screenWidthDp * fraction).dp
    val vertical = (configuration.screenHeightDp * fraction).dp
    return padding(horizontal = horizontal, vertical = vertical)
}

/**
 * Scales a focused item up (default 1.06, ~6%) with a short spring-free tween. Applied with
 * [graphicsLayer] so the scale never re-runs layout or clips a sibling — it is a pure draw-time
 * transform, which is what keeps rapid d-pad focus changes at a solid frame rate on low-end boxes.
 */
@Composable
fun Modifier.tvFocusScale(focused: Boolean, scale: Float = 1.06f): Modifier {
    val target = if (focused) scale else 1f
    val current by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 120),
        label = "tvFocusScale",
    )
    return this.graphicsLayer {
        scaleX = current
        scaleY = current
    }
}

/**
 * High-contrast focus stroke. Kept as a plain [border] (not a shadow) because on Fire TV the
 * shadow path is the expensive one; a crisp stroke reads just as well from the sofa and is cheap.
 * Color defaults to the theme accent so it follows Dark/Light mode automatically.
 */
@Composable
fun Modifier.tvFocusGlow(
    focused: Boolean,
    stroke: Dp = 3.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    shape: RoundedCornerShape = RoundedCornerShape(10.dp),
): Modifier = if (focused) border(stroke, color, shape) else this

/**
 * A translucent dark panel with rounded corners — the building block for every overlay surface
 * (mini-guides, dialogs, sidebars). Translucent so the video glows through, exactly like
 * TiviMate's overlays, instead of a flat opaque slab.
 */
@Composable
fun TvPanel(
    modifier: Modifier = Modifier,
    corner: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(corner))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(24.dp),
        content = content,
    )
}

/**
 * A single source of truth for TV remote navigation.
 *
 * Installs [BackHandler] (enabled only while an overlay is visible, so it dismisses the overlay
 * first and never swallows the app-level back) and returns a [Modifier] carrying
 * [androidx.compose.ui.input.key.onPreviewKeyEvent] for the d-pad. Wire it like this:
 *
 * ```
 * val tvRemote = rememberTvRemote(
 *     onZap = { zapBy(it) },
 *     onSidebar = { railExpanded = true },
 *     onToggleOsd = { reveal() },
 *     onBack = { overlayVisible = false },
 *     overlayVisible = overlayVisible,
 * )
 * Box(Modifier.fillMaxSize().then(tvRemote)) { ... }
 * ```
 *
 * - `DPAD_UP / DPAD_DOWN` — channel zap (TiviMate quick-channel-change)
 * - `DPAD_LEFT` — call up the category/channel sidebar
 * - `DPAD_CENTER / ENTER / NUMPAD_ENTER` — reveal the OSD controls
 * - `BACK` — dismiss the overlay back to full-screen video
 */
@Composable
fun rememberTvRemote(
    onZap: (delta: Int) -> Unit,
    onSidebar: () -> Unit,
    onToggleOsd: () -> Unit,
    onBack: () -> Unit,
    overlayVisible: Boolean,
    onGuide: (() -> Unit)? = null,
): Modifier {
    BackHandler(enabled = overlayVisible, onBack = onBack)
    return Modifier.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else {
            val nativeCode = event.nativeKeyEvent.keyCode
            when {
                // Channel Up / Page Up / D-Pad Up
                event.key == Key.DirectionUp ||
                event.key == Key.ChannelUp ||
                event.key == Key.PageUp ||
                nativeCode == 166 || // KEYCODE_CHANNEL_UP
                nativeCode == 92     // KEYCODE_PAGE_UP
                -> { onZap(-1); true }

                // Channel Down / Page Down / D-Pad Down
                event.key == Key.DirectionDown ||
                event.key == Key.ChannelDown ||
                event.key == Key.PageDown ||
                nativeCode == 167 || // KEYCODE_CHANNEL_DOWN
                nativeCode == 93     // KEYCODE_PAGE_DOWN
                -> { onZap(1); true }

                // Sidebar
                event.key == Key.DirectionLeft -> { onSidebar(); true }

                // Center / OK / Info
                event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter ||
                event.key == Key.Info
                -> { onToggleOsd(); true }

                // Guide / TV
                event.key == Key.Guide ||
                nativeCode == 172 || // KEYCODE_GUIDE
                nativeCode == 170    // KEYCODE_TV
                -> {
                    if (onGuide != null) {
                        onGuide()
                        true
                    } else {
                        onSidebar()
                        true
                    }
                }

                // Never swallow Back — it must reach the BackHandler above.
                else -> false
            }
        }
    }
}

/**
 * The non-destructive guide-over-video shell.
 *
 * Architecture (single surface, continuous playback):
 *
 * ```
 * Box(fillMaxSize)
 * ├─ video layer  — always mounted, never re-initialized (same ExoPlayer as the preview)
 * │    └─ scales to a mini-preview via graphicsLayer when the guide opens
 * └─ guide layer  — translucent panel sliding in from the right (AnimatedVisibility)
 *      └─ d-pad handled by rememberTvRemote; BACK dismisses back to full video
 * ```
 *
 * Because the [PlayerView] is never removed from composition, the decoder and surface stay alive
 * while the guide is open — the audio/video pipeline is untouched, which is the whole point of
 * "overlay on top of the live stream" vs. swapping screens. [miniPreviewFraction] is the scale the
 * video shrinks to (top-left anchored, TiviMate-style pip).
 */
@Composable
fun VideoGuideShell(
    overlayVisible: Boolean,
    miniPreviewFraction: Float = 0.34f,
    guidePanelWidthFraction: Float = 0.62f,
    modifier: Modifier = Modifier,
    videoContent: @Composable BoxScope.() -> Unit,
    guideContent: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxSize()) {
        // ---- Video layer: always mounted, scales to a mini-preview when the guide opens ----
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val s = if (overlayVisible) miniPreviewFraction else 1f
                    scaleX = s
                    scaleY = s
                    // Anchor the shrink at the top-left so it becomes a tidy corner pip.
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        ) {
            videoContent()
        }

        // ---- Guide layer: translucent EPG drawer over the still-live video ----
        AnimatedVisibility(
            visible = overlayVisible,
            enter = slideInHorizontally(animationSpec = tween(220)) + fadeIn(animationSpec = tween(220)),
            exit = slideOutHorizontally(animationSpec = tween(220)) + fadeOut(animationSpec = tween(220)),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            TvPanel(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(guidePanelWidthFraction),
                corner = 18.dp,
            ) {
                guideContent()
            }
        }
    }
}

/**
 * A compact, always-readable hint bar for the remote mapping — shown at the bottom of an overlay
 * so first-time viewers know the gestures without digging through settings. Matches TiviMate's
 * footer legend style: accent key, muted description.
 */
@Composable
fun TvRemoteLegend(
    items: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        items.forEach { (keyHint, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = keyHint,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// Small spacer kept here so overlays don't have to guess the rhythm between sections.
@Composable
internal fun TvSpacer(height: Dp = 12.dp) = Spacer(Modifier.height(height).fillMaxWidth())