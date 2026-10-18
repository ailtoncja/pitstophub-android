package com.pitstophub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.pitstophub.app.R
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/** Abas de toda pagina de categoria; "Como funciona" vem primeiro, como no site. */
enum class CategorySection(val label: Int) {
    RULES(R.string.cat_tab_rules),
    OVERVIEW(R.string.cat_tab_overview),
    CALENDAR(R.string.nav_calendar),
    STANDINGS(R.string.nav_standings),
    GRID(R.string.nav_grid),
}

/**
 * Cabecalho comum das paginas de uma categoria: voltar, nome, estado do dado, seguir e as abas. A categoria deixa de
 * ser um estado escondido: esta sempre na tela.
 */
@Composable
fun CategoryHeader(
    categoryId: String,
    title: String,
    current: CategorySection,
    following: Boolean,
    hasRules: Boolean,
    onBack: () -> Unit,
    onToggleFollow: () -> Unit,
    onSection: (CategorySection) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().background(PitColors.Carbon)) {
        Row(Modifier.height(48.dp).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = PitColors.Silver)
            }
            Text(stringResource(R.string.nav_categories), style = PitText.DataLabel, color = PitColors.Silver)
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = PitText.HeadlineLgMobile, color = Color.White, maxLines = 2)
                DataStateLabel(categoryId, Modifier.padding(top = 6.dp))
            }
            Box(
                Modifier.padding(start = 12.dp).clip(PitShapes.Pill)
                    .background(if (following) PitColors.RacingRed else Color.Transparent)
                    .border(1.dp, if (following) PitColors.RacingRed else Color.White.copy(alpha = 0.3f), PitShapes.Pill)
                    .clickable(onClick = onToggleFollow).heightIn(min = 48.dp).padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (following) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone, null, Modifier.size(18.dp), tint = Color.White)
                    Text(stringResource(if (following) R.string.reel_following else R.string.reel_follow), style = PitText.ButtonLabel, color = Color.White)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            CategorySection.entries.filter { it != CategorySection.RULES || hasRules }.forEach { section ->
                val active = section == current
                Column(
                    Modifier.clickable { onSection(section) }.semantics { role = Role.Tab; selected = active },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(section.label),
                        Modifier.heightIn(min = 44.dp).padding(horizontal = 12.dp, vertical = 12.dp),
                        style = PitText.DataLabel,
                        color = if (active) Color.White else PitColors.Silver,
                    )
                    Box(Modifier.height(2.dp).fillMaxWidth().background(if (active) Color.White else Color.Transparent))
                }
            }
        }
        HorizontalDivider(color = PitColors.Hairline)
    }
}
