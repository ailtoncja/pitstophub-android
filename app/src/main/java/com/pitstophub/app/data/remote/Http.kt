package com.pitstophub.app.data.remote

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    coerceInputValues = true
    explicitNulls = false
}

/** Resposta HTTP completa: quem chama decide o que fazer com cada codigo (login precisa do corpo do erro). */
data class HttpResult(val code: Int, val body: String) {
    val ok: Boolean get() = code in 200..299
}

/** Pequeno adaptador de OkHttp para coroutines (cancela a chamada junto com a coroutine). */
class HttpFetcher(val client: OkHttpClient) {

    suspend fun getString(url: String, headers: Map<String, String> = emptyMap()): String {
        val result = execute("GET", url, headers)
        if (!result.ok) throw IOException("HTTP ${result.code} em $url")
        return result.body
    }

    /** Qualquer metodo; so lanca em falha de rede. Corpo (quando houver) vai como JSON. */
    suspend fun execute(
        method: String,
        url: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
    ): HttpResult = suspendCancellableCoroutine { continuation ->
        val requestBody = body?.toRequestBody(JSON_MEDIA_TYPE)
            ?: if (method == "GET" || method == "HEAD") null else "".toRequestBody(null)
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .method(method, requestBody)
            .build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        val text = it.body.string()
                        if (continuation.isActive) continuation.resume(HttpResult(it.code, text))
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            }
        })
    }

    companion object {
        const val USER_AGENT = "PitStopHubAndroid/0.2 (+https://github.com/ailtoncja/pitstophub)"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
