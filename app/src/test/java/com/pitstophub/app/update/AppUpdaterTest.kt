package com.pitstophub.app.update

import com.pitstophub.app.FakeHttp
import com.pitstophub.app.MemoryStore
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

private const val CERT = "33c7b66edd406749a58e5484c73c648575f388fa9da97c64bbdda5f0d962bf69"
private const val APK_URL = "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk"
private const val ASSET_URL = "https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sig=a%2Fb%3D"

/** Servidor de arquivos falso: cada URL responde com redirecionamento (Location) ou com bytes. */
private class FakeFiles(private val handler: (Request) -> Response.Builder) {
    val requested = mutableListOf<String>()

    val client: OkHttpClient = OkHttpClient.Builder()
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor { chain ->
            val request = chain.request()
            requested += request.url.toString()
            handler(request).request(request).protocol(Protocol.HTTP_1_1).message("fake").build()
        }
        .build()
}

private fun redirect(to: String) = Response.Builder().code(302).header("Location", to).body(ByteArray(0).toResponseBody(null))

private fun bytes(content: ByteArray) = Response.Builder().code(200).body(content.toResponseBody("application/octet-stream".toMediaType()))

private class FakeSigners(var installed: Set<String> = setOf(CERT), var archive: Set<String> = setOf(CERT)) : SignerSource {
    override fun installed() = installed
    override fun archive(apk: File) = archive
}

class AppUpdaterTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val apkBytes = ByteArray(4096) { (it % 251).toByte() }
    private val apkSha = UpdateRules.sha256Hex(apkBytes)

    private fun release(sha256: String = apkSha) =
        ReleaseInfo(tag = "v1.3.2", version = "1.3.2", apkName = "pitstophub-1.3.2.apk", apkUrl = APK_URL, size = apkBytes.size.toLong(), sha256 = sha256)

    /** Caminho feliz: link do Release -> 302 para o servidor de arquivos -> bytes do APK. */
    private fun githubFiles() = FakeFiles { request ->
        when (request.url.toString()) {
            APK_URL -> redirect(ASSET_URL)
            ASSET_URL -> bytes(apkBytes)
            else -> error("URL inesperada: ${request.url}")
        }
    }

    private fun updater(
        http: FakeHttp = FakeHttp { 200 to releaseJson(digest = "sha256:$apkSha", body = "notas") },
        files: FakeFiles = githubFiles(),
        signers: SignerSource = FakeSigners(),
        store: MemoryStore = MemoryStore(),
        dir: File = File(temp.root, "updates"),
        installed: String = "1.3.1",
        now: () -> Long = { 1_000_000_000L },
    ) = AppUpdater(http.fetcher, ApkDownloader(files.client), signers, store, dir, installed, now)

    // --- conferir ----------------------------------------------------------------------------------

    @Test
    fun `versao maior no GitHub vira oferta de atualizacao`() = runTest {
        val http = FakeHttp { 200 to releaseJson(digest = "sha256:$apkSha", body = "notas") }
        val release = updater(http = http).checkIfDue()
        assertEquals("1.3.2", release?.version)
        assertEquals(apkSha, release?.sha256)
        val request = http.requests.single()
        assertEquals(UpdateRules.LATEST_RELEASE_URL, request.url.toString())
        assertEquals("application/vnd.github+json", request.header("Accept"))
    }

    @Test
    fun `mesma versao ou mais antiga nao oferece nada`() = runTest {
        assertNull(updater(installed = "1.3.2").checkIfDue())
        assertNull(updater(installed = "1.4.0").checkIfDue())
    }

    @Test
    fun `confere no maximo a cada 6 horas`() = runTest {
        val http = FakeHttp { 200 to releaseJson(digest = "sha256:$apkSha", body = "notas") }
        val store = MemoryStore()
        var clock = 1_000_000_000L
        val updater = updater(http = http, store = store, now = { clock })

        assertNotNull(updater.checkIfDue())
        clock += UpdateRules.CHECK_INTERVAL_MS - 1
        // Dentro das 6 horas nao vai a rede, mas a versao achada continua valendo (app fechado no meio da atualizacao).
        assertEquals("1.3.2", updater.checkIfDue()?.version)
        assertEquals(1, http.requests.size)

        clock += 1
        assertNotNull(updater.checkIfDue())
        assertEquals(2, http.requests.size)
    }

    @Test
    fun `agora nao cala ate a proxima consulta`() = runTest {
        val http = FakeHttp { 200 to releaseJson(digest = "sha256:$apkSha", body = "notas") }
        var clock = 1_000_000_000L
        val updater = updater(http = http, now = { clock })

        assertNotNull(updater.checkIfDue())
        updater.dismiss()
        clock += 60_000
        assertNull(updater.checkIfDue())
        assertEquals(1, http.requests.size)

        clock += UpdateRules.CHECK_INTERVAL_MS
        assertNotNull(updater.checkIfDue())
    }

    @Test
    fun `versao pendente some depois de instalada`() = runTest {
        val store = MemoryStore()
        assertNotNull(updater(store = store, installed = "1.3.1").checkIfDue())
        // Mesmo store, app ja na 1.3.2 e dentro das 6 horas: nada a oferecer.
        assertNull(updater(store = store, installed = "1.3.2", http = FakeHttp { error("nao deveria consultar") }).checkIfDue())
        assertTrue(store.values.keys.none { it.contains("pending") })
    }

    @Test
    fun `GitHub sem versao nova limpa a pendente`() = runTest {
        val store = MemoryStore()
        var clock = 1_000_000_000L
        assertNotNull(updater(store = store, now = { clock }).checkIfDue())
        clock += UpdateRules.CHECK_INTERVAL_MS
        val sameVersion = FakeHttp { 200 to releaseJson(tag = "v1.3.1", name = "pitstophub-1.3.1.apk", digest = "sha256:$apkSha") }
        assertNull(updater(store = store, http = sameVersion, now = { clock }).checkIfDue())
    }

    @Test
    fun `sem rede ou com erro do GitHub fica calado`() = runTest {
        assertNull(updater(http = FakeHttp { throw IOException("sem rede") }).checkIfDue())
        assertNull(updater(http = FakeHttp { 403 to """{"message":"API rate limit exceeded"}""" }).checkIfDue())
        assertNull(updater(http = FakeHttp { 200 to "<html>" }).checkIfDue())
    }

    // --- baixar e conferir -------------------------------------------------------------------------

    @Test
    fun `baixa pelo redirecionamento do GitHub e confere hash e assinatura`() = runTest {
        val files = githubFiles()
        val result = updater(files = files).download(release())
        val apk = (result as DownloadResult.Ready).apk
        assertTrue(apk.exists())
        assertEquals(apkSha, UpdateRules.sha256Hex(apk))
        assertEquals(listOf(APK_URL, ASSET_URL), files.requested)
        // Nada de arquivo parcial sobrando.
        assertEquals(listOf(apk.name), apk.parentFile!!.list()!!.toList())
    }

    @Test
    fun `redirecionamento para fora do GitHub e recusado`() = runTest {
        val dir = File(temp.root, "updates")
        val files = FakeFiles { request ->
            when (request.url.toString()) {
                APK_URL -> redirect("https://evil.example.com/pitstophub.apk")
                else -> error("nao deveria seguir para ${request.url}")
            }
        }
        assertEquals(DownloadResult.Failed(rejected = true), updater(files = files, dir = dir).download(release()))
        assertEquals(listOf(APK_URL), files.requested)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `hash diferente do publicado e recusado e o arquivo apagado`() = runTest {
        val dir = File(temp.root, "updates")
        val result = updater(dir = dir).download(release(sha256 = "0".repeat(64)))
        assertEquals(DownloadResult.Failed(rejected = true), result)
        assertTrue(dir.listFiles().isNullOrEmpty())
    }

    @Test
    fun `APK assinado por outra chave e recusado`() = runTest {
        val dir = File(temp.root, "updates")
        val signers = FakeSigners(archive = setOf("f".repeat(64)))
        assertEquals(DownloadResult.Failed(rejected = true), updater(signers = signers, dir = dir).download(release()))
        assertTrue(dir.listFiles().isNullOrEmpty())

        // Sem conseguir ler a assinatura do APK tambem nao instala.
        assertEquals(DownloadResult.Failed(rejected = true), updater(signers = FakeSigners(archive = emptySet())).download(release()))
    }

    @Test
    fun `falha de rede no download nao quebra`() = runTest {
        val files = FakeFiles { throw IOException("caiu") }
        assertEquals(DownloadResult.Failed(rejected = false), updater(files = files).download(release()))
        val notFound = FakeFiles { Response.Builder().code(404).body(ByteArray(0).toResponseBody(null)) }
        assertEquals(DownloadResult.Failed(rejected = false), updater(files = notFound).download(release()))
    }

    @Test
    fun `link fora do repositorio nem chega a rede`() = runTest {
        val files = FakeFiles { error("nao deveria baixar") }
        val evil = release().copy(apkUrl = "https://evil.example.com/pitstophub-1.3.2.apk")
        assertEquals(DownloadResult.Failed(rejected = true), updater(files = files).download(evil))
        assertTrue(files.requested.isEmpty())
    }

    @Test
    fun `respeita o limite de tamanho`() = runTest {
        val target = File(temp.root, "x.apk")
        val downloader = ApkDownloader(githubFiles().client)
        try {
            downloader.download(APK_URL, target, maxBytes = apkBytes.size - 1L)
            fail("deveria recusar")
        } catch (_: UpdateRejected) {
        }
        assertFalse(target.exists())
        assertFalse(File(temp.root, "x.apk.part").exists())
    }

    @Test
    fun `limpeza apaga os APKs baixados`() = runTest {
        val dir = File(temp.root, "updates")
        val updater = updater(dir = dir)
        assertTrue(updater.download(release()) is DownloadResult.Ready)
        updater.cleanup()
        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
