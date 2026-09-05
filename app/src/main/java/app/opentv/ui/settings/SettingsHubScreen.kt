/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import app.opentv.ui.theme.AppTheme
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.BuildConfig
import app.opentv.R

/**
 * Modern, HD, and silky-smooth Settings Hub for Android TV.
 * Features jewel-toned gradient icon badges, spring-based focus scale animations,
 * frosted glassmorphism, and intuitive D-pad navigation.
 */
@Composable
fun SettingsHubScreen(
    onOpenProviders: () -> Unit,
    onOpenAddons: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenChannels: () -> Unit,
    onOpenWebManager: () -> Unit,
    onOpenDisplay: () -> Unit,
    onOpenParental: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenRecordings: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenRemotePairing: () -> Unit = {},
    onBack: () -> Unit,
) {
    val contentSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Dns,
            gradientColors = Pair(Color(0xFF0288D1), Color(0xFF00B0FF)),
            title = stringResource(R.string.settings_providers_title),
            subtitle = stringResource(R.string.settings_providers_subtitle),
            onClick = onOpenProviders,
        ),
        HubCardItem(
            icon = Icons.Filled.LiveTv,
            gradientColors = Pair(Color(0xFF00A344), Color(0xFF00E676)),
            title = stringResource(R.string.settings_guide_title),
            subtitle = stringResource(R.string.settings_guide_subtitle),
            onClick = onOpenGuide,
        ),
        HubCardItem(
            icon = Icons.Filled.PhoneAndroid,
            gradientColors = Pair(Color(0xFF0097A7), Color(0xFF00E5FF)),
            title = "Remote Support & Pairing",
            subtitle = "Edit, add, or troubleshoot playlists remotely",
            tag = "REMOTE",
            onClick = onOpenRemotePairing,
        ),
        HubCardItem(
            icon = Icons.Filled.GridView,
            gradientColors = Pair(Color(0xFFF57C00), Color(0xFFFFB300)),
            title = stringResource(R.string.common_channels),
            subtitle = stringResource(R.string.settings_channels_subtitle),
            onClick = onOpenChannels,
        ),
    )

    val playbackSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Tune,
            gradientColors = Pair(Color(0xFF5E35B1), Color(0xFF7C4DFF)),
            title = stringResource(R.string.settings_display_title),
            subtitle = stringResource(R.string.settings_display_subtitle),
            onClick = onOpenDisplay,
        ),
    )

    val systemSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Storage,
            gradientColors = Pair(Color(0xFFD32F2F), Color(0xFFFF5252)),
            title = stringResource(R.string.settings_recording_title),
            subtitle = stringResource(R.string.settings_recording_subtitle),
            onClick = onOpenRecordings,
        ),
        HubCardItem(
            icon = Icons.Filled.Lock,
            gradientColors = Pair(Color(0xFFC2185B), Color(0xFFFF4081)),
            title = stringResource(R.string.settings_parental_title),
            subtitle = stringResource(R.string.settings_parental_subtitle),
            onClick = onOpenParental,
        ),
        HubCardItem(
            icon = Icons.Filled.Info,
            gradientColors = Pair(Color(0xFF455A64), Color(0xFF90A4AE)),
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_subtitle),
            onClick = onOpenAbout,
        ),
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0E1620),
                        Color(0xFF090D13),
                    )
                )
            )
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Top Action Header
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp, fontSize = 11.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.primary,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.nav_settings),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Preferences, Playlists & System Configuration",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                        color = Color(0xFF8B9BA8),
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    SystemStatusPill()
                    HubDoneButton(onBack)
                }
            }

            Spacer(Modifier.height(22.dp))

            // Categorized Settings Grid
            LazyColumn(
                contentPadding = PaddingValues(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Section 1: Playlists & Channels
                item {
                    SettingsSectionHeader("Playlists & Channels", Icons.Filled.LiveTv)
                }
                items(contentSection.chunked(2).size) { idx ->
                    val pair = contentSection.chunked(2)[idx]
                    CardGridRow(pair)
                }

                // Section 2: Playback & Interface
                item {
                    Spacer(Modifier.height(8.dp))
                    SettingsSectionHeader("Playback & Interface", Icons.Filled.Tune)
                }
                items(playbackSection.chunked(2).size) { idx ->
                    val pair = playbackSection.chunked(2)[idx]
                    CardGridRow(pair)
                }

                // Section 3: System & Storage
                item {
                    Spacer(Modifier.height(8.dp))
                    SettingsSectionHeader("System & Storage", Icons.Filled.Storage)
                }
                items(systemSection.chunked(2).size) { idx ->
                    val pair = systemSection.chunked(2)[idx]
                    CardGridRow(pair)
                }
            }
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

@Composable
private fun SettingsSectionHeader(title: String, icon: ImageVector) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppTheme.primary,
                modifier = Modifier.size(15.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.2.sp, fontSize = 12.sp),
                fontWeight = FontWeight.Bold,
                color = AppTheme.primary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(Color(0xFF1E2D3C))
        )
    }
}

@Composable
private fun CardGridRow(items: List<HubCardItem>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        for (item in items) {
            Box(Modifier.weight(1f)) {
                HubCard(item)
            }
        }
        if (items.size == 1) {
            Spacer(Modifier.weight(1f))
        }
    }
}

private data class HubCardItem(
    val icon: ImageVector,
    val gradientColors: Pair<Color, Color>,
    val title: String,
    val subtitle: String,
    val tag: String? = null,
    val onClick: () -> Unit,
)

@Composable
private fun HubCard(item: HubCardItem) {
    var focused by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (focused) 1.025f else 1.0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        label = "card_scale",
    )
    val arrowOffset by animateDpAsState(
        targetValue = if (focused) 4.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "arrow_offset",
    )

    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .fillMaxWidth()
            .height(96.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (focused) AppTheme.cardFocusBg
                else Color(0xFF141C25),
            )
            .then(
                if (focused) Modifier.background(
                    Brush.linearGradient(
                        listOf(
                            item.gradientColors.first.copy(alpha = 0.18f),
                            item.gradientColors.second.copy(alpha = 0.18f),
                        )
                    )
                ) else Modifier
            )
            .then(
                if (focused) {
                    Modifier.border(2.5.dp, item.gradientColors.second.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                } else {
                    Modifier.border(0.75.dp, Color(0xFF22303E), RoundedCornerShape(16.dp))
                }
            )
            .focusable()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Jewel-Toned Icon Badge
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            item.gradientColors.first,
                            item.gradientColors.second,
                        )
                    )
                )
                .then(
                    if (!focused) {
                        Modifier.border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(14.dp))
                    } else {
                        Modifier.border(1.5.dp, item.gradientColors.second.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }

        Spacer(Modifier.width(18.dp))

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.tag != null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (focused) Color(0xFF00B0FF).copy(alpha = 0.3f)
                                else Color(0xFF00B0FF).copy(alpha = 0.25f)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = item.tag,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = Color(0xFF40C4FF),
                        )
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (focused) Color(0xFFB0BEC5) else Color(0xFF8B9BA8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Directional Arrow with smooth horizontal nudge
        Box(
            Modifier
                .offset(x = arrowOffset)
                .size(32.dp)
                .clip(CircleShape)
                .background(
                    if (focused) item.gradientColors.first.copy(alpha = 0.3f)
                    else Color(0xFF1B2632)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = if (focused) Color.White else Color(0xFF8B9BA8),
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun HubDoneButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.05f else 1.0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
        label = "btn_scale",
    )

    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (focused) {
                    Modifier.background(Brush.linearGradient(listOf(AppTheme.dark, AppTheme.primary)))
                } else {
                    Modifier.background(Color(0xFF1B2633))
                }
            )
            .then(
                if (focused) Modifier.border(2.dp, Color(0xFF4DD0E1), RoundedCornerShape(12.dp))
                else Modifier.border(1.dp, Color(0xFF2B3C4E), RoundedCornerShape(12.dp)),
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            tint = if (focused) Color.White else AppTheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.5.sp),
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}
