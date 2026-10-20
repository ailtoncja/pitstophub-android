package com.pitstophub.app.update

import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.remote.HttpFetcher
import com.pitstophub.app.data.repo.KeyValueStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.File

/** Resultado de baixar uma versao nova. */
sealed interface DownloadResult {
    /** APK baixado e conferido (SHA-256 e assinatura): pode ir para o instalador do Android. */
    data class Ready(val apk: File) : DownloadResult

    /** Rede caiu, hash ou assinatura nao bateram: nada fica no aparelho. */
    data class Failed(val rejected: Boolean) : DownloadResult
}

/**
 * Atualizacao do app pelos GitHub Releases (o APK nao passa pela Play Store):
 *
 * 1. [checkIfDue] consulta `releases/latest` no maximo a cada 6 horas e diz se ha versao maior que [installedVersion];
 * 2. [download] baixa o APK do Release para [dir] (cache privado), confere o SHA-256 publicado (campo `digest` do GitHub
 *    e/ou as notas do Release) e se o certificado de assinatura e o mesmo do app instalado;
 * 3. a interface entrega o arquivo ao instalador do Android (ver [ApkInstaller]), que pede confirmacao como sempre.
 *
 * Qualquer falha de rede termina calada; os APKs baixados sao apagados no proximo [cleanup] (inicio do app).
 */
class AppUpdater(
    private val http: HttpFetcher,
    private val downloader: ApkDownloader,
    private val signers: SignerSource,
    private val store: KeyValueStore,
    private val dir: File,
    private val installedVersion: String,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Versao nova a oferecer, ou `null` (esta em dia, a pessoa recusou, ou a rede falhou). A rede so e consultada a cada
     * 6 horas; no meio tempo vale a versao achada na ultima consulta, enquanto ela nao for recusada (assim a pergunta
     * volta se o app foi fechado no meio, por exemplo ao liberar "instalar apps desconhecidos").
     */
    suspend fun checkIfDue(): ReleaseInfo? {
        val last = store.getString(KEY_LAST_CHECK)?.toLongOrNull() ?: 0L
        val current = now()
        if (current - last !in 0 until UpdateRules.CHECK_INTERVAL_MS) {
            store.putString(KEY_LAST_CHECK, current.toString())
            try {
                val result = http.execute("GET", UpdateRules.LATEST_RELEASE_URL, GITHUB_HEADERS)
                if (result.ok) {
                    val latest = UpdateRules.parseRelease(result.body)?.takeIf { UpdateRules.isNewer(it.tag, installedVersion) }
                    store.putString(KEY_PENDING, latest?.let { AppJson.encodeToString(it) })
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // sem rede: fica com o que ja se sabia
            }
        }
        return pending()
    }

    /** "Agora nao": nao pergunta de novo ate a proxima consulta ao GitHub. */
    fun dismiss() {
        store.putString(KEY_PENDING, null)
    }

    private fun pending(): ReleaseInfo? {
        val json = store.getString(KEY_PENDING) ?: return null
        val release = runCatching { AppJson.decodeFromString<ReleaseInfo>(json) }.getOrNull()
        if (release == null || !UpdateRules.isAllowedApkUrl(release.apkUrl) || !UpdateRules.isNewer(release.tag, installedVersion)) {
            store.putString(KEY_PENDING, null) // ja instalada (ou invalida)
            return null
        }
        return release
    }

    /** Baixa e confere o APK da versao. Nunca lanca (exceto cancelamento). */
    suspend fun download(release: ReleaseInfo): DownloadResult = withContext(Dispatchers.IO) {
        cleanup()
        val target = File(dir, "pitstophub-${release.version}.apk")
        try {
            dir.mkdirs()
            downloader.download(release.apkUrl, target)
            verify(target, release)
            DownloadResult.Ready(target)
        } catch (e: CancellationException) {
            target.delete()
            throw e
        } catch (e: Exception) {
            target.delete()
            DownloadResult.Failed(rejected = e is UpdateRejected)
        }
    }

    private fun verify(apk: File, release: ReleaseInfo) {
        if (!UpdateRules.matchesSha256(apk, release.sha256)) throw UpdateRejected("SHA-256 do APK nao confere com o Release")
        if (!UpdateRules.sameSigners(signers.installed(), signers.archive(apk))) {
            throw UpdateRejected("APK assinado por outra chave")
        }
    }

    /** Apaga os APKs baixados (chamado no inicio do app: a instalacao, se houve, ja terminou). */
    suspend fun cleanup() = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        Unit
    }

    companion object {
        private const val KEY_LAST_CHECK = "update_last_check"
        private const val KEY_PENDING = "update_pending"
        private val GITHUB_HEADERS = mapOf(
            "Accept" to "application/vnd.github+json",
            "X-GitHub-Api-Version" to "2022-11-28",
        )
    }
}
