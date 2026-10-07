package com.pitstophub.app.notifications

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.repo.RaceResultRow
import com.pitstophub.app.domain.UserSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

private val SAO_PAULO = ZoneId.of("America/Sao_Paulo")

private fun race(id: String, date: String, startsAt: String?, name: String = "GP de Singapura", status: RaceStatus = RaceStatus.UPCOMING) =
    Race(id = id, name = name, enName = "Singapore Grand Prix", date = date, startsAt = startsAt, status = status, winner = null)

private fun category(id: String, vararg races: Race) = Category(id = id, name = id.uppercase(), fullName = id, calendar = races.toList())

class AlertPlannerTest {

    private val singapore = race("singapore", "2026-10-11", "2026-10-11T12:00:00Z") // 09:00 em Sao Paulo
    private val follows = UserSettings(language = "en", followedCategoryIds = listOf("f1"))

    private fun plan(now: String, settings: UserSettings = follows, vararg categories: Category) =
        AlertPlanner.plan(Instant.parse(now), settings, categories.toList().ifEmpty { listOf(category("f1", singapore)) }, SAO_PAULO, Locale.US)

    @Test
    fun `planeja dia da corrida, 1 hora antes e largada`() {
        val alerts = plan("2026-10-10T20:00:00Z")
        assertEquals(listOf(AlertKind.RACE_DAY, AlertKind.T60, AlertKind.START), alerts.map { it.kind })
        // largada 12:00Z (09:00 local): o aviso do dia sai 2 h antes, 07:00 local = 10:00Z
        assertEquals(Instant.parse("2026-10-11T10:00:00Z"), alerts[0].fireAt)
        assertEquals(Instant.parse("2026-10-11T11:00:00Z"), alerts[1].fireAt)
        assertEquals(Instant.parse("2026-10-11T12:00:00Z"), alerts[2].fireAt)
        assertEquals("F1: race day", alerts[0].title)
        assertEquals("F1: starts soon", alerts[1].title)
        assertEquals("F1: lights out", alerts[2].title)
        assertTrue(alerts[0].body.contains("Singapore Grand Prix") && alerts[0].body.contains("starts"))
        assertEquals("f1:singapore:t60", alerts[1].key)
    }

    @Test
    fun `textos em portugues`() {
        val alerts = plan("2026-10-10T20:00:00Z", follows.copy(language = "pt"))
        assertEquals("F1: corrida hoje", alerts[0].title)
        assertEquals("F1: largada em breve", alerts[1].title)
        assertEquals("F1: luzes apagadas", alerts[2].title)
        assertTrue(alerts[0].body.startsWith("GP de Singapura · largada"))
    }

    @Test
    fun `corrida a noite faz o aviso do dia sair as 8h`() {
        val evening = race("night", "2026-10-11", "2026-10-11T23:00:00Z") // 20:00 local
        val alerts = plan("2026-10-10T20:00:00Z", categories = arrayOf(category("f1", evening)))
        assertEquals(Instant.parse("2026-10-11T11:00:00Z"), alerts.first { it.kind == AlertKind.RACE_DAY }.fireAt) // 08:00 local
    }

    @Test
    fun `sem horario so avisa no dia, as 8h`() {
        val dateOnly = race("rally", "2026-10-11", null)
        val alerts = plan("2026-10-10T20:00:00Z", categories = arrayOf(category("f1", dateOnly)))
        assertEquals(listOf(AlertKind.RACE_DAY), alerts.map { it.kind })
        assertEquals(Instant.parse("2026-10-11T11:00:00Z"), alerts.single().fireAt)
    }

    @Test
    fun `respeita as preferencias e quem nao segue nada`() {
        assertEquals(listOf(AlertKind.START), plan("2026-10-10T20:00:00Z", follows.copy(notifyRaceDay = false, notifyT60 = false)).map { it.kind })
        assertTrue(plan("2026-10-10T20:00:00Z", UserSettings(language = "en")).isEmpty())
        // seguir so um piloto da categoria ja basta
        assertEquals(3, plan("2026-10-10T20:00:00Z", UserSettings(language = "en", followedDriverIds = listOf("f1::norris"))).size)
        // outra categoria nao dispara
        assertTrue(plan("2026-10-10T20:00:00Z", UserSettings(language = "en", followedCategoryIds = listOf("wec"))).isEmpty())
    }

    @Test
    fun `ignora corridas fora da janela ou ja concluidas`() {
        val far = race("far", "2026-11-20", "2026-11-20T12:00:00Z")
        val done = race("done", "2026-10-11", "2026-10-11T12:00:00Z", status = RaceStatus.COMPLETED)
        assertTrue(plan("2026-10-10T20:00:00Z", categories = arrayOf(category("f1", far, done))).isEmpty())
    }

    @Test
    fun `vence na janela certa e expira`() {
        val alerts = plan("2026-10-10T20:00:00Z")
        val t60 = alerts.first { it.kind == AlertKind.T60 }
        assertFalse(t60.isDue(Instant.parse("2026-10-11T10:59:00Z")))
        assertTrue(t60.isDue(Instant.parse("2026-10-11T11:00:00Z")))
        assertTrue(t60.isDue(Instant.parse("2026-10-11T11:50:00Z")))
        assertFalse(t60.isDue(Instant.parse("2026-10-11T12:00:00Z"))) // ja largou
        val start = alerts.first { it.kind == AlertKind.START }
        assertTrue(start.isDue(Instant.parse("2026-10-11T12:10:00Z")))
        assertFalse(start.isDue(Instant.parse("2026-10-11T12:16:00Z")))
    }
}

class ResultPlannerTest {

    private val f1 = Category(
        id = "f1", name = "F1", fullName = "Formula 1",
        teams = listOf(Team("mclaren", "McLaren"), Team("ferrari", "Ferrari")),
        drivers = listOf(
            Driver("norris", "Lando Norris", teamId = "mclaren"),
            Driver("piastri", "Oscar Piastri", teamId = "mclaren"),
            Driver("leclerc", "Charles Leclerc", teamId = "ferrari"),
        ),
    )
    private val race = race("bahrain", "2026-10-04", null, name = "GP do Bahrein", status = RaceStatus.COMPLETED).copy(enName = "Bahrain Grand Prix")

    private fun row(position: Int, driver: String, team: String) =
        RaceResultRow(position.toString(), driver, driver.take(3).uppercase(), team, null, "", 0.0, finishPosition = position)

    private val results = listOf(
        row(1, "Max Verstappen", "Red Bull"),
        row(2, "Lando Norris", "McLaren"),
        row(4, "Oscar Piastri", "McLaren"),
        row(5, "Charles Leclerc", "Ferrari"),
    )

    @Test
    fun `avisa onde terminaram os seguidos, na ordem de prioridade`() {
        val settings = UserSettings(
            language = "pt",
            followedDriverIds = listOf("f1::norris"),
            followedTeamIds = listOf("f1::mclaren", "f1::ferrari"),
            priorityFollowIds = listOf("team::f1::ferrari", "driver::f1::norris", "team::f1::mclaren"),
        )
        val alert = ResultPlanner.forF1(f1, race, results, settings)!!
        assertEquals("F1: resultado", alert.title)
        assertEquals(
            "Ferrari terminou com o carro em 5º no GP do Bahrein! Lando Norris terminou em 2º no GP do Bahrein! " +
                "McLaren terminou com o carro em 4º no GP do Bahrein!",
            alert.body,
        )
        assertEquals("result:f1:bahrain", alert.key)
    }

    @Test
    fun `equipe com dois carros lista as posicoes, em ingles`() {
        val settings = UserSettings(language = "en", followedTeamIds = listOf("f1::mclaren"))
        val alert = ResultPlanner.forF1(f1, race, results, settings)!!
        assertEquals("McLaren finished with cars in P2 and P4 in the Bahrain Grand Prix.", alert.body)
    }

    @Test
    fun `sem nenhum seguido no resultado nao ha aviso`() {
        assertNull(ResultPlanner.forF1(f1, race, results, UserSettings(followedDriverIds = listOf("f1::hamilton"))))
        assertNull(ResultPlanner.forF1(f1, race, emptyList(), UserSettings(followedDriverIds = listOf("f1::norris"))))
    }

    @Test
    fun `categorias sem tabela de resultados avisam quando um seguido vence`() {
        val won = race.copy(winner = "Lando Norris")
        val driverAlert = ResultPlanner.forWinner(f1, won, UserSettings(language = "pt", followedDriverIds = listOf("f1::norris")))
        assertNotNull(driverAlert)
        assertEquals("Lando Norris venceu GP do Bahrein!", driverAlert!!.body)
        val teamAlert = ResultPlanner.forWinner(f1, won, UserSettings(language = "en", followedTeamIds = listOf("f1::mclaren")))!!
        assertEquals("McLaren won the Bahrain Grand Prix with Lando Norris.", teamAlert.body)
        assertNull(ResultPlanner.forWinner(f1, won, UserSettings(followedDriverIds = listOf("f1::leclerc"))))
        assertNull(ResultPlanner.forWinner(f1, race, UserSettings(followedDriverIds = listOf("f1::norris")))) // sem vencedor ainda
    }

    @Test
    fun `lista de posicoes em portugues e ingles`() {
        assertEquals("2º, 4º e 5º", ResultPlanner.places(listOf(2, 4, 5), true))
        assertEquals("P2, P4 and P5", ResultPlanner.places(listOf(2, 4, 5), false))
        assertEquals("P2 and P4", ResultPlanner.places(listOf(2, 4), false))
        assertEquals("7º", ResultPlanner.places(listOf(7), true))
    }
}
