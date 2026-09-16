/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.settings.components.SettingsNavRow
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
    ADDONS(R.string.settings_addons_title, R.string.settings_addons_subtitle, Icons.Filled.Extension),
    PROFILES(R.string.settings_profiles_title, R.string.settings_profiles_subtitle, Icons.Filled.Person),
    PARENTAL(R.string.settings_parental_title, R.string.settings_parental_subtitle, Icons.Filled.Lock),
    WEB_MANAGER(R.string.settings_webmanager_title, R.string.settings_webmanager_subtitle, Icons.Filled.Devices),
    ABOUT(R.string.settings_about_title, R.string.settings_about_subtitle, Icons.Filled.Info),
}

private val DRAWER_EXPANDED = 260.dp
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
    // A section is always selected: the hub used to boot to an empty "choose a section" pane, which
    // spent half the screen apologising. It opens on Providers — the top of the list and the first
    // thing people come here to manage.
    var selected by remember { mutableStateOf(SettingsSection.PROVIDERS) }
    val menuFocus = remember { FocusRequester() }
    // A page's own back control hands focus back to the drawer, which re-expands it.
    val refocusMenu: () -> Unit = { runCatching { menuFocus.requestFocus() } }

    var railHasFocus by remember { mutableStateOf(false) }
    val expanded = railHasFocus
    // The drawer still collapses to the icon rail when focus moves into a page, but instantly:
    // no width animation, so the menu never appears to slide.
    val drawerWidth = if (expanded) DRAWER_EXPANDED else DRAWER_COLLAPSED

    // Back inside a section hands focus back to the menu (the way TiviMate does); Back on the menu
    // leaves Settings. Exiting straight from a page cost the viewer their place in the list.
    BackHandler {
        if (railHasFocus) onDismiss() else refocusMenu()
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
            when (selected) {
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
                SettingsSection.ADDONS -> StremioAddonsScreen(onBack = refocusMenu)
                SettingsSection.WEB_MANAGER -> WebManagerScreen(onBack = refocusMenu)
                SettingsSection.PROFILES -> ProfilesScreen(onBack = refocusMenu)
                SettingsSection.PARENTAL -> ParentalControlsScreen(onBack = refocusMenu)
                SettingsSection.ABOUT -> AboutScreen(onBack = refocusMenu)
            }
        }
    }
}

/** The left menu: search, then one flat list of sections. */
@Composable
private fun SettingsDrawer(
    width: Dp,
    expanded: Boolean,
    selected: SettingsSection,
    menuFocus: FocusRequester,
    onSelect: (SettingsSection) -> Unit,
    onOpenRemotePairing: () -> Unit,
    onRailFocus: (Boolean) -> Unit,
    onDone: () -> Unit,
) {
    val listState = rememberScrollState()
    // The menu is the entry point, so it takes focus shortly after it is composed. A one-frame delay
    // lets the nodes attach before requestFocus runs.
    //
    // This is a plain scrolling Column, not a LazyColumn: there are ~13 rows, so laziness buys
    // nothing, and it costs correctness — a FocusRequester attached to a row the list has not
    // composed yet is silently dropped, which used to leave the cursor wherever it happened to fall
    // and scroll the menu to whatever row caught it.
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { menuFocus.requestFocus() }
    }

    Column(
        Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .onFocusChanged { onRailFocus(it.hasFocus) }
            .focusGroup()
            .padding(vertical = 10.dp),
    ) {
        // One flat list that all scrolls together — sections, then the trailing actions. Nothing is
        // pinned: the old layout kept Search on top and Remote/Done glued to the bottom, which ate
        // a third of a short drawer and split one menu into three.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(listState)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            // Sections, in menu order. About is drawn below Remote Support so it sits last, just
            // above Done.
            SettingsSection.entries
                .filter { it != SettingsSection.ABOUT }
                .forEach { section ->
                    SettingsNavRow(
                        title = stringResource(section.titleRes),
                        icon = section.icon,
                        selected = selected == section,
                        expanded = expanded,
                        titleSize = 16.sp,
                        titleWeight = FontWeight.Normal,
                        onClick = { onSelect(section) },
                        modifier = if (selected == section) Modifier.focusRequester(menuFocus) else Modifier,
                    )
                }
            SettingsNavRow(
                title = stringResource(R.string.settings_remote_title),
                icon = Icons.Filled.PhoneAndroid,
                expanded = expanded,
                titleSize = 16.sp,
                titleWeight = FontWeight.Normal,
                onClick = onOpenRemotePairing,
            )
            SettingsNavRow(
                title = stringResource(R.string.settings_about_title),
                icon = SettingsSection.ABOUT.icon,
                selected = selected == SettingsSection.ABOUT,
                expanded = expanded,
                titleSize = 16.sp,
                titleWeight = FontWeight.Normal,
                onClick = { onSelect(SettingsSection.ABOUT) },
                modifier = if (selected == SettingsSection.ABOUT) Modifier.focusRequester(menuFocus) else Modifier,
            )
            SettingsNavRow(
                title = stringResource(R.string.common_done),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                expanded = expanded,
                titleSize = 16.sp,
                titleWeight = FontWeight.Normal,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onDone,
            )
        }
    }
}

/* Settings search lived here: an index over every section and row plus a full results
 * panel. Removed so the drawer is one plain scrolling list with no second way in. */
