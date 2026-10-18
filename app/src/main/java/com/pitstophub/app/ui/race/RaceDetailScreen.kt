package com.pitstophub.app.ui.race

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.model.SessionKind
import com.pitstophub.app.data.model.SessionSlot
import com.pitstophub.app.data.remote.RaceWeather
import com.pitstophub.app.data.remote.WeatherCondition
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.data.repo.RaceResultRow
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RacePhase
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.CircuitArt
import com.pitstophub.app.ui.calendar.WinnerBox
import com.pitstophub.app.ui.components.CountdownPanel
import com.pitstophub.app.ui.components.HeroBackdrop
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitStatTile
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.components.SkewBadge
import com.pitstophub.app.ui.components.rememberNow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import kotlin.math.roundToInt
import java.time.Instant

@Composable
fun RaceDetailScreen(state: RaceDetailState, onBack: () -> Unit, onOpenDriver: (String) -> Unit, modifier: Modifier = Modifier) {
    val portuguese = isPortuguese()
    val locale = currentLocale()
    val context = LocalContext.current
    val race = state.race
    val category = state.data?.category

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(title = race?.displayName(portuguese) ?: stringResource(R.string.nav_calendar), onBack = onBack)
        if (race == null || category == null) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    stringResource(if (state.notFound) R.string.race_not_found else R.string.loading),
                    style = PitText.BodyMd, color = PitColors.OnSurfaceVariant,
                )
            }
            return@Column
        }
        val now by rememberNow()

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "hero") { RaceHero(race, category, now, portuguese) }
            item(key = "actions") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ActionButton(Icons.Filled.CalendarMonth, stringResource(R.string.race_add_calendar), Modifier.weight(1f)) {
                        RaceActions.addToCalendar(context, category, race, portuguese)
                    }
                    ActionButton(Icons.Filled.Share, stringResource(R.string.race_share), Modifier.weight(1f)) {
                        RaceActions.share(context, category, race, portuguese, locale)
                    }
                }
            }
            state.weather?.let { weather -> item(key = "weather") { WeatherSection(weather) } }
            item(key = "info") { RaceInfo(race, portuguese) }
            if (race.status == RaceStatus.COMPLETED) race.winner?.let { winner -> item(key = "winner") { WinnerBox(winner, category) } }
            state.circuit?.info?.let { info -> item(key = "circuit") { CircuitSection(info, race.circuit, portuguese) } }
            if (race.sessions.isNotEmpty()) item(key = "sessions") { SessionsSection(race.sessions, now) }
            item(key = "results") { ResultsSection(state.results, category, onOpenDriver) }
        }
    }
}

@Composable
private fun ActionButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(PitShapes.Card).background(PitColors.SurfaceHigh).border(1.dp, PitColors.Hairline, PitShapes.Card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = PitColors.Primary, modifier = Modifier.size(20.dp))
        Text(label, style = PitText.ButtonLabel, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RaceHero(race: Race, category: Category, now: Instant, portuguese: Boolean) {
    val phase = if (race.status == RaceStatus.UPCOMING) RaceTime.phase(race, now) else null
    Box(Modifier.fillMaxWidth().height(320.dp).clip(PitShapes.Media)) {
        HeroBackdrop(category.id, category.name, race, artHeight = 190.dp)
        Column(Modifier.align(Alignment.BottomStart).padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(race.displayName(portuguese), style = PitText.HeadlineLgMobile, color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis)
            when {
                phase == RacePhase.COUNTDOWN -> CountdownPanel(RaceTime.startInstant(race), now)
                phase == RacePhase.LIVE -> SkewBadge(stringResource(R.string.status_live), live = true)
                phase == RacePhase.AWAITING_RESULT -> SkewBadge(stringResource(R.string.status_awaiting))
                race.status == RaceStatus.COMPLETED -> SkewBadge(stringResource(R.string.status_completed))
                race.status == RaceStatus.CANCELLED -> SkewBadge(stringResource(R.string.status_cancelled))
            }
        }
    }
}

// --- previsao do tempo -----------------------------------------------------------------------------

@Composable
private fun WeatherSection(weather: RaceWeather) {
    val condition = when (weather.condition) {
        WeatherCondition.CLEAR -> R.string.weather_clear
        WeatherCondition.CLOUDY -> R.string.weather_cloudy
        WeatherCondition.FOG -> R.string.weather_fog
        WeatherCondition.DRIZZLE -> R.string.weather_drizzle
        WeatherCondition.RAIN -> R.string.weather_rain
        WeatherCondition.SNOW -> R.string.weather_snow
        WeatherCondition.STORM -> R.string.weather_storm
    }
    val icon = when (weather.condition) {
        WeatherCondition.CLEAR -> Icons.Filled.WbSunny
        WeatherCondition.CLOUDY, WeatherCondition.FOG -> Icons.Filled.Cloud
        WeatherCondition.DRIZZLE, WeatherCondition.RAIN -> Icons.Filled.WaterDrop
        WeatherCondition.SNOW -> Icons.Filled.AcUnit
        WeatherCondition.STORM -> Icons.Filled.Thunderstorm
    }
    val rainRisk = when {
        weather.rainChance >= 60 -> R.string.weather_risk_high
        weather.rainChance >= 30 -> R.string.weather_risk_mid
        else -> R.string.weather_risk_low
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.race_weather))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherTile(icon, stringResource(R.string.weather_temp), "${weather.temperatureMax.roundToInt()}°C", stringResource(condition), Modifier.weight(1f))
            WeatherTile(
                Icons.Filled.WaterDrop, stringResource(R.string.weather_rain_chance), "${weather.rainChance}%", stringResource(rainRisk), Modifier.weight(1f),
                tint = PitColors.Silver,
            )
            WeatherTile(Icons.Filled.Air, stringResource(R.string.weather_wind), "${weather.windKmh.roundToInt()}", "km/h", Modifier.weight(1f))
        }
    }
}

@Composable
private fun WeatherTile(icon: ImageVector, label: String, value: String, caption: String, modifier: Modifier, tint: Color = PitColors.OnSurfaceVariant) {
    PitCard(modifier) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1)
                Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
            }
            Text(value, style = PitText.DataValue.copy(fontSize = 22.sp, lineHeight = 24.sp), color = Color.White, maxLines = 1)
            Text(caption, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// --- circuito ---------------------------------------------------------------------------------------

@Composable
private fun CircuitSection(info: CircuitInfo, circuitName: String, portuguese: Boolean) {
    val art = CircuitArt.forTrackImage(info.trackImage)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(stringResource(R.string.circuit_layout))
        if (art != null) {
            Box(Modifier.fillMaxWidth().height(220.dp).clip(PitShapes.Soft).background(PitColors.SurfaceLowest).border(1.dp, PitColors.Hairline, PitShapes.Soft)) {
                androidx.compose.foundation.Image(
                    painterResource(art), circuitName,
                    Modifier.fillMaxSize().padding(16.dp), contentScale = ContentScale.Fit,
                )
                if (info.drsZones.isNotEmpty()) {
                    Text(
                        stringResource(R.string.circuit_drs_count, info.drsZones.size),
                        Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(PitShapes.Card).background(PitColors.SurfaceHigh).padding(horizontal = 8.dp, vertical = 4.dp),
                        style = PitText.DataLabelXs, color = PitColors.OnSurface,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PitStatTile(stringResource(R.string.circuit_length), "%.3f".format(info.lengthKm), Modifier.weight(1f), caption = "km")
            PitStatTile(stringResource(R.string.circuit_corners), info.corners.toString(), Modifier.weight(1f), caption = stringResource(if (info.clockwise) R.string.circuit_clockwise else R.string.circuit_counterclockwise))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PitStatTile(stringResource(R.string.circuit_laps), info.laps.toString(), Modifier.weight(1f))
            PitStatTile(stringResource(R.string.circuit_distance), "%.1f".format(info.raceDistanceKm), Modifier.weight(1f), caption = "km")
        }
        PitCard(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Icon(Icons.Filled.Timer, null, tint = PitColors.OnSurfaceVariant, modifier = Modifier.size(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.circuit_record), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
                    val record = info.lapRecord
                    if (record != null) {
                        Text("${record.time} • ${record.driver} (${record.year})", style = PitText.BodyMd, color = PitColors.OnSurface)
                    } else {
                        Text(stringResource(R.string.circuit_no_record), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
                    }
                }
                Text(stringResource(R.string.circuit_since, info.firstGrandPrix), style = PitText.DataLabelXs, color = PitColors.Primary)
            }
        }
        if (info.brakingZones.isNotEmpty()) {
            Text(stringResource(R.string.circuit_braking), style = PitText.DataLabel, color = PitColors.OnSurface)
            PitCard(Modifier.fillMaxWidth()) {
                Column {
                    info.brakingZones.forEachIndexed { index, zone ->
                        if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(zone.turn, style = PitText.DataLabel.copy(fontWeight = FontWeight.Bold), color = PitColors.Primary)
                                Text(zone.name, style = PitText.BodyMd.copy(fontWeight = FontWeight.Bold), color = PitColors.OnSurface)
                            }
                            Text(zone.text(portuguese), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
        if (info.drsZones.isNotEmpty()) {
            Text(stringResource(R.string.circuit_drs), style = PitText.DataLabel, color = PitColors.OnSurface)
            PitCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    info.drsZones.forEach { zone ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("▸", color = PitColors.Primary)
                            Text(zone.text(portuguese), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
        Text(stringResource(R.string.circuit_tyres), style = PitText.DataLabel, color = PitColors.OnSurface)
        PitCard(Modifier.fillMaxWidth()) {
            Text(info.tyreStrategyNote.text(portuguese), Modifier.padding(14.dp), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
        }
    }
}

@Composable
private fun RaceInfo(race: Race, portuguese: Boolean) {
    val locale = currentLocale()
    PitCard(Modifier.fillMaxWidth()) {
        Column {
            InfoRow(stringResource(R.string.info_date), RaceFormat.dateTime(race, locale))
            race.circuit.takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(color = PitColors.HairlineSoft)
                InfoRow(stringResource(R.string.info_circuit), it)
            }
            race.displayLocation(portuguese).takeIf { it.isNotBlank() }?.let {
                HorizontalDivider(color = PitColors.HairlineSoft)
                InfoRow(stringResource(R.string.info_location), it)
            }
            race.round?.let {
                HorizontalDivider(color = PitColors.HairlineSoft)
                InfoRow(stringResource(R.string.info_round), "%02d".format(it))
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Text(label, style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
        Text(value, Modifier.weight(1f).padding(start = 16.dp), style = PitText.DataLabel, color = PitColors.OnSurface, textAlign = TextAlign.End)
    }
}

// --- programacao -------------------------------------------------------------------------------

@Composable
private fun SessionsSection(sessions: List<SessionSlot>, now: Instant) {
    val locale = currentLocale()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(stringResource(R.string.race_schedule))
        PitCard(Modifier.fillMaxWidth()) {
            Column {
                sessions.forEachIndexed { index, slot ->
                    if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                    val past = runCatching { Instant.parse(slot.startsAt).isBefore(now) }.getOrDefault(false)
                    val isRace = slot.kind == SessionKind.RACE
                    Row(
                        Modifier.fillMaxWidth().alpha(if (past) 0.5f else 1f).padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(sessionLabel(slot.kind)),
                            style = PitText.DataLabel.copy(fontWeight = if (isRace) FontWeight.Bold else FontWeight.Medium),
                            color = if (isRace) PitColors.Primary else PitColors.OnSurface,
                        )
                        Text(RaceFormat.sessionDateTime(slot.startsAt, locale), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

private fun sessionLabel(kind: SessionKind): Int = when (kind) {
    SessionKind.FP1 -> R.string.session_fp1
    SessionKind.FP2 -> R.string.session_fp2
    SessionKind.FP3 -> R.string.session_fp3
    SessionKind.SPRINT_QUALIFYING -> R.string.session_sprint_qualifying
    SessionKind.SPRINT -> R.string.session_sprint
    SessionKind.QUALIFYING -> R.string.session_qualifying
    SessionKind.RACE -> R.string.session_race
}

// --- resultados --------------------------------------------------------------------------------

@Composable
private fun ResultsSection(results: ResultsState, category: Category, onOpenDriver: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionHeader(stringResource(R.string.race_results))
        when (results) {
            ResultsState.Loading -> Text(stringResource(R.string.loading), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
            ResultsState.Failed -> Text(stringResource(R.string.race_results_failed), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
            ResultsState.NotAvailable -> Text(
                stringResource(if (category.id == "f1") R.string.race_results_pending else R.string.race_results_unavailable),
                style = PitText.BodyMd, color = PitColors.OnSurfaceVariant,
            )
            is ResultsState.Ready -> {
                Highlights(results.rows)
                ResultsTable(results.rows, category, onOpenDriver)
            }
        }
    }
}

/** Pole position e volta mais rapida da prova, em dois blocos. */
@Composable
private fun Highlights(rows: List<RaceResultRow>) {
    val pole = rows.firstOrNull { it.grid == "1" }
    val fastest = rows.firstOrNull { it.fastestLapRank == 1 }
    if (pole == null && fastest == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        PitStatTile(stringResource(R.string.race_pole), pole?.driver ?: "—", Modifier.weight(1f), caption = pole?.team)
        PitStatTile(stringResource(R.string.race_fastest_lap), fastest?.driver ?: "—", Modifier.weight(1f), caption = fastest?.fastestLapTime)
    }
}

@Composable
private fun ResultsTable(rows: List<RaceResultRow>, category: Category, onOpenDriver: (String) -> Unit) {
    PitCard(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val header = PitText.DataLabelXs
                Text(stringResource(R.string.col_pos), Modifier.width(36.dp), style = header, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.Center)
                Text(stringResource(R.string.col_driver), Modifier.weight(1f).padding(horizontal = 8.dp), style = header, color = PitColors.OnSurfaceVariant)
                Text(stringResource(R.string.col_time), Modifier.width(92.dp), style = header, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.End)
                Text(stringResource(R.string.col_pts), Modifier.width(36.dp), style = header, color = PitColors.OnSurfaceVariant, textAlign = TextAlign.End)
            }
            HorizontalDivider(color = PitColors.Hairline)
            rows.forEachIndexed { index, row ->
                val driver = DriverLookup.find(category.drivers, row.driver)
                Row(
                    Modifier.fillMaxWidth().background(if (index % 2 == 0) PitColors.Asphalt else PitColors.AsphaltAlt)
                        .then(if (driver != null) Modifier.clickable { onOpenDriver(driver.id) } else Modifier)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.position, Modifier.width(36.dp),
                        style = PitText.DataValue.copy(fontSize = 16.sp), color = if (index == 0) PitColors.Primary else PitColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        Text(row.driver, style = PitText.HeadlineMd.copy(fontSize = 15.sp, lineHeight = 19.sp), color = PitColors.OnSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(row.team, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(row.timeOrStatus, Modifier.width(92.dp), style = PitText.DataLabelSm.copy(fontSize = 11.sp), color = PitColors.OnSurface, textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatPoints(row.points), Modifier.width(36.dp), style = PitText.DataLabel, color = PitColors.OnSurface, textAlign = TextAlign.End)
                }
                if (index < rows.lastIndex) HorizontalDivider(color = PitColors.HairlineSoft)
            }
        }
    }
}
