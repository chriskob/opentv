/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.OpenTvLanguages
import app.opentv.data.work.SyncWorker
import app.opentv.ui.settings.components.SettingsButton
import app.opentv.ui.settings.components.SettingsButtonStyle
import app.opentv.ui.settings.components.SettingsChip
import app.opentv.ui.settings.components.SettingsDropdown
import app.opentv.ui.settings.components.SettingsIconButton
import app.opentv.ui.settings.components.SettingsPage
import app.opentv.ui.settings.components.SettingsSection
import app.opentv.ui.settings.components.SettingsSegmented
import app.opentv.ui.settings.components.SettingsSpacing
import app.opentv.ui.settings.components.SettingsStepperRow
import app.opentv.ui.settings.components.SettingsToggleRow
import app.opentv.ui.theme.AppPalette

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
    val currentPalette by settings.palette.collectAsState()
    val uiTransparency by settings.uiTransparencyPercent.collectAsState()
    val channelLayout by settings.channelLayout.collectAsState()
    val previewVideo by settings.guidePreviewVideo.collectAsState()
    val showFavouritesCategory by settings.showFavouritesCategory.collectAsState()
    val catchupLookup by settings.catchupDiscovery.collectAsState()
    val catchupEnabled by settings.catchupEnabled.collectAsState()
    val catchupDays by settings.catchupDaysOverride.collectAsState()
    val catchupCorrection by settings.catchupCorrectionMin.collectAsState()
    val catchupSkip by settings.catchupSkipSec.collectAsState()
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
    val refreshTrailing: @Composable () -> Unit = {
        SettingsIconButton(
            icon = Icons.Filled.Refresh,
            onClick = onRefreshContent,
            contentDescription = stringResource(R.string.settings_content_refresh),
        )
    }

    SettingsPage(
        title = stringResource(R.string.settings_display_title),
        subtitle = stringResource(R.string.settings_display_page_subtitle),
        onBack = onBack,
    ) {
        SettingsSection(title = stringResource(R.string.settings_appearance), icon = Icons.Filled.Palette) {
            Text(
                text = stringResource(R.string.settings_palette_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.settings_palette_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            // FlowRow: the palettes wrap onto a second line on narrow panels instead of
            // overflowing the screen edge.
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AppPalette.entries.forEach { palette ->
                    ThemePalettePill(
                        palette = palette,
                        selected = palette == currentPalette,
                        onSelect = { settings.setPalette(palette) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            SettingsStepperRow(
                title = stringResource(R.string.settings_ui_transparency),
                subtitle = stringResource(R.string.settings_ui_transparency_desc),
                value = stringResource(R.string.settings_ui_transparency_value, uiTransparency),
                onDecrement = { settings.setUiTransparencyPercent(uiTransparency - 10) },
                onIncrement = { settings.setUiTransparencyPercent(uiTransparency + 10) },
                canDecrement = uiTransparency > 0,
                canIncrement = uiTransparency < 80,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_content), icon = Icons.Filled.VideoLibrary, initiallyExpanded = false) {
            Text(
                stringResource(R.string.settings_content_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            SettingsToggleRow(
                title = stringResource(R.string.nav_live_tv),
                subtitle = stringResource(R.string.settings_content_live_subtitle),
                checked = liveEnabled,
                onToggle = settings::setLiveEnabled,
                trailing = refreshTrailing,
            )
            SettingsToggleRow(
                title = stringResource(R.string.nav_movies),
                subtitle = stringResource(R.string.settings_content_movies_subtitle),
                checked = moviesEnabled,
                onToggle = settings::setMoviesEnabled,
                trailing = refreshTrailing,
            )
            SettingsToggleRow(
                title = stringResource(R.string.nav_shows),
                subtitle = stringResource(R.string.settings_content_series_subtitle),
                checked = seriesEnabled,
                onToggle = settings::setSeriesEnabled,
                trailing = refreshTrailing,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_data_refresh), icon = Icons.Filled.Sync, initiallyExpanded = false) {
            Text(
                stringResource(R.string.settings_data_refresh_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            val refreshOptions = listOf(
                stringResource(R.string.settings_refresh_2h) to 2,
                stringResource(R.string.settings_refresh_4h) to 4,
                stringResource(R.string.settings_refresh_6h) to 6,
                stringResource(R.string.settings_refresh_8h) to 8,
                stringResource(R.string.settings_refresh_12h) to 12,
                stringResource(R.string.settings_refresh_24h) to 24,
                stringResource(R.string.settings_refresh_manual) to 0,
            )

            val guideHint = stringResource(R.string.settings_guide_refresh_hint)
            val lastGuide = settings.lastGuideUpdatedMillis
            val episodeCount = settings.lastGuideChannelCount
            val guideSubtitle = if (lastGuide > 0L) {
                val stamp = remember(lastGuide) {
                    java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                        .format(java.util.Date(lastGuide))
                }
                guideHint + "\n" + stringResource(R.string.settings_guide_last_updated, stamp, episodeCount)
            } else {
                guideHint
            }

            SettingsDropdown(
                title = stringResource(R.string.settings_playlist_refresh),
                subtitle = stringResource(R.string.settings_playlist_refresh_hint),
                options = refreshOptions,
                selectedValue = playlistRefreshHours,
                onSelect = { hours ->
                    settings.setPlaylistRefreshHours(hours)
                    SyncWorker.schedule(context, hours)
                },
            )

            SettingsDropdown(
                title = stringResource(R.string.settings_guide_refresh),
                subtitle = guideSubtitle,
                options = refreshOptions,
                selectedValue = epgRefreshHours,
                onSelect = { hours -> settings.setEpgRefreshHours(hours) },
            )

            SettingsToggleRow(
                title = stringResource(R.string.settings_epg_sync_with_playlist_title),
                subtitle = stringResource(R.string.settings_epg_sync_with_playlist_subtitle),
                checked = epgSyncWithPlaylist,
                onToggle = settings::setEpgSyncWithPlaylist,
            )

            Spacer(Modifier.height(6.dp))
            val refreshNowMessage = stringResource(R.string.settings_refresh_now_toast)
            SettingsButton(
                text = stringResource(R.string.settings_refresh_now),
                icon = Icons.Filled.Refresh,
                onClick = {
                    SyncWorker.refreshNow(context)
                    Toast.makeText(context, refreshNowMessage, Toast.LENGTH_SHORT).show()
                },
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_language), icon = Icons.Filled.Language, initiallyExpanded = false) {
            val languageOptions = buildList {
                add(stringResource(R.string.settings_language_system) to "")
                OpenTvLanguages.entries.forEach { (tag, name) -> add(name to tag) }
            }
            SettingsDropdown(
                title = stringResource(R.string.settings_section_language),
                subtitle = stringResource(R.string.settings_language_note),
                options = languageOptions,
                selectedValue = language,
                onSelect = { tag -> changeAppLanguage(context, settings, tag) },
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_guide), icon = Icons.Filled.LiveTv, initiallyExpanded = false) {
            val layoutOptions = listOf(
                stringResource(R.string.settings_channel_layout_grid),
                stringResource(R.string.settings_channel_layout_list),
            )
            val layoutIndex = if (channelLayout == AppSettings.ChannelLayout.GRID) 0 else 1
            SettingsSegmented(
                options = layoutOptions,
                selectedIndex = layoutIndex,
                onSelect = { idx ->
                    settings.setChannelLayout(if (idx == 0) AppSettings.ChannelLayout.GRID else AppSettings.ChannelLayout.LIST)
                },
            )
            Spacer(Modifier.height(6.dp))
            SettingsToggleRow(
                title = stringResource(R.string.settings_live_preview_title),
                subtitle = stringResource(R.string.settings_live_preview_subtitle),
                checked = previewVideo,
                onToggle = settings::setGuidePreviewVideo,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_preview_sound_title),
                subtitle = stringResource(R.string.settings_preview_sound_subtitle),
                checked = previewSound,
                onToggle = settings::setGuidePreviewSound,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_guide_reset_on_open_title),
                subtitle = stringResource(R.string.settings_guide_reset_on_open_subtitle),
                checked = guideResetOnOpen,
                onToggle = settings::setGuideResetOnOpen,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_show_fav_category_title),
                subtitle = stringResource(R.string.settings_show_fav_category_subtitle),
                checked = showFavouritesCategory,
                onToggle = settings::setShowFavouritesCategory,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_catchup_lookup_title),
                subtitle = stringResource(R.string.settings_catchup_lookup_subtitle),
                checked = catchupLookup,
                onToggle = settings::setCatchupDiscovery,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_catchup), icon = Icons.Filled.LiveTv, initiallyExpanded = false) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_catchup_enable_title),
                subtitle = stringResource(R.string.settings_catchup_enable_subtitle),
                checked = catchupEnabled,
                onToggle = settings::setCatchupEnabled,
            )
            SettingsDropdown(
                title = stringResource(R.string.settings_catchup_days_title),
                subtitle = stringResource(R.string.settings_catchup_days_subtitle),
                options = listOf(
                    stringResource(R.string.settings_catchup_days_provider) to -1,
                    "1" to 1,
                    "2" to 2,
                    "3" to 3,
                ),
                selectedValue = catchupDays,
                onSelect = settings::setCatchupDaysOverride,
            )
            SettingsStepperRow(
                title = stringResource(R.string.settings_catchup_correction_title),
                subtitle = stringResource(R.string.settings_catchup_correction_subtitle),
                value = if (catchupCorrection >= 0) "+$catchupCorrection min" else "$catchupCorrection min",
                onDecrement = { settings.setCatchupCorrectionMin(catchupCorrection - 1) },
                onIncrement = { settings.setCatchupCorrectionMin(catchupCorrection + 1) },
                canDecrement = catchupCorrection > -30,
                canIncrement = catchupCorrection < 30,
            )
            SettingsDropdown(
                title = stringResource(R.string.settings_catchup_skip_title),
                subtitle = stringResource(R.string.settings_catchup_skip_subtitle),
                options = listOf("5s" to 5, "10s" to 10, "20s" to 20, "30s" to 30, "60s" to 60),
                selectedValue = catchupSkip,
                onSelect = settings::setCatchupSkipSec,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_playback), icon = Icons.Filled.PlayCircle, initiallyExpanded = false) {
            SettingsToggleRow(
                title = stringResource(R.string.settings_subtitles_title),
                subtitle = stringResource(R.string.settings_subtitles_subtitle),
                checked = captions,
                onToggle = settings::setSubtitlesEnabled,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_resume_title),
                subtitle = stringResource(R.string.settings_resume_subtitle),
                checked = resumeLast,
                onToggle = settings::setResumeLastChannel,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_pip_on_home_title),
                subtitle = stringResource(R.string.settings_pip_on_home_subtitle),
                checked = pipOnHome,
                onToggle = settings::setPipOnHomeEnabled,
            )
            SettingsToggleRow(
                title = stringResource(R.string.settings_match_refresh_title),
                subtitle = stringResource(R.string.settings_match_refresh_subtitle),
                checked = matchRefreshRate,
                onToggle = settings::setMatchRefreshRate,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.settings_section_submenu_buttons), icon = Icons.Filled.Tune, initiallyExpanded = false) {
            Text(
                stringResource(R.string.settings_submenu_buttons_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton(
                    text = stringResource(R.string.settings_submenu_enable_all),
                    style = SettingsButtonStyle.Primary,
                    onClick = { settings.setAllSubMenuButtons(true) },
                )
                SettingsButton(
                    text = stringResource(R.string.settings_submenu_disable_all),
                    onClick = { settings.setAllSubMenuButtons(false) },
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSettings.SubMenuButton.entries.forEach { btn ->
                    val on = enabledSubMenuButtons.contains(btn)
                    SettingsChip(
                        label = stringResource(btn.titleRes),
                        selected = on,
                        onClick = { settings.setSubMenuButtonEnabled(btn, !on) },
                    )
                }
            }
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        // The movie/show player has its own button set: record, multiview, favourites and the rest
        // of the live row mean nothing on a film, so they are configured separately rather than
        // shared. See AppSettings.VodPlayerButton.
        SettingsSection(title = stringResource(R.string.settings_section_vod_buttons), icon = Icons.Filled.Movie, initiallyExpanded = false) {
            Text(
                stringResource(R.string.settings_vod_buttons_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SettingsButton(
                    text = stringResource(R.string.settings_submenu_enable_all),
                    style = SettingsButtonStyle.Primary,
                    onClick = { settings.setAllVodButtons(true) },
                )
                SettingsButton(
                    text = stringResource(R.string.settings_submenu_disable_all),
                    onClick = { settings.setAllVodButtons(false) },
                )
            }

            Spacer(Modifier.height(10.dp))

            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AppSettings.VodPlayerButton.entries.forEach { btn ->
                    val on = enabledVodButtons.contains(btn)
                    SettingsChip(
                        label = stringResource(btn.titleRes),
                        selected = on,
                        onClick = { settings.setVodButtonEnabled(btn, !on) },
                    )
                }
            }
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        TmdbKeySection(settings)

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        WeatherZipSection(settings)

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SleepTimerSection()
    }
}
