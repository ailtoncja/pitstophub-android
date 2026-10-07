package com.pitstophub.app.domain

import kotlinx.serialization.Serializable

/**
 * Preferencias da conta, espelho de `UserSettings` do PWA (tabela `user_settings` do Supabase). Os ids de quem a
 * pessoa segue usam o mesmo formato do site: "categoria::id"; a ordem de prioridade guarda "team::cat::id" /
 * "driver::cat::id".
 */
@Serializable
data class UserSettings(
    val language: String = "pt",
    val favoriteCategoryId: String = "f1",
    val followedCategoryIds: List<String> = emptyList(),
    val followedTeamIds: List<String> = emptyList(),
    val followedDriverIds: List<String> = emptyList(),
    val favoritesOnboarded: Boolean = true,
    val priorityFollowIds: List<String> = emptyList(),
    val notifyRaceDay: Boolean = true,
    val notifyT60: Boolean = true,
    val notifyStart: Boolean = true,
    val notifyResults: Boolean = true,
    val timezone: String = DEFAULT_TIMEZONE,
) {
    val isPortuguese: Boolean get() = language != "en"

    fun followsCategory(categoryId: String) = categoryId in followedCategoryIds
    fun followsTeam(categoryId: String, teamId: String) = followKey(categoryId, teamId) in followedTeamIds
    fun followsDriver(categoryId: String, driverId: String) = followKey(categoryId, driverId) in followedDriverIds

    /** Quantidade de coisas seguidas (categorias + equipes + pilotos). */
    val followCount: Int get() = followedCategoryIds.size + followedTeamIds.size + followedDriverIds.size

    /** Categorias que interessam: as seguidas inteiras e as de cada equipe/piloto seguido. */
    fun interestingCategoryIds(): Set<String> = buildSet {
        addAll(followedCategoryIds)
        (followedTeamIds + followedDriverIds).forEach { key -> key.substringBefore("::").takeIf(String::isNotBlank)?.let(::add) }
    }

    fun toggleCategory(categoryId: String) = copy(followedCategoryIds = followedCategoryIds.toggled(categoryId))

    fun toggleTeam(categoryId: String, teamId: String) =
        copy(followedTeamIds = followedTeamIds.toggled(followKey(categoryId, teamId))).withReconciledPriority()

    fun toggleDriver(categoryId: String, driverId: String) =
        copy(followedDriverIds = followedDriverIds.toggled(followKey(categoryId, driverId))).withReconciledPriority()

    /**
     * Mantem a ordem de prioridade coerente com quem e seguido (regra do PWA): preserva a ordem, tira quem deixou
     * de ser seguido e poe no fim quem passou a ser.
     */
    fun withReconciledPriority(): UserSettings {
        val valid = followedTeamIds.map { "team::$it" } + followedDriverIds.map { "driver::$it" }
        val kept = priorityFollowIds.filter { it in valid }.distinct()
        val merged = kept + valid.filter { it !in kept }
        return if (merged == priorityFollowIds) this else copy(priorityFollowIds = merged)
    }

    fun movePriority(key: String, direction: Int): UserSettings {
        val index = priorityFollowIds.indexOf(key)
        val target = index + direction
        if (index == -1 || target !in priorityFollowIds.indices) return this
        val next = priorityFollowIds.toMutableList()
        next[index] = next[target].also { next[target] = next[index] }
        return copy(priorityFollowIds = next)
    }

    companion object {
        const val DEFAULT_TIMEZONE = "America/Sao_Paulo"
    }
}

fun followKey(categoryId: String, id: String) = "$categoryId::$id"

private fun List<String>.toggled(value: String): List<String> = if (value in this) this - value else this + value

enum class NotifyKind { RACE_DAY, T60, START, RESULTS }

fun UserSettings.wants(kind: NotifyKind): Boolean = when (kind) {
    NotifyKind.RACE_DAY -> notifyRaceDay
    NotifyKind.T60 -> notifyT60
    NotifyKind.START -> notifyStart
    NotifyKind.RESULTS -> notifyResults
}

fun UserSettings.withNotify(kind: NotifyKind, value: Boolean): UserSettings = when (kind) {
    NotifyKind.RACE_DAY -> copy(notifyRaceDay = value)
    NotifyKind.T60 -> copy(notifyT60 = value)
    NotifyKind.START -> copy(notifyStart = value)
    NotifyKind.RESULTS -> copy(notifyResults = value)
}
