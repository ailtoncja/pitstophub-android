package com.pitstophub.app.data.repo

import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Driver
import com.pitstophub.app.data.model.Team

/** Casa nomes vindos de fontes diferentes ("M. Verstappen", "Max Verstappen") com o piloto/equipe do catalogo. */
object DriverLookup {

    fun find(drivers: List<Driver>, fullName: String): Driver? {
        val normalized = TextUtil.normalize(fullName)
        if (normalized.isEmpty()) return null
        drivers.firstOrNull { TextUtil.normalize(it.name) == normalized }?.let { return it }
        val last = normalized.split(' ').last()
        return drivers.firstOrNull { TextUtil.lastName(it.name) == last }
    }

    /** Em equipes com dois pilotos ("Lucas Auer / Maro Engel") usa so o primeiro nome da lista. */
    fun findFirst(drivers: List<Driver>, names: String): Driver? =
        names.split(Regex("\\s*/\\s*")).firstNotNullOfOrNull { find(drivers, it.trim()) }

    fun team(category: Category, name: String): Team? {
        val normalized = TextUtil.normalize(name)
        if (normalized.isEmpty()) return null
        return category.teams.firstOrNull { TextUtil.normalize(it.name) == normalized }
            ?: category.teams.firstOrNull {
                val teamName = TextUtil.normalize(it.name)
                teamName.contains(normalized) || normalized.contains(teamName)
            }
    }
}
