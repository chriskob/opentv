/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.pairing.ManagerServer
import app.opentv.pairing.QrCodes
import app.opentv.ui.WebManagerViewModel
import app.opentv.ui.settings.components.*
import app.opentv.ui.theme.AppTheme

/**
 * "Manage your channels from your phone or laptop."
 */
@Composable
fun WebManagerScreen(
    onBack: () -> Unit,
    viewModel: WebManagerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()

    DisposableEffect(Unit) {
        viewModel.start()
        onDispose {
            viewModel.stop()
        }
    }

    SettingsPage(
        title = stringResource(R.string.webmanager_title),
        subtitle = stringResource(R.string.webmanager_desc),
        onBack = onBack,
    ) {
        when (val current = state) {
            is ManagerServer.State.Listening -> Listening(current.session)

            is ManagerServer.State.Failed -> Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.webmanager_stopped),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current.reason,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SettingsButton(
                        text = stringResource(R.string.common_try_again),
                        onClick = { viewModel.start() },
                        style = SettingsButtonStyle.Primary,
                    )
                    SettingsButton(
                        text = stringResource(R.string.common_done),
                        onClick = onBack,
                        style = SettingsButtonStyle.Secondary,
                    )
                }
            }

            ManagerServer.State.Idle -> Box(
                Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = AppTheme.primary)
            }
        }
    }
}

@Composable
private fun Listening(session: ManagerServer.Session) {
    val qr = remember(session.url) { QrCodes.render(session.url, QR_SIZE_PX) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        if (qr != null) {
            Box(
                Modifier
                    .widthIn(max = 280.dp)
                    .aspectRatio(1f)
                    .clip(SettingsShape.Card)
                    .background(Color.White)
                    .border(2.dp, AppTheme.primary, SettingsShape.Card)
                    .padding(14.dp),
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.webmanager_qr_desc),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(Modifier.weight(1f).widthIn(max = 540.dp)) {
            SettingsCard {
                Text(
                    text = stringResource(R.string.webmanager_open_browser).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = session.url,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.webmanager_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val QR_SIZE_PX = 600
