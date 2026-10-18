/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * The five palettes the theme picker offers, plus the guide's own.
 *
 * Every palette is a complete room: background, surfaces, text, borders and semantics all move
 * together, and the palette's own accent becomes the app accent. Slate Guide keeps the
 * slate-and-cyan look the app has worn since 0.16.11; the other four carry the real colours of
 * the theme families they are named after - Dracula's purple on #282A36, Night Owl's navy and
 * blue, Ayu's near-black and orange, Carbonfox's grey and teal. They are deliberately far apart:
 * an earlier port had flattened every family onto one base room, so all eleven chips showed the
 * same swatch and the picker promised a choice it did not deliver (maintainer's report,
 * 2026-10-18).
 *
 * [AppPalette.TVIMATE_PARITY] is not selectable: it is the guide's measured look (see
 * `GuideTheme`) and lives here so `palette()` can resolve it.
 */
enum class AppPalette(val displayName: String, val selectable: Boolean = true) {
    SLATE_GUIDE("Slate Guide"),
    DRACULA("Dracula"),
    NIGHTOWL("Night Owl"),
    AYU("Ayu"),
    CARBONFOX("Carbonfox"),

    /** The guide's own palette; the guide ignores the app theme, so it is not offered. */
    TVIMATE_PARITY("Parity Guide", selectable = false);

    companion object {
        /** The palettes the theme picker offers, the default first. */
        val choices: List<AppPalette> get() = entries.filter { it.selectable }
    }
}

/**
 * A resolved OpenTV palette. The stored fields are the tokens a theme names directly; everything
 * else is derived here so the elevation ladder stays monotonic in every theme (a fixed blend keeps
 * the elevation reading the same wherever a theme's surfaces sit).
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
    /**
     * The guide header's hairline. The reference's own screen layout declares a 1dip `#20ffffff`
     * rule at the clock's bottom edge (`0x7f0b0140`), but ships it `android:visibility="gone"`, and
     * the device capture shows no line there - so the guide leaves it off unless a caller asks for
     * it. The colour lives here so that asking for it is one flag, not a new literal.
     */
    val headerDivider: Color = Color(0x20FFFFFF),
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
     * The shared cursor / selection language, built from the overlay tokens.
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

/**
 * The default room, and the app's own look since 0.16.11: slate-blue surfaces with a cyan accent,
 * pure-white body text and the bright cyan (`#33DFFF`) live/info family.
 */
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
 * Dracula's own room: purple-slate `#282A36` over `#44475A`, keyword purple `#BD93F9` as the
 * accent, and the canonical cyan / green / yellow / red semantics.
 */
private val DraculaPalette = OpenTvPalette(
    background = Color(0xFF282A36),
    surface = Color(0xFF343746),
    subtle = Color(0xFF44475A),
    onSurface = Color(0xFFF8F8F2),
    onSurfaceVariant = Color(0xFFA6ACCC),
    outline = Color(0xFF6272A4),
    borderHover = Color(0xFF7E8AB8),
    focusBorder = Color(0xFFBD93F9),
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF282A36),
    primaryHover = Color(0xFFCBA6FA),
    primaryActive = Color(0xFFA87BF5),
    focusRing = Color(0x4ABD93F9),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFF5555),
    favourite = Color(0xFFF1FA8C),
    live = Color(0xFF8BE9FD),
    error = Color(0xFFFF5555),
    errorContainer = Color(0x33FF5555),
    success = Color(0xFF50FA7B),
    successContainer = Color(0x2050FA7B),
    warning = Color(0xFFF1FA8C),
    info = Color(0xFF8BE9FD),
    onCursor = Color(0xFFF8F8F2),
    cellFocus = Color(0xFF343746),
    onCellFocus = Color(0xFFF8F8F2),
)

/**
 * Night Owl's own room: near-black navy `#011627` stepping up through `#0B253A`, soft blue
 * `#82AAFF` as the accent, cyan `#7FDBCA` for live/info and green `#22DA6E` for success.
 */
private val NightOwlPalette = OpenTvPalette(
    background = Color(0xFF011627),
    surface = Color(0xFF0B253A),
    subtle = Color(0xFF123A55),
    onSurface = Color(0xFFD6DEEB),
    onSurfaceVariant = Color(0xFFA2B6C6),
    outline = Color(0xFF3B647E),
    borderHover = Color(0xFF4E7C99),
    focusBorder = Color(0xFF82AAFF),
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF011627),
    primaryHover = Color(0xFF9CBAFF),
    primaryActive = Color(0xFF6C97F0),
    focusRing = Color(0x4A82AAFF),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFEF5350),
    favourite = Color(0xFFFFCB8B),
    live = Color(0xFF7FDBCA),
    error = Color(0xFFEF5350),
    errorContainer = Color(0x33EF5350),
    success = Color(0xFF22DA6E),
    successContainer = Color(0x2022DA6E),
    warning = Color(0xFFFFCB8B),
    info = Color(0xFF7FDBCA),
    onCursor = Color(0xFFD6DEEB),
    cellFocus = Color(0xFF0B253A),
    onCellFocus = Color(0xFFD6DEEB),
)

/**
 * Ayu's own room: near-black warm `#0B0E14` stepping up through `#131821`, signature orange
 * `#FF8F40` as the accent, with Ayu's blue `#59C2FF`, green `#AAD94C` and red `#F07178`.
 */
private val AyuPalette = OpenTvPalette(
    background = Color(0xFF0B0E14),
    surface = Color(0xFF131821),
    subtle = Color(0xFF1C2230),
    onSurface = Color(0xFFBFBDB6),
    onSurfaceVariant = Color(0xFFA6A49C),
    outline = Color(0xFF333E4C),
    borderHover = Color(0xFF47535F),
    focusBorder = Color(0xFFFF8F40),
    primary = Color(0xFFFF8F40),
    onPrimary = Color(0xFF0B0E14),
    primaryHover = Color(0xFFFFA05C),
    primaryActive = Color(0xFFF08030),
    focusRing = Color(0x4AFF8F40),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFF07178),
    favourite = Color(0xFFFFEE99),
    live = Color(0xFF95E6CB),
    error = Color(0xFFF07178),
    errorContainer = Color(0x33F07178),
    success = Color(0xFFAAD94C),
    successContainer = Color(0x20AAD94C),
    warning = Color(0xFFFFEE99),
    info = Color(0xFF59C2FF),
    onCursor = Color(0xFFBFBDB6),
    cellFocus = Color(0xFF131821),
    onCellFocus = Color(0xFFBFBDB6),
)

/**
 * Carbonfox's own room: neutral Carbon greys `#161616` / `#262626` / `#393939` with the soft teal
 * `#3DDBD9` as the accent, Carbon's red `#FA4D56`, yellow `#F1C21B` and green `#42BE65` semantics.
 */
private val CarbonfoxPalette = OpenTvPalette(
    background = Color(0xFF161616),
    surface = Color(0xFF262626),
    subtle = Color(0xFF393939),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFFC6C6C6),
    outline = Color(0xFF525252),
    borderHover = Color(0xFF6F6F6F),
    focusBorder = Color(0xFF3DDBD9),
    primary = Color(0xFF3DDBD9),
    onPrimary = Color(0xFF161616),
    primaryHover = Color(0xFF6FE7E4),
    primaryActive = Color(0xFF2BB8B6),
    focusRing = Color(0x4A3DDBD9),
    selection = Color(0x40DFE1E4),
    hover = Color(0x1AFFFFFF),
    recording = Color(0xFFFA4D56),
    favourite = Color(0xFFF1C21B),
    live = Color(0xFF3DDBD9),
    error = Color(0xFFFA4D56),
    errorContainer = Color(0x33FA4D56),
    success = Color(0xFF42BE65),
    successContainer = Color(0x2042BE65),
    warning = Color(0xFFF1C21B),
    info = Color(0xFF78A9FF),
    onCursor = Color(0xFFF2F4F8),
    cellFocus = Color(0xFF262626),
    onCellFocus = Color(0xFFF2F4F8),
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
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xA0FFFFFF),
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
    // The cursor is the reference's filled plate: a light cell carrying dark ink (#DFE1E4 on
    // #111318, measured from the Onn capture). It is a fill, not a ring — a dark cellFocus plus
    // a border is the "cursor is just a coloured border" regression pinned against in
    // GuidePaletteContrastTest.
    cellFocus = Color(0xFFDFE1E4),
    onCellFocus = Color(0xFF111318),
)

/** The resolved palette for this selection. */
fun AppPalette.palette(): OpenTvPalette = when (this) {
    AppPalette.SLATE_GUIDE -> SlateGuidePalette
    AppPalette.DRACULA -> DraculaPalette
    AppPalette.NIGHTOWL -> NightOwlPalette
    AppPalette.AYU -> AyuPalette
    AppPalette.CARBONFOX -> CarbonfoxPalette
    AppPalette.TVIMATE_PARITY -> TivimateParityPalette
}
