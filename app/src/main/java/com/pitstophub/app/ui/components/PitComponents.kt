package com.pitstophub.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.pitstophub.app.R
import com.pitstophub.app.ui.theme.ClippedCornerShape
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.ui.theme.SkewShape
import com.pitstophub.app.ui.theme.hardShadow
import kotlinx.coroutines.delay
import java.time.Instant

enum class TopLevelTab(val label: Int, val filled: ImageVector, val outlined: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home),
    Calendar(R.string.nav_calendar, Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday),
    Standings(R.string.nav_standings, Icons.Filled.Leaderboard, Icons.Outlined.Leaderboard),
    Grid(R.string.nav_grid, Icons.Filled.Group, Icons.Outlined.Group),
}

/** Relogio que atualiza a cada [intervalMs]; serve para a contagem regressiva. */
@Composable
fun rememberNow(intervalMs: Long = 1_000): State<Instant> = produceState(Instant.now(), intervalMs) {
    while (true) {
        delay(intervalMs)
        value = Instant.now()
    }
}

// --- barras ---------------------------------------------------------------------------------------

@Composable
fun PitTopBar(
    onMenu: () -> Unit,
    onSearch: () -> Unit,
    onAlerts: () -> Unit,
    onProfile: () -> Unit,
    profileInitials: String?,
    alertsDot: Boolean,
    modifier: Modifier = Modifier,
    onBrandTap: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(PitColors.Surface)) {
        Row(
            Modifier.statusBarsPadding().height(64.dp).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(Icons.Filled.Menu, contentDescription = stringResource(R.string.cd_menu), tint = PitColors.Primary)
            }
            Text(
                text = stringResource(R.string.brand_title),
                modifier = Modifier.weight(1f).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onBrandTap,
                ),
                style = PitText.HeadlineLgMobile.copy(fontSize = 24.sp, letterSpacing = (-0.05).em),
                color = PitColors.Primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search), tint = PitColors.OnSurfaceVariant)
            }
            IconButton(onClick = onAlerts) {
                Box {
                    Icon(Icons.Filled.Notifications, contentDescription = stringResource(R.string.cd_alerts), tint = PitColors.OnSurfaceVariant)
                    if (alertsDot) {
                        Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(PitColors.PrimaryContainer))
                    }
                }
            }
            IconButton(onClick = onProfile) {
                Box(
                    Modifier.size(32.dp).clip(CircleShape).background(if (profileInitials != null) PitColors.PrimaryContainer else PitColors.SurfaceHighest)
                        .border(1.dp, PitColors.OutlineVariant, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (profileInitials != null) {
                        Text(profileInitials, style = PitText.DataLabelSm.copy(fontWeight = FontWeight.Bold), color = PitColors.OnPrimaryContainer, maxLines = 1)
                    } else {
                        Icon(Icons.Filled.Person, stringResource(R.string.cd_profile), Modifier.size(18.dp), tint = PitColors.OnSurfaceVariant)
                    }
                }
            }
        }
        HorizontalDivider(thickness = 2.dp, color = PitColors.OutlineVariant)
    }
}

/** Barra de detalhe: voltar + titulo (e acoes opcionais a direita), no mesmo estilo da barra principal. */
@Composable
fun PitDetailTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth().background(PitColors.Surface)) {
        Row(
            Modifier.statusBarsPadding().height(64.dp).fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = PitColors.Primary)
            }
            Text(
                text = title.uppercase(),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                style = PitText.HeadlineMd.copy(fontSize = 20.sp),
                color = PitColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            // sem acoes, reserva a largura do botao de voltar para o titulo ficar centralizado
            Row(Modifier.widthIn(min = 48.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
        }
        HorizontalDivider(thickness = 2.dp, color = PitColors.OutlineVariant)
    }
}

@Composable
fun PitBottomBar(selected: TopLevelTab, onSelect: (TopLevelTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(PitColors.SurfaceHighest)) {
        // sombra dura vermelha no topo (shadow-[0_-4px_0_0_red] do design)
        Box(Modifier.fillMaxWidth().height(4.dp).background(PitColors.PrimaryContainer))
        Row(
            Modifier.navigationBarsPadding().height(76.dp).fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            TopLevelTab.entries.forEach { tab ->
                val active = tab == selected
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).background(if (active) PitColors.Primary else Color.Transparent))
                    Spacer(Modifier.height(10.dp))
                    Icon(
                        imageVector = if (active) tab.filled else tab.outlined,
                        contentDescription = null,
                        tint = if (active) PitColors.Primary else PitColors.OnSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(tab.label).uppercase(),
                        style = PitText.DataLabelXs.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                        color = if (active) PitColors.Primary else PitColors.OnSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// --- badges e chips -------------------------------------------------------------------------------

@Composable
fun PulsingDot(color: Color, modifier: Modifier = Modifier, size: Dp = 6.dp) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse-alpha",
    )
    Box(modifier.size(size).alpha(alpha).clip(CircleShape).background(color))
}

/** Selo vermelho inclinado a 15 graus ("NEXT RACE"). */
@Composable
fun SkewBadge(text: String, modifier: Modifier = Modifier, live: Boolean = false) {
    Row(
        modifier.clip(SkewShape).background(PitColors.PrimaryContainer).padding(horizontal = 18.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (live) PulsingDot(Color.White)
        Text(text.uppercase(), style = PitText.DataLabel, color = PitColors.OnPrimaryContainer)
    }
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = PitColors.SurfaceHighest,
    content: Color = PitColors.OnSurfaceVariant,
    pulsing: Boolean = false,
) {
    Row(
        modifier.clip(PitShapes.Card).background(container).padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pulsing) PulsingDot(Color.White)
        Text(text.uppercase(), style = PitText.DataLabelSm.copy(fontSize = 11.sp), color = content, maxLines = 1)
    }
}

/** Chip de categoria: selecionado = pele clara com canto cortado e sombra vermelha dura; com o icone da serie. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = PitColors.Primary,
) {
    val shape: Shape = if (selected) ClippedCornerShape(12.dp) else PitShapes.Sharp
    Row(
        modifier
            .then(if (selected) Modifier.hardShadow(PitColors.PrimaryContainer, shape) else Modifier)
            .clip(shape)
            .background(if (selected) PitColors.Primary else PitColors.SurfaceHighest)
            .then(if (selected) Modifier else Modifier.border(1.dp, PitColors.Hairline, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(20.dp), tint = if (selected) PitColors.OnPrimary else accent)
        Text(
            text = label,
            style = PitText.HeadlineMd,
            color = if (selected) PitColors.OnPrimary else PitColors.OnSurface,
            maxLines = 1,
        )
    }
}

// --- botoes ----------------------------------------------------------------------------------------

@Composable
fun PitPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = ClippedCornerShape(16.dp)
    Box(
        modifier
            .hardShadow(Color.White.copy(alpha = 0.1f), shape)
            .clip(shape)
            .background(PitColors.PrimaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text.uppercase(), style = PitText.ButtonLabel, color = Color.White, maxLines = 1)
    }
}

@Composable
fun PitOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dim: Boolean = false) {
    val shape = ClippedCornerShape(16.dp)
    Box(
        modifier
            .clip(shape)
            .border(1.dp, if (dim) PitColors.OutlineVariant else Color.White, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text.uppercase(),
            style = PitText.ButtonLabel,
            color = if (dim) PitColors.OnSurfaceVariant else PitColors.OnSurface,
            maxLines = 1,
        )
    }
}

// --- blocos ---------------------------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(
            text = title.uppercase(),
            modifier = Modifier
                .weight(1f, fill = false)
                .drawBehind { drawRect(PitColors.Primary, size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height)) }
                .padding(start = 12.dp),
            style = PitText.HeadlineMd,
            color = PitColors.OnSurface,
        )
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier.clickable(onClick = onAction).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(actionLabel.uppercase(), style = PitText.DataLabel, color = PitColors.Primary, maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = PitColors.Primary)
            }
        }
    }
}

/** Card nivel 1 do design: Asphalt Grey com borda de 1 px a 10% de branco. */
@Composable
fun PitCard(
    modifier: Modifier = Modifier,
    shape: Shape = PitShapes.Card,
    borderColor: Color = PitColors.Hairline,
    containerColor: Color = PitColors.Asphalt,
    content: @Composable () -> Unit,
) {
    Box(modifier.clip(shape).background(containerColor).border(BorderStroke(1.dp, borderColor), shape)) { content() }
}

/** Painel "vidro" (sem blur real no Compose: fundo translucido + borda fina). */
@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier.clip(PitShapes.Card).background(PitColors.Asphalt.copy(alpha = 0.72f))
            .border(1.dp, PitColors.Hairline, PitShapes.Card),
    ) { content() }
}

@Composable
fun NetworkImage(
    url: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    alpha: Float = 1f,
    colorFilter: ColorFilter? = null,
) {
    if (url.isNullOrBlank()) {
        Box(modifier)
    } else {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
            alignment = alignment,
            alpha = alpha,
            colorFilter = colorFilter,
        )
    }
}

/** Dois-pontos que piscam (separador da contagem regressiva, "Live effect" do design). */
@Composable
fun BlinkingColon(modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = PitText.DataValue, color: Color = PitColors.Primary) {
    val transition = rememberInfiniteTransition(label = "colon")
    val alpha by transition.animateFloat(1f, 0.25f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "colon-alpha")
    Text(":", modifier.alpha(alpha), style = style, color = color)
}

/** Foto redonda do piloto; sem foto, mostra a inicial. */
@Composable
fun DriverAvatar(imageUrl: String?, fallback: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(PitColors.SurfaceHighest).border(1.dp, PitColors.OutlineVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(fallback.take(1).uppercase(), style = PitText.DataLabel, color = PitColors.OnSurfaceVariant)
        } else {
            NetworkImage(imageUrl, Modifier.fillMaxSize(), alignment = Alignment.TopCenter)
        }
    }
}

/** Aviso discreto quando a fonte ao vivo falhou e o app mostra o catalogo salvo. */
@Composable
fun OfflineNotice(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(PitShapes.Card).background(PitColors.SurfaceContainer)
            .border(1.dp, PitColors.Warning.copy(alpha = 0.4f), PitShapes.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(PitColors.Warning))
        Text(stringResource(R.string.offline_notice), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
    }
}

/** Textura de fibra de carbono (linhas diagonais finas) usada no hero e nos cards destacados. */
fun Modifier.carbonTexture(alpha: Float = 0.04f): Modifier = drawBehind {
    val step = 10.dp.toPx()
    var x = -size.height
    while (x < size.width) {
        drawLine(Color.White.copy(alpha = alpha), Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1.dp.toPx())
        x += step
    }
}
