package com.pitstophub.app.ui.drivers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.DriverTitle
import com.pitstophub.app.data.model.F1Champion
import com.pitstophub.app.data.model.LocalizedText
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.domain.DriverRound
import com.pitstophub.app.domain.SeasonStats
import com.pitstophub.app.domain.TeammateDuel
import com.pitstophub.app.domain.seasonStats
import com.pitstophub.app.domain.teammateDuel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DriverDetailState(
    val data: CategoryData? = null,
    /** Piloto do grid atual da categoria. */
    val driver: Driver? = null,
    /** Campeao historico da F1 que nao esta mais no grid (perfil so de carreira). */
    val champion: F1Champion? = null,
    val team: Team? = null,
    val teammate: Driver? = null,
    val standing: StandingItem? = null,
    /** Etapas do piloto na temporada (F1); null enquanto carrega ou quando nao ha fonte. */
    val rounds: List<DriverRound>? = null,
    val stats: SeasonStats? = null,
    val duel: TeammateDuel? = null,
    val seasonLoading: Boolean = false,
    val bio: LocalizedText? = null,
    val titles: List<DriverTitle> = emptyList(),
    val notFound: Boolean = false,
) {
    val isChampionOnly: Boolean get() = driver == null && champion != null
}

class DriverDetailViewModel(
    private val container: AppContainer,
    private val categoryId: String,
    private val driverId: String,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverDetailState())
    val state: StateFlow<DriverDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val repository = container.categories
        val data = try {
            repository.cached(categoryId) ?: repository.staticData(categoryId)?.also { show(it) }
            repository.load(categoryId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            repository.staticData(categoryId) ?: run {
                _state.value = DriverDetailState(notFound = true)
                return
            }
        }
        show(data)
        val driver = data.category.drivers.firstOrNull { it.id == driverId } ?: return
        if (categoryId != "f1") return

        val mate = _state.value.teammate
        _state.value = _state.value.copy(seasonLoading = true)
        try {
            coroutineScope {
                val mine = async { repository.driverSeason(driver.id, data.season) }
                val theirs = async { mate?.let { runCatching { repository.driverSeason(it.id, data.season) }.getOrDefault(emptyList()) }.orEmpty() }
                val rounds = mine.await()
                val mateRounds = theirs.await()
                _state.value = _state.value.copy(
                    rounds = rounds,
                    stats = if (rounds.isEmpty()) null else seasonStats(rounds),
                    duel = if (rounds.isEmpty() || mateRounds.isEmpty()) null else teammateDuel(rounds, mateRounds).takeUnless { it.isEmpty },
                    seasonLoading = false,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            _state.value = _state.value.copy(seasonLoading = false)
        }
    }

    private fun show(data: CategoryData) {
        val category = data.category
        val driver = category.drivers.firstOrNull { it.id == driverId }
        val champion = if (categoryId == "f1" && driver == null) container.webData.champion(driverId) else null
        if (driver == null && champion == null) {
            _state.value = DriverDetailState(data = data, notFound = true)
            return
        }
        val team = driver?.let { d -> category.teams.firstOrNull { it.id == d.teamId } }
        val mate = driver?.let { d -> category.drivers.filter { it.teamId == d.teamId && it.id != d.id }.minByOrNull { it.number.toIntOrNull() ?: Int.MAX_VALUE } }
        _state.value = _state.value.copy(
            data = data,
            driver = driver,
            champion = champion,
            team = team,
            teammate = mate,
            standing = driver?.let { standingOfDriver(category, it) },
            bio = container.webData.bio(categoryId, driverId),
            titles = container.webData.titles(categoryId, driverId),
            notFound = false,
        )
    }
}
