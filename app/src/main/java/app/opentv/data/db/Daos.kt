/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Programme
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.StreamKind
import kotlinx.coroutines.flow.Flow

/** Rows per upsert statement. Keeps a 40,000-channel playlist from building one huge query. */
private const val UPSERT_CHUNK = 500

@Dao
interface SourceDao {
    @Query("SELECT * FROM sources ORDER BY id")
    fun observeAll(): Flow<List<Source>>

    @Query("SELECT * FROM sources WHERE enabled = 1 ORDER BY id")
    suspend fun enabled(): List<Source>

    @Query("SELECT * FROM sources WHERE id = :id")
    suspend fun byId(id: Long): Source?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(source: Source): Long

    @Update
    suspend fun update(source: Source)

    @Query("DELETE FROM sources WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sources SET lastCatalogSyncMillis = :millis WHERE id = :id")
    suspend fun markCatalogSynced(id: Long, millis: Long)

    @Query("UPDATE sources SET lastEpgSyncMillis = :millis WHERE id = :id")
    suspend fun markEpgSynced(id: Long, millis: Long)
}

@Dao
interface ChannelDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE hidden = 0
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY sortIndex, name
        """
    )
    fun observe(sourceId: Long?, categoryId: String?): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE favourite = 1 AND hidden = 0 ORDER BY sortIndex, name")
    fun observeFavourites(): Flow<List<Channel>>

    @Query(
        """
        SELECT * FROM channels
        WHERE hidden = 0 AND name LIKE '%' || :query || '%'
        ORDER BY favourite DESC, sortIndex, name
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 200): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): Channel?

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Query("UPDATE channels SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE channels SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    @Upsert
    suspend fun upsertAll(channels: List<Channel>)

    /**
     * Deletes channels the provider no longer lists.
     *
     * Compares a sync stamp rather than using `streamId NOT IN (:allCurrentIds)`. The latter
     * is the obvious implementation and it breaks on exactly the providers where it matters:
     * SQLite caps bound variables (999 on older Android, 32766 on newer) and a large playlist
     * has 40,000 channels, so the query throws and the catalogue never converges.
     *
     * Note also the deliberate absence of any "delete everything then insert" method. Wiping
     * first means a sync that fails halfway leaves the user with an empty app — which is how
     * a player ends up telling people to reinstall to get their channels back.
     */
    @Query("DELETE FROM channels WHERE sourceId = :sourceId AND lastSeenMillis < :syncStamp")
    suspend fun deleteStale(sourceId: Long, syncStamp: Long)

    @Query("DELETE FROM channels WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)

    /**
     * Preserves user state (favourites, hidden, manual order) across a catalogue refresh.
     * Upsert would otherwise overwrite them with the defaults from the freshly parsed rows.
     */
    @Transaction
    suspend fun replaceCatalogue(sourceId: Long, incoming: List<Channel>, syncStamp: Long) {
        if (incoming.isEmpty()) return

        val existing = userStateForSource(sourceId).associateBy { it.streamId }
        val merged = incoming.map { channel ->
            val previous = existing[channel.streamId]
            if (previous == null) {
                channel.copy(lastSeenMillis = syncStamp)
            } else {
                channel.copy(
                    id = previous.id,
                    favourite = previous.favourite,
                    hidden = previous.hidden,
                    sortIndex = previous.sortIndex,
                    lastSeenMillis = syncStamp,
                )
            }
        }
        // Chunked so a 40,000-channel playlist does not build one enormous statement.
        merged.chunked(UPSERT_CHUNK).forEach { upsertAll(it) }
        deleteStale(sourceId, syncStamp)
    }

    @Query("SELECT * FROM channels WHERE sourceId = :sourceId")
    suspend fun userStateForSource(sourceId: Long): List<Channel>
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortIndex, name")
    fun observe(kind: StreamKind): Flow<List<Category>>

    @Upsert
    suspend fun upsertAll(categories: List<Category>)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface ProgrammeDao {
    /**
     * The guide grid: everything overlapping the visible window.
     *
     * Filtered by time only, never by a list of channel ids. `epgChannelId IN (:ids)` is the
     * natural way to write this and it breaks on any provider large enough to matter — SQLite
     * caps bound variables at 999 on older Android, and a real catalogue has thousands of
     * channels. Callers group the result by channel in memory instead.
     */
    @Query(
        """
        SELECT * FROM programmes
        WHERE endUtcMillis > :fromUtcMillis
          AND startUtcMillis < :toUtcMillis
        ORDER BY epgChannelId, startUtcMillis
        """
    )
    fun observeWindow(fromUtcMillis: Long, toUtcMillis: Long): Flow<List<Programme>>

    /** What is on right now, for the channel list's "now playing" line. */
    @Query(
        """
        SELECT * FROM programmes
        WHERE startUtcMillis <= :nowUtcMillis AND endUtcMillis > :nowUtcMillis
        """
    )
    fun observeNow(nowUtcMillis: Long): Flow<List<Programme>>

    @Query(
        """
        SELECT * FROM programmes
        WHERE epgChannelId = :channelId AND endUtcMillis > :nowUtcMillis
        ORDER BY startUtcMillis LIMIT :limit
        """
    )
    suspend fun upcoming(channelId: String, nowUtcMillis: Long, limit: Int): List<Programme>

    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Upsert
    suspend fun upsertAll(programmes: List<Programme>)

    /** Housekeeping: drop anything that finished before the retention cut-off. */
    @Query("DELETE FROM programmes WHERE endUtcMillis < :beforeUtcMillis")
    suspend fun deleteEndedBefore(beforeUtcMillis: Long)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface MovieDao {
    @Query(
        """
        SELECT * FROM movies
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY addedMillis DESC, name
        """
    )
    fun observe(categoryId: String?): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE favourite = 1 ORDER BY name")
    fun observeFavourites(): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun search(query: String, limit: Int = 200): Flow<List<Movie>>

    @Query("SELECT * FROM movies WHERE id = :id")
    suspend fun byId(id: Long): Movie?

    @Query("UPDATE movies SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Upsert
    suspend fun upsertAll(movies: List<Movie>)

    @Query("DELETE FROM movies WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface SeriesDao {
    @Query(
        """
        SELECT * FROM series
        WHERE (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY addedMillis DESC, name
        """
    )
    fun observe(categoryId: String?): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE name LIKE '%' || :query || '%' ORDER BY name LIMIT :limit")
    fun search(query: String, limit: Int = 200): Flow<List<Series>>

    @Query("SELECT * FROM series WHERE id = :id")
    suspend fun byId(id: Long): Series?

    @Query("UPDATE series SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Upsert
    suspend fun upsertAll(items: List<Series>)

    @Query("DELETE FROM series WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE sourceId = :sourceId AND seriesId = :seriesId ORDER BY season, episodeNumber")
    fun observeForSeries(sourceId: Long, seriesId: String): Flow<List<Episode>>

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun byId(id: Long): Episode?

    @Upsert
    suspend fun upsertAll(episodes: List<Episode>)

    @Query("DELETE FROM episodes WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE mediaKey = :key")
    suspend fun get(key: String): PlaybackPosition?

    @Query("SELECT * FROM playback_positions ORDER BY updatedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<PlaybackPosition>>

    @Upsert
    suspend fun upsert(position: PlaybackPosition)

    @Query("DELETE FROM playback_positions WHERE mediaKey = :key")
    suspend fun delete(key: String)
}
