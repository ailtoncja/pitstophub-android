package com.pitstophub.app.data.repo

import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Cifra o texto da sessao antes de ir para o disco; nos testes vira uma chave AES comum, sem Android Keystore. */
interface SessionCipher {
    /** Texto cifrado pronto para guardar (base64). */
    fun encrypt(plain: String): String

    /** Lanca excecao se o texto foi alterado ou se a chave nao serve mais (chave invalidada ou apagada). */
    fun decrypt(encoded: String): String

    /** Descarta a chave atual; a proxima cifragem cria outra. */
    fun reset() {}
}

/**
 * AES-256-GCM: guarda base64(iv + texto cifrado com a tag de 128 bits). O IV de 12 bytes e sorteado pelo proprio
 * [Cipher] a cada cifragem (o Android Keystore nao aceita IV escolhido por fora).
 */
class AesGcmSessionCipher(
    private val key: () -> SecretKey,
    private val onReset: () -> Unit = {},
) : SessionCipher {

    override fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val iv = cipher.iv
        check(iv != null && iv.size == IV_BYTES) { "IV inesperado" }
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(iv + sealed)
    }

    override fun decrypt(encoded: String): String {
        val bytes = Base64.getDecoder().decode(encoded)
        require(bytes.size > IV_BYTES + TAG_BITS / 8) { "Sessao cifrada curta demais" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    override fun reset() = onReset()

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
