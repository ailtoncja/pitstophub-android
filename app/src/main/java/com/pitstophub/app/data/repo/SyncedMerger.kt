package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryStandings
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.remote.SyncedRaceRow
import com.pitstophub.app.data.remote.SyncedStandingRow

/**
 * Categorias sem API propria (F2, F3, F1 Academy, WEC, IMSA, DTM, IndyCar, NASCAR, WRC): o calendario e a
 * classificacao sincronizados (job do GitHub Actions) ficam nas tabelas `synced_*` do Supabase.
 * Porte de `src/synced-races.ts`, inclusive as correcoes de WEC/IMSA.
 */
object SyncedMerger {

    val categoryIds = listOf("f2", "f3", "f1-academy", "wec", "imsa", "dtm", "indy", "nascar", "wrc")

    fun isSynced(categoryId: String): Boolean = categoryId in categoryIds

    fun mergeCalendar(base: Category, rows: List<SyncedRaceRow>): Category {
        if (rows.isEmpty()) return base
        val synced = rows.map { toRace(it) }
            .map { race -> normalizeWinner(preferOfficialWinner(base, race), base) }
        val calendar = dropNonChampionship(base, fillMissingOfficial(base, synced)).map { normalizeWinner(it, base) }
        val years = calendar.map { it.date.take(4) }.toSet()
        val changedSeason = base.calendar.any { it.date.take(4) !in years }
        return base.copy(calendar = calendar, standings = if (changedSeason) null else base.standings)
    }

    fun mergeStandings(base: Category, rows: List<SyncedStandingRow>): Category {
        if (rows.isEmpty()) return base
        val matching = matchingStandings(base, rows)
        if (matching.isEmpty()) return base.copy(standings = null)
        val items = matching.map { StandingItem(it.position, it.name, it.points, it.team) }
        return base.copy(standings = (base.standings ?: CategoryStandings()).copy(drivers = items))
    }

    fun matchingStandings(base: Category, rows: List<SyncedStandingRow>): List<SyncedStandingRow> {
        val years = base.calendar.mapNotNull { it.date.take(4).toIntOrNull() }.toSet()
        return rows.filter { it.season != null && years.size == 1 && it.season in years }
    }

    private fun toRace(row: SyncedRaceRow): Race = Race(
        id = row.raceId,
        name = row.name,
        enName = row.enName,
        location = row.location.orEmpty(),
        enLocation = row.enLocation,
        circuit = row.circuit.orEmpty(),
        date = row.date,
        startsAt = row.startsAt,
        status = when (row.status) {
            "completed" -> RaceStatus.COMPLETED
            "cancelled" -> RaceStatus.CANCELLED
            else -> RaceStatus.UPCOMING
        },
        winner = row.winner,
        round = row.round,
    )

    // WEC/IMSA: a TheSportsDB devolve "equipe #carro" (e as vezes o carro errado). O catalogo estatico guarda
    // o piloto confirmado nos sites oficiais; casa por data OU nome.
    private fun preferOfficialWinner(category: Category, race: Race): Race {
        if (category.id != "wec" && category.id != "imsa") return race
        if (race.status == RaceStatus.CANCELLED) return race
        val official = category.calendar.firstOrNull { !it.winner.isNullOrBlank() && isSameEnduranceRace(it, race) }
        return if (official?.winner != null) race.copy(winner = official.winner) else race
    }

    // Le Mans e Long Beach costumam faltar no synced_races; sem isso o merge substituia o calendario
    // inteiro e as duas etapas sumiam da UI.
    private fun fillMissingOfficial(category: Category, synced: List<Race>): List<Race> {
        if (category.id != "wec" && category.id != "imsa") return synced
        val missing = category.calendar.filter { base ->
            synced.any { it.date.take(4) == base.date.take(4) } && synced.none { isSameEnduranceRace(base, it) }
        }
        if (missing.isEmpty()) return synced
        return (synced + missing).sortedBy { it.date }
    }

    // O Roar Before the Rolex 24 e teste de pre-temporada e nao pontua.
    private fun dropNonChampionship(category: Category, races: List<Race>): List<Race> {
        if (category.id != "imsa") return races
        return races.filterNot { Regex("roar before", RegexOption.IGNORE_CASE).containsMatchIn(label(it)) }
    }

    private fun isSameEnduranceRace(a: Race, b: Race): Boolean {
        if (a.date.take(4) != b.date.take(4)) return false
        if (a.date.isNotEmpty() && a.date == b.date) return true
        if (isLeMans(a) && isLeMans(b)) return true
        if (isLongBeach(a) && isLongBeach(b)) return true
        val an = TextUtil.normalize(a.enName ?: a.name)
        val bn = TextUtil.normalize(b.enName ?: b.name)
        return an.isNotEmpty() && an == bn
    }

    private fun label(race: Race): String = "${race.enName.orEmpty()} ${race.name} ${race.location} ${race.enLocation.orEmpty()}"

    private fun isLeMans(race: Race): Boolean {
        val text = label(race).lowercase()
        return "le mans" in text && "lone star" !in text
    }

    private fun isLongBeach(race: Race): Boolean = "long beach" in label(race).lowercase()

    // A TheSportsDB traz "U. Ugochukwu" (inicial + sobrenome); casa com o nome completo do catalogo.
    private fun normalizeWinner(race: Race, category: Category): Race {
        val winner = race.winner ?: return race
        val normalized = TextUtil.normalize(winner)
        category.drivers.firstOrNull { TextUtil.normalize(it.name) == normalized }?.let { return race.copy(winner = it.name) }
        val last = TextUtil.lastName(winner)
        val bySurname = category.drivers.firstOrNull { TextUtil.lastName(it.name) == last }
        return if (bySurname != null) race.copy(winner = bySurname.name) else race
    }
}
