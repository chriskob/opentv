/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.opentv.ui.theme.AppTheme

/**
 * The one focus treatment, for the whole app.
 *
 * Before this there were seven dialects sharing one interaction: an opaque accent block, an
 * accent-container fill, an opaque white block, a translucent accent wash, a faint tint, a
 * ring-only lift, and nothing at all. This replaces all of them with the OpenChamber overlay
 * language the palette was ported from: the cursor is a translucent [AppTheme] `hover` lift plus the
 * theme's accent hairline, and the current item keeps a translucent `selection` lift while focus is
 * elsewhere.
 *
 * Because the cursor fill is translucent, labels keep ordinary `onSurface` ink instead of flipping
 * to a dark contrasting colour — the inversion every older component had to hand-roll (and
 * sometimes forgot) is gone. The hairline is what makes the cursor legible where the wash is faint.
 *
 * Attach it to any focusable surface. Callers still own `.focusable()` / `.clickable()` and the
 * content colours; this only paints focus and selection.
 *
 * @param selected a persistent (not focus) selection state — the active nav tab, settings section,
 *   category or playing row. Selected rows fill at all times, independent of the d-pad.
 * @param danger destructive controls keep their red identity in the fill and ring.
 * @param scale optional focus lift; 1f draws nothing extra. Kept small (1.02–1.06) where used.
 */
@Composable
fun Modifier.tvFocus(
    shape: Shape = RoundedCornerShape(12.dp),
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    scale: Float = 1f,
    onFocusChange: ((Boolean) -> Unit)? = null,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val palette = AppTheme.palette

    val fill by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            focused -> if (danger) palette.errorContainer else palette.cursorFill
            selected -> if (danger) palette.errorContainer else palette.selectedFill
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "tvFocusFill",
    )
    val outline by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            focused -> if (danger) palette.error else palette.cursorBorder
            else -> Color.Transparent
        },
        animationSpec = tween(120),
        label = "tvFocusOutline",
    )
    val scaleValue by animateFloatAsState(
        targetValue = if (focused && enabled) scale else 1f,
        animationSpec = tween(120),
        label = "tvFocusScale",
    )

    return this
        .graphicsLayer {
            scaleX = scaleValue
            scaleY = scaleValue
        }
        .clip(shape)
        .background(fill, shape)
        .border(1.5.dp, outline, shape)
        .alpha(if (enabled) 1f else 0.5f)
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange?.invoke(it.isFocused)
        }
}
