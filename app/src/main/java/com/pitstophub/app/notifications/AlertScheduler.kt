package com.pitstophub.app.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.edit
import com.pitstophub.app.PitStopHubApp
import java.time.Instant

/**
 * Agenda os avisos futuros como alarmes locais (`setAndAllowWhileIdle`: acorda o aparelho em Doze, com margem de
 * alguns minutos, sem exigir a permissao de alarme exato). A sincronizacao periodica refaz o agendamento inteiro.
 */
class AlertScheduler(private val context: Context, private val preferences: SharedPreferences) {

    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    /** Agenda [alerts] e cancela os alarmes antigos que nao fazem mais parte do plano. */
    fun replaceAll(alerts: List<PlannedAlert>) {
        val previous = preferences.getStringSet(KEY_SCHEDULED, emptySet()).orEmpty()
        val keep = alerts.map { it.key }.toSet()
        (previous - keep).forEach { cancel(it) }
        alerts.forEach(::schedule)
        preferences.edit { putStringSet(KEY_SCHEDULED, keep) }
    }

    fun cancelAll() = replaceAll(emptyList())

    private fun schedule(alert: PlannedAlert) {
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alert.fireAt.toEpochMilli(), pendingIntent(alert))
    }

    private fun cancel(key: String) {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = ACTION_ALERT
            data = Uri.parse("pitstophub://alert/$key")
        }
        // FLAG_NO_CREATE: so mexe em alarme que existe de fato.
        PendingIntent.getBroadcast(context, key.hashCode(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun pendingIntent(alert: PlannedAlert): PendingIntent {
        val intent = Intent(context, AlertReceiver::class.java).apply {
            action = ACTION_ALERT
            data = Uri.parse("pitstophub://alert/${alert.key}")
            putExtra(EXTRA_KEY, alert.key)
            putExtra(EXTRA_TITLE, alert.title)
            putExtra(EXTRA_BODY, alert.body)
            putExtra(AlertNotifier.EXTRA_CATEGORY, alert.categoryId)
            putExtra(AlertNotifier.EXTRA_RACE, alert.raceId)
            putExtra(EXTRA_EXPIRES, alert.expiresAt.toEpochMilli())
        }
        return PendingIntent.getBroadcast(context, alert.key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_ALERT = "com.pitstophub.app.ALERT"
        const val EXTRA_KEY = "alert_key"
        const val EXTRA_TITLE = "alert_title"
        const val EXTRA_BODY = "alert_body"
        const val EXTRA_EXPIRES = "alert_expires_at"
        private const val KEY_SCHEDULED = "scheduled_alert_keys"
    }
}

/** Dispara o aviso agendado (se ainda valer e nao tiver sido mostrado). */
class AlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as PitStopHubApp).container
        val key = intent.getStringExtra(AlertScheduler.EXTRA_KEY) ?: return
        val expires = intent.getLongExtra(AlertScheduler.EXTRA_EXPIRES, Long.MAX_VALUE)
        if (Instant.now().toEpochMilli() >= expires) return
        if (!container.alerts.enabled.value) return
        if (!container.alertLedger.markIfNew(key)) return
        AlertNotifier.post(
            context = container.languages.wrap(context),
            key = key,
            title = intent.getStringExtra(AlertScheduler.EXTRA_TITLE).orEmpty(),
            body = intent.getStringExtra(AlertScheduler.EXTRA_BODY).orEmpty(),
            channelId = AlertNotifier.CHANNEL_RACES,
            categoryId = intent.getStringExtra(AlertNotifier.EXTRA_CATEGORY),
            raceId = intent.getStringExtra(AlertNotifier.EXTRA_RACE),
        )
    }
}

/** Depois de reiniciar o aparelho os alarmes se perdem: subir o processo ja refaz o plano (ver [PitStopHubApp]). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        (context.applicationContext as PitStopHubApp).container.alerts.requestSync()
    }
}
