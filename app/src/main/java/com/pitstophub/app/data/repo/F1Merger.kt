package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryStandings
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.SessionKind
import com.pitstophub.app.data.model.SessionSlot
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.remote.JConstructorStanding
import com.pitstophub.app.data.remote.JDriverStanding
import com.pitstophub.app.data.remote.JRace
import com.pitstophub.app.data.remote.JSession
import java.time.Instant

/**
 * Combina o catalogo estatico da F1 com a Jolpica. Porte direto de `src/jolpica.ts` do PWA: o catalogo
 * guarda nomes em portugues, fotos e cores; a Jolpica manda datas, vencedores, numeros e pontos.
 */
object F1Merger {

    private val constructorToTeam = mapOf(
        "mercedes" to "mercedes",
        "ferrari" to "ferrari",
        "mclaren" to "mclaren",
        "red_bull" to "redbull",
        "haas" to "haas",
        "alpine" to "alpine",
        "alphatauri" to "rb",
        "racing_bulls" to "rb",
        "rb" to "rb",
        "sauber" to "audi",
        "kick_sauber" to "audi",
        "audi" to "audi",
        "williams" to "williams",
        "andretti" to "cadillac",
        "cadillac" to "cadillac",
        "aston_martin" to "astonmartin",
    )

    fun merge(
        base: Category,
        races: List<JRace>,
        winners: Map<String, String>,
        driverStandings: List<JDriverStanding>,
        constructorStandings: List<JConstructorStanding>,
        now: Instant,
    ): Category {
        val calendar = buildCalendar(base, races, winners, now)
        val liveDrivers = buildDrivers(base, driverStandings)
        val drivers = if (liveDrivers.isNotEmpty()) mergeDrivers(base.drivers, liveDrivers) else base.drivers
        val standings = buildStandings(base, driverStandings, constructorStandings)
        return base.copy(
            drivers = drivers,
            calendar = if (calendar.isNotEmpty()) normalizeWinners(calendar, drivers) else base.calendar,
            standings = standings ?: base.standings,
        )
    }

    // --- calendario ----------------------------------------------------------------------------

    internal fun buildCalendar(base: Category, races: List<JRace>, winners: Map<String, String>, now: Instant): List<Race> =
        races.map { race ->
            val match = matchBaseRace(base.calendar, race)
            // Uma etapa local casada so pelo nome (data diferente) pode ser de outro lugar -- ex.: o "Bahrain Grand
            // Prix in Malaysia" de 2026 casa com o GP do Bahrein de Sakhir. Nesse caso o local (pista/cidade) vem
            // da Jolpica, que sabe onde a prova acontece.
            val venue = match?.takeIf { it.date == race.date || sameVenue(it, race) }
            val status = raceStatus(race, winners.containsKey(race.round), now)
            Race(
                id = match?.id ?: TextUtil.slugify(race.raceName),
                name = match?.name ?: formatPt(race.raceName),
                enName = match?.enName ?: race.raceName,
                location = venue?.location ?: race.circuit.location.locality,
                enLocation = venue?.enLocation ?: race.circuit.location.locality,
                date = race.date,
                startsAt = isoInstant(race.date, race.time),
                circuit = venue?.circuit ?: race.circuit.circuitName,
                status = status,
                winner = winners[race.round] ?: if (status == RaceStatus.COMPLETED) match?.winner else null,
                round = race.round.toIntOrNull(),
                sessions = sessionsOf(race),
                lat = race.circuit.location.lat?.toDoubleOrNull(),
                lng = race.circuit.location.lng?.toDoubleOrNull(),
                country = race.circuit.location.country.ifBlank { null },
            )
        }

    /** A etapa local e a da Jolpica citam o mesmo lugar (cidade/pais/pista)? */
    private fun sameVenue(base: Race, race: JRace): Boolean {
        val live = tokens(race.circuit.location.locality, race.circuit.location.country, race.circuit.circuitName)
        return tokens(base.location, base.enLocation).any { it in live }
    }

    private fun raceStatus(race: JRace, hasResult: Boolean, now: Instant): RaceStatus {
        if (hasResult) return RaceStatus.COMPLETED
        val endOfDay = runCatching { Instant.parse("${race.date}T23:59:59Z") }.getOrNull() ?: return RaceStatus.UPCOMING
        return if (endOfDay.isBefore(now)) RaceStatus.COMPLETED else RaceStatus.UPCOMING
    }

    internal fun isoInstant(date: String, time: String?): String? {
        if (time.isNullOrBlank()) return null
        val hasZone = time.endsWith("Z") || Regex("[+-]\\d{2}(:?\\d{2})?$").containsMatchIn(time)
        return runCatching { Instant.parse(if (hasZone) "${date}T$time" else "${date}T${time}Z").toString() }.getOrNull()
    }

    private fun sessionsOf(race: JRace): List<SessionSlot> {
        fun slot(kind: SessionKind, session: JSession?): SessionSlot? =
            session?.let { isoInstant(it.date, it.time) }?.let { SessionSlot(kind, it) }

        return listOfNotNull(
            slot(SessionKind.FP1, race.firstPractice),
            slot(SessionKind.FP2, race.secondPractice),
            slot(SessionKind.FP3, race.thirdPractice),
            slot(SessionKind.SPRINT_QUALIFYING, race.sprintQualifying ?: race.sprintShootout),
            slot(SessionKind.SPRINT, race.sprint),
            slot(SessionKind.QUALIFYING, race.qualifying),
            slot(SessionKind.RACE, JSession(race.date, race.time)),
        ).sortedBy { it.startsAt }
    }

    private fun matchBaseRace(baseCalendar: List<Race>, race: JRace): Race? {
        baseCalendar.firstOrNull { it.date == race.date }?.let { return it }
        val raceTokens = tokens(race.raceName, race.circuit.location.locality, race.circuit.location.country)
        var best: Race? = null
        var bestScore = 0
        for (candidate in baseCalendar) {
            val baseTokens = tokens(candidate.name, candidate.enName, candidate.location, candidate.enLocation)
            val score = raceTokens.count { it in baseTokens }
            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return if (bestScore >= 1) best else null
    }

    private val stopWords = setOf("gp", "grand", "prix", "de", "do", "da", "the", "of", "formula")

    private fun tokens(vararg values: String?): Set<String> =
        values.filterNotNull().flatMap { TextUtil.normalize(it).split(' ') }
            .filter { it.length >= 3 && it !in stopWords }.toSet()

    private fun formatPt(raceName: String): String =
        Regex("^(.+?) Grand Prix$", RegexOption.IGNORE_CASE).replace(raceName) { "GP de ${it.groupValues[1]}" }

    // --- pilotos e equipes ---------------------------------------------------------------------

    internal fun findDriver(drivers: List<Driver>, fullName: String): Driver? = DriverLookup.find(drivers, fullName)

    private fun buildDrivers(base: Category, standings: List<JDriverStanding>): List<Driver> =
        standings.map { entry ->
            val fullName = entry.driver.fullName
            val found = findDriver(base.drivers, fullName)
            val constructor = entry.constructors.firstOrNull()
            Driver(
                id = found?.id ?: TextUtil.slugify(fullName),
                name = found?.name ?: TextUtil.toTitleCase(fullName),
                number = entry.driver.permanentNumber ?: found?.number ?: "--",
                nationality = found?.nationality ?: "",
                teamId = resolveTeamId(constructor?.constructorId.orEmpty(), constructor?.name.orEmpty(), base.teams, found?.teamId),
                image = found?.image,
                cutout = found?.cutout,
            )
        }

    private fun mergeDrivers(base: List<Driver>, live: List<Driver>): List<Driver> {
        val merged = LinkedHashMap<String, Driver>()
        base.forEach { merged[it.id] = it }
        for (driver in live) {
            val found = findDriver(base, driver.name)
            if (found != null) {
                merged[found.id] = found.copy(
                    number = driver.number.takeUnless { it == "--" || it.isBlank() } ?: found.number,
                    teamId = driver.teamId,
                )
            } else {
                merged[driver.id] = driver
            }
        }
        return merged.values.toList()
    }

    private fun resolveTeamId(constructorId: String, constructorName: String, teams: List<Team>, fallback: String?): String {
        constructorToTeam[constructorId.lowercase()]?.let { return it }
        val normalized = TextUtil.normalize(constructorName)
        if (normalized.isNotEmpty()) {
            teams.firstOrNull {
                val name = TextUtil.normalize(it.name)
                name == normalized || name.contains(normalized) || normalized.contains(name)
            }?.let { return it.id }
        }
        return fallback ?: TextUtil.slugify(constructorName.ifEmpty { constructorId.ifEmpty { "team" } })
    }

    /** Ids de construtor da Jolpica que correspondem a uma equipe do catalogo (a Sauber virou Audi, a RB mudou de nome). */
    internal fun constructorIds(teamId: String): List<String> = constructorToTeam.filterValues { it == teamId }.keys.toList()

    /** Nome canonico da equipe (do catalogo) a partir do construtor da Jolpica. */
    private fun resolveTeamName(constructorId: String, fallbackName: String, teams: List<Team>): String {
        val id = constructorToTeam[constructorId.lowercase()]
        return teams.firstOrNull { it.id == id }?.name ?: fallbackName
    }

    internal fun buildStandings(
        base: Category,
        drivers: List<JDriverStanding>,
        constructors: List<JConstructorStanding>,
    ): CategoryStandings? {
        val driverItems = drivers.mapNotNull { entry ->
            val position = entry.position.toIntOrNull() ?: return@mapNotNull null
            val points = entry.points.toDoubleOrNull() ?: return@mapNotNull null
            val constructor = entry.constructors.firstOrNull()
            StandingItem(
                position = position,
                name = findDriver(base.drivers, entry.driver.fullName)?.name ?: entry.driver.fullName,
                points = points,
                team = constructor?.let { resolveTeamName(it.constructorId, it.name, base.teams) },
            )
        }
        val constructorItems = constructors.mapNotNull { entry ->
            val position = entry.position.toIntOrNull() ?: return@mapNotNull null
            val points = entry.points.toDoubleOrNull() ?: return@mapNotNull null
            StandingItem(
                position = position,
                name = resolveTeamName(entry.constructor.constructorId, entry.constructor.name, base.teams),
                points = points,
            )
        }
        if (driverItems.isEmpty() && constructorItems.isEmpty()) return null
        return CategoryStandings(
            drivers = driverItems.takeIf { it.isNotEmpty() },
            constructors = constructorItems.takeIf { it.isNotEmpty() },
        )
    }

    private fun normalizeWinners(calendar: List<Race>, drivers: List<Driver>): List<Race> =
        calendar.map { race ->
            val winner = race.winner ?: return@map race
            race.copy(winner = findDriver(drivers, winner)?.name ?: winner)
        }
}
