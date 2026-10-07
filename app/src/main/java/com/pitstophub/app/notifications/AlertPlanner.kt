package com.pitstophub.app.notifications

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.domain.NotifyKind
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.domain.UserSettings
import com.pitstophub.app.domain.wants
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

enum class AlertKind(val notifyKind: NotifyKind, val tag: String) {
    RACE_DAY(NotifyKind.RACE_DAY, "day"),
    T60(NotifyKind.T60, "t60"),
    START(NotifyKind.START, "t0"),
}

/** Um aviso de corrida: quando disparar, ate quando ainda vale a pena mostrar e o texto pronto. */
data class PlannedAlert(
    val kind: AlertKind,
    val categoryId: String,
    val raceId: String,
    val fireAt: Instant,
    val expiresAt: Instant,
    val title: String,
    val body: String,
) {
    /** Chave de deduplicacao: um aviso de cada tipo por corrida. */
    val key: String get() = "${categoryId}:${raceId}:${kind.tag}"

    fun isDue(now: Instant): Boolean = !now.isBefore(fireAt) && now.isBefore(expiresAt)
}

/**
 * Regras dos avisos de corrida, portadas de `scripts/send-race-notifications.mjs` do PWA: no dia da corrida (a
 * partir das 8h, ou 2 h antes de largadas muito cedo), 1 hora antes e na largada, para quem segue a categoria ou
 * algum piloto/equipe dela. No Android a mesma regra vira alarmes locais, ja que nao ha servidor de push.
 */
object AlertPlanner {

    /** Corridas que importam: ainda nao disputadas, da ontem a depois de amanha. */
    private val lookBehind: Duration = Duration.ofDays(1)
    private val lookAhead: Duration = Duration.ofDays(2)

    fun plan(
        now: Instant,
        settings: UserSettings,
        categories: List<Category>,
        zone: ZoneId = ZoneId.systemDefault(),
        locale: Locale = Locale.getDefault(),
    ): List<PlannedAlert> {
        val interesting = settings.interestingCategoryIds()
        if (interesting.isEmpty()) return emptyList()
        val texts = AlertTexts(settings.isPortuguese)
        val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale).withZone(zone)

        return categories.filter { it.id in interesting }.flatMap { category ->
            category.calendar
                .filter { it.status == RaceStatus.UPCOMING }
                .filter { race ->
                    val start = RaceTime.startInstant(race, zone)
                    start.isAfter(now.minus(lookBehind)) && start.isBefore(now.plus(lookAhead))
                }
                .flatMap { race -> alertsFor(category, race, settings, texts, timeFormat, zone) }
        }.sortedBy { it.fireAt }
    }

    private fun alertsFor(
        category: Category,
        race: Race,
        settings: UserSettings,
        texts: AlertTexts,
        timeFormat: DateTimeFormatter,
        zone: ZoneId,
    ): List<PlannedAlert> {
        val known = RaceTime.hasKnownTime(race)
        val start = RaceTime.startInstant(race, zone)
        val raceName = race.displayName(settings.isPortuguese)
        val timeLabel = if (known) timeFormat.format(start) else null
        val alerts = mutableListOf<PlannedAlert>()

        if (settings.wants(NotifyKind.RACE_DAY)) {
            val localStart = start.atZone(zone)
            val startMinutes = if (known) localStart.hour * 60 + localStart.minute else DEFAULT_START_MINUTES
            val gate = minOf(GATE_MINUTES, maxOf(0, startMinutes - 120))
            val day: LocalDate = if (known) localStart.toLocalDate() else runCatching { LocalDate.parse(race.date) }.getOrNull() ?: localStart.toLocalDate()
            val fireAt = day.atTime(LocalTime.ofSecondOfDay(gate * 60L)).atZone(zone).toInstant()
            val expires = if (known) start else day.plusDays(1).atStartOfDay(zone).toInstant()
            alerts += PlannedAlert(AlertKind.RACE_DAY, category.id, race.id, fireAt, expires, texts.raceDayTitle(category.name), texts.raceDayBody(raceName, timeLabel))
        }
        if (known && settings.wants(NotifyKind.T60)) {
            alerts += PlannedAlert(AlertKind.T60, category.id, race.id, start.minus(Duration.ofHours(1)), start, texts.soonTitle(category.name), texts.soonBody(raceName, timeLabel))
        }
        if (known && settings.wants(NotifyKind.START)) {
            alerts += PlannedAlert(AlertKind.START, category.id, race.id, start, start.plus(Duration.ofMinutes(15)), texts.startTitle(category.name), raceName)
        }
        return alerts
    }

    private const val GATE_MINUTES = 8 * 60
    private const val DEFAULT_START_MINUTES = 15 * 60
}

/** Textos dos avisos; os mesmos do servidor de push do PWA, em portugues e ingles. */
class AlertTexts(private val portuguese: Boolean) {
    fun raceDayTitle(category: String) = if (portuguese) "$category: corrida hoje" else "$category: race day"

    fun raceDayBody(race: String, time: String?) = when {
        time == null -> race
        portuguese -> "$race · largada $time"
        else -> "$race · starts $time"
    }

    fun soonTitle(category: String) = if (portuguese) "$category: largada em breve" else "$category: starts soon"

    fun soonBody(race: String, time: String?) = if (time == null) race else "$race · $time"

    fun startTitle(category: String) = if (portuguese) "$category: luzes apagadas" else "$category: lights out"

    fun resultTitle(category: String) = if (portuguese) "$category: resultado" else "$category: result"

    val testTitle: String get() = "PitStopHub"
    val testBody: String get() = if (portuguese) "Notificações funcionando! Você receberá avisos das corridas que acompanha." else "Notifications are working! You'll get alerts for the races you follow."
}
