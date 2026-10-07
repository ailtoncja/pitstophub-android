package com.pitstophub.app.domain

import com.pitstophub.app.data.model.StandingItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun round(r: Int, grid: Int?, finish: Int?, points: Double, status: String = "Finished", fastest: Int? = null) =
    DriverRound(r, "GP $r", "2026-01-0$r", grid, finish, points, status, fastest)

class SeasonStatsTest {

    private val mine = listOf(
        round(1, 1, 1, 25.0, fastest = 1),
        round(2, 3, 2, 18.0),
        round(3, 5, null, 0.0, status = "Engine"),
        round(4, 2, 6, 8.0, status = "+1 Lap"),
    )

    @Test
    fun `resume a temporada com numeros reais`() {
        val stats = seasonStats(mine)
        assertEquals(4, stats.races)
        assertEquals(1, stats.wins)
        assertEquals(2, stats.podiums)
        assertEquals(1, stats.poles)
        assertEquals(1, stats.fastestLaps)
        assertEquals(1, stats.dnfs) // "+1 Lap" conta como classificado
        assertEquals(51.0, stats.points, 0.0)
        assertEquals(1, stats.bestFinish)
        assertEquals(3.0, stats.averageFinish!!, 0.0001)
        assertEquals(0.5, stats.podiumRate, 0.0)
        assertEquals(listOf("GP 1"), stats.winRaces)
    }

    @Test
    fun `temporada sem corridas nao quebra`() {
        val stats = seasonStats(emptyList())
        assertEquals(0, stats.races)
        assertEquals(0.0, stats.podiumRate, 0.0)
        assertNull(stats.bestFinish)
        assertNull(stats.averageFinish)
    }

    @Test
    fun `duelo compara so as etapas em comum`() {
        val mate = listOf(round(1, 2, 3, 15.0), round(2, 1, 1, 25.0), round(3, 6, 5, 10.0), round(9, 4, 4, 12.0))
        val duel = teammateDuel(mine, mate)
        assertEquals(Duel(2, 1, 3), duel.grid)
        assertEquals(Duel(1, 1, 2), duel.race)
        assertEquals(66.67, duel.grid.mineShare, 0.01)
        assertTrue(teammateDuel(mine, emptyList()).isEmpty)
    }

    @Test
    fun `pontos acumulados rodada a rodada`() {
        val rounds = listOf(TeamRoundPoints(2, "B", 10.0), TeamRoundPoints(1, "A", 28.0))
        assertEquals(listOf(28.0, 38.0), cumulativePoints(rounds))
    }

    @Test
    fun `classificacao calcula distancia, fatia, setas e pontos da ultima etapa`() {
        val items = listOf(StandingItem(1, "Kimi", 320.0), StandingItem(3, "Lewis", 214.0), StandingItem(2, "George", 236.0))
        val previous = listOf(StandingItem(1, "Kimi", 295.0), StandingItem(2, "Lewis", 199.0), StandingItem(3, "George", 218.0))
        val rows = buildStandingRows(items, previous, mapOf("Lewis" to 15.0))
        assertEquals(listOf("Kimi", "George", "Lewis"), rows.map { it.item.name })
        assertEquals(0.0, rows[0].gap, 0.0)
        assertEquals(84.0, rows[1].gap, 0.0)
        assertEquals(1, rows[1].delta) // George subiu de 3o para 2o
        assertEquals(-1, rows[2].delta) // Lewis caiu de 2o para 3o
        assertEquals(15.0, rows[2].lastRacePoints!!, 0.0)
        assertEquals(0.0, rows[1].lastRacePoints!!, 0.0)
        assertNull(buildStandingRows(items)[0].delta)
    }
}
