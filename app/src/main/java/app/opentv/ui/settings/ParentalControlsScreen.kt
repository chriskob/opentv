/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.core.ServiceLocator
import app.opentv.ui.ChannelsViewModel

/**
 * Parental controls: a PIN, and a list of categories to keep out of the guide.
 *
 * Hidden categories vanish from Live TV — rail, All channels, and search — until the session is
 * unlocked here. If a PIN is set, this screen is itself behind the PIN, so a child cannot simply
 * come here and unlock. The PIN is a family lock, not a vault: it's a four-digit code stored as a
 * salted hash, enough to stop casual access, not a serious attacker.
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
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Parental controls", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Done") }
        }

        Spacer(Modifier.height(20.dp))

        PinSection(pinIsSet = pinIsSet, onSetPin = settings::setPin, onClearPin = settings::clearPin)

        Spacer(Modifier.height(16.dp))

        Text("Hidden categories", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            "Anything switched on here is removed from Live TV until you turn on “Show hidden " +
                "now” below. With a PIN set, this screen stays locked, so hidden stays hidden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(max = 720.dp),
        )
        Spacer(Modifier.height(12.dp))

        Card {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Show hidden now (this session)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Reveals hidden categories until the app is restarted.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = unlocked, onCheckedChange = settings::setHiddenUnlocked)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (categories.isEmpty()) {
            Text(
                "No categories yet — add a provider first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.widthIn(max = 720.dp),
            ) {
                items(categories, key = { it.key }) { group ->
                    Card {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(group.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = group.key in hidden,
                                onCheckedChange = { on ->
                                    val next = hidden.toMutableSet().apply { if (on) add(group.key) else remove(group.key) }
                                    settings.setHiddenCategories(next)
                                },
                            )
                        }
                    }
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

    Text("PIN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
    Card {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (pinIsSet && !editing) {
                Text("A PIN is set.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { editing = true; pin = ""; confirm = ""; error = null }) {
                        Text("Change PIN")
                    }
                    OutlinedButton(onClick = onClearPin) { Text("Remove PIN") }
                }
            } else {
                Text(
                    if (pinIsSet) "Enter a new 4-digit PIN." else "Set a 4-digit PIN.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PinField("New PIN", pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
                    PinField("Confirm", confirm) { if (it.length <= 4) confirm = it.filter(Char::isDigit) }
                }
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = pin.length == 4 && confirm.length == 4,
                        onClick = {
                            when {
                                pin.length != 4 -> error = "PIN must be 4 digits."
                                pin != confirm -> error = "PINs don't match."
                                else -> {
                                    onSetPin(pin); editing = false; pin = ""; confirm = ""; error = null
                                }
                            }
                        },
                    ) { Text("Save PIN") }
                    if (editing) {
                        OutlinedButton(onClick = { editing = false; error = null }) { Text("Cancel") }
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

    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Enter PIN", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            PinField("PIN", pin) { if (it.length <= 4) pin = it.filter(Char::isDigit) }
            if (error) {
                Spacer(Modifier.height(8.dp))
                Text("Wrong PIN.", color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    enabled = pin.length == 4,
                    onClick = { if (!onSubmit(pin)) { error = true; pin = "" } },
                ) { Text("Unlock") }
                OutlinedButton(onClick = onCancel) { Text("Back") }
            }
        }
    }
}
