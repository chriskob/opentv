/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import androidx.compose.runtime.Composable
import app.opentv.ui.theme.GuideTheme

/**
 * Wraps the guide screen so it renders in its own palette and metrics.
 *
 * Keeping this in a one-line composable of its own means the guide's entry point states plainly
 * that it owns its theme, and the parity tests wrap fixtures the same way the screen does.
 */
@Composable
fun GuideScreenHost(content: @Composable () -> Unit) {
    GuideTheme(content = content)
}
