/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.data.remote.WeatherClient
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.SettingsButton
import app.opentv.ui.settings.components.SettingsChoiceRow
import app.opentv.ui.settings.components.SettingsNavRow
import app.opentv.ui.settings.components.SettingsSection
import app.opentv.ui.settings.components.SettingsShape
import app.opentv.ui.settings.components.SettingsToggleRow
import app.opentv.ui.settings.components.settingsFocus
import app.opentv.ui.theme.AppPalette
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.palette

/** Persist the chosen language and recreate the activity so the whole UI reloads translated. */
internal fun changeAppLanguage(context: Context, settings: AppSettings, tag: String) {
    if (settings.languageTag.value == tag) return
    settings.setLanguageTag(tag)
    context.findActivity()?.recreate()
}

/**
 * Optional TMDB back-fill. The user pastes their own free key; it is stored on-device in
 * [AppSettings] and used to fill posters/backdrops/synopsis/cast a provider left blank.
 */
@Composable
internal fun TmdbKeySection(settings: AppSettings) {
    val context = LocalContext.current
    val savedKey by settings.tmdbApiKey.collectAsState()
    var field by remember(savedKey) { mutableStateOf(savedKey) }
    val savedMessage = stringResource(R.string.settings_tmdb_saved)

    SettingsSection(title = stringResource(R.string.settings_section_metadata), icon = Icons.Filled.Movie, initiallyExpanded = false) {
        Text(
            stringResource(R.string.settings_tmdb_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        TvOutlinedTextField(
            value = field,
            onValueChange = { field = it.trim() },
            singleLine = true,
            label = { Text(stringResource(R.string.settings_tmdb_key_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsButton(
                text = stringResource(R.string.settings_tmdb_save),
                onClick = {
                    settings.setTmdbApiKey(field)
                    Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                },
            )
            if (savedKey.isNotBlank()) {
                Spacer(Modifier.width(12.dp))
                Text(
                    stringResource(R.string.settings_tmdb_active),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Keyless player-header weather: an on/off toggle plus the US zip code it looks up.
 * Enabling without a valid zip is allowed — the header simply stays clock-only until
 * a zip is saved. Answering here also settles the first-run prompt for good.
 */
@Composable
internal fun WeatherZipSection(settings: AppSettings) {
    val context = LocalContext.current
    val enabled by settings.weatherEnabled.collectAsState()
    val savedZip by settings.weatherZip.collectAsState()
    var field by remember(savedZip) { mutableStateOf(savedZip) }
    var showError by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.settings_weather_saved)
    val updatingMessage = stringResource(R.string.settings_weather_updating)
    val needSetupMessage = stringResource(R.string.settings_weather_need_setup)
    // After typing the zip, the keyboard's Done key jumps focus straight to Save.
    // On a TV remote it is otherwise easy to get stranded inside the text field,
    // with D-pad Up/Down swallowed by the field instead of moving to the buttons.
    val saveFocus = remember { FocusRequester() }

    SettingsSection(title = stringResource(R.string.settings_section_weather), icon = Icons.Filled.WbSunny, initiallyExpanded = false) {
        Text(
            stringResource(R.string.settings_weather_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        SettingsToggleRow(
            title = stringResource(R.string.settings_weather_toggle),
            checked = enabled,
            onToggle = {
                settings.setWeatherEnabled(it)
                settings.weatherPromptAnswered = true
            },
        )
        Spacer(Modifier.height(8.dp))
        TvOutlinedTextField(
            value = field,
            onValueChange = {
                field = it.filter { c -> c.isDigit() || c == '-' }.take(10)
                showError = false
            },
            singleLine = true,
            isError = showError,
            supportingText = if (showError) {
                { Text(stringResource(R.string.weather_zip_invalid)) }
            } else null,
            label = { Text(stringResource(R.string.settings_weather_zip_label)) },
            placeholder = { Text(stringResource(R.string.weather_zip_hint)) },
            keyboardActions = KeyboardActions(
                onDone = { runCatching { saveFocus.requestFocus() } },
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (WeatherClient.isValidZip(field)) {
                        settings.setWeatherZip(field.trim())
                        settings.weatherPromptAnswered = true
                        Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
                    } else {
                        showError = true
                    }
                },
                modifier = Modifier.focusRequester(saveFocus),
            )
            SettingsButton(
                text = stringResource(R.string.settings_weather_update_now),
                onClick = {
                    // Refresh from whatever is in the field: a valid zip is saved
                    // first so Update now never refreshes a stale location.
                    if (WeatherClient.isValidZip(field)) {
                        settings.setWeatherZip(field.trim())
                        settings.weatherPromptAnswered = true
                        if (enabled) {
                            settings.requestWeatherRefresh()
                            Toast.makeText(context, updatingMessage, Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, needSetupMessage, Toast.LENGTH_LONG).show()
                        }
                    } else {
                        showError = true
                    }
                },
            )
        }
    }
}

/** A sleep-timer header row that expands to the preset choices. */
@Composable
internal fun SleepTimerSection() {
    val deadline by SleepTimer.deadline.collectAsState()
    val remaining = deadline?.let {
        val left = it - System.currentTimeMillis()
        if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }
    var expanded by remember { mutableStateOf(false) }

    SettingsSection(title = stringResource(R.string.settings_sleep_timer), icon = Icons.Filled.Bedtime, initiallyExpanded = false) {
        SettingsNavRow(
            title = if (remaining == null) stringResource(R.string.settings_sleep_off)
            else stringResource(R.string.settings_sleep_minutes, remaining),
            subtitle = if (remaining == null) stringResource(R.string.settings_sleep_off_desc)
            else stringResource(R.string.settings_sleep_on_desc, remaining),
            onClick = { expanded = !expanded },
            tint = AppTheme.primary,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (remaining != null) {
                        Box(
                            Modifier
                                .clip(SettingsShape.Row)
                                .background(AppTheme.primary.copy(alpha = 0.15f))
                                .border(1.dp, AppTheme.primary.copy(alpha = 0.5f), SettingsShape.Row)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "${remaining}m",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = AppTheme.primary,
                            )
                        }
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AppTheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                SettingsChoiceRow(
                    title = stringResource(R.string.settings_sleep_off),
                    selected = remaining == null,
                    onSelect = { SleepTimer.clear() },
                )
                SleepTimer.presets.forEach { mins ->
                    SettingsChoiceRow(
                        title = stringResource(R.string.settings_sleep_minutes, mins),
                        selected = false,
                        onSelect = { SleepTimer.armMinutes(mins) },
                    )
                }
            }
        }
    }
}

/** One palette chip: a miniature of the theme's background / surface / accent, with its name. */
@Composable
internal fun ThemePalettePill(
    palette: AppPalette,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val colors = palette.palette()
    val swatchShape = RoundedCornerShape(6.dp)

    Row(
        Modifier
            .settingsFocus(
                shape = SettingsShape.Row,
                selected = selected,
                onFocusChange = { focused = it },
            )
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier
                .width(30.dp)
                .height(20.dp)
                .clip(swatchShape)
                .border(
                    width = if (focused || selected) 1.5.dp else 1.dp,
                    color = when {
                        focused -> Color.White
                        selected -> colors.primary
                        else -> colors.outline
                    },
                    shape = swatchShape,
                ),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().background(colors.background))
            Box(Modifier.weight(1f).fillMaxHeight().background(colors.surface))
            Box(Modifier.weight(1f).fillMaxHeight().background(colors.primary))
        }

        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp),
            fontWeight = FontWeight.Medium,
            color = when {
                // The active palette is a persistent pill, so its label is onSurface white; the
                // swatch beside it is what carries the colours.
                focused || selected -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}
