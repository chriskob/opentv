/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.R

/**
 * Modern, clean, and user-friendly Settings Hub for Android TV.
 * Categorized 2-column layout with high-contrast active focus indicators,
 * tinted icon badges, and intuitive remote navigation.
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
    onBack: () -> Unit,
) {
    val contentSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Dns,
            iconTint = Color(0xFF29B6F6),
            title = stringResource(R.string.settings_providers_title),
            subtitle = stringResource(R.string.settings_providers_subtitle),
            onClick = onOpenProviders,
        ),
        HubCardItem(
            icon = Icons.Filled.LiveTv,
            iconTint = Color(0xFF66BB6A),
            title = stringResource(R.string.settings_guide_title),
            subtitle = stringResource(R.string.settings_guide_subtitle),
            onClick = onOpenGuide,
        ),
        HubCardItem(
            icon = Icons.Filled.GridView,
            iconTint = Color(0xFFFFA726),
            title = stringResource(R.string.common_channels),
            subtitle = stringResource(R.string.settings_channels_subtitle),
            onClick = onOpenChannels,
        ),
    )

    val playbackSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Tune,
            iconTint = Color(0xFF26C6DA),
            title = stringResource(R.string.settings_display_title),
            subtitle = stringResource(R.string.settings_display_subtitle),
            onClick = onOpenDisplay,
        ),
    )

    val systemSection = listOf(
        HubCardItem(
            icon = Icons.Filled.Storage,
            iconTint = Color(0xFFFF7043),
            title = stringResource(R.string.settings_recording_title),
            subtitle = stringResource(R.string.settings_recording_subtitle),
            onClick = onOpenRecordings,
        ),
        HubCardItem(
            icon = Icons.Filled.Lock,
            iconTint = Color(0xFFEF5350),
            title = stringResource(R.string.settings_parental_title),
            subtitle = stringResource(R.string.settings_parental_subtitle),
            onClick = onOpenParental,
        ),
        HubCardItem(
            icon = Icons.Filled.Info,
            iconTint = Color(0xFF7E57C2),
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_subtitle),
            onClick = onOpenAbout,
        ),
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        // Top Action Header
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_settings),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Preferences, Playlists & System Configuration",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }
            HubDoneButton(onBack)
        }

        Spacer(Modifier.height(20.dp))

        // Categorized Settings List
        LazyColumn(
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Section 1: Playlists & Content
            item {
                SettingsSectionHeader("Playlists & Content")
            }
            items(contentSection.chunked(2).size) { idx ->
                val pair = contentSection.chunked(2)[idx]
                CardGridRow(pair)
            }

            // Section 2: Playback & Interface
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader("Playback & Interface")
            }
            items(playbackSection.chunked(2).size) { idx ->
                val pair = playbackSection.chunked(2)[idx]
                CardGridRow(pair)
            }

            // Section 3: System & Management
            item {
                Spacer(Modifier.height(8.dp))
                SettingsSectionHeader("System & Management")
            }
            items(systemSection.chunked(2).size) { idx ->
                val pair = systemSection.chunked(2)[idx]
                CardGridRow(pair)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = Color(0xFF26C6DA),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun CardGridRow(items: List<HubCardItem>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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
    val iconTint: Color,
    val title: String,
    val subtitle: String,
    val onClick: () -> Unit,
)

@Composable
private fun HubCard(item: HubCardItem) {
    var focused by remember { mutableStateOf(false) }

    Row(
        Modifier
            .fillMaxWidth()
            .height(86.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF18222C),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp)),
            )
            .focusable()
            .clickable(onClick = item.onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Colored Icon Badge
        Box(
            Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (focused) item.iconTint.copy(alpha = 0.2f)
                    else item.iconTint.copy(alpha = 0.15f),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (focused) item.iconTint.copy(alpha = 0.95f) else item.iconTint,
                modifier = Modifier.size(26.dp),
            )
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = if (focused) Color(0xFF10171E) else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                color = if (focused) Color(0xFF37474F) else Color.White.copy(alpha = 0.65f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = if (focused) Color(0xFF10171E) else Color.White.copy(alpha = 0.35f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HubDoneButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF1E2833),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(10.dp))
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
            tint = if (focused) Color(0xFF10171E) else Color.White,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.common_done),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (focused) Color(0xFF10171E) else Color.White,
        )
    }
}

