package com.pitstophub.app.ui.teams

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.pitstophub.app.ui.theme.PitColors

/** Cor da equipe ("#00D2BE"); cor invalida cai no vermelho da marca. */
fun parseTeamColor(hex: String?): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(PitColors.PrimaryContainer)

/** Segunda cor da faixa de "libre": a mesma cor escurecida (o catalogo guarda uma cor so por equipe). */
fun teamColorEnd(color: Color): Color = lerp(color, Color.Black, 0.65f)
