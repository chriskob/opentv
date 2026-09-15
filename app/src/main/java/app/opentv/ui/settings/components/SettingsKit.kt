/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
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
val SettingsDanger = Color(0xFFFF6B6B)

/** Page rhythm, shared so every screen breathes the same way. */
object SettingsSpacing {
    val PageHorizontal = 40.dp
    val PageVertical = 28.dp
    val SectionGap = 22.dp
}

/**
 * The single focus treatment. Attach it to any focusable surface instead of hand-rolling another
 * `onFocusChanged` + `background` + `border` block.
 *
 * @param selected a persistent (not focus) selection state, e.g. the active accent or profile.
 * @param danger destructive controls ring in red rather than the accent.
 * @param focusScale how far the element lifts on focus; keep at 1f for rows inside a card so they
 *   do not overlap their neighbours.
 */
@Composable
fun Modifier.settingsFocus(
    shape: Shape = SettingsShape.Row,
    selected: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    focusScale: Float = 1f,
    onFocusChange: ((Boolean) -> Unit)? = null,
): Modifier {
    var focused by remember { mutableStateOf(false) }
    val ring = if (danger) SettingsDanger else AppTheme.primary

    val fill by animateColorAsState(
        targetValue = when {
            focused && danger -> SettingsDanger.copy(alpha = 0.16f)
            focused -> AppTheme.primary.copy(alpha = 0.14f)
            selected -> AppTheme.primary.copy(alpha = 0.10f)
            else -> Color.Transparent
        },
        animationSpec = tween(140),
        label = "settingsFocusFill",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            focused -> 1f
            selected -> 0.45f
            else -> 0f
        },
        animationSpec = tween(140),
        label = "settingsFocusRing",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) focusScale else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "settingsFocusScale",
    )

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .background(fill, shape)
        .border(width = if (focused) 2.dp else if (selected) 1.dp else 0.dp, color = ring.copy(alpha = ringAlpha), shape = shape)
        .alpha(if (enabled) 1f else 0.5f)
        .onFocusChanged {
            focused = it.isFocused
            onFocusChange?.invoke(it.isFocused)
        }
}

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
            tint = tint ?: if (focused) AppTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    width = 2.dp,
                    color = if (selected || focused) AppTheme.primary else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(AppTheme.primary))
            }
        }
        Spacer(Modifier.width(12.dp))
        SettingsRowText(title = title, subtitle = subtitle, emphasized = focused || selected)
    }
}

/** A raised settings card. */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(SettingsShape.Card)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SettingsShape.Card)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(2.dp),
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
    collapsible: Boolean = true,
    initiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (collapsible) {
            var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
            SettingsSectionHeader(title = title, icon = icon, expanded = expanded, onToggle = { expanded = !expanded })
            AnimatedVisibility(visible = expanded) {
                SettingsCard(content = content)
            }
        } else {
            SettingsSectionHeader(title = title, icon = icon, expanded = true, onToggle = null)
            SettingsCard(content = content)
        }
    }
}

@Composable
private fun SettingsSectionHeader(
    title: String,
    icon: ImageVector?,
    expanded: Boolean,
    onToggle: (() -> Unit)?,
) {
    val label: @Composable () -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(18.dp))
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.4.sp),
                fontWeight = FontWeight.Bold,
                color = AppTheme.primary,
            )
        }
    }

    if (onToggle == null) {
        Row(Modifier.padding(start = 4.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) { label() }
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
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) { label() }
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = if (focused) AppTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = rotation },
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(
            title = title,
            subtitle = subtitle,
            emphasized = focused,
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
    trailing: @Composable (() -> Unit)? = null,
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = if (expanded) 12.dp else 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(SettingsShape.Tile)
                    .background(tint.copy(alpha = if (focused || selected) 0.20f else 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
            if (expanded) Spacer(Modifier.width(12.dp))
        }
        if (expanded) {
            SettingsRowText(title = title, subtitle = subtitle, emphasized = focused || selected)
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
                    tint = if (focused) AppTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.SettingsRowText(title: String, subtitle: String?, emphasized: Boolean) {
    Column(Modifier.weight(1f)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
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
    val ring = if (danger) SettingsDanger else AppTheme.primary
    val shape = SettingsShape.Control

    val fill = when {
        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.4f)
        focused && danger -> SettingsDanger.copy(alpha = 0.22f)
        focused -> AppTheme.primary.copy(alpha = 0.22f)
        danger -> SettingsDanger.copy(alpha = 0.14f)
        style == SettingsButtonStyle.Primary -> AppTheme.primary
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val content = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        focused -> MaterialTheme.colorScheme.onSurface
        danger -> SettingsDanger
        style == SettingsButtonStyle.Primary -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val outline = when {
        focused -> ring
        danger -> SettingsDanger.copy(alpha = 0.45f)
        style == SettingsButtonStyle.Primary -> AppTheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val scale by animateFloatAsState(
        targetValue = if (focused && enabled) 1.04f else 1f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 700f),
        label = "settingsButtonScale",
    )

    Row(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .background(fill, shape)
            .border(if (focused) 2.dp else 1.dp, outline, shape)
            .alpha(if (enabled) 1f else 0.75f)
            .onFocusChanged { focused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp),
            fontWeight = FontWeight.SemiBold,
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
            Icon(Icons.Filled.Check, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(16.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) AppTheme.primary else MaterialTheme.colorScheme.onSurface,
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
            Box(
                Modifier
                    .weight(1f)
                    .settingsFocus(
                        shape = SettingsShape.Row,
                        selected = isSelected,
                        focusScale = 1.02f,
                    )
                    .focusable()
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onSelect(index) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) AppTheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    fontWeight = FontWeight.SemiBold,
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
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                    fontWeight = FontWeight.Bold,
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
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(20.dp))
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

/** A labelled number stepper (− value +). */
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
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsRowText(title = title, subtitle = subtitle, emphasized = false)
        Spacer(Modifier.width(16.dp))
        StepperButton("−", onDecrement, canDecrement)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(min = 64.dp).padding(horizontal = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        StepperButton("+", onIncrement, canIncrement)
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit, enabled: Boolean) {
    Box(
        Modifier
            .size(44.dp)
            .settingsFocus(shape = SettingsShape.Control, enabled = enabled, focusScale = 1.06f)
            .focusable(enabled)
            .clickable(enabled = enabled, indication = null, interactionSource = remember { MutableInteractionSource() }) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = glyph,
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = FontWeight.SemiBold,
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
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
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
