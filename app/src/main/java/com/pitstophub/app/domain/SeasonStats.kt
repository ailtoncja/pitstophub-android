package com.pitstophub.app.domain

import com.pitstophub.app.data.model.StandingItem

/** Resultado de um piloto numa etapa (uma linha da Jolpica). */
data class DriverRound(
    val round: Int,
    val raceName: String,
    val date: String,
    val grid: Int?,
    val finish: Int?,
    val points: Double,
    val status: String,
    /** 1 = volta mais rapida da prova; null quando a fonte nao informou. */
    val fastestLapRank: Int?,
)

data class SeasonStats(
    val races: Int,
    val wins: Int,
    val podiums: Int,
    val podiumRate: Double,
    val poles: Int,
    val fastestLaps: Int,
    val dnfs: Int,
    val points: Double,
    val bestFinish: Int?,
    val averageFinish: Double?,
    val winRaces: List<String>,
)

private val classified = Regex("^(finished|\\+\\d+ laps?|lapped)$", RegexOption.IGNORE_CASE)

/** Numeros da temporada de um piloto, calculados das etapas reais (nada inventado). */
fun seasonStats(rounds: List<DriverRound>): SeasonStats {
    val finishes = rounds.mapNotNull { it.finish }.filter { it > 0 }
    val wins = rounds.filter { it.finish == 1 }
    val podiums = rounds.count { (it.finish ?: 0) in 1..3 }
    return SeasonStats(
        races = rounds.size,
        wins = wins.size,
        podiums = podiums,
        podiumRate = if (rounds.isEmpty()) 0.0 else podiums.toDouble() / rounds.size,
        poles = rounds.count { it.grid == 1 },
        fastestLaps = rounds.count { it.fastestLapRank == 1 },
        dnfs = rounds.count { it.status.isNotEmpty() && !classified.matches(it.status.trim()) },
        points = rounds.sumOf { it.points },
        bestFinish = finishes.minOrNull(),
        averageFinish = if (finishes.isEmpty()) null else finishes.average(),
        winRaces = wins.map { it.raceName },
    )
}

data class Duel(val mine: Int, val theirs: Int, val total: Int) {
    /** Porcentagem de etapas em que o piloto ficou na frente (0..100). */
    val mineShare: Double get() = percent(mine, total)
}

data class TeammateDuel(val grid: Duel, val race: Duel) {
    val isEmpty: Boolean get() = grid.total == 0 && race.total == 0
}

/** Confronto direto com o companheiro de equipe, so nas etapas em que os dois largaram/terminaram. */
fun teammateDuel(mine: List<DriverRound>, mate: List<DriverRound>): TeammateDuel {
    val mateByRound = mate.associateBy { it.round }
    var gridMine = 0; var gridTheirs = 0; var gridTotal = 0
    var raceMine = 0; var raceTheirs = 0; var raceTotal = 0
    for (row in mine) {
        val other = mateByRound[row.round] ?: continue
        val myGrid = row.grid ?: 0
        val otherGrid = other.grid ?: 0
        if (myGrid > 0 && otherGrid > 0) {
            gridTotal++
            if (myGrid < otherGrid) gridMine++ else if (otherGrid < myGrid) gridTheirs++
        }
        val myFinish = row.finish ?: 0
        val otherFinish = other.finish ?: 0
        if (myFinish > 0 && otherFinish > 0) {
            raceTotal++
            if (myFinish < otherFinish) raceMine++ else if (otherFinish < myFinish) raceTheirs++
        }
    }
    return TeammateDuel(Duel(gridMine, gridTheirs, gridTotal), Duel(raceMine, raceTheirs, raceTotal))
}

fun percent(part: Int, total: Int): Double = if (total > 0) part.toDouble() / total * 100.0 else 0.0

/** Pontos que a equipe fez em cada rodada (corrida + sprint). */
data class TeamRoundPoints(val round: Int, val raceName: String, val points: Double)

/** Pontos acumulados rodada a rodada (curva "Evolucao do campeonato"). */
fun cumulativePoints(rounds: List<TeamRoundPoints>): List<Double> {
    var total = 0.0
    return rounds.sortedBy { it.round }.map { total += it.points; total }
}

/** Uma linha da classificacao com a diferenca para o lider e o movimento desde a rodada anterior. */
data class StandingLine(
    val item: StandingItem,
    /** Pontos a menos que o lider (0 para o lider). */
    val gap: Double,
    /** Fracao dos pontos do lider (0..1). */
    val share: Double,
    /** Posicoes ganhas (+) ou perdidas (-) desde a rodada anterior; null sem dado. */
    val delta: Int?,
    /** Pontos feitos na ultima etapa; null sem dado. */
    val lastRacePoints: Double?,
)

fun buildStandingRows(
    items: List<StandingItem>,
    previous: List<StandingItem>? = null,
    lastRacePoints: Map<String, Double>? = null,
): List<StandingLine> {
    val sorted = items.sortedWith(compareBy<StandingItem> { it.position }.thenByDescending { it.points })
    val leader = sorted.firstOrNull()?.points ?: 0.0
    val before = previous.orEmpty().associate { it.name to it.position }
    return sorted.map { item ->
        StandingLine(
            item = item,
            gap = (leader - item.points).coerceAtLeast(0.0),
            share = if (leader > 0) item.points / leader else 0.0,
            delta = before[item.name]?.let { it - item.position },
            lastRacePoints = lastRacePoints?.let { it[item.name] ?: 0.0 },
        )
    }
}
