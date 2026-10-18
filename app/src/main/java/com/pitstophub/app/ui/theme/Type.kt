package com.pitstophub.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R

// Uma familia so, como no site: Archivo (Google Fonts, OFL), variavel em peso e largura. Numeros sempre tabulares.

private fun archivo(weight: Int, width: Float = 100f) = Font(
    resId = R.font.archivo,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(width)),
)

val ArchivoFamily = FontFamily(archivo(400), archivo(500), archivo(600), archivo(700), archivo(800))

// Apelidos antigos: todos apontam para a Archivo.
val AnybodyFamily = ArchivoFamily
val InterFamily = ArchivoFamily
val MonoFamily = ArchivoFamily

private const val TNUM = "tnum"

/** Escala do app (mesmos papeis do site). Frase, nada de caixa alta nem italico. */
object PitText {
    /** Titulo do reel. */
    val DisplayLg = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 38.sp, lineHeight = 38.sp, letterSpacing = (-0.035).em)
    val HeadlineLg = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 30.sp, lineHeight = 33.sp, letterSpacing = (-0.03).em)
    val HeadlineLgMobile = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 31.sp, letterSpacing = (-0.025).em)
    val HeadlineMd = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 24.sp, letterSpacing = (-0.015).em)
    val TitleSm = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 21.sp)
    val BodyLg = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp)
    val BodyMd = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp)
    val BodySm = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp)
    /** Rotulos e dados: tabulares, em frase. */
    val DataLabel = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 18.sp, fontFeatureSettings = TNUM)
    val DataLabelSm = DataLabel.copy(fontSize = 12.sp, lineHeight = 16.sp)
    val DataLabelXs = DataLabel.copy(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.Medium)
    val DataValue = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 22.sp, fontFeatureSettings = TNUM)
    val ButtonLabel = TextStyle(fontFamily = ArchivoFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp)
}

val PitTypography = Typography(
    displayLarge = PitText.DisplayLg,
    headlineLarge = PitText.HeadlineLg,
    headlineMedium = PitText.HeadlineLgMobile,
    headlineSmall = PitText.HeadlineMd,
    titleLarge = PitText.HeadlineMd,
    titleMedium = PitText.TitleSm,
    titleSmall = PitText.TitleSm.copy(fontSize = 14.sp),
    bodyLarge = PitText.BodyLg,
    bodyMedium = PitText.BodyMd,
    bodySmall = PitText.BodySm,
    labelLarge = PitText.DataLabel,
    labelMedium = PitText.DataLabelSm,
    labelSmall = PitText.DataLabelXs,
)
