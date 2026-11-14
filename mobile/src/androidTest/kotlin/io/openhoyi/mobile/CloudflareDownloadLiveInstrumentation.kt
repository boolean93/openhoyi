package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import org.json.JSONObject
import java.io.File
import java.io.IOException
import javax.net.ssl.HttpsURLConnection

/** Explicit disposable-CI-only network acceptance. Never opens activities, BLE or an installer. */
class CloudflareDownloadLiveInstrumentation : Instrumentation() {
    private var allowed = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        allowed = arguments?.getString("disposableCiEmulator") == "true"
        start()
    }
    override fun onStart() {
        val report = Bundle()
        var directory: File? = null
        try {
            check(allowed && BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk"))
            val canonical = ReleaseUpdateHttpCall(acceleratedEnabled = false).execute()
            val events = mutableListOf<ReleaseTransferEvent>()
            val mirror = ReleaseUpdateHttpCall(acceleratedEnabled = true,
                installedSignerSha256 = canonical.signerSha256, diagnostic = events::add).execute()
            check(events.any { it.source == ReleaseSource.CLOUDFLARE && it.outcome == ReleaseTransferOutcome.SUCCESS })
            check(mirror.versionCode == canonical.versionCode && mirror.apkSha256 == canonical.apkSha256 &&
                mirror.signerSha256 == canonical.signerSha256 && mirror.apkSize == canonical.apkSize &&
                mirror.downloadUrl == canonical.downloadUrl) { "Mirror does not match current canonical release" }
            val canonicalFields = JSONObject(checkNotNull(canonical.indexJson)).apply { remove("mirrorDownloadUrls") }
            val mirrorFields = JSONObject(checkNotNull(mirror.indexJson)).apply { remove("mirrorDownloadUrls") }
            val keys = canonicalFields.keys().asSequence().toSet()
            check(keys == mirrorFields.keys().asSequence().toSet() &&
                keys.all { canonicalFields.get(it) == mirrorFields.get(it) }) { "Mirror metadata drift" }
            directory = File(targetContext.cacheDir, "mirror-live-${System.nanoTime()}").also { check(it.mkdir()) }
            fun verify(file: File, target: ReleaseUpdate) {
                check(file.length() == target.apkSize && releaseApkSha256(file) == target.apkSha256)
                check(verifyReleaseApkSignature(file, Build.VERSION.SDK_INT) == canonical.signerSha256)
                @Suppress("DEPRECATION")
                val info = checkNotNull(targetContext.packageManager.getPackageArchiveInfo(file.path, 0))
                val app = checkNotNull(info.applicationInfo)
                @Suppress("DEPRECATION")
                val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
                check(info.packageName == "io.openhoyi.mobile" && code == target.versionCode &&
                    app.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 && app.minSdkVersion <= Build.VERSION.SDK_INT &&
                    info.splitNames.isNullOrEmpty() && app.splitSourceDirs.isNullOrEmpty())
                check(file.length() == target.apkSize && releaseApkSha256(file) == target.apkSha256)
            }
            val primary = File(directory, "primary.part")
            events.clear()
            ReleaseApkDownload(verify = ::verify, diagnostic = events::add).download(mirror, primary) { _, _ -> }
            check(events.last().source == ReleaseSource.CLOUDFLARE && events.last().outcome == ReleaseTransferOutcome.SUCCESS)
            check(primary.delete())
            var blockedMirror = 0
            val fallback = File(directory, "fallback.part")
            events.clear()
            val transfer = ReleaseApkTransfer(open = { url ->
                if (url.host == "openhoyi.ios.dog") { blockedMirror++; throw IOException("Injected mirror transport failure") }
                url.openConnection() as HttpsURLConnection
            })
            ReleaseApkDownload(transfer, ::verify, events::add).download(mirror, fallback) { _, _ -> }
            check(blockedMirror == 2 && events.last().source == ReleaseSource.GITHUB &&
                events.last().outcome == ReleaseTransferOutcome.SUCCESS)
            report.putString("stream", "CLOUDFLARE_DOWNLOAD_LIVE_PASSED version=${mirror.versionCode} bytes=${mirror.apkSize} " +
                "sha256=${mirror.apkSha256} index=true cloudflareApk=true githubFallback=true signed=true noInstall=true noBle=true\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Exception) {
            // Only a type/classification, never a URL, signed query or raw exception message.
            report.putString("stream", "CLOUDFLARE_DOWNLOAD_LIVE_FAILED type=${error.javaClass.simpleName}\n")
            finish(Activity.RESULT_CANCELED, report)
        } finally { directory?.deleteRecursively() }
    }
}
