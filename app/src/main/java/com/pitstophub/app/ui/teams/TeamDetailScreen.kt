package com.pitstophub.app.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.data.repo.TextUtil
import com.pitstophub.app.domain.Nationality
import com.pitstophub.app.domain.cumulativePoints
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.components.FollowKind
import com.pitstophub.app.ui.components.FollowToggle
import com.pitstophub.app.ui.components.BioCard
import com.pitstophub.app.ui.components.LineChart
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitStatTile
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.components.StatusChip
import com.pitstophub.app.ui.components.carbonTexture
import com.pitstophub.app.ui.drivers.standingOfDriver
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.ui.tributes.LocalTributes

@Composable
fun TeamDetailScreen(
    state: TeamDetailState,
    onBack: () -> Unit,
    onOpenDriver: (String) -> Unit,
    onOpenStandings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val team = state.team
    val category = state.data?.category
    val portuguese = isPortuguese()
    val account = LocalAccount.current
    val settings by account.settings.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(
            title = team?.name ?: stringResource(R.string.nav_grid),
            onBack = onBack,
            actions = {
                if (team != null && category != null) {
                    FollowToggle(FollowKind.TEAM, settings.followsTeam(category.id, team.id), { account.toggleTeam(category.id, team.id, team.name) }, Modifier.padding(end = 4.dp))
                }
            },
        )
        if (team == null || category == null) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.TopStart) {
                Text(
                    stringResource(if (state.notFound) R.string.team_not_found else R.string.loading),
                    style = PitText.BodyMd, color = PitColors.OnSurfaceVariant,
                )
            }
            return@Column
        }
        val drivers = category.drivers.filter { it.teamId == team.id }.sortedBy { it.number.toIntOrNull() ?: Int.MAX_VALUE }
        val ranking = (category.standings?.constructors ?: category.standings?.teams).orEmpty().sortedBy { it.position }
        val standingIndex = ranking.indexOfFirst { TextUtil.normalize(it.name) == TextUtil.normalize(team.name) }
        val standing = ranking.getOrNull(standingIndex)
        val neighbour = if (standingIndex > 0) ranking[standingIndex - 1] else ranking.getOrNull(standingIndex + 1)
        val wins = remember(category, team) { teamWins(category, team) }
        val totalDriverPoints = drivers.sumOf { standingOfDriver(category, it)?.points ?: 0.0 }
        val evolution = remember(state.points) { cumulativePoints(state.points) }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "hero") { TeamHero(team) }
            item(key = "standing") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PitStatTile(stringResource(R.string.team_position), standing?.let { "P${it.position}" } ?: "—", Modifier.weight(1f), highlight = standing?.position == 1)
                        PitStatTile(stringResource(R.string.points_label), standing?.let { formatPoints(it.points) } ?: "—", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PitStatTile(
                            stringResource(R.string.team_wins),
                            wins.toString(),
                            Modifier.weight(1f),
                            highlight = wins > 0,
                            caption = if (wins == 0) stringResource(R.string.team_no_wins) else null,
                        )
                        PitStatTile(
                            stringResource(R.string.team_gap),
                            if (standing != null && neighbour != null) signedPoints(standing.points - neighbour.points) else "—",
                            Modifier.weight(1f),
                            caption = neighbour?.let { stringResource(R.string.team_vs, it.name) },
                        )
                    }
                }
            }
            if (drivers.isNotEmpty()) {
                item(key = "drivers-header") { SectionHeader(stringResource(R.string.team_drivers)) }
                items(drivers, key = { it.id }) { driver ->
                    DriverCard(driver, category, totalDriverPoints, onClick = { onOpenDriver(driver.id) })
                }
            }
            if (evolution.size > 1) {
                item(key = "evolution") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.team_evolution))
                        PitCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("R%02d · %s".format(state.points.first().round, formatPoints(evolution.first())), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                                    Text("R%02d · %s".format(state.points.last().round, formatPoints(evolution.last())), style = PitText.DataLabelSm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PitColors.OnSurface)
                                }
                                LineChart(evolution)
                            }
                        }
                    }
                }
            }
            state.bio?.let { bio ->
                item(key = "about") {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        SectionHeader(stringResource(R.string.team_about))
                        BioCard(bio.text(portuguese))
                    }
                }
            }
            if (ranking.isNotEmpty()) {
                item(key = "full") { PitOutlineButton(stringResource(R.string.team_full_standings, category.name), onOpenStandings, Modifier.fillMaxWidth(), dim = true) }
            }
        }
    }
}

/** Corridas ganhas por qualquer piloto da equipe (pelo nome do vencedor no calendario). */
internal fun teamWins(category: Category, team: Team): Int =
    category.calendar.count { race ->
        race.winner?.split(Regex("\\s*/\\s*"))?.any { part -> DriverLookup.find(category.drivers, part.trim())?.teamId == team.id } == true
    }

private fun signedPoints(value: Double): String = (if (value > 0) "+" else if (value < 0) "-" else "") + formatPoints(kotlin.math.abs(value))

@Composable
private fun TeamHero(team: Team) {
    val tributes = LocalTributes.current
    Box(
        Modifier.fillMaxWidth().height(240.dp).clip(PitShapes.Soft)
            .background(Brush.linearGradient(listOf(Color(0xFF2E2E33), PitColors.Asphalt, PitColors.Carbon)))
            .border(1.dp, PitColors.Hairline, PitShapes.Soft)
            .carbonTexture(0.04f),
    ) {
        val art = team.clearart ?: team.badge
        NetworkImage(
            art,
            Modifier.align(Alignment.Center).fillMaxWidth().height(170.dp).padding(horizontal = 24.dp),
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.45f to Color.Transparent, 1f to PitColors.Carbon.copy(alpha = 0.9f)),
            ),
        )
        Row(
            Modifier.align(Alignment.BottomStart).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // brasao da AF Corse (WEC): 5 toques abrem uma homenagem escondida
            Box(
                if (team.id == "af-corse-wec") {
                    Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { tributes?.onFerrariWecTap() }
                } else {
                    Modifier
                },
            ) { TeamBadge(team, 44.dp) }
            Column {
                Text(team.name, style = PitText.HeadlineMd, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    team.car?.let { Text(it, style = PitText.DataLabel, color = PitColors.OnSurfaceVariant) }
                    team.raceClass?.let { StatusChip(it) }
                }
                team.principal?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun DriverCard(driver: Driver, category: Category, totalPoints: Double, onClick: () -> Unit) {
    val standing: StandingItem? = standingOfDriver(category, driver)
    val share = if (standing != null && totalPoints > 0) (standing.points / totalPoints).toFloat().coerceIn(0f, 1f) else 0f
    PitCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column {
            Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(4.dp).fillMaxSize().background(PitColors.OutlineVariant))
                Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                    if (driver.number.isNotBlank()) {
                        Text(driver.number, style = PitText.DataValue.copy(fontSize = 28.sp, lineHeight = 30.sp), color = Color.White)
                    }
                    Text(driver.name, style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val nationality = driver.nationality
                    if (nationality.isNotBlank()) {
                        Text("${Nationality.flag(nationality)} ${Nationality.label(nationality, isPortuguese())}".trim(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 1)
                    }
                }
                if (standing != null) {
                    Column(Modifier.padding(end = 4.dp), horizontalAlignment = Alignment.End) {
                        Text(formatPoints(standing.points), style = PitText.DataValue, color = Color.White)
                        Text("P${standing.position}", style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    }
                }
                val portrait = driver.cutout ?: driver.image
                if (portrait != null) {
                    NetworkImage(
                        portrait,
                        Modifier.size(width = 96.dp, height = 96.dp),
                        contentScale = if (driver.cutout != null) ContentScale.Fit else ContentScale.Crop,
                        alignment = if (driver.cutout != null) Alignment.BottomCenter else Alignment.TopCenter,
                    )
                }
            }
            if (standing != null && totalPoints > 0) {
                Row(Modifier.fillMaxWidth().height(4.dp).background(PitColors.SurfaceHighest)) {
                    if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(PitColors.OnSurfaceVariant))
                    if (share < 1f) Box(Modifier.weight(1f - share).fillMaxHeight())
                }
            }
        }
    }
}
