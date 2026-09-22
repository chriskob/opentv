/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.vod

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
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
import androidx.lifecycle.viewmodel.compose.viewModel
import app.opentv.R
import app.opentv.core.AppSettings
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.parser.displayTitle
import app.opentv.ui.VodViewModel
import app.opentv.ui.components.PrefetchImagesAround
import app.opentv.ui.components.posterRequest
import app.opentv.ui.components.tvFocus
import app.opentv.ui.theme.AppTheme
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Movies: a modern, row-based home — Continue Watching, Recommended, Recently added and a row per
 * genre, each a horizontally-scrolling shelf of poster cards. A category chip strip along the top
 * keeps whole-category browsing one press away without a permanent rail eating the width. Clicking
 * a film opens its detail page rather than playing straight away, the streaming-app convention.
 */
@Composable
fun MoviesScreen(
    onOpenMovie: (Movie) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.movieCategories.collectAsState()
    val categoryCounts by viewModel.movieCategoryCounts.collectAsState()
    val resume by viewModel.continueWatching.collectAsState()
    val recommended by viewModel.recommendedMovies.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedMovies.collectAsState()
    val genreRows by viewModel.movieGenreRows.collectAsState()
    val categoryMovies by viewModel.movies.collectAsState()
    val vodLoading by viewModel.vodLoading.collectAsState()
    // Only the providers made of films: a playlist added for live TV alone has nothing to browse
    // here, so it must not appear in this rail's filter.
    val providers by viewModel.movieSources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()
    val context = LocalContext.current
    val settings = remember { AppSettings.get(context) }
    val sort by settings.movieSort.collectAsState()
    val providerNames = remember(providers) { providers.associate { it.id to it.name } }

    // Pull the movie library the first time this tab is opened, not at login; refresh the computed
    // home rows (recommended, by-genre) on open too — cheap, and covers a library already on disk.
    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }

    // null = the curated home rows; a category id = that category's full grid.
    // Saveable: BACK from a detail pops back to HOME (rebuilt from scratch), and the viewer
    // expects the category they were browsing — not the shelves.
    var browseCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // BACK returns from a category to the shelves. The rail has no "All" row to do it — that entry
    // was removed because the unfiltered list behind it pulled the whole library in one go — so the
    // way out has to be explicit. Declared here so it takes precedence over MainScreen's handler
    // only while a category is actually open.
    BackHandler(enabled = browseCategory != null) { browseCategory = null }

    val hasContent = resume.isNotEmpty() || recommended.isNotEmpty() ||
        recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()

    // Records the opened title so BACK from its detail can restore scroll + focus onto it.
    val openMovie: (Movie) -> Unit = { movie ->
        viewModel.lastOpenedMovieId = movie.id
        onOpenMovie(movie)
    }

    Column(Modifier.fillMaxSize()) {
        VodToolbar(
            onOpenSearch = onOpenSearch,
            sort = sort,
            onSelectSort = settings::setMovieSort,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Content is declared FIRST so it takes focus when the tab opens — the rail must
            // never steal the d-pad on entry — and is inset by the rail's width.
            Box(Modifier.fillMaxSize().padding(start = VOD_RAIL_WIDTH)) {
                when {
                    browseCategory != null -> MovieCategoryGrid(
                        categoryMovies, viewModel, openMovie,
                        sort = sort,
                        providerNames = providerNames,
                        returnToId = viewModel.lastOpenedMovieId,
                        onReturnFocusGranted = { viewModel.lastOpenedMovieId = null },
                    )
                    !hasContent -> when {
                        vodLoading || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_movies))
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_movies), stringResource(R.string.vod_no_movies_add))
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (resume.isNotEmpty()) item(key = "cw") {
                            ContinueWatchingRow(
                                resume, onResume,
                                onBareCard = viewModel::fillArtworkForKey,
                                onArtworkFailed = viewModel::replaceDeadArtworkForKey,
                            )
                        }
                        if (recommended.isNotEmpty()) item(key = "rec") {
                            MoviePosterRow(
                                stringResource(R.string.vod_recommended), recommended, openMovie,
                                returnToId = viewModel.lastOpenedMovieId,
                                onReturnFocusGranted = { viewModel.lastOpenedMovieId = null },
                                onBareCard = viewModel::fillArtworkFor,
                                onArtworkFailed = { viewModel.replaceDeadMovieArtwork(it.id) },
                            )
                        }
                        if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                            MoviePosterRow(
                                stringResource(R.string.vod_recently_added), recentlyAdded, openMovie,
                                returnToId = viewModel.lastOpenedMovieId,
                                onReturnFocusGranted = { viewModel.lastOpenedMovieId = null },
                                onBareCard = viewModel::fillArtworkFor,
                                onArtworkFailed = { viewModel.replaceDeadMovieArtwork(it.id) },
                            )
                        }
                        items(genreRows, key = { "g:${it.genre}" }) { group ->
                            MoviePosterRow(
                                group.genre, group.items, openMovie,
                                returnToId = viewModel.lastOpenedMovieId,
                                onReturnFocusGranted = { viewModel.lastOpenedMovieId = null },
                                onBareCard = viewModel::fillArtworkFor,
                                onArtworkFailed = { viewModel.replaceDeadMovieArtwork(it.id) },
                            )
                        }
                    }
                }
            }
            // The category side menu — the same rail the Live TV guide uses, so whole-category
            // browsing looks and behaves identically in every section.
            VodCategoryRail(
                providers = if (providers.size > 1) providers else emptyList(),
                selectedProvider = selectedSource,
                onSelectProviderAll = { browseCategory = null; viewModel.selectVodSource(null) },
                onSelectProvider = { id -> browseCategory = null; viewModel.selectVodSource(id) },
                categories = categories.map { it.id to it.name },
                categoryCounts = categoryCounts,
                selectedCategory = browseCategory,
                onSelectCategory = { id -> browseCategory = id; viewModel.selectMovieCategory(id) },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/**
 * Shows: the same row-based home as Movies (Continue Watching, Recently added, genre rows) with the
 * category chip strip for whole-category browsing. A show opens its detail page — where episodes are
 * fetched on demand, since pulling every episode of every series up front is what makes a first sync
 * take forever.
 */
@Composable
fun SeriesScreen(
    onOpenSeries: (Series) -> Unit,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    onOpenSearch: () -> Unit,
    hasSources: Boolean,
    isSyncing: Boolean,
    viewModel: VodViewModel = viewModel(),
) {
    val categories by viewModel.seriesCategories.collectAsState()
    val categoryCounts by viewModel.seriesCategoryCounts.collectAsState()
    val resume by viewModel.continueWatching.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedSeries.collectAsState()
    val genreRows by viewModel.seriesGenreRows.collectAsState()
    val categorySeries by viewModel.series.collectAsState()
    val vodLoading by viewModel.vodLoading.collectAsState()
    // Only the providers made of shows: a playlist added for live TV, or for its films alone, has
    // nothing to browse here, so it must not appear in this rail's filter.
    val providers by viewModel.seriesSources.collectAsState()
    val selectedSource by viewModel.selectedVodSource.collectAsState()
    val seriesContext = LocalContext.current
    val seriesSettings = remember { AppSettings.get(seriesContext) }
    val seriesSort by seriesSettings.seriesSort.collectAsState()
    val seriesProviderNames = remember(providers) { providers.associate { it.id to it.name } }

    LaunchedEffect(Unit) {
        if (hasSources) viewModel.ensureVodLoaded()
        viewModel.loadHomeFeeds()
    }

    // Saveable: BACK from a detail pops back to HOME (rebuilt from scratch) — see MoviesScreen.
    var browseCategory by rememberSaveable { mutableStateOf<String?>(null) }

    // BACK returns from a category to the shelves — see MoviesScreen: there is no "All" row to do it.
    BackHandler(enabled = browseCategory != null) { browseCategory = null }

    val hasContent = resume.isNotEmpty() || recentlyAdded.isNotEmpty() || genreRows.isNotEmpty()

    // Records the opened show so BACK from its detail can restore scroll + focus onto it.
    val openSeries: (Series) -> Unit = { series ->
        viewModel.lastOpenedSeriesId = series.id
        onOpenSeries(series)
    }

    Column(Modifier.fillMaxSize()) {
        VodToolbar(
            onOpenSearch = onOpenSearch,
            sort = seriesSort,
            onSelectSort = seriesSettings::setSeriesSort,
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // Content first (focus on open — the rail must never steal the d-pad on entry),
            // inset by the rail's width. See MoviesScreen for the same arrangement.
            Box(Modifier.fillMaxSize().padding(start = VOD_RAIL_WIDTH)) {
                when {
                    browseCategory != null -> SeriesCategoryGrid(
                        categorySeries, openSeries, viewModel,
                        sort = seriesSort,
                        providerNames = seriesProviderNames,
                        returnToId = viewModel.lastOpenedSeriesId,
                        onReturnFocusGranted = { viewModel.lastOpenedSeriesId = null },
                    )
                    !hasContent -> when {
                        vodLoading || isSyncing -> LoadingVod(stringResource(R.string.vod_loading_shows))
                        hasSources -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_provider))
                        else -> EmptyVod(stringResource(R.string.vod_no_shows), stringResource(R.string.vod_no_shows_add))
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (resume.isNotEmpty()) item(key = "cw") {
                            ContinueWatchingRow(
                                resume, onResume,
                                onBareCard = viewModel::fillArtworkForKey,
                                onArtworkFailed = viewModel::replaceDeadArtworkForKey,
                            )
                        }
                        if (recentlyAdded.isNotEmpty()) item(key = "recent") {
                            SeriesPosterRow(
                                stringResource(R.string.vod_recently_added), recentlyAdded, openSeries,
                                returnToId = viewModel.lastOpenedSeriesId,
                                onReturnFocusGranted = { viewModel.lastOpenedSeriesId = null },
                                onBareCard = viewModel::fillArtworkFor,
                                onArtworkFailed = { viewModel.replaceDeadSeriesArtwork(it.id) },
                            )
                        }
                        items(genreRows, key = { "g:${it.genre}" }) { group ->
                            SeriesPosterRow(
                                group.genre, group.items, openSeries,
                                returnToId = viewModel.lastOpenedSeriesId,
                                onReturnFocusGranted = { viewModel.lastOpenedSeriesId = null },
                                onBareCard = viewModel::fillArtworkFor,
                                onArtworkFailed = { viewModel.replaceDeadSeriesArtwork(it.id) },
                            )
                        }
                    }
                }
            }
            VodCategoryRail(
                providers = if (providers.size > 1) providers else emptyList(),
                selectedProvider = selectedSource,
                onSelectProviderAll = { browseCategory = null; viewModel.selectVodSource(null) },
                onSelectProvider = { id -> browseCategory = null; viewModel.selectVodSource(id) },
                categories = categories.map { it.id to it.name },
                categoryCounts = categoryCounts,
                selectedCategory = browseCategory,
                onSelectCategory = { id -> browseCategory = id; viewModel.selectSeriesCategory(id) },
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

// ---- Whole-category browse grids ---------------------------------------------------------------

/**
 * One category's films as a poster grid. Quality variants collapse to one card, badged.
 *
 * Large categories (thousands of titles) render in windows of [GRID_PAGE_SIZE]: only the first
 * window composes on open so the grid paints immediately, and scrolling near the end appends the
 * next window. Lazy grids already recycle off-screen cards, but composing thousands of items up
 * front still blocks the first frame on a Fire Stick — windowing fixes that without any DB change.
 */
private const val GRID_PAGE_SIZE = 120

/** Posters across a category grid. Fixed so every row is full, TV-first layout. */
private const val GRID_COLUMNS = 5

/**
 * Browse order for a category's collapsed movie groups. RECENT is identity — the DAO's native
 * newest-first order — so the default view is byte-for-byte what it always was. Everything else
 * sorts in memory over the already-loaded list: no query changes, and ties always break by title
 * so the order is stable between recompositions.
 */
private fun movieOrder(
    sort: AppSettings.VodSort,
    providerNames: Map<Long, String>,
): Comparator<app.opentv.data.repo.MovieVariantGroup> = when (sort) {
    AppSettings.VodSort.RECENT -> Comparator { _, _ -> 0 }
    AppSettings.VodSort.AZ -> compareBy { it.primary.displayTitle.lowercase() }
    AppSettings.VodSort.YEAR -> compareByDescending<app.opentv.data.repo.MovieVariantGroup> { it.primary.year ?: 0 }
        .thenBy { it.primary.displayTitle.lowercase() }
    AppSettings.VodSort.RATING -> compareByDescending<app.opentv.data.repo.MovieVariantGroup> { it.primary.rating ?: -1.0 }
        .thenBy { it.primary.displayTitle.lowercase() }
    AppSettings.VodSort.PROVIDER -> compareBy<app.opentv.data.repo.MovieVariantGroup> { providerNames[it.primary.sourceId].orEmpty().lowercase() }
        .thenBy { it.primary.displayTitle.lowercase() }
}

/** Show half of [movieOrder]. */
private fun seriesOrder(
    sort: AppSettings.VodSort,
    providerNames: Map<Long, String>,
): Comparator<Series> = when (sort) {
    AppSettings.VodSort.RECENT -> Comparator { _, _ -> 0 }
    AppSettings.VodSort.AZ -> compareBy { it.displayTitle.lowercase() }
    AppSettings.VodSort.YEAR -> compareByDescending<Series> { it.year ?: 0 }
        .thenBy { it.displayTitle.lowercase() }
    AppSettings.VodSort.RATING -> compareByDescending<Series> { it.rating ?: -1.0 }
        .thenBy { it.displayTitle.lowercase() }
    AppSettings.VodSort.PROVIDER -> compareBy<Series> { providerNames[it.sourceId].orEmpty().lowercase() }
        .thenBy { it.displayTitle.lowercase() }
}

@Composable
private fun MovieCategoryGrid(
    movies: List<Movie>,
    viewModel: VodViewModel,
    onOpenMovie: (Movie) -> Unit,
    sort: AppSettings.VodSort = AppSettings.VodSort.RECENT,
    providerNames: Map<Long, String> = emptyMap(),
    returnToId: Long? = null,
    onReturnFocusGranted: () -> Unit = {},
) {
    if (movies.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_movies)); return }
    // Room re-emits this list on EVERY row write — including each TMDB artwork backfill
    // landing while posters are still filling in. Keying the regroup on list *identity* re-ran
    // collapse + sort over thousands of titles per write (main thread, heavy alloc churn),
    // reset the windowing, and re-fired the scroll-restore effect — the stutter/ANR loop seen
    // while posters loaded. Title membership (ids) only changes when the category actually
    // changes, so structure memoizes on ids; fresh poster URLs ride a separate light overlay
    // map so backfilled art still paints without regrouping anything.
    val movieIds = remember(movies) { movies.map { it.id } }
    val posterById = remember(movies) { movies.associate { it.id to it.posterUrl } }
    val collapsed = remember(movieIds) { viewModel.collapseVariants(movies) }
    // Browse order is applied in memory over the collapsed groups — RECENT keeps the DAO's native
    // newest-first order, so the default view is exactly what it always was.
    val groups = remember(collapsed, sort, providerNames) { collapsed.sortedWith(movieOrder(sort, providerNames)) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val art = remember(groups, posterById) { groups.map { posterById[it.primary.id].orEmpty() } }
    PrefetchImagesAround(
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1 },
        lastVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        urls = art,
    )
    // Open with enough windows for the saved scroll anchor when returning from a detail —
    // scrolling to an item that was never composed silently does nothing. Keyed on membership,
    // NOT list identity, so backfill writes landing mid-browse neither shrink the window
    // (which dropped focus and yanked scroll) nor re-fire the restore effect below.
    var visibleCount by remember(movieIds) {
        mutableIntStateOf(
            maxOf(GRID_PAGE_SIZE, viewModel.movieGridIndex + GRID_PAGE_SIZE)
                .coerceAtMost(groups.size),
        )
    }
    // Persist the scroll anchor continuously; restore it once the grid has its rows back.
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.movieGridIndex = index
                viewModel.movieGridOffset = offset
            }
    }
    LaunchedEffect(gridState, groups) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= 0 && last >= visibleCount - 20 && visibleCount < groups.size) {
                    visibleCount = (visibleCount + GRID_PAGE_SIZE).coerceAtMost(groups.size)
                }
            }
    }
    LaunchedEffect(groups) {
        val index = viewModel.movieGridIndex
        if (index > 0 && groups.isNotEmpty()) {
            runCatching { gridState.scrollToItem(index, viewModel.movieGridOffset) }
        }
    }
    val visible = remember(groups, visibleCount) { groups.take(visibleCount) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(visible, key = { it.primary.id }) { group ->
            val quality = group.variants.firstOrNull()?.qualityLabel?.takeIf { it.isNotBlank() }
            val badge = quality
                ?: if (group.hasMultipleQualities) {
                    stringResource(R.string.guide_qualities_count, group.variants.size)
                } else {
                    null
                }
            // Bare card on screen: one guarded TMDB fill (once per session per title, max 3
            // concurrent). The persisted hit re-emits through `movies` and repaints this card.
            // Poster comes from the fresh overlay map, not the memoized group, so backfilled art
            // paints without regrouping (see above).
            LaunchedEffect(group.primary.id) { viewModel.fillArtworkFor(group.primary) }
            PosterCard(
                title = group.primary.displayTitle,
                posterUrl = posterById[group.primary.id] ?: group.primary.posterUrl,
                subtitle = group.primary.year?.toString(),
                rating = group.primary.rating,
                qualityBadge = badge,
                onClick = { onOpenMovie(group.primary) },
                modifier = Modifier.fillMaxWidth(),
                fixedWidth = false,
                requestFocus = returnToId == group.primary.id,
                onFocusGranted = onReturnFocusGranted,
                onArtworkFailed = { viewModel.replaceDeadMovieArtwork(group.primary.id) },
            )
        }
        if (visibleCount < groups.size) {
            item(key = "more", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                GridLoadingMore()
            }
        }
    }
}

/** One category's shows as a poster grid — windowed the same way as [MovieCategoryGrid]. */
@Composable
private fun SeriesCategoryGrid(
    series: List<Series>,
    onOpenSeries: (Series) -> Unit,
    viewModel: VodViewModel,
    sort: AppSettings.VodSort = AppSettings.VodSort.RECENT,
    providerNames: Map<Long, String> = emptyMap(),
    returnToId: Long? = null,
    onReturnFocusGranted: () -> Unit = {},
) {
    if (series.isEmpty()) { LoadingVod(stringResource(R.string.vod_loading_shows)); return }
    // Same backfill re-emission guard as MovieCategoryGrid: membership (ids) only changes when
    // the category actually changes; fresh poster URLs ride a light overlay map.
    val seriesIds = remember(series) { series.map { it.id } }
    val seriesPosterById = remember(series) { series.associate { it.id to it.posterUrl } }
    val ordered = remember(seriesIds, sort, providerNames) { series.sortedWith(seriesOrder(sort, providerNames)) }
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    val art = remember(ordered, seriesPosterById) { ordered.map { seriesPosterById[it.id].orEmpty() } }
    PrefetchImagesAround(
        firstVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1 },
        lastVisibleIndex = { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        urls = art,
    )
    var visibleCount by remember(seriesIds) {
        mutableIntStateOf(
            maxOf(GRID_PAGE_SIZE, viewModel.seriesGridIndex + GRID_PAGE_SIZE)
                .coerceAtMost(ordered.size),
        )
    }
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                viewModel.seriesGridIndex = index
                viewModel.seriesGridOffset = offset
            }
    }
    LaunchedEffect(gridState, ordered) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { last ->
                if (last >= 0 && last >= visibleCount - 20 && visibleCount < ordered.size) {
                    visibleCount = (visibleCount + GRID_PAGE_SIZE).coerceAtMost(ordered.size)
                }
            }
    }
    LaunchedEffect(ordered) {
        val index = viewModel.seriesGridIndex
        if (index > 0 && ordered.isNotEmpty()) {
            runCatching { gridState.scrollToItem(index, viewModel.seriesGridOffset) }
        }
    }
    val visible = remember(ordered, visibleCount) { ordered.take(visibleCount) }
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(GRID_COLUMNS),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        gridItems(visible, key = { it.id }) { item ->
            LaunchedEffect(item.id) { viewModel.fillArtworkFor(item) }
            PosterCard(
                title = item.displayTitle,
                posterUrl = seriesPosterById[item.id] ?: item.posterUrl,
                subtitle = item.year?.toString(),
                rating = item.rating,
                onClick = { onOpenSeries(item) },
                modifier = Modifier.fillMaxWidth(),
                fixedWidth = false,
                requestFocus = returnToId == item.id,
                onFocusGranted = onReturnFocusGranted,
                onArtworkFailed = { viewModel.replaceDeadSeriesArtwork(item.id) },
            )
        }
        if (visibleCount < ordered.size) {
            item(key = "more", span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                GridLoadingMore()
            }
        }
    }
}

/** Full-width "loading more titles" footer while a windowed grid still has rows to append. */
@Composable
private fun GridLoadingMore() {
    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.width(22.dp).height(22.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(R.string.vod_loading_more),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---- Shared shelves ----------------------------------------------------------------------------

/** A titled horizontal shelf of movie poster cards. Shared by the home and the detail's "more like this". */
@Composable
internal fun MoviePosterRow(
    title: String,
    movies: List<Movie>,
    onOpenMovie: (Movie) -> Unit,
    returnToId: Long? = null,
    onReturnFocusGranted: () -> Unit = {},
    /** Fired once per composed card so owners can lazily fill missing artwork. Defaults to none. */
    onBareCard: (Movie) -> Unit = {},
    /** Fired when a composed card's URL fails to render, so owners can replace dead art. */
    onArtworkFailed: (Movie) -> Unit = {},
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val art = remember(movies) { movies.map { it.posterUrl.orEmpty() } }
    PrefetchImagesAround(
        firstVisibleIndex = { state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1 },
        lastVisibleIndex = { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        urls = art,
    )
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(movies, key = { it.id }) { movie ->
                LaunchedEffect(movie.id) { onBareCard(movie) }
                PosterCard(
                    title = movie.displayTitle,
                    posterUrl = movie.posterUrl,
                    subtitle = movie.year?.toString(),
                    rating = movie.rating,
                    onClick = { onOpenMovie(movie) },
                    requestFocus = returnToId == movie.id,
                    onFocusGranted = onReturnFocusGranted,
                    onArtworkFailed = { onArtworkFailed(movie) },
                )
            }
        }
    }
}

/** A titled horizontal shelf of series poster cards. */
@Composable
internal fun SeriesPosterRow(
    title: String,
    series: List<Series>,
    onOpenSeries: (Series) -> Unit,
    returnToId: Long? = null,
    onReturnFocusGranted: () -> Unit = {},
    /** Fired once per composed card so owners can lazily fill missing artwork. Defaults to none. */
    onBareCard: (Series) -> Unit = {},
    /** Fired when a composed card's URL fails to render, so owners can replace dead art. */
    onArtworkFailed: (Series) -> Unit = {},
) {
    val state = androidx.compose.foundation.lazy.rememberLazyListState()
    val art = remember(series) { series.map { it.posterUrl.orEmpty() } }
    PrefetchImagesAround(
        firstVisibleIndex = { state.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: -1 },
        lastVisibleIndex = { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 },
        urls = art,
    )
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        LazyRow(
            state = state,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(series, key = { it.id }) { item ->
                LaunchedEffect(item.id) { onBareCard(item) }
                PosterCard(
                    title = item.displayTitle,
                    posterUrl = item.posterUrl,
                    subtitle = item.year?.toString(),
                    rating = item.rating,
                    onClick = { onOpenSeries(item) },
                    requestFocus = returnToId == item.id,
                    onFocusGranted = onReturnFocusGranted,
                    onArtworkFailed = { onArtworkFailed(item) },
                )
            }
        }
    }
}

@Composable
internal fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp),
    )
}

/**
 * The reusable poster card: art, title and an optional year, with a rating chip, a quality badge and
 * a resume progress bar drawn over the art where the data is there. The focused card scales up and
 * gains a primary border — the app's established focus cue — and, being focusable, the lazy row
 * brings it into view on its own.
 */
@Composable
internal fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    rating: Double? = null,
    qualityBadge: String? = null,
    progress: Float? = null,
    /**
     * True for the card BACK is returning to. The card pulls d-pad focus onto itself once, then
     * reports [onFocusGranted] so the caller clears the target — without the clear, a later
     * recomposition would yank focus back here while the viewer is browsing elsewhere.
     */
    requestFocus: Boolean = false,
    onFocusGranted: () -> Unit = {},
    /**
     * Fired when the card's URL was present but failed to render. Owners map it to a verified
     * TMDB replacement; blank URLs never fire (see [PosterImage]).
     */
    onArtworkFailed: () -> Unit = {},
    /**
     * False in fixed-column grids: the card stretches to its column (via the incoming [modifier])
     * instead of holding the 140dp shelf width.
     */
    fixedWidth: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "posterScale")
    val returnFocus = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) runCatching { returnFocus.requestFocus() }
    }
    Column(
        modifier
            .then(if (fixedWidth) Modifier.width(POSTER_WIDTH) else Modifier)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .focusRequester(returnFocus)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused && requestFocus) onFocusGranted()
            }
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, AppTheme.palette.cursorBorder, RoundedCornerShape(8.dp))
                    else Modifier,
                ),
        ) {
            PosterImage(
                posterUrl = posterUrl,
                title = title,
                modifier = Modifier.fillMaxSize(),
                onLoadError = onArtworkFailed,
            )
            rating?.takeIf { it > 0.0 }?.let {
                Badge(
                    text = "★ ${formatRating(it)}",
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                )
            }
            qualityBadge?.let {
                Badge(
                    text = it,
                    highlight = true,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }
            progress?.let {
                LinearProgressIndicator(
                    progress = { it },
                    modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        subtitle?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * Poster art with an instant-feeling loading state.
 *
 * While Coil decodes (or hits the network on first sight) a shimmer fills the frame instead of a
 * flat grey box, so shelves read as loading rather than broken. On a missing or dead URL a titled
 * gradient tile with the initial shows — the same fallback the detail hero uses — so a grid never
 * shows an empty rectangle. Uses [posterRequest] so the UI shares one cache key and decode size
 * with the prefetcher; any other request shape would decode the same art twice.
 */
@Composable
internal fun PosterImage(
    posterUrl: String?,
    title: String,
    modifier: Modifier = Modifier,
    /**
     * Fired when the URL was present but Coil could not render it (404, decode failure…).
     * Owners use it to kick a verified TMDB replacement; blank URLs never fire it (there is
     * nothing to verify dead), and each URL fires at most once per composition instance.
     */
    onLoadError: () -> Unit = {},
) {
    var loaded by remember(posterUrl) { mutableStateOf(false) }
    var failed by remember(posterUrl) { mutableStateOf(false) }
    Box(modifier) {
        if (failed || posterUrl.isNullOrBlank()) {
            PosterFallbackTile(title = title, modifier = Modifier.fillMaxSize())
        } else {
            if (!loaded) {
                Box(Modifier.fillMaxSize().shimmerPlaceholder())
            }
            AsyncImage(
                model = posterRequest(LocalContext.current, posterUrl),
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onLoading = { loaded = false; failed = false },
                onSuccess = { loaded = true },
                onError = { state ->
                    failed = true
                    // Coil failures are otherwise invisible; the URL + state is what
                    // distinguishes a dead host from a client-side decode/timeout problem.
                    android.util.Log.w("OpenTV", "Poster failed for '$title' <$posterUrl>: $state")
                    onLoadError()
                },
            )
        }
    }
}

/** Gradient tile with the title initial — shown when poster art is missing or failed to load. */
@Composable
private fun PosterFallbackTile(title: String, modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0f to MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                1f to MaterialTheme.colorScheme.surfaceVariant,
            ),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.trim().take(1).uppercase().ifBlank { "?" },
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
        )
    }
}

/**
 * Shimmer sweep for image frames. Cheap on a stick: one infinite transition per visible card,
 * no blur, no per-frame allocation outside the brush.
 */
@Composable
private fun Modifier.shimmerPlaceholder(): Modifier {
    val transition = rememberInfiniteTransition(label = "posterShimmer")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerSweep",
    )
    val base = MaterialTheme.colorScheme.surfaceVariant
    return background(
        Brush.linearGradient(
            colors = listOf(base, base.copy(alpha = 0.55f), base),
            start = androidx.compose.ui.geometry.Offset(-200f + 500f * sweep, 0f),
            end = androidx.compose.ui.geometry.Offset(100f + 500f * sweep, 300f),
        ),
    )
}

/** A small rounded chip drawn over poster art — a rating or a quality label. */
@Composable
private fun Badge(text: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary
    else androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.66f)
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary else androidx.compose.ui.graphics.Color.White
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = fg,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

// ---- Continue watching -------------------------------------------------------------------------

@Composable
internal fun ContinueWatchingRow(
    items: List<VodViewModel.ResumeItem>,
    onResume: (mediaKey: String, url: String, title: String) -> Unit,
    /** See [MoviePosterRow.onBareCard]: lazily fills missing movie art. Defaults to none. */
    onBareCard: (mediaKey: String, hasPoster: Boolean) -> Unit = { _, _ -> },
    /** See [PosterCard.onArtworkFailed]: replaces provably dead movie art. Defaults to none. */
    onArtworkFailed: (mediaKey: String) -> Unit = {},
) {
    Column(Modifier.fillMaxWidth()) {
        SectionHeader(stringResource(R.string.vod_continue_watching))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.mediaKey }) { item ->
                ResumeCard(
                    item,
                    onBareCard = { onBareCard(item.mediaKey, !item.posterUrl.isNullOrBlank()) },
                    onArtworkFailed = { onArtworkFailed(item.mediaKey) },
                    onClick = { onResume(item.mediaKey, item.streamUrl, item.title) },
                )
            }
        }
    }
}

/** A landscape resume thumbnail with a progress fill — a movie or an episode part-way through. */
@Composable
private fun ResumeCard(
    item: VodViewModel.ResumeItem,
    onBareCard: () -> Unit = {},
    onArtworkFailed: () -> Unit = {},
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "resumeScale")
    Column(
        Modifier
            .width(190.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(107.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (focused) Modifier.border(3.dp, AppTheme.palette.cursorBorder, RoundedCornerShape(6.dp))
                    else Modifier,
                ),
        ) {
            LaunchedEffect(item.mediaKey) { onBareCard() }
            PosterImage(
                posterUrl = item.posterUrl,
                title = item.title,
                modifier = Modifier.fillMaxSize(),
                onLoadError = onArtworkFailed,
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
            color = if (focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---- Category side rail ------------------------------------------------------------------------

/** Width of the category rail — the same 238dp column the Live TV guide's rail uses. */
private val VOD_RAIL_WIDTH = 238.dp

/**
 * The Movies/Shows category list as a LEFT SIDE MENU, mirroring the Live TV guide's rail:
 * the same 240dp column, the same rounded row styling and focus treatment, and the same order —
 * providers first (only when more than one is configured), then one row per category.
 *
 * There is deliberately no "All" row. It existed, and selecting it ran an unfiltered query that
 * returned every title of every provider as a single list — tens of thousands of rows — which is
 * what made the box crawl. With it gone a grid is always exactly one category's worth of titles.
 *
 * The guide's rail is hidden until LEFT is pressed; here it stays visible so whole-category
 * browsing is one press away from the shelves — and BACK from an open category returns there. The
 * content beside the rail is declared before this in the composition, so it keeps the d-pad on
 * entry — the rail never steals focus.
 *
 * Each category carries its title count ("Action · 1,234") so the size of a category is visible
 * before opening it — the number a viewer actually picks a category by. [categoryCounts] is keyed
 * by category id; a category the map does not know yet (still importing) simply shows no count.
 */
@Composable
private fun VodCategoryRail(
    providers: List<Source>,
    selectedProvider: Long?,
    onSelectProviderAll: () -> Unit,
    onSelectProvider: (Long) -> Unit,
    categories: List<Pair<String, String>>,
    categoryCounts: Map<String, Int>,
    selectedCategory: String?,
    onSelectCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier
            .width(VOD_RAIL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (providers.isNotEmpty()) {
            item(key = "rail-providers") {
                RailSectionLabel(stringResource(R.string.channels_manager_source_header))
            }
            item(key = "rail-provider-all") {
                VodRailEntry(
                    label = stringResource(R.string.channels_manager_all_sources),
                    selected = selectedProvider == null,
                    onClick = onSelectProviderAll,
                )
            }
            items(providers, key = { "rail-provider:${it.id}" }) { source ->
                VodRailEntry(
                    label = source.name,
                    selected = selectedProvider == source.id,
                    onClick = { onSelectProvider(source.id) },
                )
            }
            item(key = "rail-provider-divider") { Spacer(Modifier.height(10.dp)) }
        }
        item(key = "rail-categories") { RailSectionLabel(stringResource(R.string.vod_categories)) }
        items(categories, key = { "rail-category:${it.first}" }) { (id, name) ->
            VodRailEntry(
                label = name,
                count = categoryCounts[id],
                selected = selectedCategory == id,
                onClick = { onSelectCategory(id) },
            )
        }
    }
}

/** A section heading inside the rail ("Providers", "Categories"). */
@Composable
private fun RailSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 2.dp),
    )
}

/**
 * One rail row — the guide's RailEntry styling verbatim: bold when focused or selected, a light
 * fill plus a white ring on focus, and the container tint when merely selected.
 *
 * [count] is the row's title count, right-aligned and muted so it reads as a fact about the
 * category rather than a second label. Omitted (null) when there is no number worth showing.
 */
@Composable
private fun VodRailEntry(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor = when {
        selected -> AppTheme.primary
        focused -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .tvFocus(
                shape = RoundedCornerShape(8.dp),
                selected = selected,
                onFocusChange = { focused = it },
            )
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = "%,d".format(count),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

// ---- Search / loading / empty ------------------------------------------------------------------

/**
 * The top bar of the Movies and Shows home: search on the left, the TiviMate-style sort menu
 * pinned top-right. The rail's global search already covers movies and series; this makes it
 * reachable without leaving the tab.
 */
@Composable
private fun VodToolbar(
    onOpenSearch: () -> Unit,
    sort: AppSettings.VodSort,
    onSelectSort: (AppSettings.VodSort) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchAffordance(onOpenSearch, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        SortMenuButton(sort = sort, onSelectSort = onSelectSort)
    }
}

/**
 * The sort picker: a pill showing the active order, opening a dropdown with the five browse
 * orders. The choice persists per section in settings, so a restart keeps it.
 */
@Composable
private fun SortMenuButton(
    sort: AppSettings.VodSort,
    onSelectSort: (AppSettings.VodSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .tvFocus(shape = RoundedCornerShape(10.dp), onFocusChange = { focused = it })
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Sort,
                contentDescription = stringResource(R.string.vod_sort_title),
                tint = if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                sortLabel(sort),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (focused) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            Text(
                stringResource(R.string.vod_sort_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            AppSettings.VodSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(sortLabel(option)) },
                    onClick = {
                        expanded = false
                        onSelectSort(option)
                    },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )
            }
        }
    }
}

/** The short label the sort pill shows for the active order. */
@Composable
private fun sortLabel(sort: AppSettings.VodSort): String = stringResource(
    when (sort) {
        AppSettings.VodSort.RECENT -> R.string.vod_sort_recent
        AppSettings.VodSort.PROVIDER -> R.string.vod_sort_provider
        AppSettings.VodSort.AZ -> R.string.vod_sort_az
        AppSettings.VodSort.YEAR -> R.string.vod_sort_year
        AppSettings.VodSort.RATING -> R.string.vod_sort_rating
    },
)

/**
 * The search entry — the toolbar's left side. Focusable for d-pad on TV and tappable on touch,
 * it just opens the existing search screen.
 */
@Composable
private fun SearchAffordance(onOpenSearch: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .tvFocus(shape = RoundedCornerShape(10.dp), onFocusChange = { focused = it })
            .clickable(onClick = onOpenSearch)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        Icon(Icons.Filled.Search, contentDescription = null, tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.vod_search_hint),
            style = MaterialTheme.typography.titleMedium,
            color = tint,
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
                stringResource(R.string.vod_large_provider_note),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Poster shelf card width. Grid cards stretch to fill their fixed column. */
private val POSTER_WIDTH = 140.dp

/** Rating to one decimal place, locale-independent (the "★" is drawn beside it). */
internal fun formatRating(rating: Double): String = String.format(java.util.Locale.US, "%.1f", rating)
