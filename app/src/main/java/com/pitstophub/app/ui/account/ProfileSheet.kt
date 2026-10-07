package com.pitstophub.app.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pitstophub.app.BuildConfig
import com.pitstophub.app.R
import com.pitstophub.app.i18n.LanguageChoice
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.PitSegmented
import com.pitstophub.app.ui.components.PitSheet
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText

/** Perfil: conta, idioma e atalhos para favoritos, alertas, campeoes e categorias. */
@Composable
fun ProfileSheet(
    onDismiss: () -> Unit,
    onLogin: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenChampions: () -> Unit,
    onOpenCategories: () -> Unit,
) {
    val account = LocalAccount.current
    val session by account.session.collectAsStateWithLifecycle()
    val settings by account.settings.collectAsStateWithLifecycle()
    val language by account.languageChoice.collectAsStateWithLifecycle()

    PitSheet(title = stringResource(R.string.profile_title), onDismiss = onDismiss, expanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            // conta
            Row(
                Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Soft).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier.size(52.dp).clip(PitShapes.Card).background(PitColors.PrimaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    val name = session?.name
                    if (name != null) {
                        Text(name.trim().take(2).uppercase(), style = PitText.HeadlineMd, color = PitColors.OnPrimaryContainer)
                    } else {
                        Icon(Icons.Filled.Person, null, tint = PitColors.OnPrimaryContainer, modifier = Modifier.size(28.dp))
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        session?.name ?: stringResource(R.string.profile_guest),
                        style = PitText.BodyMd.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = PitColors.OnSurface,
                        maxLines = 1,
                    )
                    Text(
                        session?.email ?: stringResource(R.string.profile_guest_hint),
                        style = PitText.BodySm,
                        color = PitColors.OnSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            if (session == null) {
                PitPrimaryButton(stringResource(R.string.profile_login), onLogin, Modifier.fillMaxWidth())
            }

            // idioma
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.profile_language).uppercase(), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                PitSegmented(
                    labels = listOf(stringResource(R.string.language_auto), "Português", "English"),
                    selected = LanguageChoice.entries.indexOf(language),
                    onSelect = { account.setLanguage(LanguageChoice.entries[it]) },
                )
            }

            // atalhos
            Column {
                MenuRow(
                    Icons.Filled.Favorite,
                    stringResource(R.string.profile_favorites),
                    stringResource(R.string.profile_favorites_hint, settings.followCount),
                    onClick = if (session == null) onLogin else onOpenFavorites,
                )
                MenuRow(Icons.Filled.Notifications, stringResource(R.string.profile_alerts), stringResource(R.string.profile_alerts_hint), onClick = onOpenAlerts)
                MenuRow(Icons.Filled.EmojiEvents, stringResource(R.string.profile_champions), stringResource(R.string.profile_champions_hint), onClick = onOpenChampions)
                MenuRow(Icons.Filled.Explore, stringResource(R.string.profile_categories), stringResource(R.string.profile_categories_hint), onClick = onOpenCategories)
            }

            if (session != null) {
                PitOutlineButton(stringResource(R.string.profile_logout), { account.logout(); onDismiss() }, Modifier.fillMaxWidth(), dim = true)
            }
            Text(
                "PitStopHub ${BuildConfig.VERSION_NAME}",
                Modifier.fillMaxWidth(),
                style = PitText.DataLabelXs,
                color = PitColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
            Text(
                stringResource(R.string.profile_disclaimer),
                Modifier.fillMaxWidth(),
                style = PitText.DataLabelXs,
                color = PitColors.OnSurfaceVariant.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(PitShapes.Card).background(PitColors.SurfaceHigh), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = PitColors.Primary, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = PitText.BodyMd, color = PitColors.OnSurface)
            Text(subtitle, style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 2)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = PitColors.OnSurfaceVariant, modifier = Modifier.size(18.dp))
    }
}
