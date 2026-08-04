/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.core.AppSettings
import app.opentv.data.db.CategoryDao
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.EpisodeDao
import app.opentv.data.db.MovieDao
import app.opentv.data.db.SeriesDao
import app.opentv.data.db.SourceDao
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.LiveStreamFormat
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.M3uParser
import app.opentv.data.remote.XtreamApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keeps one channel per distinct quality signature, preserving order (so "best first" is
 * preserved when the caller has already sorted by rank).
 *
 * Two streams with the same rank AND the same label are treated as the same quality — one is
 * kept, the rest dropped. This is what collapses "RAW / RAW" and "HD / HD" duplicates down to
 * a single option, and turns a falsely-multi-quality channel back into a single one.
 */
internal fun distinctByQuality(channels: List<app.opentv.data.model.Channel>): List<app.opentv.data.model.Channel> {
    val seen = HashSet<String>()
    return channels.filter { seen.add("${it.qualityRank}|${it.qualityLabel.lowercase()}") }
}

/**
 * Channels, movies and series.
 *
 * Same principle as [EpgRepository]: a refresh that fails must never leave the user with less
 * than they started with. Catalogue writes go through [ChannelDao.replaceCatalogue], which
 * merges rather than wipes and carries favourites, hidden flags and manual ordering across.
 */
class CatalogRepository(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val api: XtreamApi,
    private val http: OkHttpClient,
    private val settings: AppSettings,
) {

    sealed interface SyncResult {
        data class Success(
            val channelCount: Int,
            val movieCount: Int,
            val seriesCount: Int,
        ) : SyncResult

        data class Failed(val reason: String, val cause: Throwable?) : SyncResult
    }

    fun observeChannels(sourceId: Long? = null, categoryId: String? = null): Flow<List<Channel>> =
        channelDao.observe(sourceId, categoryId)

    fun observeChannelsIn(categoryIds: List<String>): Flow<List<Channel>> =
        channelDao.observeInCategories(categoryIds)

    /**
     * Channels in a set of categories INCLUDING hidden ones, optionally scoped to one source — the
     * channel manager's browse feed. Separate from [observeChannelsIn] (which drops `hidden` rows
     * for the guide) because the manager must show hidden channels so they can be un-hidden.
     */
    fun observeChannelsInIncludingHidden(sourceId: Long?, categoryIds: List<String>): Flow<List<Channel>> =
        channelDao.observeInCategoriesIncludingHidden(sourceId, categoryIds)

    fun observeFavouriteChannels(): Flow<List<Channel>> = channelDao.observeFavourites()

    /** Reactive number of visible channels on disk — the UI uses this to tell "guide still
     * building" (channels exist) apart from "nothing loaded" (a failed or empty sync). */
    fun observeChannelCount(): Flow<Int> = channelDao.observeVisibleCount()

    fun observeCategories(kind: StreamKind): Flow<List<Category>> = categoryDao.observe(kind)

    fun observeMovies(categoryId: String? = null): Flow<List<Movie>> = movieDao.observe(categoryId)

    fun observeSeries(categoryId: String? = null): Flow<List<Series>> = seriesDao.observe(categoryId)

    fun observeEpisodes(sourceId: Long, seriesId: String): Flow<List<Episode>> =
        episodeDao.observeForSeries(sourceId, seriesId)

    fun searchChannels(query: String): Flow<List<Channel>> = channelDao.search(query)

    fun searchChannelsIncludingHidden(query: String): Flow<List<Channel>> =
        channelDao.searchIncludingHidden(query)

    fun searchMovies(query: String): Flow<List<Movie>> = movieDao.search(query)

    fun searchSeries(query: String): Flow<List<Series>> = seriesDao.search(query)

    suspend fun channel(id: Long): Channel? = channelDao.byId(id)

    suspend fun movie(id: Long): Movie? = movieDao.byId(id)

    suspend fun episode(id: Long): Episode? = episodeDao.byId(id)

    suspend fun movieByStreamUrl(url: String): Movie? = movieDao.byStreamUrl(url)

    suspend fun episodeByStreamUrl(url: String): Episode? = episodeDao.byStreamUrl(url)

    suspend fun series(id: Long): app.opentv.data.model.Series? = seriesDao.byId(id)

    suspend fun setChannelFavourite(id: Long, favourite: Boolean) =
        channelDao.setFavourite(id, favourite)

    suspend fun setChannelHidden(id: Long, hidden: Boolean) = channelDao.setHidden(id, hidden)

    suspend fun setMovieFavourite(id: Long, favourite: Boolean) =
        movieDao.setFavourite(id, favourite)

    // --- Sync helpers: read/apply curation by stable stream URL ---
    suspend fun favouriteChannelUrls(): List<String> = channelDao.favouriteUrls()
    suspend fun hiddenChannelUrls(): List<String> = channelDao.hiddenUrls()
    suspend fun markChannelFavouriteByUrl(url: String) = channelDao.markFavouriteByUrl(url)
    suspend fun markChannelHiddenByUrl(url: String) = channelDao.markHiddenByUrl(url)
    suspend fun favouriteMovieUrls(): List<String> = movieDao.favouriteUrls()
    suspend fun markMovieFavouriteByUrl(url: String) = movieDao.markFavouriteByUrl(url)
    suspend fun favouriteSeriesIds(): List<String> = seriesDao.favouriteSeriesIds()
    suspend fun markSeriesFavouriteBySeriesId(seriesId: String) =
        seriesDao.markFavouriteBySeriesId(seriesId)

    /** Series episodes are fetched lazily — panels are slow and most series are never opened. */
    suspend fun ensureEpisodes(source: Source, seriesId: String) {
        if (source.kind != SourceKind.XTREAM) return
        runCatching { api.episodes(source, seriesId) }
            .onSuccess { if (it.isNotEmpty()) episodeDao.upsertAll(it) }
            .onFailure { Log.w(TAG, "Episode fetch failed for series $seriesId", it) }
    }

    /**
     * Full catalogue: live channels, then movies and series. Used by the periodic worker and by a
     * manual refresh, where there's no user staring at a spinner. The initial add takes the faster
     * [syncLive] + background [syncVod] path instead, so the guide appears without waiting for a
     * 40,000-title VOD list.
     */
    suspend fun sync(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        // Skip fetching a content type the user has switched off — that's the whole speed-up.
        // Live is gated here (not in syncLive) so onboarding's direct syncLive still loads channels.
        // Already-synced rows are left untouched: turning a type back on and refreshing restores it.
        val live =
            if (settings.liveEnabled.value) syncLive(source, nowUtcMillis)
            else SyncResult.Success(0, 0, 0)
        if (live is SyncResult.Success && source.kind == SourceKind.XTREAM) {
            runCatching { syncXtreamVod(source, nowUtcMillis) }
                .onFailure { Log.w(TAG, "VOD sync failed for source ${source.id}", it) }
        }
        live
    }

    /** Live channels only — the fast path so the guide can show before VOD and the guide load. */
    suspend fun syncLive(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            when (source.kind) {
                SourceKind.XTREAM -> syncXtreamLive(source, nowUtcMillis)
                SourceKind.M3U -> syncM3u(source, nowUtcMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catalogue sync failed for source ${source.id}", e)
            SyncResult.Failed(e.message ?: "The catalogue could not be downloaded.", e)
        }
    }

    /** Movies + series — best-effort, meant to run in the background so a huge VOD list never
     * blocks live TV. Silent on failure: an account with no VOD is normal, not an error. */
    suspend fun syncVod(source: Source, nowUtcMillis: Long) = withContext(Dispatchers.IO) {
        runCatching { if (source.kind == SourceKind.XTREAM) syncXtreamVod(source, nowUtcMillis) }
            .onFailure { Log.w(TAG, "VOD sync failed for source ${source.id}", it) }
    }

    private suspend fun syncXtreamLive(source: Source, nowUtcMillis: Long): SyncResult {
        // Authenticate first so a wrong password produces a clear message rather than
        // four separate confusing failures further down.
        api.authenticate(source)

        val liveCategories = api.liveCategories(source)
        val channels = api.liveStreams(source)
        if (channels.isEmpty()) {
            return SyncResult.Failed(
                "The server returned no channels. The account may have no package assigned.",
                null,
            )
        }

        categoryDao.upsertAll(liveCategories)
        val categoryNames = liveCategories.associate { it.id to it.name }
        channelDao.replaceCatalogue(source.id, normalized(channels, categoryNames), nowUtcMillis)
        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(channels.size, 0, 0)
    }

    private suspend fun syncXtreamVod(source: Source, nowUtcMillis: Long) {
        // VOD is optional: plenty of accounts have live TV only, and a 404 on get_vod_streams
        // must not cost the user their channel list. Movies and series are gated independently so
        // a user who only turned off, say, Series still gets their movie library refreshed.
        val moviesOn = settings.moviesEnabled.value
        val seriesOn = settings.seriesEnabled.value
        if (!moviesOn && !seriesOn) return

        val movieCategories =
            if (moviesOn) runCatching { api.movieCategories(source) }.getOrDefault(emptyList())
            else emptyList()
        val movies =
            if (moviesOn) runCatching { api.movies(source) }.getOrDefault(emptyList())
            else emptyList()
        val seriesCategories =
            if (seriesOn) runCatching { api.seriesCategories(source) }.getOrDefault(emptyList())
            else emptyList()
        val series =
            if (seriesOn) runCatching { api.series(source) }.getOrDefault(emptyList())
            else emptyList()

        if (movieCategories.isNotEmpty() || seriesCategories.isNotEmpty()) {
            categoryDao.upsertAll(movieCategories + seriesCategories)
        }
        if (movies.isNotEmpty()) movieDao.upsertAll(movies)
        if (series.isNotEmpty()) seriesDao.upsertAll(series)
    }

    private suspend fun syncM3u(source: Source, nowUtcMillis: Long): SyncResult {
        val request = Request.Builder()
            .url(source.url)
            .header("User-Agent", source.userAgent)
            .build()

        val parsed = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return SyncResult.Failed("Playlist download failed (HTTP ${response.code}).", null)
            }
            val stream = response.body?.byteStream()
                ?: return SyncResult.Failed("The playlist was empty.", null)
            M3uParser.parse(stream, source.id)
        }

        if (parsed.channels.isEmpty()) {
            return SyncResult.Failed(
                "No channels found in that playlist. Check the URL points at an M3U file.",
                null,
            )
        }

        // Synthesise categories from group-title so the UI has something to group by.
        val categories = parsed.channels
            .mapNotNull { it.categoryId }
            .distinct()
            .sorted()
            .mapIndexed { index, name ->
                Category(
                    id = name,
                    sourceId = source.id,
                    name = name,
                    kind = StreamKind.LIVE,
                    sortIndex = index,
                )
            }

        categoryDao.upsertAll(categories)
        val categoryNames = categories.associate { it.id to it.name }
        channelDao.replaceCatalogue(source.id, normalized(parsed.channels, categoryNames), nowUtcMillis)

        // If the playlist declared its own guide URL and the user did not set one, adopt it.
        if (source.epgUrl.isNullOrBlank() && !parsed.declaredEpgUrl.isNullOrBlank()) {
            sourceDao.update(source.copy(epgUrl = parsed.declaredEpgUrl))
        }

        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(parsed.channels.size, 0, 0)
    }

    /**
     * Stamps every channel with its normalised identity before it is stored.
     *
     * This one pass powers both headline features: [Channel.groupKey] folds quality
     * variants of a channel into a single row and is the join key for EPG matching, and
     * [Channel.displayName] is the cleaned name the UI shows instead of `UK| BBC ONE FHD`.
     */
    private fun normalized(
        channels: List<Channel>,
        categoryNames: Map<String, String>,
    ): List<Channel> {
        val stamped = channels.map { channel ->
            val n = ChannelNameNormalizer.normalize(channel.name)
            var rank = n.qualityRank
            var label = n.qualityLabel
            if (label.isEmpty() || rank == 0) {
                // Some providers put the quality in the CATEGORY, not the channel:
                // 'UK| GENERAL HD/RAW' and 'UK| GENERAL hevc' holding identically named
                // channels. Without this, the player's switch is four buttons all
                // reading 'Standard' — grouped correctly, labelled uselessly.
                val categoryName = channel.categoryId?.let { categoryNames[it] }
                if (categoryName != null) {
                    val c = ChannelNameNormalizer.normalize(categoryName)
                    if (label.isEmpty()) label = c.qualityLabel
                    if (rank == 0) rank = c.qualityRank
                }
            }
            channel.copy(
                displayName = n.baseName,
                groupKey = n.groupKey,
                qualityRank = rank,
                qualityLabel = label,
                // Providers ship decorative separator rows ('#### UK GENERAL ####') as
                // channels. They are headings, not channels — hide them on import.
                hidden = channel.hidden || isSeparatorRow(channel.name),
            )
        }

        return stamped
    }

    /** Decorative list headings: starts AND ends with a run of banner characters. */
    private fun isSeparatorRow(rawName: String): Boolean {
        val t = rawName.trim()
        return t.length >= 6 &&
            t.take(3).all { it in SEPARATOR_CHARS } &&
            t.takeLast(3).all { it in SEPARATOR_CHARS }
    }

    /**
     * Re-cleans every stored channel with the current normaliser, no network needed.
     *
     * displayName, groupKey, quality and separator-hiding are computed at import time, so a
     * change to the normaliser (a new superscript char, a new junk pattern) does not reach
     * channels already in the database until the next full catalogue sync — which can be
     * hours away. This runs the same pass over existing rows locally, so a code fix shows up
     * on the next launch instead of the next sync. Bump [NORMALIZER_VERSION] to trigger it.
     */
    suspend fun renormalizeAll(): Int = withContext(Dispatchers.IO) {
        val existing = channelDao.allForMatching()
        if (existing.isEmpty()) return@withContext 0

        val bySource = existing.groupBy { it.sourceId }
        var changed = 0
        for ((_, channels) in bySource) {
            val names = channelCategoryNames(channels)
            val renamed = normalized(channels, names)
            // Only write rows that actually changed, to keep the write small.
            val diff = renamed.filterIndexed { i, c ->
                val old = channels[i]
                c.displayName != old.displayName || c.groupKey != old.groupKey ||
                    c.qualityLabel != old.qualityLabel || c.qualityRank != old.qualityRank ||
                    c.hidden != old.hidden
            }
            if (diff.isNotEmpty()) {
                diff.chunked(500).forEach { channelDao.upsertAll(it) }
                changed += diff.size
            }
        }
        Log.i(TAG, "Re-normalised $changed channels with the current normaliser")
        changed
    }

    /** Best-effort category-id → name map for a set of channels. */
    private suspend fun channelCategoryNames(channels: List<Channel>): Map<String, String> {
        val ids = channels.mapNotNull { it.categoryId }.toSet()
        if (ids.isEmpty()) return emptyMap()
        return categoryDao.namesFor(ids).associate { it.id to it.name }
    }

    /**
     * The switchable quality variants of a channel, best first, ONE per distinct quality.
     *
     * A provider often lists the same stream in several categories — PRIME American Crimes
     * shows up twice, both RAW. Those are not "qualities"; offering a switch between two
     * identical (and sometimes one dead) feeds is worse than useless. So variants are
     * de-duplicated by their quality signature: only genuinely different qualities survive,
     * and a channel that is really single-quality gets no switch at all.
     */
    suspend fun variants(channel: Channel): List<Channel> {
        if (channel.groupKey.isEmpty()) return listOf(channel)
        val all = channelDao.variantsInGroup(channel.groupKey)
        return distinctByQuality(all).ifEmpty { listOf(channel) }
    }

    /**
     * Switches an Xtream source's live-stream container (HLS ↔ MPEG-TS) and rewrites its live
     * channels' playback URLs in place, so the change takes effect immediately without a re-sync.
     *
     * Xtream only: an M3U source's channel URLs come straight from its playlist and must not be
     * rebuilt from credentials. The `channels` table is live-only (movies and series have their own
     * tables), so every row here is a live channel whose URL derives from [XtreamApi.liveStreamUrl].
     */
    suspend fun setLiveFormat(sourceId: Long, format: LiveStreamFormat) = withContext(Dispatchers.IO) {
        val source = sourceDao.byId(sourceId) ?: return@withContext
        if (source.kind != SourceKind.XTREAM || source.liveFormat == format) return@withContext
        val updated = source.copy(liveFormat = format)
        sourceDao.update(updated)
        // Rebuild each live channel's URL for the new container.
        channelDao.forSource(sourceId).forEach { channel ->
            val newUrl = api.liveStreamUrl(updated, channel.streamId)
            if (newUrl != channel.streamUrl) channelDao.updateStreamUrl(channel.id, newUrl)
        }
    }

    suspend fun deleteSource(sourceId: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteForSource(sourceId)
        categoryDao.deleteForSource(sourceId)
        movieDao.deleteForSource(sourceId)
        seriesDao.deleteForSource(sourceId)
        episodeDao.deleteForSource(sourceId)
        sourceDao.delete(sourceId)
    }

    companion object {
        private const val TAG = "CatalogRepository"
        val SEPARATOR_CHARS = setOf('#', '*', '=', '~', '-', '_', '•', '█', '▓', '|')

        /**
         * Bump this whenever the normaliser changes in a way that should re-process
         * already-imported channels. The app compares it against a stored value on launch
         * and runs [renormalizeAll] once when it moves.
         */
        const val NORMALIZER_VERSION = 2
    }
}
