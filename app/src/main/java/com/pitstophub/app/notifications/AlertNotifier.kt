package com.pitstophub.app.notifications

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.pitstophub.app.MainActivity
import com.pitstophub.app.R
import java.time.Instant

/** Guarda quais avisos ja foram mostrados, para nao repetir (alarme + sincronizacao podem disparar o mesmo). */
class AlertLedger(private val preferences: SharedPreferences, private val clock: () -> Instant = { Instant.now() }) {

    /** true se a chave ainda nao tinha sido marcada (e passa a estar). */
    @Synchronized
    fun markIfNew(key: String): Boolean {
        val name = PREFIX + key
        if (preferences.contains(name)) return false
        preferences.edit { putLong(name, clock().toEpochMilli()) }
        return true
    }

    @Synchronized
    fun seen(key: String): Boolean = preferences.contains(PREFIX + key)

    /** Esquece o que e antigo (a dedup so precisa cobrir a janela de interesse de uma corrida). */
    @Synchronized
    fun prune(maxAgeDays: Long = 7) {
        val limit = clock().minusSeconds(maxAgeDays * 86_400).toEpochMilli()
        val stale = preferences.all.filter { (name, value) -> name.startsWith(PREFIX) && (value as? Long ?: 0L) < limit }.keys
        if (stale.isNotEmpty()) preferences.edit { stale.forEach(::remove) }
    }

    private companion object {
        const val PREFIX = "sent_"
    }
}

/** Canais e envio das notificacoes do app. */
object AlertNotifier {
    const val CHANNEL_RACES = "race_alerts"
    const val CHANNEL_RESULTS = "results"

    const val EXTRA_CATEGORY = "alert_category_id"
    const val EXTRA_RACE = "alert_race_id"

    /** Cria os canais (idempotente). Receber o contexto ja no idioma escolhido traduz os nomes. */
    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RACES, context.getString(R.string.channel_races_name), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.channel_races_desc)
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_RESULTS, context.getString(R.string.channel_results_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_results_desc)
            },
        )
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // areNotificationsEnabled() cobre a permissao do Android 13+
    fun post(context: Context, key: String, title: String, body: String, channelId: String, categoryId: String?, raceId: String?) {
        if (!canPost(context)) return
        ensureChannels(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            categoryId?.let { putExtra(EXTRA_CATEGORY, it) }
            raceId?.let { putExtra(EXTRA_RACE, it) }
        }
        val id = key.hashCode()
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_pitstop)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setColor(0xFFE10600.toInt())
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setPriority(if (channelId == CHANNEL_RACES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setGroup(channelId)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }
}
