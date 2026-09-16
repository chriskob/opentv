/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.ui.ChannelsViewModel
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme

/**
 * Parental controls: a PIN, and a list of categories to keep out of the guide.
 */
@Composable
fun ParentalControlsScreen(
    onBack: () -> Unit,
    channelsViewModel: ChannelsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val settings = remember { ServiceLocator.get(context).settings }
    val pinIsSet by settings.pinIsSet.collectAsState()
    val hidden by settings.hiddenCategories.collectAsState()
    val unlocked by settings.hiddenUnlocked.collectAsState()
    val categories by channelsViewModel.categoryGroups.collectAsState()

    var authed by remember { mutableStateOf(false) }

    if (pinIsSet && !authed) {
        PinGate(
            onCancel = onBack,
            onSubmit = { entered -> settings.verifyPin(entered).also { if (it) authed = true } },
        )
        return
    }

    SettingsPage(
        title = stringResource(R.string.settings_parental_title),
        subtitle = stringResource(R.string.settings_parental_page_subtitle),
        onBack = onBack,
    ) {
        PinSection(pinIsSet = pinIsSet, onSetPin = settings::setPin, onClearPin = settings::clearPin)

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        SettingsSection(title = stringResource(R.string.parental_hidden_categories)) {
            SettingsToggleRow(
                title = stringResource(R.string.parental_show_hidden_title),
                subtitle = stringResource(R.string.parental_show_hidden_subtitle),
                checked = unlocked,
                onToggle = settings::setHiddenUnlocked,
            )
        }

        Spacer(Modifier.height(SettingsSpacing.SectionGap))

        if (categories.isEmpty()) {
            SettingsEmptyState(title = stringResource(R.string.parental_no_categories))
        } else {
            SettingsCard {
                categories.forEach { group ->
                    SettingsToggleRow(
                        title = group.label,
                        checked = group.key in hidden,
                        onToggle = { on ->
                            val next = hidden.toMutableSet().apply { if (on) add(group.key) else remove(group.key) }
                            settings.setHiddenCategories(next)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinSection(
    pinIsSet: Boolean,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val pinLenError = stringResource(R.string.parental_pin_len_error)
    val pinMismatchError = stringResource(R.string.parental_pin_mismatch)

    SettingsSection(title = stringResource(R.string.parental_pin)) {
        if (pinIsSet && !editing) {
            Text(
                text = stringResource(R.string.parental_pin_is_set),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsButton(
                    text = stringResource(R.string.parental_change_pin),
                    onClick = { editing = true; pin = ""; confirm = ""; error = null },
                    style = SettingsButtonStyle.Secondary,
                )
                SettingsButton(
                    text = stringResource(R.string.parental_remove_pin),
                    onClick = onClearPin,
                    style = SettingsButtonStyle.Danger,
                )
            }
        } else {
            Text(
                text = if (pinIsSet) stringResource(R.string.parental_pin_enter_new) else stringResource(R.string.parental_pin_set),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                PinField(stringResource(R.string.parental_pin_new), pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
                PinField(stringResource(R.string.parental_pin_confirm), confirm) { if (it.length <= 4) confirm = it.filter(Char::isDigit) }
            }
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = SettingsDanger, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SettingsButton(
                    text = stringResource(R.string.parental_save_pin),
                    onClick = {
                        when {
                            pin.length != 4 -> error = pinLenError
                            pin != confirm -> error = pinMismatchError
                            else -> {
                                onSetPin(pin); editing = false; pin = ""; confirm = ""; error = null
                            }
                        }
                    },
                    style = SettingsButtonStyle.Primary,
                    enabled = pin.length == 4 && confirm.length == 4,
                )
                if (editing) {
                    SettingsButton(
                        text = stringResource(R.string.common_cancel),
                        onClick = { editing = false; error = null },
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    TvOutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.widthIn(min = 160.dp),
    )
}

@Composable
private fun PinGate(onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        SettingsCard(
            modifier = Modifier.widthIn(max = 380.dp),
            contentPadding = PaddingValues(32.dp),
        ) {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = AppTheme.primary, modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.parental_enter_pin),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(16.dp))
                PinField(stringResource(R.string.parental_pin), pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.parental_wrong_pin), color = SettingsDanger)
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsButton(
                        text = stringResource(R.string.parental_unlock),
                        onClick = { if (!onSubmit(pin)) { error = true; pin = "" } },
                        style = SettingsButtonStyle.Primary,
                        enabled = pin.length == 4,
                    )
                    SettingsButton(
                        text = stringResource(R.string.common_back),
                        onClick = onCancel,
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }
        }
    }
}
