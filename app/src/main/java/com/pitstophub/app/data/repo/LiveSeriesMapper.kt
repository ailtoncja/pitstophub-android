package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryStandings
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.model.StandingsClassGroup
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.remote.ApiEbDriver
import com.pitstophub.app.data.remote.ApiEbTeam
import com.pitstophub.app.data.remote.ApiGtwcTeam
import com.pitstophub.app.data.remote.ApiRace
import com.pitstophub.app.data.remote.ApiStandings
import com.pitstophub.app.data.remote.ApiStandingsClass
import java.time.Instant

/** GT World Challenge (Europe/America/Asia) e Endurance Brasil, via api-motorsports. Porte de gtwc-live.ts e endurance-brasil-live.ts. */
object LiveSeriesMapper {

    val gtwcSeriesByCategory = mapOf(
        "gtwc-europe" to "europe",
        "gtwc-america" to "america",
        "gtwc-asia" to "asia",
    )
    const val ENDURANCE_BRASIL = "endurance-brasil"

    // --- GT World Challenge --------------------------------------------------------------------

    // Vencedores confirmados nos sites oficiais da SRO (a API guarda o time; em Sprint, "time A / time B").
    private val gtwcOfficialWinners = mapOf(
        "europe" to mapOf(
            "circuit-paul-ricard" to "Nicki Thiim",
            "brands-hatch" to "Arthur Leclerc / Bastian Buus",
            "monza" to "Rocco Mazzola",
            "crowdstrike-24-hours-of-spa" to "Bastian Buus",
            "misano" to "Kelvin van der Linde",
            "magny-cours" to "Jules Gounon / Bastian Buus",
        ),
        "america" to mapOf(
            "sonoma-raceway" to "Mikael Grenier",
            "circuit-of-the-americas" to "Ryan Yardley",
            "sebring-international-raceway" to "Robby Foley",
            "road-atlanta" to "Philip Ellis",
        ),
        "asia" to mapOf(
            "sepang" to "Alessandro Ghiretti / Lu Wei",
            "pertamina-mandalika-international-circuit" to "Congfu Cheng / Loek Hartog",
            "fuji-international-speedway" to "Weian Chen / Lu Wei",
        ),
    )

    private val locationPt = mapOf(
        "France" to "França", "Great Britain" to "Reino Unido", "Italy" to "Itália", "Belgium" to "Bélgica",
        "Germany" to "Alemanha", "Netherlands" to "Países Baixos", "Spain" to "Espanha", "Portugal" to "Portugal",
        "United States" to "EUA", "USA" to "EUA", "Canada" to "Canadá", "Malaysia" to "Malásia",
        "Indonesia" to "Indonésia", "Japan" to "Japão", "China" to "China",
    )

    fun gtwcRaces(seriesId: String, api: List<ApiRace>, now: Instant): List<Race> = api.map { race ->
        val official = gtwcOfficialWinners[seriesId]?.get(race.raceId)
            ?.split(Regex("\\s*/\\s*"))?.joinToString(" / ") { TextUtil.toDriverName(it) }
        Race(
            id = race.raceId,
            name = race.name,
            circuit = race.name,
            location = race.location?.let { locationPt[it] ?: it }.orEmpty(),
            enLocation = race.location,
            date = race.date,
            status = if (endOfDay(race.date)?.isBefore(now) == true) RaceStatus.COMPLETED else RaceStatus.UPCOMING,
            winner = official ?: race.winner,
            round = race.round,
        )
    }

    fun gtwcRoster(api: List<ApiGtwcTeam>): Pair<List<Team>, List<Driver>> {
        val teams = ArrayList<Team>()
        val drivers = ArrayList<Driver>()
        val seen = HashSet<String>()
        for (team in api) {
            val teamId = TextUtil.slugify(team.name)
            teams += Team(
                id = teamId,
                name = team.name,
                color = gtwcClassColor(team.raceClass),
                car = team.car,
                raceClass = team.raceClass?.let(TextUtil::toTitleCase),
            )
            for (driver in team.drivers) {
                val driverId = "$teamId-${TextUtil.slugify(driver.name)}"
                if (!seen.add(driverId)) continue
                drivers += Driver(
                    id = driverId,
                    name = TextUtil.toDriverName(driver.name),
                    number = team.carNumber.orEmpty(),
                    nationality = driver.nationality.orEmpty(),
                    teamId = teamId,
                )
            }
        }
        return teams to drivers
    }

    /**
     * So expoe a classificacao quando ha uma unica classe "geral". America so tem classificacao de times
     * dividida em Pro/Pro-Am/Am; misturar as classes numa lista so de posicoes seria enganoso.
     */
    fun gtwcStandings(api: ApiStandings): CategoryStandings? {
        val drivers = singleClass(api.drivers)
        val teams = singleClass(api.teams)
        if (drivers == null && teams == null) return null
        return CategoryStandings(drivers = drivers, teams = teams)
    }

    private fun singleClass(classes: List<ApiStandingsClass>?): List<StandingItem>? {
        if (classes == null || classes.size != 1) return null
        return classes[0].entries.map { StandingItem(it.position, TextUtil.toDriverName(it.name), it.points) }
    }

    private fun gtwcClassColor(className: String?): String {
        val norm = className?.lowercase() ?: return "#2ECC71"
        return when {
            "pro" in norm && "am" !in norm -> "#E63946"
            "silver" in norm -> "#A8A8A8"
            "am" in norm || "bronze" in norm -> "#CD7F32"
            else -> "#2ECC71"
        }
    }

    // --- Endurance Brasil ----------------------------------------------------------------------

    private val classOrder = listOf("P1", "P2", "P2 Light", "P3", "GT3", "GT3L", "GT4", "GT4L")

    fun ebRaces(api: List<ApiRace>): List<Race> = api.map { race ->
        Race(
            id = race.raceId,
            name = race.name,
            enName = translateRaceName(race.name),
            circuit = race.location ?: race.name,
            location = race.location.orEmpty(),
            enLocation = race.location?.let(::translateLocation),
            date = race.date,
            status = if (race.completed == true) RaceStatus.COMPLETED else RaceStatus.UPCOMING,
            winner = race.winner,
            round = race.round,
        )
    }

    fun ebRoster(teams: List<ApiEbTeam>, drivers: List<ApiEbDriver>): Pair<List<Team>, List<Driver>> {
        val classByDriver = drivers.associate { it.driverId to it.raceClass }
        val mappedTeams = teams.map { team ->
            val teamClass = team.drivers.mapNotNull { it.driverId?.let(classByDriver::get) }.firstOrNull()
            Team(
                id = team.teamId,
                name = team.name,
                color = ebClassColor(teamClass),
                raceClass = teamClass,
                principal = team.teamPrincipal,
            )
        }
        val mappedDrivers = drivers.map {
            Driver(id = it.driverId, name = it.name, nationality = "Brasil", teamId = it.teamId.orEmpty())
        }
        return mappedTeams to mappedDrivers
    }

    fun ebStandings(api: ApiStandings, category: Category): CategoryStandings? {
        val teamByDriver = category.drivers.associate { driver ->
            driver.name to category.teams.firstOrNull { it.id == driver.teamId }?.name
        }
        val classes = (api.drivers ?: emptyList())
            .filter { it.entries.isNotEmpty() }
            .sortedBy { classRank(it.classLabel) }
        if (classes.isEmpty()) return null
        return CategoryStandings(
            driverClasses = classes.map { group ->
                StandingsClassGroup(
                    classLabel = group.classLabel,
                    entries = group.entries.map {
                        StandingItem(it.position, it.name, it.points, team = teamByDriver[it.name], extra = it.uf)
                    },
                )
            },
        )
    }

    private fun classRank(label: String): Int = classOrder.indexOf(label).let { if (it == -1) classOrder.size else it }

    private fun ebClassColor(className: String?): String {
        val norm = className.orEmpty().uppercase()
        return when {
            norm == "P1" -> "#E63946"
            norm.startsWith("P2") -> "#F77F00"
            norm == "P3" -> "#F4A261"
            norm.startsWith("GT3") -> "#2A9D8F"
            norm.startsWith("GT4") -> "#E9C46A"
            else -> "#009B3A"
        }
    }

    private fun translateRaceName(name: String): String = name
        .replace(Regex("Etapa\\s+(\\d+)", RegexOption.IGNORE_CASE), "Round $1")
        .replace(Regex("Quatro Horas d[eo]\\s+", RegexOption.IGNORE_CASE), "Four Hours of ")
        .replace(Regex("Tr[eê]s Horas d[eo]\\s+", RegexOption.IGNORE_CASE), "Three Hours of ")
        .replace(Regex("Horas d[eo]\\s+", RegexOption.IGNORE_CASE), "Hours of ")

    private fun translateLocation(location: String): String = location
        .replace(Regex("^Autódromo Internacional de\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Autódromo de\\s+", RegexOption.IGNORE_CASE), "")
        .replace(Regex("^Autódromo\\s+", RegexOption.IGNORE_CASE), "")

    private fun endOfDay(date: String): Instant? = runCatching { Instant.parse("${date}T23:59:59Z") }.getOrNull()
}
