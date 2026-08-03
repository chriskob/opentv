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
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Movie
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Profile
import app.opentv.data.model.Programme
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.repo.distinctByQuality
import app.opentv.R
import app.opentv.sync.NasSync
import app.opentv.sync.SyncBundle
import app.opentv.sync.SyncClient
import app.opentv.sync.SyncPosition
import app.opentv.sync.SyncServer
import kotlinx.serialization.json.Json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import android.util.Log
import app.opentv.core.StatusBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
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
        /**
         * False until the saved sources have been read from the database once. Distinguishing
         * "no sources yet loaded" from "loaded, and there are none" is what stops a returning
         * user being sent to the setup screen — and asked for their provider again — during the
         * brief moment before the database responds on a cold launch.
         */
        val loaded: Boolean = false,
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
                _ui.value = _ui.value.copy(sources = sources, loaded = true)
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
            // Load live channels first and get the user watching straight away. Movies, series and
            // the guide are what make a big provider take minutes — they load in the background so
            // "loading channels" is a few seconds, not a twenty-minute blank screen.
            when (val result = graph.catalogRepository.syncLive(saved, now)) {
                is CatalogRepository.SyncResult.Failed -> {
                    _ui.value = _ui.value.copy(syncing = false, syncMessage = result.reason)
                    onDone(false)
                    return@launch
                }
                is CatalogRepository.SyncResult.Success -> {
                    _ui.value = _ui.value.copy(
                        syncing = false,
                        syncMessage = "Loaded ${result.channelCount} channels. The guide is " +
                            "loading in the background; Movies and Shows load when you open them.",
                    )
                    onDone(true)
                }
            }

            // Background: the guide. Movies/series are pulled on demand from their own tabs, so
            // nothing the user hasn't asked for ever blocks the channels they can already watch.
            runCatching {
                val summary = StatusBus.during("Building the TV guide…") {
                    graph.epgRepository.syncAll(now)
                }
                _ui.value = _ui.value.copy(
                    syncMessage = when {
                        summary.channelsMatched > 0 ->
                            "Guide ready — matched ${summary.channelsMatched} of " +
                                "${summary.channelsTotal} channels."
                        else ->
                            "Channels ready. No guide data matched yet — add a free guide " +
                                "under Guide settings."
                    },
                )
                // Book any new series-link airings the fresh guide just revealed.
                runCatching { graph.recordingEngine.rescanSeriesRules() }
            }.onFailure { Log.w("OpenTV", "Background VOD/guide load failed", it) }
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
            }
            val summary = graph.epgRepository.syncAll(now, force = true)
            problems += summary.feedsFailed
            runCatching { graph.recordingEngine.rescanSeriesRules() }
            _ui.value = _ui.value.copy(
                syncing = false,
                syncMessage = if (problems == 0) {
                    "Refreshed $channels channels, guide matched " +
                        "${summary.channelsMatched} of ${summary.channelsTotal}."
                } else {
                    "Refreshed $channels channels, $problems problem(s) — see Guide settings."
                },
            )
        }
    }

    fun blankDraft() = Source(name = "", kind = SourceKind.XTREAM, url = "")
}

class ChannelsViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)
    private val settings = graph.settings

    /** null = All. Exposed so the sidebar can highlight the active entry. */
    val selectedCategory = MutableStateFlow<String?>(null)
    val favouritesOnly = MutableStateFlow(false)
    private val query = MutableStateFlow("")
    private val nowTick = MutableStateFlow(System.currentTimeMillis())

    /**
     * One rail entry = one *logical* category.
     *
     * Providers split categories by codec — 'UK| GENERAL HD/RAW' and 'UK| GENERAL hevc'
     * are the same shelf twice. Fold them by normalised name, show the clean label, and
     * filter across every underlying id at once. This is the on-device version of what
     * Viewella did on a server it had to pay for.
     */
    data class CategoryGroup(val key: String, val label: String, val ids: List<String>)

    val categoryGroups: StateFlow<List<CategoryGroup>> =
        graph.catalogRepository.observeCategories(StreamKind.LIVE)
            .map { raw ->
                val groups = LinkedHashMap<String, Pair<String, MutableList<String>>>()
                for (category in raw) {
                    val n = ChannelNameNormalizer.normalize(category.name)
                    val key = n.groupKey.ifEmpty { category.id }
                    val entry = groups.getOrPut(key) { n.baseName to mutableListOf() }
                    entry.second += category.id
                }
                groups.map { (key, value) -> CategoryGroup(key, value.first, value.second) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * The category ids to hide from the guide *right now*: the ids behind every group the user
     * marked adult — unless the session is unlocked, in which case nothing is hidden.
     */
    private val hiddenCategoryIds: StateFlow<Set<String>> =
        combine(categoryGroups, settings.hiddenCategories, settings.hiddenUnlocked) { groups, hidden, unlocked ->
            if (unlocked) emptySet()
            else groups.filter { it.key in hidden }.flatMap { it.ids }.toSet()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** The rail's view of categories: hidden ones drop out until the session is unlocked. */
    val visibleCategoryGroups: StateFlow<List<CategoryGroup>> =
        combine(categoryGroups, settings.hiddenCategories, settings.hiddenUnlocked) { groups, hidden, unlocked ->
            if (unlocked) groups else groups.filter { it.key !in hidden }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * One row on screen = one *logical* channel.
     *
     * [primary] is the best-quality variant and what plays on click; [variants] is every
     * quality of the same channel, for the in-player switch. `UK| BBC ONE SD/HD/FHD/RAW`
     * is one row, not four — the guide has one BBC One, and so should we.
     */
    data class Row(
        val primary: Channel,
        val variants: List<Channel>,
        val now: Programme?,
        val next: Programme?,
        /** Every programme for this channel inside the guide window, start-ordered. */
        val programmes: List<Programme>,
    ) {
        val key: Any get() = if (primary.groupKey.isEmpty()) primary.id else primary.groupKey
    }

    /**
     * The guide's left edge: the current half-hour, rounded down. Programme block positions
     * are measured from here. Recomputed on each tick so the grid drifts with real time.
     */
    val windowStartMillis: StateFlow<Long> = nowTick
        .map { now -> now - (now % HALF_HOUR_MILLIS) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000),
            System.currentTimeMillis().let { it - it % HALF_HOUR_MILLIS })

    private data class RowsKey(
        val categoryIds: List<String>?,
        val favs: Boolean,
        val query: String,
        val hiddenIds: Set<String>,
    )

    /**
     * The whole guide window's programmes, grouped by their EPG channel id once — the single
     * expensive step. Grouping the entire catalogue's programmes (hundreds of thousands of rows
     * on a big provider) is what made every category tap slow, because it used to run inside the
     * per-category flow and rebuild from scratch each time. Built here once, shared across every
     * category switch, so a tap only has to regroup that category's channels — quick.
     *
     * Keyed on the half-hour bucket (not the raw minute tick) so it holds steady while you flick
     * between categories and refreshes at most twice an hour; the underlying Room flow also
     * re-emits on its own when an EPG sync lands new programmes.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val programmeIndex: StateFlow<Map<String, List<Programme>>> =
        nowTick
            .map { it - it % HALF_HOUR_MILLIS }
            .distinctUntilChanged()
            .flatMapLatest { windowStart ->
                graph.epgRepository
                    .observeWindow(windowStart, windowStart + GUIDE_LOOKAHEAD_MILLIS)
                    .map { programmes -> programmes.groupBy { it.epgChannelId } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Guards the start-up loading bar so it shows once, on the first build, not on every tap. */
    @Volatile
    private var guideBuilt = false

    @OptIn(ExperimentalCoroutinesApi::class)
    val rows: StateFlow<List<Row>> =
        combine(selectedCategory, favouritesOnly, query, categoryGroups, hiddenCategoryIds) {
                category, favs, q, groups, hidden ->
            val ids = category?.let { key -> groups.firstOrNull { it.key == key }?.ids }
            RowsKey(ids, favs, q, hidden)
        }
            .flatMapLatest { key ->
                val channelFlow = when {
                    key.query.isNotBlank() -> graph.catalogRepository.searchChannels(key.query)
                    key.favs -> graph.catalogRepository.observeFavouriteChannels()
                    key.categoryIds != null -> graph.catalogRepository.observeChannelsIn(key.categoryIds)
                    else -> graph.catalogRepository.observeChannels()
                }
                // Combine the (per-category) channel list with the shared, already-grouped
                // programme index. Switching category rebuilds only the channel→row grouping;
                // the heavy programme grouping happened once and is reused, so a tap is quick.
                // Deliberately NOT combined with the minute tick: building "All channels" (20k
                // rows) can take longer than 60s, and a tick landing mid-build would cancel and
                // restart it via flatMapLatest so it would never finish. "Now" is captured per
                // build instead; the guide's live progress bars advance off the screen's clock.
                combine(channelFlow, programmeIndex) { channels, byEpgChannel ->
                    val now = System.currentTimeMillis()
                    // Adult/hidden channels drop out everywhere they could otherwise leak —
                    // All, search and favourites — until the session is unlocked.
                    val visible =
                        if (key.hiddenIds.isEmpty()) channels
                        else channels.filter { it.categoryId !in key.hiddenIds }
                    // Show the size-aware loading bar for the FIRST guide build only (start-up).
                    // After that the guide is built and flicking between categories is cheap, so
                    // don't flash a loading line on every tap — that bar was only ever meant for
                    // the one-time heavy load, and leaking it per-category read as a stuck "100%".
                    val firstBuild = !guideBuilt
                    if (firstBuild) StatusBus.set(sizeMessage(visible.size), 0f)
                    buildRows(visible, byEpgChannel, now, reportProgress = firstBuild)
                }
            }
            // Grouping thousands of channels against a 12-hour, all-feeds programme window is heavy
            // enough to freeze the UI for a big provider — a category tap that took minutes. Run the
            // whole pipeline off the main thread so the list just appears when it's ready.
            .flowOn(Dispatchers.Default)
            // Once the first guide is on screen, clear the start-up bar — and never show it for a
            // plain category switch again.
            .onEach { if (!guideBuilt) { guideBuilt = true; StatusBus.set(null) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildRows(
        channels: List<Channel>,
        byEpgChannel: Map<String, List<Programme>>,
        now: Long,
        reportProgress: Boolean = false,
    ): List<Row> {
        // The programme→channel grouping is done once upstream (see [programmeIndex]) and passed
        // in ready-made, rather than regrouping the whole EPG on every category tap. Grouping is
        // kept out of SQL because the programme query cannot take a channel-id list without
        // hitting SQLite's bound-variable cap, and quality-variant folding is pure list work.
        val groups = LinkedHashMap<Any, MutableList<Channel>>()
        for (channel in channels) {
            val key: Any = channel.groupKey.ifEmpty { channel.id }
            groups.getOrPut(key) { mutableListOf() } += channel
        }

        val total = groups.size.coerceAtLeast(1)
        var built = 0
        return groups.values.map { group ->
            group.sortByDescending { it.qualityRank }
            // Only genuinely different qualities are switchable; identical-quality dupes
            // (same stream in two categories) collapse to one, so no false "2 qualities".
            val variants = distinctByQuality(group)
            val primary = variants.first()

            // Walk every variant's guide-id candidates (override → provider → matched)
            // and use the first that actually has programmes. An id that never lights up
            // must not shadow a sibling variant whose id does.
            val list = variants
                .flatMap { it.epgCandidates }
                .firstNotNullOfOrNull { id -> byEpgChannel[id] }
                ?.sortedBy { it.startUtcMillis }
                .orEmpty()

            // Feed the progress bar as rows come together — this is the slow part on a big
            // provider, so it's what the percentage should actually track.
            if (reportProgress) {
                built++
                if (built % 400 == 0 || built == total) {
                    StatusBus.setProgress(built.toFloat() / total)
                }
            }

            Row(
                primary = primary,
                variants = variants,
                now = list.firstOrNull { it.isLiveAt(now) },
                next = list.firstOrNull { it.startUtcMillis > now },
                programmes = list,
            )
        }
    }

    /** A friendly, size-aware line for the load — a small provider gets a quick word, a huge one
     * gets a "bear with me". Used at start-up so the wait always says what it's doing. */
    private fun sizeMessage(count: Int): String = when {
        count <= 0 -> "Building the guide…"
        count < 2000 -> "Loading $count channels — a small one, this'll be quick."
        count < 8000 -> "Loading $count channels — a fair few, give me a moment…"
        else -> "Loading $count channels — a big one, bear with me, I'm on it…"
    }

    val favourites: StateFlow<List<Channel>> =
        graph.catalogRepository.observeFavouriteChannels()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectCategory(id: String?) {
        favouritesOnly.value = false
        selectedCategory.value = id
    }

    fun selectFavourites() {
        favouritesOnly.value = true
        selectedCategory.value = null
    }

    fun search(text: String) { query.value = text }

    // ---- Dedicated channel search --------------------------------------------------------------
    // Kept separate from the guide's `rows` on purpose: `rows` loads the entire 12-hour EPG
    // window, which is far too heavy to run on every keystroke (that was the ~30s search lag).
    // This path is channel-only — no programme window — debounced, and capped by the DAO's LIMIT.
    private val searchInput = MutableStateFlow("")

    fun setSearchQuery(text: String) { searchInput.value = text }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchResults: StateFlow<List<Row>> =
        searchInput
            .map { it.trim() }
            .debounce(200)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < 2) flowOf(emptyList())
                else graph.catalogRepository.searchChannels(q)
                    // No EPG here — group channels into rows with no now/next. Finding and
                    // playing the channel is the job; guide detail would cost the slow query.
                    .map { channels -> buildRows(channels, emptyMap(), System.currentTimeMillis()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun tick() { nowTick.value = System.currentTimeMillis() }

    // ---- Channel manager (search, then hide/show or favourite) ---------------------------------
    private val managerInput = MutableStateFlow("")

    fun setManagerQuery(text: String) { managerInput.value = text }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val managerResults: StateFlow<List<Row>> =
        managerInput
            .map { it.trim() }
            .debounce(200)
            .distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < 2) flowOf(emptyList())
                else graph.catalogRepository.searchChannelsIncludingHidden(q)
                    .map { channels -> buildRows(channels, emptyMap(), System.currentTimeMillis()) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Hide or show a whole logical channel — every quality variant follows. */
    fun setRowHidden(row: Row, hidden: Boolean) {
        viewModelScope.launch {
            row.variants.forEach { graph.catalogRepository.setChannelHidden(it.id, hidden) }
        }
    }

    fun toggleFavourite(row: Row) {
        viewModelScope.launch {
            // Favouriting the row favourites the logical channel: every variant follows,
            // so the choice survives switching quality.
            val target = !row.primary.favourite
            row.variants.forEach { graph.catalogRepository.setChannelFavourite(it.id, target) }
        }
    }

    fun hide(row: Row) {
        viewModelScope.launch {
            row.variants.forEach { graph.catalogRepository.setChannelHidden(it.id, true) }
        }
    }

    private companion object {
        const val GUIDE_LOOKAHEAD_MILLIS = 12 * 60 * 60 * 1000L
        const val HALF_HOUR_MILLIS = 30 * 60 * 1000L
    }
}

/** Backs the Guide settings screen: feed list, toggles, custom URLs, match report. */
class EpgViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)

    data class UiState(
        val feeds: List<EpgFeed> = emptyList(),
        val syncing: Boolean = false,
        val statusLine: String? = null,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch { graph.epgRepository.ensureFeeds() }
        viewModelScope.launch {
            graph.epgRepository.observeFeeds().collect { feeds ->
                _ui.value = _ui.value.copy(feeds = feeds)
            }
        }
    }

    fun setEnabled(feed: EpgFeed, enabled: Boolean) {
        viewModelScope.launch {
            graph.epgRepository.setFeedEnabled(feed.id, enabled)
            if (enabled) refresh() // turning a guide on should visibly do something
        }
    }

    fun addCustom(name: String, url: String) {
        viewModelScope.launch {
            graph.epgRepository.addCustomFeed(name, url)
            refresh()
        }
    }

    fun remove(feed: EpgFeed) {
        viewModelScope.launch { graph.epgRepository.removeFeed(feed) }
    }

    fun refresh() {
        if (_ui.value.syncing) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(syncing = true, statusLine = "Downloading guides…")
            val summary = graph.epgRepository.syncAll(System.currentTimeMillis(), force = true)
            _ui.value = _ui.value.copy(
                syncing = false,
                statusLine = "Guide matched ${summary.channelsMatched} of " +
                    "${summary.channelsTotal} channels" +
                    if (summary.feedsFailed > 0) " · ${summary.feedsFailed} feed(s) failed" else "",
            )
        }
    }
}

class VodViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)
    private val settings = graph.settings

    /** One card in the Continue Watching shelf — a movie or episode with somewhere left to go. */
    data class ResumeItem(
        val mediaKey: String,
        val title: String,
        val posterUrl: String?,
        val streamUrl: String,
        val progress: Float,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    val continueWatching: StateFlow<List<ResumeItem>> =
        settings.activeProfileId
            .flatMapLatest { pid -> graph.playbackPositions.observeRecent(pid) }
            .mapLatest { positions -> positions.filter { !it.isFinished }.mapNotNull { resolveResume(it) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun resolveResume(pos: PlaybackPosition): ResumeItem? {
        val parts = pos.mediaKey.split(":", limit = 2)
        if (parts.size != 2) return null
        val id = parts[1].toLongOrNull() ?: return null
        val progress =
            if (pos.durationMillis > 0) (pos.positionMillis.toFloat() / pos.durationMillis).coerceIn(0f, 1f) else 0f
        return when (parts[0]) {
            "movie" -> graph.catalogRepository.movie(id)?.let {
                ResumeItem(pos.mediaKey, it.name, it.posterUrl, it.streamUrl, progress)
            }
            "ep" -> graph.catalogRepository.episode(id)?.let {
                ResumeItem(
                    pos.mediaKey,
                    it.title.ifBlank { "S${it.season} E${it.episodeNumber}" },
                    it.stillUrl,
                    it.streamUrl,
                    progress,
                )
            }
            else -> null
        }
    }

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

    // Movies + series load on demand — the first time the user opens Movies or Shows — rather than
    // up front at login. A provider's 40,000-title VOD list is exactly what makes a first sync
    // crawl, and most sessions only ever watch live TV. Loaded once per app run.
    private val _vodLoading = MutableStateFlow(false)
    val vodLoading: StateFlow<Boolean> = _vodLoading.asStateFlow()

    @Volatile private var vodRequested = false

    fun ensureVodLoaded() {
        if (vodRequested) return
        vodRequested = true
        viewModelScope.launch {
            _vodLoading.value = true
            StatusBus.during("Loading movies & shows…") {
                runCatching {
                    val now = System.currentTimeMillis()
                    for (source in graph.sourceRepository.enabled()) {
                        graph.catalogRepository.syncVod(source, now)
                    }
                }
            }
            _vodLoading.value = false
        }
    }

    // ---- VOD search (shared by the unified search screen) --------------------------------------
    private val vodSearchInput = MutableStateFlow("")

    fun setVodSearchQuery(text: String) { vodSearchInput.value = text }

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val movieResults: StateFlow<List<Movie>> =
        vodSearchInput.map { it.trim() }.debounce(200).distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < 2) flowOf(emptyList()) else graph.catalogRepository.searchMovies(q)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val seriesResults: StateFlow<List<Series>> =
        vodSearchInput.map { it.trim() }.debounce(200).distinctUntilChanged()
            .flatMapLatest { q ->
                if (q.length < 2) flowOf(emptyList()) else graph.catalogRepository.searchSeries(q)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

    /** For the series detail screen: look the series up by row id. */
    fun seriesById(id: Long): kotlinx.coroutines.flow.Flow<Series?> =
        kotlinx.coroutines.flow.flow { emit(graph.catalogRepository.series(id)) }

    /** An always-empty episode flow, so the detail screen has something before a series loads. */
    val noEpisodes: kotlinx.coroutines.flow.Flow<List<app.opentv.data.model.Episode>> =
        kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun movieById(id: Long): Movie? = graph.catalogRepository.movie(id)

    /** The user-agent to play a movie/episode with (per source). */
    suspend fun userAgentForSource(sourceId: Long): String =
        graph.sourceRepository.byId(sourceId)?.userAgent ?: "OpenTV/0.1 (Android)"
}

/**
 * Local viewing profiles. No accounts — just names on this device — with the active one stored in
 * [app.opentv.core.AppSettings] so the whole app agrees on whose watch history is in play.
 */
class ProfilesViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)
    private val settings = graph.settings

    val profiles: StateFlow<List<Profile>> =
        graph.profiles.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeProfileId: StateFlow<Long> = settings.activeProfileId

    init {
        // Fresh installs create the table empty (no migration runs), so seed the default profile.
        viewModelScope.launch {
            if (graph.profiles.all().isEmpty()) {
                graph.profiles.insert(Profile(name = "Me", createdAtMillis = 0))
            }
        }
    }

    fun addProfile(name: String) {
        viewModelScope.launch {
            val id = graph.profiles.insert(
                Profile(name = name.trim().ifBlank { "New profile" }, createdAtMillis = 0),
            )
            settings.setActiveProfile(id)
        }
    }

    fun rename(id: Long, name: String) {
        viewModelScope.launch { graph.profiles.rename(id, name.trim().ifBlank { "Profile" }) }
    }

    fun select(id: Long) { settings.setActiveProfile(id) }

    fun remove(id: Long) {
        // The base profile stays — there is always at least one to be.
        if (id == 1L) return
        viewModelScope.launch {
            graph.profiles.deletePositions(id)
            graph.profiles.delete(id)
            if (settings.activeProfileId.value == id) settings.setActiveProfile(1L)
        }
    }
}

/**
 * Local, device-to-device watch-history sync — no servers of ours.
 *
 * One device shares (a short-lived LAN server behind a six-digit code); the other receives by
 * typing that device's address and code. Only resume positions travel, keyed by stream URL and
 * profile name so they land on the right film and the right person on the other device. Merge is
 * newest-wins per item, so syncing both ways leaves the two devices agreeing.
 */
class SyncViewModel(app: Application) : AndroidViewModel(app) {
    private val graph = ServiceLocator.get(app)
    private val server = SyncServer(viewModelScope)
    private val client = SyncClient(graph.httpClient)

    val serverState: StateFlow<SyncServer.State> = server.state

    sealed interface ReceiveState {
        data object Idle : ReceiveState
        data object Connecting : ReceiveState
        data class Done(val merged: Int) : ReceiveState
        data class Failed(val message: String) : ReceiveState
    }

    private val _receiveState = MutableStateFlow<ReceiveState>(ReceiveState.Idle)
    val receiveState: StateFlow<ReceiveState> = _receiveState.asStateFlow()

    // ---- NAS ("cloud") sync ------------------------------------------------------------------

    private val nas = NasSync(graph)

    /** Whether an SMB/NAS is set up at all — the NAS pane needs one before it can do anything. */
    val smbHost: StateFlow<String> = graph.settings.smbHost
    val nasAutoSync: StateFlow<Boolean> = graph.settings.nasAutoSync

    sealed interface NasState {
        data object Idle : NasState
        data object Syncing : NasState
        data class Done(val message: String) : NasState
        data class Failed(val message: String) : NasState
    }

    private val _nasState = MutableStateFlow<NasState>(NasState.Idle)
    val nasState: StateFlow<NasState> = _nasState.asStateFlow()

    fun setNasAutoSync(enabled: Boolean) = graph.settings.setNasAutoSync(enabled)

    fun syncNas() {
        if (_nasState.value is NasState.Syncing) return
        viewModelScope.launch {
            _nasState.value = NasState.Syncing
            val ctx = getApplication<Application>()
            _nasState.value = when (val result = nas.sync()) {
                is NasSync.Result.Success ->
                    if (result.peers == 0) NasState.Done(ctx.getString(R.string.sync_nas_uploaded))
                    else NasState.Done(ctx.getString(R.string.sync_nas_done, result.merged, result.peers))
                is NasSync.Result.NotConfigured ->
                    NasState.Failed(ctx.getString(R.string.sync_nas_needs_setup))
                is NasSync.Result.Failed ->
                    NasState.Failed(ctx.getString(R.string.sync_nas_failed))
            }
        }
    }

    fun startSharing() {
        viewModelScope.launch { server.start(buildBundleJson()) }
    }

    fun stopSharing() = server.stop()

    fun receive(address: String, code: String) {
        viewModelScope.launch {
            _receiveState.value = ReceiveState.Connecting
            client.pull(address, code)
                .onSuccess { _receiveState.value = ReceiveState.Done(merge(it)) }
                .onFailure { _receiveState.value = ReceiveState.Failed(it.message ?: "Sync failed.") }
        }
    }

    fun resetReceive() { _receiveState.value = ReceiveState.Idle }

    private suspend fun buildBundleJson(): String {
        val names = graph.profiles.all().associate { it.id to it.name }
        val items = graph.playbackPositions.all().mapNotNull { pos ->
            val profileName = names[pos.profileId] ?: return@mapNotNull null
            val parts = pos.mediaKey.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val id = parts[1].toLongOrNull() ?: return@mapNotNull null
            val streamUrl = when (parts[0]) {
                "movie" -> graph.catalogRepository.movie(id)?.streamUrl
                "ep" -> graph.catalogRepository.episode(id)?.streamUrl
                else -> null
            } ?: return@mapNotNull null
            SyncPosition(profileName, streamUrl, parts[0], pos.positionMillis, pos.durationMillis, pos.updatedAtMillis)
        }
        return Json.encodeToString(SyncBundle.serializer(), SyncBundle(positions = items))
    }

    private suspend fun merge(bundle: SyncBundle): Int {
        var merged = 0
        for (item in bundle.positions) {
            val profileId = profileIdFor(item.profile) ?: continue
            val localId = when (item.kind) {
                "movie" -> graph.catalogRepository.movieByStreamUrl(item.streamUrl)?.id
                "ep" -> graph.catalogRepository.episodeByStreamUrl(item.streamUrl)?.id
                else -> null
            } ?: continue
            val mediaKey = "${item.kind}:$localId"
            val existing = graph.playbackPositions.get(profileId, mediaKey)
            if (existing == null || item.updatedAtMillis > existing.updatedAtMillis) {
                graph.playbackPositions.upsert(
                    PlaybackPosition(
                        profileId = profileId,
                        mediaKey = mediaKey,
                        positionMillis = item.positionMillis,
                        durationMillis = item.durationMillis,
                        updatedAtMillis = item.updatedAtMillis,
                    ),
                )
                merged++
            }
        }
        return merged
    }

    private suspend fun profileIdFor(name: String): Long? {
        graph.profiles.byName(name)?.let { return it.id }
        return runCatching { graph.profiles.insert(Profile(name = name, createdAtMillis = 0)) }.getOrNull()
    }

    override fun onCleared() {
        server.stop()
    }
}
