/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.ui.plex

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
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
            // A refresh keeps whatever is on screen and spins the hero's refresh mark instead of
            // blanking: only a first-ever load with nothing to show gets the spinner.
            is PlexShelfState.Syncing -> if (s.currentMovies.isEmpty() && s.currentShows.isEmpty()) {
                PlexBusy()
            } else {
                PlexShelves(s.currentMovies, s.currentShows, isRefreshing = true, onPlay, { viewModel.sync() }, viewModel)
            }
            is PlexShelfState.Ready -> if (s.movies.isEmpty() && s.shows.isEmpty()) {
                PlexEmptyState(onConnect)
            } else {
                PlexShelves(s.movies, s.shows, isRefreshing = false, onPlay, { viewModel.sync() }, viewModel)
            }
            // Keep whatever is already on screen; the message underneath says what went wrong.
            is PlexShelfState.Failed -> if (s.movies.isEmpty() && s.shows.isEmpty()) {
                PlexEmptyState(onConnect)
            } else {
                PlexShelves(s.movies, s.shows, isRefreshing = false, onPlay, { viewModel.sync() }, viewModel)
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
    isRefreshing: Boolean,
    onPlay: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    viewModel: PlexViewModel,
) {
    // Entry focus belongs on the hero Play button: the screen opens at the top, one press plays
    // the newest thing, and browsing the rows below swings the spotlight to whatever has focus.
    var heroFocusTaken by remember { mutableStateOf(false) }
    val heroFocus = remember { FocusRequester() }
    // Which card the spotlight is following. Null until the viewer moves - the hero opens on the
    // newest show, and only hands over once someone actually browses.
    var spotlightKey by remember { mutableStateOf<String?>(null) }
    val hero = heroFor(spotlightKey, shows, movies)
    LaunchedEffect(hero?.key) {
        if (!heroFocusTaken && hero != null) {
            runCatching { heroFocus.requestFocus() }
            heroFocusTaken = true
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item(key = "plex-hero") {
                PlexHero(
                    hero = hero,
                    isRefreshing = isRefreshing,
                    focusRequester = heroFocus,
                    onPlay = {
                        when (hero) {
                            is HeroItem.Show -> viewModel.playSeries(hero.series, onPlay)
                            is HeroItem.Film -> viewModel.playMovie(hero.movie, onPlay)
                        }
                    },
                    onRefresh = onRefresh,
                )
            }
        }
        // Shows first: serials are what gets checked nightly, films are browsed at weekends.
        if (shows.isNotEmpty()) {
            item(key = "plex-shows") {
                Column(modifier = Modifier.padding(top = 4.dp)) {
                    SectionHeader(stringResource(R.string.plex_recent_shows))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(shows, key = { _, s -> "s:${s.id}" }) { index, series ->
                            Entrance(index) {
                                FocusSpot(
                                    onFocused = { spotlightKey = "s:${series.id}" },
                                ) {
                                    NewRibbon(isNew = isFresh(series.addedMillis)) {
                                        PosterCard(
                                            title = series.name,
                                            posterUrl = series.posterUrl,
                                            subtitle = series.year?.toString(),
                                            onClick = { viewModel.playSeries(series, onPlay) },
                                            cardWidth = PLEX_POSTER_WIDTH,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (movies.isNotEmpty()) {
            item(key = "plex-movies") {
                Column(modifier = Modifier.padding(top = 20.dp)) {
                    SectionHeader(stringResource(R.string.plex_recent_movies))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        itemsIndexed(movies, key = { _, m -> "m:${m.id}" }) { index, movie ->
                            Entrance(index) {
                                FocusSpot(
                                    onFocused = { spotlightKey = "m:${movie.id}" },
                                ) {
                                    NewRibbon(isNew = isFresh(movie.addedMillis)) {
                                        PosterCard(
                                            title = movie.name,
                                            posterUrl = movie.posterUrl,
                                            subtitle = movie.year?.toString(),
                                            onClick = { viewModel.playMovie(movie, onPlay) },
                                            cardWidth = PLEX_POSTER_WIDTH,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item(key = "plex-bottom-pad") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Whatever the spotlight is showing: the focused card if the viewer is browsing, else the newest
 * show, else the newest film. The hero Play button plays exactly what the hero shows, so browsing
 * and playing never disagree about what "this" is.
 */
private sealed interface HeroItem {
    val key: String
    val title: String
    val artUrl: String?
    val meta: String?
    val plot: String?

    data class Show(val series: Series) : HeroItem {
        override val key = "s:${series.id}"
        override val title = series.name
        override val artUrl = series.backdropUrl ?: series.posterUrl
        override val meta = series.year?.toString()
        override val plot = series.plot
    }

    data class Film(val movie: Movie) : HeroItem {
        override val key = "m:${movie.id}"
        override val title = movie.name
        override val artUrl = movie.backdropUrl ?: movie.posterUrl
        override val meta = listOfNotNull(
            movie.year?.toString(),
            movie.durationSeconds?.let { formatPlexRuntime(it) },
        ).joinToString("  •  ").takeIf { it.isNotEmpty() }
        override val plot = movie.plot
    }
}

private fun heroFor(focusedKey: String?, shows: List<Series>, movies: List<Movie>): HeroItem? {
    focusedKey?.let { key ->
        if (key.startsWith("s:")) shows.firstOrNull { "s:${it.id}" == key }?.let { return HeroItem.Show(it) }
        else movies.firstOrNull { "m:${it.id}" == key }?.let { return HeroItem.Film(it) }
    }
    shows.firstOrNull()?.let { return HeroItem.Show(it) }
    movies.firstOrNull()?.let { return HeroItem.Film(it) }
    return null
}

private fun formatPlexRuntime(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

/** Fresh enough to wear the ribbon: joined the library in the last seven days. */
private fun isFresh(addedMillis: Long): Boolean {
    if (addedMillis <= 0L) return false
    return System.currentTimeMillis() - addedMillis < 7L * 24 * 3600_000L
}

/**
 * The spotlight: full-bleed fanart with cinematic scrims, the title set large, and a gold Play
 * button that plays exactly what is shown. The backdrop crossfades as the viewer browses the rows
 * below, so the whole screen breathes with them.
 */
@Composable
private fun PlexHero(
    hero: HeroItem,
    isRefreshing: Boolean,
    focusRequester: FocusRequester,
    onPlay: () -> Unit,
    onRefresh: () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.background
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp),
    ) {
        Crossfade(targetState = hero.artUrl, label = "plexHeroArt") { art ->
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2A2118), Color(0xFF14100A)),
                            ),
                        ),
                )
            }
        }
        // Scrims: bottom melt into the page, left lift behind the text. Without both, bright
        // fanart eats the title on some libraries and the hero reads as a wallpaper.
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.35f to Color.Transparent,
                    1f to bg,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to bg.copy(alpha = 0.72f),
                    0.55f to Color.Transparent,
                ),
            ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.66f)
                .padding(start = 28.dp, end = 16.dp, bottom = 20.dp),
        ) {
            Text(
                stringResource(R.string.plex_new_on),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = 3.sp,
                color = PlexGold,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                hero.title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            hero.meta?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
            hero.plot?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(6.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlexGoldButton(
                    label = stringResource(R.string.plex_play),
                    onClick = onPlay,
                    focusRequester = focusRequester,
                )
                Spacer(Modifier.width(12.dp))
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(22.dp).height(22.dp),
                        strokeWidth = 2.dp,
                        color = PlexGold,
                    )
                } else {
                    TextButton(onClick = onRefresh) {
                        Text(
                            stringResource(R.string.plex_refresh),
                            color = PlexGold,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The gold Play button. Same focus language as the poster cards (lift + light border) so it reads
 * as part of the same screen, but filled gold on near-black ink: from the couch there must never
 * be any doubt which control plays the thing.
 */
@Composable
private fun PlexGoldButton(
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "plexPlayScale")
    Row(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRequester(focusRequester)
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(PlexGold)
            .then(if (focused) Modifier.border(3.dp, Color.White, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
        )
    }
}

/**
 * Reports the focused card upward so the hero can follow the browsing. A plain wrapper: the card
 * keeps its own focusable, and focus bubbling tells the parent it has arrived.
 */
@Composable
private fun FocusSpot(
    onFocused: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.onFocusChanged { if (it.hasFocus) onFocused() },
    ) {
        content()
    }
}

/**
 * Staggered entrance: cards fade and glide in a few dozen milliseconds apart, so the shelf lands
 * as a wave rather than popping in all at once. One orchestrated arrival beats scattered motion.
 */
@Composable
private fun Entrance(
    index: Int,
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay((index * 45L).coerceAtMost(450L))
        shown = true
    }
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f,
        animationSpec = tween(300),
        label = "plexEnter",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationX = (1f - alpha) * 32f
        },
    ) {
        content()
    }
}

/**
 * The gold NEW ribbon: a small flag on the poster's top corner for anything that joined the
 * library this week. The shelf's whole job is "what is new", and the ribbon answers it at a
 * glance without reading a single date.
 */
@Composable
private fun NewRibbon(
    isNew: Boolean,
    content: @Composable () -> Unit,
) {
    Box {
        content()
        if (isNew) {
            Text(
                stringResource(R.string.plex_new),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp, top = 8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(PlexGold)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * Plex shelf card width. Smaller than the 140dp Movies/Shows cards so both shelves - headers,
 * posters, titles - read top to bottom on one screen instead of the second row starting
 * half-visible.
 */
private val PLEX_POSTER_WIDTH = 112.dp

/**
 * The Plex section's own identity: brand gold on near-black. Deliberately not a theme accent -
 * the whole point is that this corner of the app reads as Plex the moment it opens, whatever
 * theme the rest of the app wears.
 */
internal val PlexGold = Color(0xFFE5A00D)

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
