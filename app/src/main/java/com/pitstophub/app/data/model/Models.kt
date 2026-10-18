package com.pitstophub.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Modelos espelhados de src/types.ts do PWA. O catalogo estatico (assets/motorsport_data.json) e
// exportado desse arquivo, entao os nomes de campo precisam continuar iguais.

@Serializable
enum class RaceStatus {
    @SerialName("upcoming") UPCOMING,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class SessionKind { FP1, FP2, FP3, SPRINT_QUALIFYING, SPRINT, QUALIFYING, RACE }

@Serializable
data class SessionSlot(val kind: SessionKind, val startsAt: String)

@Serializable
data class Driver(
    val id: String,
    val name: String,
    val number: String = "",
    val nationality: String = "",
    val teamId: String = "",
    val image: String? = null,
    val cutout: String? = null,
)

@Serializable
data class Team(
    val id: String,
    val name: String,
    val color: String = "#E10600",
    val car: String? = null,
    val logo: String? = null,
    val badge: String? = null,
    val clearart: String? = null,
    @SerialName("class") val raceClass: String? = null,
    val principal: String? = null,
)

@Serializable
data class Race(
    val id: String,
    val name: String,
    val enName: String? = null,
    val location: String = "",
    val enLocation: String? = null,
    val date: String,
    val startsAt: String? = null,
    val circuit: String = "",
    val status: RaceStatus = RaceStatus.UPCOMING,
    val winner: String? = null,
    val round: Int? = null,
    val sessions: List<SessionSlot> = emptyList(),
    /** Coordenadas do circuito: so a fonte ao vivo (Jolpica) informa. Servem para a previsao do tempo. */
    val lat: Double? = null,
    val lng: Double? = null,
) {
    /** "Rodada 33" nao diz nada como titulo: em portugues, cai no nome em ingles quando ele existe. */
    fun displayName(portuguese: Boolean): String = when {
        !portuguese -> enName ?: name
        GENERIC_ROUND.matches(name) && !enName.isNullOrBlank() -> enName
        else -> name
    }
    fun displayLocation(portuguese: Boolean): String = if (portuguese) location else enLocation ?: location
}

private val GENERIC_ROUND = Regex("""^Rodada \d+$""")

@Serializable
data class StandingItem(
    val position: Int,
    val name: String,
    val points: Double,
    val team: String? = null,
    val extra: String? = null,
)

@Serializable
data class StandingsClassGroup(val classLabel: String, val entries: List<StandingItem>)

@Serializable
data class CategoryStandings(
    val drivers: List<StandingItem>? = null,
    val constructors: List<StandingItem>? = null,
    val teams: List<StandingItem>? = null,
    // Classificacao por classe (Endurance Brasil): nunca misturar classes numa unica lista.
    val driverClasses: List<StandingsClassGroup>? = null,
) {
    val isEmpty: Boolean
        get() = drivers.isNullOrEmpty() && constructors.isNullOrEmpty() && teams.isNullOrEmpty() && driverClasses.isNullOrEmpty()
}

/** Uma secao de "Regras e Formato" (formato do fim de semana, pontuacao, regulamento). */
@Serializable
data class RuleSection(
    val title: String,
    val enTitle: String? = null,
    val body: String,
    val enBody: String? = null,
) {
    fun displayTitle(portuguese: Boolean): String = if (portuguese) title else enTitle ?: title
    fun displayBody(portuguese: Boolean): String = if (portuguese) body else enBody ?: body
}

@Serializable
data class Category(
    val id: String,
    val name: String,
    val fullName: String,
    val enFullName: String? = null,
    val description: String = "",
    val enDescription: String? = null,
    val longDescription: String = "",
    val enLongDescription: String? = null,
    val rulesFormat: List<RuleSection> = emptyList(),
    val icon: String = "",
    val teams: List<Team> = emptyList(),
    val drivers: List<Driver> = emptyList(),
    val calendar: List<Race> = emptyList(),
    val standings: CategoryStandings? = null,
) {
    fun displayFullName(portuguese: Boolean): String = if (portuguese) fullName else enFullName ?: fullName
    fun displayDescription(portuguese: Boolean): String = if (portuguese) description else enDescription ?: description
    fun displayLongDescription(portuguese: Boolean): String = if (portuguese) longDescription else enLongDescription ?: longDescription
}

@Serializable
data class TeamBio(val pt: String, val en: String) {
    fun text(portuguese: Boolean): String = if (portuguese) pt else en
}

@Serializable
data class CatalogFile(
    val categories: List<Category>,
    val teamBios: Map<String, TeamBio> = emptyMap(),
)

/** Categoria ja combinada com os dados ao vivo (quando disponiveis). */
data class CategoryData(
    val category: Category,
    val season: String,
    /** Calendario veio de uma fonte ao vivo (nao do catalogo estatico). */
    val liveCalendar: Boolean,
    val liveStandings: Boolean,
    /** Uma busca ao vivo falhou e o app caiu no catalogo estatico. */
    val offline: Boolean,
)
