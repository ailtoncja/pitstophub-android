package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Team
import com.pitstophub.app.data.model.TeamBio

/** Texto de apoio para equipes que vem so da API ao vivo (GTWC e Endurance Brasil), como no PWA. */
object TeamBios {

    private val liveSeries = mapOf(
        "gtwc-europe" to "GT World Challenge Europe",
        "gtwc-america" to "GT World Challenge America",
        "gtwc-asia" to "GT World Challenge Asia",
        LiveSeriesMapper.ENDURANCE_BRASIL to "Endurance Brasil",
    )

    fun fallback(categoryId: String, team: Team): TeamBio? {
        val series = liveSeries[categoryId] ?: return null
        val classPt = team.raceClass?.let { " na classe $it" }.orEmpty()
        val classEn = team.raceClass?.let { " in the $it class" }.orEmpty()

        if (categoryId == LiveSeriesMapper.ENDURANCE_BRASIL) {
            val principalPt = team.principal?.let { ", dirigida por $it" }.orEmpty()
            val principalEn = team.principal?.let { ", led by $it" }.orEmpty()
            return TeamBio(
                pt = "A ${team.name}$principalPt compete no Endurance Brasil$classPt. O campeonato nacional reúne protótipos e GTs em provas de 3 e 4 horas nos principais autódromos do país.",
                en = "${team.name}$principalEn contests Endurance Brasil$classEn. The national championship mixes prototypes and GTs in 3- and 4-hour races at Brazil’s main circuits.",
            )
        }

        val carPt = team.car?.let { " com o $it" }.orEmpty()
        val carEn = team.car?.let { " with the $it" }.orEmpty()
        return TeamBio(
            pt = "A ${team.name} compete no $series$classPt$carPt. O grid da SRO reúne fabricantes de GT3 de todo o mundo, com Balance of Performance entre os modelos.",
            en = "${team.name} contests $series$classEn$carEn. The SRO grid brings together GT3 manufacturers from around the world, with Balance of Performance between the models.",
        )
    }
}
