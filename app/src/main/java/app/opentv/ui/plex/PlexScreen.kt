/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.ui.plex

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.ui.theme.AppTheme
import app.opentv.ui.vod.PosterCard
import app.opentv.ui.vod.SectionHeader

/**
 * The Plex shelf: what has just arrived on the server.
 *
 * Built from the same poster card and row header as the Movies and Shows shelves rather than
 * anything new, so it is obviously the same app and obviously navigated the same way. Two shelves -
 * films and shows - because those are the two things a Plex library holds and the two ways a viewer
 * looks for something new.
 *
 * What it deliberately does *not* do is pretend to be a browser. There is no library list, no
 * search, no sorting: this answers one question, "what is new?", and the guide and the Movies
 * screen already cover the rest.
 */
@Composable
fun PlexScreen(
    onPlay: (mediaKey: String, url: String, title: String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlexViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val error by viewModel.lastError.collectAsStateWithLifecycle()

    // Pull once per open. A shelf of ten is cheap to refresh and is the whole point of the screen,
    // so arriving and seeing yesterday's titles would make it look broken.
    LaunchedEffect(Unit) { viewModel.sync() }

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        PlexHeader(isRefreshing = state is PlexShelfState.Syncing, onRefresh = { viewModel.sync() })

        when (val s = state) {
            is PlexShelfState.NotConnected -> PlexNotConnected(onConnect)
            // A refresh with nothing on screen yet: a spinner beats an empty grid that looks
            // like "your Plex has nothing".
            is PlexShelfState.Syncing -> if (s.movies == 0 && s.shows == 0) {
                PlexBusy()
            } else {
                PlexEmptyState(onConnect)
            }
            is PlexShelfState.Ready -> if (s.movies.isEmpty() && s.shows.isEmpty()) {
                PlexEmptyState(onConnect)
            } else {
                PlexShelves(s.movies, s.shows, onPlay, viewModel)
            }
            // Keep whatever is already on screen; the message underneath says what went wrong.
            is PlexShelfState.Failed -> if (s.movies.isEmpty() && s.shows.isEmpty()) {
                PlexEmptyState(onConnect)
            } else {
                PlexShelves(s.movies, s.shows, onPlay, viewModel)
            }
        }

        // A failure keeps the shelf it already had and says what went wrong underneath, rather
        // than replacing ten perfectly watchable titles with an error page.
        if (error != null) {
            PlexMessage(
                text = error!!,
                isError = true,
                onDismiss = { viewModel.clearError() },
            )
        }
    }
}

@Composable
private fun PlexHeader(isRefreshing: Boolean, onRefresh: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.plex_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.plex_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // A shelf of "recently added" is only useful if it can be brought up to date, and the sync
        // that fills it runs in the background. A button here means the viewer is never stuck with
        // yesterday's titles wondering whether Plex has anything new.
        if (isRefreshing) {
            CircularProgressIndicator(
                modifier = Modifier.width(22.dp).height(22.dp),
                strokeWidth = 2.dp,
                color = AppTheme.palette.favourite,
            )
        } else {
            TextButton(onClick = onRefresh) {
                Text(
                    stringResource(R.string.plex_refresh),
                    color = AppTheme.palette.favourite,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun PlexShelves(
    movies: List<Movie>,
    shows: List<Series>,
    onPlay: (String, String, String) -> Unit,
    viewModel: PlexViewModel,
) {
    // Entry focus belongs on the first card of the top shelf. Without this, focus lands wherever
    // the system finds something first - usually the movies row, since both rows compose before
    // data settles - and the column scrolls the shows half off the top. Cleared once granted, so
    // later recompositions (a background refresh landing new rows) never yank focus back.
    var entryFocusTaken by remember { mutableStateOf(false) }
    val topKey = shows.firstOrNull()?.let { "s:${it.id}" }
        ?: movies.firstOrNull()?.let { "m:${it.id}" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Shows first: serials are what gets checked nightly, films are browsed at weekends.
        if (shows.isNotEmpty()) {
            item(key = "plex-shows") {
                Column {
                    SectionHeader(stringResource(R.string.plex_recent_shows))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(shows, key = { "s:${it.id}" }) { series ->
                            PosterCard(
                                title = series.name,
                                posterUrl = series.posterUrl,
                                subtitle = series.year?.toString(),
                                onClick = { viewModel.playSeries(series, onPlay) },
                                requestFocus = !entryFocusTaken && "s:${series.id}" == topKey,
                                onFocusGranted = { entryFocusTaken = true },
                                cardWidth = PLEX_POSTER_WIDTH,
                            )
                        }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "plex-movies") {
                Column {
                    SectionHeader(stringResource(R.string.plex_recent_movies))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(movies, key = { "m:${it.id}" }) { movie ->
                            PosterCard(
                                title = movie.name,
                                posterUrl = movie.posterUrl,
                                subtitle = movie.year?.toString(),
                                onClick = { viewModel.playMovie(movie, onPlay) },
                                requestFocus = !entryFocusTaken && "m:${movie.id}" == topKey,
                                onFocusGranted = { entryFocusTaken = true },
                                cardWidth = PLEX_POSTER_WIDTH,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plex shelf card width. Smaller than the 140dp Movies/Shows cards so both shelves - headers,
 * posters, titles - read top to bottom on one screen instead of the second row starting
 * half-visible.
 */
private val PLEX_POSTER_WIDTH = 112.dp

@Composable
private fun PlexNotConnected(onConnect: () -> Unit) {
    PlexEmpty(
        title = stringResource(R.string.plex_not_connected_title),
        body = stringResource(R.string.plex_not_connected_body),
        actionLabel = stringResource(R.string.plex_connect_action),
        onAction = onConnect,
    )
}

@Composable
private fun PlexEmptyState(onConnect: () -> Unit) = PlexEmpty(
    title = stringResource(R.string.plex_empty_title),
    body = stringResource(R.string.plex_empty_body),
    actionLabel = stringResource(R.string.plex_refresh_action),
    onAction = onConnect,
)

@Composable
private fun PlexEmpty(
    title: String = stringResource(R.string.plex_empty_title),
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun PlexBusy() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = AppTheme.palette.favourite)
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.plex_fetching),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlexMessage(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isError) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Button(onClick = onDismiss) { Text(stringResource(R.string.plex_dismiss)) }
    }
}
