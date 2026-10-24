package com.pitstophub.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.pitstophub.app.R
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitText
import com.pitstophub.app.update.ApkInstaller
import kotlinx.coroutines.launch

/**
 * Dialogo da atualizacao do app: pergunta, progresso do download e, se o Android ainda nao deixa o PitStopHub instalar
 * apps, o pedido para liberar. Falhas viram snackbar.
 */
@Composable
fun UpdateDialog(state: UpdateState, snackbar: SnackbarHostState, appViewModel: AppViewModel) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()

    fun install(ready: UpdateState.Ready) {
        if (!ApkInstaller.canInstall(context)) {
            // Fica o dialogo: a pessoa libera nas configuracoes, volta e toca em Instalar.
            ApkInstaller.openInstallPermissionSettings(context)
            return
        }
        if (ApkInstaller.install(context, ready.apk)) appViewModel.updateHandedOff() else appViewModel.dismissUpdate()
    }

    // Resposta do botao "Procurar atualizacao" quando nao ha versao nova (ou a rede falhou).
    LaunchedEffect(appViewModel) {
        appViewModel.updateNotice.collect { notice ->
            val message = when (notice) {
                UpdateNotice.UP_TO_DATE -> resources.getString(R.string.update_up_to_date, com.pitstophub.app.BuildConfig.VERSION_NAME)
                UpdateNotice.FAILED -> resources.getString(R.string.update_check_failed)
            }
            scope.launch { snackbar.showSnackbar(message) }
        }
    }

    when (state) {
        UpdateState.Idle -> Unit
        is UpdateState.Available -> UpdateAlert(
            title = stringResource(R.string.update_title),
            message = stringResource(R.string.update_message, state.release.version),
            confirm = stringResource(R.string.update_now) to appViewModel::startUpdate,
            dismiss = stringResource(R.string.update_later) to appViewModel::dismissUpdate,
        )
        is UpdateState.Downloading -> UpdateAlert(
            title = stringResource(R.string.update_title),
            message = stringResource(R.string.update_downloading, state.release.version),
            progress = true,
            dismiss = stringResource(R.string.update_cancel) to appViewModel::dismissUpdate,
        )
        is UpdateState.Ready -> {
            // Assim que o APK fica pronto, segue direto para o instalador (ou para a liberacao).
            LaunchedEffect(state.apk) { install(state) }
            UpdateAlert(
                title = stringResource(R.string.update_title),
                message = stringResource(R.string.update_permission),
                confirm = stringResource(R.string.update_install) to { install(state) },
                dismiss = stringResource(R.string.update_later) to appViewModel::dismissUpdate,
            )
        }
        is UpdateState.Failed -> LaunchedEffect(state) {
            // O snackbar roda no escopo do dialogo: sair do estado Failed nao pode cancela-lo.
            scope.launch { snackbar.showSnackbar(resources.getString(if (state.rejected) R.string.update_rejected else R.string.update_failed)) }
            appViewModel.consumeUpdateFailure()
        }
    }
}

@Composable
private fun UpdateAlert(
    title: String,
    message: String,
    dismiss: Pair<String, () -> Unit>,
    confirm: Pair<String, () -> Unit>? = null,
    progress: Boolean = false,
) {
    AlertDialog(
        // Voltar equivale ao botao de recusar; tocar fora nao fecha.
        onDismissRequest = dismiss.second,
        properties = DialogProperties(dismissOnClickOutside = false),
        containerColor = PitColors.SurfaceHigh,
        title = { Text(title, style = PitText.HeadlineMd, color = PitColors.OnSurface) },
        text = {
            Column {
                Text(message, style = PitText.BodyMd, color = PitColors.Silver)
                if (progress) {
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = PitColors.Primary, trackColor = PitColors.OutlineVariant)
                }
            }
        },
        confirmButton = { confirm?.let { (label, action) -> PitPrimaryButton(label, action) } },
        dismissButton = { PitOutlineButton(dismiss.first, dismiss.second, dim = true) },
    )
}
