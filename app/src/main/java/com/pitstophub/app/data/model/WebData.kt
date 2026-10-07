package com.pitstophub.app.data.model

import kotlinx.serialization.Serializable

// Dados editoriais exportados do PWA por tools/export-web-data.mjs (biografias, titulos, campeoes de F1 e
// fichas de circuito). Os nomes de campo seguem os tipos TypeScript de origem.

@Serializable
data class LocalizedText(val pt: String, val en: String) {
    fun text(portuguese: Boolean): String = if (portuguese) pt else en
}

@Serializable
data class DriverTitle(val years: List<Int> = emptyList(), val pt: String, val en: String) {
    fun label(portuguese: Boolean): String = if (portuguese) pt else en
}

@Serializable
data class DriverExtrasFile(
    val bios: Map<String, LocalizedText> = emptyMap(),
    val titles: Map<String, List<DriverTitle>> = emptyMap(),
)

@Serializable
data class ChampionStint(
    val name: String,
    val years: String,
    val championYears: List<Int> = emptyList(),
    val series: LocalizedText? = null,
    val teamId: String? = null,
)

@Serializable
data class F1Champion(
    val id: String,
    val name: String,
    val number: String = "",
    val nationality: String = "",
    val teamId: String = "",
    val image: String? = null,
    val championYears: List<Int> = emptyList(),
    val cardRating: Int = 0,
    val careerWins: Int = 0,
    val careerPodiums: Int = 0,
    val careerPoles: Int = 0,
    val careerStarts: Int = 0,
    val careerFastestLaps: Int = 0,
    val grayscalePhoto: Boolean = false,
    val teams: List<ChampionStint> = emptyList(),
)

@Serializable
data class LapRecord(val time: String, val driver: String, val year: Int)

@Serializable
data class BrakingZone(val turn: String, val name: String, val pt: String, val en: String) {
    fun text(portuguese: Boolean): String = if (portuguese) pt else en
}

@Serializable
data class CircuitInfo(
    val trackImage: String? = null,
    val lengthKm: Double,
    val raceDistanceKm: Double,
    val laps: Int,
    val corners: Int,
    /** "clockwise" ou "counterclockwise". */
    val direction: String,
    val lapRecord: LapRecord? = null,
    val firstGrandPrix: Int,
    val drsZones: List<LocalizedText> = emptyList(),
    val brakingZones: List<BrakingZone> = emptyList(),
    val tyreStrategyNote: LocalizedText,
) {
    val clockwise: Boolean get() = direction == "clockwise"
}

/** Um circuito do catalogo: palavras que o identificam no texto da corrida e a ficha tecnica. */
@Serializable
data class CircuitEntry(
    val id: String,
    val keywords: List<String>,
    val exclude: List<String> = emptyList(),
    val info: CircuitInfo,
)
