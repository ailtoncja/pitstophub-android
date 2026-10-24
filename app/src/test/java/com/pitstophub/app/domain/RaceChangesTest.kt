package com.pitstophub.app.domain

import com.pitstophub.app.SiteData
import com.pitstophub.app.SiteData.category
import com.pitstophub.app.SiteData.jolpica
import com.pitstophub.app.SiteData.synced
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.repo.F1Merger
import com.pitstophub.app.data.repo.SyncedMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

// Espelha src/lib/race-changes.test.ts do site: dados dos pares (simetria, alvos, fontes), raceChange e a ordem do
// calendario com as canceladas.
class RaceChangesTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")
    private val utc = ZoneOffset.UTC

    private fun catalog(id: String) = category(id).calendar
    private fun ids(races: List<Race>) = races.map { it.id }
    private fun live(categoryId: String, vararg rows: com.pitstophub.app.data.remote.SyncedRaceRow) = SyncedMerger.mergeCalendar(category(categoryId), rows.toList()).calendar

    // --- dados dos pares (motorsport_data.json, exportado de types.ts) -------------------------------------

    private val all = SiteData.catalog.categories.flatMap { c -> c.calendar.map { Triple(c.id, c.calendar, it) } }

    @Test
    fun `todo replacedBy e replaces aponta para uma etapa existente da mesma categoria, nunca para si mesma`() {
        for ((categoryId, calendar, race) in all) {
            for (id in race.replacedBy.orEmpty() + race.replaces.orEmpty()) {
                assertTrue("$categoryId/${race.id} -> $id", calendar.any { it.id == id })
                assertNotEquals("$categoryId/${race.id}", race.id, id)
            }
        }
    }

    @Test
    fun `os links sao simetricos e transferida vale nos dois lados`() {
        var pairs = 0
        for ((categoryId, calendar, race) in all) {
            for (id in race.replacedBy.orEmpty()) {
                val target = calendar.first { it.id == id }
                assertTrue("$categoryId/$id deveria citar ${race.id}", race.id in target.replaces.orEmpty())
                assertEquals("$categoryId/${race.id} <-> $id", race.relocated, target.relocated)
                pairs++
            }
            for (id in race.replaces.orEmpty()) {
                assertTrue("$categoryId/$id deveria citar ${race.id}", race.id in calendar.first { it.id == id }.replacedBy.orEmpty())
            }
        }
        assertTrue(pairs >= 7)
    }

    @Test
    fun `so etapa cancelada tem replacedBy, so etapa nao cancelada tem replaces`() {
        for ((categoryId, _, race) in all) {
            if (race.replacedBy != null) assertEquals("$categoryId/${race.id}", RaceStatus.CANCELLED, race.status)
            if (race.replaces != null) assertNotEquals("$categoryId/${race.id}", RaceStatus.CANCELLED, race.status)
        }
    }

    @Test
    fun `toda etapa cancelada diz se teve substituta e todo par cita a fonte`() {
        for ((categoryId, _, race) in all) {
            if (race.status == RaceStatus.CANCELLED) assertNotNull("$categoryId/${race.id}", race.replacedBy)
            if (race.status == RaceStatus.CANCELLED || race.replaces != null || race.replacedBy != null || race.relocated) {
                assertTrue("$categoryId/${race.id}", Regex("""^https://\S+$""").matches(race.changeSource.orEmpty()))
            }
        }
    }

    @Test
    fun `os pares confirmados nas fontes oficiais`() {
        fun pick(categoryId: String, id: String) = catalog(categoryId).first { it.id == id }
        pick("f1", "bahrain-sakhir").let {
            assertEquals(RaceStatus.CANCELLED, it.status)
            assertEquals(listOf("bahrain"), it.replacedBy)
            assertTrue(it.relocated)
        }
        pick("f1", "bahrain").let {
            assertEquals("Sepang International Circuit", it.circuit)
            assertEquals(listOf("bahrain-sakhir"), it.replaces)
            assertTrue(it.relocated)
        }
        assertEquals(emptyList<String>(), pick("f1", "saudi").replacedBy)
        for (id in listOf("sakhir-f2", "jeddah-f2")) assertEquals(listOf("miami-f2", "montreal-f2"), pick("f2", id).replacedBy)
        pick("f3", "sakhir-f3").let {
            assertEquals(RaceStatus.CANCELLED, it.status)
            assertEquals("2026-04-12", it.date)
            assertEquals(emptyList<String>(), it.replacedBy)
        }
        pick("f1-academy", "jeddah-academy").let {
            assertEquals(RaceStatus.CANCELLED, it.status)
            assertEquals("2026-04-19", it.date)
            assertEquals(emptyList<String>(), it.replacedBy)
        }
        for (id in listOf("qatar", "bahrain")) assertEquals(listOf("barcelona-wec", "monza-wec"), pick("wec", id).replacedBy)
        pick("wrc", "saudi").let {
            assertEquals(RaceStatus.CANCELLED, it.status)
            assertEquals(emptyList<String>(), it.replacedBy)
        }
    }

    // --- raceChange ---------------------------------------------------------------------------------------

    @Test
    fun `etapa cancelada com substituta, transferida e sem substituta`() {
        val f1 = catalog("f1")
        val sakhir = RaceChanges.raceChange(f1, f1.first { it.id == "bahrain-sakhir" }) as RaceChange.ReplacedBy
        assertTrue(sakhir.relocated)
        assertEquals(listOf("bahrain"), ids(sakhir.races))
        assertTrue(RaceChanges.raceChange(f1, f1.first { it.id == "saudi" }) is RaceChange.NoReplacement)
        assertNull(RaceChanges.raceChange(f1, f1.first { it.id == "australia" }))
    }

    @Test
    fun `a substituta vinda da fonte ao vivo (id proprio) ainda acha as canceladas do catalogo`() {
        val f2 = catalog("f2")
        val miami = live("f2", synced("r02-miami-gardens", "Miami", "2026-05-03", "completed", "Miami International Autodrome")).first()
        val change = RaceChanges.raceChange(f2, miami) as RaceChange.Replaces
        assertEquals(false, change.relocated)
        assertEquals(listOf("sakhir-f2", "jeddah-f2"), ids(change.races))
        assertTrue(RaceChanges.raceChange(f2, live("f2", synced("r03-montreal", "Montreal", "2026-05-24")).first()) is RaceChange.Replaces)
        val monza = Race(id = "r08-monza", name = "Monza", location = "Monza", date = "2026-11-08")
        assertEquals(listOf("qatar", "bahrain"), ids((RaceChanges.raceChange(catalog("wec"), monza) as RaceChange.Replaces).races))
        // Outra etapa da mesma fonte nao vira substituta.
        assertNull(RaceChanges.raceChange(f2, Race(id = "r04-monte-carlo", name = "Mônaco", location = "Mônaco", date = "2026-06-07", status = RaceStatus.COMPLETED)))
    }

    @Test
    fun `F1 (Jolpica) - a etapa de Sepang mantem o id e aponta para Sakhir`() {
        val sepang = F1Merger.buildCalendar(
            category("f1"),
            listOf(jolpica("16", "Bahrain Grand Prix in Malaysia", "sepang", "Sepang International Circuit", "Kuala Lumpur", "Malaysia", "2026-10-04", "07:00:00Z")),
            emptyMap(),
            now,
        ).single()
        val change = RaceChanges.raceChange(catalog("f1"), sepang) as RaceChange.Replaces
        assertTrue(change.relocated)
        assertEquals(listOf("bahrain-sakhir"), ids(change.races))
    }

    @Test
    fun `todo link de substituta abre uma pagina (id do catalogo vai para o id ao vivo)`() {
        val f2 = live(
            "f2",
            synced("r01-melbourne", "Melbourne", "2026-03-08"),
            synced("r02-miami-gardens", "Miami", "2026-05-03", "completed", "Miami International Autodrome"),
            synced("r03-montreal", "Montreal", "2026-05-24", "completed", "Circuit Gilles-Villeneuve"),
        )
        assertEquals(RaceRoute.Redirect("r02-miami-gardens"), RaceRoutes.resolve(f2, catalog("f2"), "miami-f2"))
        assertEquals(RaceRoute.Redirect("r03-montreal"), RaceRoutes.resolve(f2, catalog("f2"), "montreal-f2"))
        val jeddah = RaceRoutes.resolve(f2, catalog("f2"), "jeddah-f2") as RaceRoute.Found
        assertEquals(RaceStatus.CANCELLED, jeddah.race.status)
    }

    // --- ordem do calendario com as canceladas ------------------------------------------------------------

    @Test
    fun `F2 ao vivo - Sakhir e Jeddah voltam na posicao da data, entre Melbourne e Miami`() {
        val f2 = live(
            "f2",
            synced("r01-melbourne", "Melbourne", "2026-03-08"),
            synced("r02-miami-gardens", "Miami", "2026-05-03"),
            synced("r03-montreal", "Montreal", "2026-05-24"),
            synced("r13-lusail", "Lusail", "2026-11-29", "upcoming"),
        )
        assertEquals(
            listOf("r01-melbourne", "sakhir-f2", "jeddah-f2", "r02-miami-gardens", "r03-montreal", "r13-lusail"),
            ids(RaceChanges.calendarWithCancelled(f2, catalog("f2"), utc)),
        )
        val sections = RaceChanges.calendarSections(f2, catalog("f2"), now, utc)
        assertEquals(listOf("r13-lusail"), ids(sections.toCome))
        assertEquals(listOf("r03-montreal", "r02-miami-gardens", "jeddah-f2", "sakhir-f2", "r01-melbourne"), ids(sections.played))
    }

    @Test
    fun `WEC - Catar e Bahrein ficam em a disputar, na data, mas nao viram a proxima etapa nem contam`() {
        val wec = live("wec", synced("r07-barcelona", "Barcelona", "2026-10-18"), synced("r08-monza", "Monza", "2026-11-08", "upcoming"))
        val sections = RaceChanges.calendarSections(wec, catalog("wec"), now, utc)
        assertEquals(listOf("qatar", "bahrain", "r08-monza"), ids(sections.toCome))
        assertEquals("r07-barcelona", sections.played.first().id)
        // Sem duplicar: a mescla do WEC ja recoloca as canceladas do catalogo.
        assertEquals(1, ids(RaceChanges.calendarWithCancelled(wec, catalog("wec"), utc)).count { it == "qatar" })
        assertEquals("r08-monza", RaceTime.nextRace(wec, now, utc)?.id)
    }

    @Test
    fun `F1 (catalogo) - as canceladas nao entram na numeracao nem na contagem`() {
        val f1 = catalog("f1")
        assertEquals(4, RaceChanges.roundNumber(f1, f1.first { it.id == "miami" }))
        assertEquals(0, RaceChanges.roundNumber(f1, f1.first { it.id == "saudi" }))
        assertEquals(f1.size - 2, f1.count { it.status != RaceStatus.CANCELLED })
        val all = ids(RaceChanges.calendarWithCancelled(f1, f1, utc))
        assertEquals(all.indexOf("japan") + 1, all.indexOf("bahrain-sakhir"))
        assertEquals(all.indexOf("bahrain-sakhir") + 1, all.indexOf("saudi"))
    }

    @Test
    fun `WRC - o Rally Saudi Arabia cancelado nao e mais a proxima etapa`() {
        val wrc = catalog("wrc")
        assertNotEquals("saudi", RaceTime.nextRace(wrc, now, utc)?.id)
        assertEquals(13, wrc.count { it.status != RaceStatus.CANCELLED })
        assertTrue("saudi" in ids(RaceChanges.calendarSections(wrc, wrc, now, utc).toCome))
    }

    @Test
    fun `joinNames junta com virgulas e e`() {
        assertEquals("A", RaceChanges.joinNames(listOf("A"), "e"))
        assertEquals("A e B", RaceChanges.joinNames(listOf("A", "B"), "e"))
        assertEquals("A, B and C", RaceChanges.joinNames(listOf("A", "B", "C"), "and"))
    }
}
