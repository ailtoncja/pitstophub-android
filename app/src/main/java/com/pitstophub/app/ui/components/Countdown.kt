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

/** Contagem em uma linha: "7 d  15 h  37 min", algarismos tabulares (mesmo formato do site). */
@Composable
fun CountdownPanel(target: Instant, now: Instant, modifier: Modifier = Modifier, showSeconds: Boolean = false) {
    val parts = RaceTime.countdown(target, now)
    Text(
        buildString {
            if (parts.days > 0) append("${parts.days} d  ")
            append("%02d h  %02d min".format(parts.hours, parts.minutes))
            if (showSeconds) append("  %02d s".format(parts.seconds))
        },
        modifier,
        style = PitText.DataValue.copy(fontSize = 20.sp),
        color = Color.White,
    )
}

@Suppress("unused")
@Composable
private fun CountdownUnit(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = PitText.DataValue, color = Color.White)
        Spacer(Modifier.height(4.dp))
        Text(label, style = PitText.DataLabelSm.copy(fontSize = 11.sp), color = PitColors.Tertiary)
    }
}
