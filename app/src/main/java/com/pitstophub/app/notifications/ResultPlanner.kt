package com.pitstophub.app.notifications

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.data.repo.DriverLookup
import com.pitstophub.app.data.repo.RaceResultRow
import com.pitstophub.app.domain.UserSettings

/** Aviso de resultado: onde terminaram os pilotos e equipes que a pessoa segue. */
data class ResultAlert(val categoryId: String, val raceId: String, val title: String, val body: String) {
    val key: String get() = "result:$categoryId:$raceId"
}

/**
 * Porte de `scripts/send-result-notifications.mjs`. Na F1 usa o resultado completo da Jolpica (posicao de cada
 * seguido, na ordem de prioridade escolhida). Nas outras categorias o app nao tem a tabela de resultados (o
 * servidor le texto da TheSportsDB com chave propria), entao so avisa quando um seguido VENCE a etapa.
 */
object ResultPlanner {

    fun forF1(category: Category, race: Race, results: List<RaceResultRow>, settings: UserSettings): ResultAlert? {
        val pt = settings.isPortuguese
        val raceName = race.displayName(pt)

        // posicao de cada linha do resultado, ligada ao piloto do catalogo
        val placed = results.mapNotNull { row ->
            val position = row.finishPosition ?: return@mapNotNull null
            DriverLookup.find(category.drivers, row.driver)?.let { it to position }
        }

        val covered = mutableSetOf<Int>()
        val driverMessages = linkedMapOf<String, String>()
        for (key in settings.followedDriverIds.filter { it.startsWith("${category.id}::") }) {
            val driverId = key.substringAfter("::")
            val driver = category.drivers.firstOrNull { it.id == driverId } ?: continue
            val position = placed.firstOrNull { it.first.id == driverId }?.second ?: continue
            covered += position
            driverMessages[driverId] = if (pt) "${driver.name} terminou em ${position}º no $raceName!" else "${driver.name} finished P$position in the $raceName."
        }

        val teamMessages = linkedMapOf<String, String>()
        for (key in settings.followedTeamIds.filter { it.startsWith("${category.id}::") }) {
            val teamId = key.substringAfter("::")
            val team = category.teams.firstOrNull { it.id == teamId } ?: continue
            val positions = placed.filter { it.first.teamId == teamId }.map { it.second }.distinct()
                .filter { it !in covered }.sorted()
            if (positions.isEmpty()) continue
            teamMessages[teamId] = if (pt) {
                "${team.name} terminou com ${if (positions.size > 1) "os carros em" else "o carro em"} ${places(positions, true)} no $raceName!"
            } else {
                "${team.name} finished with ${if (positions.size > 1) "cars in" else "the car in"} ${places(positions, false)} in the $raceName."
            }
        }
        return compose(category, race, settings, driverMessages, teamMessages)
    }

    /** Categorias sem tabela de resultados: avisa quando um piloto (ou equipe) seguido venceu. */
    fun forWinner(category: Category, race: Race, settings: UserSettings): ResultAlert? {
        val winner = race.winner?.takeIf { it.isNotBlank() } ?: return null
        val pt = settings.isPortuguese
        val raceName = race.displayName(pt)
        val driverMessages = linkedMapOf<String, String>()
        val teamMessages = linkedMapOf<String, String>()
        for (part in winner.split(Regex("\\s*/\\s*"))) {
            val driver = DriverLookup.find(category.drivers, part.trim()) ?: continue
            if (settings.followsDriver(category.id, driver.id)) {
                driverMessages[driver.id] = if (pt) "${driver.name} venceu $raceName!" else "${driver.name} won the $raceName."
            }
            val team = category.teams.firstOrNull { it.id == driver.teamId } ?: continue
            if (settings.followsTeam(category.id, team.id) && driver.id !in driverMessages) {
                teamMessages[team.id] = if (pt) "${team.name} venceu $raceName com ${driver.name}!" else "${team.name} won the $raceName with ${driver.name}."
            }
        }
        return compose(category, race, settings, driverMessages, teamMessages)
    }

    /** Segue `priority_follow_ids`: quem esta no topo da lista aparece primeiro; quem sobrou entra no fim. */
    private fun compose(
        category: Category,
        race: Race,
        settings: UserSettings,
        driverMessages: Map<String, String>,
        teamMessages: Map<String, String>,
    ): ResultAlert? {
        if (driverMessages.isEmpty() && teamMessages.isEmpty()) return null
        val ordered = mutableListOf<String>()
        val usedDrivers = mutableSetOf<String>()
        val usedTeams = mutableSetOf<String>()
        for (key in settings.priorityFollowIds) {
            val parts = key.split("::")
            if (parts.size != 3 || parts[1] != category.id) continue
            val (kind, _, id) = parts
            if (kind == "driver" && id in driverMessages) {
                ordered += driverMessages.getValue(id); usedDrivers += id
            } else if (kind == "team" && id in teamMessages) {
                ordered += teamMessages.getValue(id); usedTeams += id
            }
        }
        driverMessages.filterKeys { it !in usedDrivers }.values.forEach(ordered::add)
        teamMessages.filterKeys { it !in usedTeams }.values.forEach(ordered::add)
        return ResultAlert(category.id, race.id, AlertTexts(settings.isPortuguese).resultTitle(category.name), ordered.joinToString(" "))
    }

    /** "2º, 4º e 5º" / "P2, P4 and P5". */
    internal fun places(positions: List<Int>, portuguese: Boolean): String {
        val labels = positions.map { if (portuguese) "${it}º" else "P$it" }
        return when {
            labels.size <= 1 -> labels.firstOrNull().orEmpty()
            else -> labels.dropLast(1).joinToString(", ") + (if (portuguese) " e " else " and ") + labels.last()
        }
    }
}
