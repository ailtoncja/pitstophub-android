package com.pitstophub.app.ui.teams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.model.TeamBio
import com.pitstophub.app.domain.TeamRoundPoints
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TeamDetailState(
    val data: CategoryData? = null,
    val team: Team? = null,
    val bio: TeamBio? = null,
    /** Pontos da equipe por rodada (F1): alimenta a curva "Evolucao do campeonato". */
    val points: List<TeamRoundPoints> = emptyList(),
    val notFound: Boolean = false,
)

class TeamDetailViewModel(
    private val container: AppContainer,
    private val categoryId: String,
    private val teamId: String,
) : ViewModel() {

    private val repository = container.categories

    private val _state = MutableStateFlow(TeamDetailState())
    val state: StateFlow<TeamDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val data = repository.load(categoryId)
                val team = data.category.teams.firstOrNull { it.id == teamId }
                if (team == null) {
                    _state.value = TeamDetailState(data = data, notFound = true)
                    return@launch
                }
                _state.value = TeamDetailState(data = data, team = team, bio = repository.teamBio(categoryId, team))
                if (categoryId == "f1") {
                    val points = runCatching { repository.teamSeasonPoints(data.season, team.id) }.getOrDefault(emptyList())
                    _state.value = _state.value.copy(points = points)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = TeamDetailState(notFound = true)
            }
        }
    }
}
