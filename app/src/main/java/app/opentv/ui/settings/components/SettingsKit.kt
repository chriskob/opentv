/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import app.opentv.R
import app.opentv.ui.components.tvFocus
import app.opentv.ui.theme.AppTheme
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * The settings design language, in one place.
 *
 * Before this file every settings page drew its own card chrome (`#18222C` on `#263442`), its own
 * back button, its own switch colours and its own focus treatment — seven different focus dialects
 * across ten screens. This kit replaces all of that with one set of primitives, so a change to the
 * look lands everywhere at once and every page follows the user's chosen accent automatically.
 *
 * Focus is the important bit: a focused element earns an accent ring, a faint accent wash and a
 * small lift — it never becomes an opaque block that swallows the content underneath.
 */

/** Semantic shapes, so radii stop drifting between 8/10/12/14/16dp across screens. */
object SettingsShape {
    val Card = RoundedCornerShape(16.dp)
    val Row = RoundedCornerShape(12.dp)
    val Control = RoundedCornerShape(12.dp)
    val Tile = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(50)
    val Dialogs = RoundedCornerShape(20.dp)
}

/** Destructive actions (remove, delete) use this instead of scattering three different reds. */
val SettingsDanger: Color
    @Composable get() = AppTheme.palette.error

/** Page rhythm, shared so every screen breathes the same way. */
object SettingsSpacing {
    val PageHorizontal = 48.dp
    val PageVertical = 32.dp
    val SectionGap = 28.dp
}

/**
 * The settings call-site name for the shared [tvFocus] treatment. Kept so the ~20 settings
 * call sites did not have to move; it now draws exactly what every other screen draws.
 *
 * @param selected a persistent (not focus) selection state, e.g. the active section or profile.
 *   Selected rows keep the `selection` lift at all times.
 * @param quietSelection kept for call-site compatibility; selection is already a translucent lift,
 *   so it needs no separate quiet form.
 * @param focusScale kept for call-site compatibility; ignored — nothing scales any more.
 */
@Composable
fun Modifier.settingsFocus(
    shape: Shape = SettingsShape.Row,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") quietSelection: Boolean = false,
    @Suppress("UNUSED_PARAMETER") focusScale: Float = 1f,
    onFocusChange: ((Boolean) -> Unit)? = null,
): Modifier = tvFocus(
    shape = shape,
    selected = selected,
    danger = danger,
    enabled = enabled,
    onFocusChange = onFocusChange,
)

/** One switch palette for the whole app (the old code copy-pasted its own into six files). */
@Composable
fun settingsSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = AppTheme.primary,
    checkedBorderColor = AppTheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
)

/** A small focused icon control, for trailing actions inside a row (refresh, reveal, remove). */
@Composable
fun SettingsIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(40.dp)
            .settingsFocus(shape = SettingsShape.Control, focusScale = 1.06f, onFocusChange = { focused = it })
            .focusable()
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            // The cursor's fill is the accent's dark shade, so an explicit tint keeps working on it;
            // without one, fall back to the plain onSurface white the rest of the row uses.
            tint = if (focused) MaterialTheme.colorScheme.onSurface
            else tint ?: MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A single-select choice row with a radio indicator. */
@Composable
fun SettingsChoiceRow(
    title: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .settingsFocus(
                shape = SettingsShape.Row,
                selected = selected,
                onFocusChange = { focused = it },
            )
            .focusable()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    // A selected row now sits on the accent fill, so the ring and dot invert with it
                    // exactly as the title does.
                    color = if (focused || selected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(
                            if (focused || selected) MaterialTheme.colorScheme.onSurface else AppTheme.primary,
                        ),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        SettingsRowText(
            title = title,
            subtitle = subtitle,
        )
    }
}

/**
 * A settings group. Deliberately flat: no card fill, no border, no rounding. Rows sit straight on
 * the page the way TiviMate and Sparkle draw theirs, so a page reads as one list instead of a stack
 * of boxes, and the only filled thing on screen is the cursor.
 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SettingsShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f), SettingsShape.Card)
            .padding(
                if (contentPadding != PaddingValues(0.dp)) contentPadding
                else PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        content = content,
    )
}

/**
 * A titled group: an accent label, then a card holding the rows.
 *
 * Collapsible by default so a long settings page reads as a scannable list of sections instead of
 * one enormous scroll. The header is focusable; the chevron flips as it opens. Pass
 * [initiallyExpanded] = true for a section that should start open.
 */
@Composable
fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (collapsible) {
            var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
            SettingsSectionHeader(title = title, summary = summary, icon = icon, expanded = expanded, onToggle = { expanded = !expanded })
            AnimatedVisibility(visible = expanded) {
                SettingsCard(content = content)
            }
        } else {
            SettingsSectionHeader(title = title, summary = summary, icon = icon, expanded = true, onToggle = null)
            SettingsCard(content = content)
        }
    }
}

/**
 * Section title plus, optionally, a one-line summary of the values inside — so a collapsed section
 * still says what it holds (e.g. "Playlist every 6h · Guide every 4h") and the viewer only has to
 * open the sections they actually want to change.
 */
@Composable
private fun SettingsSectionHeader(
    title: String,
    @Suppress("UNUSED_PARAMETER") icon: ImageVector?,
    summary: String?,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val label: @Composable (Boolean) -> Unit = { onAccent ->
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
            color = if (onAccent) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface,
        )
    }

    if (onToggle == null) {
        Row(Modifier.padding(start = 2.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { label(false) }
    } else {
        var focused by remember { mutableStateOf(false) }
        val rotation by animateFloatAsState(
            targetValue = if (expanded) 180f else 0f,
            animationSpec = tween(180),
            label = "sectionChevron",
        )
        Row(
            Modifier
                .fillMaxWidth()
                .settingsFocus(shape = SettingsShape.Row, onFocusChange = { focused = it })
                .focusable()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { onToggle() }
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { label(focused) }
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1.2f)
                        .padding(end = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp).graphicsLayer { rotationZ = rotation },
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}


/** A title/subtitle row with a switch; the whole row toggles. */
@Composable
fun SettingsToggleRow(
    title: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .settingsFocus(
                shape = SettingsShape.Row,
                enabled = enabled,
                onFocusChange = { focused = it },
            )
            .focusable(enabled)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title = title,
            subtitle = subtitle,
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
            Spacer(Modifier.width(8.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            colors = settingsSwitchColors(),
            modifier = Modifier.focusProperties { canFocus = false },
        )
    }
}

/** A title/subtitle row that navigates or performs an action, with an optional trailing control. */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    expanded: Boolean = true,
    tint: Color = AppTheme.primary,
    titleSize: TextUnit = 16.sp,
    titleWeight: FontWeight = FontWeight.Medium,
    trailing: @Composable (() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    // The wrapper lets a selected row draw an accent bar pinned to its left edge — the "you are
    // here" mark, distinct from the focus ring that moves with the cursor.
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .settingsFocus(
                    shape = SettingsShape.Row,
                    selected = selected,
                    onFocusChange = { focused = it },
                )
                .focusable()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = if (expanded) 16.dp else 8.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(SettingsShape.Tile)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp),
                )
            }
            if (expanded) Spacer(Modifier.width(12.dp))
        }
        if (expanded) {
            SettingsRowText(
                title = title,
                subtitle = subtitle,
                titleSize = titleSize,
                titleWeight = titleWeight,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (expanded) {
            if (trailing != null) {
                trailing()
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        }
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 3.dp)
                    .clip(SettingsShape.Pill)
                    .background(if (focused) MaterialTheme.colorScheme.onSurface else AppTheme.primary)
                    .width(4.dp)
                    .height(26.dp),
            )
        }
    }
}

@Composable
private fun RowScope.SettingsRowText(
    title: String,
    subtitle: String?,
    titleSize: TextUnit = 16.sp,
    titleWeight: FontWeight = FontWeight.Medium,
) {
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = titleSize),
            fontWeight = titleWeight,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

enum class SettingsButtonStyle { Primary, Secondary, Danger }

/** The one button. Replaces every raw Material `Button`/`OutlinedButton`/`TextButton` in settings. */
@Composable
fun SettingsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: SettingsButtonStyle = SettingsButtonStyle.Secondary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val danger = style == SettingsButtonStyle.Danger
    val shape = SettingsShape.Control

    // The base fill is the button's identity (solid accent for Primary, tinted for Danger); focus
    // only adds the shared neutral cursor lift on top, it never repaints the button's colour.
    val baseFill = when {
        danger -> SettingsDanger.copy(alpha = 0.14f)
        style == SettingsButtonStyle.Primary -> AppTheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val fill = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        focused && danger -> SettingsDanger.copy(alpha = 0.22f)
        focused && style == SettingsButtonStyle.Primary -> AppTheme.primary
        focused -> AppTheme.palette.cursorFill
        else -> baseFill
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        focused -> MaterialTheme.colorScheme.onSurface
        danger -> SettingsDanger
        style == SettingsButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val outline = when {
        focused -> if (danger) SettingsDanger else AppTheme.palette.cursorBorder
        danger -> SettingsDanger.copy(alpha = 0.45f)
        style == SettingsButtonStyle.Primary -> AppTheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Row(
        modifier
            .clip(shape)
            .background(fill, shape)
            .border(if (focused) 1.5.dp else 1.dp, outline, shape)
            .alpha(if (enabled) 1f else 0.75f)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SettingsBackButton(
    onClick: () -> Unit,
    label: String = stringResource(R.string.common_done),
) {
    SettingsButton(
        text = label,
        onClick = onClick,
        style = SettingsButtonStyle.Secondary,
        icon = Icons.AutoMirrored.Filled.ArrowBack,
    )
}

/**
 * A compact selectable chip. Use a chip grid instead of a full-width toggle row per item when a
 * group has many options — it turns a long scroll into a couple of scannable rows.
 */
@Composable
fun SettingsChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(SettingsShape.Pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .settingsFocus(
                shape = SettingsShape.Pill,
                selected = selected,
                enabled = enabled,
                // A chip grid can have many chips on at once, so selection stays a faint tint here;
                // the full pill is reserved for nav lists, where there is one current thing.
                quietSelection = true,
                focusScale = 1.04f,
                onFocusChange = { focused = it },
            )
            .focusable(enabled)
            .clickable(
                enabled = enabled,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = if (focused) MaterialTheme.colorScheme.onSurface else AppTheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = when {
                focused -> MaterialTheme.colorScheme.onSurface
                selected -> AppTheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
        )
    }
}

/** A labelled two-or-more way choice, e.g. Grid / List. */
@Composable
fun SettingsSegmented(
    options: List<String>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(SettingsShape.Control)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Control)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            var optionFocused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .weight(1f)
                    .settingsFocus(
                        shape = SettingsShape.Row,
                        selected = isSelected,
                        focusScale = 1.02f,
                        onFocusChange = { optionFocused = it },
                    )
                    .focusable()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
                    fontWeight = FontWeight.Medium,
                    color = when {
                        // The chosen segment sits on the accent fill at all times, so its label is
                        // ordinary onSurface white rather than the accent.
                        optionFocused || isSelected -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }
}

/** A row that opens a dialog list of options. */
@Composable
fun <T> SettingsDropdown(
    title: String,
    options: List<Pair<String, T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.second == selectedValue }?.first ?: "$selectedValue"

    SettingsNavRow(
        title = title,
        subtitle = subtitle,
        onClick = { showDialog = true },
        modifier = modifier,
        trailing = {
            Row(
                Modifier
                    .clip(SettingsShape.Row)
                    .background(AppTheme.primary.copy(alpha = 0.12f))
                    .border(1.dp, AppTheme.primary.copy(alpha = 0.3f), SettingsShape.Row)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = currentLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(18.dp))
            }
        },
    )

    if (showDialog) {
        // A focused, app-styled picker rather than Material's generic AlertDialog: the current value
        // is named up top, the list opens scrolled to it with focus already on it, and closing is a
        // single focusable button — all reachable from a d-pad without hunting.
        val listState = rememberLazyListState()
        val selectedIndex = options.indexOfFirst { it.second == selectedValue }
        val focusIndex = if (selectedIndex >= 0) selectedIndex else 0
        val itemFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (focusIndex > 0) runCatching { listState.scrollToItem(focusIndex) }
            delay(60)
            runCatching { itemFocus.requestFocus() }
        }
        Dialog(
            onDismissRequest = { showDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                Modifier
                    .width(520.dp)
                    .clip(SettingsShape.Dialogs)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Dialogs)
                    .padding(20.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 18.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Current: $currentLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 430.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(options) { index, option ->
                        val isSelected = option.second == selectedValue
                        SettingsNavRow(
                            title = option.first,
                            onClick = {
                                onSelect(option.second)
                                showDialog = false
                            },
                            selected = isSelected,
                            tint = AppTheme.primary,
                            modifier = if (index == focusIndex) Modifier.focusRequester(itemFocus) else Modifier,
                            trailing = {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = AppTheme.primary,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    SettingsButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { showDialog = false },
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

/**
 * A labelled number stepper (− value +).
 *
 * The whole row is the one focus target, and LEFT/RIGHT move the value. It used to be two
 * separately focusable − and + buttons nested inside a bare Row — and on a real box neither
 * button could be reached: d-pad traversal stepped straight over the row, from the setting above
 * to the one below. So the control rendered, looked interactive, and could not be operated with a
 * remote at all. For Catch-up correction that meant the single setting able to fix an archive
 * running early or late was unreachable, which is very likely why it went unused for so long.
 *
 * One row, one focus stop, arrows to adjust — the same shape as every other settings row, and the
 * same left/right idiom the rest of the app uses for stepping and scrubbing.
 */
@Composable
fun SettingsStepperRow(
    title: String,
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    canDecrement: Boolean = true,
    canIncrement: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .settingsFocus(
                shape = SettingsShape.Row,
                onFocusChange = { focused = it },
            )
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.DirectionLeft -> if (canDecrement) { onDecrement(); true } else false
                    Key.DirectionRight -> if (canIncrement) { onIncrement(); true } else false
                    else -> false
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(title = title, subtitle = subtitle)
        Spacer(Modifier.width(16.dp))
        // The stepper draws as one visible control — a bordered segment like the dropdown value
        // chips — instead of bare glyphs that nearly vanished into the page background.
        Row(
            Modifier
                .clip(SettingsShape.Control)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Control)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperGlyph("−", enabled = canDecrement, muted = !canDecrement)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.widthIn(min = 88.dp).padding(horizontal = 4.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            StepperGlyph("+", enabled = canIncrement, muted = !canIncrement)
        }
    }
}

/**
 * The −/+ marks on a stepper row. Decorative: the row itself takes focus and LEFT/RIGHT adjust, so
 * these must not be focus stops or they would re-introduce the unreachable-targets bug.
 */
@Composable
private fun StepperGlyph(glyph: String, enabled: Boolean, muted: Boolean) {
    Box(
        Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Medium,
            color = if (muted) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Empty / nothing-here state, so a blank pane always explains itself. */
@Composable
fun SettingsEmptyState(
    title: String,
    message: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SettingsShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), SettingsShape.Card)
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(10.dp))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (message != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

/**
 * The one page scaffold. Gives every settings screen the same background, padding, header and back
 * affordance — the part ten screens used to copy by hand.
 */
@Composable
fun SettingsPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = stringResource(R.string.common_done),
    actions: @Composable RowScope.() -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val frame = Modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)
    Column(
        (if (scrollable) frame.verticalScroll(rememberScrollState()) else frame)
            .padding(horizontal = SettingsSpacing.PageHorizontal, vertical = SettingsSpacing.PageVertical),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 23.sp),
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actions()
            if (onBack != null) {
                Spacer(Modifier.width(12.dp))
                SettingsBackButton(onClick = onBack, label = backLabel)
            }
        }

        Spacer(Modifier.height(24.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}