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
import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.PlaybackPosition
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
        Programme::class,
        Movie::class,
        Series::class,
        Episode::class,
        PlaybackPosition::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class OpenTvDatabase : RoomDatabase() {
    abstract fun sources(): SourceDao
    abstract fun categories(): CategoryDao
    abstract fun channels(): ChannelDao
    abstract fun programmes(): ProgrammeDao
    abstract fun movies(): MovieDao
    abstract fun series(): SeriesDao
    abstract fun episodes(): EpisodeDao
    abstract fun positions(): PlaybackPositionDao

    companion object {
        fun build(context: Context): OpenTvDatabase =
            Room.databaseBuilder(context, OpenTvDatabase::class.java, "opentv.db")
                // WAL keeps guide writes from blocking guide reads, so a background EPG
                // refresh cannot make the UI stutter on a slow TV box.
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
    }
}
