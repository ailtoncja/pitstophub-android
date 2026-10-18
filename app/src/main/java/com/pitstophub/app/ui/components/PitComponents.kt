package com.pitstophub.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.res.painterResource
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.DataState
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
    Agenda(R.string.nav_agenda, Icons.Filled.CalendarToday, Icons.Outlined.CalendarToday),
    Categories(R.string.nav_categories, Icons.Filled.GridView, Icons.Outlined.GridView),
    Profile(R.string.nav_profile, Icons.Filled.Person, Icons.Outlined.Person),
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

/** Barra do topo (Inicio, Agenda, Categorias): escudo + PitStopHub, busca e avisos. */
@Composable
fun PitTopBar(
    @Suppress("UNUSED_PARAMETER") onMenu: () -> Unit,
    onSearch: () -> Unit,
    onAlerts: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onProfile: () -> Unit,
    @Suppress("UNUSED_PARAMETER") profileInitials: String?,
    alertsDot: Boolean,
    modifier: Modifier = Modifier,
    onBrandTap: () -> Unit = {},
) {
    Column(modifier.fillMaxWidth().background(PitColors.Carbon)) {
        Row(
            Modifier.statusBarsPadding().height(56.dp).fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onBrandTap),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(painter = painterResource(R.drawable.ic_launcher_logo), contentDescription = null, modifier = Modifier.size(30.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style = PitText.HeadlineMd.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.04).em),
                    color = Color.White,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onSearch) {
                Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.cd_search), tint = Color.White)
            }
            IconButton(onClick = onAlerts) {
                Box {
                    Icon(Icons.Outlined.Notifications, contentDescription = stringResource(R.string.cd_alerts), tint = Color.White)
                    if (alertsDot) {
                        Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(PitColors.RacingRed))
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = PitColors.Hairline)
    }
}

/** Barra de detalhe: voltar com o contexto ao lado (ex.: "F1 · Calendario 2026") e acoes opcionais a direita. */
@Composable
fun PitDetailTopBar(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().background(PitColors.Carbon).height(56.dp).padding(start = 4.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back), tint = PitColors.Silver)
        }
        Text(
            text = title,
            modifier = Modifier.weight(1f).padding(end = 4.dp),
            style = PitText.DataLabel,
            color = PitColors.Silver,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

/** Navegacao inferior: Inicio, Agenda, Categorias e Perfil (igual ao site no celular). */
@Composable
fun PitBottomBar(selected: TopLevelTab?, onSelect: (TopLevelTab) -> Unit, modifier: Modifier = Modifier, profileInitials: String? = null) {
    Column(modifier.fillMaxWidth().background(PitColors.Carbon)) {
        HorizontalDivider(thickness = 1.dp, color = PitColors.Hairline)
        Row(Modifier.navigationBarsPadding().height(68.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TopLevelTab.entries.forEach { tab ->
                val active = tab == selected
                val tint = if (active) Color.White else PitColors.SilverDim
                Column(
                    Modifier.weight(1f).fillMaxSize().clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (tab == TopLevelTab.Profile && profileInitials != null) {
                        Box(Modifier.size(26.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                            Text(profileInitials, style = PitText.DataLabelSm.copy(fontWeight = FontWeight.Bold), color = Color.Black, maxLines = 1)
                        }
                    } else {
                        Icon(imageVector = if (active) tab.filled else tab.outlined, contentDescription = null, tint = tint)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(tab.label),
                        style = PitText.DataLabelXs.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                        color = tint,
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

/** Etiqueta de estado da largada (ao vivo com ponto vermelho, a confirmar...). Sem caixa colorida. */
@Composable
fun SkewBadge(text: String, modifier: Modifier = Modifier, live: Boolean = false) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (live) PulsingDot(PitColors.RacingRed, size = 8.dp)
        Text(text, style = PitText.DataLabel, color = if (live) Color.White else PitColors.Silver)
    }
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = PitColors.SurfaceHighest,
    content: Color = PitColors.Silver,
    pulsing: Boolean = false,
) {
    Row(
        modifier.clip(PitShapes.Pill).background(container).padding(horizontal = 10.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (pulsing) PulsingDot(Color.White)
        Text(text, style = PitText.DataLabelSm, color = content, maxLines = 1)
    }
}

/** Pilula de filtro/categoria: selecionada = branca cheia. */
@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    @Suppress("UNUSED_PARAMETER") accent: Color = PitColors.Primary,
) {
    Row(
        modifier
            .clip(PitShapes.Pill)
            .background(if (selected) Color.White else Color.Transparent)
            .border(1.dp, if (selected) Color.White else Color.White.copy(alpha = 0.22f), PitShapes.Pill)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(18.dp), tint = if (selected) Color.Black else PitColors.Silver)
        Text(label, style = PitText.DataLabel, color = if (selected) Color.Black else PitColors.Silver, maxLines = 1)
    }
}

// --- botoes ----------------------------------------------------------------------------------------

/** Botao primario: pilula branca de 48 dp. */
@Composable
fun PitPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(PitShapes.Pill).background(Color.White).clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PitText.ButtonLabel, color = Color.Black, maxLines = 1)
    }
}

/** Botao secundario: pilula com contorno. */
@Composable
fun PitOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, dim: Boolean = false) {
    Box(
        modifier.clip(PitShapes.Pill).border(1.dp, Color.White.copy(alpha = if (dim) 0.18f else 0.32f), PitShapes.Pill)
            .clickable(onClick = onClick).heightIn(min = 48.dp).padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PitText.ButtonLabel, color = if (dim) PitColors.Silver else Color.White, maxLines = 1)
    }
}

/** Glifo redondo de 48 dp (trilho do reel). Ativo = preenchido; `red` para "seguindo". */
@Composable
fun GlyphButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    red: Boolean = false,
) {
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (active) (if (red) PitColors.RacingRed else Color.White) else Color.Black.copy(alpha = 0.45f))
                .border(1.dp, if (active) Color.Transparent else Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active && !red) Color.Black else Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = PitText.DataLabelXs, color = Color.White, maxLines = 1)
    }
}

/** Forma do estado do dado: filete cheio = ao vivo, tracejado = diario, meio filete = catalogo. */
@Composable
fun StateMark(state: DataState, modifier: Modifier = Modifier, color: Color = Color.White) {
    Canvas(modifier.size(width = 18.dp, height = 4.dp)) {
        val y = size.height / 2
        val stroke = 3.dp.toPx()
        when (state) {
            DataState.LIVE -> drawLine(color, Offset(0f, y), Offset(size.width, y), stroke)
            DataState.DAILY -> {
                val dash = size.width / 5
                for (i in 0 until 3) drawLine(color, Offset(i * dash * 2, y), Offset(i * dash * 2 + dash, y), stroke)
            }
            DataState.CATALOG -> drawLine(color, Offset(0f, y), Offset(size.width / 2, y), stroke)
        }
    }
}

/** Estado do dado com o nome ao lado. */
@Composable
fun DataStateLabel(categoryId: String, modifier: Modifier = Modifier) {
    val state = CategoryVisuals.dataState(categoryId)
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        StateMark(state)
        Text(
            stringResource(
                when (state) {
                    DataState.LIVE -> R.string.state_live
                    DataState.DAILY -> R.string.state_daily
                    DataState.CATALOG -> R.string.state_catalog
                },
            ),
            style = PitText.DataLabelXs,
            color = PitColors.Silver,
        )
    }
}

// --- blocos ---------------------------------------------------------------------------------------

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = title, modifier = Modifier.weight(1f, fill = false), style = PitText.HeadlineMd, color = Color.White)
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier.clickable(onClick = onAction).heightIn(min = 48.dp).padding(start = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(actionLabel, style = PitText.DataLabel, color = Color.White, maxLines = 1)
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = Color.White)
            }
        }
    }
}

/** Painel: fundo #0E0E10, borda de 1 px a 12% de branco, raio 16. */
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
fun BlinkingColon(modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = PitText.DataValue, color: Color = PitColors.Silver) {
    Text(" ", modifier, style = style, color = color)
}

/** Foto redonda do piloto; sem foto, mostra a inicial. */
@Composable
fun DriverAvatar(imageUrl: String?, fallback: String, modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Box(
        modifier.size(size).clip(CircleShape).background(PitColors.SurfaceHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Text(fallback.take(1).uppercase(), style = PitText.DataLabel, color = PitColors.Silver)
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
            .border(1.dp, PitColors.Hairline, PitShapes.Card).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(PitColors.Warning))
        Text(stringResource(R.string.offline_notice), style = PitText.BodySm, color = PitColors.Silver)
    }
}

/** A textura de carbono do visual antigo foi aposentada. */
@Suppress("UNUSED_PARAMETER", "UnusedReceiverParameter")
fun Modifier.carbonTexture(alpha: Float = 0.04f): Modifier = this
