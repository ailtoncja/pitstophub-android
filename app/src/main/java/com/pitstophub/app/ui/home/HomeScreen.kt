package com.pitstophub.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.domain.ReelItem
import com.pitstophub.app.domain.ReelPhase
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.components.GlyphButton
import com.pitstophub.app.ui.components.HeroBackdrop
import com.pitstophub.app.ui.components.PulsingDot
import com.pitstophub.app.ui.components.StateMark
import com.pitstophub.app.ui.components.hasTrackArt
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.race.RaceActions
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

/** Altura da faixa do proximo item que aparece embaixo do item atual. */
private val PEEK = 76.dp

/**
 * Inicio: o reel das proximas largadas. Uma corrida ocupa a tela, a seguinte ja aparece embaixo e o encaixe
 * e de uma em uma (com uma vibracao curta). Glifos a direita: seguir, agenda, compartilhar e abrir.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    items: List<ReelItem>,
    loading: Boolean,
    followed: Set<String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenRace: (categoryId: String, raceId: String) -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    onToggleFollow: (categoryId: String, name: String) -> Unit,
) {
    val pager = rememberPagerState { items.size }
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(pager) {
        var first = true
        androidx.compose.runtime.snapshotFlow { pager.currentPage }.collect {
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            first = false
        }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(if (loading) R.string.loading else R.string.reel_empty),
                    style = PitText.BodyMd,
                    color = PitColors.Silver,
                )
            }
            return@PullToRefreshBox
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val pageHeight = maxHeight - PEEK
            VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                pageSize = PageSize.Fixed(pageHeight),
                contentPadding = PaddingValues(bottom = PEEK),
                beyondViewportPageCount = 1,
            ) { index ->
                ReelPage(
                    item = items[index],
                    current = index == pager.currentPage,
                    following = items[index].data.category.id in followed,
                    onOpenRace = onOpenRace,
                    onOpenCategory = onOpenCategory,
                    onToggleFollow = onToggleFollow,
                )
            }
            // "A seguir": o proximo item ja esta carregado logo abaixo
            val next = items.getOrNull(pager.currentPage + 1)
            if (next != null) {
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp).padding(horizontal = 16.dp)
                        .clip(PitShapes.Pill).background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), PitShapes.Pill)
                        .clickable { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }
                        .heightIn(min = 40.dp).padding(start = 16.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(stringResource(R.string.reel_next), style = PitText.DataLabelSm.copy(fontWeight = FontWeight.Medium), color = PitColors.Silver)
                    Text(
                        "${next.data.category.name} · ${raceTitle(next)}",
                        style = PitText.DataLabelSm,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.size(18.dp), tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun raceTitle(item: ReelItem): String =
    if (isPortuguese()) item.race.name else item.race.enName ?: item.race.name

@Composable
private fun ReelPage(
    item: ReelItem,
    current: Boolean,
    following: Boolean,
    onOpenRace: (String, String) -> Unit,
    onOpenCategory: (String) -> Unit,
    onToggleFollow: (String, String) -> Unit,
) {
    val context = LocalContext.current
    val locale = currentLocale()
    val portuguese = isPortuguese()
    val now by rememberNow(30_000)
    val category = item.data.category
    val race = item.race
    val timed = RaceTime.hasKnownTime(race)
    val zone = ZoneId.systemDefault()
    val local = item.start.atZone(zone)
    val place = RaceFormat.place(race.circuit, if (portuguese) race.location else race.enLocation ?: race.location)
    val title = raceTitle(item)

    Box(Modifier.fillMaxSize().clickable { onOpenRace(category.id, race.id) }) {
        HeroBackdrop(categoryId = category.id, categoryName = category.name, race = race, artHeight = 300.dp)

        if (current && !hasTrackArt(category.id, race)) {
            Text(
                stringResource(R.string.reel_no_track),
                modifier = Modifier.align(Alignment.TopStart).padding(16.dp).clip(PitShapes.Pill)
                    .background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 12.dp, vertical = 6.dp),
                style = PitText.DataLabelXs,
                color = PitColors.Silver,
            )
        }

        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 20.dp, end = 88.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = PitText.DisplayLg, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(
                text = when {
                    item.phase == ReelPhase.WRAPPED -> race.winner?.let { stringResource(R.string.reel_winner, it) } ?: stringResource(R.string.home_season_wrapped)
                    timed -> "${weekdayDay(local, locale)} · ${local.format(DateTimeFormatter.ofPattern("HH:mm"))} ${local.zone.getDisplayName(JTextStyle.SHORT, locale)}"
                    else -> weekdayDay(local, locale)
                },
                style = PitText.HeadlineMd.copy(fontSize = 18.sp),
                color = Color.White,
            )
            when (item.phase) {
                ReelPhase.LIVE -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PulsingDot(PitColors.RacingRed, size = 8.dp)
                    Text(stringResource(R.string.status_live), style = PitText.DataLabel, color = Color.White)
                }
                ReelPhase.AWAITING -> Text(stringResource(R.string.status_awaiting), style = PitText.DataLabel, color = PitColors.Silver)
                ReelPhase.WRAPPED -> Text(stringResource(R.string.home_season_wrapped), style = PitText.DataLabel, color = PitColors.Silver)
                ReelPhase.COUNTDOWN -> if (timed) {
                    val parts = RaceTime.countdown(item.start, now)
                    Text(
                        buildString {
                            if (parts.days > 0) append("${parts.days} d  ")
                            append("%02d h  %02d min".format(parts.hours, parts.minutes))
                        },
                        style = PitText.DataValue.copy(fontSize = 22.sp),
                        color = Color.White,
                        modifier = Modifier.semantics { contentDescription = "${parts.days} d ${parts.hours} h ${parts.minutes} min" },
                    )
                } else {
                    Text(stringResource(R.string.reel_time_tbc), style = PitText.DataLabel, color = PitColors.Silver)
                }
            }
            if (place.isNotBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.LocationOn, null, Modifier.size(16.dp), tint = PitColors.Silver)
                    Text(place, style = PitText.BodyMd, color = PitColors.Silver, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(
                Modifier.clip(PitShapes.Pill).clickable { onOpenCategory(category.id) }.heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StateMark(CategoryVisuals.dataState(category.id))
                Text(category.name, style = PitText.DataLabel, color = Color.White)
                if (item.round != null) {
                    Text(if (item.rounds >= item.round) stringResource(R.string.reel_round_of, item.round, item.rounds) else stringResource(R.string.reel_round, item.round), style = PitText.DataLabel.copy(fontWeight = FontWeight.Medium), color = PitColors.Silver)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(14.dp), tint = PitColors.Silver)
            }
        }

        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlyphButton(
                icon = if (following) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                label = stringResource(if (following) R.string.reel_following else R.string.reel_follow),
                onClick = { onToggleFollow(category.id, category.name) },
                active = following,
                red = true,
            )
            if (item.phase != ReelPhase.WRAPPED) {
                GlyphButton(Icons.Outlined.EventAvailable, stringResource(R.string.reel_agenda), onClick = { RaceActions.addToCalendar(context, category, race, portuguese) })
            }
            GlyphButton(Icons.Outlined.Share, stringResource(R.string.reel_share), onClick = { RaceActions.share(context, category, race, portuguese, locale) })
            GlyphButton(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.reel_open), onClick = { onOpenRace(category.id, race.id) })
        }
    }
}

/** "Domingo, 25 out" / "Sunday, 25 Oct". */
internal fun weekdayDay(date: java.time.ZonedDateTime, locale: java.util.Locale): String {
    val weekday = date.dayOfWeek.getDisplayName(JTextStyle.FULL, locale)
    val month = date.month.getDisplayName(JTextStyle.SHORT, locale).trimEnd('.')
    return "${weekday.replaceFirstChar { it.titlecase(locale) }}, ${date.dayOfMonth} $month"
}

