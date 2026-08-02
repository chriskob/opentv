/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.sync

import app.opentv.core.ServiceLocator
import app.opentv.data.model.PlaybackPosition
import app.opentv.data.model.Profile
import kotlinx.serialization.json.Json

/**
 * Reads local state into a [SyncBundle] and applies a received one. The single place that knows
 * how OpenTV's tables map onto the portable, cross-device bundle, so the server, the client and
 * the "sync now" button all agree on what travels and how it merges.
 *
 * Merge policy (see [SyncBundle]): watch positions are two-way newest-wins; curation (profiles,
 * favourites, hidden channels, hidden categories) is applied **additively** — we spread what the
 * hub has without deleting anything locally. That keeps a first-version sync from ever wiping a
 * favourite you set on one box or un-hiding the structural separator rows the importer hides.
 */
class SyncEngine(private val graph: ServiceLocator.Graph) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun gather(): SyncBundle {
        val profiles = graph.profiles.all()
        val names = profiles.associate { it.id to it.name }
        val positions = graph.playbackPositions.all().mapNotNull { pos ->
            val profileName = names[pos.profileId] ?: return@mapNotNull null
            val parts = pos.mediaKey.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null
            val id = parts[1].toLongOrNull() ?: return@mapNotNull null
            val streamUrl = when (parts[0]) {
                "movie" -> graph.catalogRepository.movie(id)?.streamUrl
                "ep" -> graph.catalogRepository.episode(id)?.streamUrl
                else -> null
            } ?: return@mapNotNull null
            SyncPosition(
                profile = profileName,
                streamUrl = streamUrl,
                kind = parts[0],
                positionMillis = pos.positionMillis,
                durationMillis = pos.durationMillis,
                updatedAtMillis = pos.updatedAtMillis,
            )
        }
        return SyncBundle(
            generatedAtMillis = System.currentTimeMillis(),
            profiles = profiles.map { it.name },
            positions = positions,
            favouriteChannels = graph.catalogRepository.favouriteChannelUrls(),
            hiddenChannels = graph.catalogRepository.hiddenChannelUrls(),
            favouriteMovies = graph.catalogRepository.favouriteMovieUrls(),
            hiddenCategories = graph.settings.hiddenCategories.value.toList(),
        )
    }

    fun encode(bundle: SyncBundle): String = json.encodeToString(SyncBundle.serializer(), bundle)

    fun decode(bundleJson: String): SyncBundle =
        json.decodeFromString(SyncBundle.serializer(), bundleJson)

    /** Applies a received bundle. Returns the number of watch-position rows changed. */
    suspend fun apply(bundle: SyncBundle): Int {
        // Profiles — add any the hub has that we're missing. Never remove (that would delete a
        // local profile's watch history).
        for (name in bundle.profiles) {
            if (name.isNotBlank() && graph.profiles.byName(name) == null) {
                runCatching { graph.profiles.insert(Profile(name = name, createdAtMillis = 0)) }
            }
        }

        // Curation — additive union, keyed by stable identifiers.
        for (url in bundle.favouriteChannels) graph.catalogRepository.markChannelFavouriteByUrl(url)
        for (url in bundle.hiddenChannels) graph.catalogRepository.markChannelHiddenByUrl(url)
        for (url in bundle.favouriteMovies) graph.catalogRepository.markMovieFavouriteByUrl(url)
        if (bundle.hiddenCategories.isNotEmpty()) {
            graph.settings.setHiddenCategories(
                graph.settings.hiddenCategories.value + bundle.hiddenCategories,
            )
        }

        // Watch positions — two-way, newest wins.
        var changed = 0
        for (item in bundle.positions) {
            val profileId = profileIdFor(item.profile) ?: continue
            val localId = when (item.kind) {
                "movie" -> graph.catalogRepository.movieByStreamUrl(item.streamUrl)?.id
                "ep" -> graph.catalogRepository.episodeByStreamUrl(item.streamUrl)?.id
                else -> null
            } ?: continue
            val mediaKey = "${item.kind}:$localId"
            val existing = graph.playbackPositions.get(profileId, mediaKey)
            if (existing == null || item.updatedAtMillis > existing.updatedAtMillis) {
                graph.playbackPositions.upsert(
                    PlaybackPosition(
                        profileId = profileId,
                        mediaKey = mediaKey,
                        positionMillis = item.positionMillis,
                        durationMillis = item.durationMillis,
                        updatedAtMillis = item.updatedAtMillis,
                    ),
                )
                changed++
            }
        }
        return changed
    }

    private suspend fun profileIdFor(name: String): Long? {
        graph.profiles.byName(name)?.let { return it.id }
        return runCatching { graph.profiles.insert(Profile(name = name, createdAtMillis = 0)) }.getOrNull()
    }
}
