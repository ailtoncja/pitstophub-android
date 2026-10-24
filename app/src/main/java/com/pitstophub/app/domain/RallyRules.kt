package com.pitstophub.app.domain

import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RallyDates
import com.pitstophub.app.data.model.RallyDay
import com.pitstophub.app.data.model.RallyInfo
import com.pitstophub.app.data.model.RallyStage
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToLong

/** Numeros-chave do rali, calculados das especiais (nao digitados a mao). */
data class RallyKeyNumbers(
    val count: Int,
    val km: Double,
    /** A(s) especial(is) mais longa(s): passagens repetidas tem a mesma distancia. */
    val longest: List<RallyStage>,
    val longestKm: Double,
    val powerStage: RallyStage?,
)

/** Horario de uma especial no fuso do aparelho; [day] e o dia da semana quando a conversao muda o dia ("Qui"). */
data class StageClock(val time: String, val day: String?)

/** Porte de src/rally-info.ts e src/rally/dates.ts do site. */
object RallyRules {

    /**
     * Id da ficha do rali da etapa, pela data (os ralis do WRC nao se sobrepoem). O catalogo guarda o ultimo dia e a
     * fonte ao vivo pode guardar outro dia do mesmo fim de semana, por isso a folga (1 dia antes, 2 depois).
     */
    fun rallyIdFor(index: List<RallyDates>, race: Race): String? {
        val day = runCatching { LocalDate.parse(race.date.take(10)) }.getOrNull() ?: return null
        return index.firstOrNull { rally ->
            val start = runCatching { LocalDate.parse(rally.start) }.getOrNull() ?: return@firstOrNull false
            val end = runCatching { LocalDate.parse(rally.end) }.getOrNull() ?: return@firstOrNull false
            !day.isBefore(start.minusDays(1)) && !day.isAfter(end.plusDays(2))
        }?.id
    }

    fun stages(info: RallyInfo): List<RallyStage> = info.days.flatMap { it.stages }

    private fun round2(value: Double): Double = (value * 100).roundToLong() / 100.0

    fun keyNumbers(info: RallyInfo): RallyKeyNumbers {
        val stages = stages(info)
        val longestKm = stages.maxOfOrNull { it.km } ?: 0.0
        return RallyKeyNumbers(
            count = stages.size,
            km = round2(stages.sumOf { it.km }),
            longest = stages.filter { it.km == longestKm },
            longestKm = longestKm,
            powerStage = stages.firstOrNull { it.powerStage },
        )
    }

    fun dayKm(day: RallyDay): Double = round2(day.stages.sumOf { it.km })

    /** Nome da especial sem o numero da passagem ("Coiluna – Loelle 1" -> "Coiluna – Loelle"). */
    fun stageBaseName(name: String): String = name.replace(Regex("""\s+\d+$"""), "")

    /** Rotas que uma especial do dia usa (para apagar as outras no mapa quando um dia e escolhido). */
    fun routesOf(day: RallyDay): Set<String> = day.stages.mapNotNull { it.routeId }.toSet()

    /** Rotas da Power Stage (rotulo e linha em destaque). */
    fun powerRoutes(info: RallyInfo): Set<String> = stages(info).filter { it.powerStage }.mapNotNull { it.routeId }.toSet()

    /**
     * Horario no fuso de quem abre (ou em [zone]). Os dias do itinerario seguem a data local do rali; quando a
     * conversao cai em outro dia (ex.: Japao visto do Brasil, 12 h a menos), [StageClock.day] traz o dia da semana
     * do aparelho para o horario nao parecer do dia errado.
     */
    fun stageClock(iso: String, rallyDate: String, locale: Locale, zone: ZoneId = ZoneId.systemDefault()): StageClock? {
        val local = runCatching { OffsetDateTime.parse(iso).atZoneSameInstant(zone) }.getOrNull() ?: return null
        val time = local.format(DateTimeFormatter.ofPattern("HH:mm", locale))
        if (local.toLocalDate().toString() == rallyDate) return StageClock(time, null)
        return StageClock(time, weekdayShort(local.dayOfWeek, locale))
    }

    fun weekdayShort(day: DayOfWeek, locale: Locale): String = capitalize(day.getDisplayName(TextStyle.SHORT, locale).replace(".", ""), locale)

    fun weekdayLong(date: String, locale: Locale): String =
        runCatching { capitalize(LocalDate.parse(date).dayOfWeek.getDisplayName(TextStyle.FULL, locale), locale) }.getOrDefault(date)

    fun monthShort(date: String, locale: Locale): String =
        runCatching { LocalDate.parse(date).month.getDisplayName(TextStyle.SHORT, locale).replace(".", "") }.getOrDefault("")

    fun dayOfMonth(date: String): String = date.drop(8).take(2).toIntOrNull()?.toString().orEmpty()

    private fun capitalize(text: String, locale: Locale): String = text.replaceFirstChar { it.titlecase(locale) }
}
