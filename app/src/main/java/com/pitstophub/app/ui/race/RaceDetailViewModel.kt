package com.pitstophub.app.ui.race

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.CircuitEntry
import com.pitstophub.app.data.model.Race
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

data class RaceDetailState(
    val data: CategoryData? = null,
    val race: Race? = null,
    val results: ResultsState = ResultsState.NotAvailable,
    /** Ficha tecnica do circuito (extensao, curvas, recorde, DRS, frenagem), quando o site tambem a tem. */
    val circuit: CircuitEntry? = null,
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
                val race = data.category.calendar.firstOrNull { it.id == raceId }
                if (race == null) {
                    _state.value = RaceDetailState(data = data, notFound = true)
                    return@launch
                }
                _state.value = RaceDetailState(
                    data = data,
                    race = race,
                    results = initialResults(race),
                    circuit = container.webData.circuitFor(data.category, race),
                )
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
