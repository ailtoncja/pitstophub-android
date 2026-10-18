package com.pitstophub.app.ui.drivers

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.formatTitleYears
import com.pitstophub.app.data.countTitles
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.DriverTitle
import com.pitstophub.app.domain.DriverRound
import com.pitstophub.app.domain.Duel
import com.pitstophub.app.domain.Nationality
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.champions.ChampionArt
import com.pitstophub.app.ui.components.FollowKind
import com.pitstophub.app.ui.components.FollowToggle
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.components.BioCard
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitStatTile
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.components.carbonTexture
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.teams.TeamBadge
import com.pitstophub.app.ui.teams.parseTeamColor
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.ui.tributes.LocalTributes
import kotlinx.coroutines.launch

/** Pagina do piloto: foto, numeros da temporada, duelo com o companheiro, ultimas etapas, telemetria, biografia e titulos. */
@Composable
fun DriverDetailScreen(
    state: DriverDetailState,
    onBack: () -> Unit,
    onOpenTeam: (String) -> Unit,
    onOpenRace: (String) -> Unit,
    onOpenChampions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val portuguese = isPortuguese()
    val category = state.data?.category
    val champion = state.champion
    val driver = state.driver
    val name = driver?.name ?: champion?.name
    val account = LocalAccount.current
    val settings by account.settings.collectAsStateWithLifecycle()

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(
            title = name ?: stringResource(R.string.nav_grid),
            onBack = onBack,
            actions = {
                if (driver != null && category != null) {
                    FollowToggle(
                        FollowKind.DRIVER,
                        settings.followsDriver(category.id, driver.id),
                        { account.toggleDriver(category.id, driver.id, driver.name) },
                        Modifier.padding(end = 4.dp),
                    )
                }
            },
        )
        if (category == null || name == null) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(stringResource(if (state.notFound) R.string.driver_not_found else R.string.loading), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "hero") { DriverHero(state, category) }
            item(key = "numbers") { NumbersSection(state) }

            val duel = state.duel
            val mate = state.teammate
            if (duel != null && mate != null) {
                item(key = "duel") { DuelSection(driver!!.name, mate.name, duel.grid, duel.race) }
            }

            val lastRounds = state.rounds?.sortedByDescending { it.round }?.take(3).orEmpty()
            if (lastRounds.isNotEmpty()) {
                item(key = "last") { LastRoundsSection(lastRounds, category, portuguese, onOpenRace) }
            } else if (state.seasonLoading) {
                item(key = "last-loading") { Text(stringResource(R.string.loading), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant) }
            }

            state.stats?.takeIf { it.races > 0 }?.let { stats ->
                item(key = "telemetry") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader(stringResource(R.string.driver_telemetry))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PitStatTile(stringResource(R.string.driver_fastest_laps), "%02d".format(stats.fastestLaps), Modifier.weight(1f))
                            PitStatTile(stringResource(R.string.driver_poles), "%02d".format(stats.poles), Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PitStatTile(stringResource(R.string.driver_avg_finish), stats.averageFinish?.let { "%.1f".format(it) } ?: "—", Modifier.weight(1f))
                            PitStatTile(stringResource(R.string.driver_dnf), "%02d".format(stats.dnfs), Modifier.weight(1f), highlight = stats.dnfs > 0)
                        }
                    }
                }
            }

            if (champion != null && state.isChampionOnly) {
                item(key = "career") { CareerSection(champion, portuguese, onOpenChampions) }
            }

            if (state.bio != null || state.titles.isNotEmpty()) {
                item(key = "bio") { BioSection(state, portuguese) }
            }

            state.team?.let { team ->
                item(key = "team") {
                    PitCard(Modifier.fillMaxWidth().clickable { onOpenTeam(team.id) }) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            TeamBadge(team, 48.dp)
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.driver_team), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
                                Text(team.name, style = PitText.HeadlineMd.copy(fontSize = 20.sp, lineHeight = 24.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Text(stringResource(R.string.action_open), style = PitText.DataLabel, color = PitColors.Primary)
                        }
                    }
                }
            }
        }
    }
}

// --- cabecalho ----------------------------------------------------------------------------------------

@Composable
private fun DriverHero(state: DriverDetailState, category: Category) {
    val driver = state.driver
    val champion = state.champion
    val name = driver?.name ?: champion!!.name
    val number = driver?.number ?: champion?.number.orEmpty()
    val nationality = driver?.nationality ?: champion?.nationality.orEmpty()
    val color = parseTeamColor(state.team?.color)
    val tributes = LocalTributes.current
    val scope = rememberCoroutineScope()
    val parts = name.trim().split(' ')
    val first = parts.dropLast(1).joinToString(" ")
    val last = parts.last()
    val isHamilton = driver?.id == "hamilton" && category.id == "f1"

    Box(
        Modifier.fillMaxWidth().height(if (state.isChampionOnly) 470.dp else 400.dp).clip(PitShapes.Soft)
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.5f), PitColors.Asphalt, PitColors.Carbon)))
            .border(1.dp, PitColors.Hairline, PitShapes.Soft)
            .carbonTexture(0.04f),
    ) {
        if (number.isNotBlank() && number != "--" && !state.isChampionOnly) {
            Text(
                number,
                Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp),
                style = PitText.DisplayLg.copy(fontSize = 120.sp, lineHeight = 120.sp),
                color = Color.White.copy(alpha = 0.07f),
                maxLines = 1,
            )
        }
        // foto: a carta do campeao, o recorte do piloto ou a foto
        val cardArt = champion?.let(ChampionArt::of)
        val photoModifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(if (state.isChampionOnly) 330.dp else 310.dp)
            .then(
                if (isHamilton) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                tributes?.onHoldStart()
                                tryAwaitRelease()
                                tributes?.onHoldEnd()
                            },
                            onTap = { tributes?.onHamiltonTap() },
                        )
                    }
                } else {
                    Modifier
                },
            )
        when {
            state.isChampionOnly && cardArt != null -> Image(painterResource(cardArt), name, photoModifier.padding(8.dp), contentScale = ContentScale.Fit)
            else -> {
                val portrait = driver?.cutout ?: driver?.image
                NetworkImage(
                    portrait,
                    photoModifier,
                    contentScale = if (driver?.cutout != null) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (driver?.cutout != null) Alignment.BottomCenter else Alignment.TopCenter,
                )
            }
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.40f to Color.Transparent, 0.85f to PitColors.Carbon.copy(alpha = 0.85f), 1f to PitColors.Carbon)))
        Box(Modifier.width(4.dp).fillMaxHeight().background(color).align(Alignment.CenterStart))

        Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.isChampionOnly) {
                    Text(stringResource(R.string.driver_champion), style = PitText.DataLabel, color = PitColors.Primary)
                } else {
                    state.team?.let { team ->
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(team.name, style = PitText.DataLabel, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                val flag = Nationality.flag(nationality)
                if (flag.isNotEmpty() || nationality.isNotBlank()) {
                    Text("$flag ${Nationality.label(nationality, isPortuguese())}".trim(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 1)
                }
            }
            if (first.isNotEmpty()) Text(first, style = PitText.HeadlineMd, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    last,
                    Modifier.weight(1f, fill = false),
                    style = PitText.DisplayLg.copy(fontSize = 44.sp, lineHeight = 46.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (number.isNotBlank() && number != "--" && !state.isChampionOnly) {
                    Text("  #$number", style = PitText.DataValue.copy(fontSize = 22.sp), color = PitColors.OnSurfaceVariant)
                }
            }
        }
    }
}

// --- numeros ----------------------------------------------------------------------------------------

@Composable
private fun NumbersSection(state: DriverDetailState) {
    val champion = state.champion
    if (state.isChampionOnly && champion != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PitStatTile(stringResource(R.string.driver_titles), champion.championYears.size.toString(), Modifier.weight(1f), highlight = true, caption = champion.championYears.joinToString(", "))
                PitStatTile(stringResource(R.string.driver_wins), champion.careerWins.toString(), Modifier.weight(1f), caption = stringResource(R.string.driver_career))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PitStatTile(stringResource(R.string.driver_podiums), champion.careerPodiums.toString(), Modifier.weight(1f), caption = stringResource(R.string.driver_career))
                PitStatTile(stringResource(R.string.driver_poles), champion.careerPoles.toString(), Modifier.weight(1f), caption = stringResource(R.string.driver_career))
            }
        }
        return
    }
    val standing = state.standing
    val stats = state.stats
    val category = state.data?.category
    val wins = stats?.wins ?: category?.calendar?.count { race -> race.winner?.let { winner -> winner.split('/').any { it.trim() == state.driver?.name } } == true } ?: 0
    val leaderPoints = category?.standings?.drivers?.maxOfOrNull { it.points }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PitStatTile(stringResource(R.string.driver_position), standing?.let { "P${it.position}" } ?: "—", Modifier.weight(1f), highlight = standing?.position == 1)
            PitStatTile(
                stringResource(R.string.driver_points),
                standing?.let { formatPoints(it.points) } ?: "—",
                Modifier.weight(1f),
                caption = if (standing != null && leaderPoints != null && leaderPoints > 0) stringResource(R.string.driver_of_leader, (standing.points / leaderPoints * 100).toInt()) else null,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PitStatTile(stringResource(R.string.driver_wins), wins.toString(), Modifier.weight(1f), highlight = wins > 0, caption = stats?.winRaces?.take(2)?.joinToString(", ") { shortGrandPrix(it) })
            PitStatTile(
                stringResource(R.string.driver_podiums),
                stats?.podiums?.toString() ?: "—",
                Modifier.weight(1f),
                caption = stats?.let { stringResource(R.string.driver_of_races, (it.podiumRate * 100).toInt()) },
            )
        }
    }
}

private fun shortGrandPrix(name: String) = name.replace(Regex("\\s*(Grand Prix|GP)\\s*$", RegexOption.IGNORE_CASE), "")

// --- duelo --------------------------------------------------------------------------------------------

@Composable
private fun DuelSection(mine: String, mate: String, grid: Duel, race: Duel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.driver_duel))
        PitCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(stringResource(R.string.driver_vs, shortName(mate)), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                if (grid.total > 0) DuelBar(stringResource(R.string.driver_duel_grid), shortName(mine), shortName(mate), grid)
                if (race.total > 0) DuelBar(stringResource(R.string.driver_duel_race), shortName(mine), shortName(mate), race)
            }
        }
    }
}

private fun shortName(name: String): String {
    val parts = name.trim().split(' ')
    return if (parts.size < 2) name else "${parts.first().first()}. ${parts.drop(1).joinToString(" ")}"
}

@Composable
private fun DuelBar(title: String, mine: String, mate: String, duel: Duel) {
    val share = (duel.mineShare / 100.0).toFloat().coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${duel.mine}  $mine", Modifier.weight(1f), style = PitText.DataLabel, color = PitColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(title, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
            Text("$mate  ${duel.theirs}", Modifier.weight(1f), style = PitText.DataLabel, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.fillMaxWidth().height(6.dp).background(PitColors.SurfaceHighest)) {
            if (share > 0f) Box(Modifier.weight(share).fillMaxHeight().background(Color.White))
            if (share < 1f) Box(Modifier.weight(1f - share).fillMaxHeight().background(PitColors.SurfaceBright))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("%.1f%%".format(duel.mineShare), style = PitText.DataLabelXs, color = PitColors.Primary)
            Text("%.1f%%".format(100 - duel.mineShare), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
        }
    }
}

// --- ultimas etapas ----------------------------------------------------------------------------------

@Composable
private fun LastRoundsSection(rounds: List<DriverRound>, category: Category, portuguese: Boolean, onOpenRace: (String) -> Unit) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.driver_last_rounds))
        PitCard(Modifier.fillMaxWidth()) {
            Column {
                rounds.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                    val race = category.calendar.firstOrNull { it.round == row.round }
                    val finish = row.finish
                    val accent = when {
                        finish != null && finish <= 3 -> PitColors.Success
                        row.points > 0 -> PitColors.Success
                        else -> PitColors.SurfaceBright
                    }
                    Row(
                        Modifier.fillMaxWidth().then(if (race != null) Modifier.clickable { onOpenRace(race.id) } else Modifier).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.width(4.dp).height(36.dp).background(accent))
                        Column(Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    (race?.displayName(portuguese) ?: row.raceName),
                                    Modifier.weight(1f, fill = false),
                                    style = PitText.BodyMd.copy(fontWeight = FontWeight.SemiBold),
                                    color = PitColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                if (row.grid == 1) Text(stringResource(R.string.driver_pole), style = PitText.DataLabelXs, color = PitColors.Primary)
                            }
                            Text(RaceFormat.monthDay(row.date, locale), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
                        }
                        RoundCell(stringResource(R.string.driver_grid), row.grid?.takeIf { it > 0 }?.let { "P$it" } ?: "—")
                        RoundCell(stringResource(R.string.driver_finish), finish?.let { "P$it" } ?: "DNF", strong = true)
                        RoundCell(stringResource(R.string.col_pts), "+${formatPoints(row.points)}", tint = PitColors.Primary, strong = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundCell(label: String, value: String, tint: Color = PitColors.OnSurface, strong: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(54.dp)) {
        Text(label, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, softWrap = false)
        Text(value, style = PitText.DataLabel.copy(fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium), color = tint, maxLines = 1)
    }
}

// --- carreira do campeao -----------------------------------------------------------------------------

@Composable
private fun CareerSection(champion: com.pitstophub.app.data.model.F1Champion, portuguese: Boolean, onOpenChampions: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.driver_career_teams))
        PitCard(Modifier.fillMaxWidth()) {
            Column {
                champion.teams.forEachIndexed { index, stint ->
                    if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stint.name, style = PitText.BodyMd.copy(fontWeight = FontWeight.SemiBold), color = PitColors.OnSurface)
                            stint.series?.let { Text(it.text(portuguese), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant) }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(stint.years, style = PitText.DataLabelSm, color = PitColors.OnSurface)
                            if (stint.championYears.isNotEmpty()) Text(stint.championYears.joinToString(", "), style = PitText.DataLabelXs.copy(fontWeight = FontWeight.Bold), color = PitColors.Primary)
                        }
                    }
                }
            }
        }
        PitOutlineButton(stringResource(R.string.champions_title), onOpenChampions, Modifier.fillMaxWidth(), dim = true)
    }
}

// --- biografia e titulos ----------------------------------------------------------------------------

@Composable
private fun BioSection(state: DriverDetailState, portuguese: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(stringResource(R.string.driver_bio))
        if (state.titles.isNotEmpty()) TitlesBlock(state.titles, portuguese)
        state.bio?.let { BioCard(it.text(portuguese)) }
        Spacer(Modifier.height(4.dp))
    }
}

/** Placar de titulos: contador em destaque ao lado da lista de campeonatos com os anos. */
@Composable
private fun TitlesBlock(titles: List<DriverTitle>, portuguese: Boolean) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier.width(104.dp).fillMaxHeight().clip(PitShapes.Card).background(PitColors.SurfaceHigh).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Filled.EmojiEvents, null, Modifier.size(26.dp), tint = Color.White)
            Text(countTitles(titles).toString(), style = PitText.DisplayLg.copy(fontSize = 44.sp, lineHeight = 48.sp), color = Color.White)
            Text(stringResource(R.string.driver_titles), style = PitText.DataLabelXs, color = Color.White.copy(alpha = 0.85f))
        }
        PitCard(Modifier.weight(1f)) {
            Column {
                titles.forEachIndexed { index, title ->
                    if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(title.label(portuguese), style = PitText.BodyMd.copy(fontWeight = FontWeight.SemiBold), color = PitColors.OnSurface)
                        formatTitleYears(title.years).takeIf { it.isNotEmpty() }?.let {
                            Text(it, style = PitText.DataLabelSm, color = PitColors.Primary)
                        }
                    }
                }
            }
        }
    }
}
