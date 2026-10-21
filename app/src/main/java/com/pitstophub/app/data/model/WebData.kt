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

/**
 * Ficha do circuito de uma etapa (`CircuitInfo` de src/circuit-info.ts). Tudo e opcional: so existe o que tem fonte.
 * Voltas, distancia, recorde, primeiro GP, Straight Mode, frenagens e pneus sao dados do GP de F1 e so aparecem na F1.
 */
@Serializable
data class CircuitInfo(
    val trackImage: String? = null,
    /** Desenho feito a partir do OpenStreetMap: exige o credito "© OpenStreetMap" onde aparece (ODbL). */
    val osm: Boolean = false,
    val lengthKm: Double? = null,
    val raceDistanceKm: Double? = null,
    val laps: Int? = null,
    val corners: Int? = null,
    /** "clockwise" ou "counterclockwise". */
    val direction: String? = null,
    val lapRecord: LapRecord? = null,
    val firstGrandPrix: Int? = null,
    /** Zonas de Straight Mode (aero ativa) no mapa oficial da F1 de 2026; o DRS deixou de existir. */
    val straightModeZones: Int? = null,
    val brakingZones: List<BrakingZone> = emptyList(),
    val tyreStrategyNote: LocalizedText? = null,
) {
    val clockwise: Boolean? get() = direction?.let { it == "clockwise" }
}

// --- regras de escolha do circuito (circuits.json, exportado de src/circuit-info.ts) ---------------------

/** Termos que identificam um lugar no texto da etapa (sem acento, minusculos): texto puro ou regex. */
@Serializable
data class TermOption(val terms: List<String> = emptyList(), val patterns: List<String> = emptyList(), val layout: String)

@Serializable
data class LayoutEvent(val categoryId: String, val date: String, val layout: String)

/** Um lugar com desenho do OpenStreetMap; pode ter mais de um tracado (oval x misto). */
@Serializable
data class Venue(
    val terms: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
    val not: List<String> = emptyList(),
    /** So nestas categorias (null = todas). */
    val categories: List<String>? = null,
    val layout: String,
    val byCategory: Map<String, String> = emptyMap(),
    val byText: List<TermOption> = emptyList(),
    val byEvent: List<LayoutEvent> = emptyList(),
)

@Serializable
data class DrawnCircuit(
    val name: String,
    val layout: String,
    val trackImage: String,
    val lengthKm: Double? = null,
    val corners: Int? = null,
    val direction: String? = null,
)

/** Ficha de GP (pistas da F1 2026, Sakhir, Imola, Jeddah) e as palavras que a escolhem. */
@Serializable
data class GpCircuit(
    val id: String,
    val terms: List<String> = emptyList(),
    val patterns: List<String> = emptyList(),
    val not: List<String> = emptyList(),
    /** Fichas que, se casarem, descartam esta (Sepang antes de Sakhir). */
    val unless: List<String> = emptyList(),
    /** O pais da etapa (Jolpica) entra no texto. */
    val withCountry: Boolean = false,
    val info: CircuitInfo,
)

@Serializable
data class CircuitsFile(
    val venues: List<Venue> = emptyList(),
    val drawings: Map<String, DrawnCircuit> = emptyMap(),
    val gp: List<GpCircuit> = emptyList(),
)
