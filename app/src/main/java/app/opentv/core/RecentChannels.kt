/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

/**
 * One entry in the player's watched-channel bar: a source, and the provider's own id for the
 * channel inside it. Deliberately *not* the channel's database row id — see [RecentChannels].
 */
data class RecentChannelRef(val sourceId: Long, val streamId: String)

/**
 * The watched-channel history, keyed so that it survives everything the catalogue does to itself.
 *
 * The bar used to store `Channel.id`. That is an `autoGenerate` row id, and nothing keeps it:
 *
 *  - [app.opentv.data.db.ChannelDao.deleteStale] deletes every channel a provider did not list in
 *    the latest sync. A provider that drops a channel for one sync — or one partial fetch — takes
 *    the row with it. When the channel is listed again it is *inserted*, so it comes back under a
 *    **new** id.
 *  - Deleting and re-adding a playlist reassigns every id at once.
 *
 * So a stored id can refer to nothing while the channel itself is alive and well. This type stores
 * the pair the rest of the app already treats as the identity of user state — the same key
 * [app.opentv.data.db.ChannelDao.replaceCatalogue] matches on to carry favourites, renames and
 * hidden flags across a refresh, and the same one the entity documents as "stable per source".
 * A re-sync therefore cannot orphan a history entry, and a channel that returns under a new id
 * comes back with its place in the bar intact.
 */
object RecentChannels {

    /** How many channels the bar remembers. */
    const val LIMIT = 30

    private const val DELIMITER = ","
    private const val SEPARATOR = ':'

    /** Renders refs for SharedPreferences: `sourceId:streamId`, newest first, comma separated. */
    fun encode(refs: List<RecentChannelRef>): String =
        refs.joinToString(DELIMITER) { "${it.sourceId}$SEPARATOR${it.streamId}" }

    /**
     * Parses stored refs, dropping anything malformed.
     *
     * Splits on the **first** colon only. A source id is a number, but a stream id can contain
     * colons of its own — M3U channels carry the provider's guide id as `tvg:bbc1`, or a hash as
     * `url:1f3c…` (see [app.opentv.data.import.M3uParser]).
     */
    fun decode(raw: String?): List<RecentChannelRef> {
        if (raw.isNullOrBlank()) return emptyList()
        return raw.split(DELIMITER).mapNotNull { entry ->
            val trimmed = entry.trim()
            val cut = trimmed.indexOf(SEPARATOR)
            if (cut <= 0) return@mapNotNull null
            val sourceId = trimmed.substring(0, cut).toLongOrNull() ?: return@mapNotNull null
            val streamId = trimmed.substring(cut + 1)
            if (streamId.isEmpty()) return@mapNotNull null
            RecentChannelRef(sourceId, streamId)
        }.distinct()
    }

    /**
     * The refs worth forgetting: those whose **source no longer exists**, so the playlist they came
     * from has been deleted and there is nothing left to resolve them against. Unrecoverable by
     * definition, and the only thing this app is entitled to throw away.
     *
     * Everything else is kept, including a ref whose channel is momentarily absent. That is the
     * whole point of the type: absence is not death. The older id-based sweep decided the opposite
     * ("it resolves to nothing, so forget it") and that is exactly why channels used to vanish from
     * the bar for good. Entries the provider has genuinely retired still leave eventually — they
     * age out of the [LIMIT] as other channels are watched — but they do it without discarding
     * anything that was coming back.
     */
    fun refsToForget(refs: List<RecentChannelRef>, liveSourceIds: Set<Long>): List<RecentChannelRef> =
        refs.filterNot { it.sourceId in liveSourceIds }
}
