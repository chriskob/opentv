/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.settings.components.SettingsEmptyState
import app.opentv.ui.settings.components.SettingsNavRow
import app.opentv.ui.settings.components.SettingsBackButton
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.core.AppSettings
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.theme.displayName
import kotlinx.coroutines.delay

/**
 * One section of the settings hub. Each maps to a screen that is embedded unchanged on the right;
 * the shell only chooses which one is visible and who answers that screen's injected `onBack`.
 */
private enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
) {
    PROVIDERS(R.string.settings_providers_title, R.string.settings_providers_subtitle, Icons.Filled.Dns),
    GUIDE(R.string.settings_guide_title, R.string.settings_guide_subtitle, Icons.Filled.LiveTv),
    CHANNELS(R.string.common_channels, R.string.settings_channels_subtitle, Icons.Filled.GridView),
    DISPLAY(R.string.settings_display_title, R.string.settings_display_subtitle, Icons.Filled.Tune),
    RECORDINGS(R.string.settings_recording_title, R.string.settings_recording_subtitle, Icons.Filled.Storage),
    SYNC(R.string.settings_sync_title, R.string.settings_sync_subtitle, Icons.Filled.Sync),
    ADDONS(R.string.settings_addons_title, R.string.settings_addons_subtitle, Icons.Filled.Extension),
    WEB_MANAGER(R.string.settings_webmanager_title, R.string.settings_webmanager_subtitle, Icons.Filled.Devices),
    PROFILES(R.string.settings_profiles_title, R.string.settings_profiles_subtitle, Icons.Filled.Person),
    PARENTAL(R.string.settings_parental_title, R.string.settings_parental_subtitle, Icons.Filled.Lock),
    ABOUT(R.string.settings_about_title, R.string.settings_about_subtitle, Icons.Filled.Info),
}

/** The menu's grouping. Flat lists of nine-plus entries are hard to scan from a sofa. */
private enum class SettingsGroup(
    @StringRes val labelRes: Int,
    val sections: List<SettingsSection>,
) {
    CONTENT(
        R.string.settings_group_content,
        listOf(SettingsSection.PROVIDERS, SettingsSection.GUIDE, SettingsSection.CHANNELS),
    ),
    PLAYBACK(
        R.string.settings_group_playback,
        listOf(SettingsSection.DISPLAY, SettingsSection.RECORDINGS),
    ),
    NETWORK(
        R.string.settings_group_network,
        listOf(SettingsSection.SYNC, SettingsSection.ADDONS, SettingsSection.WEB_MANAGER),
    ),
    SYSTEM(
        R.string.settings_group_system,
        listOf(SettingsSection.PROFILES, SettingsSection.PARENTAL, SettingsSection.ABOUT),
    ),
}

private val DRAWER_EXPANDED = 304.dp
private val DRAWER_COLLAPSED = 92.dp

/**
 * TiviMate-style settings: a section menu down the left and the selected screen on the right.
 *
 * The menu behaves like the app's main rail — it is wide and labelled while the d-pad is inside it
 * and slides to a slim icon rail the moment focus moves into the page, so the content owns the
 * screen. Choosing a section swaps only the right pane; the drawer stays put. Back inside a page
 * hands focus back to the menu instead of leaving Settings, and Back on the menu leaves.
 */
@Composable
fun SettingsScreen(
    sourcesViewModel: SourcesViewModel,
    onOpenAddSource: () -> Unit,
    onOpenRemotePairing: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by remember { mutableStateOf<SettingsSection?>(null) }
    val menuFocus = remember { FocusRequester() }
    // A page's own back control hands focus back to the drawer, which re-expands it.
    val refocusMenu: () -> Unit = { runCatching { menuFocus.requestFocus() } }

    var railHasFocus by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val expanded = railHasFocus || selected == null
    val drawerWidth by animateDpAsState(
        targetValue = if (expanded) DRAWER_EXPANDED else DRAWER_COLLAPSED,
        animationSpec = tween(durationMillis = 220),
        label = "settingsDrawerWidth",
    )

    // Inside a section, Back returns to the menu (the way TiviMate does); on the menu, Back leaves
    // Settings. Exiting straight from a page cost the viewer their place in the list every time.
    BackHandler {
        if (selected != null) {
            selected = null
            runCatching { menuFocus.requestFocus() }
        } else {
            onDismiss()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        SettingsDrawer(
            width = drawerWidth,
            expanded = expanded,
            selected = selected,
            menuFocus = menuFocus,
            onSelect = { selected = it },
            onOpenRemotePairing = onOpenRemotePairing,
            onOpenSearch = { searching = true; query = "" },
            onRailFocus = { railHasFocus = it },
            onDone = onDismiss,
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                // Focus entering the page collapses the drawer; going back to the menu re-expands it.
                .onFocusChanged { if (it.hasFocus) railHasFocus = false },
        ) {
            if (searching) {
                SettingsSearchPanel(
                    query = query,
                    onQueryChange = { query = it },
                    entries = settingsSearchIndex(),
                    onPick = { selected = it; searching = false; query = "" },
                    onClose = { searching = false; query = "" },
                )
            } else when (selected) {
                SettingsSection.PROVIDERS -> ProvidersScreen(
                    viewModel = sourcesViewModel,
                    onAddSource = onOpenAddSource,
                    onOpenRemotePairing = onOpenRemotePairing,
                    onBack = refocusMenu,
                )
                SettingsSection.GUIDE -> EpgSettingsScreen(onBack = refocusMenu)
                SettingsSection.CHANNELS -> ChannelManagerScreen(onBack = refocusMenu)
                SettingsSection.DISPLAY -> AppSettingsScreen(onBack = refocusMenu)
                SettingsSection.RECORDINGS -> RecordingSettingsScreen(onBack = refocusMenu)
                SettingsSection.SYNC -> SyncScreen(onBack = refocusMenu)
                SettingsSection.ADDONS -> StremioAddonsScreen(onBack = refocusMenu)
                SettingsSection.WEB_MANAGER -> WebManagerScreen(onBack = refocusMenu)
                SettingsSection.PROFILES -> ProfilesScreen(onBack = refocusMenu)
                SettingsSection.PARENTAL -> ParentalControlsScreen(onBack = refocusMenu)
                SettingsSection.ABOUT -> AboutScreen(onBack = refocusMenu)
                null -> SettingsEmptyHint()
            }
        }
    }
}

@Composable
private fun SettingsEmptyHint() {
    Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
        SettingsEmptyState(
            title = stringResource(R.string.settings_pick_from_menu),
            icon = Icons.Filled.Tune,
            modifier = Modifier.width(420.dp),
        )
    }
}

/** The left menu: brand, grouped sections, then Remote Pairing and Back. */
@Composable
private fun SettingsDrawer(
    width: Dp,
    expanded: Boolean,
    selected: SettingsSection?,
    menuFocus: FocusRequester,
    onSelect: (SettingsSection) -> Unit,
    onOpenRemotePairing: () -> Unit,
    onOpenSearch: () -> Unit,
    onRailFocus: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val listState = rememberLazyListState()
    // The menu is the entry point, so it takes focus shortly after it is composed. A one-frame delay
    // lets the nodes attach before requestFocus runs.
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { menuFocus.requestFocus() }
    }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .onFocusChanged { onRailFocus(it.hasFocus) }
            .focusGroup()
            .padding(vertical = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (expanded) 20.dp else 0.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (expanded) Arrangement.Start else Arrangement.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_opentv_logo),
                contentDescription = "OpenTV",
                modifier = Modifier.size(32.dp),
            )
            if (expanded) {
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.settings_menu_heading),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Spacer(Modifier.height(6.dp))

        Box(Modifier.padding(horizontal = 12.dp)) {
            SettingsNavRow(
                title = stringResource(R.string.settings_search_nav),
                subtitle = if (expanded) stringResource(R.string.settings_search_nav_subtitle) else null,
                icon = Icons.Filled.Search,
                expanded = expanded,
                onClick = onOpenSearch,
            )
        }

        Spacer(Modifier.height(6.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SettingsGroup.entries.forEach { group ->
                item(key = "group_${group.name}") {
                    if (expanded) {
                        Text(
                            text = stringResource(group.labelRes).uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.4.sp),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 10.dp, top = 14.dp, bottom = 6.dp),
                        )
                    } else {
                        Spacer(Modifier.height(10.dp))
                    }
                }
                items(group.sections, key = { it.name }) { section ->
                    val focusTarget = (selected ?: SettingsSection.entries.first()) == section
                    SettingsNavRow(
                        title = stringResource(section.titleRes),
                        subtitle = stringResource(section.subtitleRes),
                        icon = section.icon,
                        selected = selected == section,
                        expanded = expanded,
                        onClick = { onSelect(section) },
                        modifier = if (focusTarget) Modifier.focusRequester(menuFocus) else Modifier,
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SettingsNavRow(
                title = stringResource(R.string.settings_remote_title),
                subtitle = stringResource(R.string.settings_remote_subtitle),
                icon = Icons.Filled.PhoneAndroid,
                expanded = expanded,
                tint = AppTheme.primary,
                onClick = onOpenRemotePairing,
            )
            SettingsNavRow(
                title = stringResource(R.string.common_done),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                expanded = expanded,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onDone,
            )
        }
    }
}

/** One searchable setting: the row's own name plus the section it lives in. */
private data class SettingsSearchEntry(
    val label: String,
    val sectionTitle: String,
    val section: SettingsSection,
)

/**
 * Everything the search can find. Sections match by their own title/subtitle, and the rows inside a
 * page are listed explicitly so searching a setting's name — not just its section — works.
 */
@Composable
private fun settingsSearchIndex(): List<SettingsSearchEntry> {
    val entries = mutableListOf<SettingsSearchEntry>()
    SettingsSection.entries.forEach { section ->
        val title = stringResource(section.titleRes)
        entries += SettingsSearchEntry(title, title, section)
        entries += SettingsSearchEntry(stringResource(section.subtitleRes), title, section)
    }

    val display = SettingsSection.DISPLAY
    val displayTitle = stringResource(display.titleRes)
    val displayKeys = listOf(
        R.string.settings_appearance,
        R.string.settings_section_content, R.string.nav_live_tv, R.string.nav_movies, R.string.nav_shows,
        R.string.settings_playlist_refresh, R.string.settings_guide_refresh,
        R.string.settings_epg_sync_with_playlist_title, R.string.settings_refresh_now,
        R.string.settings_section_language,
        R.string.settings_live_preview_title, R.string.settings_preview_sound_title,
        R.string.settings_guide_reset_on_open_title, R.string.settings_show_fav_category_title,
        R.string.settings_catchup_lookup_title,
        R.string.settings_subtitles_title, R.string.settings_resume_title,
        R.string.settings_pip_on_home_title, R.string.settings_match_refresh_title,
        R.string.settings_section_submenu_buttons, R.string.settings_section_vod_buttons,
        R.string.settings_section_metadata, R.string.settings_sleep_timer,
    )
    displayKeys.forEach { entries += SettingsSearchEntry(stringResource(it), displayTitle, display) }
    AppSettings.AccentColor.entries.forEach {
        entries += SettingsSearchEntry(it.displayName, displayTitle, display)
    }
    AppSettings.SubMenuButton.entries.forEach {
        entries += SettingsSearchEntry(stringResource(it.titleRes), displayTitle, display)
    }
    AppSettings.VodPlayerButton.entries.forEach {
        entries += SettingsSearchEntry(stringResource(it.titleRes), displayTitle, display)
    }

    return entries.distinctBy { it.label.lowercase() to it.section }
}

@Composable
private fun SettingsSearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    entries: List<SettingsSearchEntry>,
    onPick: (SettingsSection) -> Unit,
    onClose: () -> Unit,
) {
    val results = if (query.isBlank()) {
        entries
    } else {
        entries.filter {
            it.label.contains(query, ignoreCase = true) || it.sectionTitle.contains(query, ignoreCase = true)
        }
    }

    // Land focus in the field so the panel is usable the moment it opens (press OK to type).
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { fieldFocus.requestFocus() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_search_title),
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SettingsBackButton(onClick = onClose, label = stringResource(R.string.common_cancel))
        }

        Spacer(Modifier.height(20.dp))
        TvOutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_search_placeholder)) },
            modifier = Modifier.fillMaxWidth().focusRequester(fieldFocus),
        )
        Spacer(Modifier.height(16.dp))

        if (results.isEmpty()) {
            SettingsEmptyState(title = stringResource(R.string.settings_search_empty), icon = Icons.Filled.Search)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(results) { entry ->
                    SettingsNavRow(
                        title = entry.label,
                        subtitle = entry.sectionTitle,
                        onClick = { onPick(entry.section) },
                    )
                }
            }
        }
    }
}
