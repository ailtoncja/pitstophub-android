package com.pitstophub.app.ui.account

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pitstophub.app.R
import com.pitstophub.app.data.model.Category
import com.pitstophub.app.data.model.Race
import com.pitstophub.app.domain.NotifyKind
import com.pitstophub.app.domain.RaceFormat
import com.pitstophub.app.domain.RaceTime
import com.pitstophub.app.ui.LocalAppContainer
import com.pitstophub.app.ui.components.PitOutlineButton
import com.pitstophub.app.ui.components.PitPrimaryButton
import com.pitstophub.app.ui.components.PitSheet
import com.pitstophub.app.ui.components.PitSwitchRow
import com.pitstophub.app.ui.currentLocale
import com.pitstophub.app.ui.isPortuguese
import com.pitstophub.app.ui.theme.PitColors
import com.pitstophub.app.ui.theme.PitShapes
import com.pitstophub.app.ui.theme.PitText
import java.time.Instant

private data class UpcomingItem(val category: Category, val race: Race)

/** Avisos: chave geral do aparelho, tipos de aviso (valem tambem no site) e as proximas etapas do que a pessoa segue. */
@Composable
fun AlertsSheet(onDismiss: () -> Unit, onLogin: () -> Unit, onOpenRace: (categoryId: String, raceId: String) -> Unit) {
    val account = LocalAccount.current
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val session by account.session.collectAsStateWithLifecycle()
    val settings by account.settings.collectAsStateWithLifecycle()
    val enabled by account.alertsEnabled.collectAsStateWithLifecycle()
    val portuguese = isPortuguese()
    val locale = currentLocale()

    var systemAllows by remember { mutableStateOf(account.systemAllowsNotifications()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { systemAllows = account.systemAllowsNotifications() }

    fun openSystemSettings() {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        systemAllows = granted || account.systemAllowsNotifications()
        if (systemAllows) {
            account.setAlertsEnabled(true)
            account.refreshAlerts()
        } else {
            openSystemSettings()
        }
    }

    fun turnOn() {
        when {
            account.systemAllowsNotifications() -> {
                account.setAlertsEnabled(true)
                account.refreshAlerts()
            }
            Build.VERSION.SDK_INT >= 33 -> permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> openSystemSettings()
        }
    }

    // Proximas etapas das categorias que a pessoa acompanha.
    val upcoming by produceState<List<UpcomingItem>?>(null, settings.interestingCategoryIds(), session?.userId) {
        val ids = settings.interestingCategoryIds()
        if (session == null || ids.isEmpty()) {
            value = emptyList()
            return@produceState
        }
        val now = Instant.now()
        value = ids.mapNotNull { id -> runCatching { container.categories.load(id).category }.getOrNull() }
            .flatMap { category ->
                category.calendar.filter { it.status == com.pitstophub.app.data.model.RaceStatus.UPCOMING }.take(2).map { UpcomingItem(category, it) }
            }
            .sortedBy { RaceTime.startInstant(it.race) }
            .filter { RaceTime.startInstant(it.race).isAfter(now.minusSeconds(3 * 3600)) }
            .take(5)
    }

    PitSheet(title = stringResource(R.string.alerts_title), onDismiss = onDismiss, expanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (session == null) {
                Column(
                    Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Soft).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.alerts_need_login), style = PitText.BodyMd, color = PitColors.OnSurfaceVariant)
                    PitPrimaryButton(stringResource(R.string.profile_login), onLogin)
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Soft).padding(horizontal = 16.dp, vertical = 6.dp)) {
                    PitSwitchRow(
                        title = stringResource(R.string.alerts_device),
                        description = stringResource(
                            when {
                                enabled && systemAllows -> R.string.alerts_device_on
                                enabled -> R.string.alerts_device_blocked
                                else -> R.string.alerts_device_off
                            },
                        ),
                        checked = enabled && systemAllows,
                        onCheckedChange = { on -> if (on) turnOn() else account.setAlertsEnabled(false) },
                    )
                    HorizontalDivider(color = PitColors.HairlineSoft)
                    PitSwitchRow(stringResource(R.string.alerts_race_day), settings.notifyRaceDay, { account.setNotify(NotifyKind.RACE_DAY, it) }, description = stringResource(R.string.alerts_race_day_hint))
                    PitSwitchRow(stringResource(R.string.alerts_t60), settings.notifyT60, { account.setNotify(NotifyKind.T60, it) }, description = stringResource(R.string.alerts_t60_hint))
                    PitSwitchRow(stringResource(R.string.alerts_start), settings.notifyStart, { account.setNotify(NotifyKind.START, it) }, description = stringResource(R.string.alerts_start_hint))
                    PitSwitchRow(stringResource(R.string.alerts_results), settings.notifyResults, { account.setNotify(NotifyKind.RESULTS, it) }, description = stringResource(R.string.alerts_results_hint))
                }
                if (enabled && systemAllows) {
                    PitOutlineButton(stringResource(R.string.alerts_test), { account.sendTestNotification() }, dim = true)
                }
                if (settings.followCount == 0) {
                    Text(stringResource(R.string.alerts_follow_something), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                }
                Text(stringResource(R.string.alerts_local_note), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant.copy(alpha = 0.8f))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.alerts_next), style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant)
                    val items = upcoming
                    when {
                        items == null -> Text(stringResource(R.string.loading), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                        items.isEmpty() -> Text(stringResource(R.string.alerts_next_empty), style = PitText.BodySm, color = PitColors.OnSurfaceVariant)
                        else -> Column(Modifier.fillMaxWidth().clip(PitShapes.Soft).background(PitColors.Asphalt).border(1.dp, PitColors.Hairline, PitShapes.Soft)) {
                            items.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(color = PitColors.HairlineSoft)
                                Row(
                                    Modifier.fillMaxWidth().clickable { onOpenRace(item.category.id, item.race.id) }.padding(14.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.race.displayName(portuguese), style = PitText.BodyMd, color = PitColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${item.category.name} • ${RaceFormat.dateTime(item.race, locale)}", style = PitText.DataLabelSm, color = PitColors.OnSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(session?.userId) { if (session != null) account.refreshAlerts() }
}
