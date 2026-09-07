/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.BuildConfig
import app.opentv.R
import app.opentv.ui.SourcesViewModel
import app.opentv.ui.channels.ChannelManagerScreen
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.delay

/**
 * The sections of the settings shell. Each embeds its existing screen unchanged; the shell only
 * swaps which one fills the right-hand pane and who answers that screen's injected [onBack].
 */
private enum class SettingsSection(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val icon: ImageVector,
    val gradient: Pair<Color, Color>,
) {
    PROVIDERS(
        R.string.settings_providers_title, R.string.settings_providers_subtitle,
        Icons.Filled.Dns, Pair(Color(0xFF0288D1), Color(0xFF00B0FF)),
    ),
    GUIDE(
        R.string.settings_guide_title, R.string.settings_guide_subtitle,
        Icons.Filled.LiveTv, Pair(Color(0xFF00A344), Color(0xFF00E676)),
    ),
    CHANNELS(
        R.string.common_channels, R.string.settings_channels_subtitle,
        Icons.Filled.GridView, Pair(Color(0xFFF57C00), Color(0xFFFFB300)),
    ),
    DISPLAY(
        R.string.settings_display_title, R.string.settings_display_subtitle,
        Icons.Filled.Tune, Pair(Color(0xFF5E35B1), Color(0xFF7C4DFF)),
    ),
    RECORDINGS(
        R.string.settings_recording_title, R.string.settings_recording_subtitle,
        Icons.Filled.Storage, Pair(Color(0xFFD32F2F), Color(0xFFFF5252)),
    ),
    PARENTAL(
        R.string.settings_parental_title, R.string.settings_parental_subtitle,
        Icons.Filled.Lock, Pair(Color(0xFFC2185B), Color(0xFFFF4081)),
    ),
    ABOUT(
        R.string.settings_about_title, R.string.settings_about_subtitle,
        Icons.Filled.Info, Pair(Color(0xFF455A64), Color(0xFF90A4AE)),
    ),
}

/**
 * TiviMate-style settings: pressing Settings slides a menu out from the left with every section
 * listed; choosing one slides the menu back and shows that section's existing screen in the
 * right-hand pane. Back (or a section's own back affordance) slides the menu out again; Back on
 * the menu leaves Settings.
 *
 * The section screens are embedded completely unchanged — they already take an injected
 * [onBack], which the shell points at "slide the menu out" instead of leaving.
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
    // Section back controls hand focus back to the (always-open) menu.
    val refocusMenu: () -> Unit = { runCatching { menuFocus.requestFocus() } }

    BackHandler { onDismiss() }

    Row(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1620),
                        Color(0xFF090D13),
                    )
                )
            ),
    ) {
        SettingsMenuPanel(
            selected = selected,
            menuFocus = menuFocus,
            onSelect = { section -> selected = section },
            onOpenRemotePairing = onOpenRemotePairing,
            onDone = onDismiss,
        )

        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
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
                SettingsSection.PARENTAL -> ParentalControlsScreen(onBack = refocusMenu)
                SettingsSection.ABOUT -> AboutScreen(onBack = refocusMenu)
                null -> SettingsEmptyHint()
            }
        }
    }
}

@Composable
private fun SettingsEmptyHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 22.sp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF8B9BA8),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.settings_pick_from_menu),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = Color(0xFF5C6B7A),
            )
        }
    }
}

/** The settings menu: every section, Remote Pairing, and a BACK item. Always visible. */
@Composable
private fun SettingsMenuPanel(
    selected: SettingsSection?,
    menuFocus: FocusRequester,
    onSelect: (SettingsSection) -> Unit,
    onOpenRemotePairing: () -> Unit,
    onDone: () -> Unit,
) {
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { menuFocus.requestFocus() }
    }

    Column(
        Modifier
            .width(300.dp)
            .fillMaxHeight()
            .background(Color(0xFF0C141D))
            .border(width = 0.75.dp, color = Color(0xFF1E2D3C))
            .padding(vertical = 18.dp),
    ) {
        Column(Modifier.padding(horizontal = 18.dp)) {
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontSize = 11.5.sp),
                fontWeight = FontWeight.Bold,
                color = AppTheme.primary,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.nav_settings),
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            SystemStatusPill()
        }

        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF1E2D3C))
        )
        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsSection.entries.forEach { section ->
                val isFocusTarget = (selected ?: SettingsSection.entries.first()) == section
                SettingsMenuRow(
                    icon = section.icon,
                    gradient = section.gradient,
                    title = stringResource(section.titleRes),
                    subtitle = stringResource(section.subtitleRes),
                    isCurrent = selected == section,
                    modifier = if (isFocusTarget) Modifier.focusRequester(menuFocus) else Modifier,
                    onClick = { onSelect(section) },
                )
            }

            SettingsMenuRow(
                icon = Icons.Filled.PhoneAndroid,
                gradient = Pair(Color(0xFF0097A7), Color(0xFF00E5FF)),
                title = "Remote Support & Pairing",
                subtitle = "Edit, add, or troubleshoot playlists remotely",
                isCurrent = false,
                onClick = onOpenRemotePairing,
            )

            SettingsMenuRow(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                gradient = Pair(AppTheme.dark, AppTheme.primary),
                title = "BACK",
                subtitle = null,
                isCurrent = false,
                onClick = onDone,
            )
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: ImageVector,
    gradient: Pair<Color, Color>,
    title: String,
    subtitle: String?,
    isCurrent: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .then(modifier)
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    focused -> Color(0xFF1C2A38)
                    isCurrent -> Color(0xFF131E29)
                    else -> Color.Transparent
                }
            )
            .then(
                when {
                    focused -> Modifier.border(1.5.dp, AppTheme.primary, RoundedCornerShape(10.dp))
                    isCurrent -> Modifier.border(1.dp, Color(0xFF2B3C4E), RoundedCornerShape(10.dp))
                    else -> Modifier
                }
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Brush.linearGradient(listOf(gradient.first, gradient.second))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.5.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                    color = Color(0xFF8B9BA8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (isCurrent) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(AppTheme.primary)
            )
        }
    }
}

@Composable
private fun SystemStatusPill() {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xFF16202C))
            .border(0.75.dp, Color(0xFF283849), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(Color(0xFF00E676))
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Ready · v${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
            color = Color(0xFFCFD8DC),
        )
    }
}