package com.pitstophub.app.data

import android.content.Context
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CircuitEntry
import com.pitstophub.app.data.model.DriverExtrasFile
import com.pitstophub.app.data.model.DriverTitle
import com.pitstophub.app.data.model.F1Champion
import com.pitstophub.app.data.model.LocalizedText
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.remote.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import java.text.Normalizer

/** Carrega os JSON editoriais do PWA (assets) uma vez e responde as consultas por piloto, campeao e circuito. */
class WebData(private val context: Context?, private val loader: (String) -> String? = { name ->
    context?.assets?.open(name)?.bufferedReader()?.use { it.readText() }
}) {

    private val extras: DriverExtrasFile by lazy {
        loader(DRIVER_EXTRAS)?.let { runCatching { AppJson.decodeFromString<DriverExtrasFile>(it) }.getOrNull() } ?: DriverExtrasFile()
    }

    val champions: List<F1Champion> by lazy {
        loader(CHAMPIONS)?.let { runCatching { AppJson.decodeFromString(ListSerializer(F1Champion.serializer()), it) }.getOrNull() }.orEmpty()
    }

    private val circuits: List<CircuitEntry> by lazy {
        loader(CIRCUITS)?.let { runCatching { AppJson.decodeFromString(ListSerializer(CircuitEntry.serializer()), it) }.getOrNull() }.orEmpty()
    }

    suspend fun warmUp() = withContext(Dispatchers.IO) { extras; champions; circuits }

    fun bio(categoryId: String, driverId: String): LocalizedText? = extras.bios["$categoryId:$driverId"]

    fun titles(categoryId: String, driverId: String): List<DriverTitle> = extras.titles["$categoryId:$driverId"].orEmpty()

    fun champion(driverId: String): F1Champion? = champions.firstOrNull { it.id == driverId }

    /** Ficha tecnica do circuito de uma corrida (null quando o PWA tambem nao tem pagina para ela). */
    fun circuit(race: Race): CircuitEntry? {
        val haystack = haystack("${race.circuit} ${race.location} ${race.enLocation.orEmpty()} ${race.name} ${race.enName.orEmpty()}")
        return circuits.firstOrNull { entry ->
            entry.keywords.any { it in haystack } && entry.exclude.none { it in haystack }
        }
    }

    /** O PWA so mostra ficha para as series que de fato correm nessas pistas. */
    fun circuitFor(category: Category, race: Race): CircuitEntry? =
        if (category.id in CIRCUIT_CATEGORIES) circuit(race) else null

    companion object {
        const val DRIVER_EXTRAS = "driver_extras.json"
        const val CHAMPIONS = "f1_champions.json"
        const val CIRCUITS = "circuits.json"

        /** F2/F3/F1 Academy dividem fins de semana com a F1; WEC, DTM e NASCAR passam por algumas pistas. */
        val CIRCUIT_CATEGORIES = setOf("f1", "f2", "f3", "f1-academy", "wec", "dtm", "nascar")

        /** Minusculas e sem acento, mantendo a pontuacao ("gra-bretanha"), como o `raceHaystack` do PWA. */
        fun haystack(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
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
