package com.pitstophub.app.ui.account

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pitstophub.app.BuildConfig
import com.pitstophub.app.R
import com.pitstophub.app.data.remote.AuthError
import com.pitstophub.app.data.remote.AuthResult
import com.pitstophub.app.data.repo.MIN_PASSWORD_LENGTH
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.PitSegmented
import com.pitstophub.app.ui.components.PitSheet
import com.pitstophub.app.ui.components.PitTextField
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import kotlinx.coroutines.launch

private enum class AuthMode { LOGIN, REGISTER }

/** Entrar / criar conta (Supabase, a mesma conta do site). */
@Composable
fun AuthSheet(onDismiss: () -> Unit) {
    val account = LocalAccount.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<AuthError?>(null) }
    var errorDetail by remember { mutableStateOf<String?>(null) }
    var noticeRes by remember { mutableStateOf<Int?>(null) }

    fun submit() {
        busy = true
        error = null
        errorDetail = null
        noticeRes = null
        scope.launch {
            val result = if (mode == AuthMode.LOGIN) account.login(email, password) else account.register(name, email, password)
            busy = false
            when (result) {
                is AuthResult.Authenticated -> onDismiss()
                AuthResult.PendingVerification -> {
                    noticeRes = R.string.auth_check_email
                    mode = AuthMode.LOGIN
                    password = ""
                }
                is AuthResult.Failure -> {
                    error = result.error
                    errorDetail = result.detail
                }
            }
        }
    }

    PitSheet(
        title = stringResource(if (mode == AuthMode.LOGIN) R.string.auth_title_login else R.string.auth_title_register),
        onDismiss = onDismiss,
        expanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            PitSegmented(
                labels = listOf(stringResource(R.string.auth_tab_login), stringResource(R.string.auth_tab_register)),
                selected = mode.ordinal,
                onSelect = {
                    mode = AuthMode.entries[it]
                    error = null
                    noticeRes = null
                },
            )
            if (!account.accountsAvailable) {
                Text(
                    stringResource(R.string.auth_unavailable),
                    Modifier.fillMaxWidth().border(1.dp, PitColors.Warning.copy(alpha = 0.5f), PitShapes.Card).padding(12.dp),
                    style = PitText.BodySm,
                    color = PitColors.OnSurfaceVariant,
                )
            }
            if (mode == AuthMode.REGISTER) {
                PitTextField(name, { name = it }, stringResource(R.string.auth_name), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
            }
            PitTextField(email, { email = it }, stringResource(R.string.auth_email), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            PitTextField(
                password,
                { password = it },
                stringResource(R.string.auth_password),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                supportingText = if (mode == AuthMode.REGISTER) stringResource(R.string.auth_password_hint, MIN_PASSWORD_LENGTH) else null,
            )
            noticeRes?.let { Text(stringResource(it), style = PitText.BodySm, color = PitColors.Success) }
            error?.let { Text(stringResource(errorText(it)), style = PitText.BodySm, color = PitColors.Error) }
            // so em build de desenvolvimento: mostra a causa tecnica (ajuda a achar erro de URL/chave do Supabase)
            if (BuildConfig.DEBUG) errorDetail?.let { Text(it, style = PitText.DataLabelXs, color = PitColors.OnSurfaceVariant) }
            PitPrimaryButton(
                text = stringResource(
                    when {
                        busy -> R.string.auth_busy
                        mode == AuthMode.LOGIN -> R.string.auth_submit_login
                        else -> R.string.auth_submit_register
                    },
                ),
                onClick = { if (!busy && account.accountsAvailable) submit() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@StringRes
private fun errorText(error: AuthError): Int = when (error) {
    AuthError.NOT_CONFIGURED -> R.string.auth_unavailable
    AuthError.INVALID_NAME -> R.string.auth_error_name
    AuthError.INVALID_EMAIL -> R.string.auth_error_email
    AuthError.INVALID_PASSWORD -> R.string.auth_error_password
    AuthError.INVALID_CREDENTIALS -> R.string.auth_error_credentials
    AuthError.EMAIL_NOT_CONFIRMED -> R.string.auth_error_unconfirmed
    AuthError.ALREADY_REGISTERED -> R.string.auth_error_registered
    AuthError.WEAK_PASSWORD -> R.string.auth_error_weak
    AuthError.RATE_LIMITED -> R.string.auth_error_rate
    AuthError.NETWORK -> R.string.auth_error_network
    AuthError.UNKNOWN -> R.string.auth_error_unknown
}
