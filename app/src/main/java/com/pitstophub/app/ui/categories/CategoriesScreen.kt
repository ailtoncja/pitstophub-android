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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.height
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.components.DataStateLabel
import com.pitstophub.app.ui.components.HeroBackdrop
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
    showTopBar: Boolean = true,
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
        if (showTopBar) PitDetailTopBar(title = stringResource(R.string.categories_title), onBack = onBack)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(stringResource(R.string.categories_explore), style = PitText.HeadlineLgMobile, color = PitColors.OnSurface)
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
                        Text(stringResource(group.title), style = PitText.HeadlineMd, color = PitColors.OnSurface)
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
    @Suppress("UNUSED_PARAMETER") liveToday: Boolean,
    onClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val now = remember { Instant.now() }
    val next = RaceTime.nextRace(category.calendar, now)
    val locale = currentLocale()
    Box(
        Modifier.fillMaxWidth().height(168.dp).clip(PitShapes.Card).border(1.dp, PitColors.Hairline, PitShapes.Card).clickable(onClick = onClick),
    ) {
        HeroBackdrop(categoryId = category.id, categoryName = category.name, race = null, compactCar = true)
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, end = 72.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                if (portuguese) category.fullName else category.enFullName ?: category.fullName,
                style = PitText.HeadlineMd,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                next?.let { "${RaceFormat.monthDay(it.date, locale)} · ${if (portuguese) it.name else it.enName ?: it.name}" } ?: stringResource(R.string.categories_no_next),
                style = PitText.BodySm,
                color = PitColors.Silver,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            DataStateLabel(category.id, Modifier.padding(top = 2.dp))
        }
        FollowToggle(FollowKind.CATEGORY, following, onToggleFollow, Modifier.align(Alignment.TopEnd).padding(10.dp))
    }
}
