package com.pitstophub.app.ui.account

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.LocalAppContainer
import com.pitstophub.app.ui.components.FollowKind
import com.pitstophub.app.ui.components.FollowToggle
import com.pitstophub.app.ui.components.NetworkImage
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.PitSheet
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.teams.parseTeamColor
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

private data class PriorityEntry(
    val key: String,
    val isTeam: Boolean,
    val categoryId: String,
    val categoryName: String,
    val id: String,
    val name: String,
    val image: String?,
    val color: Color,
)

/**
 * Gerencia o que a pessoa acompanha: a ordem de prioridade dos avisos de resultado e a lista de categorias,
 * equipes e pilotos para seguir. Com [onboarding] vira a escolha inicial de uma conta nova.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavoritesSheet(onDismiss: () -> Unit, onboarding: Boolean = false) {
    val account = LocalAccount.current
    val container = LocalAppContainer.current
    val settings by account.settings.collectAsStateWithLifecycle()
    val categories by produceState<List<Category>>(emptyList()) { value = container.categories.categories() }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val portuguese = isPortuguese()

    val entries = remember(categories, settings.priorityFollowIds) {
        val byId = categories.associateBy { it.id }
        settings.priorityFollowIds.mapNotNull { key ->
            val parts = key.split("::")
            if (parts.size != 3) return@mapNotNull null
            val (kind, categoryId, id) = parts
            val category = byId[categoryId] ?: return@mapNotNull null
            if (kind == "team") {
                val team = category.teams.firstOrNull { it.id == id } ?: return@mapNotNull null
                PriorityEntry(key, true, categoryId, category.name, id, team.name, team.badge, parseTeamColor(team.color))
            } else {
                val driver = category.drivers.firstOrNull { it.id == id } ?: return@mapNotNull null
                val team = category.teams.firstOrNull { it.id == driver.teamId }
                PriorityEntry(key, false, categoryId, category.name, id, driver.name, driver.cutout ?: driver.image, parseTeamColor(team?.color))
            }
        }
    }

    val finish = {
        account.finishOnboarding()
        onDismiss()
    }

    PitSheet(
        title = stringResource(if (onboarding) R.string.onboarding_title else R.string.favorites_title),
        onDismiss = if (onboarding) finish else onDismiss,
        expanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            if (onboarding) Text(stringResource(R.string.onboarding_body), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.favorites_priority), style = PitText.DataLabel, color = PitColors.OnSurface)
                Text(stringResource(R.string.favorites_priority_hint), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                if (entries.isEmpty()) {
                    Text(
                        stringResource(R.string.favorites_none),
                        Modifier.fillMaxWidth().border(1.dp, PitColors.OutlineVariant, PitShapes.Soft).padding(20.dp),
                        style = PitText.BodySm,
                        color = PitColors.OnSurfaceVariant,
                    )
                } else {
                    entries.forEachIndexed { index, entry ->
                        PriorityRow(
                            position = index + 1,
                            entry = entry,
                            canMoveUp = index > 0,
                            canMoveDown = index < entries.lastIndex,
                            onUp = { account.movePriority(entry.key, -1) },
                            onDown = { account.movePriority(entry.key, 1) },
                            onRemove = { if (entry.isTeam) account.removeTeam(entry.categoryId, entry.id) else account.removeDriver(entry.categoryId, entry.id) },
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.favorites_browse), style = PitText.DataLabel, color = PitColors.OnSurface)
                categories.forEach { category ->
                    val open = openId == category.id
                    val followed = category.teams.count { settings.followsTeam(category.id, it.id) } + category.drivers.count { settings.followsDriver(category.id, it.id) }
                    PitCard(Modifier.fillMaxWidth().animateContentSize()) {
                        Column {
                            Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).clickable { openId = if (open) null else category.id }.padding(vertical = 12.dp, horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(Modifier.width(3.dp).height(24.dp).background(CategoryVisuals.accent(category.id)))
                                    Text(category.displayFullName(portuguese), Modifier.weight(1f), style = PitText.BodyMd, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (followed > 0) Text("♥ $followed", style = PitText.DataLabelSm, color = PitColors.Primary)
                                    Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = PitColors.OnSurfaceVariant)
                                }
                                FollowToggle(
                                    FollowKind.CATEGORY,
                                    settings.followsCategory(category.id),
                                    { account.toggleCategory(category.id, category.name) },
                                    Modifier.padding(end = 8.dp),
                                    size = 36.dp,
                                )
                            }
                            if (open) {
                                HorizontalDivider(color = PitColors.HairlineSoft)
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                    if (category.teams.isNotEmpty()) {
                                        Text(stringResource(R.string.favorites_teams), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            category.teams.forEach { team ->
                                                FollowChip(team.name, team.badge, settings.followsTeam(category.id, team.id)) { account.toggleTeam(category.id, team.id, team.name) }
                                            }
                                        }
                                    }
                                    if (category.drivers.isNotEmpty()) {
                                        Text(stringResource(R.string.favorites_drivers), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            category.drivers.forEach { driver ->
                                                FollowChip(driver.name, driver.cutout ?: driver.image, settings.followsDriver(category.id, driver.id), round = true) {
                                                    account.toggleDriver(category.id, driver.id, driver.name)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (onboarding) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    PitOutlineButton(stringResource(R.string.onboarding_skip), finish, dim = true)
                    PitPrimaryButton(stringResource(R.string.onboarding_done), finish)
                }
            }
        }
    }
}

@Composable
private fun PriorityRow(
    position: Int,
    entry: PriorityEntry,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    PitCard(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(position.toString(), Modifier.width(18.dp), style = PitText.DataLabel, color = PitColors.OnSurfaceVariant)
            Box(Modifier.size(38.dp).clip(if (entry.isTeam) PitShapes.Card else androidx.compose.foundation.shape.CircleShape).background(PitColors.SurfaceHighest), contentAlignment = Alignment.Center) {
                NetworkImage(
                    entry.image,
                    Modifier.size(38.dp),
                    contentScale = if (entry.isTeam) ContentScale.Fit else ContentScale.Crop,
                    alignment = if (entry.isTeam) Alignment.Center else Alignment.TopCenter,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(entry.name, style = PitText.BodyMd, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.categoryName} • ${stringResource(if (entry.isTeam) R.string.favorites_team else R.string.favorites_driver)}",
                    style = PitText.DataLabelXs,
                    color = entry.color,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onUp, enabled = canMoveUp, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.favorites_move_up), tint = if (canMoveUp) PitColors.OnSurface else PitColors.OutlineVariant)
            }
            IconButton(onClick = onDown, enabled = canMoveDown, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.favorites_move_down), tint = if (canMoveDown) PitColors.OnSurface else PitColors.OutlineVariant)
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(34.dp)) {
                Icon(Icons.Filled.Close, stringResource(R.string.favorites_remove), tint = PitColors.OnSurfaceVariant)
            }
        }
    }
}

@Composable
private fun FollowChip(label: String, image: String?, active: Boolean, round: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.clip(PitShapes.Card)
            .background(if (active) androidx.compose.ui.graphics.Color.White else PitColors.SurfaceHigh)
            .border(1.dp, if (active) androidx.compose.ui.graphics.Color.White else PitColors.Hairline, PitShapes.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!image.isNullOrBlank()) {
            NetworkImage(
                image,
                Modifier.size(20.dp).clip(if (round) androidx.compose.foundation.shape.CircleShape else PitShapes.Sharp),
                contentScale = if (round) ContentScale.Crop else ContentScale.Fit,
                alignment = if (round) Alignment.TopCenter else Alignment.Center,
            )
        }
        Text(label, style = PitText.BodySm, color = if (active) PitColors.Primary else PitColors.OnSurface, maxLines = 1)
    }
}
