package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.RaceStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Numeros que resumem uma categoria no menu e na lista (equipes e etapas), como `categorySummary` do PWA. */
data class CategorySummary(val teams: Int, val rounds: Int, val completed: Int) {
    companion object {
        fun of(category: Category): CategorySummary = CategorySummary(
            teams = category.teams.size,
            rounds = category.calendar.count { it.status != RaceStatus.CANCELLED },
            completed = category.calendar.count { it.status == RaceStatus.COMPLETED },
        )

        /** Ha corrida da categoria hoje (no fuso do aparelho): acende o pontinho de "ao vivo". */
        fun isActiveToday(category: Category, now: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean {
            val today = now.atZone(zone).toLocalDate()
            return category.calendar.any { race ->
                race.status != RaceStatus.CANCELLED && runCatching { LocalDate.parse(race.date) == today }.getOrDefault(false)
            }
        }
    }
}
