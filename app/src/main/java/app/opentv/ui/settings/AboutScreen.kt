/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.settings

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.opentv.BuildConfig
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.update.UpdateChecker
import kotlinx.coroutines.launch

/**
 * About: what this is, what version it is, and how to get involved.
 */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var updateLine by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.about_title),
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 26.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "OpenTV application details, updates, and open-source licenses",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.65f),
                )
            }

            AboutBackButton(onBack)
        }

        Spacer(Modifier.height(24.dp))

        Section(stringResource(R.string.about_version)) {
            Text(
                text = "OpenTV ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    enabled = !checking,
                    onClick = {
                        checking = true
                        updateLine = null
                        scope.launch {
                            val graph = ServiceLocator.get(context)
                            val update = runCatching {
                                UpdateChecker(graph.httpClient, BuildConfig.VERSION_NAME).check()
                            }.getOrNull()
                            updateLine = when {
                                update != null -> context.getString(R.string.about_update_available, update.versionName)
                                else -> context.getString(R.string.about_up_to_date)
                            }
                            if (update != null) {
                                app.opentv.update.UpdateHub.state.value =
                                    app.opentv.update.UpdateUiState.Available(update)
                            }
                            checking = false
                        }
                    },
                ) {
                    Text(
                        if (checking) stringResource(R.string.about_checking)
                        else stringResource(R.string.about_check_updates),
                    )
                }
                updateLine?.let {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF26C6DA),
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section(stringResource(R.string.about_what_is_title)) {
            Text(
                stringResource(R.string.about_what_is_body),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.75f),
            )
        }

        Spacer(Modifier.height(20.dp))

        Section(stringResource(R.string.about_licence_links_title)) {
            LinkLine(
                stringResource(R.string.about_licence_label),
                "GNU General Public License v3.0",
                url = "https://www.gnu.org/licenses/gpl-3.0.html",
            )
            LinkLine(stringResource(R.string.about_source_code), "github.com/chriskob/opentv")
            LinkLine(stringResource(R.string.about_report_bug), "github.com/chriskob/opentv/issues")
            LinkLine(stringResource(R.string.about_install_page), "chriskob.github.io/opentv")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(
        text = title.uppercase(),
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
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) { content() }
    }
}

@Composable
private fun LinkLine(label: String, value: String, url: String = "https://$value") {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (focused) Color(0xFFF0F4F8)
                else Color.Transparent,
            )
            .then(
                if (focused) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .focusable()
            .clickable {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { Toast.makeText(context, url, Toast.LENGTH_LONG).show() }
            }
            .padding(vertical = 8.dp, horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label:  ",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) Color(0xFF10171E) else Color.White,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) Color(0xFF00838F) else Color(0xFF26C6DA),
        )
    }
}

@Composable
private fun AboutBackButton(onClick: () -> Unit) {
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
