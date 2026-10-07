package com.pitstophub.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.tan

/** Raios do design: 0.125rem / 0.25rem / 0.5rem. */
object PitShapes {
    val Sharp = RoundedCornerShape(2.dp)
    val Card = RoundedCornerShape(4.dp)
    val Soft = RoundedCornerShape(8.dp)
}

/** "Clipped corner" do design: corte de 45 graus no canto superior direito. */
class ClippedCornerShape(private val cut: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val c = with(density) { cut.toPx() }.coerceAtMost(minOf(size.width, size.height))
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width - c, 0f)
                lineTo(size.width, c)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/** Retangulo inclinado a 15 graus (`skewX(-15deg)` do CSS): topo deslocado para a direita. */
object SkewShape : Shape {
    private val tan15 = tan(Math.toRadians(15.0)).toFloat()

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val offset = (size.height * tan15).coerceAtMost(size.width / 2f)
        return Outline.Generic(
            Path().apply {
                moveTo(offset, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width - offset, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}

/** Sombra "decalque" do design: copia solida do formato, sem blur, deslocada. */
fun Modifier.hardShadow(color: Color, shape: Shape, offsetX: Dp = 4.dp, offsetY: Dp = 4.dp): Modifier =
    drawBehind {
        val outline = shape.createOutline(size, layoutDirection, this)
        translate(offsetX.toPx(), offsetY.toPx()) { drawOutline(outline, color) }
    }
