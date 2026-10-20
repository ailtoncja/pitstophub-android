package com.pitstophub.app

import android.content.Context
import com.pitstophub.app.data.StaticCatalog
import com.pitstophub.app.data.WebData
import com.pitstophub.app.data.remote.HttpFetcher
import com.pitstophub.app.data.remote.JolpicaClient
import com.pitstophub.app.data.remote.MotorsportsClient
import com.pitstophub.app.data.remote.SupabaseAuthClient
import com.pitstophub.app.data.remote.SupabaseRestClient
import com.pitstophub.app.data.remote.SupabaseSettingsClient
import com.pitstophub.app.data.remote.WeatherClient
import com.pitstophub.app.data.repo.AuthRepository
import com.pitstophub.app.data.repo.CategoryRepository
import com.pitstophub.app.data.repo.KeystoreSessionKey
import com.pitstophub.app.data.repo.PrefsKeyValueStore
import com.pitstophub.app.data.repo.SessionStore
import com.pitstophub.app.data.repo.UserSettingsRepository
import com.pitstophub.app.i18n.LanguageManager
import com.pitstophub.app.notifications.AlertCoordinator
import com.pitstophub.app.notifications.AlertLedger
import com.pitstophub.app.notifications.AlertScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** Injecao de dependencia manual: uma instancia por processo, criada em [PitStopHubApp]. */
class AppContainer(context: Context) {

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.SECONDS)
        .build()

    private val fetcher = HttpFetcher(httpClient)

    /**
     * Mesmo cliente (e pool de conexoes), mas sem cache em disco: login e `user_settings` levam o token do usuario e a
     * resposta (tokens, dados da conta) nao pode ficar gravada em `cacheDir/http`.
     */
    private val privateFetcher = HttpFetcher(httpClient.newBuilder().cache(null).build())

    /** Escopo de trabalho que vive com o processo (sincronizacao de conta, avisos). */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val preferences = context.getSharedPreferences("pitstophub", Context.MODE_PRIVATE)

    val languages = LanguageManager(preferences)

    val categories = CategoryRepository(
        catalog = StaticCatalog(context),
        jolpica = JolpicaClient(fetcher),
        motorsports = MotorsportsClient(fetcher),
        supabase = SupabaseRestClient(fetcher, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
    )

    /** Biografias, titulos, campeoes e fichas de circuito exportados do PWA. */
    val webData = WebData(context)

    val weather = WeatherClient(fetcher)

    // --- conta -------------------------------------------------------------------------------------

    val auth = AuthRepository(
        client = SupabaseAuthClient(privateFetcher, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
        store = SessionStore(
            store = PrefsKeyValueStore(context.getSharedPreferences("pitstophub_session", Context.MODE_PRIVATE)),
            cipher = KeystoreSessionKey().cipher(),
        ),
    )

    val userSettings = UserSettingsRepository(
        auth = auth,
        client = SupabaseSettingsClient(privateFetcher, BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY),
        cache = PrefsKeyValueStore(context.getSharedPreferences("pitstophub_settings", Context.MODE_PRIVATE)),
        scope = appScope,
        effectiveLanguage = { languages.effectiveCode(context) },
        applyAccountLanguage = { languages.setFromAccount(it) },
    )

    // --- avisos ------------------------------------------------------------------------------------

    private val alertPreferences = context.getSharedPreferences("pitstophub_alerts", Context.MODE_PRIVATE)

    val alertLedger = AlertLedger(alertPreferences)

    val alerts = AlertCoordinator(
        context = context,
        settings = userSettings,
        auth = auth,
        categories = categories,
        ledger = alertLedger,
        scheduler = AlertScheduler(context, alertPreferences),
        preferences = alertPreferences,
        languages = languages,
        scope = appScope,
    )

    /** O login so funciona com URL e anon key do Supabase no `local.properties`. */
    val accountsAvailable: Boolean get() = auth.configured
}
