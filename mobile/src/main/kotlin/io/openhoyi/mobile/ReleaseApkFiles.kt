package io.openhoyi.mobile

import java.io.File

/** A handoff sidecar protects file lifetime only. It never grants verification or installation intent. */
internal class ReleaseApkFiles(private val verified: File, private val staging: File) {
    private val apkName = Regex("[a-f0-9-]{36}\\.apk")
    private fun owns(file: File) = file.name.matches(apkName) && file.canonicalFile.parentFile == verified.canonicalFile
    private fun marker(file: File) = File(verified, file.name + ".handoff")
    fun rememberHandoff(file: File, target: ReleaseUpdate): Boolean = runCatching {
        require(owns(file) && file.isFile)
        val raw = requireNotNull(target.indexJson)
        require(releaseUpdateKey(ReleaseUpdateIndex.parse(raw)) == releaseUpdateKey(target))
        AtomicDocumentStorage(marker(file).toPath()).write(raw.toByteArray(Charsets.UTF_8))
        true
    }.getOrDefault(false)
    fun retained(file: File) = owns(file) && marker(file).exists()
    fun cleanup(file: File, target: ReleaseUpdate, allowHandoff: Boolean = false): Boolean = runCatching {
        require(owns(file))
        val keep = marker(file)
        if (keep.exists()) {
            // Corrupt/unknown sidecars are protected, never swept as if owned by this attempt.
            val recorded = ReleaseUpdateIndex.parse(keep.readText(Charsets.UTF_8))
            require(releaseUpdateKey(recorded) == releaseUpdateKey(target))
            if (!allowHandoff) return@runCatching false
        }
        val part = File(staging, file.name.removeSuffix(".apk") + ".part")
        if (part.exists() && !part.delete()) return@runCatching false
        if (file.exists() && !file.delete()) return@runCatching false
        if (keep.exists() && !keep.delete()) return@runCatching false
        true
    }.getOrDefault(false)
    fun cleanupInstalled(identity: InstalledUpdateIdentity?) {
        if (identity?.packageName != "io.openhoyi.mobile" || identity.debuggable || identity.signerSha256 == null) return
        verified.listFiles()?.filter { it.name.endsWith(".apk.handoff") }?.forEach { keep ->
            runCatching {
                val name = keep.name.removeSuffix(".handoff")
                require(name.matches(apkName))
                val target = ReleaseUpdateIndex.parse(keep.readText(Charsets.UTF_8))
                if (identity.signerSha256 == target.signerSha256 && identity.versionCode >= target.versionCode)
                    cleanup(File(verified, name), target, allowHandoff = true)
            }
        }
    }
}
