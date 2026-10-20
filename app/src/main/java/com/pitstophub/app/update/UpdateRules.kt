package com.pitstophub.app.update

import com.pitstophub.app.data.remote.AppJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.security.MessageDigest

/** Versao nova publicada no GitHub, ja conferida: APK do repositorio do app, tamanho dentro do limite e SHA-256 conhecido. */
@Serializable
data class ReleaseInfo(
    val tag: String,
    /** `X.Y.Z` (a tag sem o `v`). */
    val version: String,
    val apkName: String,
    val apkUrl: String,
    val size: Long,
    /** SHA-256 esperado do APK (hex minusculo). */
    val sha256: String,
)

@Serializable
private data class GitHubAsset(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
    /** `sha256:<hex>`, calculado pelo proprio GitHub no upload. */
    val digest: String? = null,
)

@Serializable
private data class GitHubRelease(
    @SerialName("tag_name") val tag: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val body: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
)

/**
 * Regras puras do atualizador (sem Android, testaveis na JVM): comparar versoes, aceitar so enderecos do repositorio do
 * app, achar o SHA-256 publicado e conferir o arquivo baixado.
 */
object UpdateRules {
    const val LATEST_RELEASE_URL = "https://api.github.com/repos/ailtoncja/pitstophub-android/releases/latest"

    /** Unico lugar de onde um APK pode ser baixado (o GitHub depois redireciona para [REDIRECT_HOSTS]). */
    const val DOWNLOAD_PREFIX = "https://github.com/ailtoncja/pitstophub-android/releases/download/"
    private val DOWNLOAD_SEGMENTS = listOf("ailtoncja", "pitstophub-android", "releases", "download")

    /** Servidores de arquivos do GitHub para onde o link do Release redireciona. */
    val REDIRECT_HOSTS = setOf("objects.githubusercontent.com", "release-assets.githubusercontent.com")

    const val MAX_APK_BYTES = 100L * 1024 * 1024
    const val CHECK_INTERVAL_MS = 6L * 60 * 60 * 1000

    private val VERSION = Regex("""^[vV]?(\d{1,6}(?:\.\d{1,6}){0,3})$""")
    private val HEX64 = Regex("""(?<![0-9a-fA-F])[0-9a-fA-F]{64}(?![0-9a-fA-F])""")

    // --- versoes -----------------------------------------------------------------------------------

    /** `v1.3.2` / `1.3.2` -> [1, 3, 2]; `null` para o que nao for numero (inclusive `1.4.0-beta`). */
    fun parseVersion(text: String): List<Int>? =
        VERSION.matchEntire(text.trim())?.groupValues?.get(1)?.split('.')?.map { it.toInt() }

    /** Comparacao semantica (1.10.0 > 1.9.9; 1.3 == 1.3.0). `null` se alguma nao for versao. */
    fun compareVersions(a: String, b: String): Int? {
        val left = parseVersion(a) ?: return null
        val right = parseVersion(b) ?: return null
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    fun isNewer(remoteTag: String, installed: String): Boolean = (compareVersions(remoteTag, installed) ?: 0) > 0

    // --- enderecos ---------------------------------------------------------------------------------

    /** O link do APK tem de ser exatamente `https://github.com/ailtoncja/pitstophub-android/releases/download/<tag>/<nome>.apk`. */
    fun isAllowedApkUrl(url: String): Boolean {
        if (!url.startsWith(DOWNLOAD_PREFIX) || suspicious(url)) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        if (!plainHttps(parsed) || parsed.host != "github.com") return false
        if (parsed.query != null || parsed.fragment != null) return false
        val segments = parsed.pathSegments
        return segments.size == 6 &&
            segments.take(4) == DOWNLOAD_SEGMENTS &&
            segments.drop(4).all { it.isNotEmpty() && it != "." && it != ".." } &&
            segments.last().endsWith(".apk", ignoreCase = true)
    }

    /**
     * Destino aceito num redirecionamento do download: o servidor de arquivos do GitHub (ou o proprio link do Release).
     * A query dele traz uma assinatura temporaria (com `%2F` etc.), entao aqui so se confere esquema, host e porta.
     */
    fun isAllowedRedirect(url: String): Boolean {
        if (isAllowedApkUrl(url)) return true
        if (url.contains('\\')) return false
        val parsed = url.toHttpUrlOrNull() ?: return false
        return plainHttps(parsed) && parsed.host in REDIRECT_HOSTS
    }

    private fun plainHttps(url: HttpUrl): Boolean =
        url.scheme == "https" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()

    private fun suspicious(url: String): Boolean =
        url.contains('\\') || url.contains("..") || url.contains('%') || url.contains('@')

    // --- SHA-256 -----------------------------------------------------------------------------------

    /** Campo `digest` do asset (`sha256:<hex>`) -> hex minusculo; `null` se for outro algoritmo ou estiver malformado. */
    fun parseDigest(digest: String?): String? {
        val value = digest?.trim() ?: return null
        if (!value.startsWith("sha256:", ignoreCase = true)) return null
        val hex = value.substring("sha256:".length)
        return if (HEX64.matchEntire(hex) != null) hex.lowercase() else null
    }

    /**
     * SHA-256 do APK escrito nas notas do Release (`- SHA-256 do APK: \`...\``, ou uma linha do `sha256sum`). Ignora a
     * linha do certificado de assinatura. `null` se nao houver, ou se houver mais de um valor diferente (ambiguo).
     */
    fun sha256FromBody(body: String?): String? {
        val found = body.orEmpty().lines()
            .filter { line -> line.contains("apk", ignoreCase = true) && !line.contains("certific", ignoreCase = true) }
            .flatMap { line -> HEX64.findAll(line).map { it.value.lowercase() }.toList() }
            .toSet()
        return found.singleOrNull()
    }

    /** Hash esperado: o `digest` do GitHub e/ou o das notas. Se os dois existirem e discordarem, nao ha hash confiavel. */
    fun expectedSha256(digest: String?, body: String?): String? {
        val fromDigest = parseDigest(digest)
        val fromBody = sha256FromBody(body)
        if (fromDigest != null && fromBody != null && fromDigest != fromBody) return null
        return fromDigest ?: fromBody
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().toHex()
    }

    fun matchesSha256(file: File, expected: String): Boolean {
        if (HEX64.matchEntire(expected) == null) return false
        return MessageDigest.isEqual(sha256Hex(file).toByteArray(), expected.lowercase().toByteArray())
    }

    fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHex()

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    // --- assinatura --------------------------------------------------------------------------------

    /** O APK so pode ser instalado se for assinado pelos mesmos certificados (SHA-256) do app instalado. */
    fun sameSigners(installed: Set<String>, apk: Set<String>): Boolean = installed.isNotEmpty() && installed == apk

    // --- Release -----------------------------------------------------------------------------------

    /**
     * Le a resposta de `releases/latest`. `null` se for rascunho/pre-release, se a tag nao for versao, se nao houver um
     * APK do repositorio do app dentro do limite de tamanho, ou se nao houver SHA-256 confiavel.
     */
    fun parseRelease(json: String): ReleaseInfo? {
        val release = runCatching { AppJson.decodeFromString<GitHubRelease>(json) }.getOrNull() ?: return null
        if (release.draft || release.prerelease) return null
        val version = parseVersion(release.tag)?.joinToString(".") ?: return null
        val apks = release.assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val asset = apks.firstOrNull { it.name.equals("pitstophub-$version.apk", ignoreCase = true) } ?: apks.singleOrNull() ?: return null
        if (!isAllowedApkUrl(asset.downloadUrl)) return null
        if (asset.size <= 0 || asset.size > MAX_APK_BYTES) return null
        val sha256 = expectedSha256(asset.digest, release.body) ?: return null
        return ReleaseInfo(release.tag, version, asset.name, asset.downloadUrl, asset.size, sha256)
    }
}
