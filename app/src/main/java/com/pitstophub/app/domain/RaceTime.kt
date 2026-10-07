package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class RacePhase {
    /** Largada ainda no futuro. */
    COUNTDOWN,

    /** Largou ha pouco (horario conhecido) e o resultado ainda nao saiu. */
    LIVE,

    /** Ja passou da hora, mas a fonte ainda nao marcou como concluida. */
    AWAITING_RESULT,
}

data class CountdownParts(val days: Long, val hours: Int, val minutes: Int, val seconds: Int)

object RaceTime {

    /** Quanto tempo depois da largada a prova ainda e considerada "ao vivo". */
    private val liveWindow: Duration = Duration.ofHours(3)

    fun hasKnownTime(race: Race): Boolean = parseInstant(race.startsAt) != null

    /** Hora de largada; sem horario na fonte, usa a meia-noite local da data (mesmo criterio do PWA). */
    fun startInstant(race: Race, zone: ZoneId = ZoneId.systemDefault()): Instant {
        parseInstant(race.startsAt)?.let { return it }
        val date = runCatching { LocalDate.parse(race.date) }.getOrNull() ?: return Instant.EPOCH
        return date.atStartOfDay(zone).toInstant()
    }

    fun phase(race: Race, now: Instant, zone: ZoneId = ZoneId.systemDefault()): RacePhase {
        val start = startInstant(race, zone)
        return when {
            now.isBefore(start) -> RacePhase.COUNTDOWN
            hasKnownTime(race) && now.isBefore(start.plus(liveWindow)) -> RacePhase.LIVE
            else -> RacePhase.AWAITING_RESULT
        }
    }

    /**
     * Proxima corrida: a primeira ainda por vir (ou em andamento). Se todas as pendentes ja passaram do
     * horario sem resultado, devolve a mais antiga delas em vez de esconder a categoria.
     */
    fun nextRace(calendar: List<Race>, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Race? {
        val pending = calendar.filter { it.status == RaceStatus.UPCOMING }.sortedBy { startInstant(it, zone) }
        return pending.firstOrNull { phase(it, now, zone) != RacePhase.AWAITING_RESULT } ?: pending.firstOrNull()
    }

    /** Ultima corrida concluida (para a Home quando a temporada acabou). */
    fun lastRace(calendar: List<Race>, zone: ZoneId = ZoneId.systemDefault()): Race? =
        calendar.filter { it.status == RaceStatus.COMPLETED }.maxByOrNull { startInstant(it, zone) }

    /**
     * O catalogo estatico (e algumas fontes) pode deixar como "upcoming" uma corrida que ja passou. Depois do
     * fim do dia da prova ela vira "concluida" (sem vencedor, se a fonte nao informou), igual ao criterio da Jolpica.
     */
    fun settle(calendar: List<Race>, now: Instant): List<Race> = calendar.map { race ->
        if (race.status != RaceStatus.UPCOMING) return@map race
        val endOfDay = runCatching { Instant.parse("${race.date}T23:59:59Z") }.getOrNull()
        if (endOfDay != null && endOfDay.isBefore(now)) race.copy(status = RaceStatus.COMPLETED) else race
    }

    fun countdown(target: Instant, now: Instant): CountdownParts {
        val total = Duration.between(now, target).seconds.coerceAtLeast(0)
        return CountdownParts(
            days = total / 86_400,
            hours = ((total % 86_400) / 3_600).toInt(),
            minutes = ((total % 3_600) / 60).toInt(),
            seconds = (total % 60).toInt(),
        )
    }

    private fun parseInstant(value: String?): Instant? =
        value?.let { runCatching { Instant.parse(it) }.getOrNull() }
}
