package com.pitstophub.app.ui.categories

import com.pitstophub.app.ui.components.HowItWorksCard
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.repo.CategoryRepository
import com.pitstophub.app.domain.CategoryGroups
import com.pitstophub.app.domain.CategorySummary
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.components.FilterPill
import com.pitstophub.app.ui.components.FollowKind
import com.pitstophub.app.ui.components.FollowToggle
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.PitStatTile
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.components.carbonTexture
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.drivers.DriverLine
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.teams.TeamCard
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CategoryHubState(val data: CategoryData? = null, val notFound: Boolean = false)

class CategoryHubViewModel(private val repository: CategoryRepository, private val categoryId: String) : ViewModel() {
    private val _state = MutableStateFlow(CategoryHubState(data = null))
    val state: StateFlow<CategoryHubState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // pinta o catalogo na hora e troca pelo dado ao vivo quando chegar
            repository.cached(categoryId)?.let { _state.value = CategoryHubState(it) }
                ?: repository.staticData(categoryId)?.let { _state.value = CategoryHubState(it) }
            try {
                _state.value = CategoryHubState(repository.load(categoryId))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (_state.value.data == null) _state.value = CategoryHubState(notFound = true)
            }
        }
    }
}

private const val DRIVERS_PREVIEW = 10

/** Pagina da categoria: resumo, numeros, atalhos, proxima etapa, equipes, pilotos, sobre e regras. */
@Composable
fun CategoryHubScreen(
    state: CategoryHubState,
    onBack: () -> Unit,
    onUseCategory: (HubTarget) -> Unit,
    onOpenRace: (Race) -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenDriver: (String) -> Unit,
    onOpenChampions: () -> Unit,
    onOpenRules: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val portuguese = isPortuguese()
    val category = state.data?.category
    val account = LocalAccount.current
    val settings by account.settings.collectAsStateWithLifecycle()
    val now by rememberNow(60_000)
    var classFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllDrivers by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(title = category?.name ?: stringResource(R.string.categories_title), onBack = onBack)
        if (category == null) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(stringResource(if (state.notFound) R.string.category_not_found else R.string.loading), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
            }
            return@Column
        }
        val accent = CategoryVisuals.accent(category.id)
        val summary = CategorySummary.of(category)
        val next = RaceTime.nextRace(category.calendar, now)
        val group = CategoryGroups.all.firstOrNull { category.id in it.ids }
        val classes = category.teams.mapNotNull { it.raceClass }.distinct()
        val teams = category.teams.filter { classFilter == null || it.raceClass == classFilter }
        val drivers = if (showAllDrivers) category.drivers else category.drivers.take(DRIVERS_PREVIEW)

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "hero") {
                Box(
                    Modifier.fillMaxWidth().clip(PitShapes.Soft)
                        .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.45f), PitColors.Asphalt, PitColors.Carbon)))
                        .border(1.dp, PitColors.Hairline, PitShapes.Soft)
                        .carbonTexture(0.04f),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                group?.let { Text(stringResource(it.title).uppercase(), style = PitText.DataLabelSm, color = accent) }
                                Text(category.name.uppercase(), style = PitText.HeadlineLgMobile, color = Color.White)
                                Text(category.displayFullName(portuguese), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
                            }
                            Box(
                                Modifier.size(56.dp).clip(PitShapes.Card).background(accent.copy(alpha = 0.2f)).border(1.dp, accent.copy(alpha = 0.5f), PitShapes.Card),
                                contentAlignment = Alignment.Center,
                            ) { Icon(CategoryVisuals.icon(category.id), null, tint = accent, modifier = Modifier.size(30.dp)) }
                        }
                        Text(category.displayDescription(portuguese), style = PitText.BodySm, color = PitColors.OnSurface)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            FollowToggle(FollowKind.CATEGORY, settings.followsCategory(category.id), { account.toggleCategory(category.id, category.name) })
                            Text(
                                stringResource(if (settings.followsCategory(category.id)) R.string.hub_following else R.string.hub_follow_hint),
                                style = PitText.DataLabelSm,
                                color = PitColors.OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (category.rulesFormat.isNotEmpty()) {
                item(key = "howto") { HowItWorksCard(category.name, onOpenRules) }
            }

            item(key = "stats") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PitStatTile(stringResource(R.string.hub_teams), summary.teams.takeIf { it > 0 }?.toString() ?: "—", Modifier.weight(1f))
                        PitStatTile(stringResource(R.string.hub_drivers), category.drivers.size.takeIf { it > 0 }?.toString() ?: "—", Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        PitStatTile(stringResource(R.string.hub_rounds), summary.rounds.takeIf { it > 0 }?.toString() ?: "—", Modifier.weight(1f))
                        PitStatTile(stringResource(R.string.hub_completed), summary.completed.toString(), Modifier.weight(1f), highlight = true)
                    }
                }
            }

            item(key = "actions") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HubAction(Icons.Filled.CalendarMonth, stringResource(R.string.nav_calendar), Modifier.weight(1f)) { onUseCategory(HubTarget.CALENDAR) }
                        HubAction(Icons.Filled.Leaderboard, stringResource(R.string.nav_standings), Modifier.weight(1f)) { onUseCategory(HubTarget.STANDINGS) }
                    }
                    if (category.id == "f1") PitPrimaryButton(stringResource(R.string.hub_champions), onOpenChampions, Modifier.fillMaxWidth())
                }
            }

            next?.let { race ->
                item(key = "next") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.hub_next))
                        PitCard(Modifier.fillMaxWidth().clickable { onOpenRace(race) }, borderColor = PitColors.Primary.copy(alpha = 0.4f)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(stringResource(R.string.round_label, race.round ?: 0).uppercase(), style = PitText.DataLabelSm, color = PitColors.Primary)
                                Text(race.displayName(portuguese).uppercase(), style = PitText.HeadlineMd, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${RaceFormat.place(race.circuit, race.displayLocation(portuguese))} • ${RaceFormat.dateTime(race, currentLocale())}",
                                    style = PitText.DataLabelSm,
                                    color = PitColors.OnSurfaceVariant,
                                    maxLines = 2,
                                )
                            }
                        }
                    }
                }
            }

            if (category.teams.isNotEmpty()) {
                item(key = "teams-header") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.hub_teams))
                        if (classes.size > 1) {
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterPill(stringResource(R.string.filter_all), classFilter == null, { classFilter = null })
                                classes.forEach { FilterPill(it, classFilter == it, { classFilter = it }) }
                            }
                        }
                    }
                }
                items(teams, key = { "team-${it.id}" }) { team ->
                    TeamCard(team, category.drivers.filter { it.teamId == team.id }, onClick = { onOpenTeam(team.id) })
                }
            } else {
                item(key = "no-teams") { Text(stringResource(R.string.hub_no_teams), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant) }
            }

            if (category.drivers.isNotEmpty()) {
                item(key = "drivers-header") { SectionHeader(stringResource(R.string.hub_drivers), actionLabel = null) }
                item(key = "drivers") {
                    Column(Modifier.fillMaxWidth().clip(PitShapes.Soft).border(1.dp, PitColors.Hairline, PitShapes.Soft), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        drivers.forEachIndexed { index, driver ->
                            if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                            DriverLine(driver, category.teams.firstOrNull { it.id == driver.teamId }, onClick = { onOpenDriver(driver.id) })
                        }
                    }
                }
                if (category.drivers.size > DRIVERS_PREVIEW) {
                    item(key = "drivers-more") {
                        PitOutlineButton(
                            stringResource(if (showAllDrivers) R.string.action_show_less else R.string.hub_show_all_drivers, category.drivers.size),
                            { showAllDrivers = !showAllDrivers },
                            Modifier.fillMaxWidth(),
                            dim = true,
                        )
                    }
                }
            } else {
                item(key = "no-drivers") { Text(stringResource(R.string.hub_no_drivers), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant) }
            }

            val about = category.displayLongDescription(portuguese)
            if (about.isNotBlank()) {
                item(key = "about") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.hub_about))
                        Text(about, style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** Destino dos atalhos da pagina da categoria: escolhem a categoria e abrem a aba. */
enum class HubTarget { CALENDAR, STANDINGS }

@Composable
private fun HubAction(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(PitShapes.Card).background(PitColors.SurfaceHigh).border(1.dp, PitColors.Hairline, PitShapes.Card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = PitColors.Primary, modifier = Modifier.size(20.dp))
        Text(label.uppercase(), style = PitText.ButtonLabel, color = PitColors.OnSurface, maxLines = 1)
    }
}
