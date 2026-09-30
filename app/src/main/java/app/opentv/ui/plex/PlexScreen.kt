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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
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
        // No top header: the hero below already says what this screen is ("NEW ON PLEX" plus the
        // title) and carries its own Refresh. A second title row above it was chrome for its own
        // sake, and the freed ~80dp helps both shelves read on one screen.
        when (val s = state) {
            is PlexShelfState.NotConnected -> PlexNotConnected(onConnect)
            // A refresh keeps whatever is on screen and spins the hero's refresh mark instead of
            // blanking: only a first-ever load with nothing to show gets the spinner.
            is PlexShelfState.Syncing -> if (s.currentMovies.isEmpty() && s.currentShows.isEmpty()) {
                PlexBusy()
            } else {
                PlexShelves(s.currentMovies, s.currentShows, onPlay, viewModel)
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
private fun PlexShelves(
    movies: List<Movie>,
    shows: List<Series>,
    onPlay: (String, String, String) -> Unit,
    viewModel: PlexViewModel,
) {
    // Entry focus belongs on the first card of the top shelf. Without this, focus lands
    // wherever the system finds something first and the column scrolls the shows half off the
    // top. Cleared once granted, so later recompositions never yank focus back.
    var entryFocusTaken by remember { mutableStateOf(false) }
    val topKey = shows.firstOrNull()?.let { "s:${it.id}" }
        ?: movies.firstOrNull()?.let { "m:${it.id}" }
    // Which card the spotlight is following. Null until the viewer moves - the hero opens on the
    // newest show, and only hands over once someone actually browses.
    var spotlightKey by remember { mutableStateOf<String?>(null) }
    val hero = heroFor(spotlightKey, shows, movies)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        if (hero != null) {
            item(key = "plex-hero") {
                PlexHero(hero = hero)
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
                                            requestFocus = !entryFocusTaken && "s:${series.id}" == topKey,
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
        }
        item(key = "plex-bottom-pad") { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * Whatever the spotlight is showing: the focused card if the viewer is browsing, else the newest
 * show, else the newest film. Display only - the posters below are what plays.
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
        override val artUrl = series.posterUrl
        override val meta = series.year?.toString()
        override val plot = series.plot
    }

    data class Film(val movie: Movie) : HeroItem {
        override val key = "m:${movie.id}"
        override val title = movie.name
        override val artUrl = movie.posterUrl
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
 * The spotlight: full-bleed fanart with cinematic scrims and the title set large. The backdrop
 * crossfades as the viewer browses the rows below, so the whole screen breathes with them.
 * Display only - playback starts from the posters.
 */
@Composable
private fun PlexHero(
    hero: HeroItem,
) {
    val bg = MaterialTheme.colorScheme.background
    // Measured, not assumed. Four rounds of crop tuning failed because the visible window was a
    // guess; this records what the hero box and the served art actually are, in pixels, so the
    // next complaint is answered with numbers instead of another photo.
    val context = LocalContext.current
    var heroBox by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
            .clipToBounds()
            .onGloballyPositioned { heroBox = it.size },
    ) {
        // Ambient only. Blurred past recognition on purpose, so its crop is cosmetic by
        // construction. This is what the eye reads as "the show", while the artwork that has to
        // survive a crop is drawn whole further down.
        Crossfade(targetState = hero.artUrl, label = "plexHeroAmbience") { art ->
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(30.dp),
                )
                // Heavy dim so the blur reads as ambiance, not content, and the title always wins.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
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
                    0.62f to Color.Transparent,
                ),
            ),
        )
        // Key art, shown whole. ContentScale.Fit is the entire fix: whatever Plex serves - 2:3,
        // 3:4, anything - the whole poster is on screen, head included, because nothing is ever
        // cut off. Cropping a portrait poster into a landscape hero can only ever show about a
        // quarter of it, and where the subject lands inside that window is a coin toss no
        // alignment value can win. Ambient above does the filling; this is the picture.
        Crossfade(targetState = hero.artUrl, label = "plexHeroKeyArt") { art ->
            if (art != null) {
                AsyncImage(
                    model = art,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 30.dp)
                        .width(226.dp)
                        .height(340.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
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
            // No actions here at all. The posters below are the play buttons, and the screen
            // refreshes itself on every open - a manual Refresh would only ever re-ask a question
            // that was just answered.
        }
    }
    // The measurement that was missing while four crop values were being tuned blind: the hero
    // box in real pixels, next to the size Plex actually served. It fires once the box has been
    // measured and whenever the spotlight moves.
    LaunchedEffect(hero.artUrl, heroBox) {
        if (heroBox == IntSize.Zero) return@LaunchedEffect
        val art = runCatching {
            context.imageLoader.execute(ImageRequest.Builder(context).data(hero.artUrl).build())
        }.getOrNull() as? SuccessResult
        val d = art?.drawable
        android.util.Log.i(
            "OpenTV-Plex",
            "hero ${hero.key} box=${heroBox.width}x${heroBox.height}px " +
                "art=${d?.intrinsicWidth}x${d?.intrinsicHeight}px density=${context.resources.displayMetrics.density}",
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
