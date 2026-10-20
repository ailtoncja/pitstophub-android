package com.pitstophub.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateManifestTest {

    private val sha = "ab".repeat(32)
    private val apk = "https://github.com/ailtoncja/pitstophub-android/releases/download/v1.4.0/pitstophub-1.4.0.apk"

    private fun json(code: String = "6", url: String = apk, hash: String = sha, name: String = "1.4.0") =
        """{"versionCode":$code,"versionName":"$name","apk":"$url","sha256":"$hash","novidades":"Atualizador no app"}"""

    @Test
    fun `le o versao json valido`() {
        val update = UpdateManifest.parse(json())!!
        assertEquals(6L, update.code)
        assertEquals("1.4.0", update.name)
        assertEquals(apk, update.apkUrl)
        assertEquals(sha, update.sha256)
        assertEquals("Atualizador no app", update.notes)
    }

    @Test
    fun `le o formato gerado pelo publish-release ps1`() {
        val generated = """
            {
                "versionCode":  6,
                "versionName":  "1.4.0",
                "apk":  "$apk",
                "sha256":  "$sha",
                "novidades":  "Atualização automática dentro do app."
            }
        """.trimIndent()
        val update = UpdateManifest.parse(generated)!!
        assertEquals(6L, update.code)
        assertEquals("Atualização automática dentro do app.", update.notes)
    }

    @Test
    fun `aceita nomes em portugues, BOM e SHA em maiusculas`() {
        val update = UpdateManifest.parse("﻿" + """{"codigo":7,"nome":"1.5.0","apk":"$apk","sha256":"${sha.uppercase()}"}""")!!
        assertEquals(7L, update.code)
        assertEquals("1.5.0", update.name)
        assertEquals(sha, update.sha256)
        assertEquals("", update.notes)
    }

    @Test
    fun `so versao mais nova que a instalada`() {
        val update = UpdateManifest.parse(json())!!
        assertTrue(UpdateManifest.isNewer(update, 5))
        assertFalse(UpdateManifest.isNewer(update, 6))
        assertFalse(UpdateManifest.isNewer(update, 7))
    }

    @Test
    fun `recusa APK de fora dos Releases do repositorio`() {
        assertNull(UpdateManifest.parse(json(url = "https://exemplo.com/pitstophub.apk")))
        assertNull(UpdateManifest.parse(json(url = "https://github.com/outra-pessoa/pitstophub-android/releases/download/v1/x.apk")))
        assertNull(UpdateManifest.parse(json(url = "https://github.com/ailtoncja/pitstophub-android-fake/releases/download/v1/x.apk")))
        assertNull(UpdateManifest.parse(json(url = "http://github.com/ailtoncja/pitstophub-android/releases/download/v1/x.apk")))
        assertNull(UpdateManifest.parse(json(url = "https://github.com/ailtoncja/pitstophub-android/archive/v1/x.apk")))
        assertNull(UpdateManifest.parse(json(url = "https://github.com/ailtoncja/pitstophub-android/releases/download/v1/x.zip")))
    }

    @Test
    fun `recusa caminho que sai da pasta do Release`() {
        val base = UpdateManifest.APK_PREFIX
        assertNull(UpdateManifest.parse(json(url = base + "../../../outra/repo/releases/download/v1/x.apk")))
        assertNull(UpdateManifest.parse(json(url = base + "v1/../x.apk")))
        assertNull(UpdateManifest.parse(json(url = base + "v1/%2e%2e/x.apk")))
        assertNull(UpdateManifest.parse(json(url = base + "v1/a/b.apk")))
        assertNull(UpdateManifest.parse(json(url = base + "v1/x.apk?y=1")))
        assertNull(UpdateManifest.parse(json(url = base + "x.apk")))
    }

    @Test
    fun `recusa SHA-256 invalido, versao invalida e JSON quebrado`() {
        assertNull(UpdateManifest.parse(json(hash = "123")))
        assertNull(UpdateManifest.parse(json(hash = "g".repeat(64))))
        assertNull(UpdateManifest.parse(json(hash = sha + "00")))
        assertNull(UpdateManifest.parse(json(code = "0")))
        assertNull(UpdateManifest.parse(json(code = "-3")))
        assertNull(UpdateManifest.parse(json(code = "\"abc\"")))
        assertNull(UpdateManifest.parse(json(name = "1.4.0<script>")))
        assertNull(UpdateManifest.parse("{ isto nao e json"))
        assertNull(UpdateManifest.parse("[]"))
        assertNull(UpdateManifest.parse("""{"versionName":"1.4.0"}"""))
    }

    @Test
    fun `apaga downloads da versao instalada, mais velhos e pela metade`() {
        assertTrue(UpdateManifest.isStaleDownload("pitstophub-5.apk", 5))
        assertTrue(UpdateManifest.isStaleDownload("pitstophub-4.apk", 5))
        assertTrue(UpdateManifest.isStaleDownload("pitstophub-6.apk.part", 5))
        assertTrue(UpdateManifest.isStaleDownload("outro.apk", 5))
        assertFalse(UpdateManifest.isStaleDownload(UpdateManifest.apkFileName(6), 5))
    }

    @Test
    fun `SHA-256 em hexadecimal`() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", UpdateManifest.sha256Hex("abc".toByteArray()))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", UpdateManifest.sha256Hex(ByteArray(0)))
        val big = ByteArray(200_000) { (it % 251).toByte() }
        assertEquals(UpdateManifest.sha256Hex(big), UpdateManifest.sha256Hex(big.inputStream()))
        assertNotNull(UpdateManifest.parse(json(hash = UpdateManifest.sha256Hex(big))))
    }
}
