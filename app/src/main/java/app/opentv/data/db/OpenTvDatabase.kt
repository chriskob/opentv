/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import android.content.Context
import androidx.room3.ColumnTypeConverter
import androidx.room3.ColumnTypeConverters
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Episode
import app.opentv.data.model.LiveStreamFormat
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
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind

class Converters {
    @ColumnTypeConverter fun sourceKindToString(value: SourceKind): String = value.name

    @ColumnTypeConverter fun stringToSourceKind(value: String): SourceKind =
        runCatching { SourceKind.valueOf(value) }.getOrDefault(SourceKind.M3U)

    @ColumnTypeConverter fun liveStreamFormatToString(value: LiveStreamFormat): String = value.name

    @ColumnTypeConverter fun stringToLiveStreamFormat(value: String): LiveStreamFormat =
        runCatching { LiveStreamFormat.valueOf(value) }.getOrDefault(LiveStreamFormat.HLS)

    @ColumnTypeConverter fun streamKindToString(value: StreamKind): String = value.name

    @ColumnTypeConverter fun stringToStreamKind(value: String): StreamKind =
        runCatching { StreamKind.valueOf(value) }.getOrDefault(StreamKind.LIVE)

    @ColumnTypeConverter fun recordingStatusToString(value: RecordingStatus): String = value.name

    @ColumnTypeConverter fun stringToRecordingStatus(value: String): RecordingStatus =
        runCatching { RecordingStatus.valueOf(value) }.getOrDefault(RecordingStatus.FAILED)
}

/**
 * Phase 1 helper (Room 2.8.4 + sqlite 2.6.x had no execSQL): superseded in Phase 2
 * by the official androidx.sqlite.execSQL extension (see imports). Removed.
 */

@Database(
    entities = [
        Source::class,
        Category::class,
        Channel::class,
        EpgFeed::class,
        EpgChannelAlias::class,
        Programme::class,
        Movie::class,
        Series::class,
        Episode::class,
        PlaybackPosition::class,
        Profile::class,
        Recording::class,
        SeriesRule::class,
        Reminder::class,
    ],
    version = 19,
    exportSchema = true,
)
@ColumnTypeConverters(Converters::class)
abstract class OpenTvDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun categories(): CategoryDao
    abstract fun channels(): ChannelDao
    abstract fun epgFeeds(): EpgFeedDao
    abstract fun epgAliases(): EpgChannelAliasDao
    abstract fun programmes(): ProgrammeDao
    abstract fun movies(): MovieDao
    abstract fun series(): SeriesDao
    abstract fun episodes(): EpisodeDao
    abstract fun positions(): PlaybackPositionDao
    abstract fun profiles(): ProfileDao
    abstract fun recordings(): RecordingDao
    abstract fun seriesRules(): SeriesRuleDao
    abstract fun reminders(): ReminderDao

    companion object {
        /**
         * v2 → v3: profiles. Adds the profiles table with a default profile (id 1, "Me"), and
         * re-keys resume positions by (profileId, mediaKey) — existing positions all become the
         * default profile's, so nobody loses their place. Written to match Room's own DDL so the
         * identity check passes; the destructive fallback below is only a backstop.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
                )
                connection.execSQL("INSERT INTO `profiles` (`id`, `name`, `createdAtMillis`) VALUES (1, 'Me', 0)")
                connection.execSQL(
                    "CREATE TABLE `playback_positions_new` (" +
                        "`profileId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, " +
                        "`positionMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaKey`))",
                )
                connection.execSQL(
                    "INSERT INTO `playback_positions_new` " +
                        "(`profileId`, `mediaKey`, `positionMillis`, `durationMillis`, `updatedAtMillis`) " +
                        "SELECT 1, `mediaKey`, `positionMillis`, `durationMillis`, `updatedAtMillis` " +
                        "FROM `playback_positions`",
                )
                connection.execSQL("DROP TABLE `playback_positions`")
                connection.execSQL("ALTER TABLE `playback_positions_new` RENAME TO `playback_positions`")
            }
        }

        /**
         * v3 → v4: recordings. Purely additive — two new tables (recordings, series_rules) and
         * their indices, nothing existing is touched. Written to match Room's generated DDL so
         * the schema-identity check passes and favourites/overrides survive the upgrade (rather
         * than falling through to the destructive rebuild below).
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recordings` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` INTEGER NOT NULL, " +
                        "`sourceId` INTEGER NOT NULL, " +
                        "`channelName` TEXT NOT NULL, " +
                        "`logoUrl` TEXT, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT, " +
                        "`filePath` TEXT NOT NULL, " +
                        "`streamUrl` TEXT NOT NULL, " +
                        "`userAgent` TEXT NOT NULL, " +
                        "`scheduledStartMillis` INTEGER NOT NULL, " +
                        "`scheduledEndMillis` INTEGER NOT NULL, " +
                        "`startedAtMillis` INTEGER NOT NULL, " +
                        "`endedAtMillis` INTEGER NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`sizeBytes` INTEGER NOT NULL, " +
                        "`seriesRuleId` INTEGER, " +
                        "`error` TEXT)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_status` ON `recordings` (`status`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_recordings_channelId` ON `recordings` (`channelId`)")
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `series_rules` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` INTEGER NOT NULL, " +
                        "`channelName` TEXT NOT NULL, " +
                        "`titleKey` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "`enabled` INTEGER NOT NULL)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_series_rules_channelId` ON `series_rules` (`channelId`)")
            }
        }

        /**
         * v4 → v5: catch-up. Adds the archive columns to channels. Additive; the DEFAULT 0 matches
         * the entity's @ColumnInfo(defaultValue = "0") so the schema-identity check passes.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `tvArchive` INTEGER NOT NULL DEFAULT 0")
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `tvArchiveDays` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v5 → v6: programme reminders. Purely additive — one new table (reminders) and its two
         * indices, matching Room's generated DDL so favourites, recordings and overrides survive
         * the upgrade rather than falling through to the destructive rebuild.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE TABLE IF NOT EXISTS `reminders` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`channelId` INTEGER NOT NULL, " +
                        "`channelName` TEXT NOT NULL, " +
                        "`logoUrl` TEXT, " +
                        "`title` TEXT NOT NULL, " +
                        "`startUtcMillis` INTEGER NOT NULL, " +
                        "`endUtcMillis` INTEGER NOT NULL, " +
                        "`autoTune` INTEGER NOT NULL, " +
                        "`createdAtMillis` INTEGER NOT NULL, " +
                        "`fired` INTEGER NOT NULL)",
                )
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_startUtcMillis` ON `reminders` (`startUtcMillis`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_channelId` ON `reminders` (`channelId`)")
            }
        }

        /**
         * v6 → v7: per-source live-stream format. Adds the `liveFormat` column to sources. Additive;
         * the DEFAULT 'HLS' back-fills existing rows to the historical behaviour so nobody's channels
         * change container until they opt in. The entity carries no @ColumnInfo(defaultValue) — like
         * the other enum-as-String columns (`kind`) — so the DB-only default is not part of the
         * schema-identity check and the upgrade preserves favourites and overrides.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `liveFormat` TEXT NOT NULL DEFAULT 'HLS'")
            }
        }

        /**
         * v7 → v8: per-channel custom name. Adds the nullable `customName` column to channels — the
         * user's manual rename, carried across re-syncs by [ChannelDao.replaceCatalogue]. Additive
         * and nullable (no NOT NULL, no default) to match the nullable Kotlin `String?`, so the
         * schema-identity check passes and favourites/overrides survive the upgrade.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `customName` TEXT")
            }
        }

        /**
         * v8 → v9: richer VOD metadata for the Netflix-style Movies/Series redesign. Adds the
         * enrichment columns (backdrop, cast, genre, tmdbId, and — movies only — director) pulled
         * from the provider's own Xtream `get_vod_info`/`get_series_info`. Every column is nullable
         * TEXT with no default, matching the nullable Kotlin `String?` fields, so the schema-identity
         * check passes and favourites/overrides survive the upgrade rather than falling through to
         * the destructive rebuild.
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `movies` ADD COLUMN `backdropUrl` TEXT")
                connection.execSQL("ALTER TABLE `movies` ADD COLUMN `cast` TEXT")
                connection.execSQL("ALTER TABLE `movies` ADD COLUMN `genre` TEXT")
                connection.execSQL("ALTER TABLE `movies` ADD COLUMN `tmdbId` TEXT")
                connection.execSQL("ALTER TABLE `movies` ADD COLUMN `director` TEXT")
                connection.execSQL("ALTER TABLE `series` ADD COLUMN `backdropUrl` TEXT")
                connection.execSQL("ALTER TABLE `series` ADD COLUMN `cast` TEXT")
                connection.execSQL("ALTER TABLE `series` ADD COLUMN `genre` TEXT")
                connection.execSQL("ALTER TABLE `series` ADD COLUMN `tmdbId` TEXT")
            }
        }

        /** Recordings gain a profile owner so a booking can be tagged to whoever set it. Nullable
         *  (no default) to match Room's generated DDL for a `Long?` field — the project's pattern. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `recordings` ADD COLUMN `profileId` INTEGER")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `macAddress` TEXT")
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `cmd` TEXT")
            }
        }

        /**
         * v11 → v12: Composite range indexes on programmes table for instant TV guide query.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_endUtcMillis_startUtcMillis` ON `programmes` (`endUtcMillis`, `startUtcMillis`)")
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_epgChannelId_endUtcMillis_startUtcMillis` ON `programmes` (`epgChannelId`, `endUtcMillis`, `startUtcMillis`)")
            }
        }

        /**
         * v12 → v13: Standalone index on channels.sourceId for queries that filter by source
         * without categoryId — avoids full table scans on the composite (sourceId, streamId)
         * index.
         */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("CREATE INDEX IF NOT EXISTS `index_channels_sourceId` ON `channels` (`sourceId`)")
            }
        }

        /**
         * v13 → v14: Add `isNew` boolean flag to programmes table for new episodes/premieres.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `programmes` ADD COLUMN `isNew` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v14 → v15: standalone index on channels.categoryId. The guide's category switch
         * filters on categoryId without sourceId, which the (sourceId, categoryId) composite
         * cannot serve — the query was full-scanning channels on every rail switch. Matches
         * Room's own DDL (index_channels_categoryId) so the identity check passes.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_channels_categoryId` ON `channels` (`categoryId`)"
                )
            }
        }

        /**
         * v15 → v16: per-playlist content intent (the portal's Channels/Movies/Shows boxes).
         * Previously those choices were applied once at add time and never remembered, so
         * unchecking Channels still re-listed that playlist's channels on the next refresh.
         * DEFAULT 1 matches the old behaviour for playlists added before these columns existed.
         */
        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `includeLive` INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `includeVod` INTEGER NOT NULL DEFAULT 1")
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `includeSeries` INTEGER NOT NULL DEFAULT 1")
            }
        }

        /**
         * v16 → v17: user-controlled playlist order. Adds `sortIndex` to sources, DEFAULT 0, which
         * back-fills existing playlists to their current id order — nothing moves until the viewer
         * reorders. Additive, so favourites and overrides survive the upgrade.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `sources` ADD COLUMN `sortIndex` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v17 → v18: Add `isLive` boolean flag to programmes, from XMLTV `<live/>`. Additive with
         * DEFAULT 0, so rows stored before this version simply carry no LIVE badge until the next
         * EPG sync rewrites them with the flag.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `programmes` ADD COLUMN `isLive` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * v18 → v19: catch-up mode + correction. Adds the verbatim M3U `catchup` mode and the
         * per-channel `catchup-correction` minutes to channels. Additive with defaults matching
         * the entity's @ColumnInfo so the schema-identity check passes.
         */
        private val MIGRATION_18_19 = object : Migration(18, 19) {
            override suspend fun migrate(connection: SQLiteConnection) {
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupMode` TEXT NOT NULL DEFAULT ''")
                connection.execSQL("ALTER TABLE `channels` ADD COLUMN `catchupCorrectionMin` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun build(context: Context): OpenTvDatabase =
            Room.databaseBuilder(context, OpenTvDatabase::class.java, "opentv.db")
                // Room 3 requires an explicit driver. BundledSQLiteDriver ships the
                // newest SQLite everywhere, so cheap sticks behave like flagships.
                .setDriver(BundledSQLiteDriver())
                // WAL keeps guide writes from blocking guide reads, so a background EPG
                // refresh cannot make the UI stutter on a slow TV box.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16,
                    MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19,
                )
                /*
                 * Pre-1.0 policy: schema changes drop and rebuild the database. Everything
                 * in it is re-derivable from the provider (one sync away) except favourites
                 * and overrides, which is a real but small loss for testers. The policy
                 * flips to real migrations at the first tagged release — from then on,
                 * every schema change ships a Migration and this line is deleted.
                 */
                .fallbackToDestructiveMigration()
                .addCallback(object : RoomDatabase.Callback() {
                    override suspend fun onOpen(connection: SQLiteConnection) {
                        super.onOpen(connection)
                        runCatching {
                            connection.execSQL("PRAGMA wal_autocheckpoint=1000;")
                            connection.execSQL("PRAGMA wal_checkpoint(TRUNCATE);")
                        }
                    }
                })
                .build()
    }
}
