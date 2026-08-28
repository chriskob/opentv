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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.ui.ChannelsViewModel

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
                    text = stringResource(R.string.settings_parental_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Manage PIN protection and hide sensitive channel categories",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            ParentalBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        PinSection(pinIsSet = pinIsSet, onSetPin = settings::setPin, onClearPin = settings::clearPin)

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.parental_hidden_categories).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
            fontWeight = FontWeight.Bold,
            color = Color(0xFF26C6DA),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
        )
        Spacer(Modifier.height(4.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF18222C))
                .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
                .padding(20.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parental_show_hidden_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.parental_show_hidden_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                }
                Switch(checked = unlocked, onCheckedChange = settings::setHiddenUnlocked)
            }
        }

        Spacer(Modifier.height(16.dp))

        if (categories.isEmpty()) {
            Text(
                stringResource(R.string.parental_no_categories),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                categories.forEach { group ->
                    CategoryToggleRow(
                        label = group.label,
                        checked = group.key in hidden,
                        onToggle = { on ->
                            val next = hidden.toMutableSet().apply { if (on) add(group.key) else remove(group.key) }
                            settings.setHiddenCategories(next)
                        },
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CategoryToggleRow(
    label: String,
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (focused) Color(0xFFF0F4F8) else Color(0xFF18222C))
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(12.dp))
                else Modifier.border(0.5.dp, Color(0xFF263442), RoundedCornerShape(12.dp)),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                fontWeight = if (focused) FontWeight.Bold else FontWeight.Medium,
                color = if (focused) Color(0xFF10171E) else Color.White,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA),
                    checkedTrackColor = if (focused) Color(0xFFB2EBF2) else Color(0xFF004D40),
                ),
            )
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

    Text(
        text = stringResource(R.string.parental_pin).uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.sp),
        fontWeight = FontWeight.Bold,
        color = Color(0xFF26C6DA),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
    Spacer(Modifier.height(4.dp))
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF18222C))
            .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(14.dp))
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (pinIsSet && !editing) {
                Text(
                    text = stringResource(R.string.parental_pin_is_set),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editing = true; pin = ""; confirm = ""; error = null }) {
                        Text(stringResource(R.string.parental_change_pin))
                    }
                    OutlinedButton(onClick = onClearPin) { Text(stringResource(R.string.parental_remove_pin)) }
                }
            } else {
                Text(
                    text = if (pinIsSet) stringResource(R.string.parental_pin_enter_new) else stringResource(R.string.parental_pin_set),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PinField(stringResource(R.string.parental_pin_new), pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
                    PinField(stringResource(R.string.parental_pin_confirm), confirm) { if (it.length <= 4) confirm = it.filter(Char::isDigit) }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = Color(0xFFEF5350), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = pin.length == 4 && confirm.length == 4,
                        onClick = {
                            when {
                                pin.length != 4 -> error = pinLenError
                                pin != confirm -> error = pinMismatchError
                                else -> {
                                    onSetPin(pin); editing = false; pin = ""; confirm = ""; error = null
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.parental_save_pin)) }
                    if (editing) {
                        OutlinedButton(onClick = { editing = false; error = null }) { Text(stringResource(R.string.common_cancel)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.width(160.dp),
    )
}

@Composable
private fun PinGate(onCancel: () -> Unit, onSubmit: (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF18222C))
                .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(16.dp))
                .padding(32.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = Color(0xFF26C6DA), modifier = Modifier.size(40.dp))
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.parental_enter_pin),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                PinField(stringResource(R.string.parental_pin), pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.parental_wrong_pin), color = Color(0xFFEF5350))
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = pin.length == 4,
                        onClick = { if (!onSubmit(pin)) { error = true; pin = "" } },
                    ) { Text(stringResource(R.string.parental_unlock)) }
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.common_back)) }
                }
            }
        }
    }
}

@Composable
private fun ParentalBackButton(onClick: () -> Unit) {
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

