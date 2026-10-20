package com.pitstophub.app.data.repo

import com.pitstophub.app.FakeHttp
import com.pitstophub.app.MemoryStore
import com.pitstophub.app.TestCipher
import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.remote.AuthSession
import com.pitstophub.app.data.remote.SupabaseAuthClient
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Base64
import javax.crypto.KeyGenerator

private val SESSION = AuthSession(
    accessToken = "jwt-secreto",
    refreshToken = "refresh-secreto",
    expiresAt = 4102444800L,
    userId = "u-1",
    email = "ailton@example.com",
    name = "Ailton",
    createdAt = "2026-01-01T00:00:00Z",
)

class SessionStoreTest {

    @Test
    fun `sessao vai cifrada para o disco e volta igual`() {
        val store = MemoryStore()
        val cipher = TestCipher()
        SessionStore(store, cipher).save(SESSION)

        val sealed = store.values.getValue(SessionStore.KEY)
        assertFalse(sealed.contains("jwt-secreto"))
        assertFalse(sealed.contains("refresh-secreto"))
        assertFalse(store.values.containsKey(SessionStore.LEGACY_KEY))
        assertEquals(SESSION, SessionStore(store, cipher).load())
    }

    @Test
    fun `sessao em texto puro de versao antiga e cifrada e apagada na primeira leitura`() {
        val store = MemoryStore()
        val cipher = TestCipher()
        store.values[SessionStore.LEGACY_KEY] = AppJson.encodeToString(SESSION)

        assertEquals(SESSION, SessionStore(store, cipher).load())
        assertFalse(store.values.containsKey(SessionStore.LEGACY_KEY))
        assertFalse(store.values.getValue(SessionStore.KEY).contains("jwt-secreto"))
        // Ao reabrir, vem da versao cifrada.
        assertEquals(SESSION, SessionStore(store, cipher).load())
    }

    @Test
    fun `texto puro corrompido e descartado`() {
        val store = MemoryStore()
        store.values[SessionStore.LEGACY_KEY] = "{nao e json"
        assertNull(SessionStore(store, TestCipher()).load())
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `chave invalidada limpa a sessao e troca a chave`() {
        val store = MemoryStore()
        val cipher = TestCipher()
        SessionStore(store, cipher).save(SESSION)

        cipher.broken = true
        assertNull(SessionStore(store, cipher).load())
        assertTrue(store.values.isEmpty())
        assertEquals(1, cipher.resets)

        // Com a chave nova, o proximo login volta a gravar normalmente.
        cipher.broken = false
        SessionStore(store, cipher).save(SESSION)
        assertEquals(SESSION, SessionStore(store, cipher).load())
    }

    @Test
    fun `sessao cifrada com outra chave ou adulterada e descartada`() {
        val store = MemoryStore()
        SessionStore(store, TestCipher()).save(SESSION)
        // Outra instancia = outra chave (como o Keystore recriado depois de apagado).
        assertNull(SessionStore(store, TestCipher()).load())
        assertTrue(store.values.isEmpty())

        val cipher = TestCipher()
        SessionStore(store, cipher).save(SESSION)
        val bytes = Base64.getDecoder().decode(store.values.getValue(SessionStore.KEY))
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
        store.values[SessionStore.KEY] = Base64.getEncoder().encodeToString(bytes)
        assertNull(SessionStore(store, cipher).load())
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `sem cifra funcionando nada e gravado em texto puro`() {
        val store = MemoryStore()
        store.values[SessionStore.LEGACY_KEY] = AppJson.encodeToString(SESSION)
        val cipher = TestCipher().apply { broken = true }

        // A migracao devolve a sessao (fica so na memoria), mas apaga o texto puro.
        assertEquals(SESSION, SessionStore(store, cipher).load())
        assertTrue(store.values.isEmpty())

        SessionStore(store, cipher).save(SESSION)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `logout apaga a versao cifrada e a antiga`() {
        val store = MemoryStore()
        val sessions = SessionStore(store, TestCipher())
        sessions.save(SESSION)
        store.values[SessionStore.LEGACY_KEY] = "sobra"
        sessions.save(null)
        assertTrue(store.values.isEmpty())
    }

    @Test
    fun `app abre deslogado quando a chave foi invalidada`() {
        val store = MemoryStore()
        val cipher = TestCipher()
        SessionStore(store, cipher).save(SESSION)
        cipher.broken = true
        val http = FakeHttp { error("nao deveria chamar a rede") }
        val auth = AuthRepository(SupabaseAuthClient(http.fetcher, "https://x.supabase.co", "anon"), SessionStore(store, cipher)) {
            Instant.parse("2026-10-20T12:00:00Z")
        }
        assertNull(auth.session.value)
    }
}

class AesGcmSessionCipherTest {

    private val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    private val cipher = AesGcmSessionCipher(key = { key })

    @Test
    fun `iv novo a cada cifragem e formato iv mais texto cifrado`() {
        val first = cipher.encrypt("ola")
        val second = cipher.encrypt("ola")
        assertNotEquals(first, second)
        assertEquals("ola", cipher.decrypt(first))
        assertEquals("ola", cipher.decrypt(second))
        // 12 bytes de IV + 3 bytes de texto + 16 bytes de tag.
        assertEquals(12 + 3 + 16, Base64.getDecoder().decode(first).size)
    }

    @Test
    fun `entrada curta ou invalida lanca excecao`() {
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt("nao-e-base64!") }
        assertThrows(IllegalArgumentException::class.java) { cipher.decrypt(Base64.getEncoder().encodeToString(ByteArray(10))) }
    }
}
