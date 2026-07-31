/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Profile
import app.opentv.data.model.Programme
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.SourceKind
import app.opentv.data.model.StreamKind

class Converters {
    @TypeConverter fun sourceKindToString(value: SourceKind): String = value.name

    @TypeConverter fun stringToSourceKind(value: String): SourceKind =
        runCatching { SourceKind.valueOf(value) }.getOrDefault(SourceKind.M3U)

    @TypeConverter fun streamKindToString(value: StreamKind): String = value.name

    @TypeConverter fun stringToStreamKind(value: String): StreamKind =
        runCatching { StreamKind.valueOf(value) }.getOrDefault(StreamKind.LIVE)
}

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
    ],
    version = 3,
    exportSchema = true,
)
@TypeConverters(Converters::class)
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

    companion object {
        /**
         * v2 → v3: profiles. Adds the profiles table with a default profile (id 1, "Me"), and
         * re-keys resume positions by (profileId, mediaKey) — existing positions all become the
         * default profile's, so nobody loses their place. Written to match Room's own DDL so the
         * identity check passes; the destructive fallback below is only a backstop.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `profiles` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, `createdAtMillis` INTEGER NOT NULL)",
                )
                db.execSQL("INSERT INTO `profiles` (`id`, `name`, `createdAtMillis`) VALUES (1, 'Me', 0)")
                db.execSQL(
                    "CREATE TABLE `playback_positions_new` (" +
                        "`profileId` INTEGER NOT NULL, `mediaKey` TEXT NOT NULL, " +
                        "`positionMillis` INTEGER NOT NULL, `durationMillis` INTEGER NOT NULL, " +
                        "`updatedAtMillis` INTEGER NOT NULL, PRIMARY KEY(`profileId`, `mediaKey`))",
                )
                db.execSQL(
                    "INSERT INTO `playback_positions_new` " +
                        "(`profileId`, `mediaKey`, `positionMillis`, `durationMillis`, `updatedAtMillis`) " +
                        "SELECT 1, `mediaKey`, `positionMillis`, `durationMillis`, `updatedAtMillis` " +
                        "FROM `playback_positions`",
                )
                db.execSQL("DROP TABLE `playback_positions`")
                db.execSQL("ALTER TABLE `playback_positions_new` RENAME TO `playback_positions`")
            }
        }

        fun build(context: Context): OpenTvDatabase =
            Room.databaseBuilder(context, OpenTvDatabase::class.java, "opentv.db")
                // WAL keeps guide writes from blocking guide reads, so a background EPG
                // refresh cannot make the UI stutter on a slow TV box.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .addMigrations(MIGRATION_2_3)
                /*
                 * Pre-1.0 policy: schema changes drop and rebuild the database. Everything
                 * in it is re-derivable from the provider (one sync away) except favourites
                 * and overrides, which is a real but small loss for testers. The policy
                 * flips to real migrations at the first tagged release — from then on,
                 * every schema change ships a Migration and this line is deleted.
                 */
                .fallbackToDestructiveMigration()
                .build()
    }
}
