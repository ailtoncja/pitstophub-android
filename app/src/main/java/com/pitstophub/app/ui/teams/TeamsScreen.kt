package com.pitstophub.app.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SportsMotorsports
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.repo.TextUtil
import com.pitstophub.app.ui.CategoryState
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.tributes.LocalTributes
import com.pitstophub.app.ui.components.OfflineNotice
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText

private const val MAX_DRIVERS_ON_CARD = 4

/** Ordena pela posicao no campeonato de construtores/equipes quando ha classificacao; senao, ordem do catalogo. */
internal fun orderedTeams(category: Category): List<Team> {
    val standings = category.standings
    val ranking = (standings?.constructors ?: standings?.teams).orEmpty()
    if (ranking.isEmpty()) return category.teams
    val position = ranking.associate { TextUtil.normalize(it.name) to it.position }
    return category.teams.sortedBy { position[TextUtil.normalize(it.name)] ?: Int.MAX_VALUE }
}

@Composable
internal fun TeamCard(team: Team, drivers: List<Driver>, onClick: () -> Unit) {
    val sorted = drivers.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
    val tributes = LocalTributes.current
    PitCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box {
            Column {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(team.name, style = PitText.HeadlineMd, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val subtitle = listOfNotNull(team.car, team.raceClass).joinToString(" • ")
                            if (subtitle.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(subtitle.uppercase(), style = PitText.DataLabel, color = PitColors.TertiaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // brasao da AF Corse (WEC): 5 toques abrem uma homenagem escondida (o toque nao abre a equipe)
                        Box(
                            if (team.id == "af-corse-wec") {
                                Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tributes?.onFerrariWecTap() }
                            } else {
                                Modifier
                            },
                        ) { TeamBadge(team) }
                    }
                    if (sorted.isNotEmpty()) {
                        Spacer(Modifier.height(28.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            sorted.take(MAX_DRIVERS_ON_CARD).forEach { DriverLine(it) }
                            if (sorted.size > MAX_DRIVERS_ON_CARD) {
                                Text("+${sorted.size - MAX_DRIVERS_ON_CARD}", style = PitText.DataLabel, color = PitColors.OnSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TeamBadge(team: Team, size: Dp = 48.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(PitColors.SurfaceHighest).border(1.dp, PitColors.Hairline, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (team.badge.isNullOrBlank()) {
            Icon(Icons.Filled.SportsMotorsports, null, tint = PitColors.Primary)
        } else {
            NetworkImage(team.badge, Modifier.fillMaxSize().padding(size / 7), contentScale = ContentScale.Fit)
        }
    }
}

@Composable
private fun DriverLine(driver: Driver) {
    Row(
        Modifier.fillMaxWidth().background(PitColors.SurfaceLow).padding(vertical = 12.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(2.dp).height(24.dp).background(PitColors.OutlineVariant))
        // categorias sem numero de carro (Endurance Brasil) nao reservam a coluna do numero
        if (driver.number.isNotBlank()) {
            Text(
                driver.number,
                Modifier.width(52.dp).padding(start = 14.dp),
                style = PitText.DataValue,
                color = Color.White,
                maxLines = 1,
            )
        }
        Text(
            driver.name.uppercase(),
            Modifier.weight(1f).padding(start = if (driver.number.isNotBlank()) 0.dp else 14.dp),
            style = PitText.BodyMd.copy(letterSpacing = 0.025.em),
            color = PitColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
