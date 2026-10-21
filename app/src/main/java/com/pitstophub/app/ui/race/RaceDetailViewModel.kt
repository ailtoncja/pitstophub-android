package com.pitstophub.app.ui.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.CircuitRouting
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RallyInfo
import com.pitstophub.app.domain.RaceChange
import com.pitstophub.app.domain.RaceChanges
import com.pitstophub.app.domain.RaceRoute
import com.pitstophub.app.domain.RaceRoutes
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.remote.RaceWeather
import com.pitstophub.app.data.remote.WeatherClient
import com.pitstophub.app.data.repo.RaceResultRow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant

sealed interface ResultsState {
    /** Categoria sem tabela de resultados, ou corrida ainda nao disputada. */
    data object NotAvailable : ResultsState
    data object Loading : ResultsState
    data class Ready(val rows: List<RaceResultRow>) : ResultsState
    data object Failed : ResultsState
}

/** Ficha do rali (WRC): carregando (so o id, pelo indice leve de datas) ou pronta. */
sealed interface RallyState {
    data object Loading : RallyState
    data class Ready(val info: RallyInfo) : RallyState
}

data class RaceDetailState(
    val data: CategoryData? = null,
    val race: Race? = null,
    val results: ResultsState = ResultsState.NotAvailable,
    /** Ficha tecnica do circuito (extensao, curvas, recorde, Straight Mode, frenagem), quando o site tambem a tem. */
    val circuit: CircuitInfo? = null,
    /** Etapa cancelada -> substituta(s); substituta -> cancelada(s) (pares do catalogo). */
    val change: RaceChange? = null,
    /** No WRC a ficha do rali ocupa o lugar do tracado; null fora do WRC ou sem ficha. */
    val rally: RallyState? = null,
    /** Previsao do tempo para o dia da corrida; null quando nao ha coordenadas ou a data esta fora da janela. */
    val weather: RaceWeather? = null,
    val notFound: Boolean = false,
)

class RaceDetailViewModel(
    private val container: AppContainer,
    private val categoryId: String,
    private val raceId: String,
) : ViewModel() {

    private val repository = container.categories

    private val _state = MutableStateFlow(RaceDetailState())
    val state: StateFlow<RaceDetailState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            try {
                val data = repository.load(categoryId)
                // O calendario ao vivo troca os ids do catalogo e nao lista etapas canceladas: o catalogo cobre esses links.
                val race = when (val route = RaceRoutes.resolve(data.category.calendar, data.catalogCalendar, raceId)) {
                    is RaceRoute.Found -> route.race
                    is RaceRoute.Redirect -> data.category.calendar.firstOrNull { it.id == route.raceId }
                    RaceRoute.Missing -> null
                }
                if (race == null) {
                    _state.value = RaceDetailState(data = data, notFound = true)
                    return@launch
                }
                // Rali nao tem circuito: no WRC a ficha do rali (mapa das especiais + itinerario) ocupa o lugar do tracado.
                val rallyId = if (categoryId == CircuitRouting.WRC && race.status != RaceStatus.CANCELLED) container.webData.rallyIdFor(race) else null
                _state.value = RaceDetailState(
                    data = data,
                    race = race,
                    results = initialResults(race),
                    circuit = container.webData.circuitFor(categoryId, race),
                    change = RaceChanges.raceChange(data.catalogCalendar, race),
                    rally = rallyId?.let { RallyState.Loading },
                )
                if (rallyId != null) {
                    val info = container.webData.rally(rallyId)
                    _state.value = _state.value.copy(rally = info?.let { RallyState.Ready(it) })
                }
                loadWeather(race)
                if (categoryId == "f1" && race.status == RaceStatus.COMPLETED && race.round != null) {
                    try {
                        val rows = repository.f1Results(data.season, race.round)
                        _state.value = _state.value.copy(results = if (rows.isEmpty()) ResultsState.NotAvailable else ResultsState.Ready(rows))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value = _state.value.copy(results = ResultsState.Failed)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = RaceDetailState(notFound = true)
            }
        }
    }

    private suspend fun loadWeather(race: Race) {
        val lat = race.lat ?: return
        val lng = race.lng ?: return
        if (race.status != RaceStatus.UPCOMING || !WeatherClient.windowOpen(race.date, Instant.now())) return
        try {
            _state.value = _state.value.copy(weather = container.weather.forecast(lat, lng, race.date))
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // sem previsao: a secao simplesmente nao aparece
        }
    }

    private fun initialResults(race: Race): ResultsState =
        if (categoryId == "f1" && race.status == RaceStatus.COMPLETED && race.round != null) ResultsState.Loading else ResultsState.NotAvailable
}
