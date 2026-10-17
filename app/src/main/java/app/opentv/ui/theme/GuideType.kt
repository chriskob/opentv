/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * The face the guide sets programme titles in.
 *
 * The reference's programme-cell layout names `sans-serif-condensed`, and the difference is
 * visible: condensed lets a full programme title sit in a cell that a normal-width face would
 * ellipsise halfway through. Compose exposes no condensed constant - its generic families are
 * SansSerif, Serif, Monospace and Cursive - so this resolves the device family by name instead.
 * `DeviceFontFamilyName` asks the platform for a system family, which is exactly what the
 * reference's own TextView does; if a device's font set lacks it, the platform falls back to its
 * default sans face rather than failing.
 *
 * No font file is bundled: this references a family the device already ships.
 */
val GuideProgrammeFontFamily: FontFamily = FontFamily(Font(DeviceFontFamilyName("sans-serif-condensed")))
