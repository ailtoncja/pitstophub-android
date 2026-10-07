package com.pitstophub.app.ui.race

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import java.time.Duration
import java.util.Locale

/** Atalhos do sistema para uma corrida: adicionar a agenda e compartilhar. */
object RaceActions {

    // Duracao tipica da prova, so para o evento de agenda ter um fim razoavel (mesmos valores do .ics do site).
    private val raceHours = mapOf(
        "f1" to 2.0, "f2" to 1.5, "f3" to 1.5, "f1-academy" to 1.0, "wec" to 6.0, "imsa" to 4.0,
        "endurance-brasil" to 4.0, "indy" to 3.0, "nascar" to 3.5, "dtm" to 1.0, "wrc" to 8.0,
    )

    /** Abre o app de agenda ja com o evento preenchido (sem pedir permissao de calendario). */
    fun addToCalendar(context: Context, category: Category, race: Race, portuguese: Boolean) {
        val start = RaceTime.startInstant(race)
        val known = RaceTime.hasKnownTime(race)
        val hours = raceHours[category.id] ?: 3.0
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "${category.name} — ${race.displayName(portuguese)}")
            .putExtra(CalendarContract.Events.EVENT_LOCATION, RaceFormat.place(race.circuit, race.displayLocation(portuguese)))
            .putExtra(CalendarContract.Events.DESCRIPTION, "${category.displayFullName(portuguese)} • PitStopHub")
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start.plus(Duration.ofMinutes((hours * 60).toLong())).toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, !known)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, R.string.race_no_calendar_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, category: Category, race: Race, portuguese: Boolean, locale: Locale) {
        val text = buildString {
            append("${category.name} — ${race.displayName(portuguese)}")
            append("\n${RaceFormat.dateTime(race, locale)}")
            val place = RaceFormat.place(race.circuit, race.displayLocation(portuguese))
            if (place.isNotEmpty()) append("\n$place")
            append("\nPitStopHub")
        }
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
