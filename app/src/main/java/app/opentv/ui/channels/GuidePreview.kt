/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.channels

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Programme
import app.opentv.data.model.shownName
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.opentv.ui.ChannelsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The detail pane at the top of the guide.
 *
 * As focus moves down the channel list, [row] follows it and the pane shows that channel's
 * logo alongside what's on now, how far through it is, what's next, and a synopsis when the
 * guide carries one — the "info on the EPG" people ask for before they'll judge a guide.
 * Pressing it plays the channel full-screen.
 *
 * ## Inline video
 * When [previewPlayer] is non-null the highlighted channel plays, muted, inside the card. The
 * earlier version of this that locked up cheap boxes ran a *second* decoder behind the
 * full-screen one; here there is only ever this single, muted, debounced player — and the
 * caller ([HomeScreen]) stops it before handing off to full-screen and whenever the screen is
 * backgrounded, so the device never has two decoders alive at once. Low-end boxes can turn the
 * whole thing off in settings, in which case [previewPlayer] is null and this falls back to the
 * logo. The logo stays behind the video as the shutter, so a buffering or failed stream still
 * shows something rather than a black hole.
 */
@OptIn(UnstableApi::class)
@Composable
fun GuidePreview(
    row: ChannelsViewModel.Row?,
    programme: Programme? = null,
    nowMillis: Long,
    onWatch: () -> Unit,
    onRefresh: () -> Unit,
    onAddSource: () -> Unit,
    previewPlayer: ExoPlayer?,
    isRecording: Boolean = false,
    onRecord: () -> Unit = {},
    dayLabel: String = "",
    canGoPrevDay: Boolean = false,
    onPrevDay: () -> Unit = {},
    onNextDay: () -> Unit = {},
    modifier: Modifier = Modifier,
    onPreviewBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24 = remember(context) { android.text.format.DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24) {
        if (is24) SimpleDateFormat("HH:mm", Locale.getDefault())
        else SimpleDateFormat("hh:mm a", Locale.getDefault())
    }

    Row(
        modifier
            .fillMaxWidth()
            .height(186.dp)
            .padding(start = 8.dp, end = 12.dp, top = 2.dp, bottom = 2.dp),
    ) {
        // ---- 16:9 Video preview / Logo card (clean, matching TiviMate) ----
        Box(
            Modifier
                .fillMaxHeight()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .onGloballyPositioned { coords ->
                    onPreviewBoundsChanged?.invoke(coords.boundsInRoot())
                }
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (row != null && previewPlayer == null) {
                AsyncImage(
                    model = row.primary.logoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.65f),
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        // ---- Programme details (TiviMate structure) ----
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val displayProg = programme ?: row?.now

            // Line 1: Large Bold Programme Title + Star Icon on far right
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val titleText = displayProg?.title ?: row?.primary?.shownName ?: stringResource(R.string.guide_highlight_hint)
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 24.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (row != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = if (row.primary.favourite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (row.primary.favourite) Color(0xFFFFD54F) else Color(0xFF90A4AE),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // Line 2: Time range, inline progress bar, duration, Category/Source
            if (displayProg != null) {
                val startStr = timeFmt.format(Date(displayProg.startUtcMillis))
                val endStr = timeFmt.format(Date(displayProg.endUtcMillis))
                val durationMins = (displayProg.durationMillis / 60_000L).coerceAtLeast(1)
                val isLiveShow = nowMillis in displayProg.startUtcMillis until displayProg.endUtcMillis

                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$startStr – $endStr",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color(0xFFCFD8DC),
                            fontWeight = FontWeight.Normal,
                        )
                        if (isLiveShow) {
                            Spacer(Modifier.width(10.dp))
                            val progress = displayProg.progressAt(nowMillis)
                            Box(
                                Modifier
                                    .width(44.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF455A64)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                                        .height(3.dp)
                                        .background(Color.White),
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Text(
                                text = "  —  ",
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                                color = Color(0xFF78909C),
                            )
                        }
                        Text(
                            text = "$durationMins min",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                            color = Color(0xFFCFD8DC),
                            fontWeight = FontWeight.Normal,
                        )
                    }

                    val categoryTag = row?.primary?.qualityLabel?.ifBlank { row.primary.categoryId.orEmpty() }?.takeIf { it.isNotBlank() }
                    if (categoryTag != null) {
                        Text(
                            text = categoryTag,
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                            color = Color(0xFF90A4AE),
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }

                // Line 3: Description / Synopsis (up to 3 lines)
                val synopsis = displayProg.description?.takeIf { it.isNotBlank() }
                if (synopsis != null) {
                    Text(
                        text = synopsis,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, lineHeight = 18.sp),
                        color = Color(0xFFB0BEC5),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else if (row != null) {
                Text(
                    text = row.primary.shownName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF90A4AE),
                )
            }
        }
    }
}

private val previewTimeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatTime(utcMillis: Long): String = previewTimeFormat.format(Date(utcMillis))
