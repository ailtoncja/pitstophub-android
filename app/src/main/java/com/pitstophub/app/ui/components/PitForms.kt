package com.pitstophub.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/** Folha inferior no estilo do app: fundo Surface, cantos retos, titulo com a barra vermelha. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PitSheet(
    title: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    expanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = expanded),
        containerColor = PitColors.Surface,
        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
        dragHandle = {
            Box(Modifier.padding(top = 12.dp, bottom = 4.dp).width(40.dp).height(4.dp).background(PitColors.OutlineVariant))
        },
    ) {
        Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    Modifier.weight(1f).drawBehind { drawRect(PitColors.Primary, size = Size(4.dp.toPx(), size.height)) }.padding(start = 12.dp),
                    style = PitText.HeadlineMd,
                    color = PitColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, stringResource(R.string.action_close), tint = PitColors.OnSurfaceVariant)
                }
            }
            content()
        }
    }
}

@Composable
fun PitTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true,
    supportingText: String? = null,
    isError: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = PitText.DataLabelSm) },
        supportingText = supportingText?.let { { Text(it, style = PitText.DataLabelSm) } },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        shape = PitShapes.Card,
        textStyle = PitText.BodyMd,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = PitColors.OnSurface,
            unfocusedTextColor = PitColors.OnSurface,
            disabledTextColor = PitColors.OnSurfaceVariant,
            focusedBorderColor = PitColors.Primary,
            unfocusedBorderColor = PitColors.OutlineVariant,
            errorBorderColor = PitColors.Error,
            focusedLabelColor = PitColors.Primary,
            unfocusedLabelColor = PitColors.OnSurfaceVariant,
            errorLabelColor = PitColors.Error,
            cursorColor = PitColors.Primary,
            focusedContainerColor = PitColors.SurfaceLowest,
            unfocusedContainerColor = PitColors.SurfaceLowest,
            focusedSupportingTextColor = PitColors.OnSurfaceVariant,
            unfocusedSupportingTextColor = PitColors.OnSurfaceVariant,
        ),
    )
}

/** Linha com titulo, descricao opcional e interruptor. */
@Composable
fun PitSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier.fillMaxWidth().clickable(enabled = enabled) { onCheckedChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = PitText.BodyMd, color = if (enabled) PitColors.OnSurface else PitColors.OnSurfaceVariant)
            if (description != null) Text(description, style = PitText.BodySm.copy(fontSize = 13.sp, lineHeight = 18.sp), color = PitColors.OnSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = androidx.compose.ui.graphics.Color.White,
                uncheckedThumbColor = PitColors.OnSurfaceVariant,
                uncheckedTrackColor = PitColors.SurfaceHighest,
                uncheckedBorderColor = PitColors.OutlineVariant,
            ),
        )
    }
}

/** Alternador de abas: pilulas, a ativa branca. */
@Composable
fun PitSegmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(PitShapes.Pill).background(PitColors.Asphalt)
            .border(1.dp, PitColors.Hairline, PitShapes.Pill)
            .horizontalScroll(rememberScrollState()).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val active = index == selected
            Box(
                Modifier.clip(PitShapes.Pill)
                    .background(if (active) Color.White else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = PitText.DataLabel,
                    color = if (active) Color.Black else PitColors.Silver,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Bloco de numero: rotulo pequeno em cima, valor grande embaixo, legenda opcional. */
@Composable
fun PitStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    caption: String? = null,
) {
    PitCard(modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = PitText.BodySm, color = PitColors.Silver, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(value, style = PitText.DataValue.copy(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = if (highlight) FontWeight.ExtraBold else FontWeight.Bold), color = Color.White, maxLines = 1)
            if (caption != null) {
                Spacer(Modifier.height(2.dp))
                Text(caption, style = PitText.BodySm, color = PitColors.Silver, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

enum class FollowKind { CATEGORY, TEAM, DRIVER }

/** Botao quadrado de seguir: coracao (categoria), estrela (equipe) ou marcador (piloto), como no site. */
@Composable
fun FollowToggle(kind: FollowKind, following: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 40.dp) {
    val icon = when (kind) {
        FollowKind.CATEGORY -> if (following) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
        FollowKind.TEAM -> if (following) Icons.Filled.Star else Icons.Filled.StarBorder
        FollowKind.DRIVER -> if (following) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder
    }
    Box(
        modifier.size(maxOf(size, 48.dp)).clip(CircleShape)
            .background(if (following) PitColors.RacingRed else Color.Transparent)
            .border(1.dp, if (following) PitColors.RacingRed else Color.White.copy(alpha = 0.25f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = stringResource(if (following) R.string.action_unfollow else R.string.action_follow),
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Pilula de filtro (grupos de categoria, classes de equipe): ativa branca. */
@Composable
fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(PitShapes.Pill)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), PitShapes.Pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = PitText.DataLabel, color = if (selected) Color.Black else PitColors.Silver, maxLines = 1)
    }
}
