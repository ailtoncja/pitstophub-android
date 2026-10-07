package com.pitstophub.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UserSettingsTest {

    @Test
    fun `seguir e deixar de seguir alterna a chave categoria dois pontos id`() {
        val followed = UserSettings().toggleDriver("f1", "norris").toggleTeam("f1", "mclaren").toggleCategory("wec")
        assertTrue(followed.followsDriver("f1", "norris"))
        assertTrue(followed.followsTeam("f1", "mclaren"))
        assertTrue(followed.followsCategory("wec"))
        assertEquals(listOf("f1::norris"), followed.followedDriverIds)
        assertEquals(3, followed.followCount)

        val unfollowed = followed.toggleDriver("f1", "norris")
        assertFalse(unfollowed.followsDriver("f1", "norris"))
        assertEquals(2, unfollowed.followCount)
    }

    @Test
    fun `a prioridade acompanha quem e seguido, na ordem em que foi seguido`() {
        val s = UserSettings().toggleTeam("f1", "ferrari").toggleDriver("f1", "norris").toggleDriver("wec", "fuoco")
        assertEquals(listOf("team::f1::ferrari", "driver::f1::norris", "driver::wec::fuoco"), s.priorityFollowIds)
        // deixar de seguir tira da lista sem bagunçar o resto
        assertEquals(listOf("team::f1::ferrari", "driver::wec::fuoco"), s.toggleDriver("f1", "norris").priorityFollowIds)
    }

    @Test
    fun `mover prioridade troca de lugar e respeita os limites`() {
        val s = UserSettings().toggleTeam("f1", "a").toggleTeam("f1", "b").toggleTeam("f1", "c")
        assertEquals(listOf("team::f1::b", "team::f1::a", "team::f1::c"), s.movePriority("team::f1::a", 1).priorityFollowIds)
        assertEquals(s, s.movePriority("team::f1::a", -1)) // ja esta no topo
        assertEquals(s, s.movePriority("team::f1::c", 1)) // ja esta no fim
        assertEquals(s, s.movePriority("team::f1::zzz", 1)) // nao existe
    }

    @Test
    fun `lista de prioridade vinda do servidor e saneada`() {
        val raw = UserSettings(
            followedTeamIds = listOf("f1::ferrari"),
            followedDriverIds = listOf("f1::norris"),
            priorityFollowIds = listOf("driver::f1::norris", "team::f1::fantasma", "driver::f1::norris"),
        )
        assertEquals(listOf("driver::f1::norris", "team::f1::ferrari"), raw.withReconciledPriority().priorityFollowIds)
    }

    @Test
    fun `categorias de interesse incluem as de equipes e pilotos seguidos`() {
        val s = UserSettings(followedCategoryIds = listOf("f1"), followedTeamIds = listOf("wec::ferrari"), followedDriverIds = listOf("indy::palou"))
        assertEquals(setOf("f1", "wec", "indy"), s.interestingCategoryIds())
    }

    @Test
    fun `preferencias de aviso sao independentes`() {
        val s = UserSettings().withNotify(NotifyKind.T60, false).withNotify(NotifyKind.RESULTS, false)
        assertTrue(s.wants(NotifyKind.RACE_DAY))
        assertFalse(s.wants(NotifyKind.T60))
        assertTrue(s.wants(NotifyKind.START))
        assertFalse(s.wants(NotifyKind.RESULTS))
    }
}
