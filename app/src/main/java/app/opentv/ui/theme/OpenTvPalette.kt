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
    background = Color(0xFF15141B),
    surface = Color(0xFF201E2B),
    subtle = Color(0xFF25232F),
    onSurface = Color(0xFFEDECEE),
    onSurfaceVariant = Color(0xFF8A8282),
    outline = Color(0xFF2A2935),
    borderHover = Color(0xFF47415A),
    focusBorder = Color(0xFF4E496C),
    primary = Color(0xFFA277FF),
    onPrimary = Color(0xFF15141B),
    primaryHover = Color(0xFF8D68DD),
    primaryActive = Color(0xFFB08BFF),
    focusRing = Color(0x40A277FF),
    selection = Color(0x2D95ABE0),
    hover = Color(0x2D7887AC),
    recording = Color(0xFFFF6767),
    favourite = Color(0xFFFFCA85),
    live = Color(0xFFFFCA85),
    error = Color(0xFFFF6767),
    errorContainer = Color(0x20FF6767),
    success = Color(0xFF61FFCA),
    successContainer = Color(0x2061FFCA),
    warning = Color(0xFFFFCA85),
    info = Color(0xFF82E2FF),
    onCursor = Color(0xFFEDECEE),
    cellFocus = Color(0x2D7887AC),
    onCellFocus = Color(0xFFEDECEE),
)

private val AyuPalette = OpenTvPalette(
    background = Color(0xFF0F1419),
    surface = Color(0xFF17202A),
    subtle = Color(0xFF1E252D),
    onSurface = Color(0xFFD6DAE0),
    onSurfaceVariant = Color(0xFF777E86),
    outline = Color(0xFF292C30),
    borderHover = Color(0xFF323C49),
    focusBorder = Color(0xFF56647C),
    primary = Color(0xFF3FB7E3),
    onPrimary = Color(0xFF0F1419),
    primaryHover = Color(0xFF389FC5),
    primaryActive = Color(0xFF5BC1E7),
    focusRing = Color(0x403FB7E3),
    selection = Color(0x1DC7C7DF),
    hover = Color(0x1DB6B6CC),
    recording = Color(0xFFF58572),
    favourite = Color(0xFFE4A75C),
    live = Color(0xFFE4A75C),
    error = Color(0xFFF58572),
    errorContainer = Color(0x20F58572),
    success = Color(0xFF78D05C),
    successContainer = Color(0x2078D05C),
    warning = Color(0xFFE4A75C),
    info = Color(0xFF66C6F1),
    onCursor = Color(0xFFD6DAE0),
    cellFocus = Color(0x1DB6B6CC),
    onCellFocus = Color(0xFFD6DAE0),
)

private val CarbonfoxPalette = OpenTvPalette(
    background = Color(0xFF161616),
    surface = Color(0xFF222222),
    subtle = Color(0xFF292828),
    onSurface = Color(0xFFF2F4F8),
    onSurfaceVariant = Color(0xFF8B8A8A),
    outline = Color(0xFF2D2C2C),
    borderHover = Color(0xFF4C4C4C),
    focusBorder = Color(0xFF4589FF),
    primary = Color(0xFF33B1FF),
    onPrimary = Color(0xFF161616),
    primaryHover = Color(0xFF2F9ADC),
    primaryActive = Color(0xFF52BDFF),
    focusRing = Color(0x404589FF),
    selection = Color(0x20FFFFFF),
    hover = Color(0x12FFFFFF),
    recording = Color(0xFFFF8389),
    favourite = Color(0xFFF1C21B),
    live = Color(0xFFF1C21B),
    error = Color(0xFFFF8389),
    errorContainer = Color(0x20FF8389),
    success = Color(0xFF42BE65),
    successContainer = Color(0x2042BE65),
    warning = Color(0xFFF1C21B),
    info = Color(0xFF78A9FF),
    onCursor = Color(0xFFF2F4F8),
    cellFocus = Color(0x12FFFFFF),
    onCellFocus = Color(0xFFF2F4F8),
)

private val DraculaPalette = OpenTvPalette(
    background = Color(0xFF14151F),
    surface = Color(0xFF161722),
    subtle = Color(0xFF1F2030),
    onSurface = Color(0xFFF8F8F2),
    onSurfaceVariant = Color(0xFF7C7E9C),
    outline = Color(0xFF292A36),
    borderHover = Color(0xFF303244),
    focusBorder = Color(0xFF4A4D6D),
    primary = Color(0xFFBD93F9),
    onPrimary = Color(0xFF14151F),
    primaryHover = Color(0xFFA480D8),
    primaryActive = Color(0xFFC7A3FA),
    focusRing = Color(0x40BD93F9),
    selection = Color(0xA330334B),
    hover = Color(0xA330334B),
    recording = Color(0xFFFF5555),
    favourite = Color(0xFFFFB86C),
    live = Color(0xFFFFB86C),
    error = Color(0xFFFF5555),
    errorContainer = Color(0x20FF5555),
    success = Color(0xFF50FA7B),
    successContainer = Color(0x2050FA7B),
    warning = Color(0xFFFFB86C),
    info = Color(0xFF8BE9FD),
    onCursor = Color(0xFFF8F8F2),
    cellFocus = Color(0xA330334B),
    onCellFocus = Color(0xFFF8F8F2),
)

private val JetBrainsPalette = OpenTvPalette(
    background = Color(0xFF1E1F22),
    surface = Color(0xFF2B2D30),
    subtle = Color(0xFF28292C),
    onSurface = Color(0xFFDFE1E5),
    onSurfaceVariant = Color(0xFF9FA0A2),
    outline = Color(0xFF383A3F),
    borderHover = Color(0xFF4B5059),
    focusBorder = Color(0xFF6796F5),
    primary = Color(0xFF6796F5),
    onPrimary = Color(0xFF1E1F22),
    primaryHover = Color(0xFF70AEFF),
    primaryActive = Color(0xFF5594FA),
    focusRing = Color(0x506796F5),
    selection = Color(0xFF43454A),
    hover = Color(0xFF3C3E41),
    recording = Color(0xFFFA6675),
    favourite = Color(0xFFF2C55C),
    live = Color(0xFFF2C55C),
    error = Color(0xFFFA6675),
    errorContainer = Color(0xFF402929),
    success = Color(0xFF57965D),
    successContainer = Color(0xFF253627),
    warning = Color(0xFFF2C55C),
    info = Color(0xFF3592C4),
    onCursor = Color(0xFFDFE1E5),
    cellFocus = Color(0xFF3C3E41),
    onCellFocus = Color(0xFFDFE1E5),
)

private val NightOwlPalette = OpenTvPalette(
    background = Color(0xFF011627),
    surface = Color(0xFF001122),
    subtle = Color(0xFF162431),
    onSurface = Color(0xFFD6DEEB),
    onSurfaceVariant = Color(0xFF7D8892),
    outline = Color(0xFF2B3339),
    borderHover = Color(0xFF234561),
    focusBorder = Color(0xFF82AAFF),
    primary = Color(0xFF82AAFF),
    onPrimary = Color(0xFF011627),
    primaryHover = Color(0xFF6F94DF),
    primaryActive = Color(0xFF95B7FF),
    focusRing = Color(0x4082AAFF),
    selection = Color(0x94555D6D),
    hover = Color(0x68555D6D),
    recording = Color(0xFFEF5350),
    favourite = Color(0xFFECC48D),
    live = Color(0xFFECC48D),
    error = Color(0xFFEF5350),
    errorContainer = Color(0x20EF5350),
    success = Color(0xFFC5E478),
    successContainer = Color(0x20C5E478),
    warning = Color(0xFFECC48D),
    info = Color(0xFF82AAFF),
    onCursor = Color(0xFFD6DEEB),
    cellFocus = Color(0x68555D6D),
    onCellFocus = Color(0xFFD6DEEB),
)

private val MonoPlusPalette = OpenTvPalette(
    background = Color(0xFF000000),
    surface = Color(0xFF141414),
    subtle = Color(0xFF1A1A1A),
    onSurface = Color(0xFFE5E5E5),
    onSurfaceVariant = Color(0xFF808080),
    outline = Color(0xFF2F2F2F),
    borderHover = Color(0xFF333333),
    focusBorder = Color(0xFFFFFFFF),
    primary = Color(0xFFA2BEE8),
    onPrimary = Color(0xFF000000),
    primaryHover = Color(0xFFE5E5E5),
    primaryActive = Color(0xFFCCCCCC),
    focusRing = Color(0x50FFFFFF),
    selection = Color(0x1FFFFFFF),
    hover = Color(0x1FFFFFFF),
    recording = Color(0xFF9E6A6A),
    favourite = Color(0xFF9E8A6A),
    live = Color(0xFF9E8A6A),
    error = Color(0xFF9E6A6A),
    errorContainer = Color(0x209E6A6A),
    success = Color(0xFF6A8E6A),
    successContainer = Color(0x206A8E6A),
    warning = Color(0xFF9E8A6A),
    info = Color(0xFF6A7E9E),
    onCursor = Color(0xFFE5E5E5),
    cellFocus = Color(0x1FFFFFFF),
    onCellFocus = Color(0xFFE5E5E5),
)

private val OpenChamberPalette = OpenTvPalette(
    background = Color(0xFF120F0E),
    surface = Color(0xFF181715),
    subtle = Color(0xFF171616),
    onSurface = Color(0xFFC9C5BA),
    onSurfaceVariant = Color(0xFF8F8B81),
    outline = Color(0xFF242323),
    borderHover = Color(0xFF504E4C),
    focusBorder = Color(0xFFDA7C47),
    primary = Color(0xFFDA7C47),
    onPrimary = Color(0xFF000000),
    primaryHover = Color(0xFFEB8C57),
    primaryActive = Color(0xFFFD9B66),
    focusRing = Color(0x55DA7C47),
    selection = Color(0x2BC8C6C5),
    hover = Color(0x12FFFFFF),
    recording = Color(0xFFDA5B4A),
    favourite = Color(0xFFC67F13),
    live = Color(0xFFC67F13),
    error = Color(0xFFDA5B4A),
    errorContainer = Color(0x20DA5B4A),
    success = Color(0xFF76AD4F),
    successContainer = Color(0x2076AD4F),
    warning = Color(0xFFC67F13),
    info = Color(0xFF479FE6),
    onCursor = Color(0xFFC9C5BA),
    cellFocus = Color(0x12FFFFFF),
    onCellFocus = Color(0xFFC9C5BA),
)

private val SlateGuidePalette = OpenTvPalette(
    background = Color(0xFF1E2A3B),
    surface = Color(0xFF2A3A50),
    subtle = Color(0xFF33465E),
    onSurface = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFFC9D4E3),
    outline = Color(0xFF3E526B),
    borderHover = Color(0xFF5A728C),
    focusBorder = Color(0xFFFFFFFF),
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF111318),
    primaryHover = Color(0xFFA9BFD8),
    primaryActive = Color(0xFF7A93B2),
    focusRing = Color(0x80FFFFFF),
    selection = Color(0xFF5A728C),
    hover = Color(0x33FFFFFF),
    recording = Color(0xFFE5484D),
    favourite = Color(0xFFFFC94D),
    live = Color(0xFF33DFFF),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0x33FF6B6B),
    success = Color(0xFF76AD4F),
    successContainer = Color(0x2076AD4F),
    warning = Color(0xFFFFC94D),
    info = Color(0xFF33DFFF),
    onCursor = Color(0xFFFFFFFF),
    cellFocus = Color(0xFFFFFFFF),
    onCellFocus = Color(0xFF111318),
)

/**
 * The classic blue-grey + teal look: the Material blue-grey ramp as the surface ladder
 * (`#263238` background, `#37474F` surfaces) with teal 200 (`#80CBC4`) as the accent, and the
 * stock Material semantic hues for recording/favourite/live. Reconstructed from the reference's
 * base theme tokens (colorPrimary #37474F, colorPrimaryDark/windowBackground #263238, colorAccent
 * #80CBC4, colorButtonNormal #5A595B, colorControlHighlight #33FFFFFF).
 */
private val TvPlayerPalette = OpenTvPalette(
    background = Color(0xFF263238),
    surface = Color(0xFF37474F),
    subtle = Color(0xFF455A64),
    onSurface = Color(0xFFECEFF1),
    onSurfaceVariant = Color(0xFFB0BEC5),
    outline = Color(0xFF546E7A),
    borderHover = Color(0xFF78909C),
    focusBorder = Color(0xFF80CBC4),
    primary = Color(0xFF80CBC4),
    onPrimary = Color(0xFF1B2A32),
    primaryHover = Color(0xFF4DB6AC),
    primaryActive = Color(0xFFB2DFDB),
    focusRing = Color(0x5580CBC4),
    selection = Color(0x33FFFFFF),
    hover = Color(0x1FFFFFFF),
    recording = Color(0xFFF44336),
    favourite = Color(0xFFFFC107),
    live = Color(0xFF4DD0E1),
    error = Color(0xFFF44336),
    errorContainer = Color(0x33F44336),
    success = Color(0xFF4CAF50),
    successContainer = Color(0x204CAF50),
    warning = Color(0xFFFFC107),
    info = Color(0xFF00BCD4),
    onCursor = Color(0xFFFFFFFF),
    cellFocus = Color(0xFF80CBC4),
    onCellFocus = Color(0xFF1B2A32),
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
}
