package com.pitstophub.app.data

import com.pitstophub.app.SiteData
import com.pitstophub.app.SiteData.category
import com.pitstophub.app.SiteData.jolpica
import com.pitstophub.app.SiteData.race
import com.pitstophub.app.SiteData.staticRace
import com.pitstophub.app.data.model.CircuitInfo
import com.pitstophub.app.data.model.CircuitsFile
import com.pitstophub.app.data.model.LapRecord
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.repo.F1Merger
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant

// Espelha src/circuit-info.test.ts e src/circuit-drawings.test.ts do site, mais a conferencia de que o app da a mesma
// ficha que o site para todas as etapas do catalogo (resultado gravado pelo tools/export-web-data.mjs).

@Serializable
private data class ExpectedRoute(val categoryId: String, val raceId: String, val info: CircuitInfo? = null)

class CircuitRoutingTest {

    private val data = SiteData.webData
    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun image(race: Race, categoryId: String? = null): String? =
        (if (categoryId == null) data.circuit(race) else data.circuitFor(categoryId, race))?.trackImage

    private val jolpicaSepang = jolpica("16", "Bahrain Grand Prix in Malaysia", "sepang", "Sepang International Circuit", "Kuala Lumpur", "Malaysia", "2026-10-04", "07:00:00Z")

    // --- igual ao site em todas as etapas ------------------------------------------------------------

    @Test
    fun `toda etapa do catalogo recebe a mesma ficha que o site da`() {
        val expected = AppJson.decodeFromString(ListSerializer(ExpectedRoute.serializer()), File("src/test/resources/circuit_routing_expected.json").readText())
        assertEquals(SiteData.catalog.categories.sumOf { it.calendar.size }, expected.size)
        assertTrue(expected.count { it.info != null } > 100)
        for (entry in expected) {
            val race = staticRace(entry.categoryId, entry.raceId)
            assertEquals("${entry.categoryId}/${entry.raceId}", entry.info, data.circuitFor(entry.categoryId, race))
        }
    }

    // --- GP do Bahrein de 2026 em Sepang ---------------------------------------------------------------

    @Test
    fun `a etapa da F1 em Sepang usa a ficha e o mapa de Sepang`() {
        val info = data.circuit(staticRace("f1", "bahrain"))!!
        assertEquals("/circuits/sepang.png", info.trackImage)
        assertEquals(5.543, info.lengthKm!!, 0.0)
        assertEquals(310.417, info.raceDistanceKm!!, 0.0)
        assertEquals(56, info.laps)
        assertEquals(1999, info.firstGrandPrix)
    }

    @Test
    fun `o calendario ao vivo (Jolpica) leva a etapa para Sepang, nao para Sakhir`() {
        val live = F1Merger.buildCalendar(category("f1"), listOf(jolpicaSepang), emptyMap(), now).single()
        assertEquals("bahrain", live.id)
        assertEquals("Sepang International Circuit", live.circuit)
        assertEquals("/circuits/sepang.png", image(live))
    }

    @Test
    fun `mesmo casada so pelo nome com o GP de Sakhir, a etapa fica com o local da Jolpica`() {
        val onlySakhir = category("f1").copy(calendar = listOf(staticRace("f1", "bahrain-sakhir")))
        val live = F1Merger.buildCalendar(onlySakhir, listOf(jolpicaSepang), emptyMap(), now).single()
        assertEquals("Sepang International Circuit", live.circuit)
        assertEquals("Kuala Lumpur", live.location)
        assertEquals("Malaysia", live.country)
        assertEquals("/circuits/sepang.png", image(live))
    }

    @Test
    fun `o local decide antes do nome do evento`() {
        assertEquals("/circuits/sepang.png", image(race(name = "GP do Bahrein", enName = "Bahrain GP", country = "Malaysia")))
        assertEquals("/circuits/sepang.png", image(race(name = "Bahrain Grand Prix in Malaysia", location = "Kuala Lumpur")))
        assertEquals("/circuits/sepang.png", image(race(name = "GP do Bahrein", circuit = "Sepang International Circuit")))
    }

    @Test
    fun `provas disputadas em Sakhir usam a ficha e o mapa de Sakhir`() {
        for (r in listOf(staticRace("f1", "bahrain-sakhir"), staticRace("f2", "sakhir-f2"), staticRace("wec", "bahrain"))) {
            val info = data.circuit(r)!!
            assertEquals(r.id, "/circuits/sakhir.png", info.trackImage)
            assertEquals(r.id, 5.412, info.lengthKm!!, 0.0)
        }
        assertEquals("/circuits/sakhir.png", image(race(name = "8 Horas do Bahrein")))
    }

    @Test
    fun `o Madring vence o nome Spanish Grand Prix da Jolpica e tem os dados da F1 de 2026`() {
        assertEquals("/circuits/madrid.png", image(race(name = "Spanish Grand Prix", circuit = "Madring", location = "Madrid")))
        assertEquals("/circuits/spain.png", image(staticRace("f1", "spain")))
        val info = data.circuit(staticRace("f1", "madrid"))!!
        assertEquals(5.414, info.lengthKm!!, 0.0)
        assertEquals(308.399, info.raceDistanceKm!!, 0.0)
        assertEquals(LapRecord("1:35.587", "G. Russell (Mercedes)", 2026), info.lapRecord)
    }

    @Test
    fun `toda etapa da F1 2026 tem ficha com mapa, e o mapa esta no app`() {
        for (r in category("f1").calendar) {
            val img = data.circuitFor("f1", r)?.trackImage
            assertNotNull(r.id, img)
            assertTrue(r.id, Regex("""^/circuits/[\w-]+\.png$""").matches(img!!))
            assertTrue(img, SiteData.mapAsset(img).exists())
        }
    }

    @Test
    fun `DRS saiu, Straight Mode entrou (e so onde o mapa da F1 mostra as zonas)`() {
        assertTrue(data.circuit(staticRace("f1", "bahrain"))!!.straightModeZones!! > 0)
        assertNull(data.circuit(staticRace("f1", "monaco"))!!.straightModeZones)
        assertTrue(!File(SiteData.assets, WebData.CIRCUITS).readText().contains("drsZones"))
    }

    // --- desenhos do OpenStreetMap ----------------------------------------------------------------------

    @Test
    fun `todo desenho publicado tem o mapa no app`() {
        val file = AppJson.decodeFromString<CircuitsFile>(File(SiteData.assets, WebData.CIRCUITS).readText())
        // 34 desenhos do site #9 e os 7 tracados do #12 (Charlotte, New Hampshire, Chicagoland, Markham, Washington, Sebring, Atlanta)
        assertEquals(41, file.drawings.size)
        for (key in listOf("charlotte-oval", "new-hampshire", "chicagoland", "markham", "washington-dc", "sebring", "atlanta")) {
            assertEquals(key, "/circuits/$key.png", file.drawings[key]?.trackImage)
        }
        for ((key, drawn) in file.drawings) {
            assertEquals(key, "/circuits/$key.png", drawn.trackImage)
            assertTrue(key, SiteData.mapAsset(drawn.trackImage).exists())
        }
    }

    @Test
    fun `todo mapa de qualquer etapa do catalogo existe no app, e os desenhos do OSM levam o credito`() {
        val file = AppJson.decodeFromString<CircuitsFile>(File(SiteData.assets, WebData.CIRCUITS).readText())
        val drawn = file.drawings.values.map { it.trackImage }.toSet()
        for (category in SiteData.catalog.categories) {
            for (r in category.calendar) {
                val info = data.circuitFor(category.id, r) ?: continue
                val img = info.trackImage ?: continue
                assertTrue("${category.id}/${r.id} $img", SiteData.mapAsset(img).exists())
                if (img in drawn) assertTrue(r.id, info.osm)
            }
        }
    }

    // --- dados do GP so na F1 --------------------------------------------------------------------------

    @Test
    fun `a WEC em Spa usa o mapa de Spa, sem voltas nem recorde da F1`() {
        val info = data.circuitFor("wec", staticRace("wec", "spa"))!!
        assertEquals("/circuits/spa.png", info.trackImage)
        assertEquals(7.004, info.lengthKm!!, 0.0)
        assertNull(info.laps)
        assertNull(info.lapRecord)
        assertTrue(info.brakingZones.isEmpty())
        assertNull(info.tyreStrategyNote)
        assertEquals(44, data.circuitFor("f1", staticRace("f1", "belgium"))?.laps)
    }

    @Test
    fun `o WRC nao tem ficha de circuito (Monte-Carlo e rali, nao o circuito de Monaco)`() {
        assertNull(data.circuitFor("wrc", staticRace("wrc", "montecarlo")))
    }

    @Test
    fun `Spa e Monza da GTWC Europe usam os mapas de Spa e Monza`() {
        assertEquals("/circuits/spa.png", image(race(name = "CrowdStrike 24 Hours of Spa", circuit = "CrowdStrike 24 Hours of Spa", location = "Bélgica", enLocation = "Belgium"), "gtwc-europe"))
        assertEquals("/circuits/italy.png", image(race(name = "Monza", circuit = "Monza", location = "Itália", enLocation = "Italy"), "gtwc-europe"))
    }

    @Test
    fun `lugares com nome parecido`() {
        assertNotEquals("/circuits/miami.png", image(staticRace("nascar", "homestead"), "nascar"))
        assertNotEquals("/circuits/le-mans.png", image(staticRace("imsa", "roadatlanta"), "imsa"))
        assertEquals("/circuits/usa.png", image(staticRace("wec", "cota"), "wec"))
        assertEquals("/circuits/vegas.png", image(staticRace("f1", "vegas"), "f1"))
        assertNotEquals("/circuits/vegas.png", image(staticRace("nascar", "vegas1"), "nascar"))
        // So o circuito de rua de Washington, D.C. usa o desenho de Washington.
        assertNotEquals("/circuits/washington-dc.png", image(race(name = "Pacific Raceways", circuit = "Pacific Raceways", location = "Kent, Washington"), "gtwc-america"))
    }

    // --- tracado certo por categoria (amostras do catalogo e das fontes ao vivo) -----------------------

    private fun live(name: String, circuit: String, location: String = "", date: String = "2026-06-01") = race(name = name, circuit = circuit, location = location, date = date)

    @Test
    fun `tracado certo por categoria`() {
        val cases: List<Triple<String, Race, String?>> = listOf(
            // NASCAR
            Triple("nascar", staticRace("nascar", "daytona500"), "daytona-oval"),
            Triple("nascar", live("Daytona", "Daytona International Speedway", "", "2026-08-29"), "daytona-oval"),
            Triple("nascar", staticRace("nascar", "indy-brickyard"), "indianapolis-oval"),
            Triple("nascar", staticRace("nascar", "watkinsglen"), "watkins-glen-nascar"),
            Triple("nascar", staticRace("nascar", "phoenix2"), "phoenix"),
            Triple("nascar", live("Talladega", "Talladega Superspeedway"), "talladega"),
            Triple("nascar", staticRace("nascar", "texas"), "texas"),
            Triple("nascar", staticRace("nascar", "pocono"), "pocono"),
            Triple("nascar", live("Michigan", "Michigan International Speedway"), "michigan"),
            Triple("nascar", staticRace("nascar", "iowa"), "iowa"),
            Triple("nascar", staticRace("nascar", "nashville-nascar"), "nashville-superspeedway"),
            Triple("nascar", staticRace("nascar", "homestead"), "homestead"),
            // NASCAR em COTA usa o National Course (sem desenho verificado): nenhum mapa, nao o da F1.
            Triple("nascar", staticRace("nascar", "cota-nascar"), null),
            // Tolerancia de extensao de 3% (site #12): Charlotte, New Hampshire, Chicagoland e Atlanta.
            Triple("nascar", staticRace("nascar", "charlotte600"), "charlotte-oval"),
            Triple("nascar", live("Coca-Cola 600", "Charlotte Motor Speedway", "", "2026-05-24"), "charlotte-oval"),
            Triple("nascar", staticRace("nascar", "charlotte-roval"), "charlotte-oval"),
            Triple("nascar", live("Bank of America 400", "Charlotte Motor Speedway", "", "2026-10-11"), "charlotte-oval"),
            // A data vence o texto: o nome antigo "Roval" nao tira o desenho do oval em 2026.
            Triple("nascar", live("Bank of America ROVAL 400", "Charlotte Motor Speedway ROVAL", "", "2026-10-11"), "charlotte-oval"),
            Triple("nascar", staticRace("nascar", "newhampshire"), "new-hampshire"),
            Triple("nascar", live("Dollar Tree 301", "New Hampshire Motor Speedway", "", "2026-08-23"), "new-hampshire"),
            Triple("nascar", staticRace("nascar", "chicagoland"), "chicagoland"),
            Triple("nascar", live("eero 400", "Chicagoland Speedway", "", "2026-07-05"), "chicagoland"),
            Triple("nascar", staticRace("nascar", "atlanta1"), "atlanta"),
            Triple("nascar", staticRace("nascar", "atlanta2"), "atlanta"),
            Triple("nascar", live("Quaker State 400 Available at Walmart", "Atlanta Motor Speedway", "", "2026-07-12"), "atlanta"),
            // IndyCar
            Triple("indy", staticRace("indy", "indy-500"), "indianapolis-oval"),
            Triple("indy", staticRace("indy", "indy-road"), "indianapolis-road"),
            Triple("indy", live("Indianapolis (misto)", "IMS Road Course", "Indianapolis, Indiana"), "indianapolis-road"),
            Triple("indy", live("Indy 500", "Indianapolis Motor Speedway", "Indianapolis, Indiana"), "indianapolis-oval"),
            Triple("indy", live("St. Petersburg", "Streets of St. Petersburg", "St. Petersburg, Florida"), "st-petersburg"),
            Triple("indy", staticRace("indy", "phoenix"), "phoenix"),
            Triple("indy", staticRace("indy", "barber"), "barber"),
            Triple("indy", staticRace("indy", "detroit-indy"), "detroit"),
            Triple("indy", staticRace("indy", "road-america-indy"), "road-america"),
            Triple("indy", live("Mid-Ohio", "Mid-Ohio Sports Car Course", "Lexington, Ohio"), "mid-ohio"),
            Triple("indy", staticRace("indy", "nashville"), "nashville-superspeedway"),
            Triple("indy", staticRace("indy", "milwaukee-2"), "milwaukee"),
            Triple("indy", staticRace("indy", "long-beach-indy"), null),
            Triple("indy", staticRace("indy", "markham"), "markham"),
            Triple("indy", live("Ontario Honda Dealers Indy at Markham", "Streets of Markham", "", "2026-08-16"), "markham"),
            Triple("indy", staticRace("indy", "washington"), "washington-dc"),
            Triple("indy", live("Freedom 250 Grand Prix of Washington DC", "Streets of Washington", "", "2026-08-23"), "washington-dc"),
            // IMSA
            Triple("imsa", staticRace("imsa", "daytona"), null),
            Triple("imsa", staticRace("imsa", "indianapolis"), "indianapolis-road"),
            Triple("imsa", live("Indianapolis", "Indianapolis Motor Speedway RC"), "indianapolis-road"),
            Triple("imsa", staticRace("imsa", "detroit"), "detroit"),
            Triple("imsa", staticRace("imsa", "roadamerica"), "road-america"),
            Triple("imsa", live("VIR", "VIRginia International Raceway"), "vir"),
            Triple("imsa", staticRace("imsa", "watkins"), null),
            Triple("imsa", staticRace("imsa", "sebring"), "sebring"),
            Triple("imsa", live("74th Annual Mobil 1 Twelve Hours of Sebring", "Sebring International Raceway", "", "2026-03-21"), "sebring"),
            // DTM
            Triple("dtm", live("Lausitzring", "DEKRA Lausitzring Sprint", "Alemanha"), "lausitzring"),
            Triple("dtm", staticRace("dtm", "oschersleben"), "oschersleben"),
            Triple("dtm", staticRace("dtm", "nurburgring"), "nurburgring-sprint"),
            Triple("dtm", staticRace("dtm", "sachsenring"), "sachsenring"),
            Triple("dtm", staticRace("dtm", "hockenheimring"), "hockenheim"),
            Triple("dtm", staticRace("dtm", "norisring"), null),
            // WEC
            Triple("wec", staticRace("wec", "lemans"), "le-mans"),
            Triple("wec", staticRace("wec", "fuji"), "fuji"),
            // GT World Challenge
            Triple("gtwc-europe", live("Brands Hatch", "Brands Hatch", "Grã-Bretanha"), "brands-hatch"),
            Triple("gtwc-europe", live("Misano", "Misano", "Itália"), "misano"),
            Triple("gtwc-europe", live("Nürburgring", "Nürburgring", "Alemanha"), "nurburgring-gp"),
            Triple("gtwc-europe", live("Portimao", "Portimao", "Portugal"), "portimao"),
            Triple("gtwc-america", live("Indianapolis Motor Speedway", "Indianapolis Motor Speedway", "Estados Unidos"), "indianapolis-road"),
            Triple("gtwc-america", live("Road America", "Road America"), "road-america"),
            Triple("gtwc-america", live("Barber Motorsports Park", "Barber Motorsports Park"), "barber"),
            Triple("gtwc-america", live("Sebring International Raceway", "Sebring International Raceway", "", "2026-05-09"), "sebring"),
            Triple("gtwc-asia", live("Fuji International Speedway (SRO GT PowerTour)", "Fuji International Speedway (SRO GT PowerTour)", "Japão"), "fuji"),
            Triple("gtwc-asia", live("Okayama International Circuit (SRO GT PowerTour)", "Okayama International Circuit (SRO GT PowerTour)", "Japão"), "okayama"),
            // Brasil
            Triple("endurance-brasil", live("Etapa 8 - Três Horas de Goiânia", "Autódromo Internacional de Goiânia", "Autódromo Internacional de Goiânia"), "goiania"),
            Triple("endurance-brasil", live("Etapa 5 - Três Horas de Goiânia", "Autódromo Internacional de Santa Cruz do Sul", "Autódromo Internacional de Santa Cruz do Sul"), null),
            Triple("nascar-brasil", live("Londrina", "", "Londrina"), "londrina"),
            Triple("porsche-cup", live("Le Mans", "", "Le Mans"), "le-mans"),
            Triple("porsche-cup", live("Algarve (Endurance)", "", "Algarve"), "portimao"),
        )
        for ((categoryId, r, key) in cases) {
            val label = "$categoryId: ${r.name.ifBlank { r.id }}"
            if (key == null) assertNull(label, image(r, categoryId)) else assertEquals(label, "/circuits/$key.png", image(r, categoryId))
        }
    }

    @Test
    fun `a F1 em Jeddah tem a ficha do GP, a F2 so o circuito`() {
        val f1 = data.circuitFor("f1", staticRace("f1", "saudi"))!!
        assertEquals("/circuits/jeddah.png", f1.trackImage)
        assertEquals(50, f1.laps)
        assertTrue(f1.osm)
        val f2 = data.circuitFor("f2", staticRace("f2", "jeddah-f2"))!!
        assertEquals("/circuits/jeddah.png", f2.trackImage)
        assertNull(f2.laps)
    }

    @Test
    fun `GTWC America em COTA usa o tracado completo (o da F1)`() {
        assertEquals("/circuits/usa.png", image(live("Circuit of the Americas", "Circuit of the Americas"), "gtwc-america"))
    }

    @Test
    fun `assets de mapa sao so os citados pelas fichas`() {
        val file = AppJson.decodeFromString<CircuitsFile>(File(SiteData.assets, WebData.CIRCUITS).readText())
        val cited = (file.gp.mapNotNull { it.info.trackImage } + file.drawings.values.map { it.trackImage }).map { SiteData.mapAsset(it).name }.toSet()
        val bundled = File(SiteData.assets, "circuits").listFiles().orEmpty().map { it.name }.toSet()
        assertEquals(cited, bundled)
    }
}
