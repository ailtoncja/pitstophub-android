package com.pitstophub.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant

data class AuthUser(val id: String, val name: String, val email: String, val createdAt: String)

/** Sessao do Supabase (GoTrue): o token de acesso dura ~1 h e o de renovacao troca por um novo par. */
@Serializable
data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    /** Epoch em segundos em que o token de acesso expira. */
    val expiresAt: Long,
    val userId: String,
    val email: String,
    val name: String,
    val createdAt: String,
) {
    val user: AuthUser get() = AuthUser(userId, name, email, createdAt)

    fun expiresWithin(seconds: Long, now: Instant): Boolean = expiresAt - now.epochSecond <= seconds
}

/** Motivos de falha que a interface traduz; o texto cru do servidor so entra em [AuthResult.Failure.detail]. */
enum class AuthError {
    NOT_CONFIGURED,
    INVALID_NAME,
    INVALID_EMAIL,
    INVALID_PASSWORD,
    INVALID_CREDENTIALS,
    EMAIL_NOT_CONFIRMED,
    ALREADY_REGISTERED,
    WEAK_PASSWORD,
    RATE_LIMITED,
    NETWORK,
    UNKNOWN,
}

sealed interface AuthResult {
    data class Authenticated(val session: AuthSession) : AuthResult

    /** Conta criada, mas o projeto exige confirmar o e-mail antes do primeiro login. */
    data object PendingVerification : AuthResult

    data class Failure(val error: AuthError, val detail: String? = null) : AuthResult
}

/**
 * Cliente da API REST de autenticacao do Supabase (GoTrue), a mesma que o `supabase-js` do PWA usa. Sem URL e
 * anon key configuradas no `local.properties`, fica desligado.
 */
class SupabaseAuthClient(
    private val http: HttpFetcher,
    private val baseUrl: String,
    private val anonKey: String,
) {
    val enabled: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private val root: String get() = baseUrl.trimEnd('/') + "/auth/v1"
    private val headers: Map<String, String> get() = mapOf("apikey" to anonKey, "Authorization" to "Bearer $anonKey")

    suspend fun signIn(email: String, password: String): AuthResult {
        if (!enabled) return AuthResult.Failure(AuthError.NOT_CONFIGURED)
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }.toString()
        return call("$root/token?grant_type=password", body)
    }

    suspend fun signUp(name: String, email: String, password: String): AuthResult {
        if (!enabled) return AuthResult.Failure(AuthError.NOT_CONFIGURED)
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", buildJsonObject { put("display_name", name) })
        }.toString()
        return call("$root/signup", body)
    }

    /** Troca o token de renovacao por um novo par; devolve [AuthError.INVALID_CREDENTIALS] se ele foi revogado. */
    suspend fun refresh(refreshToken: String): AuthResult {
        if (!enabled) return AuthResult.Failure(AuthError.NOT_CONFIGURED)
        val body = buildJsonObject { put("refresh_token", refreshToken) }.toString()
        return call("$root/token?grant_type=refresh_token", body)
    }

    suspend fun signOut(accessToken: String) {
        if (!enabled) return
        try {
            http.execute("POST", "$root/logout", mapOf("apikey" to anonKey, "Authorization" to "Bearer $accessToken"))
        } catch (_: IOException) {
            // Sem rede, a sessao local e descartada mesmo assim; o token expira sozinho no servidor.
        }
    }

    private suspend fun call(url: String, body: String): AuthResult {
        val result = try {
            http.execute("POST", url, headers, body)
        } catch (e: IOException) {
            return AuthResult.Failure(AuthError.NETWORK, e.message)
        }
        val json = runCatching { AppJson.parseToJsonElement(result.body).jsonObject }.getOrNull()
        if (!result.ok || json == null) return AuthResult.Failure(classify(result.code, json), errorDetail(json) ?: result.body.take(200))
        return parseSuccess(json)
    }

    internal companion object {
        fun parseSuccess(json: JsonObject): AuthResult {
            val access = json["access_token"]?.jsonPrimitive?.contentOrNull
            val refresh = json["refresh_token"]?.jsonPrimitive?.contentOrNull
            val userJson = json["user"] as? JsonObject
            if (access == null || refresh == null) {
                // Confirmacao de e-mail ligada: o signup devolve so o usuario, sem sessao.
                return if (json["id"] != null || userJson != null) AuthResult.PendingVerification else AuthResult.Failure(AuthError.UNKNOWN)
            }
            val user = userJson ?: json
            val email = user["email"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val metadata = user["user_metadata"] as? JsonObject
            val displayName = (metadata?.get("display_name") as? JsonPrimitive)?.contentOrNull.orEmpty()
            val expiresAt = json["expires_at"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                ?: (Instant.now().epochSecond + (json["expires_in"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 3600L))
            return AuthResult.Authenticated(
                AuthSession(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresAt = expiresAt,
                    userId = user["id"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    email = email,
                    name = displayName.ifBlank { email.substringBefore('@').ifBlank { "Piloto" } },
                    createdAt = user["created_at"]?.jsonPrimitive?.contentOrNull ?: Instant.now().toString(),
                ),
            )
        }

        fun errorDetail(json: JsonObject?): String? = listOf("msg", "message", "error_description", "error")
            .firstNotNullOfOrNull { key -> json?.get(key)?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } }

        fun classify(code: Int, json: JsonObject?): AuthError {
            val errorCode = json?.get("error_code")?.jsonPrimitive?.contentOrNull.orEmpty().lowercase()
            val text = errorDetail(json).orEmpty().lowercase()
            return when {
                code == 429 || "rate_limit" in errorCode || "rate limit" in text || "security purposes" in text || "too many requests" in text ->
                    AuthError.RATE_LIMITED
                errorCode == "user_already_exists" || "already registered" in text -> AuthError.ALREADY_REGISTERED
                errorCode == "email_not_confirmed" || "email not confirmed" in text -> AuthError.EMAIL_NOT_CONFIRMED
                errorCode == "invalid_credentials" || "invalid login credentials" in text || "invalid_grant" in text ||
                    "refresh token" in text -> AuthError.INVALID_CREDENTIALS
                errorCode == "weak_password" || "password should be at least" in text || ("password" in text && "weak" in text) ->
                    AuthError.WEAK_PASSWORD
                else -> AuthError.UNKNOWN
            }
        }
    }
}
