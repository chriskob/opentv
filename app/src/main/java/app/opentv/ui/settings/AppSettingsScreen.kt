/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.opentv.core.AppSettings
import app.opentv.core.SleepTimer

/**
 * Display & playback preferences: the app-behaviour settings, kept apart from the guide/data
 * settings so neither screen becomes a junk drawer. Everything here writes straight to
 * [AppSettings] and takes effect immediately.
 */
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val themeMode by settings.themeMode.collectAsState()
    val previewVideo by settings.guidePreviewVideo.collectAsState()
    val previewSound by settings.guidePreviewSound.collectAsState()
    val captions by settings.subtitlesEnabled.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Display & playback", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Done") }
        }

        Spacer(Modifier.height(20.dp))

        SettingsSection("Appearance") {
            ThemeOption("Follow system", themeMode == AppSettings.ThemeMode.SYSTEM) {
                settings.setThemeMode(AppSettings.ThemeMode.SYSTEM)
            }
            ThemeOption("Dark", themeMode == AppSettings.ThemeMode.DARK) {
                settings.setThemeMode(AppSettings.ThemeMode.DARK)
            }
            ThemeOption("Light", themeMode == AppSettings.ThemeMode.LIGHT) {
                settings.setThemeMode(AppSettings.ThemeMode.LIGHT)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "TV boxes stay dark on \"Follow system\". Pick Dark or Light to force it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection("Guide") {
            ToggleRow(
                title = "Live preview in the guide",
                subtitle = "Play the selected channel in the preview pane. Turn this off " +
                    "if the guide stutters on an older box.",
                checked = previewVideo,
                onToggle = settings::setGuidePreviewVideo,
            )
            ToggleRow(
                title = "Preview sound",
                subtitle = "Play the preview channel's audio too, not just the picture.",
                checked = previewSound,
                onToggle = settings::setGuidePreviewSound,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection("Playback") {
            ToggleRow(
                title = "Show subtitles when available",
                subtitle = "The default for new channels. While watching, the Subtitles button " +
                    "on the player lets you pick a specific track or turn them off.",
                checked = captions,
                onToggle = settings::setSubtitlesEnabled,
            )
        }

        Spacer(Modifier.height(16.dp))

        SleepTimerSection()
    }
}

@Composable
private fun SleepTimerSection() {
    val deadline by SleepTimer.deadline.collectAsState()
    val remaining = deadline?.let {
        val left = it - System.currentTimeMillis()
        if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }

    Text(
        "Sleep timer",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                if (remaining == null) "Off — playback keeps going until you stop it."
                else "On — playback will stop in about $remaining min.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            SleepOption("Off", selected = remaining == null) { SleepTimer.clear() }
            SleepTimer.presets.forEach { mins ->
                SleepOption("$mins minutes", selected = false) { SleepTimer.armMinutes(mins) }
            }
        }
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(8.dp))
    Card {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) { content() }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).widthIn(max = 640.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = onToggle)
    }
}
