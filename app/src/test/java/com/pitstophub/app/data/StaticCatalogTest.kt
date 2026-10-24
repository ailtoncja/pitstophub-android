package com.pitstophub.app.data

import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.remote.AppJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Valida o JSON exportado do PWA (`assets/motorsport_data.json`) contra os modelos do app. */
class StaticCatalogTest {

    private val catalog: CatalogFile by lazy {
        val file = File("src/main/assets/${StaticCatalog.ASSET_NAME}")
        AppJson.decodeFromString<CatalogFile>(file.readText())
    }

    @Test
    fun `carrega as 16 categorias do PWA`() {
        assertEquals(16, catalog.categories.size)
        val ids = catalog.categories.map { it.id }
        assertTrue(ids.containsAll(listOf("f1", "f2", "f3", "f1-academy", "wec", "imsa", "dtm", "indy", "nascar", "wrc")))
        assertTrue(ids.containsAll(listOf("gtwc-europe", "gtwc-america", "gtwc-asia", "endurance-brasil")))
        assertTrue(ids.containsAll(listOf("nascar-brasil", "porsche-cup")))
    }

    @Test
    fun `categorias sem dados estaticos dependem da API ao vivo`() {
        // GTWC, Endurance Brasil, NASCAR Brasil e Porsche Cup vem so da api-motorsports: o catalogo
        // nao traz equipes nem corridas.
        listOf("gtwc-europe", "gtwc-america", "gtwc-asia", "endurance-brasil", "nascar-brasil", "porsche-cup").forEach { id ->
            val category = catalog.categories.first { it.id == id }
            assertTrue("$id deveria vir vazio", category.teams.isEmpty() && category.calendar.isEmpty())
        }
    }

    @Test
    fun `F1 tem grid completo e todo piloto aponta para uma equipe existente`() {
        val f1 = catalog.categories.first { it.id == "f1" }
        assertEquals(11, f1.teams.size)
        assertEquals(22, f1.drivers.size)
        val teamIds = f1.teams.map { it.id }.toSet()
        f1.drivers.forEach { assertTrue("${it.name} -> ${it.teamId}", it.teamId in teamIds) }
        assertTrue(f1.calendar.all { it.date.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
    }

    @Test
    fun `biografias de equipe usam a chave categoria dois-pontos equipe`() {
        assertNotNull(catalog.teamBios["f1:mercedes"])
        assertTrue(catalog.teamBios.getValue("f1:mercedes").pt.isNotBlank())
        assertTrue(catalog.teamBios.getValue("f1:mercedes").en.isNotBlank())
    }

    @Test
    fun `cores de equipe sao hexadecimais validos`() {
        catalog.categories.flatMap { it.teams }.forEach {
            assertTrue("${it.id}: ${it.color}", it.color.matches(Regex("#[0-9A-Fa-f]{6}")))
        }
    }
}
