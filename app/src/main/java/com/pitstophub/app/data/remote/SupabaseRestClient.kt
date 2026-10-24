package com.pitstophub.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import java.io.IOException

@Serializable
data class SyncedRaceRow(
    @SerialName("race_id") val raceId: String,
    val name: String,
    @SerialName("en_name") val enName: String? = null,
    val location: String? = null,
    @SerialName("en_location") val enLocation: String? = null,
    val circuit: String? = null,
    val date: String,
    @SerialName("starts_at") val startsAt: String? = null,
    val status: String = "upcoming",
    val winner: String? = null,
    val round: Int? = null,
)

@Serializable
data class SyncedStandingRow(
    val position: Int,
    val name: String,
    val team: String? = null,
    val points: Double,
    val season: Int? = null,
)

/**
 * Leitura publica das tabelas `synced_races` / `synced_standings` do Supabase (RLS libera `select` para
 * qualquer um). Usa a anon key, que e publica por design. Sem URL/chave configuradas, fica desligado e
 * o app usa o catalogo estatico para essas categorias.
 */
class SupabaseRestClient(
    private val http: HttpFetcher,
    private val baseUrl: String,
    private val anonKey: String,
) {
    val enabled: Boolean get() = baseUrl.isNotBlank() && anonKey.isNotBlank()

    private val headers: Map<String, String>
        get() = mapOf("apikey" to anonKey, "Authorization" to "Bearer $anonKey")

    suspend fun races(categoryId: String): List<SyncedRaceRow> {
        val base = "${baseUrl.trimEnd('/')}/rest/v1/synced_races"
        val full = "$base?select=race_id,name,en_name,location,en_location,circuit,date,starts_at,status,winner,round" +
            "&category_id=eq.$categoryId&order=round.asc"
        val body = try {
            http.getString(full, headers)
        } catch (e: IOException) {
            // Bancos antigos nao tem a coluna starts_at (o PWA tem o mesmo fallback).
            val legacy = "$base?select=race_id,name,en_name,location,en_location,circuit,date,status,winner,round" +
                "&category_id=eq.$categoryId&order=round.asc"
            http.getString(legacy, headers)
        }
        return AppJson.decodeFromString(ListSerializer(SyncedRaceRow.serializer()), body)
    }

    suspend fun standings(categoryId: String): List<SyncedStandingRow> {
        val url = "${baseUrl.trimEnd('/')}/rest/v1/synced_standings?select=position,name,team,points,season" +
            "&category_id=eq.$categoryId&order=position.asc"
        return AppJson.decodeFromString(ListSerializer(SyncedStandingRow.serializer()), http.getString(url, headers))
    }
}
