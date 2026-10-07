package com.pitstophub.app.data.repo

import com.pitstophub.app.FakeHttp
import com.pitstophub.app.MemoryStore
import com.pitstophub.app.data.remote.AuthError
import com.pitstophub.app.data.remote.AuthResult
import com.pitstophub.app.data.remote.SupabaseAuthClient
import com.pitstophub.app.data.remote.SupabaseSettingsClient
import com.pitstophub.app.domain.NotifyKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

private fun sessionJson(access: String, refresh: String, expiresAt: Long) =
    """{"access_token":"$access","refresh_token":"$refresh","expires_at":$expiresAt,
        "user":{"id":"u-1","email":"ailton@example.com","created_at":"2026-01-01T00:00:00Z","user_metadata":{"display_name":"Ailton"}}}"""

private val NOW = Instant.parse("2026-10-04T12:00:00Z")
private val LATER = NOW.epochSecond + 3600
private val ABOUT_TO_EXPIRE = NOW.epochSecond + 20

class AuthRepositoryTest {

    private fun repository(http: FakeHttp, store: MemoryStore = MemoryStore()) =
        AuthRepository(SupabaseAuthClient(http.fetcher, "https://x.supabase.co", "anon"), SessionStore(store)) { NOW } to store

    @Test
    fun `valida os campos antes de falar com o servidor`() = runTest {
        val http = FakeHttp { error("nao deveria chamar a rede") }
        val (repo, _) = repository(http)
        assertEquals(AuthResult.Failure(AuthError.INVALID_EMAIL), repo.login("sem-arroba", "x"))
        assertEquals(AuthResult.Failure(AuthError.INVALID_NAME), repo.register("A", "a@b.com", "senha-bem-longa"))
        assertEquals(AuthResult.Failure(AuthError.INVALID_PASSWORD), repo.register("Ailton", "a@b.com", "curta"))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `login guarda a sessao e logout apaga`() = runTest {
        val http = FakeHttp { 200 to sessionJson("jwt-1", "refresh-1", LATER) }
        val (repo, store) = repository(http)
        assertNull(repo.session.value)

        repo.login("  Ailton@Example.com ", "segredo-longo")
        assertEquals("u-1", repo.session.value?.userId)
        assertTrue(store.values.containsKey("auth_session"))
        assertTrue(http.bodies.first().contains("ailton@example.com")) // e-mail normalizado

        repo.logout()
        assertNull(repo.session.value)
        assertFalse(store.values.containsKey("auth_session"))
    }

    @Test
    fun `sessao salva volta ao abrir o app`() = runTest {
        val http = FakeHttp { 200 to sessionJson("jwt-1", "refresh-1", LATER) }
        val (repo, store) = repository(http)
        repo.login("ailton@example.com", "segredo-longo")
        val reopened = AuthRepository(SupabaseAuthClient(http.fetcher, "https://x.supabase.co", "anon"), SessionStore(store)) { NOW }
        assertEquals("ailton@example.com", reopened.currentUser?.email)
    }

    @Test
    fun `token valido e reaproveitado, perto de expirar e renovado`() = runTest {
        var refreshes = 0
        val http = FakeHttp { request ->
            if ("refresh_token" in request.url.toString()) {
                refreshes++
                200 to sessionJson("jwt-2", "refresh-2", LATER)
            } else {
                200 to sessionJson("jwt-1", "refresh-1", ABOUT_TO_EXPIRE)
            }
        }
        val (repo, _) = repository(http)
        repo.login("ailton@example.com", "segredo-longo")
        assertEquals("jwt-2", repo.accessToken()) // faltam so 20 s: renova
        assertEquals(1, refreshes)
        assertEquals("jwt-2", repo.accessToken()) // agora vale 1 h: nao renova de novo
        assertEquals(1, refreshes)
    }

    @Test
    fun `token de renovacao revogado desloga, falha de rede mantem a sessao`() = runTest {
        var mode = "revoked"
        val http = FakeHttp { request ->
            if ("refresh_token" in request.url.toString()) {
                if (mode == "revoked") 400 to """{"error":"invalid_grant","error_description":"Invalid Refresh Token: Already Used"}""" else throw java.io.IOException("offline")
            } else {
                200 to sessionJson("jwt-1", "refresh-1", ABOUT_TO_EXPIRE)
            }
        }
        val (repo, _) = repository(http)
        repo.login("ailton@example.com", "segredo-longo")

        mode = "offline"
        assertNull(repo.accessToken())
        assertNotNull(repo.session.value) // sem rede nao desloga

        mode = "revoked"
        assertNull(repo.accessToken())
        assertNull(repo.session.value)
    }
}

/** Deixa os coroutines de fundo do repositorio rodarem (advanceUntilIdle ignora trabalho so de fundo) e passa o debounce. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun TestScope.settle() {
    runCurrent()
    advanceTimeBy(2_000)
    runCurrent()
}

@OptIn(ExperimentalCoroutinesApi::class)
class UserSettingsRepositoryTest {

    private val settingsRow = """[{"language":"en","favorite_category_id":"wec","followed_category_ids":["f1"],"followed_team_ids":[],
        "followed_driver_ids":["f1::norris"],"favorites_onboarded":true,"priority_follow_ids":["driver::f1::norris"],
        "notify_race_day":true,"notify_t60":true,"notify_start":true,"notify_results":true,"timezone":"America/Sao_Paulo"}]"""

    private class Rig(settingsResponse: (okhttp3.Request) -> Pair<Int, String>, scope: TestScope) {
        val http = FakeHttp { request ->
            if ("/auth/" in request.url.toString()) 200 to sessionJson("jwt-1", "refresh-1", LATER) else settingsResponse(request)
        }
        val cache = MemoryStore()
        val auth = AuthRepository(SupabaseAuthClient(http.fetcher, "https://x.supabase.co", "anon"), SessionStore(MemoryStore())) { NOW }
        var accountLanguage: String? = null
        val repo = UserSettingsRepository(
            auth = auth,
            client = SupabaseSettingsClient(http.fetcher, "https://x.supabase.co", "anon"),
            cache = cache,
            scope = scope.backgroundScope,
            effectiveLanguage = { "pt" },
            applyAccountLanguage = { accountLanguage = it },
        )
    }

    @Test
    fun `visitante usa o padrao e nao pode seguir`() = runTest {
        val rig = Rig({ 200 to "[]" }, this)
        settle()
        assertTrue(rig.repo.ready.value)
        assertEquals(FollowOutcome.NEEDS_LOGIN, rig.repo.toggleDriver("f1", "norris"))
        assertTrue(rig.repo.settings.value.followedDriverIds.isEmpty())
        assertEquals("pt", rig.repo.settings.value.language)
    }

    @Test
    fun `ao entrar carrega as configuracoes da conta e aplica o idioma`() = runTest {
        val rig = Rig({ 200 to settingsRow }, this)
        rig.auth.login("ailton@example.com", "segredo-longo")
        settle()
        assertEquals(listOf("f1::norris"), rig.repo.settings.value.followedDriverIds)
        assertEquals("wec", rig.repo.settings.value.favoriteCategoryId)
        assertEquals("en", rig.accountLanguage)
        assertTrue(rig.cache.values.containsKey("user_settings_u-1"))
        assertTrue(rig.repo.ready.value)
    }

    @Test
    fun `conta nova ganha a linha padrao e cai no onboarding`() = runTest {
        val rig = Rig({ request -> if (request.method == "GET") 200 to "[]" else 201 to "" }, this)
        rig.auth.login("ailton@example.com", "segredo-longo")
        settle()
        assertFalse(rig.repo.settings.value.favoritesOnboarded)
        assertTrue(rig.http.requests.any { it.method == "POST" && it.header("Prefer")?.contains("ignore-duplicates") == true })
    }

    @Test
    fun `seguir salva no cache na hora e no servidor depois do debounce`() = runTest {
        val rig = Rig({ request -> if (request.method == "GET") 200 to settingsRow else 201 to "" }, this)
        rig.auth.login("ailton@example.com", "segredo-longo")
        settle()

        assertEquals(FollowOutcome.FOLLOWED, rig.repo.toggleTeam("f1", "mclaren"))
        assertTrue(rig.repo.settings.value.followsTeam("f1", "mclaren"))
        assertTrue(rig.repo.settings.value.priorityFollowIds.contains("team::f1::mclaren"))
        assertTrue(rig.cache.values["user_settings_u-1"]!!.contains("mclaren"))

        settle()
        val save = rig.http.bodies.last { it.contains("followed_team_ids") }
        assertTrue(save.contains("f1::mclaren"))

        rig.repo.setNotify(NotifyKind.RESULTS, false)
        settle()
        assertTrue(rig.http.bodies.last().contains("\"notify_results\":false"))
        assertEquals(FollowOutcome.UNFOLLOWED, rig.repo.toggleTeam("f1", "mclaren"))
    }

    @Test
    fun `sair volta ao padrao de visitante`() = runTest {
        val rig = Rig({ 200 to settingsRow }, this)
        rig.auth.login("ailton@example.com", "segredo-longo")
        settle()
        rig.auth.logout()
        settle()
        assertTrue(rig.repo.settings.value.followedDriverIds.isEmpty())
    }
}
