/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.pairing

/**
 * Turns what a provisioning portal said about a playlist's sections into the three flags the app
 * stores.
 *
 * ## Why this is not just "read three booleans, with defaults"
 *
 * A portal describes what to load, and it is entitled to describe it by listing only the boxes that
 * are ticked — `{movies: true, shows: true}` for someone who has just unticked Channels. Reading
 * the missing `channels` key as its default (`true`) imported exactly the channel list the user had
 * excluded, which is the bug this rule exists to prevent: unticking a box has to have an effect even
 * when the unticked box is simply absent from the payload.
 *
 * So "not stated" is tracked separately from `false`:
 *
 *  - A portal that names **any** of the three is enumerating choices, so the ones it did not name
 *    are **off**.
 *  - A portal that names **none** of them is saying nothing about sections, and the historical
 *    defaults stand — channels on, movies and shows off — so playlists added by hand, and portals
 *    written before this rule, behave exactly as they did.
 *
 * An explicit value always wins over both, in either direction.
 */
object ProvisionOptions {

    data class Resolved(
        val includeLive: Boolean,
        val includeVod: Boolean,
        val includeSeries: Boolean,
    )

    /** True when nothing at all was stated, i.e. the defaults below had to be assumed. */
    private val ASSUMED = Resolved(includeLive = true, includeVod = false, includeSeries = false)

    /**
     * Resolves the three flags. Each input is `true`/`false` when the portal stated it, or null when
     * it said nothing about that section.
     */
    fun resolve(live: Boolean?, vod: Boolean?, series: Boolean?): Resolved {
        val statedAny = live != null || vod != null || series != null
        if (!statedAny) return ASSUMED
        return Resolved(
            includeLive = live ?: false,
            includeVod = vod ?: false,
            includeSeries = series ?: false,
        )
    }
}
