package com.pitstophub.app.ui.agenda

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.domain.CategoryGroups
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.domain.Reel
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.components.CategoryChip
import com.pitstophub.app.ui.components.StateMark
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.home.weekdayDay
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Agenda: todas as largadas a frente, de todas as categorias, por dia (igual a /agenda do site). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgendaScreen(
    all: Collection<CategoryData>,
    followed: Set<String>,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenRace: (categoryId: String, raceId: String) -> Unit,
) {
    val now by rememberNow(60_000)
    val zone = ZoneId.systemDefault()
    val locale = currentLocale()
    val portuguese = isPortuguese()
    var scope by rememberSaveable { mutableStateOf("all") }

    val pool = all.filter { data ->
        when (scope) {
            "all" -> true
            "following" -> data.category.id in followed
            else -> CategoryGroups.all.getOrNull(scope.toIntOrNull() ?: -1)?.ids?.contains(data.category.id) == true
        }
    }
    val entries = Reel.upcoming(pool, now, zone).take(80)
    val days = entries.groupBy { RaceTime.startInstant(it.second, zone).atZone(zone).toLocalDate() }
    val today = LocalDate.now(zone)

    val options = buildList {
        add("all" to stringResource(R.string.agenda_all))
        if (followed.isNotEmpty()) add("following" to stringResource(R.string.agenda_following))
        CategoryGroups.all.forEachIndexed { i, group -> add(i.toString() to stringResource(group.title)) }
    }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp)) {
                    Text(stringResource(R.string.nav_agenda), style = PitText.HeadlineLgMobile, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.agenda_subtitle), style = PitText.BodyMd, color = PitColors.Silver)
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(options, key = { it.first }) { (value, label) ->
                        CategoryChip(label = label, selected = scope == value, onClick = { scope = value })
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(if (scope == "following") R.string.agenda_empty_following else R.string.reel_empty),
                        Modifier.padding(16.dp),
                        style = PitText.BodyMd,
                        color = PitColors.Silver,
                    )
                }
            }
            days.forEach { (day, list) ->
                item(key = "day-$day") {
                    Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(weekdayDay(day.atStartOfDay(zone), locale), style = PitText.TitleSm, color = Color.White)
                        if (day == today || day == today.plusDays(1)) {
                            Text(
                                stringResource(if (day == today) R.string.agenda_today else R.string.agenda_tomorrow),
                                Modifier.padding(start = 8.dp),
                                style = PitText.DataLabelXs,
                                color = PitColors.Silver,
                            )
                        }
                    }
                }
                item(key = "list-$day") {
                    Column(
                        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(PitShapes.Card).background(PitColors.Asphalt)
                            .border(1.dp, PitColors.Hairline, PitShapes.Card),
                    ) {
                        list.forEachIndexed { i, (data, race) ->
                            if (i > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                            AgendaRow(data, race, portuguese, zone) { onOpenRace(data.category.id, race.id) }
                        }
                    }
                }
            }
            item {
                Text(stringResource(R.string.agenda_tbc_note), Modifier.padding(16.dp), style = PitText.BodySm, color = PitColors.SilverDim)
            }
        }
    }
}

@Composable
private fun AgendaRow(data: CategoryData, race: Race, portuguese: Boolean, zone: ZoneId, onClick: () -> Unit) {
    val category = data.category
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.width(96.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StateMark(CategoryVisuals.dataState(category.id), color = Color.White.copy(alpha = 0.8f))
            Text(category.name, style = PitText.DataLabelSm, color = PitColors.Silver, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f)) {
            Text(if (portuguese) race.name else race.enName ?: race.name, style = PitText.TitleSm.copy(fontWeight = FontWeight.SemiBold), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val place = RaceFormat.place(race.circuit, if (portuguese) race.location else race.enLocation ?: race.location)
            if (place.isNotBlank()) Text(place, style = PitText.BodySm, color = PitColors.Silver, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            if (RaceTime.hasKnownTime(race)) RaceTime.startInstant(race, zone).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm")) else "—",
            style = PitText.DataValue.copy(fontSize = 16.sp),
            color = if (RaceTime.hasKnownTime(race)) Color.White else PitColors.SilverDim,
        )
    }
}

