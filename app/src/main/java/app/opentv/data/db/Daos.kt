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
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Profile
import app.opentv.data.model.Programme
import app.opentv.data.model.Recording
import app.opentv.data.model.RecordingStatus
import app.opentv.data.model.Reminder
import app.opentv.data.model.Series
import app.opentv.data.model.SeriesRule
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
}

@Dao
interface ChannelDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE hidden = 0
          AND (:sourceId IS NULL OR sourceId = :sourceId)
          AND (:categoryId IS NULL OR categoryId = :categoryId)
        ORDER BY sortIndex, displayName
        """
    )
    fun observe(sourceId: Long?, categoryId: String?): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE favourite = 1 AND hidden = 0 ORDER BY sortIndex, displayName")
    fun observeFavourites(): Flow<List<Channel>>

    /**
     * Channels across a SET of provider categories. Needed because one logical category
     * ("General") is often shipped as several codec-split ones ("UK| GENERAL HD/RAW",
     * "UK| GENERAL hevc") — the UI merges them into a single rail entry. The list is a
     * handful of ids, nowhere near SQLite's bound-variable cap.
     */
    @Query(
        """
        SELECT * FROM channels
        WHERE hidden = 0 AND categoryId IN (:categoryIds)
        ORDER BY sortIndex, displayName
        """
    )
    fun observeInCategories(categoryIds: List<String>): Flow<List<Channel>>

    @Query(
        """
        SELECT * FROM channels
        WHERE hidden = 0
          AND (displayName LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')
        ORDER BY favourite DESC, sortIndex, displayName
        LIMIT :limit
        """
    )
    fun search(query: String, limit: Int = 200): Flow<List<Channel>>

    /** Like [search] but keeps hidden channels in — the channel manager needs them to unhide. */
    @Query(
        """
        SELECT * FROM channels
        WHERE (displayName LIKE '%' || :query || '%' OR name LIKE '%' || :query || '%')
        ORDER BY hidden, favourite DESC, sortIndex, displayName
        LIMIT :limit
        """
    )
    fun searchIncludingHidden(query: String, limit: Int = 200): Flow<List<Channel>>

    @Query("SELECT * FROM channels WHERE id = :id")
    suspend fun byId(id: Long): Channel?

    /** Every quality variant of one logical channel, best first. */
    @Query(
        """
        SELECT * FROM channels
        WHERE groupKey = :groupKey AND groupKey != '' AND hidden = 0
        ORDER BY qualityRank DESC, displayName
        """
    )
    suspend fun variantsInGroup(groupKey: String): List<Channel>

    @Query("SELECT COUNT(*) FROM channels WHERE sourceId = :sourceId")
    suspend fun countForSource(sourceId: Long): Int

    @Query("UPDATE channels SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("UPDATE channels SET hidden = :hidden WHERE id = :id")
    suspend fun setHidden(id: Long, hidden: Boolean)

    // --- Sync: identify favourites/hidden by stream URL (stable across devices) ---
    @Query("SELECT streamUrl FROM channels WHERE favourite = 1")
    suspend fun favouriteUrls(): List<String>

    @Query("SELECT streamUrl FROM channels WHERE hidden = 1")
    suspend fun hiddenUrls(): List<String>

    @Query("UPDATE channels SET favourite = 1 WHERE streamUrl = :url")
    suspend fun markFavouriteByUrl(url: String)

    @Query("UPDATE channels SET hidden = 1 WHERE streamUrl = :url")
    suspend fun markHiddenByUrl(url: String)

    @Query("UPDATE channels SET matchedEpgId = :epgId WHERE id = :id")
    suspend fun setMatchedEpgId(id: Long, epgId: String?)

    @Query("UPDATE channels SET epgOverrideId = :epgId WHERE id = :id")
    suspend fun setEpgOverride(id: Long, epgId: String?)

    /** The matcher's working set: id, group key, and what is already known. */
    @Query("SELECT * FROM channels")
    suspend fun allForMatching(): List<Channel>

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
     * Preserves user state (favourites, hidden, manual order, EPG overrides) across a
     * catalogue refresh. Upsert would otherwise overwrite them with the defaults from the
     * freshly parsed rows.
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
                    // OR, not overwrite: the importer hides separator rows ('### UK ###')
                    // at parse time, and that decision must stick even for rows that
                    // predate the rule. User hides are preserved the same way.
                    hidden = previous.hidden || channel.hidden,
                    sortIndex = previous.sortIndex,
                    epgOverrideId = previous.epgOverrideId,
                    matchedEpgId = previous.matchedEpgId,
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

    /** Minimal id+name rows for the re-normalise pass. */
    @Query("SELECT id, name FROM categories WHERE id IN (:ids)")
    suspend fun namesFor(ids: Set<String>): List<CategoryName>

    @Upsert
    suspend fun upsertAll(categories: List<Category>)

    @Query("DELETE FROM categories WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

/** Projection for [CategoryDao.namesFor]. */
data class CategoryName(val id: String, val name: String)

@Dao
interface EpgFeedDao {
    @Query("SELECT * FROM epg_feeds ORDER BY builtIn DESC, id")
    fun observeAll(): Flow<List<EpgFeed>>

    @Query("SELECT * FROM epg_feeds")
    suspend fun all(): List<EpgFeed>

    @Query("SELECT * FROM epg_feeds WHERE enabled = 1")
    suspend fun enabled(): List<EpgFeed>

    @Query("SELECT * FROM epg_feeds WHERE providerSourceId = :sourceId")
    suspend fun forProvider(sourceId: Long): EpgFeed?

    @Query("SELECT * FROM epg_feeds WHERE url = :url")
    suspend fun byUrl(url: String): EpgFeed?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(feed: EpgFeed): Long

    @Update
    suspend fun update(feed: EpgFeed)

    @Query("UPDATE epg_feeds SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE epg_feeds SET lastSyncMillis = :millis, lastResult = :result WHERE id = :id")
    suspend fun markSynced(id: Long, millis: Long, result: String)

    @Query("DELETE FROM epg_feeds WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EpgChannelAliasDao {
    @Query("SELECT * FROM epg_channels")
    suspend fun all(): List<EpgChannelAlias>

    @Query("SELECT COUNT(*) FROM epg_channels")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(aliases: List<EpgChannelAlias>)

    @Query("DELETE FROM epg_channels WHERE feedId = :feedId")
    suspend fun deleteForFeed(feedId: Long)
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

    @Query("SELECT COUNT(*) FROM programmes WHERE feedId = :feedId")
    suspend fun countForFeed(feedId: Long): Int

    /** Distinct guide channels that actually have programmes — the match report's baseline. */
    @Query("SELECT DISTINCT epgChannelId FROM programmes")
    suspend fun channelIdsWithProgrammes(): List<String>

    @Upsert
    suspend fun upsertAll(programmes: List<Programme>)

    /** Housekeeping: drop anything that finished before the retention cut-off. */
    @Query("DELETE FROM programmes WHERE endUtcMillis < :beforeUtcMillis")
    suspend fun deleteEndedBefore(beforeUtcMillis: Long)

    @Query("DELETE FROM programmes WHERE feedId = :feedId")
    suspend fun deleteForFeed(feedId: Long)
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

    /** Stream URL is the cross-device key: the same film has the same URL on the same provider. */
    @Query("SELECT * FROM movies WHERE streamUrl = :url LIMIT 1")
    suspend fun byStreamUrl(url: String): Movie?

    @Query("UPDATE movies SET favourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    // --- Sync: favourites by stream URL ---
    @Query("SELECT streamUrl FROM movies WHERE favourite = 1")
    suspend fun favouriteUrls(): List<String>

    @Query("UPDATE movies SET favourite = 1 WHERE streamUrl = :url")
    suspend fun markFavouriteByUrl(url: String)

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

    @Query("SELECT * FROM episodes WHERE streamUrl = :url LIMIT 1")
    suspend fun byStreamUrl(url: String): Episode?

    @Upsert
    suspend fun upsertAll(episodes: List<Episode>)

    @Query("DELETE FROM episodes WHERE sourceId = :sourceId")
    suspend fun deleteForSource(sourceId: Long)
}

@Dao
interface PlaybackPositionDao {
    @Query("SELECT * FROM playback_positions WHERE profileId = :profileId AND mediaKey = :key")
    suspend fun get(profileId: Long, key: String): PlaybackPosition?

    @Query(
        "SELECT * FROM playback_positions WHERE profileId = :profileId " +
            "ORDER BY updatedAtMillis DESC LIMIT :limit",
    )
    fun observeRecent(profileId: Long, limit: Int = 30): Flow<List<PlaybackPosition>>

    @Query("SELECT * FROM playback_positions")
    suspend fun all(): List<PlaybackPosition>

    @Upsert
    suspend fun upsert(position: PlaybackPosition)

    @Query("DELETE FROM playback_positions WHERE profileId = :profileId AND mediaKey = :key")
    suspend fun delete(profileId: Long, key: String)
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profiles ORDER BY id")
    fun observeAll(): Flow<List<Profile>>

    @Query("SELECT * FROM profiles ORDER BY id")
    suspend fun all(): List<Profile>

    @Query("SELECT * FROM profiles WHERE name = :name LIMIT 1")
    suspend fun byName(name: String): Profile?

    @Insert
    suspend fun insert(profile: Profile): Long

    @Query("UPDATE profiles SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM profiles WHERE id = :id")
    suspend fun delete(id: Long)

    /** Removing a profile takes its watch history with it. */
    @Query("DELETE FROM playback_positions WHERE profileId = :id")
    suspend fun deletePositions(id: Long)
}

@Dao
interface RecordingDao {
    /** The library, newest first — scheduled and in-progress float to the top by recency. */
    @Query("SELECT * FROM recordings ORDER BY COALESCE(NULLIF(startedAtMillis, 0), scheduledStartMillis) DESC, id DESC")
    fun observeAll(): Flow<List<Recording>>

    /** Just the ones capturing right now — the player watches this to light its Record button. */
    @Query("SELECT * FROM recordings WHERE status = 'RECORDING'")
    fun observeActive(): Flow<List<Recording>>

    @Query("SELECT * FROM recordings WHERE status = 'RECORDING'")
    suspend fun active(): List<Recording>

    @Query("SELECT * FROM recordings WHERE status = 'SCHEDULED' ORDER BY scheduledStartMillis")
    suspend fun scheduled(): List<Recording>

    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun byId(id: Long): Recording?

    /** For series-link de-dup: has this rule already booked this programme window on this channel? */
    @Query(
        """
        SELECT COUNT(*) FROM recordings
        WHERE seriesRuleId = :ruleId AND channelId = :channelId AND scheduledStartMillis = :startMillis
        """
    )
    suspend fun countForRuleAt(ruleId: Long, channelId: Long, startMillis: Long): Int

    @Insert
    suspend fun insert(recording: Recording): Long

    @Update
    suspend fun update(recording: Recording)

    @Query("UPDATE recordings SET status = :status, error = :error WHERE id = :id")
    suspend fun setStatus(id: Long, status: RecordingStatus, error: String? = null)

    @Query("UPDATE recordings SET sizeBytes = :bytes WHERE id = :id")
    suspend fun setSize(id: Long, bytes: Long)

    @Query(
        "UPDATE recordings SET status = :status, startedAtMillis = :startedAt WHERE id = :id"
    )
    suspend fun markStarted(id: Long, status: RecordingStatus, startedAt: Long)

    @Query(
        "UPDATE recordings SET status = :status, endedAtMillis = :endedAt, sizeBytes = :bytes, error = :error WHERE id = :id"
    )
    suspend fun markFinished(id: Long, status: RecordingStatus, endedAt: Long, bytes: Long, error: String?)

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)

    /**
     * On a cold start, any row still marked RECORDING is a leftover from a process that was
     * killed mid-capture — reconcile it to FAILED so the UI never shows a phantom "recording".
     */
    @Query("UPDATE recordings SET status = 'FAILED', error = 'Interrupted' WHERE status = 'RECORDING'")
    suspend fun failInterrupted()
}

@Dao
interface SeriesRuleDao {
    @Query("SELECT * FROM series_rules ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<SeriesRule>>

    @Query("SELECT * FROM series_rules WHERE enabled = 1")
    suspend fun enabled(): List<SeriesRule>

    @Query("SELECT * FROM series_rules WHERE channelId = :channelId AND titleKey = :titleKey LIMIT 1")
    suspend fun forChannelTitle(channelId: Long, titleKey: String): SeriesRule?

    @Insert
    suspend fun insert(rule: SeriesRule): Long

    @Query("DELETE FROM series_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface ReminderDao {
    /** All reminders, soonest first — the reminders list and boot re-arm both read this. */
    @Query("SELECT * FROM reminders ORDER BY startUtcMillis")
    fun observeAll(): Flow<List<Reminder>>

    /** Still-future, not-yet-fired reminders — the set to re-arm after a reboot. */
    @Query("SELECT * FROM reminders WHERE fired = 0 AND startUtcMillis > :nowMillis ORDER BY startUtcMillis")
    suspend fun upcoming(nowMillis: Long): List<Reminder>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun byId(id: Long): Reminder?

    /** De-dup: is there already a reminder for this (channel, slot)? */
    @Query("SELECT * FROM reminders WHERE channelId = :channelId AND startUtcMillis = :startMillis LIMIT 1")
    suspend fun forProgramme(channelId: Long, startMillis: Long): Reminder?

    /** The guide grid asks: which upcoming slots already have a bell? Keyed by start time. */
    @Query("SELECT startUtcMillis FROM reminders WHERE channelId = :channelId AND fired = 0")
    fun observeStartsForChannel(channelId: Long): Flow<List<Long>>

    @Insert
    suspend fun insert(reminder: Reminder): Long

    @Query("UPDATE reminders SET fired = 1 WHERE id = :id")
    suspend fun markFired(id: Long)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM reminders WHERE channelId = :channelId AND startUtcMillis = :startMillis")
    suspend fun deleteForProgramme(channelId: Long, startMillis: Long)

    /** Housekeeping: drop reminders whose programme has already ended. */
    @Query("DELETE FROM reminders WHERE endUtcMillis < :beforeMillis")
    suspend fun deleteEndedBefore(beforeMillis: Long)
}
