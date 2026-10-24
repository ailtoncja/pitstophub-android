package com.pitstophub.app.data

import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.CircuitsFile
import com.pitstophub.app.data.model.GpCircuit
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.Venue
import java.text.Normalizer
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Qual ficha de circuito (e qual mapa) vale para uma etapa: porte de `getRaceCircuitInfo(race, categoryId)` de
 * src/circuit-info.ts, rodando em cima das regras exportadas para circuits.json (tools/export-web-data.mjs confere
 * que o resultado e o mesmo do site em todas as etapas do catalogo).
 *
 * 1. Lugares com desenho do OpenStreetMap: o tracado sai da prova (categoria + data), do texto ("misto",
 *    "Road Course") ou da categoria (Daytona oval na NASCAR, misto na IMSA). Lugar reconhecido sem desenho
 *    verificado do tracado: nenhum mapa (nunca o mapa de outro tracado).
 * 2. Fichas de GP (pistas da F1): o lugar decide antes do nome do evento. Fora da F1 fica so o que e do circuito.
 * 3. O WRC nao tem ficha: rali roda em estradas, nao num circuito.
 */
class CircuitRouting(private val file: CircuitsFile) {

    private val regexCache = HashMap<String, Regex>()
    private val gpById = file.gp.associateBy { it.id }

    fun infoFor(race: Race, categoryId: String?): CircuitInfo? {
        if (categoryId == WRC) return null
        val drawn = findDrawn(race, categoryId)
        if (drawn != null) return drawn.info
        val entry = file.gp.firstOrNull { gpMatches(it, race) } ?: return null
        if (categoryId == null || categoryId == F1) return entry.info
        return circuitOnly(entry.info)
    }

    /** Desenho do lugar reconhecido; `info` null quando o lugar existe mas o tracado da prova nao tem desenho. */
    private class Drawn(val info: CircuitInfo?)

    private fun findDrawn(race: Race, categoryId: String?): Drawn? {
        val haystack = haystack(race, withCountry = true)
        for (venue in file.venues) {
            if (venue.categories != null && (categoryId == null || categoryId !in venue.categories)) continue
            if (!anyTerm(haystack, venue.terms, venue.patterns)) continue
            if (venue.not.any { it in haystack }) continue
            val drawn = file.drawings[layoutOf(venue, race, haystack, categoryId)] ?: return Drawn(null)
            return Drawn(CircuitInfo(trackImage = drawn.trackImage, osm = true, lengthKm = drawn.lengthKm, corners = drawn.corners, direction = drawn.direction))
        }
        return null
    }

    private fun layoutOf(venue: Venue, race: Race, haystack: String, categoryId: String?): String {
        venue.byEvent.firstOrNull { it.categoryId == categoryId && sameWeekend(it.date, race.date) }?.let { return it.layout }
        venue.byText.firstOrNull { anyTerm(haystack, it.terms, it.patterns) }?.let { return it.layout }
        return categoryId?.let { venue.byCategory[it] } ?: venue.layout
    }

    private fun gpMatches(entry: GpCircuit, race: Race): Boolean {
        val haystack = haystack(race, entry.withCountry)
        return anyTerm(haystack, entry.terms, entry.patterns) &&
            entry.not.none { it in haystack } &&
            entry.unless.none { id -> gpById[id]?.let { gpMatches(it, race) } == true }
    }

    private fun anyTerm(haystack: String, terms: List<String>, patterns: List<String>): Boolean =
        terms.any { it in haystack } || patterns.any { regex(it).containsMatchIn(haystack) }

    private fun regex(pattern: String): Regex = synchronized(regexCache) { regexCache.getOrPut(pattern) { Regex(pattern) } }

    companion object {
        const val F1 = "f1"
        const val WRC = "wrc"

        private val COMBINING = Regex("[\\u0300-\\u036f]")

        /** Minusculas e sem acento, mantendo a pontuacao ("gra-bretanha"), como o `raceHaystack` do site. */
        fun normalize(text: String): String = COMBINING.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase()

        fun haystack(race: Race, withCountry: Boolean): String = normalize(
            buildString {
                append("${race.circuit} ${race.location} ${race.enLocation.orEmpty()} ${race.name} ${race.enName.orEmpty()}")
                if (withCountry) append(" ${race.country.orEmpty()}")
            },
        )

        /** Mesma prova com +-2 dias (o catalogo as vezes guarda o sabado e a fonte, o domingo). */
        internal fun sameWeekend(a: String, b: String): Boolean {
            val first = runCatching { LocalDate.parse(a.take(10)) }.getOrNull() ?: return false
            val second = runCatching { LocalDate.parse(b.take(10)) }.getOrNull() ?: return false
            return abs(ChronoUnit.DAYS.between(first, second)) <= 2
        }

        /** Fora da F1 fica so o que e do circuito (sem voltas, recorde, Straight Mode, frenagens, pneus). */
        fun circuitOnly(info: CircuitInfo): CircuitInfo =
            CircuitInfo(trackImage = info.trackImage, osm = info.osm, lengthKm = info.lengthKm, corners = info.corners, direction = info.direction)
    }
}
