package com.pitstophub.app.data.repo

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Chave AES-256 da sessao dentro do Android Keystore: nao sai do aparelho (nao exportavel) e some junto com o app.
 * Criada na primeira vez que for usada.
 */
class KeystoreSessionKey(private val alias: String = DEFAULT_ALIAS) {

    @Synchronized
    fun getOrCreate(): SecretKey {
        (keyStore().getKey(alias, null) as? SecretKey)?.let { return it }
        val spec = KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
            .apply { init(spec) }
            .generateKey()
    }

    @Synchronized
    fun delete() {
        runCatching { keyStore().deleteEntry(alias) }
    }

    /** Cifra da sessao usando esta chave; [SessionCipher.reset] apaga a chave para a proxima ser criada do zero. */
    fun cipher(): SessionCipher = AesGcmSessionCipher(key = ::getOrCreate, onReset = ::delete)

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val DEFAULT_ALIAS = "pitstophub_session_key"
    }
}
