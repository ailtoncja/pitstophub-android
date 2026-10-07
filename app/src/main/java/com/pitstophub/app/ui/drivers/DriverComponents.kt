package com.pitstophub.app.ui.drivers

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.StandingItem
import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.domain.Nationality
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.teams.parseTeamColor
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText

/** Classificacao de um piloto na categoria (se ele aparece na tabela de pilotos ou de alguma classe). */
fun standingOfDriver(category: Category, driver: Driver): StandingItem? {
    val standings = category.standings ?: return null
    val pools = listOfNotNull(standings.drivers) + standings.driverClasses.orEmpty().map { it.entries }
    for (pool in pools) {
        pool.firstOrNull { DriverLookup.find(listOf(driver), it.name) != null }?.let { return it }
    }
    return null
}

/** Piloto da categoria que corresponde a um nome de classificacao/resultado ("M. Verstappen"). */
fun driverNamed(category: Category, name: String): Driver? = DriverLookup.find(category.drivers, name)

/** Cartao de piloto da aba Grid: faixa na cor da equipe, numero, nome, equipe e foto. */
@Composable
fun DriverCard(
    driver: Driver,
    team: Team?,
    standing: StandingItem?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = parseTeamColor(team?.color)
    PitCard(modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().height(104.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(4.dp).fillMaxSize().background(color))
            if (standing != null) {
                Column(Modifier.width(52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("P${standing.position}", style = PitText.DataValue.copy(fontSize = 16.sp), color = if (standing.position == 1) PitColors.Primary else PitColors.OnSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f).padding(start = if (standing != null) 0.dp else 16.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (driver.number.isNotBlank() && driver.number != "--") {
                        Text(driver.number, style = PitText.DataValue.copy(fontSize = 18.sp, lineHeight = 20.sp), color = color)
                    }
                    val flag = Nationality.flag(driver.nationality)
                    if (flag.isNotEmpty()) Text(flag, fontSize = 16.sp)
                }
                Text(
                    driver.name.uppercase(),
                    style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(team?.name, standing?.let { "${formatPoints(it.points)} pts" }).joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(subtitle.uppercase(), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val portrait = driver.cutout ?: driver.image
            if (portrait != null) {
                NetworkImage(
                    portrait,
                    Modifier.size(width = 100.dp, height = 104.dp),
                    contentScale = if (driver.cutout != null) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (driver.cutout != null) Alignment.BottomCenter else Alignment.TopCenter,
                )
            }
        }
    }
}

/** Linha compacta de piloto (lista da pagina da categoria): numero, nome, equipe. */
@Composable
fun DriverLine(driver: Driver, team: Team?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).background(PitColors.SurfaceLow).padding(vertical = 10.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(30.dp).background(PitColors.OutlineVariant))
        Text(
            driver.number.takeIf { it.isNotBlank() && it != "--" } ?: "",
            Modifier.width(52.dp).padding(start = 12.dp),
            style = PitText.DataValue.copy(fontSize = 16.sp),
            color = PitColors.OnSurface,
            maxLines = 1,
        )
        Column(Modifier.weight(1f)) {
            Text(driver.name.uppercase(), style = PitText.BodyMd, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (team != null) Text(team.name.uppercase(), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        val flag = Nationality.flag(driver.nationality)
        if (flag.isNotEmpty()) Text(flag, fontSize = 18.sp)
    }
}
