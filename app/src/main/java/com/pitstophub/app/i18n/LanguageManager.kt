package com.pitstophub.app.i18n

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/** Escolha de idioma dentro do app: automatico (idioma do aparelho), portugues ou ingles. */
enum class LanguageChoice(val code: String) {
    SYSTEM("system"),
    PORTUGUESE("pt"),
    ENGLISH("en");

    companion object {
        fun fromCode(code: String?): LanguageChoice = entries.firstOrNull { it.code == code } ?: SYSTEM
    }
}

/**
 * O PWA tem seletor PT/EN; no Android o padrao e seguir o aparelho, mas a pessoa pode fixar um idioma. A troca
 * vale para telas e notificacoes: a Activity embrulha o contexto com [wrap] e se recria quando [choice] muda.
 */
class LanguageManager(private val preferences: SharedPreferences) {

    private val _choice = MutableStateFlow(LanguageChoice.fromCode(preferences.getString(KEY, null)))
    val choice: StateFlow<LanguageChoice> = _choice.asStateFlow()

    fun set(choice: LanguageChoice) {
        if (_choice.value == choice) return
        preferences.edit { putString(KEY, choice.code) }
        _choice.value = choice
    }

    /** Define o idioma a partir do codigo salvo na conta ("pt"/"en"); true se mudou. */
    fun setFromAccount(code: String): Boolean {
        val target = LanguageChoice.fromCode(code).takeIf { it != LanguageChoice.SYSTEM } ?: return false
        if (_choice.value == target) return false
        set(target)
        return true
    }

    /** Locale fixado pela pessoa, ou null para seguir o aparelho. */
    fun overrideLocale(): Locale? = when (_choice.value) {
        LanguageChoice.PORTUGUESE -> Locale.forLanguageTag("pt-BR")
        LanguageChoice.ENGLISH -> Locale.ENGLISH
        LanguageChoice.SYSTEM -> null
    }

    /** Contexto no idioma escolhido; sem escolha, o proprio contexto. */
    fun wrap(base: Context): Context {
        val locale = overrideLocale() ?: return base
        Locale.setDefault(locale)
        val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(configuration)
    }

    /** "pt" ou "en", considerando a escolha e, no automatico, o idioma do aparelho. */
    fun effectiveCode(context: Context): String {
        val language = (overrideLocale() ?: context.resources.configuration.locales[0]).language
        return if (language == "pt") "pt" else "en"
    }

    private companion object {
        const val KEY = "language_choice"
    }
}
