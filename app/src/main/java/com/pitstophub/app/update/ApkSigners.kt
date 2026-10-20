package com.pitstophub.app.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/** Certificados de assinatura (SHA-256 em hex) do app instalado e de um APK baixado. Interface para os testes. */
interface SignerSource {
    fun installed(): Set<String>
    fun archive(apk: File): Set<String>
}

/** Le os certificados pelo PackageManager (`GET_SIGNING_CERTIFICATES`; `GET_SIGNATURES` antes do Android 9). */
class PackageManagerSigners(private val context: Context) : SignerSource {

    override fun installed(): Set<String> = runCatching {
        signers(packageInfo { pm, flags -> pm.getPackageInfo(context.packageName, flags) })
    }.getOrDefault(emptySet())

    override fun archive(apk: File): Set<String> = runCatching {
        signers(packageInfo { pm, flags -> pm.getPackageArchiveInfo(apk.absolutePath, flags) })
    }.getOrDefault(emptySet())

    private fun packageInfo(read: (PackageManager, Int) -> PackageInfo?): PackageInfo? {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        return read(context.packageManager, flags)
    }

    private fun signers(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val certificates: Array<Signature>? = if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return certificates.orEmpty().map { UpdateRules.sha256Hex(it.toByteArray()) }.toSet()
    }
}
