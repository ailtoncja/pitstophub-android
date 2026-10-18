package com.pitstophub.app.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.RuleSection
import com.pitstophub.app.ui.CategoryVisuals
import com.pitstophub.app.ui.components.FilterPill
import com.pitstophub.app.ui.components.PitCard
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.carbonTexture
import com.pitstophub.app.ui.components.ruleIcon
import com.pitstophub.app.ui.components.splitBio
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import kotlinx.coroutines.launch

// Itens fixos antes dos cartoes de regra na lista: cabecalho e atalhos.
private const val FIRST_RULE_ITEM = 2
private const val LEAD_MAX = 150

/** Indice da secao que explica a pontuacao (usada pelo link "Como funcionam os pontos?" da classificacao). */
internal fun scoringRuleIndex(rules: List<RuleSection>): Int? =
    rules.indexOfFirst { rule ->
        val title = "${rule.title} ${rule.enTitle.orEmpty()}".lowercase()
        "pontua" in title || "scoring" in title || "points" in title || "classifica" in title
    }.takeIf { it >= 0 }

/** Titulo curto para os atalhos do topo ("Curiosidades e regras especiais" -> "Curiosidades e regras…"). */
internal fun shortRuleTitle(title: String, max: Int = 22): String {
    if (title.length <= max) return title
    val cut = title.take(max).substringBeforeLast(' ', title.take(max))
    return cut.trimEnd(',', ' ', '-') + "…"
}

/**
 * "Como funciona": uma pagina so com as regras da categoria (formato do fim de semana, pontuacao, carros...), uma secao
 * por cartao, com atalhos no topo para pular para cada uma.
 */
@Composable
fun RulesScreen(state: CategoryHubState, initialSection: Int, onBack: () -> Unit, modifier: Modifier = Modifier, showTopBar: Boolean = true) {
    val portuguese = isPortuguese()
    val category = state.data?.category

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        if (showTopBar) PitDetailTopBar(title = stringResource(R.string.rules_title), onBack = onBack)
        if (category == null || category.rulesFormat.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp)) {
                Text(
                    stringResource(if (category == null && !state.notFound) R.string.loading else R.string.rules_unavailable),
                    style = PitText.BodyMd,
                    color = PitColors.OnSurfaceVariant,
                )
            }
            return@Column
        }
        val rules = category.rulesFormat
        val listState = rememberLazyListState()
        val scope = rememberCoroutineScope()
        LaunchedEffect(initialSection) {
            if (initialSection in 1..rules.lastIndex) listState.scrollToItem(FIRST_RULE_ITEM + initialSection)
        }
        val current by remember { derivedStateOf { (listState.firstVisibleItemIndex - FIRST_RULE_ITEM).coerceIn(0, rules.lastIndex) } }

        LazyColumn(
            Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "hero") { Box(Modifier.height(0.dp)) }
            item(key = "nav") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    rules.forEachIndexed { index, rule ->
                        FilterPill(
                            shortRuleTitle(rule.displayTitle(portuguese)),
                            selected = current == index,
                            onClick = { scope.launch { listState.animateScrollToItem(FIRST_RULE_ITEM + index) } },
                        )
                    }
                }
            }
            itemsIndexed(rules, key = { index, _ -> "rule-$index" }) { index, rule ->
                RuleCard(index + 1, rule, portuguese)
            }
        }
    }
}

@Composable
private fun RuleCard(@Suppress("UNUSED_PARAMETER") number: Int, rule: RuleSection, portuguese: Boolean) {
    val title = rule.displayTitle(portuguese)
    val text = remember(rule, portuguese) { splitBio(rule.displayBody(portuguese)) }
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(ruleIcon(title), null, tint = Color.White, modifier = Modifier.size(22.dp))
            Text(title, style = PitText.HeadlineMd, color = Color.White)
        }
        if (text.lead.isNotEmpty()) {
            // so a frase curta vira destaque; um bloco longo em negrito cansa a leitura
            val short = text.lead.length <= LEAD_MAX
            Text(
                text.lead,
                style = PitText.BodyLg.copy(fontWeight = if (short) FontWeight.SemiBold else FontWeight.Normal),
                color = if (short) Color.White else PitColors.Silver,
            )
        }
        text.paragraphs.forEach { paragraph ->
            Text(paragraph, style = PitText.BodyLg, color = PitColors.Silver)
        }
    }
}
