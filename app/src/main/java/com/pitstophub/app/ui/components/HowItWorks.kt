package com.pitstophub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/**
 * Atalho bem visivel para a pagina "Como funciona" da categoria (formato do fim de semana, pontuacao e regras).
 * Aparece na tela inicial e na pagina da categoria.
 */
@Composable
fun HowItWorksCard(categoryName: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PitCard(modifier.fillMaxWidth().clickable(onClick = onClick), borderColor = PitColors.Primary.copy(alpha = 0.45f)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(48.dp).clip(PitShapes.Card).background(PitColors.PrimaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.rules_entry_title).uppercase(),
                    style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp),
                    color = PitColors.OnSurface,
                )
                Text(stringResource(R.string.rules_entry_subtitle, categoryName), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PitColors.Primary, modifier = Modifier.size(20.dp))
        }
    }
}

/** Icone de cada secao de regras, escolhido pelo titulo (os titulos vem do catalogo, em PT ou EN). */
internal fun ruleIcon(title: String): ImageVector {
    val t = title.lowercase()
    return when {
        "pontua" in t || "point" in t || "scoring" in t || "classifica" in t || "standing" in t -> Icons.Filled.Leaderboard
        "balance" in t || "bop" in t -> Icons.Filled.Tune
        "classe" in t || "class" in t || "piloto e" in t || "driver and" in t -> Icons.Filled.Groups
        "carro" in t || " car" in t || t.startsWith("car") || "regulamento" in t || "rally1" in t || "brido" in t || "hybrid" in t -> Icons.Filled.DirectionsCar
        "curiosidade" in t || "trivia" in t || "fun fact" in t -> Icons.Filled.Lightbulb
        else -> Icons.Filled.Flag
    }
}
