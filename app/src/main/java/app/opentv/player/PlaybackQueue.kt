/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.player

/**
 * The ordered channel list the full-screen player can zap through.
 *
 * Set by whatever launches playback (the guide passes the list you were browsing), read by the
 * player for channel up/down and the in-player channel list. A plain in-memory handoff rather
 * than a nav argument, because the list can be thousands of channels — far too big for a URL —
 * and it only needs to survive the hop from the guide to the player.
 */
object PlaybackQueue {
    data class Item(val id: Long, val name: String, val logoUrl: String?, val number: Int? = null)

    @Volatile
    var items: List<Item> = emptyList()

    /**
     * The channel that was playing before the current one, for the player's "previous channel"
     * key.
     *
     * It lives here rather than in the player because the player's own `previousId` is
     * `remember`-scoped: it resets the moment you leave, so the key only worked if you had
     * changed channel *within* the same visit. Coming from the guide into a category player —
     * which is how it is reached for most categories — always started with nothing to go back
     * to. Surviving the hop means the key means what it says: the channel you were watching
     * last, whatever list it came from.
     */
    @Volatile
    var previousChannelId: Long = 0L
}
