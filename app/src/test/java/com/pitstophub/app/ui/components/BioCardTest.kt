package com.pitstophub.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BioCardTest {

    private val hamilton = "Lewis Hamilton estreou na Fórmula 1 pela McLaren em 2007 e conquistou sete títulos mundiais " +
        "(2008, 2014, 2015, 2017, 2018, 2019 e 2020), o maior número da história da categoria. Somando 106 vitórias, " +
        "207 pódios e 104 poles na carreira, mudou-se para a Ferrari em 2025. Após um início difícil na nova equipe, " +
        "voltou a subir ao pódio em 2026 na China, no Canadá e em Mônaco, e conquistou sua primeira vitória pela " +
        "Ferrari na primeira edição do GP de Barcelona-Catalunya. Até a etapa da Hungria de 2026, terminou em 5º " +
        "lugar na corrida mais recente após uma penalidade de cinco segundos por excesso de velocidade no pit lane."

    @Test
    fun `a primeira frase vira o destaque e o resto vai em paragrafos curtos`() {
        val bio = splitBio(hamilton)
        assertTrue(bio.lead.startsWith("Lewis Hamilton estreou"))
        assertTrue(bio.lead.endsWith("da categoria."))
        assertTrue(bio.paragraphs.size >= 2)
        // nada se perde: destaque + paragrafos reconstroem o texto original
        assertEquals(hamilton, (listOf(bio.lead) + bio.paragraphs).joinToString(" "))
    }

    @Test
    fun `abreviacoes e numeros nao quebram a frase`() {
        val bio = splitBio("O Dr. Silva correu em 1.5 mil voltas pela Lotus. Depois foi para a Ferrari.")
        assertEquals("O Dr. Silva correu em 1.5 mil voltas pela Lotus.", bio.lead)
        assertEquals(listOf("Depois foi para a Ferrari."), bio.paragraphs)
    }

    @Test
    fun `texto de uma frase so fica sem paragrafos e vazio nao quebra`() {
        assertEquals(BioText("Uma frase so.", emptyList()), splitBio("  Uma frase so.  "))
        assertEquals(BioText("", emptyList()), splitBio("   "))
    }
}
