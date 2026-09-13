/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

/** How many items past the visible edge of a shelf get warmed. */
private const val PREFETCH_AHEAD = 12

/**
 * The size posters are decoded at, in pixels — a poster card is 140dp wide (2:3 art), so at the
 * 320dpi a TV box reports that is 280x420, and this leaves headroom for the focused card's scale
 * and for a denser panel.
 *
 * Without an explicit size, an enqueued request has no target to measure and Coil decodes at the
 * poster's *original* resolution — a 1500x2250 panel thumbnail is 6.7MB even in RGB_565. Against a
 * memory cache sized at a few percent of the heap that is three or four posters, so scrolling
 * evicted and re-downloaded everything it had just shown (the "posters take forever to appear"
 * report), and the decode work itself is what makes a Fire Stick stutter. At 480x720 a poster is
 * 0.7MB — ten times as many fit in cache, and every appearance after the first is a memory hit.
 *
 * The same request builds the UI's image and the prefetch, so they share one cache key and one
 * decode; getting the two out of step would have them decoding the same art twice at two sizes.
 */
private const val POSTER_ART_WIDTH_PX = 480
private const val POSTER_ART_HEIGHT_PX = 720

/**
 * Builds a poster request with a stable cache key.
 *
 * Coil's default memory key varies with the requested size, so the same poster shown at rail size
 * and again at detail size — or on a shelf aimed at a different screen — is fetched and decoded
 * twice. Keying on the URL alone makes every repeat appearance a memory hit, which is what makes
 * flicking along a shelf feel instant instead of showing grey boxes again.
 */
fun posterRequest(context: Context, url: String?): ImageRequest? {
    if (url.isNullOrBlank()) return null
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey(url)
        .diskCacheKey(url)
        .size(POSTER_ART_WIDTH_PX, POSTER_ART_HEIGHT_PX)
        .crossfade(false)
        .build()
}

/**
 * Warms the image caches for the art just past the visible edge of a shelf or grid, so scrolling
 * into it shows decoded posters rather than a loading placeholder — the behaviour a viewer expects
 * from a paid IPTV player.
 *
 * [lastVisibleIndex] is read as `state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1` by the
 * caller, which keeps this usable for both LazyRow and LazyVerticalGrid. Work is bounded to
 * [PREFETCH_AHEAD] items per movement and runs off the composition, so a 20,000-title library never
 * turns into a burst of fetches.
 */
@Composable
fun PrefetchImagesAhead(lastVisibleIndex: () -> Int, urls: List<String>) {
    val context = LocalContext.current
    val loader = context.imageLoader
    LaunchedEffect(urls) {
        snapshotFlow(lastVisibleIndex)
            .distinctUntilChanged()
            .collect { last ->
                if (last < 0 || urls.isEmpty()) return@collect
                val from = last + 1
                val to = (last + PREFETCH_AHEAD).coerceAtMost(urls.lastIndex)
                for (i in from..to) {
                    posterRequest(context, urls[i])?.let { loader.enqueue(it) }
                }
            }
    }
}
