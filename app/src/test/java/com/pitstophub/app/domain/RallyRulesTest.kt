package com.pitstophub.app.domain

import com.pitstophub.app.SiteData
import com.pitstophub.app.SiteData.category
import com.pitstophub.app.SiteData.race
import com.pitstophub.app.data.WebData
import com.pitstophub.app.data.model.RallyDates
import com.pitstophub.app.data.model.RallyInfo
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.remote.AppJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.Locale

// Espelha src/rally-info.test.ts do site (fichas de rali do WRC 2026): totais, numeracao, dias, fontes e cobertura
// do mapa, mais o horario no fuso do aparelho com o dia da semana quando a conversao muda o dia.
class RallyRulesTest {

    private val data = SiteData.webData
    private val dir = File(SiteData.assets, WebData.RALLY_DIR)
    private val rallies: List<RallyInfo> by lazy {
        index.map { AppJson.decodeFromString<RallyInfo>(File(dir, "${it.id}.json").readText()) }
    }
    private val index: List<RallyDates> by lazy { AppJson.decodeFromString(ListSerializer(RallyDates.serializer()), File(dir, "index.json").readText()) }
    private val wrcCalendar = category("wrc").calendar

    @Test
    fun `toda etapa do WRC (menos a cancelada) tem ficha, e a certa`() = runTest {
        for (round in wrcCalendar) {
            if (round.status == RaceStatus.CANCELLED) continue
            val id = data.rallyIdFor(round)
            assertEquals(round.id, round.id, id)
            assertEquals(round.id, data.rally(id!!)?.id)
        }
        assertEquals(wrcCalendar.count { it.status != RaceStatus.CANCELLED }, rallies.size)
        assertEquals(13, rallies.size)
    }

    @Test
    fun `o indice leve de datas bate com as fichas`() {
        assertEquals(index, rallies.map { RallyDates(it.id, it.dates.start, it.dates.end) })
    }

    @Test
    fun `acha a ficha pela data mesmo com o id e o nome da fonte ao vivo`() {
        assertEquals("sardegna", data.rallyIdFor(race(id = "r13-alghero", name = "Rally Italy", date = "2026-10-02")))
        assertNull(data.rallyIdFor(race(date = "2026-11-14")))
        assertNull(data.rallyIdFor(race(date = "2025-10-04")))
    }

    @Test
    fun `soma das especiais = totais da fonte do itinerario`() {
        for (info in rallies) {
            val key = RallyRules.keyNumbers(info)
            assertEquals(info.id, info.official.stages, key.count)
            assertEquals(info.id, info.official.km, key.km, 0.005)
        }
    }

    @Test
    fun `especiais numeradas em sequencia, no dia certo, com no maximo uma Power Stage`() {
        for (info in rallies) {
            val stages = RallyRules.stages(info)
            assertEquals(info.id, (1..stages.size).toList(), stages.map { it.number })
            assertTrue(info.id, stages.count { it.powerStage } <= 1)
            for (day in info.days) {
                assertTrue("${info.id} ${day.date}", day.date >= info.dates.start && day.date <= info.dates.end)
                for (stage in day.stages) stage.startsAt?.let { assertEquals("${info.id} SS${stage.number}", day.date, it.take(10)) }
                assertTrue(RallyRules.dayKm(day) >= 0)
            }
        }
    }

    @Test
    fun `tem fontes com endereco valido`() {
        for (info in rallies) {
            assertTrue(info.id, info.sources.isNotEmpty())
            for (source in info.sources) {
                assertTrue(info.id, source.label.isNotBlank())
                assertTrue(source.url, source.url.startsWith("https://"))
            }
        }
    }

    @Test
    fun `mapa (quando existe) cobre todas as especiais e fica dentro do quadro`() {
        var withMap = 0
        var withRoutes = 0
        for (info in rallies) {
            val map = info.map ?: continue
            withMap++
            val roads = map.routes.map { it.id }.toSet()
            for (stage in RallyRules.stages(info)) {
                val route = stage.routeId
                if (route == null) assertTrue("${info.id} SS${stage.number} sem lugar no mapa", info.mapNote?.pt.orEmpty().contains("SS${stage.number} "))
                else assertTrue("${info.id} SS${stage.number}", route in roads)
            }
            if (map.drawsRoutes) {
                withRoutes++
                assertTrue(info.id, map.routes.any { it.path != null })
            } else {
                assertTrue(info.id, map.routes.all { it.path == null })
            }
            for (route in map.routes) {
                for (point in listOf(route.start, route.finish, route.labelAt)) {
                    assertTrue("${info.id} ${route.id}", point[0] in 0.0..map.width && point[1] in 0.0..map.height)
                }
            }
        }
        // Mapas: tracado em Sardenha, Monte-Carlo e Paraguai; so pontos em Croacia, Canarias, Portugal, Grecia e Safari.
        assertEquals(8, withMap)
        assertEquals(3, withRoutes)
    }

    @Test
    fun `numeros-chave - especial mais longa (todas as passagens) e Power Stage`() {
        val montecarlo = rallies.first { it.id == "montecarlo" }
        val key = RallyRules.keyNumbers(montecarlo)
        assertEquals(17, key.count)
        assertEquals(339.15, key.km, 0.001)
        assertEquals(29.93, key.longestKm, 0.0)
        assertEquals(listOf(10, 12), key.longest.map { it.number })
        assertEquals(17, key.powerStage?.number)
        assertEquals("La Bréole / Bellaffaire", RallyRules.stageBaseName(key.longest.first().name))
        assertNull(rallies.first { it.id == "sweden" }.map)
    }

    @Test
    fun `horario no fuso do aparelho mostra o dia da semana quando muda o dia`() {
        val saoPaulo = ZoneId.of("America/Sao_Paulo")
        // Japao (UTC+9) visto do Brasil (UTC-3): 08:05 de sexta la e 20:05 de quinta aqui.
        val shifted = RallyRules.stageClock("2026-05-29T08:05:00+09:00", "2026-05-29", Locale.forLanguageTag("pt-BR"), saoPaulo)!!
        assertEquals("20:05", shifted.time)
        assertEquals("Qui", shifted.day)
        assertEquals("Thu", RallyRules.stageClock("2026-05-29T08:05:00+09:00", "2026-05-29", Locale.US, saoPaulo)!!.day)
        // Mesmo dia: so o horario.
        val same = RallyRules.stageClock("2026-01-22T16:05:00+01:00", "2026-01-22", Locale.forLanguageTag("pt-BR"), saoPaulo)!!
        assertEquals("12:05", same.time)
        assertNull(same.day)
    }

    @Test
    fun `todas as fichas do WRC carregam do asset, e so quando pedidas`() = runTest {
        for (entry in index) assertNotNull(entry.id, data.rally(entry.id))
        assertNull(data.rally("nao-existe"))
    }
}
