package com.pitstophub.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.DrawerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.domain.CategoryGroups
import com.pitstophub.app.domain.CategorySummary
import com.pitstophub.app.ui.account.LocalAccount
import com.pitstophub.app.ui.components.PulsingDot
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import java.time.Instant

/**
 * Menu lateral: todas as categorias agrupadas, cada uma com o icone e a cor da serie, resumo (equipes e etapas),
 * indicador de corrida hoje e coracao de seguir -- o mesmo visual do menu de categorias do site.
 */
@Composable
fun CategoryDrawer(categories: List<Category>, selectedId: String, onSelect: (String) -> Unit, onExplore: () -> Unit, onOpenHub: (String) -> Unit) {
    val portuguese = isPortuguese()
    val account = LocalAccount.current
    val settings by account.settings.collectAsStateWithLifecycle()
    val byId = categories.associateBy { it.id }
    val now = Instant.now()

    ModalDrawerSheet(
        drawerShape = RectangleShape,
        drawerContainerColor = PitColors.Surface,
        windowInsets = DrawerDefaults.windowInsets,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Row(
                Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(painterResource(R.drawable.ic_launcher_logo), null, Modifier.size(40.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.drawer_title).uppercase(), style = PitText.HeadlineMd, color = PitColors.Primary)
                    Text(stringResource(R.string.drawer_subtitle, categories.size), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                }
            }
            Row(
                Modifier.padding(horizontal = 20.dp).fillMaxWidth().clip(PitShapes.Card).background(PitColors.SurfaceHigh)
                    .border(1.dp, PitColors.Hairline, PitShapes.Card).clickable(onClick = onExplore).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.drawer_explore).uppercase(), Modifier.weight(1f), style = PitText.ButtonLabel, color = PitColors.OnSurface)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PitColors.Primary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(thickness = 2.dp, color = PitColors.OutlineVariant)

            CategoryGroups.all.forEach { group ->
                val items = group.ids.mapNotNull(byId::get)
                if (items.isEmpty()) return@forEach
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(group.title).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    HorizontalDivider(Modifier.weight(1f), color = PitColors.HairlineSoft)
                    Text(items.size.toString(), Modifier.padding(start = 10.dp), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                }
                items.forEach { category ->
                    CategoryRow(
                        category = category,
                        selected = category.id == selectedId,
                        following = settings.followsCategory(category.id),
                        liveToday = CategorySummary.isActiveToday(category, now),
                        portuguese = portuguese,
                        onClick = { onSelect(category.id) },
                        onLongClick = { onOpenHub(category.id) },
                        onToggleFollow = { account.toggleCategory(category.id, category.name) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    category: Category,
    selected: Boolean,
    following: Boolean,
    liveToday: Boolean,
    portuguese: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFollow: () -> Unit,
) {
    val accent = CategoryVisuals.accent(category.id)
    val summary = CategorySummary.of(category)
    Row(
        Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().clip(PitShapes.Soft)
            .background(if (selected) PitColors.PrimaryContainer.copy(alpha = 0.16f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(48.dp).background(if (selected) PitColors.PrimaryContainer else androidx.compose.ui.graphics.Color.Transparent))
        Box(
            Modifier.padding(start = 12.dp).size(40.dp).clip(PitShapes.Card).background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.4f), PitShapes.Card),
            contentAlignment = Alignment.Center,
        ) {
            Icon(CategoryVisuals.icon(category.id), null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    category.displayFullName(portuguese),
                    style = PitText.BodyMd.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium),
                    color = if (selected) PitColors.Primary else PitColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (liveToday) PulsingDot(PitColors.PrimaryContainer)
            }
            Text(
                listOfNotNull(
                    summary.teams.takeIf { it > 0 }?.let { stringResource(R.string.summary_teams, it) },
                    summary.rounds.takeIf { it > 0 }?.let { stringResource(R.string.summary_rounds, it) },
                ).joinToString(" • ").ifEmpty { category.name }.uppercase(),
                style = PitText.DataLabelXs,
                color = PitColors.OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.size(44.dp).clickable(onClick = onToggleFollow), contentAlignment = Alignment.Center) {
            Icon(
                if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                stringResource(if (following) R.string.action_unfollow else R.string.action_follow),
                tint = if (following) PitColors.PrimaryContainer else PitColors.OnSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
