package com.pitstophub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** Raios do "Reel": paineis grandes, controles em pilula. */
object PitShapes {
    val Sharp = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Soft = RoundedCornerShape(20.dp)
    val Media = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(percent = 50)
}

/** Antes era o canto cortado do visual antigo; agora e o canto arredondado dos paineis. */
@Suppress("UNUSED_PARAMETER", "unused")
class ClippedCornerShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        PitShapes.Card.createOutline(size, layoutDirection, density)
}

/** Antes era o selo inclinado; agora e uma pilula. */
object SkewShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        PitShapes.Pill.createOutline(size, layoutDirection, density)
}

/** A sombra dura do visual antigo foi aposentada: nao desenha nada. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun Modifier.hardShadow(color: Color, shape: Shape, offsetX: Dp = 4.dp, offsetY: Dp = 4.dp): Modifier = this
