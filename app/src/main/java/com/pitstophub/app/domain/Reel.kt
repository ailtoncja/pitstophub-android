package com.pitstophub.app.domain

import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import java.time.Instant
import java.time.ZoneId

/** Fase de um item do reel: as tres da largada mais "temporada encerrada" (mostra o ultimo resultado). */
enum class ReelPhase { COUNTDOWN, LIVE, AWAITING, WRAPPED }

data class ReelItem(
    val data: CategoryData,
    val race: Race,
    val phase: ReelPhase,
    val start: Instant,
    val round: Int?,
    val rounds: Int,
)

/** Mesma regra do site (src/lib/reel.ts): uma largada por categoria, ao vivo primeiro, depois as seguidas, depois as demais. */
object Reel {

    fun build(all: Collection<CategoryData>, followed: Set<String>, now: Instant, max: Int = 8, zone: ZoneId = ZoneId.systemDefault()): List<ReelItem> {
        val items = all.mapNotNull { data ->
            val calendar = data.category.calendar
            val next = RaceTime.nextRace(calendar, now, zone)
            val race = next ?: RaceTime.lastRace(calendar, zone) ?: return@mapNotNull null
            val valid = calendar.filter { it.status != RaceStatus.CANCELLED }
            val index = valid.indexOfFirst { it.id == race.id }
            ReelItem(
                data = data,
                race = race,
                phase = if (next == null) {
                    ReelPhase.WRAPPED
                } else {
                    when (RaceTime.phase(next, now, zone)) {
                        RacePhase.COUNTDOWN -> ReelPhase.COUNTDOWN
                        RacePhase.LIVE -> ReelPhase.LIVE
                        RacePhase.AWAITING_RESULT -> ReelPhase.AWAITING
                    }
                },
                start = RaceTime.startInstant(race, zone),
                round = race.round ?: if (index >= 0) index + 1 else null,
                rounds = valid.size,
            )
        }
        fun rank(item: ReelItem): Int {
            val follows = item.data.category.id in followed
            return when (item.phase) {
                ReelPhase.LIVE -> 0
                ReelPhase.WRAPPED -> if (follows) 3 else 4
                else -> if (follows) 1 else 2
            }
        }
        return items.sortedWith(compareBy<ReelItem> { rank(it) }.thenBy { it.start }).take(max)
    }

    /** Todas as proximas largadas de todas as categorias, em ordem (para a Agenda). */
    fun upcoming(all: Collection<CategoryData>, now: Instant, zone: ZoneId = ZoneId.systemDefault()): List<Pair<CategoryData, Race>> =
        all.flatMap { data ->
            data.category.calendar
                .filter { it.status == RaceStatus.UPCOMING && RaceTime.phase(it, now, zone) != RacePhase.AWAITING_RESULT }
                .map { data to it }
        }.sortedBy { RaceTime.startInstant(it.second, zone) }
}
