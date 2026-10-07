package com.pitstophub.app.ui.champions

import androidx.annotation.DrawableRes
import com.pitstophub.app.R
import com.pitstophub.app.data.model.F1Champion

/** Cartas dos campeoes (WebP reduzido a 720 px, convertido de public/champions do PWA). */
object ChampionArt {
    private val byFile = mapOf(
        "fittipaldi" to R.drawable.champion_fittipaldi,
        "massa" to R.drawable.champion_massa,
        "piquet" to R.drawable.champion_piquet,
        "rubens" to R.drawable.champion_rubens,
        "senna" to R.drawable.champion_senna,
    )

    /** O JSON guarda o caminho do PWA ("/champions/senna.png"); o nome do arquivo escolhe a carta. */
    @DrawableRes
    fun of(champion: F1Champion): Int? {
        val file = champion.image?.substringAfterLast('/')?.substringBeforeLast('.') ?: champion.id
        return byFile[file] ?: byFile[champion.id]
    }
}
