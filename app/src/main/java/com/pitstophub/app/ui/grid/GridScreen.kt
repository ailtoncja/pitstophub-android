package com.pitstophub.app.ui.grid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.ui.CategoryState
import com.pitstophub.app.ui.components.OfflineNotice
import com.pitstophub.app.ui.components.PitSegmented
import com.pitstophub.app.ui.drivers.DriverCard
import com.pitstophub.app.ui.drivers.standingOfDriver
import com.pitstophub.app.ui.teams.TeamCard
import com.pitstophub.app.ui.teams.orderedTeams
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText

/** Aba "Grid": pilotos e equipes da categoria selecionada (a ordem segue a classificacao quando ela existe). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GridScreen(
    state: CategoryState,
    onOpenDriver: (Driver) -> Unit,
    onOpenTeam: (Team) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val category = data?.category
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 = pilotos, 1 = equipes
    val drivers = remember(category) { category?.let(::orderedDrivers).orEmpty() }
    val teams = remember(category) { category?.let(::orderedTeams).orEmpty() }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (data?.offline == true) item(key = "offline") { OfflineNotice() }
            item(key = "header") {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                    Box(Modifier.width(4.dp).fillMaxHeight().background(PitColors.PrimaryContainer))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text(stringResource(R.string.grid_title).uppercase(), style = PitText.HeadlineLg, color = PitColors.OnSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(listOfNotNull(category?.name, data?.season).joinToString(" • "), style = PitText.DataLabel, color = PitColors.OnSurfaceVariant)
                    }
                }
            }
            item(key = "toggle") {
                PitSegmented(
                    labels = listOf(
                        stringResource(R.string.grid_drivers, drivers.size),
                        stringResource(R.string.grid_teams, teams.size),
                    ),
                    selected = mode,
                    onSelect = { mode = it },
                )
            }
            val empty = if (mode == 0) drivers.isEmpty() else teams.isEmpty()
            if (empty) {
                item(key = "empty") {
                    Text(
                        stringResource(
                            when {
                                state.loading -> R.string.loading
                                mode == 0 -> R.string.drivers_empty
                                else -> R.string.teams_empty
                            },
                        ),
                        style = PitText.BodyMd,
                        color = PitColors.OnSurfaceVariant,
                    )
                }
            } else if (mode == 0) {
                items(drivers, key = { "d-${it.id}" }) { driver ->
                    DriverCard(
                        driver = driver,
                        team = category!!.teams.firstOrNull { it.id == driver.teamId },
                        standing = standingOfDriver(category, driver),
                        onClick = { onOpenDriver(driver) },
                    )
                }
            } else {
                items(teams, key = { "t-${it.id}" }) { team ->
                    TeamCard(team, category!!.drivers.filter { it.teamId == team.id }, onClick = { onOpenTeam(team) })
                }
            }
        }
    }
}

/** Pilotos ordenados pela posicao no campeonato; quem nao aparece na tabela vem depois, pelo numero do carro. */
internal fun orderedDrivers(category: Category): List<Driver> {
    val position = category.drivers.associateWith { standingOfDriver(category, it)?.position }
    return category.drivers.sortedWith(
        compareBy<Driver> { position[it] ?: Int.MAX_VALUE }.thenBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.name },
    )
}
