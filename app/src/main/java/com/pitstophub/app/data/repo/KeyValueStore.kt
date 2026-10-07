package com.pitstophub.app.data.repo

import android.content.SharedPreferences
import androidx.core.content.edit

/** Armazenamento simples de texto por chave (a conta e o cache de configuracoes); nos testes vira um mapa em memoria. */
interface KeyValueStore {
    fun getString(key: String): String?

    /** `null` remove a chave. */
    fun putString(key: String, value: String?)
}

class PrefsKeyValueStore(private val preferences: SharedPreferences) : KeyValueStore {
    override fun getString(key: String): String? = preferences.getString(key, null)

    override fun putString(key: String, value: String?) = preferences.edit {
        if (value == null) remove(key) else putString(key, value)
    }
}
