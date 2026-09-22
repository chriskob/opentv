/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.flow.distinctUntilChanged

/** How many items past the visible edge of a shelf get warmed. */
private const val PREFETCH_AHEAD = 12

/** How many items behind the visible edge get warmed when scrolling back. */
private const val PREFETCH_BEHIND = 6

/** How many posters are warmed immediately on first composition, before any scroll. */
private const val INITIAL_WARM_COUNT = 24

/**
 * The size posters are decoded at, in pixels — a poster card is 140dp wide (2:3 art), so at the
 * 320dpi a TV box reports that is 280x420, and this leaves headroom for the focused card's scale
 * and for a denser panel.
 *
 * Without an explicit size, an enqueued request has no target to measure and Coil decodes at the
 * poster's *original* resolution — a 1500x2250 panel thumbnail is 6.7MB even in RGB_565. Against a
 * memory cache sized at a few percent of the heap that is three or four posters, so scrolling
 * evicted and re-downloaded everything it had just shown (the "posters take forever to appear"
 * report), and the decode work itself is what makes a Fire Stick stutter. At 360x540 a poster is
 * ~0.4MB — nearly twice as many fit in cache as the old 480x720 (0.7MB), with no visible
 * difference on a 140dp card at TV distance — and every appearance after the first is a memory hit.
 *
 * The same request builds the UI's image and the prefetch, so they share one cache key and one
 * decode; getting the two out of step would have them decoding the same art twice at two sizes.
 */
private const val POSTER_ART_WIDTH_PX = 360
private const val POSTER_ART_HEIGHT_PX = 540

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
 * Builds a channel-logo request with a stable cache key, namespaced away from posters.
 *
 * Logos are tiny (32-48dp) and numerous (one per guide row). Without an explicit size Coil
 * decodes the provider's original (often 512px+) for a 36dp view, and without a stable key
 * each size variant decodes separately — the same churn posters had before [posterRequest].
 * Disk bytes are shared by URL; memory keys are partitioned as `logo:url:size` so a logo
 * never collides with a poster for the same URL.
 */
fun logoRequest(context: Context, url: String?, sizePx: Int = 96): ImageRequest? {
    if (url.isNullOrBlank()) return null
    val px = sizePx.coerceIn(48, 256)
    return ImageRequest.Builder(context)
        .data(url)
        .memoryCacheKey("logo:$url:$px")
        .diskCacheKey(url)
        .size(px, px)
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
 *
 * On first composition the visible info is not laid out yet (index -1), so the first
 * [INITIAL_WARM_COUNT] posters are also enqueued up front — that is what makes opening Movies feel
 * instant instead of painting grey boxes that fill in one by one.
 */
@Composable
fun PrefetchImagesAhead(lastVisibleIndex: () -> Int, urls: List<String>) {
    PrefetchImagesAround(
        firstVisibleIndex = { -1 },
        lastVisibleIndex = lastVisibleIndex,
        urls = urls,
    )
}

/**
 * Bidirectional prefetch: warms [PREFETCH_AHEAD] items past the visible edge and [PREFETCH_BEHIND]
 * behind it, so scrolling back is a memory hit too. Backwards scrolls on a d-pad (Up/Left through a
 * grid) are just as common as forwards ones, and the old forward-only version re-decoded every
 * poster on the way back.
 *
 * Pass `{ -1 }` for [firstVisibleIndex] to keep the old forward-only behaviour.
 */
@Composable
fun PrefetchImagesAround(
    firstVisibleIndex: () -> Int,
    lastVisibleIndex: () -> Int,
    urls: List<String>,
) {
    val context = LocalContext.current
    val loader = context.imageLoader
    // URLs already enqueued by this shelf instance. Room re-emits the title list on every
    // artwork backfill write, which hands this a new list *instance* with nearly identical
    // content — without dedup each write re-enqueued the initial 24 + ahead window, a Coil
    // enqueue storm that churned the heap on a 2GB box while posters were still filling in.
    // Blanks are never recorded: a missing poster that later backfills must still warm.
    val warmed = remember { mutableSetOf<String>() }
    fun warm(url: String) {
        if (url.isBlank() || !warmed.add(url)) return
        if (warmed.size > 4000) warmed.clear()
        posterRequest(context, url)?.let { loader.enqueue(it) }
    }
    // Initial warm: first screenful decodes before the user touches the d-pad.
    LaunchedEffect(urls) {
        if (urls.isEmpty()) return@LaunchedEffect
        val to = (INITIAL_WARM_COUNT - 1).coerceAtMost(urls.lastIndex)
        for (i in 0..to) {
            warm(urls[i])
        }
    }
    LaunchedEffect(urls) {
        snapshotFlow { lastVisibleIndex() to firstVisibleIndex() }
            .distinctUntilChanged()
            .collect { (last, first) ->
                if (urls.isEmpty()) return@collect
                if (last >= 0) {
                    val from = last + 1
                    val to = (last + PREFETCH_AHEAD).coerceAtMost(urls.lastIndex)
                    for (i in from..to) {
                        warm(urls[i])
                    }
                }
                if (first > 0) {
                    val from = (first - PREFETCH_BEHIND).coerceAtLeast(0)
                    val to = first - 1
                    for (i in from..to) {
                        warm(urls[i])
                    }
                }
            }
    }
}
