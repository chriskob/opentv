/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.data.db.ProgrammeDao
import app.opentv.data.db.SourceDao
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.parser.XmltvParser
import app.opentv.data.remote.XtreamApi
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Owns the electronic programme guide.
 *
 * ## The failure this class exists to prevent
 *
 * The standard way to refresh an EPG is: delete everything for the source, download the new
 * XMLTV, insert it. It is simple and it is why so many IPTV players lose their guide. If the
 * download stalls, the provider rate-limits, the box sleeps mid-sync, or the XML is truncated,
 * the user is left with an empty guide and no way back except a reinstall.
 *
 * OpenTV never deletes before it has the replacement. Instead:
 *
 * - Programmes are **upserted in batches** as they stream out of the parser. The unique index
 *   on `(sourceId, epgChannelId, startUtcMillis)` makes a re-run idempotent, so a sync that
 *   dies at 60% simply leaves 60% of a fresher guide behind — strictly better than before.
 * - Old programmes are pruned **by age**, not by "everything for this source", and only
 *   *after* a sync reports success.
 * - A failed sync leaves the previous guide completely intact and reports the reason.
 */
class EpgRepository(
    private val programmeDao: ProgrammeDao,
    private val sourceDao: SourceDao,
    private val api: XtreamApi,
) {

    sealed interface SyncResult {
        data class Success(val programmeCount: Int, val channelCount: Int) : SyncResult
        /** The guide on disk is untouched and still usable. */
        data class Failed(val reason: String, val cause: Throwable?) : SyncResult
    }

    /**
     * Programmes overlapping a time window, across every source.
     *
     * Not filtered by channel id at the SQL level — see [ProgrammeDao.observeWindow] for why
     * that would break on large providers. Callers group by [Programme.epgChannelId].
     */
    fun observeWindow(fromUtcMillis: Long, toUtcMillis: Long): Flow<List<Programme>> =
        programmeDao.observeWindow(fromUtcMillis, toUtcMillis)

    fun observeNow(nowUtcMillis: Long): Flow<List<Programme>> =
        programmeDao.observeNow(nowUtcMillis)

    suspend fun upcoming(epgChannelId: String, nowUtcMillis: Long, limit: Int = 12): List<Programme> =
        programmeDao.upcoming(epgChannelId, nowUtcMillis, limit)

    suspend fun sync(source: Source, nowUtcMillis: Long): SyncResult = withContext(Dispatchers.IO) {
        val before = programmeDao.countForSource(source.id)
        val batch = ArrayList<Programme>(BATCH_SIZE)
        var written = 0
        var channels = 0

        try {
            api.openEpgStream(source).use { stream ->
                val stats = XmltvParser.parse(
                    input = stream,
                    sourceId = source.id,
                    onChannelAlias = { _, _ -> channels++ },
                    onProgramme = { programme ->
                        // Ignore anything that finished before the retention cut-off; there is
                        // no point writing rows we are about to prune.
                        if (programme.endUtcMillis >= nowUtcMillis - RETENTION_PAST_MILLIS) {
                            batch += programme
                            if (batch.size >= BATCH_SIZE) {
                                flush(batch)
                                written += batch.size
                                batch.clear()
                            }
                        }
                    },
                )
                if (batch.isNotEmpty()) {
                    flush(batch)
                    written += batch.size
                    batch.clear()
                }
                channels = stats.channelCount
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "EPG sync failed for source ${source.id}", e)
            // Deliberately no cleanup here. Whatever we managed to write is newer than what
            // was there, and what was there is still there.
            return@withContext SyncResult.Failed(
                reason = e.message ?: "The guide could not be downloaded.",
                cause = e,
            )
        }

        if (written == 0 && before > 0) {
            // The download succeeded but contained nothing usable. Keep the old guide.
            return@withContext SyncResult.Failed(
                reason = "The guide downloaded but contained no programmes. Keeping the previous guide.",
                cause = null,
            )
        }

        // Only now is it safe to prune.
        programmeDao.deleteEndedBefore(nowUtcMillis - RETENTION_PAST_MILLIS)
        sourceDao.markEpgSynced(source.id, nowUtcMillis)

        Log.i(
            TAG,
            "EPG for source ${source.id}: wrote $written programmes across $channels " +
                "guide channels",
        )

        SyncResult.Success(programmeCount = written, channelCount = channels)
    }

    private suspend fun flush(batch: List<Programme>) {
        // Upsert rather than insert: the unique index means a repeated sync updates in place
        // instead of exploding on a constraint violation.
        programmeDao.upsertAll(batch)
    }

    /** True when the guide is old enough to be worth refreshing. */
    fun isStale(source: Source, nowUtcMillis: Long): Boolean =
        nowUtcMillis - source.lastEpgSyncMillis >= REFRESH_INTERVAL_MILLIS

    companion object {
        private const val TAG = "EpgRepository"

        /** Writes per transaction. Large enough to be fast, small enough not to hold WAL open. */
        const val BATCH_SIZE = 500

        /** Keep finished programmes for a day so catch-up and "what was on" still work. */
        val RETENTION_PAST_MILLIS: Long = TimeUnit.DAYS.toMillis(1)

        /** Providers publish a rolling window; refreshing more often than this is rude. */
        val REFRESH_INTERVAL_MILLIS: Long = TimeUnit.HOURS.toMillis(6)
    }
}
