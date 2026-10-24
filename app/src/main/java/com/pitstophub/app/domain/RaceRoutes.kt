package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * O que a pagina de uma etapa deve mostrar (porte de src/lib/race-route.ts).
 *
 * As fontes ao vivo (Jolpica na F1, synced_races nas categorias sincronizadas, api-motorsports) substituem o
 * calendario do catalogo com ids proprios ("r01-melbourne" no lugar de "melbourne-f2") e sem as etapas canceladas.
 * Mas os ids do catalogo continuam circulando: a interface usa o catalogo enquanto a sincronizacao nao termina, as
 * notificacoes e a busca guardam esses ids, e os links "substituida por..." apontam para o catalogo.
 */
sealed interface RaceRoute {
    data class Found(val race: Race) : RaceRoute
    data class Redirect(val raceId: String) : RaceRoute
    data object Missing : RaceRoute
}

object RaceRoutes {

    // Um fim de semana de corrida: o catalogo as vezes guarda o sabado e a fonte, o domingo (ou o contrario).
    private const val SAME_WEEKEND_DAYS = 2L

    /**
     * 1. id presente no calendario em uso (ao vivo ou catalogo) -> essa etapa;
     * 2. etapa cancelada do catalogo -> a propria etapa do catalogo (a fonte ao vivo nao a lista);
     * 3. etapa do catalogo que a fonte ao vivo tem com outro id -> o id ao vivo;
     * 4. etapa so do catalogo -> a etapa do catalogo.
     */
    fun resolve(calendar: List<Race>, catalog: List<Race>, raceId: String): RaceRoute {
        calendar.firstOrNull { it.id == raceId }?.let { return RaceRoute.Found(it) }
        val base = catalog.firstOrNull { it.id == raceId } ?: return RaceRoute.Missing
        if (base.status == RaceStatus.CANCELLED) return RaceRoute.Found(base)
        val twin = findLiveTwin(calendar, base)
        return if (twin != null) RaceRoute.Redirect(twin.id) else RaceRoute.Found(base)
    }

    /** A mesma etapa no calendario ao vivo: mesma data, ou o mesmo lugar no mesmo fim de semana. */
    fun findLiveTwin(calendar: List<Race>, base: Race): Race? {
        val baseDay = dayOf(base.date) ?: return null
        val baseTokens = tokensOf(base)
        var best: Race? = null
        var bestScore = -1L
        for (race in calendar) {
            if (race.status == RaceStatus.CANCELLED) continue
            val day = dayOf(race.date) ?: continue
            val gap = abs(ChronoUnit.DAYS.between(baseDay, day))
            if (gap > SAME_WEEKEND_DAYS) continue
            val shared = tokensOf(race).count { it in baseTokens }
            // Fora da mesma data, so vale se citar o mesmo lugar.
            if (gap > 0 && shared == 0) continue
            // Data mais proxima primeiro; no empate (rodada dupla), quem cita mais do mesmo nome/lugar.
            val score = (SAME_WEEKEND_DAYS - gap) * 100 + shared
            if (score > bestScore) {
                bestScore = score
                best = race
            }
        }
        return best
    }

    /** Mesmo lugar no mesmo fim de semana (mais estrito que [findLiveTwin]: o lugar precisa bater sempre). */
    fun sameEventAndPlace(a: Race, b: Race): Boolean {
        val first = dayOf(a.date) ?: return false
        val second = dayOf(b.date) ?: return false
        if (abs(ChronoUnit.DAYS.between(first, second)) > SAME_WEEKEND_DAYS) return false
        val tokens = tokensOf(a)
        return tokensOf(b).any { it in tokens }
    }

    private fun dayOf(date: String): LocalDate? = runCatching { LocalDate.parse(date.take(10)) }.getOrNull()

    // Palavras que todo nome de etapa tem e nao dizem onde ela acontece.
    private val GENERIC_WORDS = setOf("rodada", "round", "grand", "prix", "race", "corrida", "circuit", "circuito", "international", "the", "and", "das", "dos")
    private val ROUND_PREFIX = Regex("^r\\d+$")
    private val DIGITS = Regex("^\\d+$")
    private val COMBINING = Regex("[\\u0300-\\u036f]")
    private val SEPARATORS = Regex("[^a-z0-9]+")

    internal fun tokensOf(race: Race): Set<String> {
        val text = listOfNotNull(race.id, race.name, race.enName, race.location, race.enLocation, race.circuit).filter { it.isNotEmpty() }.joinToString(" ")
        return COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()
            .split(SEPARATORS)
            // Numeros soltos ficam ("Corrida 1"/"milwaukee-2"); prefixos de rodada ("r07") e palavras curtas, nao.
            .filter { it.isNotEmpty() && it !in GENERIC_WORDS && !ROUND_PREFIX.matches(it) && (it.length > 2 || DIGITS.matches(it)) }
            .toSet()
    }
}
