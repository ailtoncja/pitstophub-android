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
            itemsIndexed(calendar, key = { _, race -> race.id }) { index, race ->
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

    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        if (category != null) {
            Text(category.displayFullName(portuguese).uppercase(), style = PitText.DataLabelSm, color = PitColors.Primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }
        Box {
            Text(
                text = stringResource(R.string.calendar_season_title, season).uppercase(),
                style = PitText.HeadlineLgMobile,
                color = PitColors.OnSurface,
            )
            Box(
                Modifier.align(Alignment.BottomStart).offset(y = 10.dp).width(150.dp).height(4.dp)
                    .clip(SkewShape).background(PitColors.PrimaryContainer),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.calendar_stats, held, done, left).uppercase(),
            style = PitText.DataLabel,
            color = PitColors.OnSurfaceVariant,
        )
        if (next != null) {
            Spacer(Modifier.height(24.dp))
            NextRacePanel(next, now, portuguese)
        }
    }
}

@Composable
private fun NextRacePanel(next: Race, now: Instant, portuguese: Boolean) {
    val parts = RaceTime.countdown(RaceTime.startInstant(next), now)
    val phase = RaceTime.phase(next, now)
    Row(
        Modifier.fillMaxWidth().clip(PitShapes.Card).background(PitColors.SurfaceHigh.copy(alpha = 0.6f))
            .border(1.dp, PitColors.OutlineVariant, PitShapes.Card).padding(16.dp).height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.next_race).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            Text(next.displayName(portuguese).uppercase(), style = PitText.DataValue.copy(fontSize = 16.sp, lineHeight = 20.sp), color = PitColors.Primary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.width(1.dp).fillMaxSize().background(PitColors.OutlineVariant))
        Column {
            Text(stringResource(R.string.calendar_countdown).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            if (phase == RacePhase.COUNTDOWN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val style = PitText.DataValue.copy(fontSize = 16.sp)
                    Text("%02d".format(parts.days), style = style, color = PitColors.OnSurface)
                    BlinkingColon(style = style, color = PitColors.PrimaryContainer)
                    Text("%02d".format(parts.hours), style = style, color = PitColors.OnSurface)
                    BlinkingColon(style = style, color = PitColors.PrimaryContainer)
                    Text("%02d".format(parts.minutes), style = style, color = PitColors.OnSurface)
                    BlinkingColon(style = style, color = PitColors.PrimaryContainer)
                    Text("%02d".format(parts.seconds), style = style, color = PitColors.OnSurface)
                }
            } else {
                Text(
                    stringResource(if (phase == RacePhase.LIVE) R.string.status_live else R.string.status_awaiting).uppercase(),
                    style = PitText.DataValue.copy(fontSize = 16.sp),
                    color = PitColors.PrimaryContainer,
                )
            }
        }
    }
}

// --- card de corrida ---------------------------------------------------------------------------

@Composable
private fun RaceCard(
    race: Race,
    round: Int,
    kind: RaceKind,
    phase: RacePhase?,
    category: Category,
    portuguese: Boolean,
    locale: java.util.Locale,
    onClick: () -> Unit,
) {
    val shape = ClippedCornerShape(16.dp)
    val isNext = kind == RaceKind.NEXT
    val dimmed = kind == RaceKind.FUTURE || kind == RaceKind.CANCELLED

    Box(
        Modifier.fillMaxWidth()
            .alpha(if (kind == RaceKind.FUTURE) 0.7f else if (kind == RaceKind.CANCELLED) 0.55f else 1f)
            .then(if (isNext) Modifier.hardShadow(PitColors.PrimaryContainer, shape, 0.dp, 4.dp) else Modifier)
            .clip(shape)
            .background(PitColors.Asphalt)
            .border(1.dp, if (isNext) PitColors.Primary.copy(alpha = 0.3f) else if (dimmed) PitColors.HairlineSoft else PitColors.Hairline, shape)
            .clickable(onClick = onClick),
    ) {
        Column {
            // bloco de data + rodada
            Column(
                Modifier.fillMaxWidth()
                    .background(if (isNext) PitColors.SurfaceHigh.copy(alpha = 0.8f) else if (dimmed) PitColors.SurfaceHigh.copy(alpha = 0.5f) else PitColors.SurfaceHigh)
                    .then(if (isNext) Modifier.background(PitColors.Primary.copy(alpha = 0.10f)).carbonTexture(0.05f) else Modifier)
                    .padding(16.dp),
            ) {
                Text(
                    RaceFormat.monthDay(race.date, locale),
                    style = PitText.DataLabel,
                    color = if (isNext) PitColors.Primary else PitColors.OnSurfaceVariant,
                )
                Text(
                    stringResource(R.string.round_label, round),
                    style = PitText.HeadlineMd,
                    color = if (isNext) PitColors.Primary else if (dimmed) PitColors.OnSurfaceVariant else PitColors.OnSurface,
                )
            }
            HorizontalDivider(color = PitColors.HairlineSoft)

            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (kind) {
                        RaceKind.PAST -> StatusChip(stringResource(R.string.status_completed))
                        RaceKind.NEXT -> when (phase) {
                            RacePhase.LIVE -> StatusChip(stringResource(R.string.status_live), container = PitColors.PrimaryContainer, content = Color.White, pulsing = true)
                            RacePhase.AWAITING_RESULT -> StatusChip(stringResource(R.string.status_awaiting))
                            else -> StatusChip(stringResource(R.string.status_upcoming), container = PitColors.PrimaryContainer, content = Color.White, pulsing = true)
                        }
                        RaceKind.CANCELLED -> StatusChip(stringResource(R.string.status_cancelled), container = PitColors.ErrorContainer, content = PitColors.Error)
                        RaceKind.FUTURE -> Unit
                    }
                    Text(
                        race.displayName(portuguese).uppercase(),
                        style = PitText.HeadlineMd,
                        color = if (dimmed) PitColors.OnSurfaceVariant else PitColors.OnSurface,
                    )
                    val place = race.circuit.ifBlank { race.displayLocation(portuguese) }
                    if (place.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.ShareLocation, null, Modifier.size(16.dp), tint = if (dimmed) PitColors.SurfaceHighest else PitColors.OnSurfaceVariant)
                            Text(
                                place.uppercase(),
                                style = PitText.DataLabelSm,
                                color = if (dimmed) PitColors.SurfaceBright else PitColors.OnSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                if (kind == RaceKind.PAST) race.winner?.let { WinnerBox(it, category) }

                when (kind) {
                    RaceKind.PAST -> PitOutlineButton(stringResource(R.string.action_view_results), onClick, Modifier.fillMaxWidth())
                    RaceKind.NEXT -> PitPrimaryButton(stringResource(R.string.action_view_details), onClick, Modifier.fillMaxWidth())
                    RaceKind.FUTURE -> PitOutlineButton(stringResource(R.string.action_track_info), onClick, Modifier.fillMaxWidth(), dim = true)
                    RaceKind.CANCELLED -> Unit
                }
            }
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
        Modifier.fillMaxWidth().clip(PitShapes.Card).background(PitColors.SurfaceContainer).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DriverAvatar(driver?.image ?: team?.badge, fallback = winner)
        Column {
            Text(stringResource(R.string.winner_label).uppercase(), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
            Text(label.uppercase(), style = PitText.DataLabel.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = PitColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
