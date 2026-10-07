package com.pitstophub.app.ui.categories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Tune
import com.pitstophub.app.data.model.CatalogFile
import com.pitstophub.app.data.model.RuleSection
import com.pitstophub.app.data.remote.AppJson
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
        assertSame(Icons.Filled.Lightbulb, ruleIcon("Curiosidades"))
        assertSame(Icons.Filled.Flag, ruleIcon("Formato do fim de semana"))
    }
}
