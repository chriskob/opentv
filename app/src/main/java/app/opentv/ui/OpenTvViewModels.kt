/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Movie
import app.opentv.data.model.Programme
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.repo.EpgRepository
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

/**
 * View models for the whole app.
 *
 * Grouped in one file on purpose: they are small, they share the same dependency graph, and
 * a contributor tracking down "where does the channel list come from" finds it in one place.
 */

class SourcesViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    data class UiState(
        val sources: List<Source> = emptyList(),
        val testing: Boolean = false,
        val testResult: String? = null,
        val testError: String? = null,
        val syncing: Boolean = false,
        val syncMessage: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            graph.sourceRepository.observeAll().collect { sources ->
                _ui.value = _ui.value.copy(sources = sources)
            }
        }
    }

    fun test(draft: Source) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(testing = true, testResult = null, testError = null)
            val result = graph.sourceRepository.test(draft)
            _ui.value = _ui.value.copy(
                testing = false,
                testResult = result.getOrNull(),
                testError = result.exceptionOrNull()?.message,
            )
        }
    }

    /** Saves, then immediately pulls the catalogue so the user sees channels, not a spinner. */
    fun saveAndSync(draft: Source, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(syncing = true, syncMessage = "Saving source…")
            val id = graph.sourceRepository.save(draft)
            val saved = graph.sourceRepository.byId(id)
            if (saved == null) {
                _ui.value = _ui.value.copy(syncing = false, syncMessage = "Could not save source.")
                onDone(false)
                return@launch
            }

            _ui.value = _ui.value.copy(syncMessage = "Loading channels…")
            val now = System.currentTimeMillis()
            when (val result = graph.catalogRepository.sync(saved, now)) {
                is CatalogRepository.SyncResult.Failed -> {
                    _ui.value = _ui.value.copy(syncing = false, syncMessage = result.reason)
                    onDone(false)
                    return@launch
                }
                is CatalogRepository.SyncResult.Success -> {
                    _ui.value = _ui.value.copy(
                        syncMessage = "Loaded ${result.channelCount} channels. Downloading guide…",
                    )
                }
            }

            val refreshed = graph.sourceRepository.byId(id) ?: saved
            val epg = graph.epgRepository.sync(refreshed, now)
            _ui.value = _ui.value.copy(
                syncing = false,
                syncMessage = when (epg) {
                    is EpgRepository.SyncResult.Success ->
                        "Ready. ${epg.programmeCount} guide entries."
                    // Not a failure the user needs to act on: channels work without a guide.
                    is EpgRepository.SyncResult.Failed ->
                        "Channels are ready. Guide unavailable: ${epg.reason}"
                },
            )
            onDone(true)
        }
    }

    fun delete(source: Source) {
        viewModelScope.launch { graph.catalogRepository.deleteSource(source.id) }
    }

    fun refreshAll() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(syncing = true, syncMessage = "Refreshing…")
            val now = System.currentTimeMillis()
            var channels = 0
            var problems = 0
            for (source in graph.sourceRepository.enabled()) {
                when (val result = graph.catalogRepository.sync(source, now)) {
                    is CatalogRepository.SyncResult.Success -> channels += result.channelCount
                    is CatalogRepository.SyncResult.Failed -> problems++
                }
                val refreshed = graph.sourceRepository.byId(source.id) ?: source
                if (graph.epgRepository.sync(refreshed, now) is EpgRepository.SyncResult.Failed) {
                    problems++
                }
            }
            _ui.value = _ui.value.copy(
                syncing = false,
                syncMessage = if (problems == 0) "Refreshed $channels channels."
                else "Refreshed $channels channels, $problems source(s) had problems.",
            )
        }
    }

    fun blankDraft() = Source(name = "", kind = SourceKind.XTREAM, url = "")
}

class ChannelsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    private val selectedCategory = MutableStateFlow<String?>(null)
    private val query = MutableStateFlow("")
    private val nowTick = MutableStateFlow(System.currentTimeMillis())

    val categories: StateFlow<List<Category>> =
        graph.catalogRepository.observeCategories(StreamKind.LIVE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    data class Row(val channel: Channel, val now: Programme?, val next: Programme?)

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> =
        combine(selectedCategory, query) { category, q -> category to q }
            .flatMapLatest { (category, q) ->
                val channelFlow = if (q.isBlank()) {
                    graph.catalogRepository.observeChannels(categoryId = category)
                } else {
                    graph.catalogRepository.searchChannels(q)
                }
                combine(channelFlow, nowTick) { channels, now -> channels to now }
                    .flatMapLatest { (channels, now) ->
                        graph.epgRepository
                            .observeWindow(now, now + GUIDE_LOOKAHEAD_MILLIS)
                            .map { programmes ->
                                // Grouped here rather than in SQL: the query cannot take a
                                // channel-id list without hitting SQLite's bound-variable cap.
                                val byChannel = programmes.groupBy { it.epgChannelId }
                                channels.map { channel ->
                                    val list = channel.epgChannelId
                                        ?.let { byChannel[it] }
                                        ?.sortedBy { it.startUtcMillis }
                                        .orEmpty()
                                    Row(
                                        channel = channel,
                                        now = list.firstOrNull { it.isLiveAt(now) },
                                        next = list.firstOrNull { it.startUtcMillis > now },
                                    )
                                }
                            }
                    }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favourites: StateFlow<List<Channel>> =
        graph.catalogRepository.observeFavouriteChannels()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(id: String?) { selectedCategory.value = id }

    fun search(text: String) { query.value = text }

    fun tick() { nowTick.value = System.currentTimeMillis() }

    fun toggleFavourite(channel: Channel) {
        viewModelScope.launch {
            graph.catalogRepository.setChannelFavourite(channel.id, !channel.favourite)
        }
    }

    fun hide(channel: Channel) {
        viewModelScope.launch { graph.catalogRepository.setChannelHidden(channel.id, true) }
    }

    private companion object {
        const val GUIDE_LOOKAHEAD_MILLIS = 6 * 60 * 60 * 1000L
    }
}

class VodViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    private val movieCategory = MutableStateFlow<String?>(null)
    private val seriesCategory = MutableStateFlow<String?>(null)

    val movieCategories: StateFlow<List<Category>> =
        graph.catalogRepository.observeCategories(StreamKind.MOVIE)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val seriesCategories: StateFlow<List<Category>> =
        graph.catalogRepository.observeCategories(StreamKind.SERIES)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val movies: StateFlow<List<Movie>> = movieCategory
        .flatMapLatest { graph.catalogRepository.observeMovies(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val series: StateFlow<List<Series>> = seriesCategory
        .flatMapLatest { graph.catalogRepository.observeSeries(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectMovieCategory(id: String?) { movieCategory.value = id }

    fun selectSeriesCategory(id: String?) { seriesCategory.value = id }

    fun toggleMovieFavourite(movie: Movie) {
        viewModelScope.launch {
            graph.catalogRepository.setMovieFavourite(movie.id, !movie.favourite)
        }
    }

    /**
     * Episodes are fetched on demand rather than during catalogue sync.
     *
     * Pulling every episode of every series up front is what makes a first sync take twenty
     * minutes on a large provider, and most of it is never looked at.
     */
    fun loadEpisodes(series: Series) {
        viewModelScope.launch {
            val source = graph.sourceRepository.byId(series.sourceId) ?: return@launch
            graph.catalogRepository.ensureEpisodes(source, series.seriesId)
        }
    }

    fun episodes(series: Series) =
        graph.catalogRepository.observeEpisodes(series.sourceId, series.seriesId)
}
