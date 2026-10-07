package com.pitstophub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/** Biografia partida em uma frase de destaque e paragrafos curtos (o catalogo traz tudo num bloco so). */
internal data class BioText(val lead: String, val paragraphs: List<String>)

// Quebra depois de ponto final/exclamacao/interrogacao quando a proxima frase comeca com maiuscula. Exige duas letras
// minusculas (ou digitos) antes do ponto, para nao partir em abreviacoes como "Dr." ou "Jr.".
private val sentenceBreak = Regex("(?<=[a-zà-ÿ0-9)]{2}[.!?])\\s+(?=[A-ZÀ-Ý\"“])")

private const val PARAGRAPH_TARGET = 230

internal fun splitBio(text: String): BioText {
    val sentences = text.trim().split(sentenceBreak).map { it.trim() }.filter { it.isNotEmpty() }
    if (sentences.isEmpty()) return BioText("", emptyList())
    val paragraphs = mutableListOf<String>()
    var current = StringBuilder()
    for (sentence in sentences.drop(1)) {
        if (current.isNotEmpty() && current.length + sentence.length > PARAGRAPH_TARGET) {
            paragraphs += current.toString()
            current = StringBuilder()
        }
        if (current.isNotEmpty()) current.append(' ')
        current.append(sentence)
    }
    if (current.isNotEmpty()) paragraphs += current.toString()
    return BioText(sentences.first(), paragraphs)
}

/**
 * Cartao editorial para biografias: faixa vermelha na lateral, aspas grandes ao fundo, a primeira frase em destaque e o
 * resto em paragrafos curtos com bastante respiro.
 */
@Composable
fun BioCard(text: String, modifier: Modifier = Modifier) {
    val bio = remember(text) { splitBio(text) }
    if (bio.lead.isEmpty()) return
    Box(modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Soft)) {
        Text(
            "“",
            Modifier.align(Alignment.TopEnd).padding(end = 18.dp),
            style = PitText.DisplayLg.copy(fontSize = 120.sp, lineHeight = 120.sp),
            color = PitColors.PrimaryContainer.copy(alpha = 0.11f),
        )
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(PitColors.PrimaryContainer))
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    bio.lead,
                    style = PitText.BodyLg.copy(fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold),
                    color = PitColors.OnSurface,
                )
                if (bio.paragraphs.isNotEmpty()) {
                    Box(Modifier.width(40.dp).height(3.dp).background(PitColors.PrimaryContainer))
                    bio.paragraphs.forEach { paragraph ->
                        Text(paragraph, style = PitText.BodyMd.copy(lineHeight = 26.sp), color = PitColors.Tertiary)
                    }
                }
            }
        }
    }
}
