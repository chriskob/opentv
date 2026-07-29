/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.data.db.CategoryDao
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.EpisodeDao
import app.opentv.data.db.MovieDao
import app.opentv.data.db.SeriesDao
import app.opentv.data.db.SourceDao
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
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

    fun observeFavouriteChannels(): Flow<List<Channel>> = channelDao.observeFavourites()

    fun observeCategories(kind: StreamKind): Flow<List<Category>> = categoryDao.observe(kind)

    fun observeMovies(categoryId: String? = null): Flow<List<Movie>> = movieDao.observe(categoryId)

    fun observeSeries(categoryId: String? = null): Flow<List<Series>> = seriesDao.observe(categoryId)

    fun observeEpisodes(sourceId: Long, seriesId: String): Flow<List<Episode>> =
        episodeDao.observeForSeries(sourceId, seriesId)

    fun searchChannels(query: String): Flow<List<Channel>> = channelDao.search(query)

    fun searchMovies(query: String): Flow<List<Movie>> = movieDao.search(query)

    fun searchSeries(query: String): Flow<List<Series>> = seriesDao.search(query)

    suspend fun channel(id: Long): Channel? = channelDao.byId(id)

    suspend fun movie(id: Long): Movie? = movieDao.byId(id)

    suspend fun setChannelFavourite(id: Long, favourite: Boolean) =
        channelDao.setFavourite(id, favourite)

    suspend fun setChannelHidden(id: Long, hidden: Boolean) = channelDao.setHidden(id, hidden)

    suspend fun setMovieFavourite(id: Long, favourite: Boolean) =
        movieDao.setFavourite(id, favourite)

    /** Series episodes are fetched lazily — panels are slow and most series are never opened. */
    suspend fun ensureEpisodes(source: Source, seriesId: String) {
        if (source.kind != SourceKind.XTREAM) return
        runCatching { api.episodes(source, seriesId) }
            .onSuccess { if (it.isNotEmpty()) episodeDao.upsertAll(it) }
            .onFailure { Log.w(TAG, "Episode fetch failed for series $seriesId", it) }
    }

    suspend fun sync(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        try {
            when (source.kind) {
                SourceKind.XTREAM -> syncXtream(source, nowUtcMillis)
                SourceKind.M3U -> syncM3u(source, nowUtcMillis)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catalogue sync failed for source ${source.id}", e)
            SyncResult.Failed(e.message ?: "The catalogue could not be downloaded.", e)
        }
    }

    private suspend fun syncXtream(source: Source, nowUtcMillis: Long): SyncResult {
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

        // VOD is optional: plenty of accounts have live TV only, and a 404 on get_vod_streams
        // must not cost the user their channel list.
        val movieCategories = runCatching { api.movieCategories(source) }.getOrDefault(emptyList())
        val movies = runCatching { api.movies(source) }.getOrDefault(emptyList())
        val seriesCategories = runCatching { api.seriesCategories(source) }.getOrDefault(emptyList())
        val series = runCatching { api.series(source) }.getOrDefault(emptyList())

        categoryDao.upsertAll(liveCategories + movieCategories + seriesCategories)
        channelDao.replaceCatalogue(source.id, normalized(channels), nowUtcMillis)
        if (movies.isNotEmpty()) movieDao.upsertAll(movies)
        if (series.isNotEmpty()) seriesDao.upsertAll(series)

        sourceDao.markCatalogSynced(source.id, nowUtcMillis)
        return SyncResult.Success(channels.size, movies.size, series.size)
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
        channelDao.replaceCatalogue(source.id, normalized(parsed.channels), nowUtcMillis)

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
    private fun normalized(channels: List<Channel>): List<Channel> = channels.map { channel ->
        val n = ChannelNameNormalizer.normalize(channel.name)
        channel.copy(
            displayName = n.baseName,
            groupKey = n.groupKey,
            qualityRank = n.qualityRank,
            qualityLabel = n.qualityLabel,
        )
    }

    /** Every quality variant of the same logical channel, best first. */
    suspend fun variants(channel: Channel): List<Channel> =
        if (channel.groupKey.isEmpty()) listOf(channel)
        else channelDao.variantsInGroup(channel.groupKey).ifEmpty { listOf(channel) }

    suspend fun deleteSource(sourceId: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteForSource(sourceId)
        categoryDao.deleteForSource(sourceId)
        movieDao.deleteForSource(sourceId)
        seriesDao.deleteForSource(sourceId)
        episodeDao.deleteForSource(sourceId)
        sourceDao.delete(sourceId)
    }

    private companion object {
        const val TAG = "CatalogRepository"
    }
}
