package com.pitstophub.app.data.remote

import com.pitstophub.app.FakeHttp
import com.pitstophub.app.domain.UserSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private const val SESSION_JSON = """
{"access_token":"jwt-1","token_type":"bearer","expires_in":3600,"expires_at":4102444800,"refresh_token":"refresh-1",
 "user":{"id":"u-1","email":"ailton@example.com","created_at":"2026-01-02T03:04:05Z","user_metadata":{"display_name":"Ailton"}}}
"""

class SupabaseAuthClientTest {

    private fun client(http: FakeHttp, url: String = "https://x.supabase.co", key: String = "anon") = SupabaseAuthClient(http.fetcher, url, key)

    @Test
    fun `sem url ou chave o login fica desligado`() = runTest {
        val http = FakeHttp { error("nao deveria chamar a rede") }
        assertEquals(AuthResult.Failure(AuthError.NOT_CONFIGURED), client(http, key = "").signIn("a@b.com", "x"))
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `login com senha devolve a sessao e manda apikey e corpo corretos`() = runTest {
        val http = FakeHttp { 200 to SESSION_JSON }
        val result = client(http).signIn("ailton@example.com", "segredo-longo")
        val session = (result as AuthResult.Authenticated).session
        assertEquals("jwt-1", session.accessToken)
        assertEquals("refresh-1", session.refreshToken)
        assertEquals(4102444800L, session.expiresAt)
        assertEquals("u-1", session.userId)
        assertEquals("Ailton", session.name)
        val request = http.requests.single()
        assertEquals("https://x.supabase.co/auth/v1/token?grant_type=password", request.url.toString())
        assertEquals("anon", request.header("apikey"))
        assertEquals("no-store", request.header("Cache-Control")) // resposta com tokens fora do cache em disco
        assertTrue(http.bodies.single().contains("\"password\":\"segredo-longo\""))
    }

    @Test
    fun `sem nome nos metadados usa o começo do e-mail`() = runTest {
        val json = SESSION_JSON.replace("\"user_metadata\":{\"display_name\":\"Ailton\"}", "\"user_metadata\":{}")
        val session = (client(FakeHttp { 200 to json }).signIn("a@b.com", "x") as AuthResult.Authenticated).session
        assertEquals("ailton", session.name)
    }

    @Test
    fun `credenciais invalidas viram INVALID_CREDENTIALS`() = runTest {
        val http = FakeHttp { 400 to """{"error":"invalid_grant","error_description":"Invalid login credentials"}""" }
        assertEquals(AuthError.INVALID_CREDENTIALS, (client(http).signIn("a@b.com", "x") as AuthResult.Failure).error)
    }

    @Test
    fun `erros conhecidos do servidor sao classificados`() {
        fun kind(code: Int, body: String) = SupabaseAuthClient.classify(code, AppJson.parseToJsonElement(body) as kotlinx.serialization.json.JsonObject)
        assertEquals(AuthError.ALREADY_REGISTERED, kind(422, """{"code":422,"error_code":"user_already_exists","msg":"User already registered"}"""))
        assertEquals(AuthError.EMAIL_NOT_CONFIRMED, kind(400, """{"error_code":"email_not_confirmed","msg":"Email not confirmed"}"""))
        assertEquals(AuthError.WEAK_PASSWORD, kind(422, """{"error_code":"weak_password","msg":"Password should be at least 10 characters"}"""))
        assertEquals(AuthError.RATE_LIMITED, kind(429, """{"msg":"email rate limit exceeded"}"""))
        assertEquals(AuthError.UNKNOWN, kind(500, """{"msg":"boom"}"""))
    }

    @Test
    fun `cadastro com confirmacao de e-mail nao devolve sessao`() = runTest {
        val http = FakeHttp { 200 to """{"id":"u-2","email":"novo@example.com","confirmation_sent_at":"2026-01-01T00:00:00Z"}""" }
        assertEquals(AuthResult.PendingVerification, client(http).signUp("Novo", "novo@example.com", "senha-bem-longa"))
        assertTrue(http.bodies.single().contains("\"display_name\":\"Novo\""))
    }

    @Test
    fun `falha de rede vira NETWORK`() = runTest {
        val http = FakeHttp { throw IOException("sem sinal") }
        assertEquals(AuthError.NETWORK, (client(http).signIn("a@b.com", "x") as AuthResult.Failure).error)
    }

    @Test
    fun `renovar usa o token de renovacao`() = runTest {
        val http = FakeHttp { 200 to SESSION_JSON }
        client(http).refresh("refresh-0")
        assertEquals("https://x.supabase.co/auth/v1/token?grant_type=refresh_token", http.requests.single().url.toString())
        assertTrue(http.bodies.single().contains("\"refresh_token\":\"refresh-0\""))
    }
}

class SupabaseSettingsClientTest {

    private fun client(http: FakeHttp) = SupabaseSettingsClient(http.fetcher, "https://x.supabase.co", "anon")

    @Test
    fun `le a linha da conta com o token do usuario`() = runTest {
        val row = """[{"language":"en","favorite_category_id":"wec","followed_category_ids":["f1"],"followed_team_ids":["f1::ferrari"],
            "followed_driver_ids":["f1::norris"],"favorites_onboarded":true,"priority_follow_ids":["team::f1::ferrari","driver::f1::norris"],
            "notify_race_day":false,"notify_t60":true,"notify_start":true,"notify_results":false,"timezone":"Europe/Lisbon"}]"""
        val http = FakeHttp { 200 to row }
        val settings = client(http).fetch("u-1", "jwt-1")!!
        assertEquals("en", settings.language)
        assertEquals("wec", settings.favoriteCategoryId)
        assertEquals(listOf("f1::norris"), settings.followedDriverIds)
        assertEquals(listOf("team::f1::ferrari", "driver::f1::norris"), settings.priorityFollowIds)
        assertEquals(false, settings.notifyRaceDay)
        assertEquals(false, settings.notifyResults)
        assertEquals("Europe/Lisbon", settings.timezone)
        val request = http.requests.single()
        assertEquals("Bearer jwt-1", request.header("Authorization"))
        assertEquals("no-store", request.header("Cache-Control"))
        assertTrue(request.url.toString().contains("user_id=eq.u-1"))
    }

    @Test
    fun `conta sem linha devolve null`() = runTest {
        assertNull(client(FakeHttp { 200 to "[]" }).fetch("u-1", "jwt"))
    }

    @Test
    fun `banco antigo sem colunas novas cai para a lista menor`() = runTest {
        val http = FakeHttp { request ->
            if ("notify_race_day" in request.url.toString()) 400 to """{"code":"42703"}"""
            else if ("favorites_onboarded" in request.url.toString()) 400 to """{"code":"42703"}"""
            else 200 to """[{"language":"pt","favorite_category_id":"f1","followed_category_ids":[],"followed_team_ids":[],"followed_driver_ids":[]}]"""
        }
        val settings = client(http).fetch("u-1", "jwt")!!
        assertEquals(3, http.requests.size)
        assertEquals(true, settings.notifyT60) // padrao quando a coluna nao existe
        assertEquals(false, settings.favoritesOnboarded)
    }

    @Test
    fun `salvar tenta completo, depois favoritos, depois o basico`() = runTest {
        var calls = 0
        val http = FakeHttp { if (++calls < 3) 400 to "{}" else 201 to "" }
        client(http).save("u-1", "jwt", UserSettings(followedDriverIds = listOf("f1::norris")))
        assertEquals(3, http.requests.size)
        assertTrue(http.bodies[0].contains("notify_race_day"))
        assertTrue(http.bodies[1].contains("priority_follow_ids") && !http.bodies[1].contains("notify_race_day"))
        assertTrue(!http.bodies[2].contains("priority_follow_ids"))
        assertEquals("resolution=merge-duplicates,return=minimal", http.requests[0].header("Prefer"))
    }
}
