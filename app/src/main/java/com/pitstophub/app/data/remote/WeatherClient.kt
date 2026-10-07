package com.pitstophub.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class WeatherCondition { CLEAR, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class RaceWeather(
    val temperatureMax: Double,
    val temperatureMin: Double,
    /** Chance de chuva em porcentagem (0..100). */
    val rainChance: Int,
    val windKmh: Double,
    val condition: WeatherCondition,
)

@Serializable
private data class OpenMeteoDaily(
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
    @SerialName("precipitation_probability_max") val precipitation: List<Double?> = emptyList(),
    @SerialName("wind_speed_10m_max") val wind: List<Double?> = emptyList(),
    @SerialName("weather_code") val code: List<Int?> = emptyList(),
)

@Serializable
private data class OpenMeteoResponse(val daily: OpenMeteoDaily? = null)

/** Previsao do dia da corrida (Open-Meteo: gratuito, sem chave). Mesmos criterios de `src/lib/weather.ts` do PWA. */
class WeatherClient(private val http: HttpFetcher) {

    suspend fun forecast(lat: Double, lng: Double, date: String): RaceWeather? {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lng" +
            "&daily=temperature_2m_max,temperature_2m_min,precipitation_probability_max,wind_speed_10m_max,weather_code" +
            "&timezone=auto&start_date=$date&end_date=$date"
        return parse(http.getString(url))
    }

    companion object {
        /** A previsao so existe de ontem ate ~15 dias a frente. */
        fun windowOpen(date: String, now: Instant): Boolean {
            val day = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
            val days = ChronoUnit.DAYS.between(now.atZone(java.time.ZoneOffset.UTC).toLocalDate(), day)
            return days >= -1 && days <= 15
        }

        internal fun parse(json: String): RaceWeather? {
            val daily = AppJson.decodeFromString<OpenMeteoResponse>(json).daily ?: return null
            val max = daily.temperatureMax.firstOrNull() ?: return null
            return RaceWeather(
                temperatureMax = max,
                temperatureMin = daily.temperatureMin.firstOrNull() ?: max,
                rainChance = daily.precipitation.firstOrNull()?.toInt() ?: 0,
                windKmh = daily.wind.firstOrNull() ?: 0.0,
                condition = conditionOf(daily.code.firstOrNull() ?: 3),
            )
        }

        /** Codigo WMO simplificado. */
        fun conditionOf(code: Int): WeatherCondition = when (code) {
            0, 1 -> WeatherCondition.CLEAR
            2, 3 -> WeatherCondition.CLOUDY
            45, 48 -> WeatherCondition.FOG
            in 51..57 -> WeatherCondition.DRIZZLE
            in 61..67, in 80..82 -> WeatherCondition.RAIN
            in 71..77, 85, 86 -> WeatherCondition.SNOW
            in 95..99 -> WeatherCondition.STORM
            else -> WeatherCondition.CLOUDY
        }
    }
}
