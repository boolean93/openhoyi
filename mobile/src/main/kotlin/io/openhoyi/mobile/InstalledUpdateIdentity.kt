package io.openhoyi.mobile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/** Local comparison only: no package information or certificate is transmitted to the server. */
internal fun installedUpdateIdentity(context: Context): InstalledUpdateIdentity? = runCatching {
    val manager = context.packageManager
    @Suppress("DEPRECATION")
    val info = manager.getPackageInfo(context.packageName,
        if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES)
    @Suppress("DEPRECATION")
    val signers = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
    val certificate = signers?.singleOrNull()?.let {
        MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
    @Suppress("DEPRECATION")
    val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    val application = info.applicationInfo ?: error("Package application identity unavailable")
    InstalledUpdateIdentity(info.packageName, code, application.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, certificate, info.versionName)
}.getOrNull()
