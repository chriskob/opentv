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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.ui.ProfilesViewModel
import app.opentv.ui.channels.OnScreenKeyboard

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

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.profiles_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage multi-user watch history and personalized favorites",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            ProfileActionButton(
                label = stringResource(R.string.profiles_add),
                icon = Icons.Filled.Add,
                onClick = { editing = NEW },
            )

            Spacer(Modifier.width(14.dp))

            ProfilesBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(profiles, key = { it.id }) { profile ->
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

@Composable
private fun ProfileCard(
    name: String,
    active: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color(0xFF18222C),
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp))
                else if (active) Modifier.border(1.dp, Color(0xFF26C6DA).copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp)),
            )
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = if (focused) Color(0xFF10171E) else Color.White,
                )
                if (active) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.profiles_active),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        fontWeight = FontWeight.Bold,
                        color = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA),
                    )
                }
            }

            if (!active) {
                TextButton(onClick = onSelect) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.profiles_use), color = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA))
                }
                Spacer(Modifier.width(4.dp))
            }
            TextButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = null, tint = if (focused) Color(0xFF37474F) else Color.White.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.profiles_rename), color = if (focused) Color(0xFF37474F) else Color.White.copy(alpha = 0.8f))
            }
            if (canRemove) {
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Color(0xFFEF5350), modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.common_remove), color = Color(0xFFEF5350))
                }
            }
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
    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(horizontal = 40.dp, vertical = 28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(heading, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.width(20.dp))
            Text(
                name.ifEmpty { "…" },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF26C6DA),
            )
            Spacer(Modifier.weight(1f))
            Button(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text(stringResource(R.string.common_save)) }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
        }
        Spacer(Modifier.height(24.dp))
        OnScreenKeyboard(
            onKey = { if (name.length < 24) name += it },
            onSpace = { if (name.length < 24) name += " " },
            onBackspace = { name = name.dropLast(1) },
            onClear = { name = "" },
        )
    }
}

@Composable
private fun ProfileActionButton(label: String, icon: ImageVector, onClick: () -> Unit) {
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
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (focused) Color(0xFF10171E) else Color(0xFF26C6DA),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (focused) Color(0xFF10171E) else Color.White,
        )
    }
}

@Composable
private fun ProfilesBackButton(onClick: () -> Unit) {
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

private const val NEW = -1L

