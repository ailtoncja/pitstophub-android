package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.SessionKind
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.remote.JConstructor
import com.pitstophub.app.data.remote.JConstructorStanding
import com.pitstophub.app.data.remote.JDriver
import com.pitstophub.app.data.remote.JDriverStanding
import com.pitstophub.app.data.remote.JolpicaClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class F1MergerTest {

    private val now = Instant.parse("2026-10-04T12:00:00Z")

    private val base = Category(
        id = "f1",
        name = "F1",
        fullName = "Formula 1",
        teams = listOf(Team("mercedes", "Mercedes"), Team("redbull", "Oracle Red Bull Racing")),
        drivers = listOf(
            Driver("russell", "George Russell", "63", "Reino Unido", "mercedes", image = "http://img/russell.jpg", cutout = "http://img/russell.png"),
            Driver("verstappen", "Max Verstappen", "3", "Países Baixos", "redbull"),
        ),
        calendar = listOf(
            Race("australia", "GP da Austrália", "Australian GP", "Melbourne", "Melbourne", "2026-03-08", circuit = "Albert Park", status = RaceStatus.COMPLETED),
        ),
    )

    private val racesJson = """
        {"MRData":{"RaceTable":{"season":"2026","Races":[
          {"season":"2026","round":"1","raceName":"Australian Grand Prix",
           "Circuit":{"circuitId":"albert_park","circuitName":"Albert Park Grand Prix Circuit","Location":{"locality":"Melbourne","country":"Australia"}},
           "date":"2026-03-08","time":"04:00:00Z",
           "FirstPractice":{"date":"2026-03-06","time":"01:30:00Z"},"Qualifying":{"date":"2026-03-07","time":"05:00:00Z"}},
          {"season":"2026","round":"2","raceName":"Chinese Grand Prix",
           "Circuit":{"circuitId":"shanghai","circuitName":"Shanghai International Circuit","Location":{"locality":"Shanghai","country":"China"}},
           "date":"2026-03-15","time":"07:00:00Z",
           "FirstPractice":{"date":"2026-03-13","time":"03:30:00Z"},"Qualifying":{"date":"2026-03-14","time":"07:00:00Z"},
           "Sprint":{"date":"2026-03-14","time":"03:00:00Z"},"SprintQualifying":{"date":"2026-03-13","time":"07:30:00Z"}},
          {"season":"2026","round":"24","raceName":"Abu Dhabi Grand Prix",
           "Circuit":{"circuitId":"yas_marina","circuitName":"Yas Marina Circuit","Location":{"locality":"Abu Dhabi","country":"UAE"}},
           "date":"2026-12-06","time":"13:00:00Z"}
        ]}}}
    """.trimIndent()

    private fun merge(
        winners: Map<String, String> = emptyMap(),
        drivers: List<JDriverStanding> = emptyList(),
        constructors: List<JConstructorStanding> = emptyList(),
    ): Category = F1Merger.merge(base, JolpicaClient.parseRaces(racesJson), winners, drivers, constructors, now)

    @Test
    fun `calendario ao vivo reaproveita nome e id do catalogo quando a data bate`() {
        val australia = merge(winners = mapOf("1" to "George Russell")).calendar.first()
        assertEquals("australia", australia.id)
        assertEquals("GP da Austrália", australia.name)
        assertEquals("Albert Park", australia.circuit)
        assertEquals(1, australia.round)
        assertEquals("2026-03-08T04:00:00Z", australia.startsAt)
        assertEquals("George Russell", australia.winner)
    }

    @Test
    fun `corrida fora do catalogo ganha id e nome a partir da Jolpica`() {
        val china = merge().calendar[1]
        assertEquals("chinese-grand-prix", china.id)
        assertEquals("GP de Chinese", china.name)
        assertEquals("Chinese Grand Prix", china.enName)
    }

    @Test
    fun `status vem do resultado ou da data`() {
        val calendar = merge(winners = mapOf("1" to "George Russell")).calendar
        assertEquals(RaceStatus.COMPLETED, calendar[0].status) // tem vencedor
        assertEquals(RaceStatus.COMPLETED, calendar[1].status) // data passada, sem vencedor
        assertEquals(RaceStatus.UPCOMING, calendar[2].status)  // data futura
        assertNull(calendar[2].winner)
    }

    @Test
    fun `programacao do fim de semana vem ordenada e inclui sprint`() {
        val kinds = merge().calendar[1].sessions.map { it.kind }
        assertEquals(
            listOf(SessionKind.FP1, SessionKind.SPRINT_QUALIFYING, SessionKind.SPRINT, SessionKind.QUALIFYING, SessionKind.RACE),
            kinds,
        )
        assertEquals(listOf(SessionKind.FP1, SessionKind.QUALIFYING, SessionKind.RACE), merge().calendar[0].sessions.map { it.kind })
    }

    @Test
    fun `classificacao usa nomes do catalogo e resolve a equipe pelo construtor`() {
        val merged = merge(
            drivers = listOf(
                JDriverStanding("1", "51", "2", JDriver("russell", "63", "RUS", "George", "Russell"), listOf(JConstructor("mercedes", "Mercedes"))),
                JDriverStanding("2", "40", "1", JDriver("max_verstappen", "3", "VER", "Max", "Verstappen"), listOf(JConstructor("red_bull", "Red Bull"))),
            ),
            constructors = listOf(
                JConstructorStanding("1", "100", "3", JConstructor("red_bull", "Red Bull")),
                JConstructorStanding("2", "90", "1", JConstructor("mercedes", "Mercedes")),
            ),
        )
        val drivers = merged.standings!!.drivers!!
        assertEquals(listOf("George Russell", "Max Verstappen"), drivers.map { it.name })
        assertEquals("Oracle Red Bull Racing", drivers[1].team) // "Red Bull" da Jolpica -> nome do catalogo
        assertEquals(51.0, drivers[0].points, 0.0)
        assertEquals(listOf("Oracle Red Bull Racing", "Mercedes"), merged.standings!!.constructors!!.map { it.name })
    }

    @Test
    fun `numero e equipe dos pilotos sao atualizados sem perder foto e nacionalidade`() {
        val merged = merge(
            drivers = listOf(
                JDriverStanding("1", "51", "2", JDriver("russell", "63", "RUS", "George", "Russell"), listOf(JConstructor("ferrari", "Ferrari"))),
            ),
        )
        val russell = merged.drivers.first { it.id == "russell" }
        assertEquals("Reino Unido", russell.nationality)
        assertEquals("http://img/russell.png", russell.cutout)
        assertEquals("http://img/russell.jpg", russell.image)
        assertEquals("ferrari", russell.teamId)
        assertEquals(2, merged.drivers.size) // Verstappen continua no grid
    }

    @Test
    fun `sem dados da Jolpica o catalogo fica intacto`() {
        val merged = F1Merger.merge(base, emptyList(), emptyMap(), emptyList(), emptyList(), now)
        assertEquals(base.calendar, merged.calendar)
        assertNull(merged.standings)
        assertTrue(merged.drivers == base.drivers)
    }
}
