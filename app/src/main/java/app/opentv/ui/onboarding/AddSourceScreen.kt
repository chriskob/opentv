/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.ui.SourcesViewModel

/**
 * First-run setup.
 *
 * Two things this screen does that most IPTV players do not:
 *
 * 1. **Test before saving.** The user finds out the password is wrong here, with a sentence
 *    explaining what to change, rather than staring at an empty channel list later.
 * 2. **Says where the credentials go.** People are handing over their provider login. They
 *    are entitled to know it stays on the device, and to be told so without having to go
 *    looking for a privacy policy.
 */
@Composable
fun AddSourceScreen(
    viewModel: SourcesViewModel,
    onFinished: () -> Unit,
) {
    // Offered first, because typing a server address and password with a d-pad is the worst
    // moment in every app of this kind. Typing on the TV is still there for anyone who
    // prefers it, or who has no phone to hand.
    var usePhone by remember { mutableStateOf(true) }

    if (usePhone) {
        PhonePairingScreen(
            onReceived = { draft -> viewModel.saveAndSync(draft) { ok -> if (ok) onFinished() } },
            onCancel = { usePhone = false },
        )
        return
    }

    val ui by viewModel.ui.collectAsState()
    val context = LocalContext.current

    var kind by remember { mutableStateOf(SourceKind.XTREAM) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var epgUrl by remember { mutableStateOf("") }
    var showAdvanced by remember { mutableStateOf(false) }
    var userAgent by remember { mutableStateOf(Source.DEFAULT_USER_AGENT) }

    fun draft() = Source(
        name = name.ifBlank {
            if (kind == SourceKind.XTREAM) context.getString(R.string.onboarding_default_provider_name)
            else context.getString(R.string.onboarding_default_playlist_name)
        },
        kind = kind,
        url = url,
        username = username.takeIf { it.isNotBlank() },
        password = password.takeIf { it.isNotBlank() },
        epgUrl = epgUrl.takeIf { it.isNotBlank() },
        userAgent = userAgent.ifBlank { Source.DEFAULT_USER_AGENT },
    )

    val canSubmit = url.isNotBlank() &&
        (kind == SourceKind.M3U || (username.isNotBlank() && password.isNotBlank()))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.widthIn(max = 640.dp)) {
            Text(stringResource(R.string.onboarding_add_provider_title), style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.onboarding_add_provider_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(24.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = kind == SourceKind.XTREAM,
                    onClick = { kind = SourceKind.XTREAM },
                    label = { Text(stringResource(R.string.onboarding_xtream_login)) },
                )
                FilterChip(
                    selected = kind == SourceKind.M3U,
                    onClick = { kind = SourceKind.M3U },
                    label = { Text(stringResource(R.string.onboarding_m3u_url)) },
                )
            }

            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.epg_name_optional)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = {
                    Text(if (kind == SourceKind.XTREAM) stringResource(R.string.onboarding_server_address) else stringResource(R.string.onboarding_playlist_url))
                },
                supportingText = {
                    Text(
                        if (kind == SourceKind.XTREAM) {
                            stringResource(R.string.onboarding_server_help)
                        } else {
                            stringResource(R.string.onboarding_playlist_help)
                        },
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )

            if (kind == SourceKind.XTREAM) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(stringResource(R.string.recset_field_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.recset_field_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showAdvanced = !showAdvanced }) {
                Text(if (showAdvanced) stringResource(R.string.onboarding_hide_advanced) else stringResource(R.string.onboarding_advanced))
            }

            if (showAdvanced) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = epgUrl,
                    onValueChange = { epgUrl = it },
                    label = { Text(stringResource(R.string.onboarding_guide_url_optional)) },
                    supportingText = {
                        Text(stringResource(R.string.onboarding_guide_blank_help))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = userAgent,
                    onValueChange = { userAgent = it },
                    label = { Text("User-Agent") },
                    supportingText = {
                        Text(stringResource(R.string.onboarding_user_agent_help))
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(20.dp))

            ui.testResult?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Text(message, Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(12.dp))
            }
            ui.testError?.let { message ->
                Card(Modifier.fillMaxWidth()) {
                    Text(
                        message,
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
            ui.syncMessage?.let { message ->
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { viewModel.test(draft()) },
                    enabled = canSubmit && !ui.testing && !ui.syncing,
                ) {
                    if (ui.testing) {
                        CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.recset_test_connection))
                    }
                }
                Button(
                    onClick = { viewModel.saveAndSync(draft()) { ok -> if (ok) onFinished() } },
                    enabled = canSubmit && !ui.testing && !ui.syncing,
                ) {
                    Text(if (ui.syncing) stringResource(R.string.onboarding_working) else stringResource(R.string.onboarding_save_load))
                }
            }

            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = { usePhone = true }) {
                Text(stringResource(R.string.onboarding_use_phone))
            }

            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(R.string.onboarding_privacy_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
