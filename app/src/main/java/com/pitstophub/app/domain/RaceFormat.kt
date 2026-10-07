package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.repo.TextUtil
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/** Formatacao de datas e horas das corridas, sempre no fuso do aparelho. */
object RaceFormat {

    /** "MAR 02" / "02 MAR": rotulo curto de data do card do calendario. */
    fun monthDay(date: String, locale: Locale): String {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return date
        val month = parsed.month.getDisplayName(java.time.format.TextStyle.SHORT, locale).trimEnd('.').uppercase(locale)
        val day = "%02d".format(parsed.dayOfMonth)
        return if (locale.language == "pt") "$day $month" else "$month $day"
    }

    /** "dom., 8 de mar. · 01:00" quando ha horario; so a data quando nao ha. */
    fun dateTime(race: Race, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = race.startsAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (instant != null) {
            val day = DateTimeFormatter.ofPattern(dayPattern(locale), locale).withZone(zone).format(instant)
            val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)
            return "$day · $time"
        }
        val parsed = runCatching { LocalDate.parse(race.date) }.getOrNull() ?: return race.date
        return DateTimeFormatter.ofPattern(dayPattern(locale), locale).format(parsed)
    }

    fun sessionDateTime(startsAt: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): String {
        val instant = runCatching { Instant.parse(startsAt) }.getOrNull() ?: return startsAt
        val day = DateTimeFormatter.ofPattern(if (locale.language == "pt") "EEE, dd/MM" else "EEE, MMM d", locale).withZone(zone).format(instant)
        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone).format(instant)
        return "${day.uppercase(locale)} · $time"
    }

    /**
     * "Circuito, Cidade" sem repeticao: a Jolpica chama o circuito de "Marina Bay Street Circuit" e a cidade de
     * "Marina Bay"; quando um texto contem o outro, fica so o mais completo.
     */
    fun place(circuit: String, location: String): String {
        val c = circuit.trim()
        val l = location.trim()
        if (c.isEmpty()) return l
        if (l.isEmpty()) return c
        val nc = TextUtil.normalize(c)
        val nl = TextUtil.normalize(l)
        return when {
            nc.contains(nl) -> c
            nl.contains(nc) -> l
            else -> "$c, $l"
        }
    }

    /** "VER" para "Max Verstappen": 3 primeiras letras do sobrenome. */
    fun driverCode(name: String): String =
        name.trim().split(' ').lastOrNull().orEmpty().filter { it.isLetter() }.take(3).uppercase()

    private fun dayPattern(locale: Locale) = if (locale.language == "pt") "EEE, d 'de' MMM" else "EEE, MMM d"
}
