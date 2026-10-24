package com.pitstophub.app.ui

import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryData
import com.pitstophub.app.update.DownloadResult
import com.pitstophub.app.update.ManualCheck
import com.pitstophub.app.update.ReleaseInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CategoryState(
    val data: CategoryData? = null,
    /** Primeira carga: ainda sem dado ao vivo (pode haver dado estatico na tela). */
    val loading: Boolean = true,
    /** Puxar para atualizar. */
    val refreshing: Boolean = false,
    val failed: Boolean = false,
)

/** Destino pedido por fora do app (toque numa notificacao). */
data class DeepLink(val categoryId: String, val raceId: String?)

/** Atualizacao do app (dialogo na raiz). */
sealed interface UpdateState {
    data object Idle : UpdateState

    /** Ha versao nova: pergunta "Atualizar?". */
    data class Available(val release: ReleaseInfo) : UpdateState

    data class Downloading(val release: ReleaseInfo) : UpdateState

    /** APK baixado e conferido; falta o instalador do Android (ou liberar "instalar apps desconhecidos"). */
    data class Ready(val release: ReleaseInfo, val apk: File) : UpdateState

    /** Falhou ([rejected]: hash/assinatura/endereco nao conferem). Vira snackbar. */
    data class Failed(val rejected: Boolean) : UpdateState
}

/** Resposta de uma conferida pedida na hora, mostrada como aviso rapido. */
enum class UpdateNotice { UP_TO_DATE, FAILED }

/** Categoria selecionada + dados dela, compartilhados pelas abas. */
class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.categories
    private val preferences = container.preferences

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _selectedId = MutableStateFlow(preferences.getString(KEY_CATEGORY, DEFAULT_CATEGORY) ?: DEFAULT_CATEGORY)
    val selectedId: StateFlow<String> = _selectedId.asStateFlow()

    private val _state = MutableStateFlow(CategoryState())
    val state: StateFlow<CategoryState> = _state.asStateFlow()

    /** Todas as categorias carregadas (estatico na hora, ao vivo quando chega): alimenta o reel e a Agenda. */
    private val _all = MutableStateFlow<Map<String, CategoryData>>(emptyMap())
    val all: StateFlow<Map<String, CategoryData>> = _all.asStateFlow()

    private val _deepLink = MutableStateFlow<DeepLink?>(null)
    val deepLink: StateFlow<DeepLink?> = _deepLink.asStateFlow()

    private val updater get() = container.updater

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    private var downloadJob: Job? = null

    /** Enquanto o botao "Procurar atualizacao" espera a resposta do GitHub. */
    private val _checkingUpdate = MutableStateFlow(false)
    val checkingUpdate: StateFlow<Boolean> = _checkingUpdate.asStateFlow()

    /** Aviso rapido do resultado de uma conferida pedida na hora. */
    private val _updateNotice = MutableSharedFlow<UpdateNotice>()
    val updateNotice: SharedFlow<UpdateNotice> = _updateNotice.asSharedFlow()

    init {
        viewModelScope.launch {
            _categories.value = repository.categories()
            loadAll(force = false)
        }
        viewModelScope.launch { _selectedId.collectLatest { load(it, force = false) } }
        viewModelScope.launch { container.webData.warmUp() }
        viewModelScope.launch {
            // APKs de uma atualizacao anterior (instalada ou nao) ja nao servem.
            updater.cleanup()
            // Em segundo plano e calado: sem rede, ou sem versao nova, nada aparece.
            updater.checkIfDue()?.let { _update.value = UpdateState.Available(it) }
        }
    }

    // --- atualizacao do app ----------------------------------------------------------------------

    fun startUpdate() {
        val release = (_update.value as? UpdateState.Available)?.release ?: return
        _update.value = UpdateState.Downloading(release)
        downloadJob = viewModelScope.launch {
            _update.value = when (val result = updater.download(release)) {
                is DownloadResult.Ready -> UpdateState.Ready(release, result.apk)
                is DownloadResult.Failed -> UpdateState.Failed(result.rejected)
            }
        }
    }

    /**
     * Botao "Procurar atualizacao": vai a rede na hora, sem esperar as 6 horas da conferida automatica.
     * O resultado aparece como aviso rapido (em dia / nao deu) ou como o proprio dialogo da versao nova.
     */
    fun checkForUpdates() {
        if (_checkingUpdate.value || _update.value !is UpdateState.Idle) return
        _checkingUpdate.value = true
        viewModelScope.launch {
            when (val result = updater.checkNow()) {
                is ManualCheck.Available -> _update.value = UpdateState.Available(result.release)
                ManualCheck.UpToDate -> _updateNotice.emit(UpdateNotice.UP_TO_DATE)
                ManualCheck.Failed -> _updateNotice.emit(UpdateNotice.FAILED)
            }
            _checkingUpdate.value = false
        }
    }

    /** Volta do segundo plano: se ja passou o intervalo, confere de novo sem a pessoa precisar reabrir o app. */
    fun onForeground() {
        if (_update.value !is UpdateState.Idle) return
        viewModelScope.launch {
            updater.checkIfDue()?.let { if (_update.value is UpdateState.Idle) _update.value = UpdateState.Available(it) }
        }
    }

    /** "Agora nao" (pergunta, download ou instalacao): fecha e apaga o que tiver sido baixado. */
    fun dismissUpdate() {
        downloadJob?.cancel()
        downloadJob = null
        _update.value = UpdateState.Idle
        updater.dismiss()
        viewModelScope.launch { updater.cleanup() }
    }

    /** O APK foi entregue ao instalador do Android; ele e apagado no proximo inicio do app. */
    fun updateHandedOff() {
        _update.value = UpdateState.Idle
    }

    fun consumeUpdateFailure() {
        if (_update.value is UpdateState.Failed) _update.value = UpdateState.Idle
    }

    fun select(categoryId: String) {
        if (categoryId == _selectedId.value) return
        preferences.edit { putString(KEY_CATEGORY, categoryId) }
        _selectedId.value = categoryId
    }

    fun refresh() {
        viewModelScope.launch { load(_selectedId.value, force = true) }
    }

    /** Atualiza todas as categorias (puxar para atualizar na Inicio e na Agenda). */
    fun refreshAll() {
        viewModelScope.launch { loadAll(force = true) }
    }

    private fun loadAll(force: Boolean) {
        for (category in _categories.value) {
            viewModelScope.launch {
                if (!force) {
                    val instant = repository.cached(category.id) ?: repository.staticData(category.id)
                    if (instant != null) _all.update { it + (category.id to instant) }
                }
                try {
                    val data = repository.load(category.id, force)
                    _all.update { it + (category.id to data) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // fica com o que ja havia (estatico ou cache)
                }
            }
        }
    }

    fun openFromNotification(categoryId: String, raceId: String?) {
        _deepLink.value = DeepLink(categoryId, raceId)
    }

    fun consumeDeepLink() {
        _deepLink.value = null
    }

    private suspend fun load(id: String, force: Boolean) {
        if (force) {
            _state.update { it.copy(refreshing = true, failed = false) }
        } else {
            // Pinta na hora o que ja se tem (memoria ou catalogo estatico) e atualiza quando a rede responder.
            val instant = repository.cached(id) ?: repository.staticData(id)
            _state.value = CategoryState(data = instant, loading = true)
        }
        try {
            _state.value = CategoryState(data = repository.load(id, force), loading = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, refreshing = false, failed = true) }
        }
    }

    companion object {
        const val DEFAULT_CATEGORY = "f1"
        private const val KEY_CATEGORY = "selected_category"
    }
}
