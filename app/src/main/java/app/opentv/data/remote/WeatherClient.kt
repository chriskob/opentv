/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv.data.remote

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Keyless current-weather lookup for the player header, backed by the National
 * Weather Service (api.weather.gov) — open US government data, no API key, just
 * a User-Agent header (see https://www.weather.gov/documentation/services-web-API).
 *
 * A US zip code resolves to coordinates through `api.zippopotam.us` (free,
 * keyless), then coordinates resolve through NWS linked data: `points` gives the
 * observation-stations URL, the first station gives the latest observation with
 * temperature (°C, converted to °F here), an icon URL whose tokens name the
 * condition, and measured precipitation. Everything parses defensively — null on
 * anything unexpected, so the header clock keeps working and the weather chip
 * simply hides.
 *
 * Blocking network work runs on [Dispatchers.IO]; callers stay on the
 * composition-safe pattern used by [XtreamApi].
 */
class WeatherClient(
    private val http: OkHttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {

    /** Station id cache per rounded coordinate, so the 30-minute refresh is one call. */
    private val stationCache = ConcurrentHashMap<String, String>()

    /** Current conditions for one zip code, or null when anything fails. */
    suspend fun currentForZip(zip: String): WeatherNow? = withContext(Dispatchers.IO) {
        val coords = resolveZip(zip) ?: return@withContext null
        fetchCurrent(coords.first, coords.second)
    }

    /** Resolves a 5-digit US zip to latitude/longitude. Null when invalid or offline. */
    suspend fun resolveZip(zip: String): Pair<Double, Double>? = withContext(Dispatchers.IO) {
        val clean = zip.trim()
        if (!isValidZip(clean)) return@withContext null
        runCatching {
            val body = get("https://api.zippopotam.us/us/$clean") ?: return@withContext null
            parseZippopotam(body)
        }.getOrNull()
    }

    /** Current temperature at coordinates. Null on any failure. */
    suspend fun fetchCurrent(lat: Double, lon: Double): WeatherNow? = withContext(Dispatchers.IO) {
        runCatching {
            val key = "%.2f,%.2f".format(lat, lon)
            val stationId = stationCache[key] ?: run {
                val points = get("https://api.weather.gov/points/$lat,$lon")
                    ?: return@withContext null
                val stationsUrl = parsePointsStationsUrl(points) ?: return@withContext null
                val stations = get(stationsUrl) ?: return@withContext null
                val id = parseStationId(stations) ?: return@withContext null
                stationCache[key] = id
                id
            }
            // A cached station can be decommissioned; one retry re-resolves it.
            val obs = get("https://api.weather.gov/stations/$stationId/observations/latest")
                ?: run {
                    stationCache.remove(key)
                    return@withContext null
                }
            parseNwsObservation(obs)
        }.getOrNull()
    }

    /** Blocking GET with the NWS-required User-Agent. Null on any failure. */
    private fun get(url: String): String? {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("Accept", "application/geo+json")
                .header("User-Agent", USER_AGENT)
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string()
            }
        }.getOrNull()
    }

    companion object {
        private val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

        /** NWS asks for an identifying User-Agent (a key may replace this one day). */
        const val USER_AGENT = "OpenTV-AndroidTV (weather header)"

        /**
         * Measured rain below this (mm) still reads as whatever the station reports —
         * trace precipitation shouldn't flip an overcast icon to rain.
         */
        const val RAIN_THRESHOLD_MM = 0.5

        /** True for a 5-digit (optionally ZIP+4) US zip code. */
        fun isValidZip(zip: String): Boolean = zip.trim().matches(Regex("\\d{5}(-\\d{4})?"))

        /** Parses a Zippopotam response body into lat/lon. Null when the shape is unexpected. */
        fun parseZippopotam(body: String): Pair<Double, Double>? {
            return runCatching {
                val root = lenientJson.parseToJsonElement(body).jsonObject
                val place = root["places"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
                val lat = place["latitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
                val lon = place["longitude"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: return null
                lat to lon
            }.getOrNull()
        }

        /** Reads the observation-stations URL out of a `/points` response. */
        fun parsePointsStationsUrl(body: String): String? {
            return runCatching {
                lenientJson.parseToJsonElement(body).jsonObject["properties"]
                    ?.jsonObject?.get("observationStations")
                    ?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        /** Reads the first station id out of an observation-stations response. */
        fun parseStationId(body: String): String? {
            return runCatching {
                val features = lenientJson.parseToJsonElement(body).jsonObject["features"]
                    ?.jsonArray ?: return null
                val props = features.firstOrNull()?.jsonObject
                    ?.get("properties")?.jsonObject ?: return null
                props["stationIdentifier"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }

        /** Parses an NWS latest-observation body into [WeatherNow]. Null when unexpected. */
        fun parseNwsObservation(body: String): WeatherNow? {
            return runCatching {
                val props = lenientJson.parseToJsonElement(body).jsonObject["properties"]
                    ?.jsonObject ?: return null
                val tempC = props["temperature"]?.jsonObject
                    ?.get("value")?.jsonPrimitive?.doubleOrNull ?: return null
                fun precip(key: String): Double =
                    props[key]?.jsonObject?.get("value")?.jsonPrimitive?.doubleOrNull ?: 0.0
                val present = props["presentWeather"]?.jsonArray?.mapNotNull { element ->
                    val obj = element.jsonObjectOrNull() ?: return@mapNotNull element
                        .jsonPrimitiveOrNull()?.contentOrNull
                    obj["rawString"]?.jsonPrimitive?.contentOrNull
                        ?: obj["weather"]?.jsonPrimitive?.contentOrNull
                } ?: emptyList()
                val icon = props["icon"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val description = props["textDescription"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val precipMm = maxOf(precip("precipitationLastHour"), precip("precipitationLast3Hours"))
                WeatherNow(
                    tempF = tempC * 9.0 / 5.0 + 32.0,
                    iconUrl = icon,
                    description = description,
                    presentWeather = present,
                    precipMm = precipMm,
                )
            }.getOrNull()
        }

        /**
         * Buckets NWS conditions for icon choice. Priority: measured rain at or above
         * [RAIN_THRESHOLD_MM], then the station's present-weather codes, then the icon
         * URL tokens, then the text description. Anything unrecognised reads as cloudy.
         */
        fun kindForNws(
            iconUrl: String,
            description: String,
            presentWeather: List<String> = emptyList(),
            precipMm: Double = 0.0,
        ): WeatherKind {
            if (precipMm >= RAIN_THRESHOLD_MM) return WeatherKind.RAIN
            val present = presentWeather.joinToString(" ").uppercase()
            fun has(vararg needles: String, haystack: String = present): Boolean =
                needles.any { it in haystack }
            if (has("TS", "THUNDER")) return WeatherKind.STORM
            if (has("SN", "SG", "PL", "IC", "SNOW", "SLEET", "FLURR", "BLIZZARD")) return WeatherKind.SNOW
            if (has("RA", "DZ", "SH", "RAIN", "DRIZZLE", "SHOWER", "PRECIP")) return WeatherKind.RAIN
            if (has("FG", "BR", "HZ", "FU", "FOG", "MIST", "HAZE", "SMOKE")) return WeatherKind.FOG

            val tokens = iconUrl.substringBefore("?").split("/").lastOrNull()
                .orEmpty().lowercase().split("_", ",").map { token ->
                    // Night variants (nskc, nbkn, …) share the day bucket.
                    if (token.length > 3 && token.startsWith("n") &&
                        token.drop(1) in setOf("skc", "few", "sct", "bkn", "ovc")
                    ) token.drop(1) else token
                }.toSet()
            fun tok(vararg needles: String): Boolean = needles.any { it in tokens }
            if (tok("tsra", "thunderstorm", "tropical")) return WeatherKind.STORM
            if (tok("snow", "sleet", "blizzard", "flurries", "ice")) return WeatherKind.SNOW
            if (tok("fzra")) return WeatherKind.RAIN
            if (tok("rain", "drizzle", "showers")) return WeatherKind.RAIN
            if (tok("fog", "haze", "smoke", "dust")) return WeatherKind.FOG
            if (tok("skc", "few", "fair", "hot", "cold")) return WeatherKind.CLEAR

            val desc = description.uppercase()
            if (has("THUNDER", haystack = desc)) return WeatherKind.STORM
            if (has("SNOW", "SLEET", "FLURR", "BLIZZARD", haystack = desc)) return WeatherKind.SNOW
            if (has("RAIN", "SHOWER", "DRIZZLE", "PRECIP", haystack = desc)) return WeatherKind.RAIN
            if (has("FOG", "MIST", "HAZE", "SMOKE", haystack = desc)) return WeatherKind.FOG
            if (has("CLEAR", "SUNNY", "FAIR", haystack = desc)) return WeatherKind.CLEAR
            return WeatherKind.CLOUDY
        }

        private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
            runCatching { jsonObject }.getOrNull()

        private fun kotlinx.serialization.json.JsonElement.jsonPrimitiveOrNull() =
            runCatching { jsonPrimitive }.getOrNull()
    }
}

/** Current conditions snapshot shown in the player header. */
data class WeatherNow(
    val tempF: Double,
    /** Raw NWS icon URL; its tokens name the condition (…/day/rain_showers?size=…). */
    val iconUrl: String = "",
    /** Station text description, e.g. "Light Rain". */
    val description: String = "",
    /** Station present-weather codes/words, e.g. ["RA", "BR"]. */
    val presentWeather: List<String> = emptyList(),
    /** Measured precipitation in mm (0 when dry or unreported). */
    val precipMm: Double = 0.0,
) {
    /** Rounded display string, e.g. "72°". */
    val display: String get() = "${kotlin.math.round(tempF).toInt()}°"
    val kind: WeatherKind get() = WeatherClient.kindForNws(iconUrl, description, presentWeather, precipMm)
}

/** Icon bucket for conditions. */
enum class WeatherKind { CLEAR, CLOUDY, FOG, RAIN, SNOW, STORM }
