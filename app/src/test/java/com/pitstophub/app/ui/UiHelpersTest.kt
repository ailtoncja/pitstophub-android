package com.pitstophub.app.ui

import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.domain.CategoryGroups
import com.pitstophub.app.ui.standings.abbreviate
import com.pitstophub.app.ui.standings.formatPoints
import com.pitstophub.app.ui.teams.orderedTeams
import kotlinx.serialization.decodeFromString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

class UiHelpersTest {

    private lateinit var previous: Locale

    @Before
    fun fixLocale() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US) // "131.5" em vez de "131,5"
    }

    @After
    fun restoreLocale() = Locale.setDefault(previous)

    @Test
    fun `abrevia o primeiro nome como no design`() {
        assertEquals("M. Verstappen", abbreviate("Max Verstappen"))
        assertEquals("A. Kimi Antonelli", abbreviate("Andrea Kimi Antonelli"))
        assertEquals("Senna", abbreviate("Senna"))
    }

    @Test
    fun `pontos inteiros sem casa decimal e fracionados com uma`() {
        assertEquals("354", formatPoints(354.0))
        assertEquals("131.5", formatPoints(131.5))
        assertEquals("0", formatPoints(0.0))
    }

    @Test
    fun `menu lateral cobre todas as categorias do catalogo uma unica vez`() {
        val catalog = AppJson.decodeFromString<CatalogFile>(File("src/main/assets/motorsport_data.json").readText())
        val inMenu = CategoryGroups.all.flatMap { it.ids }
        assertEquals(catalog.categories.map { it.id }.sorted(), inMenu.sorted())
    }

    @Test
    fun `equipes seguem a ordem do campeonato de construtores`() {
        val catalog = AppJson.decodeFromString<CatalogFile>(File("src/main/assets/motorsport_data.json").readText())
        val f1 = catalog.categories.first { it.id == "f1" }
        val ordered = orderedTeams(f1)
        assertEquals(f1.teams.size, ordered.size)
        val ranking = f1.standings!!.constructors!!.sortedBy { it.position }.map { it.name }
        // a primeira equipe da lista e a lider do campeonato de construtores
        assertEquals(ranking.first().lowercase(), ordered.first().name.lowercase())
    }
}
