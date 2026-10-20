package com.pitstophub.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pitstophub.app.R
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.update.UpdateState
import com.pitstophub.app.update.UpdateStep

/** Aviso de versao nova (nao bloqueia o app): novidades, "Atualizar" / "Depois" e o andamento do download. */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onUpdate: () -> Unit,
    onLater: () -> Unit,
    onAllowInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = state.update ?: return
    PitCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.update_available_title, update.name), style = PitText.TitleSm, color = Color.White)
            Text(
                update.notes.ifBlank { stringResource(R.string.update_default_notes) },
                style = PitText.BodySm,
                color = PitColors.Silver,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            val status = when {
                state.step == UpdateStep.DOWNLOADING -> state.progress?.let { stringResource(R.string.update_downloading_percent, (it * 100).toInt()) }
                    ?: stringResource(R.string.update_downloading)
                state.step == UpdateStep.INSTALLING -> stringResource(R.string.update_installing)
                state.step == UpdateStep.FAILED -> stringResource(R.string.update_failed)
                !state.canInstall -> stringResource(R.string.update_permission_hint)
                else -> null
            }
            if (status != null) {
                Text(status, style = PitText.DataLabelSm, color = if (state.step == UpdateStep.FAILED) PitColors.Signal else Color.White)
            }
            if (state.busy) {
                val progress = state.progress
                val track = PitColors.SurfaceHighest
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = track)
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White, trackColor = track)
                }
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PitOutlineButton(stringResource(R.string.update_later), onLater, dim = true)
                    if (state.canInstall) {
                        PitPrimaryButton(stringResource(R.string.update_now), onUpdate)
                    } else {
                        PitPrimaryButton(stringResource(R.string.update_allow), onAllowInstall)
                    }
                }
            }
        }
    }
}
