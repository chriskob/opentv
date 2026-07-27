/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.core

import android.content.Context
import app.opentv.data.db.OpenTvDatabase
import app.opentv.data.remote.XtreamApi
import app.opentv.data.repo.CatalogRepository
import app.opentv.data.repo.EpgRepository
import app.opentv.data.repo.SourceRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Hand-rolled dependency container.
 *
 * Hilt would be the conventional choice. It is deliberately not used: it adds an annotation
 * processor, a build-time graph, and a class of error message that is genuinely hard to read,
 * in exchange for wiring roughly a dozen objects. For a project whose main hope is drive-by
 * contributions from people fixing their own bugs, "you can read the whole graph in one file"
 * is worth more than the ceremony.
 */
object ServiceLocator {

    @Volatile private var instance: Graph? = null

    fun get(context: Context): Graph =
        instance ?: synchronized(this) {
            instance ?: Graph(context.applicationContext).also { instance = it }
        }

    class Graph(context: Context) {
        val database: OpenTvDatabase by lazy { OpenTvDatabase.build(context) }

        val httpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                // Generous: catalogue endpoints on a busy panel can take a long time to
                // produce 40,000 rows, and timing out mid-list is worse than waiting.
                .readTimeout(60, TimeUnit.SECONDS)
                .callTimeout(5, TimeUnit.MINUTES)
                .retryOnConnectionFailure(true)
                .followRedirects(true)
                .build()
        }

        val xtreamApi: XtreamApi by lazy { XtreamApi(httpClient) }

        val sourceRepository: SourceRepository by lazy {
            SourceRepository(database.sources(), xtreamApi)
        }

        val catalogRepository: CatalogRepository by lazy {
            CatalogRepository(
                sourceDao = database.sources(),
                channelDao = database.channels(),
                categoryDao = database.categories(),
                movieDao = database.movies(),
                seriesDao = database.series(),
                episodeDao = database.episodes(),
                api = xtreamApi,
                http = httpClient,
            )
        }

        val epgRepository: EpgRepository by lazy {
            EpgRepository(database.programmes(), database.sources(), xtreamApi)
        }

        val playbackPositions get() = database.positions()
    }
}
