package com.pitstophub.app.data.remote

import com.pitstophub.app.domain.UserSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant

/**
 * Leitura/gravacao da tabela `user_settings` (PostgREST) com o token do usuario; a RLS deixa cada pessoa ver e
 * alterar so a propria linha. As colunas novas (favoritos, avisos) dependem das migracoes SQL da pasta
 * `supabase` do PWA, entao cada operacao tem o mesmo recuo do site: completo -> favoritos -> legado.
 */
class SupabaseSettingsClient(
    private val http: HttpFetcher,
    private val baseUrl: String,
    private val anonKey: String,
) {
    val enabled: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private val table: String get() = baseUrl.trimEnd('/') + "/rest/v1/user_settings"

    private fun headers(token: String, extra: Map<String, String> = emptyMap()) =
        mapOf("apikey" to anonKey, "Authorization" to "Bearer $token", "Content-Type" to "application/json") + extra

    /** Configuracoes salvas, ou null se a pessoa ainda nao tem linha (conta nova). Lanca em falha de rede. */
    suspend fun fetch(userId: String, token: String): UserSettings? {
        for (columns in SELECTS) {
            val result = http.execute("GET", "$table?select=$columns&user_id=eq.$userId", headers(token))
            if (!result.ok) continue // coluna inexistente: tenta a lista menor
            val rows = AppJson.parseToJsonElement(result.body).jsonArray
            return rows.firstOrNull()?.jsonObject?.let(::parse)
        }
        throw IOException("Nao foi possivel ler user_settings")
    }

    suspend fun save(userId: String, token: String, settings: UserSettings) {
        val now = Instant.now().toString()
        val base = buildJsonObject {
            put("user_id", userId)
            put("theme", "dark")
            put("language", settings.language)
            put("favorite_category_id", settings.favoriteCategoryId)
            put("followed_category_ids", strings(settings.followedCategoryIds))
            put("followed_team_ids", strings(settings.followedTeamIds))
            put("followed_driver_ids", strings(settings.followedDriverIds))
            put("updated_at", now)
        }
        val favorites = JsonObject(base + mapOf(
            "favorites_onboarded" to JsonPrimitive(settings.favoritesOnboarded),
            "priority_follow_ids" to strings(settings.priorityFollowIds),
        ))
        val full = JsonObject(favorites + mapOf(
            "notify_race_day" to JsonPrimitive(settings.notifyRaceDay),
            "notify_t60" to JsonPrimitive(settings.notifyT60),
            "notify_start" to JsonPrimitive(settings.notifyStart),
            "notify_results" to JsonPrimitive(settings.notifyResults),
            "timezone" to JsonPrimitive(settings.timezone),
        ))
        var lastCode = 0
        for (payload in listOf(full, favorites, base)) {
            val result = http.execute(
                "POST",
                "$table?on_conflict=user_id",
                headers(token, mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")),
                payload.toString(),
            )
            if (result.ok) return
            lastCode = result.code
        }
        throw IOException("HTTP $lastCode ao salvar user_settings")
    }

    /** Cria a linha padrao da conta nova sem sobrescrever uma existente (`ignoreDuplicates` do PWA). */
    suspend fun ensureRow(userId: String, token: String, language: String) {
        val payload = buildJsonObject {
            put("user_id", userId)
            put("theme", "dark")
            put("language", language)
            put("favorite_category_id", "f1")
            put("followed_category_ids", strings(emptyList()))
            put("followed_team_ids", strings(emptyList()))
            put("followed_driver_ids", strings(emptyList()))
            put("updated_at", Instant.now().toString())
        }
        try {
            http.execute(
                "POST",
                "$table?on_conflict=user_id",
                headers(token, mapOf("Prefer" to "resolution=ignore-duplicates,return=minimal")),
                payload.toString(),
            )
        } catch (_: IOException) {
            // Sera criada no primeiro salvamento.
        }
    }

    internal companion object {
        private const val LEGACY = "language,favorite_category_id,followed_category_ids,followed_team_ids,followed_driver_ids"
        private const val FAVORITES = "$LEGACY,favorites_onboarded,priority_follow_ids"
        private const val FULL = "$FAVORITES,notify_race_day,notify_t60,notify_start,notify_results,timezone"
        val SELECTS = listOf(FULL, FAVORITES, LEGACY)

        private fun strings(values: List<String>): JsonArray = buildJsonArray { values.forEach { add(JsonPrimitive(it)) } }

        private fun JsonObject.strings(key: String): List<String> =
            (this[key] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()

        private fun JsonObject.flag(key: String, default: Boolean): Boolean =
            (this[key] as? JsonPrimitive)?.booleanOrNull ?: default

        fun parse(row: JsonObject): UserSettings = UserSettings(
            language = if (row["language"]?.jsonPrimitive?.contentOrNull == "en") "en" else "pt",
            favoriteCategoryId = row["favorite_category_id"]?.jsonPrimitive?.contentOrNull ?: "f1",
            followedCategoryIds = row.strings("followed_category_ids"),
            followedTeamIds = row.strings("followed_team_ids"),
            followedDriverIds = row.strings("followed_driver_ids"),
            favoritesOnboarded = row.flag("favorites_onboarded", false),
            priorityFollowIds = row.strings("priority_follow_ids"),
            notifyRaceDay = row.flag("notify_race_day", true),
            notifyT60 = row.flag("notify_t60", true),
            notifyStart = row.flag("notify_start", true),
            notifyResults = row.flag("notify_results", true),
            timezone = row["timezone"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: UserSettings.DEFAULT_TIMEZONE,
        )
    }
}
