package com.pitstophub.app.ui

import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pitstophub.app.AppContainer
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.CategoryData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

    init {
        viewModelScope.launch {
            _categories.value = repository.categories()
            loadAll(force = false)
        }
        viewModelScope.launch { _selectedId.collectLatest { load(it, force = false) } }
        viewModelScope.launch { container.webData.warmUp() }
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
