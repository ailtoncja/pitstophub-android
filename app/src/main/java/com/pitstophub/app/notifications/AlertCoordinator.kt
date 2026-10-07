package com.pitstophub.app.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pitstophub.app.PitStopHubApp
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.RaceStatus
import com.pitstophub.app.data.repo.AuthRepository
import com.pitstophub.app.data.repo.CategoryRepository
import com.pitstophub.app.data.repo.UserSettingsRepository
import com.pitstophub.app.domain.NotifyKind
import com.pitstophub.app.domain.UserSettings
import com.pitstophub.app.domain.wants
import com.pitstophub.app.i18n.LanguageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/**
 * Cerebro dos avisos locais. O PWA recebe push de um servidor (GitHub Actions + Web Push); o app nativo nao tem
 * servidor de push (exigiria Firebase), entao aplica as MESMAS regras no aparelho: uma sincronizacao a cada ~30
 * min (WorkManager) busca as categorias seguidas, dispara o que ja venceu e agenda alarmes para as proximas horas.
 */
class AlertCoordinator(
    private val context: Context,
    private val settings: UserSettingsRepository,
    private val auth: AuthRepository,
    private val categories: CategoryRepository,
    private val ledger: AlertLedger,
    private val scheduler: AlertScheduler,
    private val preferences: SharedPreferences,
    private val languages: LanguageManager,
    private val scope: CoroutineScope,
    private val clock: () -> Instant = { Instant.now() },
) {
    private val _enabled = MutableStateFlow(preferences.getBoolean(KEY_ENABLED, true))

    /** Chave geral deste aparelho (as quatro preferencias por tipo ficam na conta e valem tambem no site). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val syncLock = Mutex()

    fun setEnabled(value: Boolean) {
        preferences.edit { putBoolean(KEY_ENABLED, value) }
        _enabled.value = value
    }

    /** O sistema permite mostrar notificacoes (permissao do Android 13+ e canal nao bloqueado). */
    fun systemAllows(): Boolean = AlertNotifier.canPost(context)

    /** Observa login, preferencias e a chave geral; liga ou desliga a sincronizacao periodica conforme o caso. */
    fun start() {
        scope.launch {
            combine(settings.settings, auth.session.map { it?.userId }, _enabled) { prefs, userId, on ->
                Triple(prefs, userId, on)
            }.distinctUntilChanged().collectLatest { (prefs, userId, on) ->
                if (userId != null && on && prefs.followCount > 0) {
                    ensurePeriodicWork()
                    sync()
                } else {
                    WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
                    scheduler.cancelAll()
                }
            }
        }
    }

    fun requestSync() {
        scope.launch { sync() }
    }

    private fun ensurePeriodicWork() {
        val request = PeriodicWorkRequestBuilder<AlertWorker>(30, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    /** Mostra uma notificacao de teste, para a pessoa conferir permissao e som. */
    fun sendTest() {
        val localized = languages.wrap(context)
        val texts = AlertTexts(settings.settings.value.isPortuguese)
        AlertNotifier.post(localized, "test:${clock().toEpochMilli()}", texts.testTitle, texts.testBody, AlertNotifier.CHANNEL_RACES, null, null)
    }

    /** Uma rodada completa: avisos de corrida (dispara os vencidos, agenda os futuros) e de resultado. */
    suspend fun sync() = syncLock.withLock {
        val prefs = settings.settings.value
        if (auth.currentUser == null || !_enabled.value || prefs.followCount == 0 || !systemAllows()) {
            scheduler.cancelAll()
            return@withLock
        }
        val now = clock()
        val localized = languages.wrap(context)
        val loaded = loadInteresting(prefs)

        val planned = AlertPlanner.plan(now, prefs, loaded.map { it.second })
        val (due, future) = planned.partition { it.isDue(now) }
        for (alert in due) {
            if (ledger.markIfNew(alert.key)) {
                AlertNotifier.post(localized, alert.key, alert.title, alert.body, AlertNotifier.CHANNEL_RACES, alert.categoryId, alert.raceId)
            }
        }
        scheduler.replaceAll(future.filter { it.fireAt.isAfter(now) && !ledger.seen(it.key) })

        if (prefs.wants(NotifyKind.RESULTS)) {
            for ((season, category) in loaded) postResults(localized, prefs, category, season, now)
        }
        ledger.prune()
    }

    private suspend fun loadInteresting(prefs: UserSettings): List<Pair<String, Category>> = coroutineScope {
        prefs.interestingCategoryIds().map { id ->
            async {
                try {
                    categories.load(id).let { it.season to it.category }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun postResults(localized: Context, prefs: UserSettings, category: Category, season: String, now: Instant) {
        val since = now.atZone(ZoneOffset.UTC).toLocalDate().minusDays(RESULT_WINDOW_DAYS)
        val recent = category.calendar.filter { race ->
            race.status == RaceStatus.COMPLETED && runCatching { !LocalDate.parse(race.date).isBefore(since) }.getOrDefault(false)
        }
        val hasFollows = prefs.followedDriverIds.any { it.startsWith("${category.id}::") } || prefs.followedTeamIds.any { it.startsWith("${category.id}::") }
        if (!hasFollows) return
        for (race in recent) {
            val alert = try {
                when {
                    category.id == "f1" && race.round != null -> {
                        val key = "checked:${category.id}:${race.id}"
                        if (ledger.seen(key)) null else {
                            val rows = categories.f1Results(season, race.round)
                            if (rows.isEmpty()) null else ResultPlanner.forF1(category, race, rows, prefs).also { ledger.markIfNew(key) }
                        }
                    }
                    category.id != "f1" -> ResultPlanner.forWinner(category, race, prefs)
                    else -> null
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: continue
            if (ledger.markIfNew(alert.key)) {
                AlertNotifier.post(localized, alert.key, alert.title, alert.body, AlertNotifier.CHANNEL_RESULTS, alert.categoryId, alert.raceId)
            }
        }
    }

    private companion object {
        const val KEY_ENABLED = "alerts_enabled"
        const val WORK_NAME = "pitstophub-alerts"
        const val RESULT_WINDOW_DAYS = 3L
    }
}

/** Tarefa periodica do WorkManager: so chama a sincronizacao do coordenador. */
class AlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as PitStopHubApp).container
        return try {
            container.alerts.sync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
