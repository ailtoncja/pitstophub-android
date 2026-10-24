package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.remote.ApiBrasilDriver
import com.pitstophub.app.data.remote.ApiBrasilRace
import com.pitstophub.app.data.remote.ApiBrasilStandingEntry
import com.pitstophub.app.data.remote.ApiBrasilStandings
import com.pitstophub.app.data.remote.ApiBrasilStandingsClass
import com.pitstophub.app.data.remote.ApiEbDriver
import com.pitstophub.app.data.remote.ApiEbTeam
import com.pitstophub.app.data.remote.ApiGtwcTeam
import com.pitstophub.app.data.remote.ApiPerson
import com.pitstophub.app.data.remote.ApiRace
import com.pitstophub.app.data.remote.ApiStandingEntry
import com.pitstophub.app.data.remote.ApiStandings
import com.pitstophub.app.data.remote.ApiStandingsClass
import com.pitstophub.app.data.remote.SyncedRaceRow
import com.pitstophub.app.data.remote.SyncedStandingRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class LiveSeriesAndSyncedTest {

    private val now = Instant.parse("2026-10-04T12:00:00Z")

    // --- GT World Challenge ---------------------------------------------------------------------

    @Test
    fun `corridas do GTWC traduzem o pais e usam o vencedor oficial`() {
        val races = LiveSeriesMapper.gtwcRaces(
            "europe",
            listOf(
                ApiRace("europe", "brands-hatch", 2, "Brands Hatch", "Great Britain", "2026-05-03", winner = "AF Corse / Lionspeed GP"),
                ApiRace("europe", "spa-extra", 9, "Spa", "Belgium", "2026-12-20", winner = null),
            ),
            now,
        )
        assertEquals("Reino Unido", races[0].location)
        assertEquals("Great Britain", races[0].enLocation)
        assertEquals("Arthur Leclerc / Bastian Buus", races[0].winner) // vencedor oficial, nao o time da API
        assertEquals(RaceStatus.COMPLETED, races[0].status)
        assertEquals(RaceStatus.UPCOMING, races[1].status)
    }

    @Test
    fun `elenco do GTWC titula os nomes e nao repete piloto`() {
        val (teams, drivers) = LiveSeriesMapper.gtwcRoster(
            listOf(
                ApiGtwcTeam(
                    "2Seas Motorsport", "Mercedes-AMG GT3 EVO", "BRONZE", "222",
                    listOf(ApiPerson("Kiern JEWISS", "Reino Unido"), ApiPerson("Kiern JEWISS", "Reino Unido"), ApiPerson("Reece BARR", "Irlanda")),
                ),
            ),
        )
        assertEquals("2seas-motorsport", teams.single().id)
        assertEquals("Bronze", teams.single().raceClass)
        assertEquals(listOf("Kiern Jewiss", "Reece Barr"), drivers.map { it.name })
        assertTrue(drivers.all { it.number == "222" && it.teamId == "2seas-motorsport" })
    }

    @Test
    fun `classificacao do GTWC so aparece quando ha uma unica classe geral`() {
        val geral = ApiStandings(drivers = listOf(ApiStandingsClass("Geral", listOf(ApiStandingEntry(1, "LUCAS AUER", 131.5)))))
        val standings = LiveSeriesMapper.gtwcStandings(geral)
        assertEquals("Lucas Auer", standings!!.drivers!!.single().name)
        assertEquals(131.5, standings.drivers.single().points, 0.0)

        val porClasse = ApiStandings(
            teams = listOf(
                ApiStandingsClass("Pro", listOf(ApiStandingEntry(1, "A", 10.0))),
                ApiStandingsClass("Am", listOf(ApiStandingEntry(1, "B", 8.0))),
            ),
        )
        assertNull(LiveSeriesMapper.gtwcStandings(porClasse))
    }

    // --- Endurance Brasil -----------------------------------------------------------------------

    @Test
    fun `classes do Endurance Brasil seguem a ordem P1 antes de GT3 e GT4, sem classes vazias`() {
        val category = Category(
            id = "endurance-brasil", name = "EB", fullName = "Endurance Brasil",
            teams = listOf(Team("acme", "Acme Racing")),
            drivers = listOf(Driver("kim", "Kim Camelo", teamId = "acme")),
        )
        val api = ApiStandings(
            drivers = listOf(
                ApiStandingsClass("GT4", listOf(ApiStandingEntry(1, "Kim Camelo", 510.0, "SP"))),
                ApiStandingsClass("P2", emptyList()),
                ApiStandingsClass("P1", listOf(ApiStandingEntry(1, "Outro Piloto", 400.0, "RJ"))),
                ApiStandingsClass("GT3", listOf(ApiStandingEntry(1, "Terceiro", 300.0))),
            ),
        )
        val classes = LiveSeriesMapper.ebStandings(api, category)!!.driverClasses!!
        assertEquals(listOf("P1", "GT3", "GT4"), classes.map { it.classLabel })
        val kim = classes.last().entries.single()
        assertEquals("Acme Racing", kim.team) // equipe vem do elenco
        assertEquals("SP", kim.extra)
    }

    @Test
    fun `elenco do Endurance Brasil colore a equipe pela classe do piloto`() {
        val (teams, drivers) = LiveSeriesMapper.ebRoster(
            teams = listOf(ApiEbTeam("acme", "Acme Racing", "Chefe", listOf(ApiPerson("Kim Camelo", driverId = "kim")))),
            drivers = listOf(ApiEbDriver("kim", "Kim Camelo", "SP", "GT4", "acme")),
        )
        assertEquals("GT4", teams.single().raceClass)
        assertEquals("#E9C46A", teams.single().color)
        assertEquals("Chefe", teams.single().principal)
        assertEquals("Brasil", drivers.single().nationality)
    }

    @Test
    fun `corridas do Endurance Brasil traduzem o nome e o autodromo`() {
        val race = LiveSeriesMapper.ebRaces(
            listOf(ApiRace(raceId = "r1", round = 1, name = "Etapa 1 - Quatro Horas de Brasília", location = "Autódromo Internacional de Brasília", date = "2026-04-11", completed = true, winner = "Foresti Sports")),
        ).single()
        assertEquals("Round 1 - Four Hours of Brasília", race.enName)
        assertEquals("Brasília", race.enLocation)
        assertEquals(RaceStatus.COMPLETED, race.status)
    }

    // --- NASCAR Brasil e Porsche Cup Brasil -----------------------------------------------------

    @Test
    fun `corridas brasileiras usam o status que a API ja resolveu`() {
        val races = LiveSeriesMapper.brasilRaces(
            listOf(
                ApiBrasilRace(raceId = "r01", round = 1, name = "Interlagos", location = "Interlagos", date = "2026-03-01", status = "completed", winner = "Marçal Muller / Jeff Giassi"),
                ApiBrasilRace(raceId = "r10", round = 10, name = "Interlagos (Endurance)", location = "Interlagos", date = "2026-11-28", status = "upcoming"),
            ),
        )
        assertEquals(RaceStatus.COMPLETED, races[0].status)
        assertEquals("Marçal Muller / Jeff Giassi", races[0].winner)
        assertEquals(RaceStatus.UPCOMING, races[1].status)
        assertEquals(10, races[1].round)
    }

    @Test
    fun `uma divisao vira lista de pilotos e varias viram divisoes separadas`() {
        val single = LiveSeriesMapper.brasilStandings(
            ApiBrasilStandings(drivers = listOf(ApiBrasilStandingsClass("Geral", listOf(ApiBrasilStandingEntry(1, "Thiago Camilo", points = 226.0))))),
        )
        assertEquals("Thiago Camilo", single!!.drivers!!.single().name)
        assertNull(single.driverClasses)

        val many = LiveSeriesMapper.brasilStandings(
            ApiBrasilStandings(
                drivers = listOf(
                    ApiBrasilStandingsClass("Sprint Carrera", listOf(ApiBrasilStandingEntry(1, "Marçal Muller", points = 180.0))),
                    ApiBrasilStandingsClass("Sprint Trophy", emptyList()),
                    ApiBrasilStandingsClass("Endurance Challenge", listOf(ApiBrasilStandingEntry(1, "Fabio Carbone", points = 105.0))),
                ),
            ),
        )
        assertEquals(listOf("Sprint Carrera", "Endurance Challenge"), many!!.driverClasses!!.map { it.classLabel })
        assertNull(many.drivers)

        assertNull(LiveSeriesMapper.brasilStandings(ApiBrasilStandings(drivers = null)))
    }

    @Test
    fun `o grid brasileiro vem sem equipe, porque a fonte oficial nao publica`() {
        val drivers = LiveSeriesMapper.brasilDrivers(
            listOf(ApiBrasilDriver(driverId = "marcal-muller", name = "Marçal Muller", number = "544", classes = listOf("Sprint Carrera"))),
        )
        assertEquals("544", drivers.single().number)
        assertEquals("", drivers.single().teamId)
        assertEquals("Brasil", drivers.single().nationality)
    }

    // --- categorias sincronizadas (Supabase) ----------------------------------------------------

    private fun row(id: String, name: String, date: String, winner: String? = null, status: String = "completed") =
        SyncedRaceRow(raceId = id, name = name, date = date, status = status, winner = winner)

    @Test
    fun `IMSA descarta o Roar Before the Rolex 24`() {
        val base = Category("imsa", "IMSA", "IMSA")
        val merged = SyncedMerger.mergeCalendar(
            base,
            listOf(row("roar", "Roar Before the Rolex 24", "2026-01-18"), row("daytona", "Rolex 24 at Daytona", "2026-01-24")),
        )
        assertEquals(listOf("daytona"), merged.calendar.map { it.id })
    }

    @Test
    fun `WEC recoloca Le Mans quando a sincronizacao nao traz a etapa e prefere o vencedor oficial`() {
        val base = Category(
            "wec", "WEC", "WEC",
            calendar = listOf(
                Race("spa", "6 Horas de Spa", location = "Spa", date = "2026-05-09", status = RaceStatus.COMPLETED, winner = "Kevin Estre"),
                Race("lemans", "24 Horas de Le Mans", enName = "24 Hours of Le Mans", location = "Le Mans", date = "2026-06-14", status = RaceStatus.COMPLETED, winner = "Antonio Giovinazzi"),
            ),
        )
        val merged = SyncedMerger.mergeCalendar(base, listOf(row("spa", "6 Hours of Spa", "2026-05-09", winner = "Porsche #6")))
        assertEquals(listOf("spa", "lemans"), merged.calendar.map { it.id })
        assertEquals("Kevin Estre", merged.calendar.first().winner)
    }

    @Test
    fun `sem linhas sincronizadas nada muda`() {
        val base = Category("dtm", "DTM", "DTM", calendar = listOf(Race("r", "Corrida", date = "2026-05-01")))
        assertEquals(base, SyncedMerger.mergeCalendar(base, emptyList()))
        assertEquals(base, SyncedMerger.mergeStandings(base, emptyList()))
    }

    @Test
    fun `classificacao sincronizada vira a lista de pilotos`() {
        val base = Category("dtm", "DTM", "DTM", calendar = listOf(Race("r", "Corrida", date = "2026-05-01")))
        val merged = SyncedMerger.mergeStandings(base, listOf(SyncedStandingRow(1, "Fulano", "Time", 120.5, season = 2026)))
        val item = merged.standings!!.drivers!!.single()
        assertEquals("Fulano", item.name)
        assertEquals("Time", item.team)
        assertNotNull(merged.standings)
    }

    @Test
    fun `virada nao mistura classificacao etapas ou vencedores antigos`() {
        val base = Category("wec", "WEC", "WEC", calendar = listOf(
            Race("spa", "Spa", date = "2026-05-01", winner = "Antigo"),
            Race("lemans", "Le Mans", date = "2026-06-01"),
        ))
        val old = SyncedStandingRow(1, "Piloto", "Time", 100.0, season = 2026)
        val withPoints = SyncedMerger.mergeStandings(base, listOf(old))
        val next = SyncedMerger.mergeCalendar(withPoints, listOf(row("spa", "Spa", "2027-05-01", "Novo")))
        assertNull(next.standings)
        assertEquals(1, next.calendar.size)
        assertEquals("Novo", next.calendar.single().winner)
        assertNull(SyncedMerger.mergeStandings(next, listOf(old)).standings)
        assertNull(SyncedMerger.mergeStandings(next, listOf(old.copy(season = null))).standings)
        assertNotNull(SyncedMerger.mergeStandings(next, listOf(old.copy(season = 2027))).standings)
    }
}
