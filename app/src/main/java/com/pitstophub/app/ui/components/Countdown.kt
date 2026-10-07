package com.pitstophub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText
import java.time.Instant

/** Painel de vidro com dias : horas : minutos, com a barra vermelha a esquerda (hero da Home e detalhe). */
@Composable
fun CountdownPanel(target: Instant, now: Instant, modifier: Modifier = Modifier, showSeconds: Boolean = false) {
    val parts = RaceTime.countdown(target, now)
    GlassPanel(modifier) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(PitColors.Primary))
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(if (showSeconds) 10.dp else 16.dp)) {
                CountdownUnit("%02d".format(parts.days), stringResource(R.string.unit_days))
                BlinkingColon()
                CountdownUnit("%02d".format(parts.hours), stringResource(R.string.unit_hours))
                BlinkingColon()
                CountdownUnit("%02d".format(parts.minutes), stringResource(R.string.unit_minutes))
                if (showSeconds) {
                    BlinkingColon()
                    CountdownUnit("%02d".format(parts.seconds), stringResource(R.string.unit_seconds))
                }
            }
        }
    }
}

@Composable
private fun CountdownUnit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = PitText.DataValue, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = PitText.DataLabelSm.copy(fontSize = 11.sp), color = PitColors.Tertiary)
    }
}
