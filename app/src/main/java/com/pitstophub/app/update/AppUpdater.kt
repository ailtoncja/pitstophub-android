package com.pitstophub.app.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.IntentCompat
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pitstophub.app.BuildConfig
import com.pitstophub.app.PitStopHubApp
import com.pitstophub.app.R
import com.pitstophub.app.data.remote.HttpFetcher
import com.pitstophub.app.i18n.LanguageManager
import com.pitstophub.app.notifications.AlertNotifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class UpdateStep { IDLE, DOWNLOADING, INSTALLING, FAILED }

/** O que o aviso de versao nova mostra. [update] ja exclui a versao adiada com "Depois". */
data class UpdateState(
    val update: AppUpdate? = null,
    val canInstall: Boolean = false,
    val step: UpdateStep = UpdateStep.IDLE,
    val progress: Float? = null,
) {
    val busy: Boolean get() = step == UpdateStep.DOWNLOADING || step == UpdateStep.INSTALLING
}

/**
 * O app se atualiza sem loja, a partir dos GitHub Releases (ver [UpdateManifest]).
 *
 * - Com o app aberto: ao abrir (no maximo a cada 30 min) confere o `versao.json`; havendo versao nova, um aviso
 *   oferece "Atualizar" / "Depois" ("Depois" adia so aquela versao).
 * - Com o app fechado: o [UpdateWorker] confere a cada 6 h. Com "instalar apps desconhecidos" liberado, baixa, confere
 *   o SHA-256 e instala (silencioso no Android 12+ quando o sistema permite); senao, ou se o Android pedir
 *   confirmacao, deixa uma notificacao.
 *
 * Desligado no debug: o APK de debug tem outra assinatura e o Android recusaria a atualizacao.
 */
class AppUpdater(
    private val context: Context,
    httpClient: OkHttpClient,
    private val languages: LanguageManager,
    private val scope: CoroutineScope,
    private val installedCode: Long = BuildConfig.VERSION_CODE.toLong(),
    val enabled: Boolean = !BuildConfig.DEBUG,
) {
    // Mesmo pool do app, sem cache em disco. Segue o redirecionamento do github.com para o servidor de arquivos dele,
    // mas nunca de https para http. Cada chamada define o proprio tempo limite.
    private val client = httpClient.newBuilder()
        .cache(null)
        .followRedirects(true)
        .followSslRedirects(false)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(0, TimeUnit.SECONDS)
        .build()

    private val prefs = context.getSharedPreferences("pitstophub_update", Context.MODE_PRIVATE)
    private val installing = AtomicBoolean(false)
    private val _state = MutableStateFlow(UpdateState())
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** A tela do app esta visivel (MainActivity entre onStart e onStop). */
    @Volatile var inForeground = false

    private val downloads: File get() = File(context.filesDir, "updates")

    /** Ao subir o processo: agenda a conferencia periodica e apaga APKs que ja nao servem. */
    fun start() {
        val work = WorkManager.getInstance(context)
        if (!enabled) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build())
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        scope.launch(Dispatchers.IO) {
            cleanup()
            publish()
        }
    }

    /** Ao abrir ou voltar ao app (tambem depois de liberar a instalacao nas Configuracoes). */
    fun refresh() {
        if (!enabled) return
        scope.launch(Dispatchers.IO) {
            publish()
            checkIfDue(FOREGROUND_INTERVAL_MS)
            publish()
        }
    }

    /** "Depois": esconde o aviso desta versao (uma versao mais nova volta a aparecer). */
    fun snooze() {
        val update = _state.value.update ?: return
        prefs.edit { putLong(KEY_SNOOZED, update.code) }
        publish()
    }

    /** "Atualizar": baixa, confere e instala agora (o Android pode pedir confirmacao). */
    fun updateNow() {
        val update = _state.value.update ?: return
        scope.launch { install(update, background = false) }
    }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Tela do Android para liberar "instalar apps desconhecidos" para o PitStopHub. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

    /** Chamado pelo [UpdateWorker]. Falhas ficam no log; a proxima rodada tenta de novo. */
    suspend fun runInBackground() {
        if (!enabled) return
        withContext(Dispatchers.IO) { checkIfDue(BACKGROUND_INTERVAL_MS) }
        publish()
        val update = _state.value.update ?: return
        if (inForeground) return // com a tela aberta quem cuida e o aviso (sem fechar o app no meio do uso)
        if (!canInstall()) {
            notifyOnce(update, "available", update.notes.ifBlank { localized().getString(R.string.update_notification_text) })
            return
        }
        install(update, background = true)
    }

    // --- versao.json -------------------------------------------------------------------------------

    /** A versao conhecida (da ultima conferencia), se for mais nova que a instalada. Nao acessa a rede. */
    private fun known(): AppUpdate? =
        prefs.getString(KEY_MANIFEST, null)?.let(UpdateManifest::parse)?.takeIf { UpdateManifest.isNewer(it, installedCode) }

    private fun publish() {
        val update = known()?.takeUnless { it.code == prefs.getLong(KEY_SNOOZED, 0L) }
        _state.update { it.copy(update = update, canInstall = canInstall()) }
    }

    private fun checkIfDue(intervalMs: Long) {
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_CHECKED_AT, 0L) in 0..intervalMs) return
        // Marca a tentativa antes: sem rede (ou sem versao.json no Release) nao repete a cada abertura.
        prefs.edit { putLong(KEY_CHECKED_AT, now) }
        try {
            val json = fetch(UpdateManifest.MANIFEST_URL, UpdateManifest.MAX_MANIFEST_BYTES, MANIFEST_TIMEOUT_S) { input, _ ->
                input.readBytes().toString(Charsets.UTF_8)
            }
            if (UpdateManifest.parse(json) == null) throw IOException("versao.json invalido")
            prefs.edit { putString(KEY_MANIFEST, json) }
        } catch (e: Exception) {
            Log.w(TAG, "Nao consegui conferir se ha versao nova", e)
        }
    }

    // --- baixar e instalar -------------------------------------------------------------------------

    private suspend fun install(update: AppUpdate, background: Boolean) {
        if (!installing.compareAndSet(false, true)) return
        try {
            _state.update { it.copy(step = UpdateStep.DOWNLOADING, progress = null) }
            withContext(Dispatchers.IO) {
                val apk = downloadedApk(update)
                _state.update { it.copy(step = UpdateStep.INSTALLING, progress = null) }
                commit(apk, update, background)
            }
            // O resultado chega no UpdateInstallReceiver.
        } catch (e: CancellationException) {
            _state.update { it.copy(step = UpdateStep.IDLE, progress = null) }
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Nao consegui instalar a versao ${update.name}", e)
            _state.update { it.copy(step = if (background) UpdateStep.IDLE else UpdateStep.FAILED, progress = null) }
        } finally {
            installing.set(false)
        }
    }

    /** O APK da versao em files/updates, conferido pelo SHA-256 do versao.json (apaga os de outras versoes). */
    private fun downloadedApk(update: AppUpdate): File {
        val dir = downloads.apply { mkdirs() }
        val apk = File(dir, UpdateManifest.apkFileName(update.code))
        dir.listFiles()?.filter { it.name != apk.name }?.forEach { it.delete() }
        if (apk.isFile && apk.inputStream().use(UpdateManifest::sha256Hex) == update.sha256) return checkPackage(apk, update)

        val part = File(dir, apk.name + ".part")
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            fetch(update.apkUrl, UpdateManifest.MAX_APK_BYTES, APK_TIMEOUT_S) { input, length ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var lastPercent = -1L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        total += read
                        if (length > 0 && total * 100 / length != lastPercent) {
                            lastPercent = total * 100 / length
                            _state.update { it.copy(progress = (lastPercent / 100f).coerceAtMost(1f)) }
                        }
                    }
                }
            }
            if (UpdateManifest.hex(digest.digest()) != update.sha256) throw IOException("SHA-256 do APK nao confere com o versao.json")
            apk.delete()
            if (!part.renameTo(apk)) throw IOException("Nao consegui guardar o APK baixado")
        } finally {
            part.delete()
        }
        return checkPackage(apk, update)
    }

    /** Defesa extra (a assinatura quem garante e o Android): o APK tem de ser deste app e da versao anunciada. */
    private fun checkPackage(apk: File, update: AppUpdate): File {
        val info: PackageInfo? = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apk.path, 0)
        }
        if (info?.packageName != context.packageName || PackageInfoCompat.getLongVersionCode(info) != update.code) {
            apk.delete()
            throw IOException("APK nao e do PitStopHub ${update.name}")
        }
        return apk
    }

    private fun commit(apk: File, update: AppUpdate, background: Boolean) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            // Android 12+: o app atualizando a si mesmo pode instalar sem pedir confirmacao (o sistema decide).
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("pitstophub.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION)
                    .putExtra(EXTRA_CODE, update.code)
                    .putExtra(EXTRA_NAME, update.name)
                    .putExtra(EXTRA_BACKGROUND, background)
                // Mutavel (intencao explicita): o Android preenche o resultado da instalacao nela.
                val mutable = if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
                val pending = PendingIntent.getBroadcast(context, id, result, PendingIntent.FLAG_UPDATE_CURRENT or mutable)
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Resultado da sessao de instalacao (ver [UpdateInstallReceiver]). */
    internal fun onInstallResult(intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                val shown = inForeground && confirm != null &&
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!shown) {
                    // Com o app fechado o Android nao deixa abrir a pergunta: a notificacao leva ao aviso do app.
                    _state.update { it.copy(step = UpdateStep.IDLE, progress = null) }
                    known()?.let { notifyOnce(it, "confirm", localized().getString(R.string.update_notification_text)) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.update { it.copy(step = UpdateStep.IDLE, progress = null) }
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.update { it.copy(step = UpdateStep.IDLE, progress = null) }
            else -> {
                Log.w(TAG, "Atualizacao para ${intent.getStringExtra(EXTRA_NAME)} nao instalada: $status " +
                    intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                _state.update { it.copy(step = if (background) UpdateStep.IDLE else UpdateStep.FAILED, progress = null) }
            }
        }
    }

    // --- apoio -------------------------------------------------------------------------------------

    /** Baixa de um Release deste repositorio, com tamanho maximo. So o endereco inicial e conferido (o resto e redirect). */
    private fun <T> fetch(url: String, maxBytes: Long, timeoutSeconds: Long, read: (InputStream, Long) -> T): T {
        require(url.startsWith(UpdateManifest.RELEASES_PREFIX)) { "So baixamos dos Releases do PitStopHub" }
        val call = client.newCall(Request.Builder().url(url).header("User-Agent", HttpFetcher.USER_AGENT).build())
        call.timeout().timeout(timeoutSeconds, TimeUnit.SECONDS)
        call.execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} em $url")
            val length = response.body.contentLength()
            if (length > maxBytes) throw IOException("Arquivo grande demais ($length bytes)")
            return read(CappedInputStream(response.body.byteStream(), maxBytes), length)
        }
    }

    /** Apaga APKs da versao instalada (ou mais velhas) e downloads pela metade. */
    private fun cleanup() {
        if (installing.get()) return
        downloads.listFiles()?.filter { UpdateManifest.isStaleDownload(it.name, installedCode) }?.forEach { it.delete() }
    }

    /** Uma notificacao por versao e por motivo (para nao repetir a cada conferencia). */
    private fun notifyOnce(update: AppUpdate, reason: String, body: String) {
        val key = "notified_${update.code}_$reason"
        if (prefs.getBoolean(key, false) || !AlertNotifier.canPost(context)) return
        val localized = localized()
        AlertNotifier.post(
            localized, NOTIFICATION_KEY, localized.getString(R.string.update_available_title, update.name), body,
            AlertNotifier.CHANNEL_UPDATES, null, null,
        )
        prefs.edit { putBoolean(key, true) }
    }

    private fun localized(): Context = languages.wrap(context)

    private class CappedInputStream(input: InputStream, private val max: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int = super.read().also { if (it >= 0) add(1) }

        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) add(it.toLong()) }

        private fun add(n: Long) {
            count += n
            if (count > max) throw IOException("Arquivo passou de $max bytes")
        }
    }

    internal companion object {
        const val TAG = "PitStopHubUpdate"
        const val WORK_NAME = "pitstophub-update"
        const val NOTIFICATION_KEY = "app-update"
        const val KEY_MANIFEST = "manifest"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_SNOOZED = "snoozed_code"
        const val EXTRA_CODE = "update_code"
        const val EXTRA_NAME = "update_name"
        const val EXTRA_BACKGROUND = "update_background"
        const val FOREGROUND_INTERVAL_MS = 30 * 60_000L
        const val BACKGROUND_INTERVAL_MS = 60 * 60_000L
        const val MANIFEST_TIMEOUT_S = 30L
        const val APK_TIMEOUT_S = 10 * 60L
    }
}

/** Conferencia periodica (6 h) do WorkManager. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as PitStopHubApp).container.updater.runInBackground()
        return Result.success()
    }
}

/** Recebe o resultado da sessao do PackageInstaller (nao exportado; so o sistema dispara pela PendingIntent). */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        (context.applicationContext as PitStopHubApp).container.updater.onInstallResult(intent)
    }

    companion object {
        const val ACTION = "com.pitstophub.app.UPDATE_INSTALL_RESULT"
    }
}
