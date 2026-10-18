package com.pitstophub.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.repo.TextUtil
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.ui.LocalAppContainer
import com.pitstophub.app.ui.components.PitSheet
import com.pitstophub.app.ui.components.PitTextField
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

private enum class HitKind(val label: Int, val icon: ImageVector) {
    CATEGORY(R.string.search_kind_category, Icons.Filled.Flag),
    DRIVER(R.string.search_kind_driver, Icons.Filled.Person),
    TEAM(R.string.search_kind_team, Icons.Filled.Groups),
    RACE(R.string.search_kind_race, Icons.Filled.CalendarMonth),
}

private data class Hit(val kind: HitKind, val title: String, val subtitle: String, val categoryId: String, val id: String)

private const val MAX_PER_KIND = 6

/** Busca em todas as categorias do catalogo: categorias, pilotos, equipes e etapas. */
@Composable
fun SearchSheet(
    onDismiss: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onOpenDriver: (categoryId: String, driverId: String) -> Unit,
    onOpenTeam: (categoryId: String, teamId: String) -> Unit,
    onOpenRace: (categoryId: String, raceId: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val portuguese = isPortuguese()
    val locale = currentLocale()
    val categories by produceState<List<Category>>(emptyList()) { value = container.categories.categories() }
    var query by rememberSaveable { mutableStateOf("") }

    val hits = remember(categories, query, portuguese) { search(categories, query, portuguese, locale) }

    PitSheet(title = stringResource(R.string.search_title), onDismiss = onDismiss, expanded = true) {
        PitTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.search_placeholder),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        Column(Modifier.padding(top = 16.dp)) {
            when {
                query.trim().length < 2 -> Text(stringResource(R.string.search_hint), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                hits.isEmpty() -> Text(stringResource(R.string.search_empty, query.trim()), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                else -> Column(Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt)) {
                    hits.forEachIndexed { index, hit ->
                        if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                onDismiss()
                                when (hit.kind) {
                                    HitKind.CATEGORY -> onOpenCategory(hit.categoryId)
                                    HitKind.DRIVER -> onOpenDriver(hit.categoryId, hit.id)
                                    HitKind.TEAM -> onOpenTeam(hit.categoryId, hit.id)
                                    HitKind.RACE -> onOpenRace(hit.categoryId, hit.id)
                                }
                            }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(36.dp).clip(PitShapes.Card).background(PitColors.SurfaceHigh), contentAlignment = Alignment.Center) {
                                Icon(hit.kind.icon, null, tint = PitColors.Primary, modifier = Modifier.size(18.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(hit.title, style = PitText.BodyMd, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(hit.subtitle, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Text(stringResource(hit.kind.label), style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Casa o comeco de alguma palavra ("ham" acha "Hamilton", mas nao "championship"). */
private fun String.startsWithWord(query: String): Boolean = " $this".contains(" $query")

private fun search(categories: List<Category>, query: String, portuguese: Boolean, locale: java.util.Locale): List<Hit> {
    val q = TextUtil.normalize(query)
    if (q.length < 2) return emptyList()
    val buckets = HitKind.entries.associateWith { mutableListOf<Hit>() }
    fun add(hit: Hit) {
        val bucket = buckets.getValue(hit.kind)
        if (bucket.size < MAX_PER_KIND) bucket += hit
    }
    for (category in categories) {
        if (TextUtil.normalize("${category.name} ${category.fullName} ${category.enFullName.orEmpty()}").startsWithWord(q)) {
            add(Hit(HitKind.CATEGORY, category.displayFullName(portuguese), category.name, category.id, category.id))
        }
        for (driver in category.drivers) {
            if (TextUtil.normalize("${driver.name} ${driver.number}").startsWithWord(q)) {
                val team = category.teams.firstOrNull { it.id == driver.teamId }
                add(Hit(HitKind.DRIVER, driver.name, listOfNotNull(category.name, team?.name).joinToString(" • "), category.id, driver.id))
            }
        }
        for (team in category.teams) {
            if (TextUtil.normalize(team.name).startsWithWord(q)) add(Hit(HitKind.TEAM, team.name, category.name, category.id, team.id))
        }
        for (race in category.calendar) {
            if (TextUtil.normalize("${race.name} ${race.enName.orEmpty()} ${race.circuit} ${race.location}").startsWithWord(q)) {
                add(Hit(HitKind.RACE, race.displayName(portuguese), "${category.name} • ${RaceFormat.monthDay(race.date, locale)}", category.id, race.id))
            }
        }
    }
    return HitKind.entries.flatMap { buckets.getValue(it) }
}
