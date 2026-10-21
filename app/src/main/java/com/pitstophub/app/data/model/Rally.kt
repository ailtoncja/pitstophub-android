package com.pitstophub.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Ficha de rali do WRC (src/rally/types.ts do site), exportada para assets/rally/<id>.json. Cada numero vem de uma
// fonte citada em `sources`; o que nao foi publicado fica ausente e a tela diz isso em vez de inventar.

@Serializable
enum class RallySurface {
    @SerialName("gravel") GRAVEL,
    @SerialName("tarmac") TARMAC,
    @SerialName("snow") SNOW,
    @SerialName("mixed") MIXED,
}

@Serializable
data class RallyStage(
    /** Numero da especial (SS). */
    val number: Int,
    val name: String,
    val km: Double,
    /** Horario do 1o carro com o fuso do rali (ISO 8601); ausente quando a fonte nao publica. */
    val startsAt: String? = null,
    /** Chave do tracado no mapa (especiais repetidas compartilham a mesma estrada). */
    val routeId: String? = null,
    val powerStage: Boolean = false,
)

@Serializable
data class RallyShakedown(val name: String, val km: Double? = null, val startsAt: String? = null)

@Serializable
data class RallyDay(
    /** Data local do rali (AAAA-MM-DD). */
    val date: String,
    val stages: List<RallyStage> = emptyList(),
    /** Treino cronometrado que nao conta para o resultado. */
    val shakedown: RallyShakedown? = null,
)

@Serializable
data class RallyRoute(
    val id: String,
    /** Rotulo no mapa, ex.: "SS2/5". */
    val label: String,
    /** Tracado (path SVG em coordenadas do viewBox); ausente quando so ha largada e chegada. */
    val path: String? = null,
    val start: List<Double>,
    val finish: List<Double>,
    val labelAt: List<Double>,
    /** Ponto da especial de onde sai a linha-guia ate o rotulo. */
    val leaderFrom: List<Double>,
    /** "left", "right" ou "center". */
    val anchor: String = "center",
)

@Serializable
data class MapPoint(val x: Double, val y: Double)

@Serializable
data class MapTown(val name: String, val x: Double, val y: Double)

@Serializable
data class ScaleBar(val km: Double, val px: Double)

@Serializable
data class RallyMap(
    val width: Double,
    val height: Double,
    /** Terra (poligonos de costa do OSM, path SVG evenodd); vazio = so fundo de terra. */
    val land: String = "",
    /** "route" = tracado oficial (especiais sem `path` ficam so com largada/chegada); "points" = so os pontos. */
    val geometry: String,
    val routes: List<RallyRoute> = emptyList(),
    val servicePark: MapPoint? = null,
    val towns: List<MapTown> = emptyList(),
    val scaleBar: ScaleBar,
    /** Lado do icone do parque de assistencia em que fica o texto ("below" ou "above"). */
    val serviceLabel: String = "below",
    val usesOsm: Boolean = true,
) {
    val drawsRoutes: Boolean get() = geometry == "route"
}

@Serializable
data class RallySource(val label: String, val url: String, val secondary: Boolean = false)

@Serializable
data class RallyDateRange(val start: String, val end: String)

@Serializable
data class RallyInfo(
    val id: String,
    val dates: RallyDateRange,
    val surface: RallySurface,
    /** Totais declarados pela fonte do itinerario (o teste confere com a soma das especiais). */
    val official: RallyTotals,
    val itineraryVersion: String? = null,
    /** O itinerario veio de fonte secundaria (sem documento oficial acessivel). */
    val itinerarySecondary: Boolean = false,
    val servicePark: String? = null,
    val days: List<RallyDay> = emptyList(),
    /** Sem localizacao confiavel das especiais: a ficha aparece sem mapa. */
    val map: RallyMap? = null,
    val mapNote: LocalizedText? = null,
    val sources: List<RallySource> = emptyList(),
)

@Serializable
data class RallyTotals(val stages: Int, val km: Double)

/** Indice leve (assets/rally/index.json): decide se a etapa tem ficha sem ler os mapas. */
@Serializable
data class RallyDates(val id: String, val start: String, val end: String)
