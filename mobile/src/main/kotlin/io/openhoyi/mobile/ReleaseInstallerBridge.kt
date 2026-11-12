package io.openhoyi.mobile

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/** No sessions, commit callbacks or silent-install permission. The system always owns confirmation. */
internal class ReleaseInstallerBridge(private val context: Context) {
    fun permitted() = context.packageManager.canRequestPackageInstalls()
    @Suppress("DEPRECATION")
    private fun systemTarget(intent: Intent): Intent {
        val matches = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { info -> info.activityInfo.exported &&
                info.activityInfo.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
        val selected = matches.sortedWith(compareByDescending<android.content.pm.ResolveInfo> { it.priority }
            .thenBy { it.activityInfo.packageName }.thenBy { it.activityInfo.name }).firstOrNull()
            ?: error("System installation component unavailable")
        return intent.setComponent(ComponentName(selected.activityInfo.packageName, selected.activityInfo.name))
    }
    fun permissionIntent(): Intent = systemTarget(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}")))
    @Suppress("DEPRECATION")
    fun intentFor(file: File): Intent {
        val directory = File(context.filesDir, "updates/verified").canonicalFile
        require(file.isFile && file.canonicalFile.parentFile == directory && file.name.matches(Regex("[a-f0-9-]{36}\\.apk")))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".updates", file)
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return systemTarget(intent)
    }
    fun install(activity: Activity, file: File) {
        require(permitted())
        activity.startActivity(intentFor(file))
    }
}
