/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.ui.plex

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.core.ServiceLocator
import app.opentv.pairing.QrCodes
import app.opentv.data.remote.PlexApi
import app.opentv.data.remote.PlexUrls
import app.opentv.ui.components.TvOutlinedTextField
import app.opentv.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Connects a Plex server.
 *
 * Two steps, because that is what Plex sign-in actually is. First the viewer types the address of
 * their own server; then, because a television cannot show a Plex login page in a way anyone can
 * use, the app asks plex.tv for a short code and the viewer approves it on the phone they are
 * already holding. The code is polled for while the screen sits there, so the moment the phone is
 * approved the app moves on by itself - there is no second button to press and nothing to get wrong.
 *
 * The token is written into the source's password column and is never shown again. The approval URL
 * is displayed rather than opened, because a TV box has no browser to open it in.
 */
@Composable
fun PlexConnectScreen(
    onConnected: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val settings = remember { AppSettings.get(context) }
    val viewModel: PlexViewModel = viewModel()

    val product = stringResource(R.string.app_name)
    val device = "OpenTV on ${android.os.Build.MODEL}"

    var serverUrl by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf<PlexParserPin?>(null) }
    var stage by remember { mutableStateOf(PlexConnectStage.ADDRESS) }
    var failure by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Once a code exists, poll until it is approved. Launched on the code rather than in a loop
    // inside the click handler, so a recomposition cannot start a second poller.
    LaunchedEffect(pin?.id) {
        val pending = pin ?: return@LaunchedEffect
        stage = PlexConnectStage.CODE
        val headers = PlexUrls.clientHeaders(
            clientIdentifier = settings.plexClientIdentifier,
            product = product,
            version = appVersion(context),
            device = device,
            platform = "Android",
        )
        // 15 minutes is the window Plex allows; past that the code is dead and asking again is the
        // only way forward, so stop rather than poll a code that can never be approved.
        repeat(300) {
            delay(3_000L)
            val token = runCatching { graph.plexApi.awaitAuthToken(pending.id, headers) }.getOrNull()
            if (token != null) {
                saving = true
                viewModel.addPlexSource("Plex", serverUrl.trim(), token) { ok ->
                    saving = false
                    if (ok) onConnected() else failure = "That Plex server could not be saved."
                }
                return@LaunchedEffect
            }
        }
        failure = "That code was not approved in time. Start again to get a new one."
    }

    Column(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp),
    ) {
        Text(
            stringResource(R.string.plex_connect_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(20.dp))

        when (stage) {
            PlexConnectStage.ADDRESS -> {
                Text(
                    stringResource(R.string.plex_connect_step1),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                TvOutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text(stringResource(R.string.plex_server_address)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.plex_connect_step1_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        enabled = serverUrl.isNotBlank() && failure == null,
                        onClick = {
                            failure = null
                            starting = true
                            scope.launch {
                                val headers = PlexUrls.clientHeaders(
                                    clientIdentifier = settings.plexClientIdentifier,
                                    product = product,
                                    version = appVersion(context),
                                    device = device,
                                    platform = "Android",
                                )
                                val created = runCatching { graph.plexApi.createPin(headers) }.getOrNull()
                                starting = false
                                pin = created?.let { PlexParserPin(it.id, it.code) }
                                if (pin == null) {
                                    failure = "Plex would not start a sign-in. This box may not be able " +
                                        "to reach plex.tv."
                                }
                            }
                        },
                    ) {
                        if (starting) {
                            CircularProgressIndicator(
                                modifier = Modifier.width(18.dp).height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text(stringResource(R.string.plex_connect_next))
                        }
                    }
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.plex_cancel)) }
                }
            }

            PlexConnectStage.CODE -> {
                Text(
                    stringResource(R.string.plex_connect_step2),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(20.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppTheme.palette.favourite.copy(alpha = 0.16f))
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        pin?.code.orEmpty(),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.palette.favourite,
                        letterSpacing = 10.sp,
                    )
                }
                Spacer(Modifier.height(20.dp))
                Text(
                    stringResource(R.string.plex_connect_step2_help),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                // The fallback is stated, not hidden behind a help menu. Reading a code off a
                // television and typing it into a website is a perfectly normal way to do this,
                // and the previous version of this screen only offered the one route - which is
                // how someone ended up hunting the Plex app for a menu item that does not exist.
                Text(
                    stringResource(R.string.plex_connect_step2_alt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                val approval = remember(pin?.code, product, device) {
                    pin?.let {
                        PlexUrls.pinApprovalUrl(
                            settings.plexClientIdentifier,
                            it.code,
                            product,
                            device,
                        )
                    }.orEmpty()
                }
                // A QR code, the same way the phone, remote and web pairing screens do it.
                //
                // Asking a viewer to read a two-line URL off a television and retype it into a
                // phone is a bad way to start a sign-in, and it is the step most likely to be
                // abandoned. Pointing a camera at the screen removes the transcription entirely:
                // the phone opens the link, signs in, and this screen carries on by itself.
                //
                // The text underneath is kept, not replaced. A code that will not scan - bad
                // light, a glare across the panel, the TV at an awkward angle - must still leave
                // a way through.
                val qr = remember(approval) { QrCodes.render(approval, QR_SIZE_PX) }
                if (approval.isNotEmpty() && qr != null) {
                    Image(
                        bitmap = qr.asImageBitmap(),
                        contentDescription = stringResource(R.string.plex_qr_desc),
                        modifier = Modifier
                            .size(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (approval.isNotEmpty()) {
                    Text(
                        approval,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (saving) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.width(20.dp).height(20.dp),
                            strokeWidth = 2.dp,
                            color = AppTheme.palette.favourite,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            stringResource(R.string.plex_connect_connecting),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    OutlinedButton(onClick = onCancel) { Text(stringResource(R.string.plex_cancel)) }
                }
            }
        }

        if (failure != null) {
            Spacer(Modifier.height(20.dp))
            Text(
                failure!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** The two halves of connecting: the server address, then the code the phone approves. */
private enum class PlexConnectStage { ADDRESS, CODE }

/** A sign-in code and the id it is polled under. */
private data class PlexParserPin(val id: Long, val code: String)

/** Enough pixels that the code still scans from a couch at an angle. Matches the pairing screens. */
private const val QR_SIZE_PX = 600

/** The app's version string, which Plex records against the session. */
private fun appVersion(context: android.content.Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "0.0.0"
