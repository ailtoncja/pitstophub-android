package com.pitstophub.app.data.remote

import com.pitstophub.app.domain.RemoteCategoryRules
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

// api-motorsports: API propria (repositorio ailtoncja/api-motorsports) que raspa os sites oficiais e
// tambem entrega o pacote agregado /bootstrap para as 15 categorias nao-F1. Mesmas rotas que o site usa.

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

// NASCAR Brasil e Porsche Cup Brasil: a api-motorsports publica o que o motor do Supabase ja le das
// fontes oficiais (CBA e planilha oficial da Porsche Cup). O status vem pronto, e a classificacao
// pode ter mais de uma divisao (a Porsche Cup tem cinco).
@Serializable
data class ApiBrasilRace(
    val raceId: String,
    val round: Int? = null,
    val name: String,
    val enName: String? = null,
    val location: String? = null,
    val enLocation: String? = null,
    val circuit: String? = null,
    val date: String,
    val startsAt: String? = null,
    val status: String = "upcoming",
    val winner: String? = null,
)

@Serializable
data class ApiBrasilStandingEntry(val position: Int, val name: String, val team: String? = null, val points: Double)

@Serializable
data class ApiBrasilStandingsClass(val classLabel: String, val entries: List<ApiBrasilStandingEntry> = emptyList())

@Serializable
data class ApiBrasilStandings(
    val season: Int? = null,
    val asOfRound: Int? = null,
    val drivers: List<ApiBrasilStandingsClass>? = null,
    val teams: List<ApiBrasilStandingsClass>? = null,
)

@Serializable
data class ApiBrasilDriver(
    val driverId: String,
    val name: String,
    val number: String? = null,
    val team: String? = null,
    val classes: List<String> = emptyList(),
)

@Serializable
data class ApiBootstrapGtwcData(
    val races: List<ApiRace> = emptyList(),
    val teams: List<ApiGtwcTeam> = emptyList(),
    val standings: ApiStandings = ApiStandings(),
)

@Serializable
data class ApiBootstrapEbData(
    val races: List<ApiRace> = emptyList(),
    val teams: List<ApiEbTeam> = emptyList(),
    val drivers: List<ApiEbDriver> = emptyList(),
    val standings: ApiStandings = ApiStandings(),
)

@Serializable
data class ApiBootstrapCategoryData(
    val id: String = "",
    val name: String = "",
    val season: Int? = null,
    val lastUpdate: String? = null,
    val asOfRound: Int? = null,
    val degraded: Boolean = false,
    val races: List<ApiBrasilRace> = emptyList(),
    val standings: ApiBrasilStandings = ApiBrasilStandings(),
    val drivers: List<ApiBrasilDriver> = emptyList(),
    val rules: RemoteCategoryRules? = null,
)

@Serializable
data class ApiBootstrap(
    val gtwc: Map<String, ApiBootstrapGtwcData?> = emptyMap(),
    val enduranceBrasil: ApiBootstrapEbData? = null,
    val categories: Map<String, ApiBootstrapCategoryData?> = emptyMap(),
    val rulesByCategory: Map<String, RemoteCategoryRules?> = emptyMap(),
)

@Serializable
data class ApiEngineResultRow(
    val position: Int? = null,
    val driver: String,
    val team: String? = null,
    val number: String? = null,
    val points: Double? = null,
    val time: String? = null,
    val laps: Int? = null,
    val status: String? = null,
)

@Serializable
data class ApiEngineSession(
    val session: String,
    val official: Boolean = true,
    val rows: List<ApiEngineResultRow> = emptyList(),
)

@Serializable
data class ApiEngineRaceDetail(
    val sessions: List<ApiEngineSession> = emptyList(),
)

class MotorsportsClient(private val http: HttpFetcher, private val baseUrl: String = BASE_URL) {

    suspend fun bootstrap(): ApiBootstrap = one("/bootstrap")

    suspend fun gtwcRaces(seriesId: String): List<ApiRace> = list("/series/$seriesId/races")
    suspend fun gtwcTeams(seriesId: String): List<ApiGtwcTeam> = list("/series/$seriesId/teams")
    suspend fun gtwcStandings(seriesId: String): ApiStandings = one("/series/$seriesId/standings")

    suspend fun ebRaces(): List<ApiRace> = list("/endurance-brasil/races")
    suspend fun ebTeams(): List<ApiEbTeam> = list("/endurance-brasil/teams")
    suspend fun ebDrivers(): List<ApiEbDriver> = list("/endurance-brasil/drivers")
    suspend fun ebStandings(): ApiStandings = one("/endurance-brasil/standings")

    suspend fun brasilRaces(categoryId: String): List<ApiBrasilRace> = list("/$categoryId/races")
    suspend fun brasilDrivers(categoryId: String): List<ApiBrasilDriver> = list("/$categoryId/drivers")
    suspend fun brasilStandings(categoryId: String): ApiBrasilStandings = one("/$categoryId/standings")

    suspend fun engineRaceDetail(categoryId: String, raceId: String): ApiEngineRaceDetail =
        one("/$categoryId/races/$raceId")

    private suspend inline fun <reified T> list(path: String): List<T> =
        AppJson.decodeFromString<List<T>>(http.getString(baseUrl + path))

    private suspend inline fun <reified T> one(path: String): T =
        AppJson.decodeFromString<T>(http.getString(baseUrl + path))

    companion object {
        const val BASE_URL = "https://api-motorsports.vercel.app"
    }
}
