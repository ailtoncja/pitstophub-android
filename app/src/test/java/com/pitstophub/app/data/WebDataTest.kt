package com.pitstophub.app.data

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.DriverTitle
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.remote.WeatherClient
import com.pitstophub.app.data.remote.WeatherCondition
import com.pitstophub.app.domain.CategorySummary
import com.pitstophub.app.ui.drivers.standingOfDriver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

private val assets = File("src/main/assets")

private fun race(name: String, circuit: String = "", location: String = "", enName: String? = null, enLocation: String? = null) =
    Race(id = name, name = name, enName = enName, location = location, enLocation = enLocation, date = "2026-10-11", circuit = circuit)

class WebDataTest {

    private val data = WebData(null) { name -> File(assets, name).takeIf { it.exists() }?.readText() }

    @Test
    fun `reconhece os 24 circuitos do site pelo texto da corrida`() {
        assertEquals("singapore", data.circuit(race("GP de Singapura", circuit = "Marina Bay Street Circuit"))?.id)
        assertEquals("interlagos", data.circuit(race("GP de São Paulo", location = "São Paulo"))?.id)
        assertEquals("silverstone", data.circuit(race("GP da Grã-Bretanha"))?.id) // palavra com hifen, como no PWA
        assertEquals("monaco", data.circuit(race("Monaco", enName = "Monaco Grand Prix"))?.id)
        assertNull(data.circuit(race("Rally Italia Sardegna", location = "Alghero")))
    }

    @Test
    fun `las vegas de rua tem ficha, o oval da NASCAR nao`() {
        assertEquals("vegas", data.circuit(race("GP de Las Vegas", circuit = "Las Vegas Strip Circuit"))?.id)
        assertNull(data.circuit(race("Pennzoil 400", circuit = "Las Vegas Motor Speedway", location = "Las Vegas")))
    }

    @Test
    fun `ficha de circuito so vale para as categorias que correm nessas pistas`() {
        val singapore = race("GP de Singapura", circuit = "Marina Bay")
        assertNotNull(data.circuitFor(Category("f1", "F1", "Formula 1"), singapore))
        assertNotNull(data.circuitFor(Category("wec", "WEC", "WEC"), singapore))
        assertNull(data.circuitFor(Category("wrc", "WRC", "WRC"), singapore))
        assertNull(data.circuitFor(Category("indy", "IndyCar", "IndyCar"), singapore))
    }

    @Test
    fun `ficha traz os numeros e o texto nos dois idiomas`() {
        val info = data.circuit(race("GP de Singapura", circuit = "Marina Bay"))!!.info
        assertEquals(19, info.corners)
        assertEquals(4.927, info.lengthKm, 0.0001)
        assertFalse(info.clockwise)
        assertTrue(info.drsZones.size >= 2)
        assertTrue(info.tyreStrategyNote.pt.isNotBlank() && info.tyreStrategyNote.en.isNotBlank())
        assertEquals("/circuits/singapore.png", info.trackImage)
    }

    @Test
    fun `todo circuito exportado tem mapa e palavra-chave`() {
        val file = File(assets, WebData.CIRCUITS).readText()
        assertTrue(file.contains("trackImage"))
        // 24 fichas, cada uma apontando para um mapa diferente
        assertEquals(24, Regex("\"trackImage\"").findAll(file).count())
    }

    @Test
    fun `campeoes e biografias de pilotos vem do site`() {
        assertEquals(5, data.champions.size)
        val senna = data.champion("senna")!!
        assertEquals(listOf(1988, 1990, 1991), senna.championYears)
        assertEquals(41, senna.careerWins)
        assertNotNull(data.bio("f1", "norris"))
        assertTrue(data.titles("f1", "norris").isNotEmpty())
        assertNull(data.bio("f1", "ninguem"))
    }

    @Test
    fun `anos de titulo agrupam em faixas`() {
        assertEquals("2009–2011, 2015", formatTitleYears(listOf(2015, 2009, 2010, 2011)))
        assertEquals("2025", formatTitleYears(listOf(2025)))
        assertEquals("", formatTitleYears(emptyList()))
        assertEquals(4, countTitles(listOf(DriverTitle(listOf(1, 2, 3), "a", "a"), DriverTitle(emptyList(), "b", "b"))))
    }
}

class WeatherClientTest {

    private val json = """{"daily":{"temperature_2m_max":[29.4],"temperature_2m_min":[26.1],"precipitation_probability_max":[80],"wind_speed_10m_max":[9.2],"weather_code":[53]}}"""

    @Test
    fun `le a previsao do dia`() {
        val weather = WeatherClient.parse(json)!!
        assertEquals(29.4, weather.temperatureMax, 0.0)
        assertEquals(26.1, weather.temperatureMin, 0.0)
        assertEquals(80, weather.rainChance)
        assertEquals(WeatherCondition.DRIZZLE, weather.condition)
        assertNull(WeatherClient.parse("""{"daily":{"temperature_2m_max":[]}}"""))
    }

    @Test
    fun `codigos WMO viram condicoes simples`() {
        assertEquals(WeatherCondition.CLEAR, WeatherClient.conditionOf(0))
        assertEquals(WeatherCondition.CLOUDY, WeatherClient.conditionOf(3))
        assertEquals(WeatherCondition.FOG, WeatherClient.conditionOf(45))
        assertEquals(WeatherCondition.RAIN, WeatherClient.conditionOf(81))
        assertEquals(WeatherCondition.SNOW, WeatherClient.conditionOf(75))
        assertEquals(WeatherCondition.STORM, WeatherClient.conditionOf(96))
    }

    @Test
    fun `janela da previsao vai de ontem a 15 dias`() {
        val now = Instant.parse("2026-10-04T12:00:00Z")
        assertTrue(WeatherClient.windowOpen("2026-10-03", now))
        assertTrue(WeatherClient.windowOpen("2026-10-19", now))
        assertFalse(WeatherClient.windowOpen("2026-10-20", now))
        assertFalse(WeatherClient.windowOpen("2026-09-30", now))
    }
}

class CategoryHelpersTest {
    @Test
    fun `corrida hoje acende o indicador da categoria`() {
        val today = java.time.LocalDate.now().toString()
        val category = Category("wrc", "WRC", "WRC", calendar = listOf(Race(id = "r", name = "Rally", date = today)))
        assertTrue(CategorySummary.isActiveToday(category, Instant.now()))
        assertEquals(1, CategorySummary.of(category).rounds)
        assertEquals(0, CategorySummary.of(category).completed)
    }

    @Test
    fun `piloto sem tabela de classificacao nao tem posicao`() {
        val driver = com.pitstophub.app.data.model.Driver("norris", "Lando Norris")
        assertNull(standingOfDriver(Category("f1", "F1", "Formula 1", drivers = listOf(driver)), driver))
    }
}
