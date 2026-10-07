package com.pitstophub.app.data.repo

import com.pitstophub.app.data.remote.AppJson
import com.pitstophub.app.data.remote.SupabaseSettingsClient
import com.pitstophub.app.domain.NotifyKind
import com.pitstophub.app.domain.UserSettings
import com.pitstophub.app.domain.withNotify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.io.IOException
import java.time.ZoneId

enum class FollowOutcome { NEEDS_LOGIN, FOLLOWED, UNFOLLOWED }

/**
 * Configuracoes da conta (favoritos, ordem de prioridade, avisos, idioma), com cache local por usuario e
 * sincronizacao com a tabela `user_settings` -- o mesmo contrato do PWA, entao favoritos feitos no site aparecem
 * no app e vice-versa. Visitante usa o padrao e nao salva nada (seguir exige conta, como no site).
 */
class UserSettingsRepository(
    private val auth: AuthRepository,
    private val client: SupabaseSettingsClient,
    private val cache: KeyValueStore,
    private val scope: CoroutineScope,
    /** Idioma que vale hoje no app ("pt"/"en"): usado como padrao de conta nova e de visitante. */
    private val effectiveLanguage: () -> String,
    /** Aplica o idioma salvo na conta (devolve se mudou). */
    private val applyAccountLanguage: (String) -> Unit,
) {
    private val _settings = MutableStateFlow(guestDefaults())
    val settings: StateFlow<UserSettings> = _settings.asStateFlow()

    /** As configuracoes da conta ja foram resolvidas (da rede, do cache ou padrao). */
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    val loggedIn: Boolean get() = auth.currentUser != null

    private var saveJob: Job? = null

    init {
        scope.launch {
            auth.session.distinctUntilChangedBy { it?.userId }.collectLatest { session ->
                if (session == null) {
                    saveJob?.cancel()
                    _settings.value = guestDefaults()
                    _ready.value = true
                } else {
                    load(session.userId)
                }
            }
        }
    }

    private fun guestDefaults() = UserSettings(language = effectiveLanguage(), timezone = ZoneId.systemDefault().id)

    private suspend fun load(userId: String) {
        _ready.value = false
        readCache(userId)?.let { _settings.value = it }
        try {
            val token = auth.accessToken()
            if (token != null && client.enabled) {
                val remote = client.fetch(userId, token)
                if (remote != null) {
                    val merged = remote.withReconciledPriority()
                    _settings.value = merged
                    writeCache(userId, merged)
                    applyAccountLanguage(merged.language)
                } else {
                    // Conta nova: cria a linha padrao e mostra a escolha de favoritos.
                    client.ensureRow(userId, token, effectiveLanguage())
                    val fresh = guestDefaults().copy(favoritesOnboarded = false)
                    _settings.value = fresh
                    writeCache(userId, fresh)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: IOException) {
            // Sem rede: segue com o cache (ou o padrao). O proximo salvamento envia as mudancas.
        } catch (_: Exception) {
            // Resposta inesperada do servidor: idem.
        } finally {
            _ready.value = true
        }
    }

    // --- acoes ----------------------------------------------------------------------------------

    fun toggleCategory(categoryId: String): FollowOutcome = follow(categoryId in _settings.value.followedCategoryIds) {
        it.toggleCategory(categoryId)
    }

    fun toggleTeam(categoryId: String, teamId: String): FollowOutcome =
        follow(_settings.value.followsTeam(categoryId, teamId)) { it.toggleTeam(categoryId, teamId) }

    fun toggleDriver(categoryId: String, driverId: String): FollowOutcome =
        follow(_settings.value.followsDriver(categoryId, driverId)) { it.toggleDriver(categoryId, driverId) }

    private fun follow(wasFollowing: Boolean, change: (UserSettings) -> UserSettings): FollowOutcome {
        if (!loggedIn) return FollowOutcome.NEEDS_LOGIN
        update(change)
        return if (wasFollowing) FollowOutcome.UNFOLLOWED else FollowOutcome.FOLLOWED
    }

    fun setNotify(kind: NotifyKind, value: Boolean) = update { it.withNotify(kind, value) }

    fun movePriority(key: String, direction: Int) = update { it.movePriority(key, direction) }

    fun setFavoriteCategory(categoryId: String) = update { if (it.favoriteCategoryId == categoryId) it else it.copy(favoriteCategoryId = categoryId) }

    fun finishOnboarding() = update { if (it.favoritesOnboarded) it else it.copy(favoritesOnboarded = true) }

    fun setLanguage(code: String) = update { if (it.language == code) it else it.copy(language = code) }

    private fun update(change: (UserSettings) -> UserSettings) {
        val next = change(_settings.value)
        if (next == _settings.value) return
        _settings.value = next
        val userId = auth.currentUser?.id ?: return
        writeCache(userId, next)
        scheduleSave(userId)
    }

    private fun scheduleSave(userId: String) {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DEBOUNCE_MS)
            try {
                val token = auth.accessToken() ?: return@launch
                client.save(userId, token, _settings.value.copy(timezone = ZoneId.systemDefault().id))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // O cache local guarda a mudanca; a proxima alteracao tenta enviar de novo.
            }
        }
    }

    // --- cache local ----------------------------------------------------------------------------

    private fun readCache(userId: String): UserSettings? = cache.getString(cacheKey(userId))?.let { raw ->
        runCatching { AppJson.decodeFromString<UserSettings>(raw) }.getOrNull()
    }

    private fun writeCache(userId: String, settings: UserSettings) = cache.putString(cacheKey(userId), AppJson.encodeToString(settings))

    private fun cacheKey(userId: String) = "user_settings_$userId"

    private companion object {
        const val SAVE_DEBOUNCE_MS = 500L
    }
}
