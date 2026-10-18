package com.pitstophub.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ShareLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.ui.components.HeroBackdrop
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RacePhase
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.CategoryState
import com.pitstophub.app.ui.components.BlinkingColon
import com.pitstophub.app.ui.components.DriverAvatar
import com.pitstophub.app.ui.components.OfflineNotice
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.StatusChip
import com.pitstophub.app.ui.components.carbonTexture
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.ClippedCornerShape
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.ui.theme.SkewShape
import com.pitstophub.app.ui.theme.hardShadow
import java.time.Instant

private enum class RaceKind { PAST, NEXT, FUTURE, CANCELLED }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    state: CategoryState,
    onOpenRace: (Race) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = state.data
    val category = data?.category
    val now by rememberNow()
    val portuguese = isPortuguese()
    val locale = currentLocale()
    val next = category?.calendar?.let { RaceTime.nextRace(it, now) }

    PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = onRefresh, modifier = modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (data?.offline == true) item(key = "offline") { OfflineNotice() }
            item(key = "header") {
                SeasonHeader(
                    category = category,
                    season = data?.season.orEmpty(),
                    next = next,
                    now = now,
                    portuguese = portuguese,
                )
            }
            val calendar = category?.calendar.orEmpty()
            if (calendar.isEmpty() && !state.loading) {
                item(key = "empty") {
                    Text(stringResource(R.string.calendar_empty), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
                }
            }
            // as que faltam primeiro (em ordem), depois as ja disputadas (a mais recente antes), como no site
            val ordered = calendar.filter { it.status != RaceStatus.COMPLETED } + calendar.filter { it.status == RaceStatus.COMPLETED }.reversed()
            itemsIndexed(ordered, key = { _, race -> race.id }) { _, race ->
                val index = calendar.indexOf(race)
                val kind = when {
                    race.status == RaceStatus.CANCELLED -> RaceKind.CANCELLED
                    race.status == RaceStatus.COMPLETED -> RaceKind.PAST
                    race.id == next?.id -> RaceKind.NEXT
                    else -> RaceKind.FUTURE
                }
                RaceCard(
                    race = race,
                    round = race.round ?: (index + 1),
                    kind = kind,
                    phase = if (kind == RaceKind.NEXT) RaceTime.phase(race, now) else null,
                    category = category!!,
                    portuguese = portuguese,
                    locale = locale,
                    onClick = { onOpenRace(race) },
                )
            }
        }
    }
}

@Composable
private fun SeasonHeader(category: Category?, season: String, next: Race?, now: Instant, portuguese: Boolean) {
    val calendar = category?.calendar.orEmpty()
    val held = calendar.count { it.status != RaceStatus.CANCELLED }
    val done = calendar.count { it.status == RaceStatus.COMPLETED }
    val left = calendar.count { it.status == RaceStatus.UPCOMING }

    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.calendar_season_title, season), style = PitText.HeadlineMd, color = Color.White)
        Text(stringResource(R.string.calendar_stats, held, done, left), style = PitText.BodySm, color = PitColors.Silver)
        if (next != null && category != null) {
            Spacer(Modifier.height(12.dp))
            NextRacePanel(category, next, now, portuguese)
        }
    }
}

/** A proxima etapa na linguagem do reel: campo da categoria, pista ou carro, titulo, horario e contagem. */
@Composable
private fun NextRacePanel(category: Category, next: Race, now: Instant, portuguese: Boolean) {
    val parts = RaceTime.countdown(RaceTime.startInstant(next), now)
    val phase = RaceTime.phase(next, now)
    val locale = currentLocale()
    Box(Modifier.fillMaxWidth().height(250.dp).clip(PitShapes.Media).border(1.dp, PitColors.Hairline, PitShapes.Media)) {
        HeroBackdrop(categoryId = category.id, categoryName = category.name, race = next, artHeight = 170.dp)
        Column(Modifier.align(Alignment.BottomStart).padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(next.displayName(portuguese), style = PitText.HeadlineLgMobile, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(RaceFormat.dateTime(next, locale), style = PitText.TitleSm, color = Color.White)
            when (phase) {
                RacePhase.COUNTDOWN -> if (RaceTime.hasKnownTime(next)) {
                    Text(
                        buildString {
                            if (parts.days > 0) append("${parts.days} d  ")
                            append("%02d h  %02d min".format(parts.hours, parts.minutes))
                        },
                        style = PitText.DataValue.copy(fontSize = 18.sp),
                        color = Color.White,
                    )
                }
                RacePhase.LIVE -> Text(stringResource(R.string.status_live), style = PitText.DataLabel, color = Color.White)
                RacePhase.AWAITING_RESULT -> Text(stringResource(R.string.status_awaiting), style = PitText.DataLabel, color = PitColors.Silver)
            }
        }
    }
}

// --- linha de corrida ------------------------------------------------------------------------------

@Composable
private fun RaceCard(
    race: Race,
    round: Int,
    kind: RaceKind,
    @Suppress("UNUSED_PARAMETER") phase: RacePhase?,
    category: Category,
    portuguese: Boolean,
    locale: java.util.Locale,
    onClick: () -> Unit,
) {
    val isNext = kind == RaceKind.NEXT
    val date = RaceTime.startInstant(race).atZone(java.time.ZoneId.systemDefault())
    Row(
        Modifier.fillMaxWidth().clip(PitShapes.Card)
            .background(if (isNext) Color.White.copy(alpha = 0.06f) else PitColors.Asphalt)
            .border(1.dp, PitColors.Hairline, PitShapes.Card)
            .alpha(if (kind == RaceKind.CANCELLED) 0.55f else 1f)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("%02d".format(date.dayOfMonth), style = PitText.HeadlineMd, color = Color.White)
            Text(date.month.getDisplayName(java.time.format.TextStyle.SHORT, locale).trimEnd('.'), style = PitText.DataLabelXs, color = PitColors.Silver)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(race.displayName(portuguese), style = PitText.TitleSm, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val place = race.circuit.ifBlank { race.displayLocation(portuguese) }
            Text(
                listOf(stringResource(R.string.round_label, round), place).filter { it.isNotBlank() }.joinToString(" · "),
                style = PitText.BodySm,
                color = PitColors.Silver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (kind == RaceKind.PAST) race.winner?.let {
                Text(stringResource(R.string.reel_winner, it), style = PitText.BodySm.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when (kind) {
            RaceKind.PAST -> Text(stringResource(R.string.status_completed), style = PitText.DataLabelXs, color = PitColors.Silver)
            RaceKind.CANCELLED -> Text(stringResource(R.string.status_cancelled), style = PitText.DataLabelXs, color = PitColors.Silver)
            else -> Text(
                if (RaceTime.hasKnownTime(race)) date.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) else "—",
                style = PitText.DataValue.copy(fontSize = 16.sp),
                color = if (RaceTime.hasKnownTime(race)) Color.White else PitColors.SilverDim,
            )
        }
    }
}

@Composable
internal fun WinnerBox(winner: String, category: Category) {
    val driver = DriverLookup.findFirst(category.drivers, winner)
    val team = if (driver == null) DriverLookup.team(category, winner) else null
    // Pilotos conhecidos viram o codigo de 3 letras ("VER"); equipes e duplas mostram o nome inteiro.
    val label = if (driver != null && !winner.contains('/')) RaceFormat.driverCode(driver.name) else winner
    Row(
        Modifier.fillMaxWidth().clip(PitShapes.Card).background(PitColors.SurfaceLow).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DriverAvatar(driver?.image ?: team?.badge, fallback = winner)
        Column {
            Text(stringResource(R.string.winner_label), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
            Text(label, style = PitText.DataLabel.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PitColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
