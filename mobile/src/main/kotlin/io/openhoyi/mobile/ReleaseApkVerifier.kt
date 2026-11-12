package io.openhoyi.mobile

import android.content.Context
import android.content.pm.ApplicationInfo
import android.os.Build
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.MessageDigest

internal data class ReleaseApkIdentity(val packageName: String, val versionCode: Long,
    val debuggable: Boolean, val signerSha256: String, val minSdk: Int, val split: Boolean)

internal fun releaseApkIdentityMatches(installed: InstalledUpdateIdentity?, target: ReleaseUpdate,
    actual: ReleaseApkIdentity, sdk: Int): Boolean = releaseUpdateStatus(installed, target) == ReleaseUpdateStatus.AVAILABLE &&
    actual.packageName == "io.openhoyi.mobile" && actual.packageName == installed?.packageName &&
    actual.versionCode == target.versionCode && !actual.debuggable && !actual.split && actual.minSdk <= sdk &&
    actual.signerSha256 == target.signerSha256 && actual.signerSha256 == installed.signerSha256

internal fun releaseApkSha256(file: File): String = file.inputStream().use { input ->
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(64 * 1024)
    while (true) {
        if (Thread.currentThread().isInterrupted) throw java.io.InterruptedIOException()
        val count = input.read(buffer); if (count < 0) break
        digest.update(buffer, 0, count)
    }
    digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal fun verifyReleaseApkSignature(file: File, sdk: Int): String {
    val result = ApkVerifier.Builder(file).setMinCheckedPlatformVersion(sdk)
        .setMaxCheckedPlatformVersion(sdk).build().verify()
    require(result.isVerified && !result.containsErrors() && result.signingCertificateLineage == null)
    val certificate = requireNotNull(result.signerCertificates.singleOrNull())
    val signer = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    return signer
}

/** Cryptographic verification of bytes, then independent manifest and installed-identity comparison. */
internal class ReleaseApkVerifier(private val context: Context) {
    fun verify(file: File, target: ReleaseUpdate) {
        require(file.isFile && file.length() == target.apkSize && target.apkSize > 0)
        require(releaseApkSha256(file) == target.apkSha256)
        val signer = verifyReleaseApkSignature(file, Build.VERSION.SDK_INT)
        @Suppress("DEPRECATION")
        val info = requireNotNull(context.packageManager.getPackageArchiveInfo(file.absolutePath, 0))
        val application = requireNotNull(info.applicationInfo)
        @Suppress("DEPRECATION")
        val version = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        val actual = ReleaseApkIdentity(info.packageName, version,
            application.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0, signer, application.minSdkVersion,
            !info.splitNames.isNullOrEmpty() || !application.splitSourceDirs.isNullOrEmpty())
        require(releaseApkIdentityMatches(installedUpdateIdentity(context), target, actual, Build.VERSION.SDK_INT))
        // Reject a file changed while parsers/verifier were reading it.
        require(file.length() == target.apkSize && releaseApkSha256(file) == target.apkSha256)
    }
}
