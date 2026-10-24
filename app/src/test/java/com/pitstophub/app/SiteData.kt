package com.pitstophub.app

import com.pitstophub.app.data.WebData
import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.remote.JCircuit
import com.pitstophub.app.data.remote.JLocation
import com.pitstophub.app.data.remote.JRace
import com.pitstophub.app.data.remote.SyncedRaceRow
import kotlinx.serialization.decodeFromString
import java.io.File

/**
 * Os dados exportados do site (app/src/main/assets), lidos como o app le. Os testes que espelham os do site
 * (os arquivos .test.ts em src/) usam as mesmas etapas do catalogo e as mesmas amostras das fontes ao vivo.
 */
object SiteData {
    val assets = File("src/main/assets")

    val catalog: CatalogFile by lazy { AppJson.decodeFromString<CatalogFile>(File(assets, "motorsport_data.json").readText()) }

    val webData: WebData by lazy { WebData(null) { name -> File(assets, name).takeIf { it.exists() }?.readText() } }

    fun category(id: String): Category = catalog.categories.firstOrNull { it.id == id } ?: error("categoria $id nao encontrada")

    fun staticRace(categoryId: String, raceId: String): Race =
        category(categoryId).calendar.firstOrNull { it.id == raceId } ?: error("etapa $categoryId/$raceId nao encontrada")

    /** Etapa avulsa, como o `race(over)` dos testes do site. */
    fun race(
        name: String = "",
        circuit: String = "",
        location: String = "",
        date: String = "2026-06-01",
        enName: String? = null,
        enLocation: String? = null,
        country: String? = null,
        id: String = "r",
        status: RaceStatus = RaceStatus.UPCOMING,
    ) = Race(id = id, name = name, enName = enName, location = location, enLocation = enLocation, date = date, circuit = circuit, status = status, country = country)

    /** Linha de synced_races como a rotina diaria grava (id "rNN-local", nome = local). */
    fun synced(id: String, name: String, date: String, status: String = "completed", circuit: String = "") =
        SyncedRaceRow(raceId = id, name = name, location = name, circuit = circuit, date = date, status = status)

    fun jolpica(round: String, raceName: String, circuitId: String, circuitName: String, locality: String, country: String, date: String, time: String = "13:00:00Z") =
        JRace(round = round, raceName = raceName, circuit = JCircuit(circuitId, circuitName, JLocation(locality, country, "0", "0")), date = date, time = time)

    /** Arquivo do mapa no app para o caminho do site ("/circuits/sepang.png" -> assets/circuits/sepang.webp). */
    fun mapAsset(trackImage: String): File = File(assets, "circuits/${trackImage.substringAfterLast('/').substringBeforeLast('.')}.webp")
}
