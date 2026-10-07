package com.pitstophub.app.data.repo

import java.text.Normalizer
import java.util.Locale

/** Utilitarios de texto usados para casar nomes de pilotos/equipes entre fontes diferentes. */
object TextUtil {
    private val combiningMarks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    private fun stripAccents(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(combiningMarks, "")

    /** "João da Silva" -> "joao da silva" (sem acento, so letras/digitos separados por espaco). */
    fun normalize(value: String): String =
        stripAccents(value).lowercase(Locale.ROOT).replace(nonAlnum, " ").trim()

    fun slugify(value: String): String =
        stripAccents(value).lowercase(Locale.ROOT).replace(nonAlnum, "-").trim('-')

    /** "reece BARR" -> "Reece Barr". Os sites oficiais mandam o sobrenome em maiusculas. */
    fun toDriverName(value: String): String =
        value.lowercase(Locale.ROOT).split(' ').filter { it.isNotEmpty() }
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    fun toTitleCase(value: String): String =
        value.lowercase(Locale.ROOT).split(Regex("(?<=[- ])|(?=[- ])"))
            .joinToString("") { part -> if (part == "-" || part == " ") part else part.replaceFirstChar { it.titlecase(Locale.ROOT) } }

    fun lastName(value: String): String = normalize(value).split(' ').lastOrNull().orEmpty()
}
