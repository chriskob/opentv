/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import app.opentv.data.model.Category
import app.opentv.data.model.Channel
import app.opentv.data.model.Episode
import app.opentv.data.model.Movie
import app.opentv.data.model.Series
import app.opentv.data.model.Source
import app.opentv.data.model.StreamKind
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Client for the Xtream Codes "player API".
 *
 * ## Why this is hand-rolled instead of using typed `@Serializable` models
 *
 * Xtream panels are not a standard. Different builds return the same field as a number, a
 * quoted number, an empty string, `null`, or omit it entirely — and `stream_id` in particular
 * flips between `12345` and `"12345"` depending on the panel. Strict deserialisation into a
 * data class means one unusual field aborts the parse of the *entire* catalogue, which the
 * user experiences as "the app won't load my channels" with no way to diagnose it.
 *
 * So we read [JsonElement] defensively and coerce. Every accessor below tolerates the wrong
 * type and returns null rather than throwing. Ugly; correct.
 */
class XtreamApi(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

    /** Verifies credentials and returns the account's expiry, if the panel reports one. */
    suspend fun authenticate(source: Source): AccountInfo = withContext(Dispatchers.IO) {
        val body = getJson(source, action = null).jsonObject
        val userInfo = body["user_info"]?.jsonObjectOrNull
            ?: throw XtreamException("The server did not return account information.")

        val status = userInfo["status"].asStringOrNull
        if (status != null && !status.equals("Active", ignoreCase = true)) {
            throw XtreamException("This account is $status.")
        }
        if (userInfo["auth"].asIntOrNull == 0) {
            throw XtreamException("Username or password rejected by the server.")
        }

        AccountInfo(
            username = userInfo["username"].asStringOrNull,
            expiryMillis = userInfo["exp_date"].asLongOrNull?.times(1000),
            maxConnections = userInfo["max_connections"].asIntOrNull,
            activeConnections = userInfo["active_cons"].asIntOrNull,
            timezone = body["server_info"]?.jsonObjectOrNull?.get("timezone").asStringOrNull,
        )
    }

    suspend fun liveCategories(source: Source): List<Category> =
        categories(source, "get_live_categories", StreamKind.LIVE)

    suspend fun movieCategories(source: Source): List<Category> =
        categories(source, "get_vod_categories", StreamKind.MOVIE)

    suspend fun seriesCategories(source: Source): List<Category> =
        categories(source, "get_series_categories", StreamKind.SERIES)

    private suspend fun categories(
        source: Source,
        action: String,
        kind: StreamKind,
    ): List<Category> = withContext(Dispatchers.IO) {
        getJson(source, action).arrayOrEmpty.mapIndexedNotNull { index, element ->
            val obj = element.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val id = obj["category_id"].asStringOrNull ?: return@mapIndexedNotNull null
            Category(
                id = id,
                sourceId = source.id,
                name = obj["category_name"].asStringOrNull ?: id,
                kind = kind,
                sortIndex = index,
            )
        }
    }

    suspend fun liveStreams(source: Source): List<Channel> = withContext(Dispatchers.IO) {
        getJson(source, "get_live_streams").arrayOrEmpty.mapIndexedNotNull { index, element ->
            val obj = element.jsonObjectOrNull ?: return@mapIndexedNotNull null
            val streamId = obj["stream_id"].asStringOrNull ?: return@mapIndexedNotNull null
            val name = obj["name"].asStringOrNull ?: return@mapIndexedNotNull null
            Channel(
                sourceId = source.id,
                streamId = streamId,
                name = name,
                categoryId = obj["category_id"].asStringOrNull,
                logoUrl = obj["stream_icon"].asStringOrNull?.takeIf { it.isNotBlank() },
                // epg_channel_id is what joins to XMLTV; it is frequently blank, in which
                // case the channel simply has no guide rather than the guide being broken.
                epgChannelId = obj["epg_channel_id"].asStringOrNull?.takeIf { it.isNotBlank() },
                number = obj["num"].asIntOrNull,
                streamUrl = liveStreamUrl(source, streamId),
                sortIndex = obj["num"].asIntOrNull ?: index,
            )
        }
    }

    suspend fun movies(source: Source): List<Movie> = withContext(Dispatchers.IO) {
        getJson(source, "get_vod_streams").arrayOrEmpty.mapNotNull { element ->
            val obj = element.jsonObjectOrNull ?: return@mapNotNull null
            val streamId = obj["stream_id"].asStringOrNull ?: return@mapNotNull null
            val name = obj["name"].asStringOrNull ?: return@mapNotNull null
            val extension = obj["container_extension"].asStringOrNull?.takeIf { it.isNotBlank() }
            Movie(
                sourceId = source.id,
                streamId = streamId,
                name = name,
                categoryId = obj["category_id"].asStringOrNull,
                posterUrl = obj["stream_icon"].asStringOrNull?.takeIf { it.isNotBlank() },
                rating = obj["rating"].asDoubleOrNull,
                year = obj["year"].asIntOrNull,
                plot = obj["plot"].asStringOrNull,
                durationSeconds = obj["duration_secs"].asIntOrNull,
                containerExtension = extension,
                streamUrl = vodStreamUrl(source, streamId, extension),
                addedMillis = obj["added"].asLongOrNull?.times(1000) ?: 0L,
            )
        }
    }

    suspend fun series(source: Source): List<Series> = withContext(Dispatchers.IO) {
        getJson(source, "get_series").arrayOrEmpty.mapNotNull { element ->
            val obj = element.jsonObjectOrNull ?: return@mapNotNull null
            val seriesId = obj["series_id"].asStringOrNull ?: return@mapNotNull null
            val name = obj["name"].asStringOrNull ?: return@mapNotNull null
            Series(
                sourceId = source.id,
                seriesId = seriesId,
                name = name,
                categoryId = obj["category_id"].asStringOrNull,
                posterUrl = obj["cover"].asStringOrNull?.takeIf { it.isNotBlank() },
                rating = obj["rating"].asDoubleOrNull,
                year = obj["year"].asIntOrNull ?: obj["releaseDate"].asStringOrNull?.take(4)?.toIntOrNull(),
                plot = obj["plot"].asStringOrNull,
                addedMillis = obj["last_modified"].asLongOrNull?.times(1000) ?: 0L,
            )
        }
    }

    /**
     * Episode list for one series.
     *
     * `episodes` is an object keyed by season number whose values are arrays — except on some
     * panels, where it is an array of arrays, and on others where a season with a single
     * episode collapses to a bare object. All three shapes are handled.
     */
    suspend fun episodes(source: Source, seriesId: String): List<Episode> =
        withContext(Dispatchers.IO) {
            val body = getJson(source, "get_series_info") { it.addQueryParameter("series_id", seriesId) }
            val episodesNode = body.jsonObjectOrNull?.get("episodes") ?: return@withContext emptyList()

            val seasonBuckets: List<Pair<Int?, JsonElement>> = when (episodesNode) {
                is JsonObject -> episodesNode.entries.map { it.key.toIntOrNull() to it.value }
                is JsonArray -> episodesNode.mapIndexed { index, value -> index to value }
                else -> emptyList()
            }

            seasonBuckets.flatMap { (seasonHint, bucket) ->
                val items: List<JsonElement> = when (bucket) {
                    is JsonArray -> bucket.toList()
                    is JsonObject -> listOf(bucket)
                    else -> emptyList()
                }
                items.mapNotNull { element ->
                    val obj = element.jsonObjectOrNull ?: return@mapNotNull null
                    val episodeId = obj["id"].asStringOrNull ?: return@mapNotNull null
                    val info = obj["info"].jsonObjectOrNull
                    val extension = obj["container_extension"].asStringOrNull
                        ?.takeIf { it.isNotBlank() }
                    Episode(
                        sourceId = source.id,
                        seriesId = seriesId,
                        episodeId = episodeId,
                        season = obj["season"].asIntOrNull ?: seasonHint ?: 1,
                        episodeNumber = obj["episode_num"].asIntOrNull ?: 0,
                        title = obj["title"].asStringOrNull ?: "Episode",
                        plot = info?.get("plot").asStringOrNull,
                        durationSeconds = info?.get("duration_secs").asIntOrNull,
                        stillUrl = info?.get("movie_image").asStringOrNull?.takeIf { it.isNotBlank() },
                        streamUrl = seriesStreamUrl(source, episodeId, extension),
                    )
                }
            }
        }

    /** Opens the XMLTV guide as a stream. The caller must close it. */
    suspend fun openEpgStream(source: Source): InputStream = withContext(Dispatchers.IO) {
        val url = source.epgUrl?.toHttpUrlOrNull() ?: xmltvUrl(source)
        val response = http.newCall(request(source, url)).execute()
        if (!response.isSuccessful) {
            response.close()
            throw XtreamException("Guide download failed (HTTP ${response.code}).")
        }
        response.body?.byteStream()
            ?: throw XtreamException("The server returned an empty guide.")
    }

    // ---- URL construction ----------------------------------------------------------------

    fun liveStreamUrl(source: Source, streamId: String): String =
        "${source.url}/live/${source.username}/${source.password}/$streamId.m3u8"

    fun vodStreamUrl(source: Source, streamId: String, extension: String?): String =
        "${source.url}/movie/${source.username}/${source.password}/$streamId.${extension ?: "mp4"}"

    fun seriesStreamUrl(source: Source, episodeId: String, extension: String?): String =
        "${source.url}/series/${source.username}/${source.password}/$episodeId.${extension ?: "mp4"}"

    private fun xmltvUrl(source: Source): HttpUrl =
        baseUrl(source).newBuilder()
            .encodedPath("/xmltv.php")
            .addQueryParameter("username", source.username.orEmpty())
            .addQueryParameter("password", source.password.orEmpty())
            .build()

    private fun baseUrl(source: Source): HttpUrl =
        source.url.toHttpUrlOrNull()
            ?: throw XtreamException("\"${source.url}\" is not a valid server address.")

    // ---- Plumbing ------------------------------------------------------------------------

    private fun getJson(
        source: Source,
        action: String?,
        extra: (HttpUrl.Builder) -> Unit = {},
    ): JsonElement {
        val builder = baseUrl(source).newBuilder()
            .encodedPath("/player_api.php")
            .addQueryParameter("username", source.username.orEmpty())
            .addQueryParameter("password", source.password.orEmpty())
        if (action != null) builder.addQueryParameter("action", action)
        extra(builder)

        http.newCall(request(source, builder.build())).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamException(describeHttpFailure(response.code))
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) throw XtreamException("The server returned an empty response.")
            return try {
                json.parseToJsonElement(text)
            } catch (e: Exception) {
                // Panels behind a captive portal or Cloudflare return HTML here. Saying
                // "not valid JSON" is useless to a user; say what it probably means.
                throw XtreamException(
                    "The server replied with something that is not a valid catalogue. " +
                        "Check the address and port are correct.",
                    e,
                )
            }
        }
    }

    private fun request(source: Source, url: HttpUrl): Request =
        Request.Builder()
            .url(url)
            .header("User-Agent", source.userAgent)
            .build()

    private fun describeHttpFailure(code: Int): String = when (code) {
        401, 403 -> "The server refused the request (HTTP $code). The username or password " +
            "may be wrong, or the provider may be blocking this app's User-Agent — try " +
            "changing it in the source's advanced settings."
        404 -> "No Xtream API at that address (HTTP 404). Check the URL and port."
        405 -> "The server rejected the request method (HTTP 405). This usually means the " +
            "address points at a plain playlist rather than an Xtream panel."
        429 -> "The server is rate-limiting this device (HTTP 429). Try again shortly."
        in 500..599 -> "The provider's server is having problems (HTTP $code)."
        else -> "The server returned HTTP $code."
    }

    data class AccountInfo(
        val username: String?,
        val expiryMillis: Long?,
        val maxConnections: Int?,
        val activeConnections: Int?,
        val timezone: String?,
    )
}

// ---- Defensive JSON accessors ------------------------------------------------------------
// Every one of these returns null rather than throwing. See the class doc for why.

private val JsonElement?.jsonObjectOrNull: JsonObject?
    get() = this as? JsonObject

private val JsonElement.arrayOrEmpty: List<JsonElement>
    get() = (this as? JsonArray)?.toList() ?: emptyList()

private val JsonElement?.asPrimitiveOrNull: JsonPrimitive?
    get() = this as? JsonPrimitive

private val JsonElement?.asStringOrNull: String?
    get() = asPrimitiveOrNull?.contentOrNull?.takeIf { it.isNotEmpty() && it != "null" }

private val JsonElement?.asIntOrNull: Int?
    get() = asStringOrNull?.substringBefore('.')?.toIntOrNull()

private val JsonElement?.asLongOrNull: Long?
    get() = asStringOrNull?.substringBefore('.')?.toLongOrNull()

private val JsonElement?.asDoubleOrNull: Double?
    get() = asStringOrNull?.toDoubleOrNull()
