package com.pitstophub.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R

// Fontes variaveis do design (Google Fonts, licenca OFL): Anybody para titulos, Inter para texto e
// JetBrains Mono para dados. Variacao de eixo exige API 26+, que e o minSdk do app.

private fun anybody(weight: Int, italic: Boolean) = Font(
    resId = if (italic) R.font.anybody_italic else R.font.anybody,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.width(100f)),
)

private fun inter(weight: Int) = Font(
    resId = R.font.inter,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun mono(weight: Int) = Font(
    resId = R.font.jetbrains_mono,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val AnybodyFamily = FontFamily(
    anybody(600, italic = true), anybody(700, italic = true), anybody(800, italic = true),
    anybody(600, italic = false), anybody(700, italic = false), anybody(800, italic = false),
)
val InterFamily = FontFamily(inter(400), inter(500), inter(600), inter(700))
val MonoFamily = FontFamily(mono(400), mono(500), mono(700))

/** Estilos nomeados exatamente como os tokens de tipografia do DESIGN.md. */
object PitText {
    val DisplayLg = TextStyle(
        fontFamily = AnybodyFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold,
        fontSize = 72.sp, lineHeight = 79.sp, letterSpacing = (-0.04).em,
    )
    val HeadlineLg = TextStyle(
        fontFamily = AnybodyFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
        fontSize = 40.sp, lineHeight = 48.sp, letterSpacing = (-0.02).em,
    )
    val HeadlineLgMobile = TextStyle(
        fontFamily = AnybodyFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Bold,
        fontSize = 32.sp, lineHeight = 38.sp,
    )
    val HeadlineMd = TextStyle(
        fontFamily = AnybodyFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp, lineHeight = 31.sp,
    )
    val BodyLg = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 29.sp)
    val BodyMd = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp)
    val BodySm = TextStyle(fontFamily = InterFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val DataLabel = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.05.em,
    )
    val DataLabelSm = DataLabel.copy(fontSize = 12.sp, lineHeight = 15.sp)
    val DataLabelXs = DataLabel.copy(fontSize = 10.sp, lineHeight = 12.sp)
    val DataValue = TextStyle(
        fontFamily = MonoFamily, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 20.sp,
    )

    /** Rotulo de botao: Anybody italico em caixa alta, como "font-display-lg text-data-label italic uppercase". */
    val ButtonLabel = TextStyle(
        fontFamily = AnybodyFamily, fontStyle = FontStyle.Italic, fontWeight = FontWeight.ExtraBold,
        fontSize = 14.sp, lineHeight = 17.sp, letterSpacing = 0.05.em,
    )
}

/** Mapeia os tokens para o tipografia do Material 3, para componentes que usam o tema por padrao. */
val PitTypography = Typography(
    displayLarge = PitText.DisplayLg,
    headlineLarge = PitText.HeadlineLg,
    headlineMedium = PitText.HeadlineLgMobile,
    headlineSmall = PitText.HeadlineMd,
    titleLarge = PitText.HeadlineMd,
    bodyLarge = PitText.BodyLg,
    bodyMedium = PitText.BodyMd,
    bodySmall = PitText.BodySm,
    labelLarge = PitText.DataLabel,
    labelMedium = PitText.DataLabelSm,
    labelSmall = PitText.DataLabelXs,
)
