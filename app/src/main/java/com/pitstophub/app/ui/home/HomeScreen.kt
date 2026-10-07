package com.pitstophub.app.ui.home

import com.pitstophub.app.ui.components.HowItWorksCard
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RacePhase
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.CategoryState
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.components.CategoryChip
import com.pitstophub.app.ui.components.CountdownPanel
import com.pitstophub.app.ui.components.HeroBackdrop
import com.pitstophub.app.ui.components.GlassPanel
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.components.SkewBadge
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: CategoryState,
    categories: List<Category>,
    selectedId: String,
    onSelectCategory: (String) -> Unit,
    onOpenRace: (Race) -> Unit,
    onOpenDriver: (String) -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenStandings: () -> Unit,
    onOpenRules: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            item(key = "hero") { NextRaceHero(state.data, selectedId, onOpenRace) }
            item(key = "categories") { CategorySelector(categories, selectedId, onSelectCategory, onOpenCategory) }
            state.data?.category?.takeIf { it.rulesFormat.isNotEmpty() }?.let { category ->
                item(key = "howto-${category.id}") { HowItWorksCard(category.name, onOpenRules) }
            }
            state.data?.category?.let { category ->
                if (category.standings?.isEmpty == false) {
                    item(key = "leaders-${category.id}") { LeadersSection(category, onOpenDriver, onOpenTeam, onOpenStandings) }
                }
            }
        }
    }
}

// --- hero ------------------------------------------------------------------------------------------

@Composable
private fun NextRaceHero(data: CategoryData?, categoryId: String, onOpenRace: (Race) -> Unit) {
    val now by rememberNow()
    val portuguese = isPortuguese()
    val calendar = data?.category?.calendar.orEmpty()
    val next = RaceTime.nextRace(calendar, now)
    val race = next ?: RaceTime.lastRace(calendar)
    val phase = if (next != null) RaceTime.phase(next, now) else null

    Box(
        Modifier.fillMaxWidth().height(400.dp).clip(PitShapes.Card)
            .then(if (race != null) Modifier.clickable { onOpenRace(race) } else Modifier),
    ) {
        HeroBackdrop(categoryId = categoryId, categoryName = data?.category?.name.orEmpty(), race = race)

        if (race == null) return@Box

        Column(Modifier.align(Alignment.BottomStart).padding(24.dp)) {
            SkewBadge(
                text = stringResource(
                    when {
                        next == null -> R.string.home_season_wrapped
                        phase == RacePhase.LIVE -> R.string.status_live
                        phase == RacePhase.AWAITING_RESULT -> R.string.status_awaiting
                        else -> R.string.next_race
                    },
                ),
                live = phase == RacePhase.LIVE,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = race.displayName(portuguese).uppercase(),
                style = PitText.HeadlineLgMobile,
                color = Color.White,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            val place = RaceFormat.place(race.circuit, race.displayLocation(portuguese))
            if (place.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = PitColors.OnSurfaceVariant)
                    Text(place, style = PitText.DataLabel, color = PitColors.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            when {
                next == null -> WinnerPanel(race)
                phase == RacePhase.COUNTDOWN -> CountdownPanel(RaceTime.startInstant(next), now)
            }
        }
    }
}

/** Temporada encerrada: no lugar da contagem, mostra quem venceu a ultima corrida. */
@Composable
private fun WinnerPanel(race: Race) {
    val winner = race.winner ?: return
    GlassPanel {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(PitColors.Primary))
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.winner_label).uppercase(), style = PitText.DataLabelSm, color = PitColors.Tertiary)
                Spacer(Modifier.height(4.dp))
                Text(winner, style = PitText.DataValue, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// --- categorias -------------------------------------------------------------------------------------

@Composable
private fun CategorySelector(categories: List<Category>, selectedId: String, onSelect: (String) -> Unit, onOpenCategory: (String) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(selectedId, categories.size) {
        val index = categories.indexOfFirst { it.id == selectedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(end = 8.dp, bottom = 8.dp),
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryChip(
                category.name,
                selected = category.id == selectedId,
                onClick = { if (category.id == selectedId) onOpenCategory(category.id) else onSelect(category.id) },
                icon = CategoryVisuals.icon(category.id),
                accent = CategoryVisuals.accent(category.id),
            )
        }
    }
}

// --- lideres ----------------------------------------------------------------------------------------

/** Os tres primeiros do campeonato (pilotos; ou equipes quando a categoria so tem tabela de equipes). */
@Composable
private fun LeadersSection(category: Category, onOpenDriver: (String) -> Unit, onOpenTeam: (String) -> Unit, onOpenStandings: () -> Unit) {
    val standings = category.standings ?: return
    val drivers = standings.drivers?.takeIf { it.isNotEmpty() }
    val items = (drivers ?: standings.constructors ?: standings.teams ?: standings.driverClasses?.firstOrNull()?.entries).orEmpty().sortedBy { it.position }.take(3)
    if (items.isEmpty()) return
    val isTeam = drivers == null && standings.driverClasses.isNullOrEmpty()
    Column {
        SectionHeader(
            title = stringResource(R.string.home_leaders),
            actionLabel = stringResource(R.string.home_full_standings),
            onAction = onOpenStandings,
        )
        Spacer(Modifier.height(16.dp))
        PitCard(Modifier.fillMaxWidth()) {
            Column {
                items.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                    val driver = if (isTeam) null else DriverLookup.find(category.drivers, item.name)
                    val team = if (isTeam) DriverLookup.team(category, item.name) else null
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = driver != null || team != null) {
                            driver?.let { onOpenDriver(it.id) } ?: team?.let { onOpenTeam(it.id) }
                        }.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Text("%02d".format(item.position), Modifier.width(28.dp), style = PitText.DataValue, color = if (index == 0) PitColors.Primary else PitColors.OnSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(item.name.uppercase(), style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp), color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            (item.team ?: team?.car)?.let { Text(it.uppercase(), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                        Text(formatPoints(item.points), style = PitText.DataValue.copy(fontSize = 22.sp), color = Color.White)
                        Text(stringResource(R.string.table_pts).uppercase(), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
}
