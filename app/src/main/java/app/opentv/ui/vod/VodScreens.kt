/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.ui.VodViewModel
import coil.compose.AsyncImage

/**
 * Movies: a category rail on the left, a poster grid on the right. Click a poster to play,
 * resuming where you left off. The rail mirrors Live TV's so the app feels of a piece.
 */
@Composable
fun MoviesScreen(
    onPlayMovie: (Movie) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.movieCategories.collectAsState()
    val movies by viewModel.movies.collectAsState()
    val resume by viewModel.continueWatching.collectAsState()

    Row(Modifier.fillMaxSize()) {
        CategoryRail(
            title = "Movies",
            entries = categories.map { it.id to it.name },
            onSelect = { viewModel.selectMovieCategory(it) },
        )
        Column(Modifier.weight(1f).fillMaxSize()) {
            if (resume.isNotEmpty()) ContinueWatchingRow(resume, onResume)
            if (movies.isEmpty()) {
                when {
                    isSyncing || hasSources -> LoadingVod("Loading movies")
                    else -> EmptyVod("No movies", "Add a provider to see its movies here.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(movies, key = { it.id }) { movie ->
                        Poster(
                            title = movie.name,
                            posterUrl = movie.posterUrl,
                            subtitle = movie.year?.toString(),
                            onClick = { onPlayMovie(movie) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Shows: poster grid → a series' episode list → play. Episodes are fetched on demand the
 * first time a series is opened, because pulling every episode of every series up front is
 * what makes a first sync take forever.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.seriesCategories.collectAsState()
    val series by viewModel.series.collectAsState()
    val resume by viewModel.continueWatching.collectAsState()

    Row(Modifier.fillMaxSize()) {
        CategoryRail(
            title = "Shows",
            entries = categories.map { it.id to it.name },
            onSelect = { viewModel.selectSeriesCategory(it) },
        )
        Column(Modifier.weight(1f).fillMaxSize()) {
            if (resume.isNotEmpty()) ContinueWatchingRow(resume, onResume)
            if (series.isEmpty()) {
                when {
                    isSyncing || hasSources -> LoadingVod("Loading shows")
                    else -> EmptyVod("No shows", "Add a provider to see its shows here.")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(series, key = { it.id }) { item ->
                        Poster(
                            title = item.name,
                            posterUrl = item.posterUrl,
                            subtitle = item.year?.toString(),
                            onClick = { onOpenSeries(item) },
                        )
                    }
                }
            }
        }
    }
}

/** A series' episodes, grouped by season, each a row that plays on click. */
@Composable
fun SeriesDetailScreen(
    seriesId: Long,
    onPlayEpisode: (mediaKey: String, url: String, title: String) -> Unit,
    viewModel: VodViewModel = viewModel(),
) {
    val series by viewModel.seriesById(seriesId).collectAsState(initial = null)
    val episodes by (series?.let { viewModel.episodes(it) } ?: viewModel.noEpisodes)
        .collectAsState(initial = emptyList())

    androidx.compose.runtime.LaunchedEffect(series) {
        series?.let { viewModel.loadEpisodes(it) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(series?.name ?: "…", style = MaterialTheme.typography.headlineMedium)
        series?.plot?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(16.dp))

        if (episodes.isEmpty()) {
            Text("Loading episodes…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(episodes, key = { it.id }) { ep ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable {
                                onPlayEpisode("ep:${ep.id}", ep.streamUrl,
                                    "S${ep.season}E${ep.episodeNumber} · ${ep.title}")
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("S${ep.season}E${ep.episodeNumber}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(72.dp))
                        Text(ep.title, style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

// ---- Shared bits -------------------------------------------------------------------------

@Composable
private fun CategoryRail(
    title: String,
    entries: List<Pair<String, String>>,
    onSelect: (String?) -> Unit,
) {
    Column(
        Modifier
            .width(230.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 20.dp))
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item {
                RailText("All", true) { onSelect(null) }
            }
            items(entries, key = { it.first }) { (id, name) ->
                RailText(name, false) { onSelect(id) }
            }
        }
    }
}

@Composable
private fun RailText(label: String, bold: Boolean, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    )
}

@Composable
private fun Poster(title: String, posterUrl: String?, subtitle: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        subtitle?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ContinueWatchingRow(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            "Continue watching",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(items, key = { it.mediaKey }) { item ->
                ResumeCard(item) { onResume(item.mediaKey, item.streamUrl, item.title) }
            }
        }
    }
}

@Composable
private fun ResumeCard(item: VodViewModel.ResumeItem, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(150.dp)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (focused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                else Modifier,
            )
            .clickable(onClick = onClick)
            .padding(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(84.dp),
            )
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomStart),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EmptyVod(title: String, body: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LoadingVod(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "A large provider can take a couple of minutes the first time.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
