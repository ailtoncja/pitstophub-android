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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Espelha src/lib/race-route.test.ts do site.
class RaceRoutesTest {

    private val now = Instant.parse("2026-10-21T12:00:00Z")

    private fun route(categoryId: String, calendar: List<Race>, raceId: String) = RaceRoutes.resolve(calendar, category(categoryId).calendar, raceId)

    private fun found(result: RaceRoute): Race {
        assertTrue("esperava uma etapa, veio $result", result is RaceRoute.Found)
        return (result as RaceRoute.Found).race
    }

    // O calendario oficial da F2 2026 nao tem Sakhir nem Jeddah (canceladas) e a fonte usa ids proprios.
    private val f2 = SyncedMerger.mergeCalendar(
        category("f2"),
        listOf(
            synced("r01-melbourne", "Melbourne", "2026-03-08"),
            synced("r02-miami-gardens", "Miami", "2026-05-03"),
            synced("r13-lusail", "Lusail", "2026-11-29", "upcoming"),
        ),
    )

    @Test
    fun `F2 - a fonte ao vivo nao lista a rodada cancelada de Sakhir, mas o id do catalogo abre a etapa cancelada`() {
        assertFalse(f2.calendar.any { it.id == "sakhir-f2" })
        val race = found(route("f2", f2.calendar, "sakhir-f2"))
        assertEquals("sakhir-f2", race.id)
        assertEquals(RaceStatus.CANCELLED, race.status)
        assertEquals("Bahrain International Circuit", race.circuit)
        assertEquals("/circuits/sakhir.png", SiteData.webData.circuit(race)?.trackImage)
        assertEquals(RaceStatus.CANCELLED, found(route("f2", f2.calendar, "jeddah-f2")).status)
    }

    @Test
    fun `F2 - id do catalogo de uma etapa que a fonte tem com outro id vai para o id ao vivo`() {
        assertEquals(RaceRoute.Redirect("r01-melbourne"), route("f2", f2.calendar, "melbourne-f2"))
        assertEquals(RaceRoute.Redirect("r13-lusail"), route("f2", f2.calendar, "lusail-f2"))
    }

    @Test
    fun `F2 - id ao vivo abre direto e id inexistente continua nao encontrado`() {
        assertEquals("r02-miami-gardens", found(route("f2", f2.calendar, "r02-miami-gardens")).id)
        assertEquals(RaceRoute.Missing, route("f2", f2.calendar, "nao-existe"))
    }

    @Test
    fun `F2 - antes da sincronizacao (catalogo puro) tudo abre pelo id do catalogo`() {
        assertEquals("sakhir-f2", found(route("f2", category("f2").calendar, "sakhir-f2")).id)
        assertEquals("melbourne-f2", found(route("f2", category("f2").calendar, "melbourne-f2")).id)
    }

    @Test
    fun `F1 - o GP do Bahrein cancelado em Sakhir abre como cancelado e Sepang mantem o id bahrain`() {
        val live = F1Merger.buildCalendar(
            category("f1"),
            listOf(
                jolpica("1", "Australian Grand Prix", "albert_park", "Albert Park Grand Prix Circuit", "Melbourne", "Australia", "2026-03-08"),
                jolpica("16", "Bahrain Grand Prix in Malaysia", "sepang", "Sepang International Circuit", "Kuala Lumpur", "Malaysia", "2026-10-04"),
            ),
            emptyMap(),
            now,
        )
        assertFalse(live.any { it.id == "bahrain-sakhir" })
        val sakhir = found(route("f1", live, "bahrain-sakhir"))
        assertEquals(RaceStatus.CANCELLED, sakhir.status)
        assertEquals("Sakhir", sakhir.location)
        assertEquals(RaceStatus.CANCELLED, found(route("f1", live, "saudi")).status)
        assertEquals("Sepang International Circuit", found(route("f1", live, "bahrain")).circuit)
    }

    @Test
    fun `WEC - as canceladas que a mescla recoloca abrem pelo proprio id`() {
        val wec = SyncedMerger.mergeCalendar(category("wec"), listOf(synced("r01-imola", "Imola", "2026-04-19")))
        val race = found(route("wec", wec.calendar, "qatar"))
        assertEquals(RaceStatus.CANCELLED, race.status)
    }

    @Test
    fun `DTM - o catalogo no sabado e a fonte no domingo ainda sao a mesma etapa`() {
        val dtm = SyncedMerger.mergeCalendar(category("dtm"), listOf(synced("r07-sachsenring", "Sachsenring", "2026-09-13", "completed", "Sachsenring")))
        assertEquals(RaceRoute.Redirect("r07-sachsenring"), route("dtm", dtm.calendar, "sachsenring"))
    }

    @Test
    fun `IndyCar - rodada dupla de Milwaukee vai para a corrida certa`() {
        val indy = SyncedMerger.mergeCalendar(
            category("indy"),
            listOf(
                synced("r16-milwaukee-1", "Milwaukee 1", "2026-08-30", "completed", "Milwaukee Mile"),
                synced("r17-milwaukee-2", "Milwaukee 2", "2026-08-30", "completed", "Milwaukee Mile"),
            ),
        )
        assertEquals(RaceRoute.Redirect("r16-milwaukee-1"), route("indy", indy.calendar, "milwaukee-1"))
        assertEquals(RaceRoute.Redirect("r17-milwaukee-2"), route("indy", indy.calendar, "milwaukee-2"))
    }

    @Test
    fun `WRC - etapa so do catalogo, sem par na fonte, abre com os dados do catalogo`() {
        val wrc = SyncedMerger.mergeCalendar(category("wrc"), listOf(synced("r13-sardenha", "Sardenha", "2026-10-04")))
        val race = found(route("wrc", wrc.calendar, "saudi"))
        assertEquals("saudi", race.id)
        assertEquals("Rally Saudi Arabia", race.name)
    }

    @Test
    fun `findLiveTwin - outra pista no fim de semana seguinte e etapas canceladas nao servem de par`() {
        val base = Race(id = "monza-x", name = "Rodada de Monza", location = "Monza", circuit = "Autodromo Nazionale Monza", date = "2026-09-06")
        assertNull(RaceRoutes.findLiveTwin(listOf(SyncedMerger.mergeCalendar(category("f2"), listOf(synced("r12-baku", "Baku", "2026-09-07", "upcoming"))).calendar.first()), base))
        val cancelled = Race(id = "r10-monza", name = "Monza", location = "Monza", date = "2026-09-06", status = RaceStatus.CANCELLED)
        assertNull(RaceRoutes.findLiveTwin(listOf(cancelled), base))
    }
}
