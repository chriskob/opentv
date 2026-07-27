/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * The domain models double as Room entities.
 *
 * A separate entity layer with mappers is the textbook answer, but for an app this size it
 * roughly doubles the code for no behavioural gain, and every extra layer is one more thing
 * a first-time contributor has to understand before they can fix a bug. If OpenTV ever grows
 * a second storage backend, split them then.
 */

/** How a source delivers its catalogue. */
enum class SourceKind {
    /** Xtream Codes / "player API" panel: username + password + host. */
    XTREAM,

    /** A plain M3U/M3U8 playlist URL, optionally with a separate XMLTV EPG URL. */
    M3U,
}

/**
 * A configured provider. Everything lives on-device; there is no OpenTV account
 * and no OpenTV server. See docs/ARCHITECTURE.md for why.
 */
@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: SourceKind,
    /** Base URL including scheme and port, e.g. `http://example.com:8080`. No trailing slash. */
    val url: String,
    val username: String? = null,
    val password: String? = null,
    /** Optional explicit XMLTV URL. For Xtream this is derived if left null. */
    val epgUrl: String? = null,
    /**
     * Some panels reject requests whose User-Agent they do not recognise, which is the
     * usual cause of a blanket 403 on every stream. Overridable per source.
     */
    val userAgent: String = DEFAULT_USER_AGENT,
    val enabled: Boolean = true,
    val lastCatalogSyncMillis: Long = 0,
    val lastEpgSyncMillis: Long = 0,
) {
    companion object {
        const val DEFAULT_USER_AGENT: String = "OpenTV/0.1 (Android)"
    }
}

enum class StreamKind { LIVE, MOVIE, SERIES }

@Entity(
    tableName = "categories",
    primaryKeys = ["sourceId", "id", "kind"],
)
data class Category(
    val id: String,
    val sourceId: Long,
    val name: String,
    val kind: StreamKind,
    val sortIndex: Int = 0,
)

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["sourceId", "streamId"], unique = true),
        Index(value = ["sourceId", "categoryId"]),
        Index(value = ["epgChannelId"]),
        Index(value = ["favourite"]),
    ],
)
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    /** Provider's own stream id (Xtream) or the tvg-id/url hash (M3U). Stable per source. */
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val logoUrl: String?,
    /** The id used to join against EPG data. Often differs from [streamId]. */
    val epgChannelId: String?,
    val number: Int?,
    /** Fully-resolved playback URL. */
    val streamUrl: String,
    val favourite: Boolean = false,
    val hidden: Boolean = false,
    val sortIndex: Int = 0,
    /**
     * Stamp of the last catalogue sync that saw this channel.
     *
     * Used to prune channels the provider has dropped. The obvious alternative —
     * `DELETE ... WHERE streamId NOT IN (:allCurrentIds)` — breaks on real providers, because
     * SQLite caps bound variables (999 on older Android, 32766 on newer) and a large playlist
     * has 40,000 channels. A stamp comparison has no such limit.
     */
    val lastSeenMillis: Long = 0,
)

/**
 * One programme in the guide.
 *
 * Times are epoch millis in UTC. Rendering converts to the device zone at draw time;
 * we never store local time, which is the classic cause of a guide that is silently
 * an hour or two out.
 */
@Entity(
    tableName = "programmes",
    indices = [
        Index(value = ["sourceId", "epgChannelId", "startUtcMillis"], unique = true),
        Index(value = ["endUtcMillis"]),
    ],
)
data class Programme(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val epgChannelId: String,
    val startUtcMillis: Long,
    val endUtcMillis: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val iconUrl: String? = null,
) {
    val durationMillis: Long get() = endUtcMillis - startUtcMillis

    fun isLiveAt(nowUtcMillis: Long): Boolean =
        nowUtcMillis in startUtcMillis until endUtcMillis

    fun progressAt(nowUtcMillis: Long): Float {
        if (durationMillis <= 0L) return 0f
        return ((nowUtcMillis - startUtcMillis).toFloat() / durationMillis).coerceIn(0f, 1f)
    }
}

@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["sourceId", "streamId"], unique = true),
        Index(value = ["sourceId", "categoryId"]),
    ],
)
data class Movie(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val streamId: String,
    val name: String,
    val categoryId: String?,
    val posterUrl: String?,
    val rating: Double?,
    val year: Int?,
    val plot: String?,
    val durationSeconds: Int?,
    val containerExtension: String?,
    val streamUrl: String,
    val favourite: Boolean = false,
    val addedMillis: Long = 0,
)

@Entity(
    tableName = "series",
    indices = [
        Index(value = ["sourceId", "seriesId"], unique = true),
        Index(value = ["sourceId", "categoryId"]),
    ],
)
data class Series(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val seriesId: String,
    val name: String,
    val categoryId: String?,
    val posterUrl: String?,
    val rating: Double?,
    val year: Int?,
    val plot: String?,
    val favourite: Boolean = false,
    val addedMillis: Long = 0,
)

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["sourceId", "episodeId"], unique = true),
        Index(value = ["sourceId", "seriesId"]),
    ],
)
data class Episode(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val seriesId: String,
    val episodeId: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val plot: String?,
    val durationSeconds: Int?,
    val stillUrl: String?,
    val streamUrl: String,
)

/** Resume position for a movie or episode. Live TV is never resumed. */
@Entity(tableName = "playback_positions")
data class PlaybackPosition(
    @PrimaryKey val mediaKey: String,
    val positionMillis: Long,
    val durationMillis: Long,
    val updatedAtMillis: Long,
) {
    /** Treat the last 5% as "finished" so we do not resume two seconds before the credits. */
    val isFinished: Boolean
        get() = durationMillis > 0 && positionMillis.toDouble() / durationMillis > 0.95
}
