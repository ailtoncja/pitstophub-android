package com.pitstophub.app.ui.standings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.repo.CategoryRepository
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.domain.RaceTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * O que mudou desde a rodada anterior (so F1, via Jolpica): a classificacao de antes -- para as setas de
 * subida/queda -- e os pontos feitos na ultima etapa.
 */
data class StandingsMovement(
    val categoryId: String? = null,
    val previousDrivers: List<StandingItem>? = null,
    val previousConstructors: List<StandingItem>? = null,
    val driverPoints: Map<String, Double>? = null,
    val teamPoints: Map<String, Double>? = null,
    val lastRaceName: String? = null,
)

class StandingsViewModel(private val repository: CategoryRepository) : ViewModel() {

    private val _movement = MutableStateFlow(StandingsMovement())
    val movement: StateFlow<StandingsMovement> = _movement.asStateFlow()

    private var job: Job? = null
    private var loadedKey: String? = null

    fun load(data: CategoryData) {
        val category = data.category
        if (category.id != "f1") {
            loadedKey = null
            _movement.value = StandingsMovement(category.id)
            return
        }
        val last = category.calendar.filter { it.status == RaceStatus.COMPLETED && it.round != null }
            .maxByOrNull { RaceTime.startInstant(it) } ?: return
        val round = last.round ?: return
        val key = "${category.id}:${data.season}:$round:${category.standings?.drivers?.firstOrNull()?.points}"
        if (key == loadedKey) return
        loadedKey = key
        job?.cancel()
        job = viewModelScope.launch {
            try {
                coroutineScope {
                    val previous = async { if (round > 1) runCatching { repository.standingsAfterRound(category, data.season, round - 1) }.getOrNull() else null }
                    val results = async { runCatching { repository.f1Results(data.season, round) }.getOrDefault(emptyList()) }
                    val rows = results.await()
                    val drivers = linkedMapOf<String, Double>()
                    val teams = linkedMapOf<String, Double>()
                    for (row in rows) {
                        val driver = DriverLookup.find(category.drivers, row.driver)
                        val driverName = driver?.name ?: row.driver
                        drivers[driverName] = (drivers[driverName] ?: 0.0) + row.points
                        val teamName = driver?.let { d -> category.teams.firstOrNull { it.id == d.teamId }?.name } ?: row.team
                        teams[teamName] = (teams[teamName] ?: 0.0) + row.points
                    }
                    val before = previous.await()
                    _movement.value = StandingsMovement(
                        categoryId = category.id,
                        previousDrivers = before?.drivers,
                        previousConstructors = before?.constructors,
                        driverPoints = drivers.takeIf { rows.isNotEmpty() },
                        teamPoints = teams.takeIf { rows.isNotEmpty() },
                        lastRaceName = last.enName ?: last.name,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                loadedKey = null
            }
        }
    }
}
