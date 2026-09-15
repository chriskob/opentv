/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.ui.ProfilesViewModel
import app.opentv.ui.channels.OnScreenKeyboard
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme

/**
 * Who's watching. Add, rename, remove and switch between local profiles.
 */
@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    viewModel: ProfilesViewModel = viewModel(),
) {
    val profiles by viewModel.profiles.collectAsState()
    val activeId by viewModel.activeProfileId.collectAsState()

    var editing by remember { mutableStateOf<Long?>(null) }

    editing?.let { target ->
        NameEntry(
            initial = if (target == NEW) "" else profiles.firstOrNull { it.id == target }?.name.orEmpty(),
            heading = if (target == NEW) stringResource(R.string.profiles_new_heading) else stringResource(R.string.profiles_rename_heading),
            onCancel = { editing = null },
            onSave = { name ->
                if (target == NEW) viewModel.addProfile(name) else viewModel.rename(target, name)
                editing = null
            },
        )
        return
    }

    SettingsPage(
        title = stringResource(R.string.profiles_title),
        subtitle = stringResource(R.string.settings_profiles_page_subtitle),
        onBack = onBack,
        actions = {
            SettingsButton(
                text = stringResource(R.string.profiles_add),
                onClick = { editing = NEW },
                style = SettingsButtonStyle.Primary,
                icon = Icons.Filled.Add,
            )
        },
    ) {
        if (profiles.isEmpty()) {
            SettingsEmptyState(
                title = stringResource(R.string.profiles_empty_title),
                message = stringResource(R.string.profiles_empty_message),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                profiles.forEach { profile ->
                    val active = profile.id == activeId
                    ProfileCard(
                        name = profile.name,
                        active = active,
                        canRemove = profile.id != 1L,
                        onSelect = { viewModel.select(profile.id) },
                        onRename = { editing = profile.id },
                        onRemove = { viewModel.remove(profile.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    name: String,
    active: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .settingsFocus(
                shape = SettingsShape.Row,
                selected = active,
            )
            .focusable()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onSelect,
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (active) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.profiles_active),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.primary,
                )
            }
        }

        if (!active) {
            SettingsButton(
                text = stringResource(R.string.profiles_use),
                onClick = onSelect,
                style = SettingsButtonStyle.Primary,
                icon = Icons.Filled.CheckCircle,
            )
            Spacer(Modifier.width(8.dp))
        }
        SettingsButton(
            text = stringResource(R.string.profiles_rename),
            onClick = onRename,
            style = SettingsButtonStyle.Secondary,
            icon = Icons.Filled.Edit,
        )
        if (canRemove) {
            Spacer(Modifier.width(8.dp))
            SettingsButton(
                text = stringResource(R.string.common_remove),
                onClick = onRemove,
                style = SettingsButtonStyle.Danger,
                icon = Icons.Filled.Delete,
            )
        }
    }
}

@Composable
private fun NameEntry(
    initial: String,
    heading: String,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    SettingsPage(
        title = heading,
        onBack = onCancel,
        backLabel = stringResource(R.string.common_cancel),
        actions = {
            SettingsButton(
                text = stringResource(R.string.common_save),
                onClick = { onSave(name) },
                style = SettingsButtonStyle.Primary,
                enabled = name.isNotBlank(),
            )
        },
    ) {
        Text(
            text = name.ifEmpty { "…" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = AppTheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        OnScreenKeyboard(
            onKey = { if (name.length < 24) name += it },
            onSpace = { if (name.length < 24) name += " " },
            onBackspace = { name = name.dropLast(1) },
            onClear = { name = "" },
        )
    }
}

private const val NEW = -1L
