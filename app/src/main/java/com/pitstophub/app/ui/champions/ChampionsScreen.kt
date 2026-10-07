package com.pitstophub.app.ui.champions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pitstophub.app.R
import com.pitstophub.app.data.model.F1Champion
import com.pitstophub.app.ui.components.PitDetailTopBar
import com.pitstophub.app.ui.components.PitSegmented
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/** Galeria de campeoes da F1 (as mesmas cartas do site); tocar numa carta abre o perfil de carreira. */
@Composable
fun ChampionsScreen(champions: List<F1Champion>, onBack: () -> Unit, onOpen: (F1Champion) -> Unit, modifier: Modifier = Modifier) {
    var order by rememberSaveable { mutableIntStateOf(0) }
    val sorted = remember(champions, order) {
        fun latest(c: F1Champion) = c.championYears.maxOrNull() ?: 0
        when (order) {
            0 -> champions.sortedWith(compareByDescending<F1Champion> { it.championYears.size }.thenByDescending { latest(it) })
            1 -> champions.sortedByDescending { latest(it) }
            else -> champions.sortedByDescending { it.careerWins }
        }
    }
    val totalTitles = champions.sumOf { it.championYears.size }

    Column(modifier.fillMaxSize().background(PitColors.Carbon)) {
        PitDetailTopBar(title = stringResource(R.string.champions_title), onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.champions_kicker).uppercase(), style = PitText.DataLabelSm, color = PitColors.Primary)
                    Text(stringResource(R.string.champions_heading).uppercase(), style = PitText.HeadlineLgMobile, color = PitColors.OnSurface)
                    Text(stringResource(R.string.champions_lead), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                    Text(stringResource(R.string.champions_count, champions.size, totalTitles).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    PitSegmented(
                        labels = listOf(stringResource(R.string.champions_by_titles), stringResource(R.string.champions_by_recent), stringResource(R.string.champions_by_wins)),
                        selected = order,
                        onSelect = { order = it },
                    )
                }
            }
            items(sorted, key = { it.id }) { champion ->
                Column(Modifier.clickable { onOpen(champion) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChampionArt.of(champion)?.let { art ->
                        Image(painterResource(art), champion.name, Modifier.fillMaxWidth().clip(PitShapes.Soft), contentScale = ContentScale.FillWidth)
                    }
                    Text(
                        buildString {
                            append(champion.name)
                            if (champion.championYears.isNotEmpty()) append(" • ${champion.championYears.size}x")
                        }.uppercase(),
                        Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                        style = PitText.DataLabelXs,
                        color = PitColors.OnSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
