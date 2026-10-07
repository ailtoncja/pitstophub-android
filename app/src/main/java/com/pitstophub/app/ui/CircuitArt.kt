package com.pitstophub.app.ui

import androidx.annotation.DrawableRes
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Race

/** Mapas de circuito da F1 (copiados do PWA, reduzidos para 720 px). Outras categorias nao tem arte. */
object CircuitArt {

    private val byRaceId = mapOf(
        "australia" to R.drawable.circuit_albertpark,
        "china" to R.drawable.circuit_china,
        "japan" to R.drawable.circuit_japan,
        "bahrain" to R.drawable.circuit_bahrain,
        "miami" to R.drawable.circuit_miami,
        "canada" to R.drawable.circuit_canada,
        "monaco" to R.drawable.circuit_monaco,
        "spain" to R.drawable.circuit_spain,
        "austria" to R.drawable.circuit_austria,
        "britain" to R.drawable.circuit_silverstone,
        "belgium" to R.drawable.circuit_spa,
        "hungary" to R.drawable.circuit_hungary,
        "netherlands" to R.drawable.circuit_netherlands,
        "italy" to R.drawable.circuit_italy,
        "madrid" to R.drawable.circuit_madrid,
        "azerbaijan" to R.drawable.circuit_azerbaijan,
        "singapore" to R.drawable.circuit_singapore,
        "usa" to R.drawable.circuit_usa,
        "mexico" to R.drawable.circuit_mexico,
        "brazil" to R.drawable.circuit_interlagos,
        "vegas" to R.drawable.circuit_vegas,
        "qatar" to R.drawable.circuit_qatar,
        "abudhabi" to R.drawable.circuit_abudhabi,
        "imola" to R.drawable.circuit_imola,
    )

    // Plano B quando o id vem da Jolpica (slug do nome) e nao do catalogo: casa por palavra do circuito.
    private val byKeyword = listOf(
        "albert park" to R.drawable.circuit_albertpark,
        "shanghai" to R.drawable.circuit_china,
        "suzuka" to R.drawable.circuit_japan,
        "bahrain" to R.drawable.circuit_bahrain,
        "miami" to R.drawable.circuit_miami,
        "villeneuve" to R.drawable.circuit_canada,
        "monaco" to R.drawable.circuit_monaco,
        "catalunya" to R.drawable.circuit_spain,
        "barcelona" to R.drawable.circuit_spain,
        "red bull ring" to R.drawable.circuit_austria,
        "silverstone" to R.drawable.circuit_silverstone,
        "spa-francorchamps" to R.drawable.circuit_spa,
        "hungaroring" to R.drawable.circuit_hungary,
        "zandvoort" to R.drawable.circuit_netherlands,
        "monza" to R.drawable.circuit_italy,
        "madrid" to R.drawable.circuit_madrid,
        "baku" to R.drawable.circuit_azerbaijan,
        "marina bay" to R.drawable.circuit_singapore,
        "americas" to R.drawable.circuit_usa,
        "rodriguez" to R.drawable.circuit_mexico,
        "interlagos" to R.drawable.circuit_interlagos,
        "las vegas" to R.drawable.circuit_vegas,
        "lusail" to R.drawable.circuit_qatar,
        "yas marina" to R.drawable.circuit_abudhabi,
        "imola" to R.drawable.circuit_imola,
    )

    private val byTrackImage = mapOf(
        "abudhabi" to R.drawable.circuit_abudhabi,
        "albertpark" to R.drawable.circuit_albertpark,
        "austria" to R.drawable.circuit_austria,
        "azerbaijan" to R.drawable.circuit_azerbaijan,
        "bahrain" to R.drawable.circuit_bahrain,
        "canada" to R.drawable.circuit_canada,
        "china" to R.drawable.circuit_china,
        "hungary" to R.drawable.circuit_hungary,
        "imola" to R.drawable.circuit_imola,
        "interlagos" to R.drawable.circuit_interlagos,
        "italy" to R.drawable.circuit_italy,
        "japan" to R.drawable.circuit_japan,
        "madrid" to R.drawable.circuit_madrid,
        "mexico" to R.drawable.circuit_mexico,
        "miami" to R.drawable.circuit_miami,
        "monaco" to R.drawable.circuit_monaco,
        "netherlands" to R.drawable.circuit_netherlands,
        "qatar" to R.drawable.circuit_qatar,
        "silverstone" to R.drawable.circuit_silverstone,
        "singapore" to R.drawable.circuit_singapore,
        "spa" to R.drawable.circuit_spa,
        "spain" to R.drawable.circuit_spain,
        "usa" to R.drawable.circuit_usa,
        "vegas" to R.drawable.circuit_vegas,
    )

    /** Mapa do circuito a partir do caminho da ficha ("/circuits/interlagos.png"). */
    @DrawableRes
    fun forTrackImage(trackImage: String?): Int? =
        trackImage?.substringAfterLast('/')?.substringBeforeLast('.')?.let(byTrackImage::get)

    @DrawableRes
    fun forRace(categoryId: String, race: Race): Int? {
        if (categoryId != "f1") return null
        byRaceId[race.id]?.let { return it }
        val haystack = java.text.Normalizer.normalize("${race.circuit} ${race.location} ${race.enLocation.orEmpty()}", java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "").lowercase()
        return byKeyword.firstOrNull { (keyword, _) -> keyword in haystack }?.second
    }
}
