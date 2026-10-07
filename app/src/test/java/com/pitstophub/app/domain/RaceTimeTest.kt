package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class RaceTimeTest {

    private val utc: ZoneId = ZoneId.of("UTC")

    private fun race(id: String, date: String, startsAt: String? = null, status: RaceStatus = RaceStatus.UPCOMING) =
        Race(id = id, name = id, date = date, startsAt = startsAt, status = status)

    @Test
    fun `usa o horario da fonte quando existe`() {
        val r = race("a", "2026-10-10", "2026-10-10T12:00:00Z")
        assertTrue(RaceTime.hasKnownTime(r))
        assertEquals(Instant.parse("2026-10-10T12:00:00Z"), RaceTime.startInstant(r, utc))
    }

    @Test
    fun `sem horario usa a meia-noite local da data`() {
        val r = race("a", "2026-10-10")
        assertFalse(RaceTime.hasKnownTime(r))
        assertEquals(Instant.parse("2026-10-10T00:00:00Z"), RaceTime.startInstant(r, utc))
        // fuso de Brasilia (UTC-3): meia-noite local = 03:00 UTC
        assertEquals(Instant.parse("2026-10-10T03:00:00Z"), RaceTime.startInstant(r, ZoneId.of("America/Sao_Paulo")))
    }

    @Test
    fun `fase muda de contagem para ao vivo e para aguardando resultado`() {
        val r = race("a", "2026-10-10", "2026-10-10T12:00:00Z")
        assertEquals(RacePhase.COUNTDOWN, RaceTime.phase(r, Instant.parse("2026-10-10T11:59:59Z"), utc))
        assertEquals(RacePhase.LIVE, RaceTime.phase(r, Instant.parse("2026-10-10T13:00:00Z"), utc))
        assertEquals(RacePhase.AWAITING_RESULT, RaceTime.phase(r, Instant.parse("2026-10-10T15:00:01Z"), utc))
    }

    @Test
    fun `sem horario conhecido nunca fica ao vivo`() {
        val r = race("a", "2026-10-10")
        assertEquals(RacePhase.AWAITING_RESULT, RaceTime.phase(r, Instant.parse("2026-10-10T01:00:00Z"), utc))
    }

    @Test
    fun `proxima corrida ignora as concluidas e pula as que ja passaram sem resultado`() {
        val now = Instant.parse("2026-10-10T20:00:00Z")
        val calendar = listOf(
            race("done", "2026-09-01", status = RaceStatus.COMPLETED),
            race("stuck", "2026-10-10", "2026-10-10T12:00:00Z"), // passou e a fonte ainda nao marcou concluida
            race("next", "2026-10-24", "2026-10-24T12:00:00Z"),
            race("later", "2026-11-07", "2026-11-07T12:00:00Z"),
        )
        assertEquals("next", RaceTime.nextRace(calendar, now, utc)?.id)
    }

    @Test
    fun `se todas as pendentes ja passaram devolve a mais antiga`() {
        val now = Instant.parse("2026-12-30T00:00:00Z")
        val calendar = listOf(
            race("a", "2026-12-01", "2026-12-01T12:00:00Z"),
            race("b", "2026-12-15", "2026-12-15T12:00:00Z"),
        )
        assertEquals("a", RaceTime.nextRace(calendar, now, utc)?.id)
    }

    @Test
    fun `sem corridas pendentes nao ha proxima e a ultima e a mais recente concluida`() {
        val calendar = listOf(
            race("a", "2026-03-01", status = RaceStatus.COMPLETED),
            race("b", "2026-11-01", status = RaceStatus.COMPLETED),
            race("c", "2026-05-01", status = RaceStatus.CANCELLED),
        )
        assertNull(RaceTime.nextRace(calendar, Instant.parse("2026-12-01T00:00:00Z"), utc))
        assertEquals("b", RaceTime.lastRace(calendar, utc)?.id)
    }

    @Test
    fun `contagem regressiva decompoe dias, horas, minutos e segundos`() {
        val now = Instant.parse("2026-10-01T00:00:00Z")
        val target = now.plusSeconds(4 * 86_400L + 12 * 3_600L + 45 * 60L + 9)
        val parts = RaceTime.countdown(target, now)
        assertEquals(CountdownParts(4, 12, 45, 9), parts)
    }

    @Test
    fun `contagem nunca fica negativa`() {
        val now = Instant.parse("2026-10-01T00:00:00Z")
        assertEquals(CountdownParts(0, 0, 0, 0), RaceTime.countdown(now.minusSeconds(500), now))
    }

    @Test
    fun `corrida passada que a fonte ainda marca como proxima vira concluida`() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        val calendar = listOf(
            race("agosto", "2026-08-30"),                                  // passou, fonte desatualizada
            race("hoje", "2026-10-04"),                                    // dia da prova: ainda nao terminou
            race("futura", "2026-11-01"),
            race("cancelada", "2026-07-01", status = RaceStatus.CANCELLED), // cancelada nao muda
        )
        val settled = RaceTime.settle(calendar, now).associate { it.id to it.status }
        assertEquals(RaceStatus.COMPLETED, settled["agosto"])
        assertEquals(RaceStatus.UPCOMING, settled["hoje"])
        assertEquals(RaceStatus.UPCOMING, settled["futura"])
        assertEquals(RaceStatus.CANCELLED, settled["cancelada"])
    }

    @Test
    fun `temporada acabada nao tem proxima corrida mesmo com catalogo desatualizado`() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        val settled = RaceTime.settle(listOf(race("a", "2026-08-30"), race("b", "2026-09-06")), now)
        assertNull(RaceTime.nextRace(settled, now, utc))
        assertEquals("b", RaceTime.lastRace(settled, utc)?.id)
    }

    @Test
    fun `local nao repete o nome quando um texto contem o outro`() {
        assertEquals("Marina Bay Street Circuit", RaceFormat.place("Marina Bay Street Circuit", "Marina Bay"))
        assertEquals("Autódromo de Interlagos, São Paulo", RaceFormat.place("Autódromo de Interlagos", "São Paulo"))
        assertEquals("Interlagos, São Paulo", RaceFormat.place("Interlagos", "Interlagos, São Paulo"))
        assertEquals("Monza", RaceFormat.place("", "Monza"))
        assertEquals("Monza", RaceFormat.place("Monza", ""))
    }

    @Test
    fun `codigo do piloto sao as tres primeiras letras do sobrenome`() {
        assertEquals("VER", RaceFormat.driverCode("Max Verstappen"))
        assertEquals("ANT", RaceFormat.driverCode("Kimi Antonelli"))
        assertEquals("", RaceFormat.driverCode(""))
    }

    @Test
    fun `rotulo de data segue a ordem do idioma`() {
        assertEquals("MAR 08", RaceFormat.monthDay("2026-03-08", Locale.US))
        assertEquals("08 MAR", RaceFormat.monthDay("2026-03-08", Locale.forLanguageTag("pt-BR")))
        assertEquals("data invalida", RaceFormat.monthDay("data invalida", Locale.US))
    }
}
