package com.pitstophub.app.update

import com.pitstophub.app.data.remote.HttpFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/** Falha do atualizador que nao e de rede: endereco fora da lista, arquivo grande demais, hash ou assinatura errados. */
class UpdateRejected(message: String) : IOException(message)

/**
 * Baixa o APK de um Release para um arquivo. Os redirecionamentos sao seguidos a mao (o [client] vem com
 * `followRedirects(false)`) para que cada salto passe por [UpdateRules.isAllowedRedirect]; o tamanho e limitado tanto pelo
 * `Content-Length` quanto pelo que de fato chega.
 */
class ApkDownloader(private val client: OkHttpClient) {

    suspend fun download(url: String, target: File, maxBytes: Long = UpdateRules.MAX_APK_BYTES): File = withContext(Dispatchers.IO) {
        if (!UpdateRules.isAllowedApkUrl(url)) throw UpdateRejected("Endereco de APK fora do repositorio do app")
        val partial = File(target.parentFile, target.name + ".part")
        try {
            var current = url
            repeat(MAX_REDIRECTS + 1) {
                val request = Request.Builder()
                    .url(current)
                    .header("User-Agent", HttpFetcher.USER_AGENT)
                    .header("Accept", "application/octet-stream")
                    .build()
                val call = client.newCall(request)
                val cancel = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
                try {
                    call.execute().use { response ->
                        if (response.code in 300..399) {
                            val next = response.header("Location")?.let { response.request.url.resolve(it)?.toString() }
                            if (next == null || !UpdateRules.isAllowedRedirect(next)) throw UpdateRejected("Redirecionamento para fora do GitHub")
                            current = next
                            return@use
                        }
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code} ao baixar o APK")
                        val declared = response.body.contentLength()
                        if (declared > maxBytes) throw UpdateRejected("APK maior que o limite")
                        var total = 0L
                        response.body.byteStream().use { input ->
                            partial.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    if (total > maxBytes) throw UpdateRejected("APK maior que o limite")
                                    output.write(buffer, 0, read)
                                }
                            }
                        }
                        if (total == 0L) throw IOException("APK vazio")
                        target.delete()
                        if (!partial.renameTo(target)) throw IOException("Nao foi possivel guardar o APK baixado")
                        return@withContext target
                    }
                } finally {
                    cancel.dispose()
                }
            }
            throw UpdateRejected("Redirecionamentos demais")
        } finally {
            partial.delete()
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
    }
}
