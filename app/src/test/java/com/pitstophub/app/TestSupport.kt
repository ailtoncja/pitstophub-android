package com.pitstophub.app

import com.pitstophub.app.data.remote.HttpFetcher
import com.pitstophub.app.data.repo.KeyValueStore
import okhttp3.Dispatcher
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit

/** Executor que roda na hora, na propria thread: sem ele o OkHttp responderia em outra thread, fora do tempo virtual do teste. */
private object DirectExecutorService : AbstractExecutorService() {
    override fun execute(command: Runnable) = command.run()
    override fun shutdown() = Unit
    override fun shutdownNow(): MutableList<Runnable> = mutableListOf()
    override fun isShutdown() = false
    override fun isTerminated() = false
    override fun awaitTermination(timeout: Long, unit: TimeUnit) = true
}

/** Rede falsa: cada requisicao e respondida por [handler] (codigo + corpo), sem sair do processo. */
class FakeHttp(private val handler: (Request) -> Pair<Int, String>) {
    val requests = mutableListOf<Request>()
    val bodies = mutableListOf<String>()

    val fetcher = HttpFetcher(
        OkHttpClient.Builder().dispatcher(Dispatcher(DirectExecutorService)).addInterceptor { chain ->
            val request = chain.request()
            requests += request
            bodies += request.body?.let { body -> Buffer().also { body.writeTo(it) }.readUtf8() }.orEmpty()
            val (code, body) = handler(request)
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("fake")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }.build(),
    )
}

class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String): String? = values[key]
    override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
}
