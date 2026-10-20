package com.pitstophub.app.data.repo

import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.remote.AuthError
import com.pitstophub.app.data.remote.AuthResult
import com.pitstophub.app.data.remote.AuthSession
import com.pitstophub.app.data.remote.AuthUser
import com.pitstophub.app.data.remote.SupabaseAuthClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.time.Instant

/**
 * Guarda a sessao cifrada (AES-GCM, chave no Android Keystore) no armazenamento privado do app (sem backup:
 * `allowBackup=false`). Versoes antigas gravavam o JSON puro em [LEGACY_KEY]: na primeira leitura ele e cifrado de novo
 * e apagado.
 */
class SessionStore(private val store: KeyValueStore, private val cipher: SessionCipher) {

    fun load(): AuthSession? {
        val sealed = store.getString(KEY)
        if (sealed != null) {
            val session = runCatching { AppJson.decodeFromString<AuthSession>(cipher.decrypt(sealed)) }.getOrNull()
            if (session != null) {
                store.putString(LEGACY_KEY, null)
                return session
            }
            // Chave invalidada/apagada ou dado corrompido: descarta tudo e a pessoa entra de novo.
            clear()
            cipher.reset()
            return null
        }
        val legacy = store.getString(LEGACY_KEY) ?: return null
        val session = runCatching { AppJson.decodeFromString<AuthSession>(legacy) }.getOrNull()
        // save() apaga o texto puro mesmo se a cifragem falhar (a sessao fica so na memoria ate fechar o app).
        save(session)
        return session
    }

    fun save(session: AuthSession?) {
        if (session == null) return clear()
        val plain = AppJson.encodeToString(session)
        val sealed = runCatching { cipher.encrypt(plain) }.recoverCatching {
            // Chave invalidada: cria outra e tenta uma vez mais.
            cipher.reset()
            cipher.encrypt(plain)
        }.getOrNull()
        // Sem cifra nao grava nada: nunca volta a salvar o token em texto puro.
        store.putString(KEY, sealed)
        store.putString(LEGACY_KEY, null)
    }

    private fun clear() {
        store.putString(KEY, null)
        store.putString(LEGACY_KEY, null)
    }

    internal companion object {
        const val KEY = "auth_session_enc"
        const val LEGACY_KEY = "auth_session"
    }
}

const val MIN_PASSWORD_LENGTH = 10

/**
 * Login, cadastro e renovacao de sessao (mesmas regras de `src/auth.ts` do PWA). Quem precisa de token chama
 * [accessToken], que renova sozinho quando faltam menos de 60 s.
 */
class AuthRepository(
    private val client: SupabaseAuthClient,
    private val store: SessionStore,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val _session = MutableStateFlow(store.load())
    val session: StateFlow<AuthSession?> = _session.asStateFlow()

    private val refreshLock = Mutex()

    val configured: Boolean get() = client.enabled
    val currentUser: AuthUser? get() = _session.value?.user

    suspend fun login(email: String, password: String): AuthResult {
        val cleanEmail = email.trim().lowercase()
        if ('@' !in cleanEmail || cleanEmail.length < 5) return AuthResult.Failure(AuthError.INVALID_EMAIL)
        if (password.isEmpty()) return AuthResult.Failure(AuthError.INVALID_PASSWORD)
        return client.signIn(cleanEmail, password).also(::remember)
    }

    suspend fun register(name: String, email: String, password: String): AuthResult {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        if (cleanName.length < 2) return AuthResult.Failure(AuthError.INVALID_NAME)
        if ('@' !in cleanEmail || cleanEmail.length < 5) return AuthResult.Failure(AuthError.INVALID_EMAIL)
        if (password.length < MIN_PASSWORD_LENGTH) return AuthResult.Failure(AuthError.INVALID_PASSWORD)
        return client.signUp(cleanName, cleanEmail, password).also(::remember)
    }

    suspend fun logout() {
        val token = _session.value?.accessToken
        setSession(null)
        if (token != null) client.signOut(token)
    }

    /** Token de acesso valido (renovando se preciso) ou null sem sessao. Sessao revogada vira deslogado. */
    suspend fun accessToken(): String? {
        val current = _session.value ?: return null
        if (!current.expiresWithin(60, clock())) return current.accessToken
        return refreshLock.withLock {
            // Outra coroutine pode ter renovado enquanto esperavamos o lock.
            val latest = _session.value ?: return@withLock null
            if (!latest.expiresWithin(60, clock())) return@withLock latest.accessToken
            when (val result = client.refresh(latest.refreshToken)) {
                is AuthResult.Authenticated -> {
                    setSession(result.session.copy(name = result.session.name.ifBlank { latest.name }))
                    result.session.accessToken
                }
                is AuthResult.Failure -> {
                    // Token revogado/expirado: a conta saiu. Falha de rede mantem a sessao e tenta depois.
                    if (result.error == AuthError.INVALID_CREDENTIALS) setSession(null)
                    null
                }
                AuthResult.PendingVerification -> null
            }
        }
    }

    private fun remember(result: AuthResult) {
        if (result is AuthResult.Authenticated) setSession(result.session)
    }

    private fun setSession(session: AuthSession?) {
        store.save(session)
        _session.value = session
    }
}
