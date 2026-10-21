package com.pitstophub.app.data

import android.content.Context
import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.CircuitsFile
import com.pitstophub.app.data.model.DriverExtrasFile
import com.pitstophub.app.data.model.DriverTitle
import com.pitstophub.app.data.model.F1Champion
import com.pitstophub.app.data.model.LocalizedText
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RallyDates
import com.pitstophub.app.data.model.RallyInfo
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.domain.RallyRules
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString

/**
 * Carrega os JSON editoriais do PWA (assets) e responde as consultas por piloto, campeao, circuito e rali. As
 * fichas de rali (com os mapas) so sao lidas quando uma etapa do WRC abre.
 */
class WebData(private val context: Context?, private val loader: (String) -> String? = { name ->
    context?.assets?.open(name)?.bufferedReader()?.use { it.readText() }
}) {

    private val extras: DriverExtrasFile by lazy {
        loader(DRIVER_EXTRAS)?.let { runCatching { AppJson.decodeFromString<DriverExtrasFile>(it) }.getOrNull() } ?: DriverExtrasFile()
    }

    val champions: List<F1Champion> by lazy {
        loader(CHAMPIONS)?.let { runCatching { AppJson.decodeFromString(ListSerializer(F1Champion.serializer()), it) }.getOrNull() }.orEmpty()
    }

    private val routing: CircuitRouting by lazy {
        val file = loader(CIRCUITS)?.let { runCatching { AppJson.decodeFromString<CircuitsFile>(it) }.getOrNull() }
        CircuitRouting(file ?: CircuitsFile())
    }

    suspend fun warmUp() = withContext(Dispatchers.IO) { extras; champions; routing }

    fun bio(categoryId: String, driverId: String): LocalizedText? = extras.bios["$categoryId:$driverId"]

    fun titles(categoryId: String, driverId: String): List<DriverTitle> = extras.titles["$categoryId:$driverId"].orEmpty()

    fun champion(driverId: String): F1Champion? = champions.firstOrNull { it.id == driverId }

    /**
     * Ficha do circuito de uma etapa na categoria (null quando o site tambem nao tem): o tracado certo do lugar
     * (oval x misto) e os dados do GP so na F1. O WRC nao tem ficha de circuito.
     */
    fun circuitFor(categoryId: String, race: Race): CircuitInfo? = routing.infoFor(race, categoryId)

    /** Ficha sem categoria (todos os dados da ficha de GP), como `getRaceCircuitInfo(race)` do site. */
    fun circuit(race: Race): CircuitInfo? = routing.infoFor(race, null)

    // --- fichas de rali (WRC) ---------------------------------------------------------------------------

    private val rallyDates: List<RallyDates> by lazy {
        loader(RALLY_INDEX)?.let { runCatching { AppJson.decodeFromString(ListSerializer(RallyDates.serializer()), it) }.getOrNull() }.orEmpty()
    }
    private val rallies = HashMap<String, RallyInfo?>()

    /** Id da ficha do rali da etapa (indice leve de datas; o mapa so e lido em [rally]). */
    fun rallyIdFor(race: Race): String? = RallyRules.rallyIdFor(rallyDates, race)

    /** Ficha completa do rali (itinerario + mapa), lida do asset so quando uma etapa do WRC abre. */
    suspend fun rally(id: String): RallyInfo? = withContext(Dispatchers.IO) {
        synchronized(rallies) { if (rallies.containsKey(id)) return@withContext rallies[id] }
        val info = loader("$RALLY_DIR/$id.json")?.let { runCatching { AppJson.decodeFromString<RallyInfo>(it) }.getOrNull() }
        synchronized(rallies) { rallies[id] = info }
        info
    }

    companion object {
        const val DRIVER_EXTRAS = "driver_extras.json"
        const val CHAMPIONS = "f1_champions.json"
        const val CIRCUITS = "circuits.json"
        const val RALLY_DIR = "rally"
        const val RALLY_INDEX = "$RALLY_DIR/index.json"

        /** Minusculas e sem acento, mantendo a pontuacao ("gra-bretanha"), como o `raceHaystack` do PWA. */
        fun haystack(text: String): String = CircuitRouting.normalize(text)
    }
}

/** Anos de titulo agrupados em faixas ("2009–2011, 2015"), igual a `formatDriverTitleYears` do PWA. */
fun formatTitleYears(years: List<Int>): String {
    if (years.isEmpty()) return ""
    val sorted = years.sorted()
    val parts = mutableListOf<String>()
    var start = sorted.first()
    var previous = start
    for (i in 1..sorted.size) {
        val next = sorted.getOrNull(i)
        if (next != null && next == previous + 1) {
            previous = next
            continue
        }
        parts += if (start == previous) "$start" else "$start–$previous"
        if (next != null) {
            start = next
            previous = next
        }
    }
    return parts.joinToString(", ")
}

/** Quantidade de titulos: um por ano, ou um por entrada quando a fonte nao lista anos. */
fun countTitles(titles: List<DriverTitle>): Int = titles.sumOf { it.years.size.coerceAtLeast(1) }
