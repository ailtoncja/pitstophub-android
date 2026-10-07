package com.pitstophub.app.ui.standings

import com.pitstophub.app.ui.categories.scoringRuleIndex
import androidx.compose.material.icons.filled.Info
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.domain.Nationality
import com.pitstophub.app.domain.StandingLine
import com.pitstophub.app.domain.buildStandingRows
import com.pitstophub.app.ui.CategoryState
import com.pitstophub.app.ui.components.GlassPanel
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.components.OfflineNotice
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.MonoFamily
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.ui.theme.hardShadow
import com.pitstophub.app.ui.teams.parseTeamColor

/** Uma aba da classificacao (pilotos, construtores, equipes ou uma classe do Endurance Brasil). */
private data class StandingsTab(val key: String, val label: String, val items: List<StandingItem>, val isTeam: Boolean)

private const val COLLAPSED_ROWS = 5

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(
    state: CategoryState,
    movement: StandingsMovement,
    onOpenDriver: (String) -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenRules: (Int) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val category = data?.category
    val portuguese = isPortuguese()
    val tabs = category?.let { buildTabs(it, driversLabel = stringResource(R.string.tab_drivers), constructorsLabel = stringResource(R.string.tab_constructors), teamsLabel = stringResource(R.string.tab_teams)) }.orEmpty()

    // guarda a aba por categoria; ao trocar de categoria volta para a primeira
    var selectedIndex by rememberSaveable(category?.id) { mutableIntStateOf(0) }
    var expanded by rememberSaveable(category?.id, selectedIndex) { mutableStateOf(false) }
    val tab = tabs.getOrNull(selectedIndex) ?: tabs.firstOrNull()

    // setas de subida/queda e pontos da ultima etapa (so F1; as outras categorias nao tem a rodada anterior)
    val usable = movement.categoryId == category?.id
    val rows = remember(tab, usable, movement) {
        if (tab == null) emptyList() else buildStandingRows(
            items = tab.items,
            previous = if (usable) when (tab.key) { "drivers" -> movement.previousDrivers; "constructors" -> movement.previousConstructors; else -> null } else null,
            lastRacePoints = if (usable) when (tab.key) { "drivers" -> movement.driverPoints; "constructors" -> movement.teamPoints; else -> null } else null,
        )
    }
    val openEntry: (StandingItem, Boolean) -> Unit = { item, isTeam ->
        if (category != null) {
            if (isTeam) DriverLookup.team(category, item.name)?.let { onOpenTeam(it.id) }
            else DriverLookup.find(category.drivers, item.name)?.let { onOpenDriver(it.id) }
        }
    }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (data?.offline == true) item(key = "offline") { OfflineNotice() }
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.standings_title).uppercase(),
                        style = PitText.HeadlineLgMobile,
                        color = PitColors.OnSurface,
                    )
                    Text(
                        listOfNotNull(category?.name, data?.season?.let { stringResource(R.string.standings_season, it) }).joinToString(" • ").uppercase(),
                        style = PitText.DataLabel.copy(letterSpacing = 0.1.em),
                        color = PitColors.OnSurfaceVariant,
                    )
                }
            }
            category?.let { cat ->
                scoringRuleIndex(cat.rulesFormat)?.let { index ->
                    item(key = "how-points") { HowPointsLink(onClick = { onOpenRules(index) }) }
                }
            }
            if (tabs.size > 1) {
                item(key = "toggle") {
                    SegmentedToggle(
                        labels = tabs.map { it.label },
                        selected = tabs.indexOf(tab).coerceAtLeast(0),
                        onSelect = { selectedIndex = it },
                    )
                }
            }
            if (tab == null || category == null) {
                item(key = "empty") {
                    Text(
                        stringResource(if (state.loading) R.string.loading else R.string.standings_unavailable),
                        style = PitText.BodyMd,
                        color = PitColors.OnSurfaceVariant,
                    )
                }
            } else {
                item(key = "leader-${category.id}-${tab.key}") {
                    LeaderCard(tab.items.first(), tab.isTeam, category, portuguese, onClick = { openEntry(tab.items.first(), tab.isTeam) })
                }
                item(key = "table-${category.id}-${tab.key}") {
                    StandingsTable(tab, rows, category, movement.lastRaceName, expanded, onToggle = { expanded = !expanded }, onOpen = { openEntry(it, tab.isTeam) })
                }
            }
        }
    }
}

private fun buildTabs(category: Category, driversLabel: String, constructorsLabel: String, teamsLabel: String): List<StandingsTab> {
    val standings = category.standings ?: return emptyList()
    return buildList {
        standings.drivers?.takeIf { it.isNotEmpty() }?.let { add(StandingsTab("drivers", driversLabel, it.sortedBy(StandingItem::position), isTeam = false)) }
        standings.constructors?.takeIf { it.isNotEmpty() }?.let { add(StandingsTab("constructors", constructorsLabel, it.sortedBy(StandingItem::position), isTeam = true)) }
        standings.teams?.takeIf { it.isNotEmpty() }?.let { add(StandingsTab("teams", teamsLabel, it.sortedBy(StandingItem::position), isTeam = true)) }
        standings.driverClasses?.filter { it.entries.isNotEmpty() }?.forEach { group ->
            add(StandingsTab("class-${group.classLabel}", group.classLabel, group.entries.sortedBy(StandingItem::position), isTeam = false))
        }
    }
}

// --- alternador ------------------------------------------------------------------------------------

@Composable
private fun SegmentedToggle(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.clip(PitShapes.Card).background(PitColors.SurfaceHigh)
            .border(1.dp, PitColors.OutlineVariant.copy(alpha = 0.3f), PitShapes.Card)
            .horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.clip(PitShapes.Card)
                    .background(if (active) PitColors.PrimaryContainer else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label.uppercase(),
                    style = PitText.DataLabel,
                    color = if (active) PitColors.OnPrimaryContainer else PitColors.OnSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

// --- lider -----------------------------------------------------------------------------------------

@Composable
private fun LeaderCard(entry: StandingItem, isTeam: Boolean, category: Category, portuguese: Boolean, onClick: () -> Unit) {
    val driver = if (!isTeam) DriverLookup.find(category.drivers, entry.name) else null
    val team = if (isTeam) {
        DriverLookup.team(category, entry.name)
    } else {
        driver?.let { d -> category.teams.firstOrNull { it.id == d.teamId } } ?: entry.team?.let { DriverLookup.team(category, it) }
    }
    val shape = PitShapes.Soft
    val portrait = driver?.cutout ?: driver?.image
    val teamArt = team?.clearart ?: team?.badge
    // GTWC e Endurance Brasil nao tem foto de piloto: o cartao encolhe em vez de deixar um vazio no topo.
    val hasVisual = portrait != null || teamArt != null
    Box(
        Modifier.fillMaxWidth().heightIn(min = if (hasVisual) 400.dp else 0.dp)
            .hardShadow(PitColors.PrimaryContainer, shape)
            .clip(shape).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, shape)
            .clickable(onClick = onClick),
    ) {
        // numero gigante ao fundo (sem imagem, fica no topo, onde nao e coberto pelo painel)
        Text(
            entry.position.toString(),
            Modifier.align(if (hasVisual) Alignment.BottomEnd else Alignment.TopEnd).offset(x = 12.dp, y = if (hasVisual) 28.dp else (-4).dp),
            style = PitText.DisplayLg.copy(fontSize = if (hasVisual) 200.sp else 150.sp, lineHeight = if (hasVisual) 200.sp else 150.sp),
            color = PitColors.SurfaceHighest.copy(alpha = 0.25f),
            maxLines = 1,
        )
        // foto: recorte do piloto; sem recorte, a foto; para equipes, o carro
        when {
            portrait != null -> NetworkImage(
                portrait,
                Modifier.align(Alignment.TopEnd).fillMaxWidth(0.82f).height(290.dp),
                contentScale = if (driver?.cutout != null) ContentScale.Fit else ContentScale.Crop,
                alignment = if (driver?.cutout != null) Alignment.BottomCenter else Alignment.TopCenter,
            )
            teamArt != null -> NetworkImage(
                teamArt,
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(250.dp).padding(horizontal = 24.dp, vertical = 24.dp),
                contentScale = ContentScale.Fit,
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 0.75f to PitColors.Asphalt.copy(alpha = 0.6f), 1f to PitColors.Asphalt),
            ),
        )
        // acento vermelho a esquerda
        Box(Modifier.width(4.dp).fillMaxHeight().background(PitColors.PrimaryContainer).align(Alignment.CenterStart))

        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            if (!hasVisual) Spacer(Modifier.height(72.dp))
            GlassPanel(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.leader_label).uppercase(),
                                style = PitText.DataLabel.copy(letterSpacing = 0.1.em),
                                color = PitColors.Primary,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(entry.name.uppercase(), style = PitText.HeadlineMd, color = PitColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val subtitle = if (isTeam) team?.car ?: team?.raceClass else entry.team ?: team?.name
                            if (!subtitle.isNullOrBlank()) {
                                Text(subtitle, style = PitText.BodyMd, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        val flag = Nationality.flag(driver?.nationality)
                        if (flag.isNotEmpty()) Text(flag, fontSize = 28.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = PitColors.OutlineVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        Text(stringResource(R.string.points_label).uppercase(), style = PitText.DataLabel, color = PitColors.OnSurfaceVariant)
                        Text(
                            formatPoints(entry.points),
                            style = PitText.DisplayLg.copy(fontFamily = MonoFamily, fontStyle = androidx.compose.ui.text.font.FontStyle.Normal, fontSize = 64.sp, lineHeight = 64.sp, fontWeight = FontWeight.Bold),
                            color = PitColors.Primary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

// --- tabela ----------------------------------------------------------------------------------------

@Composable
private fun StandingsTable(
    tab: StandingsTab,
    rows: List<StandingLine>,
    category: Category,
    lastRaceName: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: (StandingItem) -> Unit,
) {
    val visible = if (expanded) rows else rows.take(COLLAPSED_ROWS)
    Column(
        Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt)
            .border(1.dp, PitColors.Hairline, PitShapes.Soft).animateContentSize(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            val header = PitText.DataLabel.copy(letterSpacing = 0.08.em)
            Text(stringResource(R.string.table_pos).uppercase(), Modifier.width(48.dp), style = header, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.Center)
            Text(
                stringResource(if (tab.isTeam) R.string.table_team else R.string.table_driver_team).uppercase(),
                Modifier.weight(1f).padding(horizontal = 16.dp), style = header, color = PitColors.OnSurfaceVariant,
            )
            Text(stringResource(R.string.table_pts).uppercase(), Modifier.width(72.dp).padding(end = 8.dp), style = header, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = PitColors.Hairline)
        visible.forEachIndexed { index, line -> StandingRow(index, line, tab.isTeam, category, lastRaceName, onClick = { onOpen(line.item) }) }
        if (rows.size > COLLAPSED_ROWS) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (expanded) R.string.action_show_less else R.string.action_view_full).uppercase(),
                    style = PitText.DataLabel.copy(letterSpacing = 0.1.em),
                    color = PitColors.OnSurface,
                )
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                    null, tint = PitColors.OnSurface,
                )
            }
        }
    }
}

@Composable
private fun StandingRow(index: Int, line: StandingLine, isTeam: Boolean, category: Category, lastRaceName: String?, onClick: () -> Unit) {
    val item = line.item
    val leader = index == 0
    val driver = if (!isTeam) DriverLookup.find(category.drivers, item.name) else null
    val team = if (isTeam) DriverLookup.team(category, item.name) else null
    val background = when {
        leader -> PitColors.Asphalt
        index % 2 == 0 -> PitColors.Asphalt
        else -> PitColors.AsphaltAlt
    }
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(background).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(if (leader) PitColors.PrimaryContainer else Color.Transparent))
        Row(
            Modifier.weight(1f).background(if (leader) PitColors.Primary.copy(alpha = 0.05f) else Color.Transparent).padding(start = 0.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                item.position.toString(),
                Modifier.width(44.dp),
                style = PitText.DataValue,
                color = if (leader) PitColors.Primary else PitColors.OnSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(Modifier.weight(1f).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (isTeam) {
                    Box(Modifier.width(4.dp).height(28.dp).background(parseTeamColor(team?.color)))
                } else {
                    val flag = Nationality.flag(driver?.nationality)
                    if (flag.isNotEmpty()) Text(flag, fontSize = 18.sp)
                }
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            (if (isTeam) item.name else abbreviate(item.name)).uppercase(),
                            Modifier.weight(1f, fill = false),
                            style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp),
                            color = PitColors.OnSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        MovementArrow(line.delta)
                    }
                    val sub = if (isTeam) team?.car else item.team
                    if (!sub.isNullOrBlank()) {
                        Text(sub.uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.End) {
                Text(
                    formatPoints(item.points),
                    style = PitText.DataValue,
                    color = PitColors.OnSurface,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                val gain = line.lastRacePoints
                if (gain != null && gain > 0) {
                    Text(
                        "+${formatPoints(gain)}" + (lastRaceName?.let { " · ${shortRaceName(it)}" } ?: ""),
                        style = PitText.DataLabelXs,
                        color = PitColors.Success,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else if (!leader && line.gap > 0) {
                    Text("-${formatPoints(line.gap)}", style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1)
                }
            }
        }
    }
    HorizontalDivider(color = PitColors.HairlineSoft)
}

/** Seta de subida (verde) ou queda (vermelha) desde a rodada anterior; sem mudanca ou sem dado, nada. */
@Composable
private fun MovementArrow(delta: Int?) {
    when {
        delta == null || delta == 0 -> Unit
        delta > 0 -> Icon(Icons.Filled.ArrowDropUp, "+$delta", tint = PitColors.Success, modifier = Modifier.size(22.dp))
        else -> Icon(Icons.Filled.ArrowDropDown, "$delta", tint = PitColors.PrimaryContainer, modifier = Modifier.size(22.dp))
    }
}

/** "Singapore Grand Prix" -> "Singapore" (cabe na linha de pontos). */
internal fun shortRaceName(name: String): String =
    name.replace(Regex("\\s*(Grand Prix|GP)$", RegexOption.IGNORE_CASE), "").replace(Regex("^GP (de|da|do|dos|das) ", RegexOption.IGNORE_CASE), "")

/** "Max Verstappen" -> "M. Verstappen" (como no design). Nomes de uma palavra ficam como estao. */
internal fun abbreviate(name: String): String {
    val parts = name.trim().split(' ').filter { it.isNotEmpty() }
    if (parts.size < 2) return name
    return "${parts.first().first().uppercaseChar()}. ${parts.drop(1).joinToString(" ")}"
}

internal fun formatPoints(points: Double): String =
    if (points % 1.0 == 0.0) points.toLong().toString() else "%.1f".format(points)

/** Atalho sob o titulo da classificacao: leva direto a secao de pontuacao do "Como funciona". */
@Composable
private fun HowPointsLink(onClick: () -> Unit) {
    Row(
        Modifier.clip(PitShapes.Card).background(PitColors.SurfaceHigh).border(1.dp, PitColors.Hairline, PitShapes.Card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Info, null, tint = PitColors.Primary, modifier = Modifier.size(18.dp))
        Text(stringResource(R.string.standings_how_points).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurface)
    }
}
