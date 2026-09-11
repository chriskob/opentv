/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.repo

import android.util.Log
import app.opentv.core.AppSettings
import app.opentv.data.db.ChannelDao
import app.opentv.data.db.EpgChannelAliasDao
import app.opentv.data.db.EpgFeedDao
import app.opentv.data.db.ProgrammeDao
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.db.SourceDao
import app.opentv.data.model.EpgChannelAlias
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Programme
import app.opentv.data.model.Source
import app.opentv.data.parser.ChannelNameNormalizer
import app.opentv.data.parser.XmltvParser
import app.opentv.data.remote.XtreamApi
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Owns the electronic programme guide, end to end: feeds in, matched channels out.
 *
 * ## The feed model
 *
 * Guide data can come from three places at once — the provider's own XMLTV (usually thin or
 * empty), the curated free sources shipped with the app, and URLs the user adds. Each is an
 * [EpgFeed] row; every enabled feed is downloaded and merged into one guide. After a sync,
 * [EpgMatcher] joins provider channels to guide channels by normalised name, which is what
 * makes a free national guide light up channels named `UK| BBC ONE FHD`.
 *
 * ## The failure this class exists to prevent
 *
 * The standard way to refresh an EPG is: delete everything, download the new XMLTV, insert
 * it. It is simple and it is why so many IPTV players lose their guide — one stalled
 * download and the user is left with nothing, plus advice to reinstall.
 *
 * OpenTV never deletes before it has the replacement:
 *
 * - Programmes are **upserted in batches** as they stream out of the parser. The unique index
 *   on `(feedId, epgChannelId, startUtcMillis)` makes a re-run idempotent, so a sync that
 *   dies at 60% leaves 60% of a fresher guide behind — strictly better than before.
 * - Old programmes are pruned **by age**, only after at least one feed succeeds.
 * - A failed feed leaves that feed's previous data intact and records the reason on the
 *   feed row, where the settings screen shows it — no silent failure.
 */
class EpgRepository(
    private val programmeDao: ProgrammeDao,
    private val feedDao: EpgFeedDao,
    private val aliasDao: EpgChannelAliasDao,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val api: XtreamApi,
    private val http: OkHttpClient,
    private val settings: AppSettings? = null,
    private val db: OpenTvDatabase? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // NOTE: an eager `windowCache` used to be filled here on cold start — and again after every
    // sync — with EVERY channel's programmes for a 6-hour window. Nothing ever read it
    // (getCachedWindow/observeWindow had no callers), yet on a 25k-channel playlist it retained
    // hundreds of thousands of Programme objects and was a primary cause of the OutOfMemoryError
    // crashes on the ONN box (384MB heap). The guide's real cache is the bounded LRU
    // [railWindowCache], which is what the queries below actually use and evict.

    data class SyncSummary(
        val feedsSucceeded: Int,
        val feedsFailed: Int,
        val programmesWritten: Int,
        val channelsMatched: Int,
        val channelsTotal: Int,
    )

    // ---- Reads -----------------------------------------------------------------------------

    fun observeFeeds(): Flow<List<EpgFeed>> = feedDao.observeAll()

    fun observeNow(nowUtcMillis: Long): Flow<List<Programme>> =
        programmeDao.observeNow(nowUtcMillis)

    suspend fun nowForChannels(channelIds: Collection<String>, nowUtcMillis: Long): List<Programme> =
        if (channelIds.isEmpty()) emptyList() else {
            channelIds.chunked(500).flatMap { chunk ->
                programmeDao.nowForChannels(chunk, nowUtcMillis)
            }
        }

    suspend fun windowForChannels(
        channelIds: Collection<String>,
        fromUtcMillis: Long,
        toUtcMillis: Long,
    ): Map<String, List<Programme>> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (channelIds.isEmpty()) return@withContext emptyMap()
        val list = channelIds.chunked(500).flatMap { chunk ->
            programmeDao.windowForChannels(chunk, fromUtcMillis, toUtcMillis)
        }
        list.groupBy { it.epgChannelId }
    }

    // Incremental in-memory cache of the guide window's programmes, keyed by EPG channel id.
    // Category switches reuse cached channels and fetch only the new ones, so rail browsing
    // doesn't re-hydrate the full window each time. Cleared when the window moves (half-hourly)
    // and after each guide sync. Bounded: oldest channels evicted past [WINDOW_CACHE_CAP].
    private val windowCacheMutex = Mutex()
    private var railCacheStart: Long = 0L
    private var railCacheEnd: Long = 0L
    // Access-ordered so the eviction loop in [windowForChannelsCached] is true LRU: the
    // category the user is currently bouncing between stays cached even when a big
    // category loaded in between would otherwise push it out by insertion age.
    private val railWindowCache = LinkedHashMap<String, List<Programme>>(0, 0.75f, true)
    // 2500 was too generous for a 384MB heap: 2500 channels × ~60 programmes ≈ 150k retained
    // Programme objects on top of the rows list. 800 was then too *small*: a guide fill of a
    // 254-row category asks for ~1,000 ids, so a fill evicted its own earlier chunks. 1500 clears
    // one full fill with headroom while staying far below the growth that caused the OOM — and
    // the guide's own row cache now covers the repeat case, so this one no longer has to.
    private val railCacheCap = 1500

    /**
     * Bumped only when a sync delivers fresh programme data. Consumers that cache guide rows watch
     * this so their copy cannot go stale.
     *
     * Deliberately NOT bumped when [railWindowCache] is simply re-scoped to a different window
     * (the guide's quick 8h pass and its 48h fill use different bounds on every single run, so a
     * bounds-driven counter moved twice per run — which invalidated the guide's row cache every
     * time and quietly reduced it to a no-op).
     */
    @Volatile
    var dataGeneration: Long = 0L
        private set

    suspend fun windowForChannelsCached(
        epgIds: List<String>,
        fromUtcMillis: Long,
        toUtcMillis: Long,
    ): Map<String, List<Programme>> = windowCacheMutex.withLock {
        if (railCacheStart != fromUtcMillis || railCacheEnd != toUtcMillis) {
            railWindowCache.clear()
            railCacheStart = fromUtcMillis
            railCacheEnd = toUtcMillis
        }
        val requested = epgIds.toHashSet()
        val missing = requested.filterNot { railWindowCache.containsKey(it) }
        missing.chunked(500).forEach { chunk ->
            windowForChannels(chunk, fromUtcMillis, toUtcMillis).forEach { (id, progs) ->
                railWindowCache[id] = progs
            }
        }
        while (railWindowCache.size > railCacheCap) {
            val evict = railWindowCache.keys.firstOrNull { it !in requested } ?: break
            railWindowCache.remove(evict)
        }
        requested.mapNotNull { id -> railWindowCache[id]?.let { id to it } }.toMap()
    }

    /**
     * The guide's quick "immediate viewing range" fetch, rail-cache aware.
     *
     * Every category switch asks for roughly the same now-2h→now+6h window. When the 48h
     * [railWindowCache] already holds a channel — any category browsed this half-hour — its
     * quick window is sliced out of memory and only channels never seen before hit the
     * database. The first visit to a category costs one query; every revisit costs none,
     * which is what makes rail category browsing feel instant.
     */
    suspend fun quickWindowForChannels(
        epgIds: List<String>,
        fromUtcMillis: Long,
        toUtcMillis: Long,
    ): Map<String, List<Programme>> = windowCacheMutex.withLock {
        if (epgIds.isEmpty()) return@withLock emptyMap()
        val out = HashMap<String, List<Programme>>(epgIds.size)
        val missing = ArrayList<String>()
        // Slice only when the quick window sits fully inside the cached 48h range — true for
        // live browsing. A catch-up hour offset widens the window backwards past the cache,
        // and slicing a cache that doesn't cover the ask would return wrong-time programmes.
        if (railCacheStart != 0L && fromUtcMillis >= railCacheStart && toUtcMillis <= railCacheEnd) {
            for (id in epgIds) {
                val cached = railWindowCache[id]
                if (cached != null) {
                    out[id] = cached.filter { it.endUtcMillis > fromUtcMillis && it.startUtcMillis < toUtcMillis }
                } else {
                    missing += id
                }
            }
        } else {
            missing.addAll(epgIds)
        }
        if (missing.isNotEmpty()) {
            windowForChannels(missing, fromUtcMillis, toUtcMillis).forEach { (id, progs) ->
                out[id] = progs
            }
        }
        out
    }

    suspend fun upcoming(epgChannelId: String, nowUtcMillis: Long, limit: Int = 12): List<Programme> =
        programmeDao.upcoming(epgChannelId, nowUtcMillis, limit)

    // ---- Feed management -------------------------------------------------------------------

    suspend fun addCustomFeed(name: String, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty() || feedDao.byUrl(trimmed) != null) return
        feedDao.insert(
            EpgFeed(name = name.trim().ifBlank { trimmed }, url = trimmed, enabled = true),
        )
    }

    suspend fun setFeedEnabled(id: Long, enabled: Boolean) = feedDao.setEnabled(id, enabled)

    suspend fun updateFeed(feed: EpgFeed) = feedDao.update(feed)

    suspend fun removeFeed(feed: EpgFeed) = withContext(Dispatchers.IO) {
        // A removed feed takes its guide data with it — orphaned programmes would otherwise
        // keep matching channels forever with content that never refreshes.
        feed.url?.let { settings?.markFeedDeleted(it) }
        feed.providerSourceId?.let { settings?.markFeedDeleted("provider:$it") }
        settings?.markFeedDeleted("feed_name:${feed.name}")
        feedDao.delete(feed.id)
        aliasDao.deleteForFeed(feed.id)
        scope.launch {
            programmeDao.deleteForFeed(feed.id)
        }
    }

    /**
     * Makes sure the standing feed rows exist: one per provider source, plus the curated
     * built-in list. Insert-if-absent, so user toggles survive every call.
     */
    suspend fun ensureFeeds() = withContext(Dispatchers.IO) {
        val deleted = settings?.deletedFeedUrls?.value ?: emptySet()
        // Automatically prune any orphaned provider feeds whose parent provider source was removed
        val orphanedFeedIds = mutableListOf<Long>()
        for (feed in feedDao.all()) {
            if (feed.providerSourceId != null && sourceDao.byId(feed.providerSourceId) == null) {
                orphanedFeedIds.add(feed.id)
                feedDao.delete(feed.id)
                aliasDao.deleteForFeed(feed.id)
            }
        }
        if (orphanedFeedIds.isNotEmpty()) {
            scope.launch {
                for (id in orphanedFeedIds) {
                    programmeDao.deleteForFeed(id)
                }
            }
        }
        for (source in sourceDao.enabled()) {
            val key = "provider:${source.id}"
            if (key !in deleted && "feed_name:${source.name} (provider guide)" !in deleted && feedDao.forProvider(source.id) == null) {
                feedDao.insert(
                    EpgFeed(
                        name = "${source.name} (provider guide)",
                        providerSourceId = source.id,
                        enabled = true,
                    ),
                )
            }
        }
        for ((name, url) in BUILT_IN_FEEDS) {
            if (url !in deleted && "feed_name:$name" !in deleted && feedDao.byUrl(url) == null) {
                // Off by default: shipping a switched-on 20 MB download for a country the
                // user may not live in would be rude. The EPG settings screen makes
                // enabling one a single click.
                feedDao.insert(EpgFeed(name = name, url = url, builtIn = true, enabled = false))
            }
        }
    }

    // ---- Sync ------------------------------------------------------------------------------

    /** Downloads every enabled feed, merges, prunes, and re-runs the matcher.
     *
     * @param refreshIntervalMillis The minimum time between re-downloads of a single feed.
     *   Defaults to [REFRESH_INTERVAL_MILLIS] (6 h). Pass 0 to force-refresh regardless. The
     *   caller reads [AppSettings.epgRefreshHours] and converts to millis.
     */
    suspend fun syncAll(
        nowUtcMillis: Long,
        force: Boolean = false,
        refreshIntervalMillis: Long = REFRESH_INTERVAL_MILLIS,
    ): SyncSummary =
        withContext(Dispatchers.IO) {
            ensureFeeds()
            maybeAutoEnableRegionalFeed()

            var succeeded = 0
            var failed = 0
            var written = 0

            for (feed in feedDao.enabled()) {
                if (!force && nowUtcMillis - feed.lastSyncMillis < refreshIntervalMillis) {
                    succeeded++
                    continue
                }
                when (val result = syncFeed(feed, nowUtcMillis)) {
                    is FeedResult.Success -> {
                        succeeded++
                        written += result.programmes
                        feedDao.markSynced(
                            feed.id,
                            nowUtcMillis,
                            "${result.programmes} programmes, ${result.channels} channels",
                        )
                    }
                    is FeedResult.Failed -> {
                        failed++
                        // The stamp is NOT advanced on failure, so the next sync retries
                        // rather than waiting out the interval on a feed that never landed.
                        feedDao.markSynced(feed.id, feed.lastSyncMillis, result.reason)
                        Log.w(TAG, "Feed '${feed.name}' failed: ${result.reason}")
                    }
                }
            }

            if (succeeded > 0) {
                programmeDao.deleteEndedBefore(nowUtcMillis - RETENTION_PAST_MILLIS)
                programmeDao.deleteStartsAfter(nowUtcMillis + RETENTION_FUTURE_MILLIS)
            }
            reclaimDiskSpace()

            val (matched, total) = runMatcher()
            // (The post-sync 6-hour `loadWindow` pre-warm that used to run here is gone: it held
            // every channel's programmes in memory for no reader — see the note by [scope].)
            // Stamp for the guide header ("EPG updated … · N channels"). total = channels the
            // guide covers; written above is programme rows, not channels, hence total here.
            settings?.lastGuideUpdatedMillis = nowUtcMillis
            settings?.lastGuideChannelCount = total
            // Fresh guide data — drop the stale window cache so the guide re-reads it, and move the
            // generation so every cached row built from the old data re-reads too.
            windowCacheMutex.withLock {
                railWindowCache.clear()
                dataGeneration++
            }
            SyncSummary(succeeded, failed, written, matched, total)
        }

    /**
     * Reclaims disk space after the prune passes. SQLite moves deleted rows to a freelist but
     * never returns the pages to the OS, so a database that once held a large guide stays at
     * its high-water mark forever unless it is vacuumed. The WAL is checkpoint-truncated
     * unconditionally (cheap); a full VACUUM runs only when the freelist holds meaningful
     * space, since it rewrites the whole file. All sizes are logged for on-device diagnostics.
     */
    private suspend fun reclaimDiskSpace() {
        val sqlite = db?.openHelper?.writableDatabase ?: return
        withContext(Dispatchers.IO) {
            runCatching {
                sqlite.query("PRAGMA wal_checkpoint(TRUNCATE);").use { it.close() }
                fun pragmaLong(name: String): Long =
                    sqlite.query("PRAGMA $name;").use { c -> if (c.moveToFirst()) c.getLong(0) else 0L }
                val pageSize = pragmaLong("page_size")
                val beforePages = pragmaLong("page_count")
                val freeBytes = pageSize * pragmaLong("freelist_count")
                Log.i(
                    TAG,
                    "EPG db: %.1f MB on disk, %.1f MB reclaimable".format(
                        beforePages * pageSize / 1048576.0,
                        freeBytes / 1048576.0,
                    ),
                )
                if (freeBytes > VACUUM_THRESHOLD_BYTES) {
                    Log.i(TAG, "Reclaiming %.1f MB via VACUUM...".format(freeBytes / 1048576.0))
                    sqlite.execSQL("VACUUM")
                    val afterBytes = pageSize * pragmaLong("page_count")
                    Log.i(
                        TAG,
                        "VACUUM done: %.1f MB -> %.1f MB".format(
                            beforePages * pageSize / 1048576.0,
                            afterBytes / 1048576.0,
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "Disk reclaim skipped: ${it.message}") }
        }
    }

    private sealed interface FeedResult {
        data class Success(val programmes: Int, val channels: Int) : FeedResult
        data class Failed(val reason: String) : FeedResult
    }

    private suspend fun syncFeed(feed: EpgFeed, nowUtcMillis: Long): FeedResult {
        val batch = ArrayList<Programme>(BATCH_SIZE)
        val aliases = ArrayList<EpgChannelAlias>(BATCH_SIZE)
        var written = 0

        try {
            openFeedStream(feed).use { stream ->
                val stats = XmltvParser.parse(
                    input = stream,
                    feedId = feed.id,
                    onChannelAlias = { id, displayName ->
                        aliases += EpgChannelAlias(
                            feedId = feed.id,
                            epgId = id,
                            displayName = displayName ?: id,
                            normalizedKey = ChannelNameNormalizer.normalize(displayName ?: id).groupKey,
                        )
                        if (aliases.size >= BATCH_SIZE) {
                            aliasDao.upsertAll(aliases)
                            aliases.clear()
                        }
                    },
                    onProgramme = { programme ->
                        // Skip anything outside the retention window; no point writing rows we
                        // are about to prune. Past is bounded for catch-up browsing, future is
                        // bounded because providers ship 7-14 day schedules while the guide
                        // shows 48h and scheduled recordings only need ~7 days ahead — storing
                        // the full horizon for every channel is what grew the database past 1 GB.
                        if (programme.endUtcMillis >= nowUtcMillis - RETENTION_PAST_MILLIS &&
                            programme.startUtcMillis <= nowUtcMillis + RETENTION_FUTURE_MILLIS
                        ) {
                            batch += programme
                            if (batch.size >= BATCH_SIZE) {
                                programmeDao.upsertAll(batch)
                                written += batch.size
                                batch.clear()
                            }
                        }
                    },
                )
                if (batch.isNotEmpty()) {
                    programmeDao.upsertAll(batch)
                    written += batch.size
                    batch.clear()
                }
                if (aliases.isNotEmpty()) {
                    aliasDao.upsertAll(aliases)
                    aliases.clear()
                }

                if (written == 0 && stats.programmeCount == 0) {
                    return FeedResult.Failed("Downloaded, but contained no programmes.")
                }
                return FeedResult.Success(written, stats.channelCount)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Deliberately no cleanup. Whatever was written is newer than what was there,
            // and what was there is still there.
            return FeedResult.Failed(e.message ?: "Download failed.")
        }
    }

    /**
     * Opens a feed as a decompressed XML stream.
     *
     * Several of the free guide sources publish `.gz` files (a national guide compresses
     * roughly 10:1), and providers occasionally gzip xmltv.php without saying so. Sniffing
     * the first two bytes for the gzip magic number handles both, regardless of what the
     * URL or the headers claim.
     */
    private suspend fun openFeedStream(feed: EpgFeed): InputStream {
        val raw: InputStream = when {
            feed.providerSourceId != null -> {
                val source: Source = sourceDao.byId(feed.providerSourceId)
                    ?: throw IllegalStateException("Provider for this guide no longer exists.")
                api.openEpgStream(source)
            }
            feed.url != null -> {
                val response = http.newCall(Request.Builder().url(feed.url).build()).execute()
                if (!response.isSuccessful) {
                    response.close()
                    throw IllegalStateException("Guide download failed (HTTP ${response.code}).")
                }
                response.body?.byteStream()
                    ?: throw IllegalStateException("The server returned an empty guide.")
            }
            else -> throw IllegalStateException("Feed has no URL and no provider.")
        }

        val buffered = BufferedInputStream(raw, 8 * 1024)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    // ---- Matching --------------------------------------------------------------------------

    /**
     * Joins every channel to the merged guide by normalised name.
     * Returns (channels with a working guide id, total channels).
     */
    suspend fun runMatcher(): Pair<Int, Int> {
        val aliases = aliasDao.all()
        val index = EpgMatcher.buildIndex(aliases.map { it.epgId to it.displayName })
        // Only ids that actually have programmes count as "working" — a match against a
        // channel the guide lists but never fills is a guide that looks broken.
        val populated = programmeDao.channelIdsWithProgrammes().toHashSet()

        val channels = channelDao.allForMatching()
        var matched = 0
        val updates = ArrayList<Pair<Long, String?>>()

        for (channel in channels) {
            val newMatch = index.match(channel.groupKey)
            if (newMatch != channel.matchedEpgId) {
                updates += channel.id to newMatch
            }
            val works = channel.epgCandidates.any { it in populated } ||
                (newMatch != null && newMatch in populated)
            if (works) matched++
        }

        if (updates.isNotEmpty()) {
            updates.chunked(BATCH_SIZE).forEach { chunk ->
                channelDao.updateMatchedEpgIds(chunk)
            }
        }

        Log.i(
            TAG,
            "Matcher: $matched of ${channels.size} channels have a working guide " +
                "(${aliases.size} guide aliases, ${populated.size} populated guide channels)",
        )
        return matched to channels.size
    }

    suspend fun setManualOverride(channelId: Long, epgId: String?) =
        channelDao.setEpgOverride(channelId, epgId)

    /**
     * Turns on the free guide for the user's region, once, on a pristine install.
     *
     * The reasoning: a provider guide is usually empty, and "go and find Guide settings"
     * is a hurdle most people meet as a blank guide and give up on. The app already knows
     * the region — the normaliser reads it off the `UK|` prefixes the provider itself
     * ships — so when a clear majority of channels agree, enable that region's built-in.
     *
     * Guard rails: only when every built-in is untouched (never enabled, never synced)
     * and no custom feed exists. Switch it off and it stays off — a choice the user has
     * made is never overridden.
     */
    private suspend fun maybeAutoEnableRegionalFeed() {
        val all = feedDao.all()
        val builtIns = all.filter { it.builtIn }
        if (builtIns.isEmpty()) return
        val pristine = builtIns.all { !it.enabled && it.lastSyncMillis == 0L } &&
            all.none { !it.builtIn && it.providerSourceId == null }
        if (!pristine) return

        val regionCounts = HashMap<String, Int>()
        for (channel in channelDao.allForMatching()) {
            val region = ChannelNameNormalizer.normalize(channel.name).region ?: continue
            regionCounts.merge(region, 1, Int::plus)
        }
        val top = regionCounts.maxByOrNull { it.value } ?: return
        if (top.value < MIN_CHANNELS_FOR_AUTO_REGION) return

        val feedName = REGION_TO_FEED[top.key] ?: return
        val feed = builtIns.firstOrNull { it.name == feedName } ?: return
        feedDao.setEnabled(feed.id, true)
        Log.i(TAG, "Auto-enabled '${feed.name}' — ${top.value} channels tagged ${top.key}")
    }

    companion object {
        private const val TAG = "EpgRepository"

        /** Writes per transaction. Large enough to be fast, small enough not to hold WAL open. */
        const val BATCH_SIZE = 500

        /** Keep finished programmes for 3 days to support catch-up / archive TV browsing. */
        val RETENTION_PAST_MILLIS: Long = TimeUnit.DAYS.toMillis(3)

        /**
         * Cap on how far ahead programmes are stored. Feeds publish 7-14 day schedules, but the
         * guide renders 48h and the recording scheduler only needs ~7 days — capping this is
         * what keeps the database a fraction of the size of an uncapped store.
         */
        val RETENTION_FUTURE_MILLIS: Long = TimeUnit.DAYS.toMillis(7)

        /** Vacuum only when the freelist holds at least this much — a full rewrite is not cheap. */
        const val VACUUM_THRESHOLD_BYTES: Long = 32L * 1024 * 1024

        /** Feeds publish rolling windows; refreshing more often than this is rude. */
        val REFRESH_INTERVAL_MILLIS: Long = TimeUnit.HOURS.toMillis(6)

        /**
         * The curated built-in list. Criteria for being here: free, no key or signup,
         * openly licensed or explicitly public, and reachable as plain XMLTV(.gz).
         * All ship disabled; the user turns on the one for where they live.
         *
         * The UK Freeview entry is GPL-3.0 (same licence as OpenTV), regenerated every
         * 12 h, ~270 channels with logos — verified working before it earned this slot.
         */
        /** A region needs at least this many prefixed channels before we act on it. */
        const val MIN_CHANNELS_FOR_AUTO_REGION = 5

        /** Region tag (as providers write it) → built-in feed name to auto-enable. */
        val REGION_TO_FEED: Map<String, String> = mapOf(
            "UK" to "UK — Freeview (free-to-air)",
            "GB" to "UK — Freeview (free-to-air)",
            "US" to "USA — epgshare01",
            "USA" to "USA — epgshare01",
            "CA" to "Canada — epgshare01",
            "AU" to "Australia — epgshare01",
            "AUS" to "Australia — epgshare01",
        )

        val BUILT_IN_FEEDS: List<Pair<String, String>> = listOf(
            "UK — Freeview (free-to-air)" to
                "https://raw.githubusercontent.com/dp247/Freeview-EPG/master/epg.xml",
            "UK — epgshare01 (Sky lineup)" to
                "https://epgshare01.online/epgshare01/epg_ripper_UK1.xml.gz",
            "USA — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_US1.xml.gz",
            "Canada — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_CA1.xml.gz",
            "Australia — epgshare01" to
                "https://epgshare01.online/epgshare01/epg_ripper_AU1.xml.gz",
        )
    }
}
