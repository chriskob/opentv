/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.content.Context
import android.widget.Toast
import app.opentv.core.OpenTvLanguages
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.cardFocusBg
import app.opentv.ui.theme.displayName
import app.opentv.ui.theme.primary
import app.opentv.ui.theme.dark
import app.opentv.ui.theme.light
import app.opentv.ui.theme.highlightGlow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import app.opentv.ui.components.TvOutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.SleepTimer
import app.opentv.core.findActivity
import app.opentv.data.work.SyncWorker

/**
 * Display & playback preferences: the app-behaviour settings, kept apart from the guide/data
 * settings so neither screen becomes a junk drawer. Everything here writes straight to
 * [AppSettings] and takes effect immediately.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val currentAccent by settings.accentColor.collectAsState()
    val themeMode by settings.themeMode.collectAsState()
    val channelLayout by settings.channelLayout.collectAsState()
    val previewVideo by settings.guidePreviewVideo.collectAsState()
    val showFavouritesCategory by settings.showFavouritesCategory.collectAsState()
    val showAllChannelsCategory by settings.showAllChannelsCategory.collectAsState()
    val catchupLookup by settings.catchupDiscovery.collectAsState()
    val previewSound by settings.guidePreviewSound.collectAsState()
    val guideResetOnOpen by settings.guideResetOnOpen.collectAsState()
    val captions by settings.subtitlesEnabled.collectAsState()
    val resumeLast by settings.resumeLastChannel.collectAsState()
    val pipOnHome by settings.pipOnHomeEnabled.collectAsState()
    val matchRefreshRate by settings.matchRefreshRate.collectAsState()
    val language by settings.languageTag.collectAsState()
    val liveEnabled by settings.liveEnabled.collectAsState()
    val moviesEnabled by settings.moviesEnabled.collectAsState()
    val seriesEnabled by settings.seriesEnabled.collectAsState()
    val playlistRefreshHours by settings.playlistRefreshHours.collectAsState()
    val epgRefreshHours by settings.epgRefreshHours.collectAsState()
    val epgSyncWithPlaylist by settings.epgSyncWithPlaylist.collectAsState()
    val enabledSubMenuButtons by settings.enabledSubMenuButtons.collectAsState()
    val enabledVodButtons by settings.enabledVodButtons.collectAsState()

    // A refresh next to each content toggle kicks a full catalogue re-sync (which now honours the
    // toggles, so a type just switched on is fetched). Runs as background work so it isn't cut short
    // if you leave this screen; a quick Toast confirms it started.
    val refreshingMessage = stringResource(R.string.settings_content_refreshing)
    val onRefreshContent: () -> Unit = {
        SyncWorker.refreshNow(context)
        Toast.makeText(context, refreshingMessage, Toast.LENGTH_SHORT).show()
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_display_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Themes, layouts, playback preferences & data intervals",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            SettingsBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        SettingsSection(stringResource(R.string.settings_appearance), Icons.Filled.Palette) {
            Text(
                text = "Accent Color",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Select a signature color for highlights, focus borders, badges, and buttons throughout OpenTV.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            // FlowRow: with 11 accents the pills wrap onto a second line on narrow panels
            // instead of overflowing the screen edge.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppSettings.AccentColor.entries.forEach { accent ->
                    AccentColorPill(
                        accent = accent,
                        selected = accent == currentAccent,
                        onSelect = { settings.setAccentColor(accent) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_content), Icons.Filled.VideoLibrary) {
            Text(
                stringResource(R.string.settings_content_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            ContentToggleRow(
                title = stringResource(R.string.nav_live_tv),
                subtitle = stringResource(R.string.settings_content_live_subtitle),
                checked = liveEnabled,
                onToggle = settings::setLiveEnabled,
                onRefresh = onRefreshContent,
            )
            ContentToggleRow(
                title = stringResource(R.string.nav_movies),
                subtitle = stringResource(R.string.settings_content_movies_subtitle),
                checked = moviesEnabled,
                onToggle = settings::setMoviesEnabled,
                onRefresh = onRefreshContent,
            )
            ContentToggleRow(
                title = stringResource(R.string.nav_shows),
                subtitle = stringResource(R.string.settings_content_series_subtitle),
                checked = seriesEnabled,
                onToggle = settings::setSeriesEnabled,
                onRefresh = onRefreshContent,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_data_refresh), Icons.Filled.Sync) {
            Text(
                stringResource(R.string.settings_data_refresh_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val playlistRefreshOptions = listOf(
                stringResource(R.string.settings_refresh_2h) to 2,
                stringResource(R.string.settings_refresh_4h) to 4,
                stringResource(R.string.settings_refresh_6h) to 6,
                stringResource(R.string.settings_refresh_8h) to 8,
                stringResource(R.string.settings_refresh_12h) to 12,
                stringResource(R.string.settings_refresh_24h) to 24,
                stringResource(R.string.settings_refresh_manual) to 0,
            )

            DropdownPickerRow(
                title = stringResource(R.string.settings_playlist_refresh),
                subtitle = "Frequency to check playlists for channel and VOD updates",
                options = playlistRefreshOptions,
                selectedValue = playlistRefreshHours,
            ) { hours ->
                settings.setPlaylistRefreshHours(hours)
                SyncWorker.schedule(context, hours)
            }

            Spacer(Modifier.height(8.dp))

            DropdownPickerRow(
                title = stringResource(R.string.settings_guide_refresh),
                subtitle = "Frequency to fetch fresh TV guide and programme data",
                options = playlistRefreshOptions,
                selectedValue = epgRefreshHours,
            ) { hours -> settings.setEpgRefreshHours(hours) }

            Spacer(Modifier.height(12.dp))

            // ---- Sync guide with playlist toggle
            ToggleRow(
                title = stringResource(R.string.settings_epg_sync_with_playlist_title),
                subtitle = stringResource(R.string.settings_epg_sync_with_playlist_subtitle),
                checked = epgSyncWithPlaylist,
                onToggle = settings::setEpgSyncWithPlaylist,
            )

            Spacer(Modifier.height(12.dp))

            // ---- Refresh now button
            val refreshNowMessage = stringResource(R.string.settings_refresh_now_toast)
            OutlinedButton(onClick = {
                SyncWorker.refreshNow(context)
                Toast.makeText(context, refreshNowMessage, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.settings_refresh_now)) }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_language), Icons.Filled.Language) {
            val languageOptions = buildList {
                add(stringResource(R.string.settings_language_system) to "")
                OpenTvLanguages.entries.forEach { (tag, name) -> add(name to tag) }
            }
            DropdownPickerRow(
                title = stringResource(R.string.settings_section_language),
                subtitle = stringResource(R.string.settings_language_note),
                options = languageOptions,
                selectedValue = language,
            ) { tag ->
                changeLanguage(context, settings, tag)
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_guide), Icons.Filled.LiveTv) {
            val layoutOptions = listOf(
                stringResource(R.string.settings_channel_layout_grid),
                stringResource(R.string.settings_channel_layout_list),
            )
            val layoutIndex = if (channelLayout == AppSettings.ChannelLayout.GRID) 0 else 1
            SegmentedSelector(layoutOptions, layoutIndex) { idx ->
                settings.setChannelLayout(if (idx == 0) AppSettings.ChannelLayout.GRID else AppSettings.ChannelLayout.LIST)
            }
            Spacer(Modifier.height(8.dp))
            ToggleRow(
                title = stringResource(R.string.settings_live_preview_title),
                subtitle = stringResource(R.string.settings_live_preview_subtitle),
                checked = previewVideo,
                onToggle = settings::setGuidePreviewVideo,
            )
            ToggleRow(
                title = stringResource(R.string.settings_preview_sound_title),
                subtitle = stringResource(R.string.settings_preview_sound_subtitle),
                checked = previewSound,
                onToggle = settings::setGuidePreviewSound,
            )
            ToggleRow(
                title = stringResource(R.string.settings_guide_reset_on_open_title),
                subtitle = stringResource(R.string.settings_guide_reset_on_open_subtitle),
                checked = guideResetOnOpen,
                onToggle = settings::setGuideResetOnOpen,
            )
            ToggleRow(
                title = stringResource(R.string.settings_show_fav_category_title),
                subtitle = stringResource(R.string.settings_show_fav_category_subtitle),
                checked = showFavouritesCategory,
                onToggle = settings::setShowFavouritesCategory,
            )
            ToggleRow(
                title = stringResource(R.string.settings_show_all_category_title),
                subtitle = stringResource(R.string.settings_show_all_category_subtitle),
                checked = showAllChannelsCategory,
                onToggle = settings::setShowAllChannelsCategory,
            )
            ToggleRow(
                title = stringResource(R.string.settings_catchup_lookup_title),
                subtitle = stringResource(R.string.settings_catchup_lookup_subtitle),
                checked = catchupLookup,
                onToggle = settings::setCatchupDiscovery,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_playback), Icons.Filled.PlayCircle) {
            ToggleRow(
                title = stringResource(R.string.settings_subtitles_title),
                subtitle = stringResource(R.string.settings_subtitles_subtitle),
                checked = captions,
                onToggle = settings::setSubtitlesEnabled,
            )
            ToggleRow(
                title = stringResource(R.string.settings_resume_title),
                subtitle = stringResource(R.string.settings_resume_subtitle),
                checked = resumeLast,
                onToggle = settings::setResumeLastChannel,
            )
            ToggleRow(
                title = stringResource(R.string.settings_pip_on_home_title),
                subtitle = stringResource(R.string.settings_pip_on_home_subtitle),
                checked = pipOnHome,
                onToggle = settings::setPipOnHomeEnabled,
            )
            ToggleRow(
                title = stringResource(R.string.settings_match_refresh_title),
                subtitle = stringResource(R.string.settings_match_refresh_subtitle),
                checked = matchRefreshRate,
                onToggle = settings::setMatchRefreshRate,
            )
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(stringResource(R.string.settings_section_submenu_buttons), Icons.Filled.Tune) {
            Text(
                stringResource(R.string.settings_submenu_buttons_note),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(
                    text = stringResource(R.string.settings_submenu_enable_all),
                    isPrimary = true,
                    onClick = { settings.setAllSubMenuButtons(true) },
                )
                SettingsActionButton(
                    text = stringResource(R.string.settings_submenu_disable_all),
                    isPrimary = false,
                    onClick = { settings.setAllSubMenuButtons(false) },
                )
            }

            Spacer(Modifier.height(10.dp))

            AppSettings.SubMenuButton.entries.forEach { btn ->
                val isEnabled = enabledSubMenuButtons.contains(btn)
                ToggleRow(
                    title = stringResource(btn.titleRes),
                    subtitle = btn.subtitle,
                    checked = isEnabled,
                    onToggle = { settings.setSubMenuButtonEnabled(btn, it) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        // The movie/show player has its own button set: record, multiview, favourites and the rest
        // of the live row mean nothing on a film, so they are configured separately rather than
        // shared. See AppSettings.VodPlayerButton.
        SettingsSection(stringResource(R.string.settings_section_vod_buttons), Icons.Filled.Movie) {
            Text(
                stringResource(R.string.settings_vod_buttons_note),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsActionButton(
                    text = stringResource(R.string.settings_submenu_enable_all),
                    isPrimary = true,
                    onClick = { settings.setAllVodButtons(true) },
                )
                SettingsActionButton(
                    text = stringResource(R.string.settings_submenu_disable_all),
                    isPrimary = false,
                    onClick = { settings.setAllVodButtons(false) },
                )
            }

            Spacer(Modifier.height(10.dp))

            AppSettings.VodPlayerButton.entries.forEach { btn ->
                ToggleRow(
                    title = stringResource(btn.titleRes),
                    subtitle = btn.subtitle,
                    checked = enabledVodButtons.contains(btn),
                    onToggle = { settings.setVodButtonEnabled(btn, it) },
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        TmdbKeySection(settings)

        Spacer(Modifier.height(16.dp))

        SleepTimerSection()
    }
}

/**
 * Optional TMDB back-fill. The user pastes their own free key; it is stored on-device in
 * [AppSettings] and used to fill posters/backdrops/synopsis/cast a provider left blank. Empty by
 * default, so the feature is off until opted into.
 */
@Composable
private fun TmdbKeySection(settings: AppSettings) {
    val context = LocalContext.current
    val savedKey by settings.tmdbApiKey.collectAsState()
    var field by remember(savedKey) { mutableStateOf(savedKey) }
    val savedMessage = stringResource(R.string.settings_tmdb_saved)

    SettingsSection(stringResource(R.string.settings_section_metadata), Icons.Filled.Movie) {
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
            OutlinedButton(onClick = {
                settings.setTmdbApiKey(field)
                Toast.makeText(context, savedMessage, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.settings_tmdb_save)) }
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

/** Persist the chosen language and recreate the activity so the whole UI reloads translated. */
private fun changeLanguage(context: Context, settings: AppSettings, tag: String) {
    if (settings.languageTag.value == tag) return
    settings.setLanguageTag(tag)
    context.findActivity()?.recreate()
}

@Composable
private fun SleepTimerSection() {
    val deadline by SleepTimer.deadline.collectAsState()
    val remaining = deadline?.let {
        val left = it - System.currentTimeMillis()
        if (left <= 0L) 0 else ((left + 59_999L) / 60_000L).toInt()
    }
    var expanded by remember { mutableStateOf(false) }
    var headerFocused by remember { mutableStateOf(false) }

    SettingsSection(stringResource(R.string.settings_sleep_timer), Icons.Filled.Bedtime) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { headerFocused = it.isFocused }
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (headerFocused) AppTheme.cardFocusBg
                        else Color(0xFF1B2632)
                    )
                    .then(
                        if (headerFocused) Modifier.border(2.dp, AppTheme.primary, RoundedCornerShape(8.dp))
                        else Modifier.border(0.75.dp, Color(0xFF263442), RoundedCornerShape(8.dp))
                    )
                    .focusable()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_sleep_timer),
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                        fontWeight = if (headerFocused) FontWeight.Bold else FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (remaining == null) stringResource(R.string.settings_sleep_off_desc)
                        else stringResource(R.string.settings_sleep_on_desc, remaining),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        color = if (remaining != null) AppTheme.primary else Color(0xFFB0BEC5),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (remaining != null) AppTheme.dark.copy(alpha = 0.6f) else Color(0xFF243242))
                            .border(
                                1.dp,
                                if (remaining != null) AppTheme.primary else Color(0xFF37474F),
                                RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (remaining != null) "${remaining}m remaining" else "Off",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (remaining != null) AppTheme.light else Color(0xFF90A4AE),
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        tint = if (headerFocused) Color.White else AppTheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SleepOption(stringResource(R.string.settings_sleep_off), selected = remaining == null) {
                        SleepTimer.clear()
                    }
                    SleepTimer.presets.forEach { mins ->
                        SleepOption(stringResource(R.string.settings_sleep_minutes, mins), selected = false) {
                            SleepTimer.armMinutes(mins)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccentColorPill(
    accent: AppSettings.AccentColor,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val primaryColor = accent.primary

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    focused -> accent.cardFocusBg
                    selected -> Color(0xFF1E2834)
                    else -> Color(0xFF161F28)
                }
            )
            .border(
                width = if (focused) 2.dp else if (selected) 1.5.dp else 0.75.dp,
                color = when {
                    focused -> primaryColor
                    selected -> primaryColor.copy(alpha = 0.8f)
                    else -> Color(0xFF263442)
                },
                shape = RoundedCornerShape(12.dp),
            )
            .onFocusChanged { focused = it.isFocused }
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
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(accent.light, primaryColor),
                    ),
                )
                .border(
                    width = if (focused || selected) 1.5.dp else 1.dp,
                    color = if (focused) Color.White else Color.Black.copy(alpha = 0.35f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF0D141C),
                    modifier = Modifier.size(12.dp),
                )
            }
        }

        Text(
            text = accent.displayName,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.sp),
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.Medium,
            color = if (focused) Color.White else if (selected) primaryColor else Color(0xFFCFD8DC),
        )
    }
}

@Composable
private fun SleepOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) AppTheme.cardFocusBg
                else if (selected) Color(0xFF1E2F3E)
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.primary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else if (selected) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = if (focused) AppTheme.dark else AppTheme.primary,
                unselectedColor = if (focused) Color(0xFF37474F) else Color(0xFF90A4AE),
            ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
        )
    }
}

@Composable
private fun SettingsSection(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        if (icon != null) {
            Icon(imageVector = icon, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.Bold,
            color = AppTheme.primary,
        )
    }
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) { content() }
    }
}

@Composable
private fun ThemeOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) AppTheme.cardFocusBg
                else if (selected) Color(0xFF1E2F3E)
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.primary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else if (selected) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            colors = androidx.compose.material3.RadioButtonDefaults.colors(
                selectedColor = if (focused) AppTheme.dark else AppTheme.primary,
                unselectedColor = if (focused) Color(0xFF37474F) else Color(0xFF90A4AE),
            ),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Normal,
            color = Color.White,
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) AppTheme.cardFocusBg
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.primary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (focused) {
            Box(Modifier.width(3.dp).height(24.dp).background(AppTheme.primary))
            Spacer(Modifier.width(9.dp))
        }
        Column(Modifier.weight(1f).widthIn(max = 640.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (focused) Color(0xFFB0BEC5) else Color.White.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = AppTheme.primary,
                checkedTrackColor = AppTheme.dark.copy(alpha = 0.55f),
                // Gray→white track when off (was accent-tinted): a switch's OFF state should
                // read as neutral "inactive", not as a colored highlight.
                uncheckedThumbColor = Color(0xFFB0BEC5),
                uncheckedTrackColor = Color(0xFF37474F),
            ),
        )
    }
}

/**
 * A [ToggleRow] with a refresh button in front of the switch: toggle the content type on/off, or
 * tap refresh to re-sync it now. Only the label column toggles on tap, so the refresh button and
 * switch stay independently focusable for d-pad users.
 */
@Composable
private fun ContentToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) AppTheme.cardFocusBg
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.primary.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                else Modifier,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (focused) {
            Box(Modifier.width(3.dp).height(24.dp).background(AppTheme.primary))
            Spacer(Modifier.width(9.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
                .clickable { onToggle(!checked) },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (focused) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (focused) Color(0xFFB0BEC5) else Color.White.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.width(8.dp))
        IconButton(onClick = onRefresh) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = stringResource(R.string.settings_content_refresh),
                tint = if (focused) AppTheme.dark else AppTheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedThumbColor = AppTheme.primary,
                checkedTrackColor = AppTheme.dark.copy(alpha = 0.55f),
                uncheckedThumbColor = Color(0xFFB0BEC5),
                uncheckedTrackColor = Color(0xFF37474F),
            ),
        )
    }
}

@Composable
private fun SettingsBackButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary))
                else androidx.compose.ui.graphics.SolidColor(Color(0xFF1E2833)),
            )
            .then(
                if (focused) Modifier.border(2.dp, AppTheme.light, RoundedCornerShape(10.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(10.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun SettingsActionButton(
    text: String,
    isPrimary: Boolean,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        focused -> AppTheme.primary
        isPrimary -> AppTheme.dark
        else -> Color(0xFF1E2833)
    }
    val fg = when {
        focused -> Color.White
        isPrimary -> AppTheme.light
        else -> Color(0xFFB0BEC5)
    }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else if (isPrimary) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                else Modifier.border(1.dp, Color(0xFF2C3E50), RoundedCornerShape(8.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (focused || isPrimary) FontWeight.Bold else FontWeight.Medium,
            color = fg,
        )
    }
}

@Composable
private fun SegmentedSelector(options: List<String>, selectedIndex: Int, accentColor: Color = AppTheme.primary, onSelect: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1B2632))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(12.dp)),
    ) {
        options.forEachIndexed { index, option ->
            val isSelected = index == selectedIndex
            var isFocused by remember { mutableStateOf(false) }
            
            Box(
                Modifier
                    .weight(1f)
                    .onFocusChanged { isFocused = it.isFocused }
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isFocused) accentColor.copy(alpha = 0.3f)
                        else if (isSelected) accentColor
                        else Color.Transparent
                    )
                    .then(
                        if (isFocused) Modifier.border(2.dp, accentColor, RoundedCornerShape(10.dp))
                        else Modifier
                    )
                    .focusable()
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = option,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isFocused) Color.White else if (isSelected) Color(0xFF0E1620) else Color(0xFF8B9BA8)
                )
            }
        }
    }
}

@Composable
private fun <T> DropdownPickerRow(
    title: String,
    subtitle: String,
    options: List<Pair<String, T>>,
    selectedValue: T,
    onSelect: (T) -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.second == selectedValue }?.first ?: "$selectedValue"

    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { isFocused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isFocused) AppTheme.cardFocusBg
                else Color.Transparent,
            )
            .then(
                if (isFocused) Modifier.border(2.dp, AppTheme.primary, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .clickable { showDialog = true }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isFocused) {
            Box(Modifier.width(3.dp).height(24.dp).background(AppTheme.primary))
            Spacer(Modifier.width(9.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = 640.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (isFocused) FontWeight.Bold else FontWeight.SemiBold,
                color = Color.White,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (isFocused) Color(0xFFB0BEC5) else Color.White.copy(alpha = 0.65f),
            )
        }
        Spacer(Modifier.width(16.dp))
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(if (isFocused) AppTheme.primary.copy(alpha = 0.25f) else Color(0xFF1B2632))
                .border(
                    if (isFocused) 1.5.dp else 0.75.dp,
                    if (isFocused) AppTheme.primary else Color(0xFF263442),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = currentLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isFocused) Color.White else AppTheme.primary,
            )
            Icon(
                imageVector = Icons.Filled.UnfoldMore,
                contentDescription = null,
                tint = if (isFocused) Color.White else AppTheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF161F28),
            shape = RoundedCornerShape(16.dp),
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(options) { option ->
                        val isSelected = option.second == selectedValue
                        var itemFocused by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { itemFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        itemFocused -> AppTheme.cardFocusBg
                                        isSelected -> Color(0xFF1E2834)
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (itemFocused) Modifier.border(2.dp, AppTheme.primary, RoundedCornerShape(8.dp))
                                    else if (isSelected) Modifier.border(1.dp, AppTheme.primary.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    else Modifier
                                )
                                .focusable()
                                .clickable {
                                    onSelect(option.second)
                                    showDialog = false
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.first,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSelected || itemFocused) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    itemFocused -> Color.White
                                    isSelected -> AppTheme.primary
                                    else -> Color(0xFFCFD8DC)
                                }
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = AppTheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = AppTheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        )
    }
}
