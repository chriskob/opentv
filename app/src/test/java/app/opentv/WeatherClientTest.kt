/*
 * This file is part of OpenTV.
 * Copyright (C) 2026 The OpenTV Contributors
 * Licensed under the GNU General Public License v3.0 or later.
 */
package app.opentv

import app.opentv.data.remote.WeatherClient
import app.opentv.data.remote.WeatherKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Keyless NWS weather parsing: shapes taken from api.weather.gov responses.
 */
class WeatherClientTest {

    @Test
    fun `accepts 5-digit zips and rejects the rest`() {
        assertThat(WeatherClient.isValidZip("90210")).isTrue()
        assertThat(WeatherClient.isValidZip("90210-1234")).isTrue()
        assertThat(WeatherClient.isValidZip("9021")).isFalse()
        assertThat(WeatherClient.isValidZip("ABCDE")).isFalse()
        assertThat(WeatherClient.isValidZip("")).isFalse()
    }

    @Test
    fun `parses zippopotam coordinates`() {
        val body = """{"post code":"90210","places":[{"latitude":"34.0901","longitude":"-118.4065"}]}"""

        assertThat(WeatherClient.parseZippopotam(body)).isEqualTo(34.0901 to -118.4065)
    }

    @Test
    fun `returns null for malformed zippopotam body`() {
        assertThat(WeatherClient.parseZippopotam("""{"places":[]}""")).isNull()
        assertThat(WeatherClient.parseZippopotam("not json")).isNull()
    }

    @Test
    fun `reads stations url from points response`() {
        val body = """{"properties":{"observationStations":"https://api.weather.gov/stations?x=1"}}"""

        assertThat(WeatherClient.parsePointsStationsUrl(body))
            .isEqualTo("https://api.weather.gov/stations?x=1")
    }

    @Test
    fun `returns null when points body lacks stations`() {
        assertThat(WeatherClient.parsePointsStationsUrl("""{}""")).isNull()
        assertThat(WeatherClient.parsePointsStationsUrl("nope")).isNull()
    }

    @Test
    fun `reads first station id`() {
        val body = """{"features":[{"properties":{"stationIdentifier":"KNYC"}}]}"""

        assertThat(WeatherClient.parseStationId(body)).isEqualTo("KNYC")
    }

    @Test
    fun `returns null when no stations listed`() {
        assertThat(WeatherClient.parseStationId("""{"features":[]}""")).isNull()
    }

    @Test
    fun `parses observation temperature and converts to fahrenheit`() {
        val body = observation(tempC = 22.0, icon = "ovc", description = "Cloudy")

        val now = WeatherClient.parseNwsObservation(body)!!
        assertThat(now.tempF).isWithin(0.01).of(71.6)
        assertThat(now.display).isEqualTo("72°")
        assertThat(now.kind).isEqualTo(WeatherKind.CLOUDY)
    }

    @Test
    fun `returns null when temperature missing`() {
        assertThat(WeatherClient.parseNwsObservation("""{"properties":{}}""")).isNull()
        assertThat(WeatherClient.parseNwsObservation("nope")).isNull()
    }

    @Test
    fun `icon tokens bucket conditions`() {
        assertThat(kind(icon = "skc")).isEqualTo(WeatherKind.CLEAR)
        assertThat(kind(icon = "nskc", description = "Clear")).isEqualTo(WeatherKind.CLEAR)
        assertThat(kind(icon = "few", description = "Sunny")).isEqualTo(WeatherKind.CLEAR)
        assertThat(kind(icon = "ovc")).isEqualTo(WeatherKind.CLOUDY)
        assertThat(kind(icon = "nbkn", description = "Mostly Cloudy")).isEqualTo(WeatherKind.CLOUDY)
        assertThat(kind(icon = "fog")).isEqualTo(WeatherKind.FOG)
        assertThat(kind(icon = "rain_showers", description = "Showers")).isEqualTo(WeatherKind.RAIN)
        assertThat(kind(icon = "snow", description = "Snow")).isEqualTo(WeatherKind.SNOW)
        assertThat(kind(icon = "tsra", description = "Thunderstorms")).isEqualTo(WeatherKind.STORM)
        assertThat(kind(icon = "mystery")).isEqualTo(WeatherKind.CLOUDY)
    }

    @Test
    fun `present weather codes bucket conditions`() {
        assertThat(kind(present = listOf("RA"))).isEqualTo(WeatherKind.RAIN)
        assertThat(kind(present = listOf("TS"))).isEqualTo(WeatherKind.STORM)
        assertThat(kind(present = listOf("SN"))).isEqualTo(WeatherKind.SNOW)
        assertThat(kind(present = listOf("FG"))).isEqualTo(WeatherKind.FOG)
    }

    @Test
    fun `measured rain at threshold overrides a cloudy report`() {
        val body = observation(tempC = 20.0, icon = "ovc", description = "Cloudy", precipLastHour = 0.6)

        assertThat(WeatherClient.parseNwsObservation(body)!!.kind).isEqualTo(WeatherKind.RAIN)
    }

    @Test
    fun `trace precip below threshold keeps the reported icon`() {
        val body = observation(tempC = 20.0, icon = "ovc", description = "Cloudy", precipLastHour = 0.2)

        assertThat(WeatherClient.parseNwsObservation(body)!!.kind).isEqualTo(WeatherKind.CLOUDY)
    }

    private fun kind(
        icon: String = "ovc",
        description: String = "",
        present: List<String> = emptyList(),
        precipMm: Double = 0.0,
    ): WeatherKind = WeatherClient.kindForNws(
        iconUrl = "https://api.weather.gov/icons/land/day/$icon?size=medium",
        description = description,
        presentWeather = present,
        precipMm = precipMm,
    )

    private fun observation(
        tempC: Double,
        icon: String,
        description: String,
        precipLastHour: Double? = null,
    ): String = """{"properties":{
        "textDescription":"$description",
        "icon":"https://api.weather.gov/icons/land/day/$icon?size=medium",
        "presentWeather":[],
        "temperature":{"unitCode":"wmoUnit:degC","value":$tempC},
        "precipitationLastHour":{"unitCode":"wmoUnit:mm","value":${precipLastHour ?: "null"}}
    }}"""
}
