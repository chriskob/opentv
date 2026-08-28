/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF10171E))
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (val current = state) {
            is ManagerServer.State.Listening -> Listening(current.session, onBack)

            is ManagerServer.State.Failed -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.webmanager_stopped),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = current.reason,
                    color = Color.White.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { viewModel.start() }) { Text(stringResource(R.string.common_try_again)) }
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_done)) }
                }
            }

            ManagerServer.State.Idle -> CircularProgressIndicator(color = Color(0xFF26C6DA))
        }
    }
}

@Composable
private fun Listening(session: ManagerServer.Session, onBack: () -> Unit) {
    val qr = remember(session.url) { QrCodes.render(session.url, QR_SIZE_PX) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(48.dp),
    ) {
        if (qr != null) {
            Box(
                Modifier
                    .size(280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .border(2.dp, Color(0xFF26C6DA), RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Image(
                    bitmap = qr.asImageBitmap(),
                    contentDescription = stringResource(R.string.webmanager_qr_desc),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(Modifier.widthIn(max = 540.dp)) {
            Text(
                text = stringResource(R.string.webmanager_title),
                style = MaterialTheme.typography.headlineLarge.copy(fontSize = 28.sp),
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.webmanager_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.75f),
            )

            Spacer(Modifier.height(24.dp))

            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF18222C))
                    .border(0.5.dp, Color(0xFF263442), RoundedCornerShape(12.dp))
                    .padding(horizontal = 18.dp, vertical = 14.dp),
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.webmanager_open_browser).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF26C6DA),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = session.url,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.webmanager_local_only),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.6f),
            )

            Spacer(Modifier.height(28.dp))
            WebManagerBackButton(onBack)
        }
    }
}

@Composable
private fun WebManagerBackButton(onClick: () -> Unit) {
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
            .padding(horizontal = 24.dp, vertical = 12.dp),
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

private const val QR_SIZE_PX = 600

