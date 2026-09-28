/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License, either version 3 of the License, or
 * (at your option) any later version.
 */
package app.opentv.ui.plex

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.repo.CatalogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

/** What the Plex shelf is doing right now, so the screen can be honest about it. */
sealed interface PlexShelfState {
    /** No Plex server has been connected yet. Offers the sign-in, not a blank grid. */
    data object NotConnected : PlexShelfState

    /** A refresh is running. The counts are what it has written so far. */
    data class Syncing(val movies: Int, val shows: Int) : PlexShelfState

    /** Connected and populated. [movies]/[shows] are what is on screen. */
    data class Ready(val movies: List<Movie>, val shows: List<Series>) : PlexShelfState

    /**
     * Connected, but the last fetch failed. The shelf keeps whatever it already had rather than
     * blanking, because yesterday's titles are better than an error page - the message says what
     * went wrong and offers a retry.
     */
    data class Failed(val reason: String, val movies: List<Movie>, val shows: List<Series>) :
        PlexShelfState
}

/**
 * Backs the Plex shelf.
 *
 * Reads from the same tables the Movies and Shows screens read, scoped to Plex sources. The rows are
 * ordinary movie/series rows; what makes them Plex is their [Source.kind], so nothing in the
 * catalogue layer has to know a second thing about them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlexViewModel(app: Application) : AndroidViewModel(app) {

    private val graph = ServiceLocator.get(app)
    private val repo: CatalogRepository = graph.catalogRepository
    private val settings = graph.settings

    private val _progress = MutableStateFlow<Pair<Int, Int>?>(null)
    val progress: StateFlow<Pair<Int, Int>?> = _progress.asStateFlow()

    /** Runs a one-off sign-in, used by the connect screen. */
    fun addPlexSource(name: String, serverUrl: String, token: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching {
                withContext(Dispatchers.IO) {
                    graph.database.sources().insert(
                        Source(
                            name = name.ifBlank { "Plex" },
                            kind = SourceKind.PLEX,
                            url = serverUrl.trim(),
                            // The token lives in the password column rather than a new one: it is
                            // this source's credential, and adding a column means a migration.
                            password = token.trim(),
                            // No live half, ever. Stated here rather than left to a UI default so
                            // nothing downstream can accidentally treat Plex as a channel provider.
                            includeLive = false,
                            includeVod = true,
                            includeSeries = true,
                        ),
                    )
                }
            }.isSuccess
            onDone(ok)
        }
    }

    private val sources: StateFlow<List<Source>> = repo.observePlexSources()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val stored: StateFlow<Pair<List<Movie>, List<Series>>> =
        sources.flatMapLatest { plexSources ->
            val ids = plexSources.map { it.id }
            if (ids.isEmpty()) {
                kotlinx.coroutines.flow.flowOf(emptyList<Movie>() to emptyList<Series>())
            } else {
                combine(
                    repo.observePlexMovies(ids, CatalogRepository.PLEX_RECENT_LIMIT),
                    repo.observePlexSeries(ids, CatalogRepository.PLEX_RECENT_LIMIT),
                ) { movies, shows -> movies to shows }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<Movie>() to emptyList())

    val state: StateFlow<PlexShelfState> = combine(sources, stored, _progress) { plex, (movies, shows), syncing ->
        when {
            plex.isEmpty() -> PlexShelfState.NotConnected
            syncing != null -> PlexShelfState.Syncing(syncing.first, syncing.second)
            else -> PlexShelfState.Ready(movies, shows)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlexShelfState.NotConnected)

    /**
     * Fetches each configured Plex server.
     *
     * Awaits the source list rather than reading it. It used to take `sources.value` and return
     * early when that was empty - which, on a cold start, it always was: the StateFlow carries
     * `emptyList()` until the database has actually answered, and the screen asks for a refresh the
     * moment it appears. So opening Plex on a freshly launched app synced nothing at all, and the
     * shelf silently showed whatever the last successful sync had left. That is the whole of "the
     * same shows as before": the sync never ran.
     */
    fun sync() {
        viewModelScope.launch {
            // Bounded, so a Plex source that cannot be read does not spin here forever; the
            // timeout is reported on screen rather than swallowed.
            val plex = withTimeoutOrNull(10_000L) {
                sources.first { it.isNotEmpty() }
            }
            if (plex == null) {
                _lastError.value = "No Plex server is connected yet. Add one from the Plex screen."
                return@launch
            }
            _progress.value = 0 to 0
            var movies = 0
            var shows = 0
            var failure: String? = null
            for (source in plex) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { repo.syncVod(source, System.currentTimeMillis()) }
                }
                val vod = result.getOrNull()
                if (vod == null) {
                    failure = result.exceptionOrNull()?.message ?: "Plex could not be reached."
                } else {
                    movies += vod.movies
                    shows += vod.series
                }
            }
            _progress.value = null
            // The stored rows drive the shelf, so re-read them rather than handing the sync's own
            // counts to the screen - those are what was *written*, not what is now *on* the shelf.
            if (failure != null) {
                _lastError.value = failure
            }
        }
    }

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    fun clearError() {
        _lastError.value = null
    }

    /**
     * Resolves [movie]'s real play URL and hands it on.
     *
     * The stored row holds a `plex://<ratingKey>` placeholder rather than a URL, because Plex can
     * change a part's path when it re-scans a library - see [CatalogRepository.PLEX_SCHEME]. So the
     * lookup happens here, once, at the moment the viewer asks to play.
     */
    fun playMovie(movie: Movie, onReady: (mediaKey: String, url: String, title: String) -> Unit) {
        play(movie.sourceId, movie.streamId, movie.name, onReady)
    }

    /**
     * Plays a show row.
     *
     * Two shapes arrive here, and the shelf cannot tell them apart by looking - it has to try.
     * Most rows ARE episodes already: a show section's recentlyAdded feed returns episodes, so the
     * row's own key plays directly. A true series key (from a library that lists shows) has no
     * playable part, and its episodes are one level down on `/children` - asking that of an
     * episode answers 400, which is how the two are told apart. Trying direct play first is not
     * just an optimisation: it is the only order that works for both shapes without knowing which
     * one a row is.
     */
    fun playSeries(series: Series, onReady: (mediaKey: String, url: String, title: String) -> Unit) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { graph.database.sources().byId(series.sourceId) }
            if (source == null) {
                _lastError.value = "That Plex server is no longer set up."
                return@launch
            }
            // An episode plays by its own key. This is the common case and costs one request.
            val direct = withContext(Dispatchers.IO) { repo.plexPlayUrl(source, series.seriesId) }
            if (direct != null) {
                onReady(CatalogRepository.plexMediaKey(series.sourceId, series.seriesId), direct, series.name)
                return@launch
            }
            // A true series key: list its episodes and open the newest.
            val episode = withContext(Dispatchers.IO) {
                runCatching { repo.plexLatestEpisodeKey(source, series.seriesId) }.getOrNull()
            }
            if (episode == null) {
                _lastError.value = "Plex has no playable episode for \"${series.name}\"."
                return@launch
            }
            val url = withContext(Dispatchers.IO) { repo.plexPlayUrl(source, episode.ratingKey) }
            if (url == null) {
                _lastError.value = "Plex would not give a playable file for \"${series.name}\"."
                return@launch
            }
            onReady(CatalogRepository.plexMediaKey(series.sourceId, episode.ratingKey), url, "${series.name} — ${episode.title}")
        }
    }

    /**
     * A Plex episode, enough to look one up and play it.
     *
     * Carries its own title and index so the OSD names what is actually playing rather than the
     * series it came from - "Series — Pilot" and "Series — The Return" are different things.
     */
    data class PlexEpisodeRef(val ratingKey: String, val title: String, val index: Long)

    private fun play(sourceId: Long, ratingKey: String, title: String, onReady: (String, String, String) -> Unit) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { graph.database.sources().byId(sourceId) }
            if (source == null) {
                _lastError.value = "That Plex server is no longer set up."
                return@launch
            }
            val url = withContext(Dispatchers.IO) { repo.plexPlayUrl(source, ratingKey) }
            if (url == null) {
                _lastError.value = "Plex would not give a playable file for \"$title\"."
                return@launch
            }
            onReady(CatalogRepository.plexMediaKey(sourceId, ratingKey), url, title)
        }
    }
}
