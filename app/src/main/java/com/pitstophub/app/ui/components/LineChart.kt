package com.pitstophub.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pitstophub.app.ui.theme.PitColors

/** Curva de evolucao (pontos acumulados): linha vermelha com area preenchida e marcador no ultimo ponto. */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, height: Dp = 110.dp, color: Color = PitColors.PrimaryContainer) {
    if (values.size < 2) return
    Canvas(modifier.fillMaxWidth().height(height)) {
        val padTop = 10.dp.toPx()
        val padBottom = 6.dp.toPx()
        val padSide = 6.dp.toPx()
        val min = values.min()
        val span = (values.max() - min).takeIf { it > 0 } ?: 1.0
        val stepX = (size.width - padSide * 2) / (values.size - 1)
        fun point(i: Int) = Offset(
            padSide + stepX * i,
            padTop + ((1 - (values[i] - min) / span) * (size.height - padTop - padBottom)).toFloat(),
        )

        // guias pontilhadas
        listOf(0.25f, 0.62f).forEach { fraction ->
            val y = size.height * fraction
            drawLine(PitColors.OutlineVariant.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }

        val line = Path().apply {
            moveTo(point(0).x, point(0).y)
            for (i in 1 until values.size) lineTo(point(i).x, point(i).y)
        }
        val area = Path().apply {
            addPath(line)
            lineTo(point(values.lastIndex).x, size.height)
            lineTo(point(0).x, size.height)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), Color.Transparent)))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val last = point(values.lastIndex)
        drawCircle(color, radius = 7.dp.toPx(), center = last, style = Stroke(width = 2.dp.toPx()))
        drawCircle(Color.White, radius = 4.dp.toPx(), center = last)
    }
}
