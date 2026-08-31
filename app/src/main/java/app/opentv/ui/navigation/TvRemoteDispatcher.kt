/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.navigation

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Unified high-level TV Remote Actions triggered across 10-foot TV interfaces.
 */
sealed interface TvRemoteAction {
    data class Zap(val delta: Int) : TvRemoteAction // -1 for Up/Prev, +1 for Down/Next
    data object OpenSidebar : TvRemoteAction
    data object OpenGuide : TvRemoteAction
    data object ToggleOsd : TvRemoteAction
    data object ToggleInfo : TvRemoteAction
    data object QuickRecentChannel : TvRemoteAction
    data class NumberDigit(val digit: Char) : TvRemoteAction
    data object PlayPause : TvRemoteAction
    data object FastForward : TvRemoteAction
    data object Rewind : TvRemoteAction
    data object Dismiss : TvRemoteAction
}

/**
 * TV Remote key parser and dispatcher for Google TV, Android TV, Fire TV, and dedicated
 * streaming box remotes (such as the Walmart ONN 4K remote, Google G10/G20 reference remotes,
 * and Smart TV remotes with dedicated channel rockers).
 */
object TvRemoteKeyMapper {

    /**
     * Translates a Compose [androidx.compose.ui.input.key.KeyEvent] to a [TvRemoteAction] when in
     * Full-screen playback mode.
     */
    fun mapFullscreenKey(event: androidx.compose.ui.input.key.KeyEvent): TvRemoteAction? {
        if (event.type != KeyEventType.KeyDown) return null

        val nativeCode = event.nativeKeyEvent.keyCode
        val key = event.key

        // 1. Dedicated Channel Up / Down Keys (e.g. Walmart ONN 4K, Smart TV Remotes)
        if (isChannelUpKey(key, nativeCode)) return TvRemoteAction.Zap(-1)
        if (isChannelDownKey(key, nativeCode)) return TvRemoteAction.Zap(1)

        // 2. Standard D-Pad Navigation in Fullscreen
        if (key == Key.DirectionUp) return TvRemoteAction.Zap(-1)
        if (key == Key.DirectionDown) return TvRemoteAction.Zap(1)
        if (key == Key.DirectionLeft) return TvRemoteAction.OpenSidebar
        if (key == Key.DirectionRight) return TvRemoteAction.QuickRecentChannel

        // 3. Center / OK / Enter button -> Reveal OSD Playback Bar
        if (key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter) {
            return TvRemoteAction.ToggleOsd
        }

        // 4. Guide / Live TV / Program Info
        if (isGuideKey(key, nativeCode)) return TvRemoteAction.OpenGuide
        if (isInfoKey(key, nativeCode)) return TvRemoteAction.ToggleInfo

        // 5. Media Control Keys
        if (key == Key.MediaPlayPause || key == Key.MediaPlay || key == Key.MediaPause) {
            return TvRemoteAction.PlayPause
        }
        if (key == Key.MediaFastForward) return TvRemoteAction.FastForward
        if (key == Key.MediaRewind) return TvRemoteAction.Rewind

        // 6. Direct Numeric Channel Zap (0-9)
        val digit = keyToDigit(key)
        if (digit != null) return TvRemoteAction.NumberDigit(digit)

        return null
    }

    /**
     * Checks if a key represents Channel Up / Page Up.
     */
    fun isChannelUpKey(key: Key, nativeKeyCode: Int): Boolean {
        return key == Key.ChannelUp ||
                key == Key.PageUp ||
                nativeKeyCode == KeyEvent.KEYCODE_CHANNEL_UP ||
                nativeKeyCode == KeyEvent.KEYCODE_PAGE_UP
    }

    /**
     * Checks if a key represents Channel Down / Page Down.
     */
    fun isChannelDownKey(key: Key, nativeKeyCode: Int): Boolean {
        return key == Key.ChannelDown ||
                key == Key.PageDown ||
                nativeKeyCode == KeyEvent.KEYCODE_CHANNEL_DOWN ||
                nativeKeyCode == KeyEvent.KEYCODE_PAGE_DOWN
    }

    /**
     * Checks if a key represents Guide / Live TV.
     */
    fun isGuideKey(key: Key, nativeKeyCode: Int): Boolean {
        return key == Key.Guide ||
                nativeKeyCode == KeyEvent.KEYCODE_GUIDE ||
                nativeKeyCode == KeyEvent.KEYCODE_TV ||
                nativeKeyCode == KeyEvent.KEYCODE_TV_INPUT ||
                nativeKeyCode == 170 || // KEYCODE_TV_DATA_SERVICE
                nativeKeyCode == 227    // KEYCODE_TV_AUDIO_DESCRIPTION
    }

    /**
     * Checks if a key represents Info / Menu.
     */
    fun isInfoKey(key: Key, nativeKeyCode: Int): Boolean {
        return key == Key.Info ||
                key == Key.Menu ||
                key == Key.Help ||
                nativeKeyCode == KeyEvent.KEYCODE_INFO ||
                nativeKeyCode == KeyEvent.KEYCODE_MENU
    }

    /**
     * Maps a key to a numeric character (0-9) for direct channel entry.
     */
    fun keyToDigit(key: Key): Char? = when (key) {
        Key.Zero, Key.NumPad0 -> '0'
        Key.One, Key.NumPad1 -> '1'
        Key.Two, Key.NumPad2 -> '2'
        Key.Three, Key.NumPad3 -> '3'
        Key.Four, Key.NumPad4 -> '4'
        Key.Five, Key.NumPad5 -> '5'
        Key.Six, Key.NumPad6 -> '6'
        Key.Seven, Key.NumPad7 -> '7'
        Key.Eight, Key.NumPad8 -> '8'
        Key.Nine, Key.NumPad9 -> '9'
        else -> null
    }
}

/**
 * Attaches a TV Remote Key Dispatcher modifier.
 */
@Composable
fun Modifier.onTvRemoteAction(
    onAction: (TvRemoteAction) -> Boolean,
): Modifier {
    return this.onPreviewKeyEvent { event ->
        val action = TvRemoteKeyMapper.mapFullscreenKey(event)
        if (action != null) {
            onAction(action)
        } else {
            false
        }
    }
}
