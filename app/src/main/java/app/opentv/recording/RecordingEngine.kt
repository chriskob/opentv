/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.recording

import android.content.Context
import app.opentv.core.AppSettings
import app.opentv.data.model.Channel
import app.opentv.data.model.Programme
import app.opentv.data.model.Recording
import app.opentv.data.model.RecordingStatus
import app.opentv.data.model.SeriesRule
import app.opentv.data.model.Source
import app.opentv.data.repo.RecordingRepository
import app.opentv.data.repo.SourceRepository

/**
 * Turns "record this" into a running capture: it creates the [Recording] row, works out where the
 * file goes and which URL to pull, then hands off to [RecordingService] to do the actual byte
 * copy in the foreground so it survives the user leaving the player or the screen going dark.
 */
class RecordingEngine(
    private val appContext: Context,
    private val repo: RecordingRepository,
    private val sources: SourceRepository,
    private val settings: AppSettings,
) {

    /** Start recording [channel] now. [programme], when given, names and time-bounds the capture. */
    suspend fun startChannel(channel: Channel, programme: Programme? = null, ruleId: Long? = null): Long {
        val source = sources.byId(channel.sourceId)
        val ua = source?.userAgent ?: Source.DEFAULT_USER_AGENT
        val now = System.currentTimeMillis()
        val title = programme?.title?.takeIf { it.isNotBlank() } ?: channel.displayName
        val filename = RecordingStorage.fileNameFor(channel.displayName, title, now)
        val locator = RecordingStorage.plannedLocator(appContext, settings, filename)

        val recording = Recording(
            channelId = channel.id,
            sourceId = channel.sourceId,
            channelName = channel.displayName,
            logoUrl = channel.logoUrl,
            title = title,
            description = programme?.description,
            filePath = locator,
            streamUrl = recordUrlFor(channel.streamUrl),
            userAgent = ua,
            scheduledStartMillis = programme?.startUtcMillis ?: 0,
            scheduledEndMillis = programme?.endUtcMillis ?: 0,
            startedAtMillis = now,
            status = RecordingStatus.RECORDING,
            seriesRuleId = ruleId,
        )
        val id = repo.insert(recording)
        RecordingService.start(appContext, id)
        return id
    }

    /** Stop an in-progress recording. */
    fun stop(recordingId: Long) = RecordingService.stop(appContext, recordingId)

    /**
     * Record a specific programme: if it's already on, start capturing now (bounded to its end);
     * if it's in the future, book an exact alarm to start it at broadcast time.
     */
    suspend fun recordProgramme(channel: Channel, programme: Programme, ruleId: Long? = null): Long {
        val now = System.currentTimeMillis()
        return if (programme.startUtcMillis > now + LEAD_MILLIS) {
            scheduleProgramme(channel, programme, ruleId)
        } else {
            startChannel(channel, programme, ruleId)
        }
    }

    /** Book a future programme. Creates a SCHEDULED row and arms the alarm. */
    suspend fun scheduleProgramme(channel: Channel, programme: Programme, ruleId: Long? = null): Long {
        // De-dup: a series rule that re-scans mustn't book the same airing twice.
        if (ruleId != null && repo.alreadyBooked(ruleId, channel.id, programme.startUtcMillis)) return -1L

        val source = sources.byId(channel.sourceId)
        val ua = source?.userAgent ?: Source.DEFAULT_USER_AGENT
        val filename = RecordingStorage.fileNameFor(channel.displayName, programme.title, programme.startUtcMillis)
        val locator = RecordingStorage.plannedLocator(appContext, settings, filename)

        val recording = Recording(
            channelId = channel.id,
            sourceId = channel.sourceId,
            channelName = channel.displayName,
            logoUrl = channel.logoUrl,
            title = programme.title,
            description = programme.description,
            filePath = locator,
            streamUrl = recordUrlFor(channel.streamUrl),
            userAgent = ua,
            scheduledStartMillis = programme.startUtcMillis,
            scheduledEndMillis = programme.endUtcMillis,
            status = RecordingStatus.SCHEDULED,
            seriesRuleId = ruleId,
        )
        val id = repo.insert(recording)
        RecordingScheduler.set(appContext, id, programme.startUtcMillis)
        return id
    }

    /** Cancel a scheduled recording: disarm the alarm and drop the row. */
    suspend fun cancelScheduled(recordingId: Long) {
        RecordingScheduler.cancel(appContext, recordingId)
        repo.delete(recordingId)
    }

    /**
     * Create (or reuse) a series-link rule for this programme's title on this channel, and book
     * every matching future airing already in the guide. Airings beyond the loaded window get
     * picked up by [scheduleMatching] on the next guide refresh.
     */
    suspend fun recordSeries(channel: Channel, programme: Programme, windowProgrammes: List<Programme>): Long {
        val key = titleKeyOf(programme.title)
        val existing = repo.ruleForChannelTitle(channel.id, key)
        val ruleId = existing?.id ?: repo.addRule(
            SeriesRule(
                channelId = channel.id,
                channelName = channel.displayName,
                titleKey = key,
                title = programme.title,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        scheduleMatching(channel, key, ruleId, windowProgrammes)
        return ruleId
    }

    /** Book every not-yet-booked airing in [programmes] whose title matches the rule. */
    suspend fun scheduleMatching(channel: Channel, titleKey: String, ruleId: Long, programmes: List<Programme>) {
        val now = System.currentTimeMillis()
        programmes
            .filter { it.endUtcMillis > now && titleKeyOf(it.title) == titleKey }
            .forEach { recordProgramme(channel, it, ruleId) }
    }

    /** Loose title match key — case- and punctuation-insensitive, so "Countryfile" == "countryfile". */
    fun titleKeyOf(title: String): String = title.lowercase().replace(Regex("[^a-z0-9]"), "")

    /**
     * Live Xtream URLs are cached as `.m3u8` (HLS) for playback, but the raw `.ts` MPEG-TS variant
     * is a single continuous body that captures to a directly-playable file with no segment
     * stitching. Other URL shapes (plain M3U) are recorded verbatim.
     */
    private fun recordUrlFor(streamUrl: String): String =
        if (streamUrl.endsWith(".m3u8")) streamUrl.removeSuffix(".m3u8") + ".ts" else streamUrl

    private companion object {
        /** A programme starting within this window counts as "on now" and records immediately. */
        const val LEAD_MILLIS = 20_000L
    }
}
