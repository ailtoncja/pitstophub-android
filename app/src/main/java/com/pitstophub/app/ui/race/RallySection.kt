package com.pitstophub.app.ui.race

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.LocationOff
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.RallyDay
import com.pitstophub.app.data.model.RallyInfo
import com.pitstophub.app.data.model.RallyMap
import com.pitstophub.app.data.model.RallySurface
import com.pitstophub.app.domain.RallyRules
import com.pitstophub.app.ui.CircuitArt
import com.pitstophub.app.ui.components.FilterPill
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.SectionHeader
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import java.text.NumberFormat
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Ficha do rali (WRC), porte de src/components/RallyCard.tsx: piso, numeros-chave, mapa das especiais (quando ha
// localizacao confiavel), itinerario por dia com o horario no fuso do aparelho, e as fontes.

// Cor do piso: so a bolinha (marca de dado), nunca cor de texto ou de interface.
private val GRAVEL = Color(0xFFC58B4C)
private val TARMAC = Color(0xFF8A94A6)
private val SNOW = Color(0xFFBFE3FF)

// Mapa: terra e mar (o mar so aparece quando ha costa do OSM).
private val LAND = Color(0xFF17171A)
private val SEA = Color(0xFF060607)
private val SCALE_GREY = Color(0xFFB9B9C0)

// Escala no canto superior direito (o inferior esquerdo costuma ser do parque de assistencia), em unidades do mapa.
private const val SCALE_Y = 72f

@Composable
fun RallySection(info: RallyInfo, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    val km = remember(locale) { kmFormatter(locale) }
    val key = remember(info) { RallyRules.keyNumbers(info) }
    // "all" ou a data do dia escolhido: apaga no mapa as especiais dos outros dias.
    var day by rememberSaveable(info.id) { mutableStateOf("all") }
    val active = remember(day, info) { if (day == "all") null else info.days.firstOrNull { it.date == day }?.let(RallyRules::routesOf).orEmpty() }
    val map = info.map

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                stringResource(if (map != null) R.string.rally_title else R.string.rally_title_no_map),
                Modifier.weight(1f),
                style = PitText.HeadlineMd,
                color = Color.White,
            )
            SurfaceTag(info.surface)
        }

        if (map != null) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill(stringResource(R.string.rally_all_days), day == "all", { day = "all" })
                info.days.forEach { d ->
                    FilterPill("${RallyRules.weekdayShort(java.time.LocalDate.parse(d.date).dayOfWeek, locale)} ${RallyRules.dayOfMonth(d.date)}", day == d.date, { day = d.date })
                }
            }
            RallyMapView(info, map, active)
        }

        KeyFacts(info, key, km)

        if (map == null) NoMapCard(info.mapNote?.text(isPortuguese()))

        Itinerary(info, selectedDay = day.takeIf { it != "all" }, km = km)
    }
}

private fun kmFormatter(locale: Locale): (Double) -> String {
    val format = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 2
        maximumFractionDigits = 2
    }
    return { value -> "${format.format(value)} km" }
}

@Composable
private fun SurfaceTag(surface: RallySurface) {
    val label = stringResource(
        when (surface) {
            RallySurface.GRAVEL -> R.string.rally_surface_gravel
            RallySurface.TARMAC -> R.string.rally_surface_tarmac
            RallySurface.SNOW -> R.string.rally_surface_snow
            RallySurface.MIXED -> R.string.rally_surface_mixed
        },
    )
    val description = "${stringResource(R.string.rally_surface_label)}: $label"
    Row(
        Modifier.clip(PitShapes.Pill).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Pill)
            .padding(horizontal = 12.dp, vertical = 7.dp).semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val dot = Modifier.size(10.dp).clip(CircleShape)
        when (surface) {
            RallySurface.GRAVEL -> Box(dot.background(GRAVEL))
            RallySurface.TARMAC -> Box(dot.background(TARMAC))
            RallySurface.SNOW -> Box(dot.background(SNOW))
            RallySurface.MIXED -> Box(dot.background(Brush.horizontalGradient(0f to GRAVEL, 0.5f to GRAVEL, 0.5f to TARMAC, 1f to TARMAC)))
        }
        Text(label, style = PitText.DataLabelSm, color = Color.White)
    }
}

// --- numeros-chave ------------------------------------------------------------------------------------

@Composable
private fun KeyFacts(info: RallyInfo, key: com.pitstophub.app.domain.RallyKeyNumbers, km: (Double) -> String) {
    val and = stringResource(R.string.common_and)
    val notPublished = stringResource(R.string.rally_not_published)
    val longest = key.longest.firstOrNull()
    PitCard(Modifier.fillMaxWidth()) {
        Column {
            FactRow(stringResource(R.string.rally_stages), key.count.toString())
            HorizontalDivider(color = PitColors.HairlineSoft)
            FactRow(stringResource(R.string.rally_competitive_km), km(key.km))
            HorizontalDivider(color = PitColors.HairlineSoft)
            FactRow(
                stringResource(R.string.rally_longest),
                if (longest != null) km(key.longestKm) else notPublished,
                longest?.let { "${RallyRules.stageBaseName(it.name)} · ${key.longest.joinToString(" $and ") { stage -> "SS${stage.number}" }}" },
            )
            HorizontalDivider(color = PitColors.HairlineSoft)
            val power = key.powerStage
            FactRow(
                stringResource(R.string.rally_power_stage),
                if (power != null) "SS${power.number} · ${km(power.km)}" else notPublished,
                power?.let { RallyRules.stageBaseName(it.name) },
            )
            HorizontalDivider(color = PitColors.HairlineSoft)
            FactRow(stringResource(R.string.rally_service), info.servicePark ?: notPublished, dim = info.servicePark == null)
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, detail: String? = null, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(132.dp), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(value, style = PitText.DataLabel, color = if (dim) PitColors.Silver else PitColors.OnSurface, textAlign = TextAlign.End)
            detail?.let { Text(it, style = PitText.BodySm, color = PitColors.Silver, textAlign = TextAlign.End) }
        }
    }
}

@Composable
private fun NoMapCard(note: String?) {
    PitCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Outlined.LocationOff, null, tint = PitColors.SilverDim, modifier = Modifier.size(32.dp))
            Text(stringResource(R.string.rally_no_map), style = PitText.TitleSm, color = Color.White)
            Text(note ?: stringResource(R.string.rally_no_map_hint), style = PitText.BodySm, color = PitColors.Silver, textAlign = TextAlign.Center)
        }
    }
}

// --- mapa ---------------------------------------------------------------------------------------------

private class ParsedMap(val land: Path?, val routes: Map<String, Path>)

@Composable
private fun RallyMapView(info: RallyInfo, map: RallyMap, active: Set<String>?) {
    val portuguese = isPortuguese()
    val uriHandler = LocalUriHandler.current
    // Os paths SVG sao lidos uma vez por mapa; o desenho escala pelo tamanho do quadro.
    val parsed = remember(map) {
        ParsedMap(
            land = map.land.takeIf { it.isNotBlank() }?.let { PathParser().parsePathString(it).toPath().apply { fillType = PathFillType.EvenOdd } },
            routes = map.routes.mapNotNull { route -> route.path?.let { route.id to PathParser().parsePathString(it).toPath() } }.toMap(),
        )
    }
    val power = remember(info) { RallyRules.powerRoutes(info) }
    val measurer = rememberTextMeasurer()
    val wrench = rememberVectorPainter(Icons.Filled.Build)
    val serviceText = stringResource(R.string.rally_service_short)
    val description = stringResource(R.string.rally_map_alt)
    val labelStyle = PitText.DataLabelXs.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold)
    val townStyle = PitText.DataLabelXs.copy(fontSize = 10.sp, lineHeight = 12.sp, color = PitColors.SilverDim)

    PitCard(Modifier.fillMaxWidth()) {
        Column {
            Box(Modifier.fillMaxWidth().aspectRatio((map.width / map.height).toFloat())) {
                Canvas(Modifier.matchParentSize().semantics { contentDescription = description }) {
                    val s = size.width / map.width.toFloat()
                    fun at(x: Double, y: Double) = Offset(x.toFloat() * s, y.toFloat() * s)
                    val isOn = { id: String -> active == null || id in active }

                    drawRect(if (parsed.land != null) SEA else LAND)
                    // Geometria em unidades do mapa; os tracos ficam com espessura fixa na tela (1/s).
                    withTransform({ scale(s, s, Offset.Zero) }) {
                        parsed.land?.let {
                            drawPath(it, LAND)
                            drawPath(it, Color.White.copy(alpha = 0.16f), style = Stroke(width = 1f / s, join = StrokeJoin.Round))
                        }
                        for (route in map.routes) {
                            if (!isOn(route.id)) continue
                            drawLine(Color.White.copy(alpha = 0.35f), point(route.leaderFrom), point(route.labelAt), strokeWidth = 1f / s)
                        }
                        val scaleX = (map.width - 28 - map.scaleBar.px).toFloat()
                        val scaleEnd = scaleX + map.scaleBar.px.toFloat()
                        drawLine(SCALE_GREY, Offset(scaleX, SCALE_Y), Offset(scaleEnd, SCALE_Y), strokeWidth = 2f / s)
                        drawLine(SCALE_GREY, Offset(scaleX, SCALE_Y - 5), Offset(scaleX, SCALE_Y + 5), strokeWidth = 1.5f / s)
                        drawLine(SCALE_GREY, Offset(scaleEnd, SCALE_Y - 5), Offset(scaleEnd, SCALE_Y + 5), strokeWidth = 1.5f / s)
                        for (route in map.routes) {
                            val alpha = if (isOn(route.id)) 1f else 0.22f
                            parsed.routes[route.id]?.let { path ->
                                drawPath(path, Color.Black.copy(alpha = 0.6f * alpha), style = Stroke(6f / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                                drawPath(path, Color.White.copy(alpha = alpha), style = Stroke((if (route.id in power) 3.5f else 2.5f) / s, cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                            drawCircle(Color.White.copy(alpha = alpha), 7f, point(route.start))
                            drawCircle(Color.Black.copy(alpha = alpha), 7f, point(route.start), style = Stroke(1.5f / s))
                            drawCircle(Color.Black.copy(alpha = alpha), 6f, point(route.finish))
                            drawCircle(Color.White.copy(alpha = alpha), 6f, point(route.finish), style = Stroke(2f / s))
                        }
                    }

                    // Textos em tamanho fixo de tela.
                    for (town in map.towns) {
                        val layout = measurer.measure(town.name, townStyle)
                        drawText(layout, topLeft = at(town.x, town.y) - Offset(layout.size.width / 2f, layout.size.height / 2f))
                    }
                    val scaleLabel = measurer.measure("${formatScale(map.scaleBar.km)} km", townStyle.copy(color = PitColors.Silver))
                    drawText(scaleLabel, topLeft = Offset(((map.width - 28 - map.scaleBar.px) * s).toFloat(), SCALE_Y * s - 6.dp.toPx() - scaleLabel.size.height))

                    for (route in map.routes) {
                        if (!isOn(route.id)) continue
                        val highlight = route.id in power
                        drawPill(measurer.measure(route.label, labelStyle.copy(color = if (highlight) Color.Black else Color.White)), at(route.labelAt[0], route.labelAt[1]), route.anchor, highlight)
                    }

                    map.servicePark?.let { park ->
                        val center = at(park.x, park.y)
                        val box = 24.dp.toPx()
                        drawRoundRect(Color.White, topLeft = center - Offset(box / 2, box / 2), size = Size(box, box), cornerRadius = CornerRadius(6.dp.toPx()))
                        val icon = 14.dp.toPx()
                        translate(center.x - icon / 2, center.y - icon / 2) {
                            with(wrench) { draw(Size(icon, icon), colorFilter = ColorFilter.tint(Color.Black)) }
                        }
                        val text = measurer.measure(serviceText, townStyle.copy(color = Color.White))
                        val y = if (map.serviceLabel == "above") center.y - box / 2 - 4.dp.toPx() - text.size.height else center.y + box / 2 + 4.dp.toPx()
                        drawText(text, topLeft = Offset(center.x - text.size.width / 2f, y), shadow = androidx.compose.ui.graphics.Shadow(Color.Black, blurRadius = 8f))
                    }
                }
            }

            HorizontalDivider(color = PitColors.HairlineSoft)
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(stringResource(R.string.rally_start)) { Box(Modifier.size(10.dp).clip(CircleShape).background(Color.White).border(1.dp, Color.Black, CircleShape)) }
                    Legend(stringResource(R.string.rally_finish)) { Box(Modifier.size(10.dp).clip(CircleShape).background(Color.Black).border(2.dp, Color.White, CircleShape)) }
                    if (map.servicePark != null) {
                        Legend(stringResource(R.string.rally_service)) { Box(Modifier.size(11.dp).clip(RoundedCornerShape(3.dp)).background(Color.White)) }
                    }
                }
                val note = stringResource(if (map.drawsRoutes) R.string.rally_route_note else R.string.rally_points_note)
                Text(listOfNotNull(note, info.mapNote?.text(portuguese)).joinToString(" "), style = PitText.BodySm, color = PitColors.SilverDim)
                if (map.usesOsm) {
                    Text(
                        "© OpenStreetMap",
                        Modifier.align(Alignment.End).heightIn(min = 32.dp).clickable { uriHandler.openUri(CircuitArt.OSM_COPYRIGHT_URL) }.padding(vertical = 6.dp),
                        style = PitText.DataLabelXs.copy(textDecoration = TextDecoration.Underline),
                        color = PitColors.Silver,
                    )
                }
            }
        }
    }
}

private fun point(xy: List<Double>) = Offset(xy[0].toFloat(), xy[1].toFloat())

private fun formatScale(km: Double): String = if (km % 1.0 == 0.0) km.toLong().toString() else km.toString()

/** Rotulo da especial: pilula ancorada a esquerda, a direita ou no centro do ponto, centrada na vertical. */
private fun DrawScope.drawPill(text: androidx.compose.ui.text.TextLayoutResult, anchorPoint: Offset, anchor: String, highlight: Boolean) {
    val padX = 6.dp.toPx()
    val height = 20.dp.toPx()
    val width = text.size.width + padX * 2
    val left = when (anchor) {
        "left" -> anchorPoint.x
        "right" -> anchorPoint.x - width
        else -> anchorPoint.x - width / 2
    }
    val top = anchorPoint.y - height / 2
    val radius = CornerRadius(height / 2)
    if (highlight) {
        drawRoundRect(Color.White, Offset(left, top), Size(width, height), radius)
    } else {
        drawRoundRect(Color.Black.copy(alpha = 0.8f), Offset(left, top), Size(width, height), radius)
        drawRoundRect(Color.White.copy(alpha = 0.25f), Offset(left, top), Size(width, height), radius, style = Stroke(1.dp.toPx()))
    }
    drawText(text, topLeft = Offset(left + padX, top + (height - text.size.height) / 2))
}

@Composable
private fun Legend(label: String, mark: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        mark()
        Text(label, style = PitText.DataLabelXs, color = PitColors.Silver)
    }
}

// --- itinerario ---------------------------------------------------------------------------------------

@Composable
private fun Itinerary(info: RallyInfo, selectedDay: String?, km: (Double) -> String) {
    val locale = currentLocale()
    val zone = ZoneId.systemDefault()
    val firstTimed = remember(info) { RallyRules.stages(info).firstOrNull { it.startsAt != null }?.startsAt }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.rally_itinerary))
        firstTimed?.let { iso ->
            val label = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone).format(DateTimeFormatter.ofPattern("zzz", locale)) }.getOrNull()
            if (label != null) Text(stringResource(R.string.rally_local_time, label), style = PitText.BodySm, color = PitColors.Silver)
        }
        info.days.forEach { day -> DayCard(day, selected = day.date == selectedDay, km = km, locale = locale) }
        val note = when {
            info.itinerarySecondary -> stringResource(R.string.rally_itinerary_secondary)
            info.itineraryVersion != null -> stringResource(R.string.rally_itinerary_note, info.itineraryVersion)
            else -> stringResource(R.string.rally_itinerary_note_no_version)
        }
        val noTimes = if (firstTimed == null) " ${stringResource(R.string.rally_no_times)}" else ""
        Text(note + noTimes, style = PitText.BodySm, color = PitColors.SilverDim)
        Sources(info)
    }
}

@Composable
private fun DayCard(day: RallyDay, selected: Boolean, km: (Double) -> String, locale: Locale) {
    PitCard(Modifier.fillMaxWidth(), borderColor = if (selected) Color.White.copy(alpha = 0.4f) else PitColors.Hairline) {
        Column {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                Text(
                    "${RallyRules.weekdayLong(day.date, locale)}, ${RallyRules.dayOfMonth(day.date)} ${RallyRules.monthShort(day.date, locale)}",
                    style = PitText.TitleSm,
                    color = Color.White,
                )
                Text(
                    if (day.stages.isEmpty()) stringResource(R.string.rally_day_no_stages)
                    else pluralStringResource(R.plurals.rally_day_totals, day.stages.size, day.stages.size, km(RallyRules.dayKm(day))),
                    style = PitText.BodySm,
                    color = PitColors.Silver,
                )
            }
            day.shakedown?.let { shakedown ->
                HorizontalDivider(color = PitColors.HairlineSoft)
                StageRow(
                    code = "SD",
                    name = shakedown.name,
                    detail = listOfNotNull(stringResource(R.string.rally_shakedown), shakedown.km?.let(km)).joinToString(" · "),
                    startsAt = shakedown.startsAt,
                    rallyDate = day.date,
                    dim = true,
                )
            }
            day.stages.forEach { stage ->
                HorizontalDivider(color = PitColors.HairlineSoft)
                StageRow(
                    code = "SS${stage.number}",
                    name = stage.name,
                    detail = km(stage.km),
                    startsAt = stage.startsAt,
                    rallyDate = day.date,
                    powerStage = stage.powerStage,
                )
            }
        }
    }
}

@Composable
private fun StageRow(code: String, name: String, detail: String, startsAt: String?, rallyDate: String, dim: Boolean = false, powerStage: Boolean = false) {
    val locale = currentLocale()
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(code, Modifier.width(40.dp), style = PitText.DataLabelSm, color = if (dim) PitColors.SilverDim else PitColors.Silver)
        Column(Modifier.weight(1f)) {
            Text(name, style = PitText.BodySm.copy(fontWeight = if (dim) FontWeight.Normal else FontWeight.SemiBold), color = if (dim) PitColors.Silver else Color.White)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail, style = PitText.DataLabelXs, color = if (dim) PitColors.SilverDim else PitColors.Silver)
                if (powerStage) {
                    Text(
                        stringResource(R.string.rally_power_stage),
                        Modifier.clip(PitShapes.Pill).background(Color.White).padding(horizontal = 8.dp, vertical = 1.dp),
                        style = PitText.DataLabelXs.copy(fontSize = 10.sp),
                        color = Color.Black,
                    )
                }
            }
        }
        startsAt?.let { iso -> RallyRules.stageClock(iso, rallyDate, locale) }?.let { clock ->
            Row(verticalAlignment = Alignment.Bottom) {
                clock.day?.let { Text("$it ", style = PitText.DataLabelSm.copy(fontWeight = FontWeight.Normal), color = PitColors.Silver) }
                Text(clock.time, style = PitText.DataLabelSm, color = if (dim) PitColors.SilverDim else Color.White)
            }
        }
    }
}

@Composable
private fun Sources(info: RallyInfo) {
    val uriHandler = LocalUriHandler.current
    var open by rememberSaveable(info.id) { mutableStateOf(false) }
    Column {
        Row(
            Modifier.heightIn(min = 44.dp).clickable { open = !open }.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.rally_sources), style = PitText.DataLabel, color = PitColors.Silver)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = PitColors.Silver, modifier = Modifier.size(18.dp))
        }
        if (open) {
            val secondary = stringResource(R.string.rally_secondary)
            info.sources.forEach { source ->
                Row(Modifier.fillMaxWidth().clickable { uriHandler.openUri(source.url) }.padding(vertical = 6.dp)) {
                    Text(
                        source.label,
                        style = PitText.BodySm.copy(textDecoration = TextDecoration.Underline),
                        color = PitColors.Silver,
                    )
                    if (source.secondary) Text(" · $secondary", style = PitText.BodySm, color = PitColors.SilverDim)
                }
            }
            if (info.map?.usesOsm == true) {
                Text(
                    stringResource(R.string.rally_osm_source),
                    Modifier.fillMaxWidth().clickable { uriHandler.openUri(CircuitArt.OSM_COPYRIGHT_URL) }.padding(vertical = 6.dp),
                    style = PitText.BodySm.copy(textDecoration = TextDecoration.Underline),
                    color = PitColors.Silver,
                )
            }
        }
    }
}

/** Placeholder enquanto a ficha (com o mapa) e lida do asset. */
@Composable
fun RallyLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(PitShapes.Card).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Card))
}
