package com.pitstophub.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.CircuitArt
import com.pitstophub.app.ui.LocalAppContainer
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText

/**
 * Campo do "Reel": o tom da categoria tinge o fundo; a midia e o mapa do circuito quando existe e, senao, o desenho
 * do carro da serie. Um veu preto embaixo carrega o texto. `bare` mostra so o campo (telas com foto propria).
 */
@Composable
fun HeroBackdrop(
    categoryId: String,
    @Suppress("UNUSED_PARAMETER") categoryName: String,
    race: Race?,
    modifier: Modifier = Modifier,
    artHeight: Dp = 230.dp,
    hue: Color? = null,
    bare: Boolean = false,
    compactCar: Boolean = false,
) {
    val tone = hue ?: CategoryVisuals.accent(categoryId)
    val circuit = if (bare) null else rememberCircuit(categoryId, race)
    val track = CircuitArt.assetFor(circuit?.trackImage)
    Box(modifier.fillMaxSize().background(PitColors.Carbon)) {
        Box(
            Modifier.fillMaxSize().drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to lerp(Color.Black, tone, 0.46f),
                        0.46f to lerp(Color.Black, tone, 0.18f),
                        1f to Color.Black,
                        center = Offset(size.width * 0.75f, size.height * 0.08f),
                        radius = size.maxDimension * 0.9f,
                    ),
                )
            },
        )
        when {
            track != null -> Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(artHeight)) {
                AsyncImage(
                    model = track,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 18.dp),
                    contentScale = ContentScale.Fit,
                )
                // Desenho feito a partir do OpenStreetMap: o credito (ODbL) acompanha o desenho.
                if (circuit?.osm == true) {
                    Text(
                        "© OpenStreetMap",
                        Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 2.dp),
                        style = PitText.DataLabelXs.copy(fontSize = 9.sp),
                        color = Color.White.copy(alpha = 0.6f),
                    )
                }
            }
            !bare -> Image(
                painter = painterResource(CategoryVisuals.car(categoryId)),
                contentDescription = null,
                modifier = if (compactCar) {
                    Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 56.dp).fillMaxWidth(0.55f).aspectRatio(1672f / 941f)
                } else {
                    Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp).widthIn(max = 560.dp).fillMaxWidth().height(artHeight + 40.dp)
                },
                contentScale = ContentScale.Fit,
                alpha = 0.85f,
                colorFilter = ColorFilter.tint(lerp(Color.White, tone, 0.3f), androidx.compose.ui.graphics.BlendMode.Modulate),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.30f to Color.Transparent, 0.48f to Color.Black.copy(alpha = 0.35f), 0.74f to Color.Black.copy(alpha = 0.88f), 1f to Color.Black),
            ),
        )
    }
}

/** Ficha do circuito da etapa nesta categoria (o tracado certo do lugar; nada no WRC). */
@Composable
fun rememberCircuit(categoryId: String, race: Race?): CircuitInfo? {
    val webData = LocalAppContainer.current.webData
    return remember(categoryId, race) { race?.let { webData.circuitFor(categoryId, it) } }
}

/** Ha mapa da pista para esta corrida? (o reel avisa quando nao ha). */
@Composable
fun hasTrackArt(categoryId: String, race: Race?): Boolean = rememberCircuit(categoryId, race)?.trackImage != null
