package com.pitstophub.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pitstophub.app.notifications.AlertNotifier
import com.pitstophub.app.ui.AppViewModel
import com.pitstophub.app.ui.PitStopHubRoot
import com.pitstophub.app.ui.theme.PitStopHubTheme
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val container: AppContainer get() = (application as PitStopHubApp).container

    private val appViewModel: AppViewModel by viewModels {
        viewModelFactory { initializer { AppViewModel(container) } }
    }

    // O idioma escolhido dentro do app vale para tudo: o contexto da Activity ja nasce traduzido.
    override fun attachBaseContext(newBase: Context) {
        val languages = (newBase.applicationContext as? PitStopHubApp)?.container?.languages
        super.attachBaseContext(languages?.wrap(newBase) ?: newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Barras do sistema transparentes com icones claros: o app e escuro por design.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        handleNotificationIntent(intent)
        // Trocar o idioma (perfil ou conta) recria a tela para reler os textos.
        lifecycleScope.launch { container.languages.choice.drop(1).collect { recreate() } }
        setContent {
            PitStopHubTheme {
                PitStopHubRoot(container = container, appViewModel = appViewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    /** Toque numa notificacao abre a categoria (e a corrida) do aviso. */
    private fun handleNotificationIntent(intent: Intent?) {
        val categoryId = intent?.getStringExtra(AlertNotifier.EXTRA_CATEGORY) ?: return
        val raceId = intent.getStringExtra(AlertNotifier.EXTRA_RACE)
        // Consome os extras: uma recriacao (troca de idioma, rotacao) nao deve reabrir o aviso.
        intent.removeExtra(AlertNotifier.EXTRA_CATEGORY)
        intent.removeExtra(AlertNotifier.EXTRA_RACE)
        appViewModel.openFromNotification(categoryId, raceId)
    }
}
