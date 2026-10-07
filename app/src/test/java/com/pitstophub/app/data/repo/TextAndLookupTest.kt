package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Team
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TextAndLookupTest {

    @Test
    fun `normalize tira acentos, pontuacao e caixa`() {
        assertEquals("joao da silva", TextUtil.normalize("  João  da Silva! "))
        assertEquals("m verstappen", TextUtil.normalize("M. Verstappen"))
    }

    @Test
    fun `slugify gera ids estaveis`() {
        assertEquals("circuit-de-barcelona-catalunya", TextUtil.slugify("Circuit de Barcelona-Catalunya"))
        assertEquals("sao-paulo", TextUtil.slugify("São Paulo"))
    }

    @Test
    fun `nomes dos sites oficiais viram titulo`() {
        assertEquals("Reece Barr", TextUtil.toDriverName("reece BARR"))
        assertEquals("Jean-Eric Vergne", TextUtil.toTitleCase("jean-eric VERGNE"))
    }

    private val drivers = listOf(
        Driver("verstappen", "Max Verstappen", "3", "Países Baixos", "redbull"),
        Driver("auer", "Lucas Auer", "1", "Áustria", "winward"),
        Driver("engel", "Maro Engel", "2", "Alemanha", "winward"),
    )

    @Test
    fun `acha piloto por nome completo, abreviado ou so sobrenome`() {
        assertEquals("verstappen", DriverLookup.find(drivers, "Max Verstappen")?.id)
        assertEquals("verstappen", DriverLookup.find(drivers, "M. Verstappen")?.id)
        assertEquals("verstappen", DriverLookup.find(drivers, "VERSTAPPEN")?.id)
        assertNull(DriverLookup.find(drivers, "Fulano de Tal"))
        assertNull(DriverLookup.find(drivers, ""))
    }

    @Test
    fun `em duplas usa o primeiro piloto reconhecido`() {
        assertEquals("engel", DriverLookup.findFirst(drivers, "Fulano / Maro Engel")?.id)
        assertEquals("auer", DriverLookup.findFirst(drivers, "Lucas Auer / Maro Engel")?.id)
    }

    @Test
    fun `acha equipe por nome exato ou parcial`() {
        val category = Category(
            id = "wec", name = "WEC", fullName = "WEC",
            teams = listOf(Team("t1", "Toyota Gazoo Racing"), Team("t2", "Ferrari AF Corse")),
        )
        assertEquals("t1", DriverLookup.team(category, "Toyota Gazoo Racing")?.id)
        assertEquals("t2", DriverLookup.team(category, "AF Corse")?.id)
        assertNull(DriverLookup.team(category, "Porsche"))
    }
}
