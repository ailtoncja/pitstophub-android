package com.pitstophub.app.update

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import java.io.File

/** Entrega o APK conferido ao instalador do Android (que pede a confirmacao de sempre). */
object ApkInstaller {

    /** Pasta do cache privado onde os APKs sao baixados; a unica exposta pelo FileProvider (`res/xml/update_paths.xml`). */
    const val DIR = "updates"

    private const val APK_MIME = "application/vnd.android.package-archive"

    fun directory(context: Context): File = File(context.cacheDir, DIR)

    /** "Instalar apps desconhecidos" liberado para o PitStopHub? */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /** Abre a tela do sistema onde se libera "instalar apps desconhecidos" so para este app. */
    fun openInstallPermissionSettings(context: Context): Boolean = start(
        context,
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri()),
    )

    /** Abre o instalador do Android com o APK (via FileProvider, so leitura e so para o instalador). */
    fun install(context: Context, apk: File): Boolean {
        val uri = runCatching { FileProvider.getUriForFile(context, "${context.packageName}.updates", apk) }.getOrNull() ?: return false
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return start(context, intent)
    }

    private fun start(context: Context, intent: Intent): Boolean = try {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
