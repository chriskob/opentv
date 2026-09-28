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
import kotlinx.coroutines.launch
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

    /** Fetches each configured Plex server. Safe to call again; a second run just refreshes. */
    fun sync() {
        viewModelScope.launch {
            val plex = sources.value
            if (plex.isEmpty()) return@launch
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
     * Plays a show by opening its most recent episode.
     *
     * A series has no playable part of its own - `/library/metadata/{seriesKey}` describes a show,
     * not a file, and asking it for parts returned nothing, so pressing a show said "Plex would not
     * give a playable file" for every show. The episodes are a level down: `/children` on the
     * series. The newest is played, which is what a card on a "recently added" shelf should open -
     * someone seeing a show appear there means the latest of it.
     */
    fun playSeries(series: Series, onReady: (mediaKey: String, url: String, title: String) -> Unit) {
        viewModelScope.launch {
            val source = withContext(Dispatchers.IO) { graph.database.sources().byId(series.sourceId) }
            if (source == null) {
                _lastError.value = "That Plex server is no longer set up."
                return@launch
            }
            val episode = withContext(Dispatchers.IO) {
                runCatching { repo.plexLatestEpisodeKey(source, series.seriesId) }.getOrNull()
            }
            if (episode == null) {
                _lastError.value = "Plex has no episodes for \"${series.name}\"."
                return@launch
            }
            val url = withContext(Dispatchers.IO) { repo.plexPlayUrl(source, episode.ratingKey) }
            if (url == null) {
                _lastError.value = "Plex would not give a playable file for \"${series.name}\"."
                return@launch
            }
            onReady(episode.ratingKey, url, "${series.name} — ${episode.title}")
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
            onReady(ratingKey, url, title)
        }
    }
}
