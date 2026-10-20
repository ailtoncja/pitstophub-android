package com.pitstophub.app.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import java.io.InputStream
import java.security.MessageDigest

/** Uma versao publicada (o conteudo do `versao.json` do Release mais recente). */
data class AppUpdate(val code: Long, val name: String, val apkUrl: String, val sha256: String, val notes: String)

/**
 * Regras puras do atualizador (sem Android, testadas na JVM). Cada GitHub Release de [REPO] leva o APK e um
 * `versao.json` gerado por `scripts/publish-release.ps1`:
 *
 * ```json
 * {"versionCode": 6, "versionName": "1.4.0", "apk": ".../releases/download/v1.4.0/pitstophub-1.4.0.apk",
 *  "sha256": "<64 hex>", "novidades": "texto curto"}
 * ```
 *
 * Um APK de fora nao entra: o endereco tem de ser um anexo de Release deste repositorio, o SHA-256 tem de bater e o
 * Android so aceita por cima um APK assinado com a mesma chave do app instalado.
 */
object UpdateManifest {
    const val REPO = "https://github.com/ailtoncja/pitstophub-android/"

    /** Todo download do atualizador comeca aqui (o GitHub redireciona para o servidor de arquivos dele, em https). */
    const val RELEASES_PREFIX = REPO + "releases/"
    const val MANIFEST_URL = RELEASES_PREFIX + "latest/download/versao.json"
    const val APK_PREFIX = RELEASES_PREFIX + "download/"

    const val MAX_MANIFEST_BYTES = 64L * 1024
    const val MAX_APK_BYTES = 60L * 1024 * 1024
    private const val MAX_NOTES = 1_000

    private val BOM = Char(0xFEFF).toString() // PowerShell 5 grava UTF-8 com BOM
    private val SHA256 = Regex("[0-9a-f]{64}")
    private val VERSION_NAME = Regex("[0-9A-Za-z._+-]{1,32}")

    // Depois do prefixo: so "<tag>/<arquivo>.apk", sem %, ?, #, barras extras ou espacos.
    private val APK_PATH = Regex("[0-9A-Za-z._+-]+/[0-9A-Za-z._+-]+\\.apk")
    private val DOWNLOADED_APK = Regex("pitstophub-(\\d+)\\.apk")

    /** Le o versao.json; null se faltar campo, se o SHA-256 nao for valido ou se o APK nao vier dos Releases do repo. */
    fun parse(json: String): AppUpdate? {
        val root = runCatching { Json.parseToJsonElement(json.removePrefix(BOM)).jsonObject }.getOrNull() ?: return null
        // Aceita tambem os nomes em portugues (codigo/nome).
        fun field(vararg keys: String) = keys.firstNotNullOfOrNull { root[it] as? JsonPrimitive }
        fun text(vararg keys: String) = field(*keys)?.takeIf { it.isString }?.content?.trim()

        val code = field("versionCode", "codigo")?.longOrNull ?: return null
        val name = text("versionName", "nome") ?: return null
        val apk = text("apk") ?: return null
        val sha = text("sha256")?.lowercase() ?: return null
        val notes = text("novidades").orEmpty().take(MAX_NOTES)
        if (code <= 0 || !VERSION_NAME.matches(name) || !isAllowedApkUrl(apk) || !SHA256.matches(sha)) return null
        return AppUpdate(code, name, apk, sha, notes)
    }

    fun isAllowedApkUrl(url: String): Boolean =
        url.startsWith(APK_PREFIX) && ".." !in url && APK_PATH.matches(url.substring(APK_PREFIX.length))

    fun isNewer(update: AppUpdate, installedCode: Long): Boolean = update.code > installedCode

    fun apkFileName(code: Long) = "pitstophub-$code.apk"

    /** Arquivo da pasta de downloads que ja nao serve: versao instalada ou mais velha, ou sobra (download pela metade). */
    fun isStaleDownload(fileName: String, installedCode: Long): Boolean {
        val code = DOWNLOADED_APK.matchEntire(fileName)?.groupValues?.get(1)?.toLongOrNull() ?: return true
        return code <= installedCode
    }

    fun sha256Hex(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    fun sha256Hex(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return hex(digest.digest())
    }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
