package com.pitstophub.app.ui.tributes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.ui.theme.PitText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Homenagens escondidas (easter eggs), as mesmas do site:
 *  - 5 toques na foto do Hamilton           -> "Para Thata"
 *  - segurar a foto do Hamilton por 5 s     -> "Bug Hunters — Para Nay"
 *  - 5 toques na marca PITSTOPHUB (topo)    -> "Bug Hunters — Para Lucas"
 *  - 5 toques no brasao da AF Corse (WEC)   -> "Bug Hunters — Para Brunao"
 * Cada uma pede 2 toques para fechar (absorve o "clique fantasma" de quem solta o dedo depois de segurar).
 */
enum class Tribute(val color: Color, val heart: Boolean, val iconInk: Color, val label: String, val text: String) {
    THATA(Color(0xFF00D2BE), true, Color.Black, "Para Thata", "Obrigado pelas lições de vida e pela ajuda no app, sou eternamente grato."),
    NAY(Color(0xFFDC0000), true, Color.White, "Bug Hunters — Para Nay", "Obrigado pela ajuda na facul e no app, você é incrível."),
    LUCAS(Color(0xFF4ADE80), false, Color.Black, "Bug Hunters — Para Lucas", "Obrigado pela ajuda em tudo, não só no app."),
    BRUNAO(Color(0xFF4ADE80), false, Color.Black, "Bug Hunters — Para Brunão", "Vlw por me ajudar a voltar pros eixos meu querido, sem você eu não estaria aqui, tmj irmão."),
}

class TributesState(private val scope: CoroutineScope, private val clock: () -> Long = System::currentTimeMillis) {
    var open by mutableStateOf<Tribute?>(null)
        private set

    private var dismissTaps = 0
    private val taps = HashMap<Tribute, Pair<Int, Long>>()
    private var holdJob: Job? = null

    fun onHamiltonTap() = tap(Tribute.THATA)
    fun onLogoTap() = tap(Tribute.LUCAS)
    fun onFerrariWecTap() = tap(Tribute.BRUNAO)

    /** Comeca a contar o toque longo; [onHoldEnd] cancela. */
    fun onHoldStart() {
        holdJob?.cancel()
        holdJob = scope.launch {
            delay(HOLD_MS)
            show(Tribute.NAY)
        }
    }

    fun onHoldEnd() {
        holdJob?.cancel()
        holdJob = null
    }

    fun dismissTap() {
        dismissTaps += 1
        if (dismissTaps >= 2) {
            dismissTaps = 0
            open = null
        }
    }

    private fun tap(tribute: Tribute) {
        val now = clock()
        val (count, last) = taps[tribute] ?: (0 to 0L)
        val next = if (now - last > WINDOW_MS) 1 else count + 1
        if (next >= REQUIRED_TAPS) {
            taps.remove(tribute)
            show(tribute)
        } else {
            taps[tribute] = next to now
        }
    }

    private fun show(tribute: Tribute) {
        dismissTaps = 0
        open = tribute
    }

    private companion object {
        const val REQUIRED_TAPS = 5
        const val WINDOW_MS = 2_500L
        const val HOLD_MS = 5_000L
    }
}

val LocalTributes = compositionLocalOf<TributesState?> { null }

@Composable
fun rememberTributesState(scope: CoroutineScope): TributesState = remember { TributesState(scope) }

@Composable
fun TributeOverlay(state: TributesState) {
    AnimatedVisibility(visible = state.open != null, enter = fadeIn(), exit = fadeOut()) {
        val tribute = state.open ?: return@AnimatedVisibility
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { state.dismissTap() }
                .padding(28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(tribute.color), contentAlignment = Alignment.Center) {
                    Icon(if (tribute.heart) Icons.Filled.Favorite else Icons.Filled.BugReport, null, tint = tribute.iconInk, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(tribute.label.uppercase(), style = PitText.DataLabel.copy(letterSpacing = 0.3.sp), color = tribute.color, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(tribute.text, style = PitText.HeadlineMd.copy(fontSize = 26.sp, lineHeight = 34.sp), color = Color.White, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Text("(toque 2x pra fechar)", style = PitText.DataLabelXs, color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}
