package com.pitstophub.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material3.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.repo.TextUtil
import com.pitstophub.app.domain.CategoryGroups
import com.pitstophub.app.domain.CategorySummary
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.components.FilterPill
import com.pitstophub.app.ui.components.FollowKind
import com.pitstophub.app.ui.components.FollowToggle
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitTextField
import com.pitstophub.app.ui.components.PulsingDot
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import java.time.Instant

/** "Explorar categorias": todas as series agrupadas, com busca, filtro por grupo, seguir e atalho para a pagina de cada uma. */
@Composable
fun CategoriesScreen(
    categories: List<Category>,
    onBack: () -> Unit,
    onOpenCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val portuguese = isPortuguese()
    val account = LocalAccount.current
    val settings by account.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var groupIndex by rememberSaveable { mutableIntStateOf(-1) } // -1 = todas
    val now = remember { Instant.now() }

    val q = TextUtil.normalize(query)
    val byId = categories.associateBy { it.id }
    val sections = CategoryGroups.all.mapIndexedNotNull { index, group ->
        if (groupIndex != -1 && groupIndex != index) return@mapIndexedNotNull null
        val items = group.ids.mapNotNull(byId::get).filter {
            q.isEmpty() || TextUtil.normalize("${it.name} ${it.fullName} ${it.enFullName.orEmpty()} ${it.description}").contains(q)
        }
        if (items.isEmpty()) null else group to items
    }

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(title = stringResource(R.string.categories_title), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.categories_kicker).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                        Text(stringResource(R.string.categories_count, categories.size), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    }
                    Text(stringResource(R.string.categories_explore).uppercase(), style = PitText.HeadlineLgMobile, color = PitColors.OnSurface)
                    Text(stringResource(R.string.categories_lead), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                    PitTextField(query, { query = it }, stringResource(R.string.categories_search))
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill(stringResource(R.string.filter_all), groupIndex == -1, { groupIndex = -1 })
                        CategoryGroups.all.forEachIndexed { index, group ->
                            FilterPill(stringResource(group.title), groupIndex == index, { groupIndex = index })
                        }
                    }
                }
            }
            if (sections.isEmpty()) {
                item(key = "empty") { Text(stringResource(R.string.categories_none), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant) }
            }
            sections.forEach { (group, items) ->
                item(key = "title-${group.ids.first()}") {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(group.title).uppercase(), style = PitText.DataLabel, color = PitColors.OnSurface)
                        Text(stringResource(R.string.categories_count, items.size), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    }
                }
                items.forEach { category ->
                    item(key = category.id) {
                        CategoryCard(
                            category = category,
                            portuguese = portuguese,
                            following = settings.followsCategory(category.id),
                            liveToday = CategorySummary.isActiveToday(category, now),
                            onClick = { onOpenCategory(category.id) },
                            onToggleFollow = { account.toggleCategory(category.id, category.name) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryCard(
    category: Category,
    portuguese: Boolean,
    following: Boolean,
    liveToday: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val accent = CategoryVisuals.accent(category.id)
    val summary = CategorySummary.of(category)
    PitCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(PitShapes.Card).background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.45f), PitShapes.Card),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CategoryVisuals.icon(category.id), null, tint = accent, modifier = Modifier.size(28.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        category.displayFullName(portuguese).uppercase(),
                        style = PitText.HeadlineMd.copy(fontSize = 18.sp, lineHeight = 22.sp),
                        color = PitColors.OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (liveToday) PulsingDot(PitColors.PrimaryContainer)
                }
                Text(category.displayDescription(portuguese), style = PitText.BodySm, color = PitColors.OnSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        summary.teams.takeIf { it > 0 }?.let { stringResource(R.string.summary_teams, it) },
                        summary.rounds.takeIf { it > 0 }?.let { stringResource(R.string.summary_rounds, it) },
                    ).joinToString(" • ").uppercase(),
                    style = PitText.DataLabelXs,
                    color = accent,
                    maxLines = 1,
                )
            }
            FollowToggle(FollowKind.CATEGORY, following, onToggleFollow)
        }
    }
}
