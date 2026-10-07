package com.pitstophub.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

// api-motorsports: API propria (repositorio ailtoncja/api-motorsports) que raspa os sites oficiais do
// GT World Challenge (Europe/America/Asia) e do Endurance Brasil. Mesmas rotas que o PWA usa.

@Serializable
data class ApiRace(
    val seriesId: String? = null,
    val raceId: String,
    val round: Int? = null,
    val name: String,
    val location: String? = null,
    val date: String,
    val sourceUrl: String? = null,
    /** So o Endurance Brasil informa; no GTWC o status vem da data. */
    val completed: Boolean? = null,
    val winner: String? = null,
)

@Serializable
data class ApiPerson(val name: String, val nationality: String? = null, val driverId: String? = null)

/** Time do GTWC: carro, classe e numero vem juntos. */
@Serializable
data class ApiGtwcTeam(
    val name: String,
    val car: String? = null,
    @SerialName("class") val raceClass: String? = null,
    val carNumber: String? = null,
    val drivers: List<ApiPerson> = emptyList(),
)

@Serializable
data class ApiEbTeam(
    val teamId: String,
    val name: String,
    val teamPrincipal: String? = null,
    val drivers: List<ApiPerson> = emptyList(),
)

@Serializable
data class ApiEbDriver(
    val driverId: String,
    val name: String,
    val uf: String? = null,
    @SerialName("class") val raceClass: String? = null,
    val teamId: String? = null,
)

@Serializable
data class ApiStandingEntry(val position: Int, val name: String, val points: Double, val uf: String? = null)

@Serializable
data class ApiStandingsClass(val classLabel: String, val entries: List<ApiStandingEntry> = emptyList())

@Serializable
data class ApiStandings(
    val drivers: List<ApiStandingsClass>? = null,
    val teams: List<ApiStandingsClass>? = null,
)

class MotorsportsClient(private val http: HttpFetcher, private val baseUrl: String = BASE_URL) {

    suspend fun gtwcRaces(seriesId: String): List<ApiRace> = list("/series/$seriesId/races")
    suspend fun gtwcTeams(seriesId: String): List<ApiGtwcTeam> = list("/series/$seriesId/teams")
    suspend fun gtwcStandings(seriesId: String): ApiStandings = one("/series/$seriesId/standings")

    suspend fun ebRaces(): List<ApiRace> = list("/endurance-brasil/races")
    suspend fun ebTeams(): List<ApiEbTeam> = list("/endurance-brasil/teams")
    suspend fun ebDrivers(): List<ApiEbDriver> = list("/endurance-brasil/drivers")
    suspend fun ebStandings(): ApiStandings = one("/endurance-brasil/standings")

    private suspend inline fun <reified T> list(path: String): List<T> =
        AppJson.decodeFromString<List<T>>(http.getString(baseUrl + path))

    private suspend inline fun <reified T> one(path: String): T =
        AppJson.decodeFromString<T>(http.getString(baseUrl + path))

    companion object {
        const val BASE_URL = "https://api-motorsports.vercel.app"
    }
}
