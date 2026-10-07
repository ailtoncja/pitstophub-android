package com.pitstophub.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.ui.CircuitArt
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText
import kotlin.math.tan

/**
 * Fundo dos herois: gradiente escuro, faixas vermelhas a 15 graus, textura de carbono e, na F1, o mapa do
 * circuito. Categorias sem mapa mostram o nome da serie como marca d'agua.
 */
@Composable
fun HeroBackdrop(
    categoryId: String,
    categoryName: String,
    race: Race?,
    modifier: Modifier = Modifier,
    artHeight: Dp = 230.dp,
) {
    val art = race?.let { CircuitArt.forRace(categoryId, it) }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.linearGradient(listOf(Color(0xFF2E0707), Color(0xFF0C0C12), Color(0xFF000000))),
            ),
        )
        Canvas(Modifier.fillMaxSize()) {
            val skew = (size.height * tan(Math.toRadians(15.0))).toFloat()
            fun slash(x: Float, width: Float, alpha: Float) {
                val path = Path().apply {
                    moveTo(x + skew, 0f); lineTo(x + skew + width, 0f); lineTo(x + width, size.height); lineTo(x, size.height); close()
                }
                drawPath(path, PitColors.PrimaryContainer.copy(alpha = alpha))
            }
            slash(size.width * 0.50f, 26.dp.toPx(), 0.10f)
            slash(size.width * 0.66f, 10.dp.toPx(), 0.20f)
            slash(size.width * 0.78f, 54.dp.toPx(), 0.07f)
        }
        Box(Modifier.fillMaxSize().carbonTexture(0.035f))

        if (art != null) {
            Image(
                painter = painterResource(art),
                contentDescription = null,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().height(artHeight).padding(horizontal = 28.dp, vertical = 20.dp),
                contentScale = ContentScale.Fit,
            )
        } else if (categoryName.isNotEmpty()) {
            Text(
                text = categoryName.uppercase(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 20.dp),
                style = PitText.DisplayLg.copy(fontSize = 64.sp, lineHeight = 70.sp),
                color = Color.White.copy(alpha = 0.07f),
                maxLines = 1,
            )
        }
        // degrade para o fundo: garante a leitura do texto
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.30f to Color.Transparent, 0.75f to PitColors.Carbon.copy(alpha = 0.88f), 1f to PitColors.Carbon),
            ),
        )
    }
}
