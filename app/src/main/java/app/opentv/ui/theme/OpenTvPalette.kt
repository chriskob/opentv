/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The full colour palettes OpenTV ships with, ported from OpenChamber's dark themes.
 *
 * OpenTV used to offer a handful of accent colours painted over one fixed near-black scheme, which
 * meant every "theme" was really the same room with a different lamp. These are complete palettes:
 * background, surfaces, text, borders and semantics all move together, and the theme's own accent is
 * the app accent. The values come from OpenChamber's theme JSON (surface / interactive / primary /
 * status groups) so the two projects read as the same design language.
 */
enum class AppPalette(val displayName: String) {
    AURA("Aura"),
    AYU("Ayu"),
    CARBONFOX("Carbonfox"),
    DRACULA("Dracula"),
    JETBRAINS("JetBrains"),
    NIGHTOWL("Night Owl"),
    MONOPLUS("Mono Plus"),
    OPENCHAMBER("OpenChamber"),
    SLATE_GUIDE("Slate Guide"),
    TVPLAYER("TVPlayer"),
    TVIMATE_PARITY("Parity Guide"),
}

/**
 * A resolved OpenTV palette. The stored fields are the OpenChamber tokens we translate directly;
 * everything the OpenChamber schema does not name is derived here so the ladder stays monotonic for
 * every theme (some themes put `elevated` above `background`, Night Owl puts it below — a fixed
 * blend keeps the elevation reading the same regardless).
 */
class OpenTvPalette internal constructor(
    val background: Color,
    val surface: Color,
    val subtle: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val borderHover: Color,
    val focusBorder: Color,
    val primary: Color,
    val onPrimary: Color,
    val primaryHover: Color,
    val primaryActive: Color,
    val focusRing: Color,
    val selection: Color,
    val hover: Color,
    val recording: Color,
    val favourite: Color,
    val live: Color,
    val error: Color,
    val errorContainer: Color,
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val info: Color,
    val onCursor: Color,
    val cellFocus: Color,
    val onCellFocus: Color,
) {
    // ---- Surface ramp -----------------------------------------------------------------------
    val surfaceContainerLowest: Color get() = background
    val surfaceContainerLow: Color get() = lerp(background, subtle, 0.25f)
    val surfaceContainer: Color get() = lerp(background, subtle, 0.45f)
    val surfaceContainerHigh: Color get() = subtle
    val surfaceContainerHighest: Color get() = lerp(subtle, onSurface, 0.06f)
    val surfaceVariant: Color get() = lerp(background, subtle, 0.50f)

    // ---- Lines and muted text ---------------------------------------------------------------
    val outlineVariant: Color get() = lerp(outline, background, 0.35f)
    val hairline: Color get() = outline
    val textMuted: Color get() = lerp(onSurfaceVariant, background, 0.25f)
    val textDisabled: Color get() = lerp(onSurfaceVariant, background, 0.45f)

    // ---- Guide / player chrome --------------------------------------------------------------
    val chrome: Color get() = surface
    val chromeHighlight: Color get() = lerp(surface, primary, 0.14f)
    val chromeCell: Color get() = surface
    val chromeCellBorder: Color get() = outline
    val selectedSurface: Color get() = lerp(surface, primary, 0.22f)
    val chipSurface: Color get() = surfaceContainerHigh

    // ---- Accent-derived ---------------------------------------------------------------------
    val cardFocusBg: Color get() = lerp(surface, primary, 0.16f)
    val primaryContainer: Color get() = primaryHover
    val onPrimaryContainer: Color get() = primaryActive

    /**
     * The ink that sits on a *filled* accent control — the primary play/pause button, the LIVE badge
     * — never on the cursor, which is the translucent [cursorFill] and keeps ordinary `onSurface`
     * text. Kept under its old name so those call sites did not have to move.
     */
    val onFocusSurface: Color get() = onPrimary

    /**
     * The shared cursor / selection language, built from the OpenChamber overlay tokens.
     *
     * The cursor is [hover] — a translucent lift over whatever is beneath — and the current item
     * (nav tab, active settings section, playing row) keeps [selection] while focus is elsewhere.
     * Because both are translucent, a focused label no longer has to repaint itself in a contrasting
     * ink: text stays [onSurface] throughout. [cursorBorder] is the theme's accent hairline, which is
     * what keeps the cursor unmistakable even on themes whose wash is faint.
     */
    val cursorFill: Color get() = hover
    val cursorBorder: Color get() = focusBorder
    val selectedFill: Color get() = selection
}

private val AuraPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFEDECEE),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF15141B),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFF6767),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFFFCA85),
    error = Color(0xFFFF6767),
    errorContainer = Color(0x20FF6767),
    success = Color(0xFF61FFCA),
    successContainer = Color(0x2061FFCA),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF82E2FF),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val AyuPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFD6DAE0),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF0F1419),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFF58572),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFE4A75C),
    error = Color(0xFFF58572),
    errorContainer = Color(0x20F58572),
    success = Color(0xFF78D05C),
    successContainer = Color(0x2078D05C),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF66C6F1),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val CarbonfoxPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF161616),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFF8389),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFF1C21B),
    error = Color(0xFFFF8389),
    errorContainer = Color(0x20FF8389),
    success = Color(0xFF42BE65),
    successContainer = Color(0x2042BE65),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF78A9FF),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val DraculaPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFF8F8F2),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF14151F),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFF5555),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFFFB86C),
    error = Color(0xFFFF5555),
    errorContainer = Color(0x20FF5555),
    success = Color(0xFF50FA7B),
    successContainer = Color(0x2050FA7B),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF8BE9FD),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val JetBrainsPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFDFE1E5),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF1E1F22),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFA6675),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFF2C55C),
    error = Color(0xFFFA6675),
    errorContainer = Color(0xFF402929),
    success = Color(0xFF57965D),
    successContainer = Color(0xFF253627),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF3592C4),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val NightOwlPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFD6DEEB),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF011627),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFEF5350),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFECC48D),
    error = Color(0xFFEF5350),
    errorContainer = Color(0x20EF5350),
    success = Color(0xFFC5E478),
    successContainer = Color(0x20C5E478),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF82AAFF),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val MonoPlusPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF000000),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFF9E6A6A),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFF9E8A6A),
    error = Color(0xFF9E6A6A),
    errorContainer = Color(0x209E6A6A),
    success = Color(0xFF6A8E6A),
    successContainer = Color(0x206A8E6A),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF6A7E9E),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val OpenChamberPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFC9C5BA),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF000000),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFDA5B4A),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFFC67F13),
    error = Color(0xFFDA5B4A),
    errorContainer = Color(0x20DA5B4A),
    success = Color(0xFF76AD4F),
    successContainer = Color(0x2076AD4F),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF479FE6),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

private val SlateGuidePalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF111318),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFE5484D),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFF33DFFF),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0x33FF6B6B),
    success = Color(0xFF76AD4F),
    successContainer = Color(0x2076AD4F),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF33DFFF),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

/**
 * TiviMate-like navy guide palette: near-black blue surfaces, slate programme cells, cyan
 * navigation accents, and a light selected cell with dark ink.
 */
private val TvPlayerPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFF3F6F8),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF07131F),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFF15B63),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFF4ED7DA),
    error = Color(0xFFF15B63),
    errorContainer = Color(0x33F15B63),
    success = Color(0xFF64D58A),
    successContainer = Color(0x2064D58A),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF4ED7DA),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

/**
 * The parity palette: the guide's own look, measured rather than themed by hand.
 *
 * Dark navy surfaces (background, cells, elevated rows) carry slate programme blocks, an amber
 * accent paints the focused cell, the now-line and the NEW badge, and cyan/red carry the live and
 * recording semantics. Values are the measured navy-plus-amber family recorded in
 * `docs/superpowers/plans/2026-10-11-guide-metrics-table.md`.
 *
 * This palette is the guide's, not the app's: the guide screen wraps itself in it (see
 * `GuideTheme`), while every other screen keeps whichever theme the viewer chose. Slate Guide is
 * deliberately untouched - `AppSettings.DEFAULT_PALETTE` still points at it and
 * `THEME_FORCE_VERSION` is not bumped, so no existing user's theme changes under them.
 */
private val TivimateParityPalette = OpenTvPalette(
    background = Color(0xFF1C232C),
    surface = Color(0xFF242B33),
    subtle = Color(0xFF2C323A),
    onSurface = Color(0xFFEEEEEE),
    onSurfaceVariant = Color(0xFFB6C5D2),
    outline = Color(0xFF4A6E78),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFF00BCD4),
    primary = Color(0xFF00BCD4),
    onPrimary = Color(0xFF111111),
    primaryHover = Color(0xFF00A5B8),
    primaryActive = Color(0xFF5FD8E4),
    focusRing = Color(0x4A00BCD4),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFF5252),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFF00BCD4),
    error = Color(0xFFFF5252),
    errorContainer = Color(0x33FF5252),
    success = Color(0xFF64D58A),
    successContainer = Color(0x2064D58A),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF00BCD4),
    onCursor = Color(0xFFEEEEEE),
    cellFocus = Color(0xFF242B33),
    onCellFocus = Color(0xFFEEEEEE),
)

/** The resolved palette for this selection. */
fun AppPalette.palette(): OpenTvPalette = when (this) {
    AppPalette.AURA -> AuraPalette
    AppPalette.AYU -> AyuPalette
    AppPalette.CARBONFOX -> CarbonfoxPalette
    AppPalette.DRACULA -> DraculaPalette
    AppPalette.JETBRAINS -> JetBrainsPalette
    AppPalette.NIGHTOWL -> NightOwlPalette
    AppPalette.MONOPLUS -> MonoPlusPalette
    AppPalette.OPENCHAMBER -> OpenChamberPalette
    AppPalette.SLATE_GUIDE -> SlateGuidePalette
    AppPalette.TVPLAYER -> TvPlayerPalette
    AppPalette.TVIMATE_PARITY -> TivimateParityPalette
}
