package com.pitstophub.app.ui.account

import androidx.compose.runtime.compositionLocalOf
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.remote.AuthResult
import com.pitstophub.app.data.remote.AuthSession
import com.pitstophub.app.data.repo.FollowOutcome
import com.pitstophub.app.domain.NotifyKind
import com.pitstophub.app.domain.UserSettings
import com.pitstophub.app.i18n.LanguageChoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/** Avisos curtos para a interface (viram snackbar) e pedidos que a tela raiz atende. */
sealed interface AccountEvent {
    data class Followed(val name: String) : AccountEvent
    data class Unfollowed(val name: String) : AccountEvent

    /** Seguir exige conta: a raiz abre o login. */
    data object NeedsLogin : AccountEvent
}

/**
 * Ponte entre a interface e a conta (login, favoritos, avisos). Qualquer tela le o estado por [LocalAccount] e
 * chama as acoes; quem exige conta devolve [AccountEvent.NeedsLogin] em vez de falhar calado.
 */
class AccountController(private val container: AppContainer, private val scope: CoroutineScope) {

    val session: StateFlow<AuthSession?> = container.auth.session
    val settings: StateFlow<UserSettings> = container.userSettings.settings
    val ready: StateFlow<Boolean> = container.userSettings.ready
    val alertsEnabled: StateFlow<Boolean> = container.alerts.enabled
    val languageChoice: StateFlow<LanguageChoice> = container.languages.choice

    /** O Supabase esta configurado (URL + anon key); sem ele nao ha login. */
    val accountsAvailable: Boolean get() = container.accountsAvailable

    private val _events = MutableSharedFlow<AccountEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<AccountEvent> = _events.asSharedFlow()

    // --- conta ----------------------------------------------------------------------------------

    suspend fun login(email: String, password: String): AuthResult = container.auth.login(email, password)

    suspend fun register(name: String, email: String, password: String): AuthResult = container.auth.register(name, email, password)

    fun logout() {
        scope.launch { container.auth.logout() }
    }

    fun requestLogin() {
        _events.tryEmit(AccountEvent.NeedsLogin)
    }

    // --- favoritos ------------------------------------------------------------------------------

    fun toggleCategory(categoryId: String, name: String) = report(container.userSettings.toggleCategory(categoryId), name)

    fun toggleTeam(categoryId: String, teamId: String, name: String) = report(container.userSettings.toggleTeam(categoryId, teamId), name)

    fun toggleDriver(categoryId: String, driverId: String, name: String) = report(container.userSettings.toggleDriver(categoryId, driverId), name)

    /** Remove da lista de prioridade sem avisar (o botao "x" ja e a confirmacao). */
    fun removeTeam(categoryId: String, teamId: String) {
        container.userSettings.toggleTeam(categoryId, teamId)
    }

    fun removeDriver(categoryId: String, driverId: String) {
        container.userSettings.toggleDriver(categoryId, driverId)
    }

    fun movePriority(key: String, direction: Int) = container.userSettings.movePriority(key, direction)

    fun finishOnboarding() = container.userSettings.finishOnboarding()

    fun setFavoriteCategory(categoryId: String) = container.userSettings.setFavoriteCategory(categoryId)

    private fun report(outcome: FollowOutcome, name: String) {
        _events.tryEmit(
            when (outcome) {
                FollowOutcome.NEEDS_LOGIN -> AccountEvent.NeedsLogin
                FollowOutcome.FOLLOWED -> AccountEvent.Followed(name)
                FollowOutcome.UNFOLLOWED -> AccountEvent.Unfollowed(name)
            },
        )
    }

    // --- avisos ---------------------------------------------------------------------------------

    fun setNotify(kind: NotifyKind, value: Boolean) = container.userSettings.setNotify(kind, value)

    fun setAlertsEnabled(value: Boolean) = container.alerts.setEnabled(value)

    fun systemAllowsNotifications(): Boolean = container.alerts.systemAllows()

    fun sendTestNotification() = container.alerts.sendTest()

    fun refreshAlerts() = container.alerts.requestSync()

    // --- idioma ---------------------------------------------------------------------------------

    fun setLanguage(choice: LanguageChoice) {
        container.languages.set(choice)
        if (choice != LanguageChoice.SYSTEM) container.userSettings.setLanguage(choice.code)
    }
}

val LocalAccount = compositionLocalOf<AccountController> { error("AccountController nao foi fornecido") }
