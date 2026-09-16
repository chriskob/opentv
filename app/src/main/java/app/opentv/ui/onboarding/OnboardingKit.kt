/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.ui.components.tvFocus
import app.opentv.ui.theme.AppTheme

/**
 * The onboarding buttons, drawn with the same focus treatment as the rest of the app.
 *
 * M3's `Button`/`OutlinedButton`/`TextButton` draw no focus ring on TV, so the first screens a new
 * user meets had no visible cursor at all. These match `SettingsButton`: an accent fill for the
 * primary action, a themed surface for secondary, and the shared translucent cursor wash plus the
 * accent hairline on focus. [loading] swaps the label for a spinner (the test-connection button).
 */
@Composable
fun OnboardingButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    val content = if (primary) AppTheme.palette.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .clip(shape)
            .background(if (primary) AppTheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest)
            .tvFocus(shape = shape, enabled = enabled)
            .focusable(enabled = enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = if (primary) AppTheme.palette.onPrimary else AppTheme.primary,
            )
        } else {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
                fontWeight = FontWeight.Medium,
                color = content,
            )
        }
    }
}
