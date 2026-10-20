package com.pitstophub.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private const val APK_SHA = "7319d0dd61ff537d5d95d999ed80526e5482a7ad14f7817d0ff4a0f65980c8ef"
private const val CERT_SHA = "33c7b66edd406749a58e5484c73c648575f388fa9da97c64bbdda5f0d962bf69"

/** Notas do Release no formato usado desde a 1.3.1 (hash do APK e do certificado). */
private val RELEASE_BODY = """
    Atualização de segurança. Instale por cima da 1.3.0 (mesma assinatura).

    - SHA-256 do APK: `$APK_SHA`
    - SHA-256 do certificado de assinatura: `$CERT_SHA`
""".trimIndent()

internal fun releaseJson(
    tag: String = "v1.3.2",
    name: String = "pitstophub-1.3.2.apk",
    url: String = "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
    size: Long = 7_971_740,
    digest: String? = "sha256:$APK_SHA",
    body: String = RELEASE_BODY,
    prerelease: Boolean = false,
    extraAssets: String = "",
): String {
    val digestField = digest?.let { ""","digest":"$it"""" }.orEmpty()
    val escapedBody = body.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
    return """
        {"tag_name":"$tag","name":"PitStopHub","draft":false,"prerelease":$prerelease,"body":"$escapedBody",
         "assets":[$extraAssets{"name":"$name","browser_download_url":"$url","size":$size$digestField,
                   "content_type":"application/vnd.android.package-archive"}]}
    """.trimIndent()
}

class UpdateRulesTest {

    @get:Rule
    val temp = TemporaryFolder()

    // --- versoes -----------------------------------------------------------------------------------

    @Test
    fun `versoes com e sem v viram numeros`() {
        assertEquals(listOf(1, 3, 2), UpdateRules.parseVersion("v1.3.2"))
        assertEquals(listOf(1, 3, 2), UpdateRules.parseVersion("1.3.2"))
        assertEquals(listOf(2, 0), UpdateRules.parseVersion("V2.0"))
        assertNull(UpdateRules.parseVersion("1.4.0-beta"))
        assertNull(UpdateRules.parseVersion("latest"))
        assertNull(UpdateRules.parseVersion(""))
        assertNull(UpdateRules.parseVersion("v1..2"))
    }

    @Test
    fun `comparacao e semantica e nao alfabetica`() {
        assertTrue(UpdateRules.isNewer("v1.3.2", "1.3.1"))
        assertTrue(UpdateRules.isNewer("v1.10.0", "1.9.9"))
        assertTrue(UpdateRules.isNewer("v2.0", "1.99.99"))
        assertTrue(UpdateRules.isNewer("v1.3.1.1", "1.3.1"))
        assertFalse(UpdateRules.isNewer("v1.3.1", "1.3.1"))
        assertFalse(UpdateRules.isNewer("v1.3", "1.3.0"))
        assertFalse(UpdateRules.isNewer("v1.3.0", "1.3.1"))
        assertFalse(UpdateRules.isNewer("v1.2.9", "1.10.0"))
        assertEquals(0, UpdateRules.compareVersions("1.3", "1.3.0"))
    }

    @Test
    fun `tag ou versao invalida nunca oferece atualizacao`() {
        assertFalse(UpdateRules.isNewer("nightly", "1.3.1"))
        assertFalse(UpdateRules.isNewer("v9.0.0-rc1", "1.3.1"))
        assertFalse(UpdateRules.isNewer("v9.0.0", "1.3.1-debug"))
        assertNull(UpdateRules.compareVersions("x", "1.0"))
    }

    // --- enderecos ---------------------------------------------------------------------------------

    @Test
    fun `so aceita APK dos Releases do repositorio do app`() {
        assertTrue(UpdateRules.isAllowedApkUrl("https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk"))

        listOf(
            "http://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://github.com/ailtoncja/outro-repo/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://github.com/outra-pessoa/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://github.com.evil.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://evil.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://github.com:8443/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/../../../evil/x.apk",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/%2e%2e/x/pitstophub.apk",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/a/pitstophub.apk",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk?x=1",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk#x",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.zip",
            "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2\\pitstophub-1.3.2.apk",
            "https://github.com/ailtoncja/pitstophub-android/archive/v1.3.2/pitstophub-1.3.2.apk",
            "",
        ).forEach { assertFalse(it, UpdateRules.isAllowedApkUrl(it)) }
    }

    @Test
    fun `redirecionamento so para o servidor de arquivos do GitHub`() {
        // Formato real do redirecionamento de hoje, com assinatura temporaria na query.
        assertTrue(
            UpdateRules.isAllowedRedirect(
                "https://release-assets.githubusercontent.com/github-production-release-asset/1408191651/3f7147d0" +
                    "?sp=r&sv=2018-11-09&sig=44Iux6i0pvIVAmr%2FcF7yPCNeJ33hmCLgwm0wN8iUhpM%3D&jwt=a.b.c",
            ),
        )
        assertTrue(UpdateRules.isAllowedRedirect("https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/2?X-Amz-Signature=x"))
        assertTrue(UpdateRules.isAllowedRedirect("https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/pitstophub-1.3.2.apk"))

        listOf(
            "http://release-assets.githubusercontent.com/a",
            "https://release-assets.githubusercontent.com.evil.com/a",
            "https://evil.com/release-assets.githubusercontent.com/a",
            "https://raw.githubusercontent.com/ailtoncja/pitstophub-android/main/x.apk",
            "https://user:pw@objects.githubusercontent.com/a",
            "https://objects.githubusercontent.com:444/a",
            "https://github.com/evil/pitstophub-android/releases/download/v1/x.apk",
            "file:///sdcard/x.apk",
            "nao e url",
        ).forEach { assertFalse(it, UpdateRules.isAllowedRedirect(it)) }
    }

    // --- SHA-256 -----------------------------------------------------------------------------------

    @Test
    fun `digest do GitHub vira hex minusculo`() {
        assertEquals(APK_SHA, UpdateRules.parseDigest("sha256:${APK_SHA.uppercase()}"))
        assertNull(UpdateRules.parseDigest("sha512:$APK_SHA"))
        assertNull(UpdateRules.parseDigest("sha256:abc"))
        assertNull(UpdateRules.parseDigest("sha256:${APK_SHA}00"))
        assertNull(UpdateRules.parseDigest(null))
    }

    @Test
    fun `hash das notas e o do APK e nao o do certificado`() {
        assertEquals(APK_SHA, UpdateRules.sha256FromBody(RELEASE_BODY))
        assertEquals(APK_SHA, UpdateRules.sha256FromBody("$APK_SHA  pitstophub-1.3.2.apk"))
        assertNull(UpdateRules.sha256FromBody("- SHA-256 do certificado de assinatura: `$CERT_SHA`"))
        assertNull(UpdateRules.sha256FromBody("Sem hash nenhum no APK"))
        assertNull(UpdateRules.sha256FromBody(null))
        // Dois hashes diferentes para o APK: ambiguo, nao confia em nenhum.
        assertNull(UpdateRules.sha256FromBody("APK: $APK_SHA\nAPK (outro): ${"0".repeat(64)}"))
    }

    @Test
    fun `hash esperado usa digest e notas e recusa quando discordam`() {
        assertEquals(APK_SHA, UpdateRules.expectedSha256("sha256:$APK_SHA", RELEASE_BODY))
        assertEquals(APK_SHA, UpdateRules.expectedSha256("sha256:$APK_SHA", null))
        assertEquals(APK_SHA, UpdateRules.expectedSha256(null, RELEASE_BODY))
        assertNull(UpdateRules.expectedSha256("sha256:${"a".repeat(64)}", RELEASE_BODY))
        assertNull(UpdateRules.expectedSha256(null, "sem hash"))
    }

    @Test
    fun `confere o SHA-256 do arquivo`() {
        val file = temp.newFile("x.apk").apply { writeText("abc") }
        val abc = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        assertEquals(abc, UpdateRules.sha256Hex(file))
        assertEquals(abc, UpdateRules.sha256Hex("abc".toByteArray()))
        assertTrue(UpdateRules.matchesSha256(file, abc))
        assertTrue(UpdateRules.matchesSha256(file, abc.uppercase()))
        assertFalse(UpdateRules.matchesSha256(file, APK_SHA))
        assertFalse(UpdateRules.matchesSha256(file, ""))
    }

    @Test
    fun `assinatura precisa ser a mesma do app instalado`() {
        assertTrue(UpdateRules.sameSigners(setOf(CERT_SHA), setOf(CERT_SHA)))
        assertFalse(UpdateRules.sameSigners(setOf(CERT_SHA), setOf(APK_SHA)))
        assertFalse(UpdateRules.sameSigners(setOf(CERT_SHA), emptySet()))
        assertFalse(UpdateRules.sameSigners(emptySet(), emptySet()))
        assertFalse(UpdateRules.sameSigners(setOf(CERT_SHA), setOf(CERT_SHA, APK_SHA)))
    }

    // --- Release -----------------------------------------------------------------------------------

    @Test
    fun `le o Release do GitHub`() {
        val release = UpdateRules.parseRelease(releaseJson())
        assertNotNull(release)
        assertEquals("v1.3.2", release!!.tag)
        assertEquals("1.3.2", release.version)
        assertEquals("pitstophub-1.3.2.apk", release.apkName)
        assertEquals(7_971_740L, release.size)
        assertEquals(APK_SHA, release.sha256)
    }

    @Test
    fun `sem digest usa o hash das notas`() {
        assertEquals(APK_SHA, UpdateRules.parseRelease(releaseJson(digest = null))?.sha256)
    }

    @Test
    fun `entre varios APKs escolhe o da versao`() {
        val other = """{"name":"outro.apk","browser_download_url":"https://github.com/ailtoncja/pitstophub-android/releases/download/v1.3.2/outro.apk","size":10},"""
        assertEquals("pitstophub-1.3.2.apk", UpdateRules.parseRelease(releaseJson(extraAssets = other))?.apkName)
    }

    @Test
    fun `Release suspeito ou incompleto e ignorado`() {
        assertNull(UpdateRules.parseRelease(releaseJson(prerelease = true)))
        assertNull(UpdateRules.parseRelease(releaseJson(tag = "nightly")))
        assertNull(UpdateRules.parseRelease(releaseJson(url = "https://evil.com/pitstophub-1.3.2.apk")))
        assertNull(UpdateRules.parseRelease(releaseJson(size = UpdateRules.MAX_APK_BYTES + 1)))
        assertNull(UpdateRules.parseRelease(releaseJson(size = 0)))
        assertNull(UpdateRules.parseRelease(releaseJson(digest = null, body = "sem hash")))
        assertNull(UpdateRules.parseRelease(releaseJson(name = "pitstophub-1.3.2.zip")))
        assertNull(UpdateRules.parseRelease("""{"message":"Not Found"}"""))
        assertNull(UpdateRules.parseRelease("nao e json"))
    }
}
