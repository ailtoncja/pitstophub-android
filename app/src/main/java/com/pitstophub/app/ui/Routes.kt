package com.pitstophub.app.ui

import kotlinx.serialization.Serializable

// Rotas tipadas do Navigation Compose. Inicio, Agenda e Categorias sao abas da barra inferior; Perfil abre uma folha.

@Serializable
data object HomeRoute

/** Todas as largadas a frente, de todas as categorias. */
@Serializable
data object AgendaRoute

@Serializable
data object CalendarRoute

@Serializable
data object StandingsRoute

@Serializable
data object GridRoute

@Serializable
data class RaceRoute(val categoryId: String, val raceId: String)

@Serializable
data class TeamRoute(val categoryId: String, val teamId: String)

@Serializable
data class DriverRoute(val categoryId: String, val driverId: String)

/** "Explorar categorias": todas as series, com busca e filtro. */
@Serializable
data object CategoriesRoute

/** Pagina de uma categoria (resumo, equipes, pilotos, regras). */
@Serializable
data class CategoryRoute(val categoryId: String)

/** "Como funciona": regras da categoria (formato do fim de semana, pontuacao...). `section` abre ja numa secao. */
@Serializable
data class RulesRoute(val categoryId: String, val section: Int = 0)

@Serializable
data object ChampionsRoute
