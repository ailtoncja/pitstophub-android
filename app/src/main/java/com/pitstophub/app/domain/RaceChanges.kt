package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Pares "etapa cancelada <-> substituta", guardados no catalogo (replacedBy/replaces/relocated/changeSource).
 * Porte de src/lib/race-changes.ts.
 *
 * As fontes ao vivo nao sabem nada disso: a Jolpica e o synced_races nao listam as canceladas e trocam os ids. Por
 * isso tudo parte do catalogo e os links usam o id do catalogo, que a pagina da etapa resolve ([RaceRoutes]).
 */
sealed interface RaceChange {
    /** Etapa cancelada que tem substituta(s) (ou foi transferida, quando [relocated]). */
    data class ReplacedBy(val races: List<Race>, val relocated: Boolean, val source: String?) : RaceChange

    /** Etapa cancelada sem substituta (confirmado na fonte). */
    data class NoReplacement(val source: String?) : RaceChange

    /** Etapa que entrou no lugar de etapa(s) cancelada(s). */
    data class Replaces(val races: List<Race>, val relocated: Boolean, val source: String?) : RaceChange
}

/** As duas listas do calendario: "a disputar" (em ordem de data) e "ja disputadas" (da mais recente para a mais antiga). */
data class CalendarSections(val toCome: List<Race>, val played: List<Race>)

object RaceChanges {

    // Mesma folga de upcomingRaces do site: uma etapa so "ja passou" um dia e meio depois da largada.
    private val AHEAD_GRACE: Duration = Duration.ofHours(36)

    /** A entrada do catalogo de uma etapa exibida (pelo id; para ids ao vivo, a mesma etapa no fim de semana). */
    fun catalogEntryFor(catalog: List<Race>, race: Race): Race? {
        catalog.firstOrNull { it.id == race.id }?.let { return it }
        if (race.status == RaceStatus.CANCELLED) {
            return catalog.firstOrNull { it.status == RaceStatus.CANCELLED && RaceRoutes.sameEventAndPlace(it, race) }
        }
        return catalog.firstOrNull { !it.replaces.isNullOrEmpty() && RaceRoutes.findLiveTwin(listOf(race), it) != null }
    }

    fun raceChange(catalog: List<Race>, race: Race): RaceChange? {
        val entry = catalogEntryFor(catalog, race) ?: return null
        fun byIds(ids: List<String>) = ids.mapNotNull { id -> catalog.firstOrNull { it.id == id } }
        val replacedBy = entry.replacedBy
        if (entry.status == RaceStatus.CANCELLED && replacedBy != null) {
            val races = byIds(replacedBy)
            return if (races.isNotEmpty()) RaceChange.ReplacedBy(races, entry.relocated, entry.changeSource) else RaceChange.NoReplacement(entry.changeSource)
        }
        val replaces = entry.replaces
        if (entry.status != RaceStatus.CANCELLED && !replaces.isNullOrEmpty()) {
            val races = byIds(replaces)
            if (races.isNotEmpty()) return RaceChange.Replaces(races, entry.relocated, entry.changeSource)
        }
        return null
    }

    /**
     * O calendario em uso mais as etapas canceladas do catalogo que a fonte ao vivo nao lista, em ordem de data.
     * As canceladas so aparecem aqui: contagem, numero de rodada e "proxima etapa" continuam ignorando-as.
     */
    fun calendarWithCancelled(calendar: List<Race>, catalog: List<Race>, zone: ZoneId = ZoneId.systemDefault()): List<Race> {
        val missing = catalog.filter { base ->
            base.status == RaceStatus.CANCELLED &&
                calendar.none { it.id == base.id || (it.status == RaceStatus.CANCELLED && RaceRoutes.sameEventAndPlace(it, base)) }
        }
        return (calendar + missing).sortedBy { RaceTime.startInstant(it, zone) }
    }

    /** Cada etapa cancelada entra na lista e na posicao da sua data. */
    fun calendarSections(calendar: List<Race>, catalog: List<Race>, now: Instant, zone: ZoneId = ZoneId.systemDefault()): CalendarSections {
        val all = calendarWithCancelled(calendar, catalog, zone)
        fun ahead(race: Race) = RaceTime.startInstant(race, zone).plus(AHEAD_GRACE).isAfter(now)
        return CalendarSections(
            toCome = all.filter { (it.status == RaceStatus.UPCOMING || it.status == RaceStatus.CANCELLED) && ahead(it) },
            played = all.filter { it.status == RaceStatus.COMPLETED || (it.status == RaceStatus.CANCELLED && !ahead(it)) }.reversed(),
        )
    }

    /** Numero da rodada: o da fonte, ou a posicao no calendario sem as canceladas (0 para uma cancelada). */
    fun roundNumber(calendar: List<Race>, race: Race): Int {
        race.round?.takeIf { it > 0 }?.let { return it }
        val index = calendar.filter { it.status != RaceStatus.CANCELLED }.indexOfFirst { it.id == race.id }
        return if (index >= 0) index + 1 else 0
    }

    /** "A e B", "A, B e C". */
    fun joinNames(names: List<String>, and: String): String {
        if (names.size <= 1) return names.joinToString("")
        return "${names.dropLast(1).joinToString(", ")} $and ${names.last()}"
    }
}
