package com.pitstophub.app.data.remote

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

// DTOs da Jolpica (compativel com a Ergast). Mesma API que o PWA usa em src/jolpica.ts.

@Serializable
data class JSession(val date: String, val time: String? = null)

@Serializable
data class JLocation(val locality: String = "", val country: String = "", val lat: String? = null, @SerialName("long") val lng: String? = null)

@Serializable
data class JCircuit(
    val circuitId: String = "",
    val circuitName: String = "",
    @SerialName("Location") val location: JLocation = JLocation(),
)

@Serializable
data class JDriver(
    val driverId: String = "",
    val permanentNumber: String? = null,
    val code: String? = null,
    val givenName: String = "",
    val familyName: String = "",
    val nationality: String? = null,
) {
    val fullName: String get() = "$givenName $familyName".trim()
}

@Serializable
data class JConstructor(val constructorId: String = "", val name: String = "")

@Serializable
data class JTime(val millis: String? = null, val time: String? = null)

@Serializable
data class JFastestLap(val rank: String? = null, @SerialName("Time") val time: JTime? = null)

@Serializable
data class JResult(
    val number: String? = null,
    val position: String = "",
    val positionText: String? = null,
    val points: String = "0",
    @SerialName("Driver") val driver: JDriver = JDriver(),
    @SerialName("Constructor") val constructor: JConstructor = JConstructor(),
    val grid: String? = null,
    val laps: String? = null,
    val status: String? = null,
    @SerialName("Time") val time: JTime? = null,
    @SerialName("FastestLap") val fastestLap: JFastestLap? = null,
)

@Serializable
data class JRace(
    val season: String? = null,
    val round: String = "",
    val raceName: String = "",
    @SerialName("Circuit") val circuit: JCircuit = JCircuit(),
    val date: String = "",
    val time: String? = null,
    @SerialName("FirstPractice") val firstPractice: JSession? = null,
    @SerialName("SecondPractice") val secondPractice: JSession? = null,
    @SerialName("ThirdPractice") val thirdPractice: JSession? = null,
    @SerialName("Qualifying") val qualifying: JSession? = null,
    @SerialName("Sprint") val sprint: JSession? = null,
    @SerialName("SprintQualifying") val sprintQualifying: JSession? = null,
    @SerialName("SprintShootout") val sprintShootout: JSession? = null,
    @SerialName("Results") val results: List<JResult>? = null,
    @SerialName("SprintResults") val sprintResults: List<JResult>? = null,
)

@Serializable
data class JDriverStanding(
    val position: String = "",
    val points: String = "0",
    val wins: String? = null,
    @SerialName("Driver") val driver: JDriver = JDriver(),
    @SerialName("Constructors") val constructors: List<JConstructor> = emptyList(),
)

@Serializable
data class JConstructorStanding(
    val position: String = "",
    val points: String = "0",
    val wins: String? = null,
    @SerialName("Constructor") val constructor: JConstructor = JConstructor(),
)

@Serializable
private data class JRaceTable(@SerialName("Races") val races: List<JRace> = emptyList())

@Serializable
private data class JRacesMR(@SerialName("RaceTable") val table: JRaceTable = JRaceTable())

@Serializable
private data class JRacesRoot(@SerialName("MRData") val mr: JRacesMR = JRacesMR())

@Serializable
private data class JStandingsList(
    @SerialName("DriverStandings") val driverStandings: List<JDriverStanding> = emptyList(),
    @SerialName("ConstructorStandings") val constructorStandings: List<JConstructorStanding> = emptyList(),
)

@Serializable
private data class JStandingsTable(@SerialName("StandingsLists") val lists: List<JStandingsList> = emptyList())

@Serializable
private data class JStandingsMR(@SerialName("StandingsTable") val table: JStandingsTable = JStandingsTable())

@Serializable
private data class JStandingsRoot(@SerialName("MRData") val mr: JStandingsMR = JStandingsMR())

class JolpicaClient(private val http: HttpFetcher, private val baseUrl: String = BASE_URL) {

    suspend fun races(year: String): List<JRace> =
        parseRaces(http.getString("$baseUrl/$year/races.json?limit=100"))

    /** Vencedor de cada rodada (chave: numero da rodada). */
    suspend fun winners(year: String): Map<String, String> =
        parseWinners(http.getString("$baseUrl/$year/results/1.json?limit=100"))

    suspend fun driverStandings(year: String): List<JDriverStanding> =
        parseStandings(http.getString("$baseUrl/$year/driverStandings.json")).firstOrNull()?.driverStandings.orEmpty()

    suspend fun constructorStandings(year: String): List<JConstructorStanding> =
        parseStandings(http.getString("$baseUrl/$year/constructorStandings.json")).firstOrNull()?.constructorStandings.orEmpty()

    /** Resultado completo de uma corrida (classificacao final). */
    suspend fun raceResults(year: String, round: Int): List<JResult> =
        parseRaces(http.getString("$baseUrl/$year/$round/results.json?limit=100")).firstOrNull()?.results.orEmpty()

    /** Resultado de cada etapa de um piloto na temporada (uma linha por corrida). */
    suspend fun driverSeasonResults(driverId: String, year: String): List<JRace> =
        parseRaces(http.getString("$baseUrl/$year/drivers/${jolpicaDriverId(driverId)}/results.json?limit=100"))

    /** Classificacao do campeonato logo apos uma rodada (para as setas de subida/queda). */
    suspend fun standingsAfterRound(year: String, round: Int): Pair<List<JDriverStanding>, List<JConstructorStanding>> = coroutineScope {
        val drivers = async { parseStandings(http.getString("$baseUrl/$year/$round/driverStandings.json")).firstOrNull()?.driverStandings.orEmpty() }
        val constructors = async { runCatching { parseStandings(http.getString("$baseUrl/$year/$round/constructorStandings.json")).firstOrNull()?.constructorStandings.orEmpty() }.getOrDefault(emptyList()) }
        drivers.await() to constructors.await()
    }

    /** Corridas de uma equipe na temporada (com os pontos de cada piloto), mais as sprints. */
    suspend fun constructorSeason(constructorId: String, year: String): Pair<List<JRace>, List<JRace>> = coroutineScope {
        val race = async { parseRaces(http.getString("$baseUrl/$year/constructors/$constructorId/results.json?limit=100")) }
        val sprint = async { runCatching { parseRaces(http.getString("$baseUrl/$year/constructors/$constructorId/sprint.json?limit=100")) }.getOrDefault(emptyList()) }
        race.await() to sprint.await()
    }

    companion object {
        const val BASE_URL = "https://api.jolpi.ca/ergast/f1"

        /** Ids do catalogo que a Jolpica escreve diferente. */
        private val driverIdOverrides = mapOf("verstappen" to "max_verstappen", "lindblad" to "arvid_lindblad")

        internal fun jolpicaDriverId(driverId: String): String = driverIdOverrides[driverId] ?: driverId

        internal fun parseRaces(json: String): List<JRace> =
            AppJson.decodeFromString<JRacesRoot>(json).mr.table.races

        internal fun parseWinners(json: String): Map<String, String> {
            val map = linkedMapOf<String, String>()
            for (race in parseRaces(json)) {
                val first = race.results?.firstOrNull() ?: continue
                if (first.position == "1") map[race.round] = first.driver.fullName
            }
            return map
        }

        private fun parseStandings(json: String): List<JStandingsList> =
            AppJson.decodeFromString<JStandingsRoot>(json).mr.table.lists
    }
}
