package com.pitstophub.app.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RuleSection
import com.pitstophub.app.data.model.SessionKind
import com.pitstophub.app.data.model.SessionSlot
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.domain.CategoryRules
import com.pitstophub.app.domain.RemoteCategoryRules
import com.pitstophub.app.domain.RuleKind
import com.pitstophub.app.ui.components.ruleIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RulesTest {

    private val catalog = AppJson.decodeFromString<CatalogFile>(File("src/main/assets/motorsport_data.json").readText())

    private fun rules(id: String) = catalog.categories.first { it.id == id }.rulesFormat

    @Test
    fun `a secao de pontuacao e achada em cada categoria que a explica`() {
        assertEquals(1, scoringRuleIndex(rules("f1")))
        assertEquals(2, scoringRuleIndex(rules("wec")))
        assertNull(scoringRuleIndex(rules("gtwc-asia"))) // so tem formato e classes
        // o titulo em ingles tambem vale
        assertEquals(1, scoringRuleIndex(listOf(RuleSection("Formato", "Format", "x"), RuleSection("Outro", "Scoring", "y"))))
    }

    @Test
    fun `toda categoria com regras tem pelo menos duas secoes para ler`() {
        val withRules = catalog.categories.filter { it.rulesFormat.isNotEmpty() }
        assertTrue(withRules.size >= 12)
        withRules.forEach { assertTrue("${it.id} sem secoes suficientes", it.rulesFormat.size >= 2) }
    }

    @Test
    fun `titulos longos viram atalhos curtos sem cortar palavra`() {
        assertEquals("Pontuação", shortRuleTitle("Pontuação"))
        assertEquals("Curiosidades e regras…", shortRuleTitle("Curiosidades e regras especiais"))
    }

    @Test
    fun `cada tipo de secao ganha o icone certo`() {
        assertSame(Icons.Filled.Leaderboard, ruleIcon("Pontuação"))
        assertSame(Icons.Filled.Tune, ruleIcon("Balance of Performance e Handicap"))
        assertSame(Icons.Filled.Groups, ruleIcon("Classes"))
        assertSame(Icons.Filled.DirectionsCar, ruleIcon("Carros e regulamento técnico"))
        assertSame(Icons.Filled.DirectionsCar, ruleIcon("Carros WRC27 (Novo regulamento)"))
        assertSame(Icons.Filled.Lightbulb, ruleIcon("Curiosidades"))
        assertSame(Icons.Filled.Flag, ruleIcon("Formato do fim de semana"))
    }

    @Test
    fun `WRC em 2026 explica Rally1 e transicao para 2027 e em 2027 muda sozinho para WRC27`() {
        val wrc = catalog.categories.first { it.id == "wrc" }
        val wrc2026 = CategoryRules.resolve(wrc)
        assertEquals(2026, CategoryRules.activeSeasonOf(wrc2026))
        val car2026 = wrc2026.rulesFormat.first { CategoryRules.ruleKind(it.title) == RuleKind.CAR }
        assertEquals("Carros Rally1 e transição para 2027", car2026.title)
        assertTrue(car2026.body.contains("novo regulamento técnico WRC27"))

        val wrc2027 = CategoryRules.resolve(
            wrc.copy(
                calendar = listOf(
                    Race("montecarlo-2027", "Rallye Monte-Carlo", location = "Mônaco", date = "2027-01-24"),
                    Race("sweden-2027", "Rally Sweden", location = "Umeå", date = "2027-02-14"),
                ),
            ),
        )
        assertEquals(2027, CategoryRules.activeSeasonOf(wrc2027))
        val car2027 = wrc2027.rulesFormat.first { CategoryRules.ruleKind(it.title) == RuleKind.CAR }
        assertEquals("Carros WRC27 (Novo regulamento)", car2027.title)
        assertTrue(car2027.body.contains("300 a 330 cv"))
        assertTrue(wrc2027.longDescription.contains("geração WRC27"))
    }

    @Test
    fun `Porsche Cup muda para 992_2 em 2027 e lista as pistas reais do calendario`() {
        val pcup = catalog.categories.first { it.id == "porsche-cup" }
        val resolved = CategoryRules.resolve(
            pcup.copy(
                calendar = listOf(
                    Race("r1", "Interlagos", location = "Interlagos", enLocation = "Interlagos", date = "2027-03-14"),
                    Race("r2", "Velocitta", location = "Velocitta", enLocation = "Velocitta", date = "2027-04-18"),
                ),
            ),
        )
        assertTrue(resolved.longDescription.contains("911 Cup 992.2"))
        assertTrue(resolved.longDescription.contains("Interlagos e Velocitta"))
        val divs = resolved.rulesFormat.first { it.title == "Divisões" }
        assertTrue(divs.body.contains("992.2"))
    }

    @Test
    fun `F1 adapta cidades de Sprint pelos dados ao vivo e aceita regras confirmadas remotas`() {
        val f1 = catalog.categories.first { it.id == "f1" }
        val f1Live = CategoryRules.resolve(
            f1.copy(
                calendar = listOf(
                    Race("aus", "GP da Austrália", location = "Melbourne", date = "2027-03-14", sessions = listOf(SessionSlot(SessionKind.RACE, "2027-03-14T04:00:00Z"))),
                    Race("bra", "GP de São Paulo", location = "São Paulo", date = "2027-11-07", sessions = listOf(SessionSlot(SessionKind.SPRINT, "2027-11-06T14:00:00Z"), SessionSlot(SessionKind.RACE, "2027-11-07T17:00:00Z"))),
                ),
            ),
            remoteRules = RemoteCategoryRules(season = 2027, assumed = false, sections = listOf(RuleSection("Pontuação", "Points system", "Novo texto", "New body"))),
        )
        val format = f1Live.rulesFormat.first { CategoryRules.ruleKind(it.title) == RuleKind.FORMAT }
        assertTrue(format.body.contains("São Paulo"))
        val scoring = f1Live.rulesFormat.first { CategoryRules.ruleKind(it.title) == RuleKind.SCORING }
        assertEquals("Novo texto", scoring.body)
    }
}
