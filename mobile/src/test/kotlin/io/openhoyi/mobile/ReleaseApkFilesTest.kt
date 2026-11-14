package io.openhoyi.mobile

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseApkFilesTest {
    private fun target(): ReleaseUpdate = ReleaseUpdateIndex.parse(JSONObject(mapOf(
        "schemaVersion" to 1, "channel" to "develop-test", "repository" to "boolean93/openhoyi",
        "packageName" to "io.openhoyi.mobile", "versionCode" to 94, "versionName" to "fixture",
        "apkSha256" to "b".repeat(64), "signerCertificateSha256" to "a".repeat(64),
        "declaredSourceCommit" to "c".repeat(40), "sourceCommitVerified" to false, "releaseTag" to "dev-94",
        "publishedAt" to "2026-11-12T11:40:15Z", "apkFile" to "update.apk", "apkSize" to 100,
        "releaseUrl" to "https://github.com/boolean93/openhoyi/releases/tag/dev-94",
        "downloadUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/update.apk",
        "distributionUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/distribution.json",
        "checksumsUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/SHA256SUMS.txt",
    )).toString())
    private fun roots(): Triple<File,File,File> {
        val root=kotlin.io.path.createTempDirectory().toFile()
        return Triple(root,root.resolve("verified").apply {mkdirs()},root.resolve("staging").apply {mkdirs()})
    }
    private fun apk(directory:File)=directory.resolve("12345678-1234-1234-1234-123456789abc.apk").apply {writeBytes(ByteArray(100))}
    @Test fun handoffSurvivesNewStoreInstanceAndOnlyExplicitDiscardMayRemoveIt() {
        val (root,verified,staging)=roots()
        try {
            val file=apk(verified);val target=target();val first=ReleaseApkFiles(verified,staging)
            assertTrue(first.rememberHandoff(file,target))
            val restarted=ReleaseApkFiles(verified,staging)
            assertTrue(restarted.retained(file));assertFalse(restarted.cleanup(file,target));assertTrue(file.exists())
            assertTrue(restarted.cleanup(file,target,allowHandoff=true));assertFalse(file.exists());assertFalse(restarted.retained(file))
        } finally {root.deleteRecursively()}
    }
    @Test fun onlyActuallyInstalledMatchingSignerAndVersionCleanKnownHandoffs() {
        val (root,verified,staging)=roots()
        try {
            val file=apk(verified);val store=ReleaseApkFiles(verified,staging);assertTrue(store.rememberHandoff(file,target()))
            val unknown=verified.resolve("unknown.apk").apply {writeText("unknown")}
            val identity=InstalledUpdateIdentity("io.openhoyi.mobile",94,false,"a".repeat(64))
            store.cleanupInstalled(identity.copy(versionCode=93));assertTrue(file.exists())
            store.cleanupInstalled(identity.copy(signerSha256="f".repeat(64)));assertTrue(file.exists())
            store.cleanupInstalled(identity.copy(packageName="io.openhoyi.mobile.mock"));assertTrue(file.exists())
            store.cleanupInstalled(identity.copy(debuggable=true,versionName="0.2.0-local.94-test"));assertTrue(file.exists())
            store.cleanupInstalled(identity);assertFalse(file.exists());assertTrue(unknown.exists())
        } finally {root.deleteRecursively()}
    }
    @Test fun orphanPartIsRemovedOnlyForTheKnownOwnedAttemptAndUnknownSidecarsAreProtected() {
        val (root,verified,staging)=roots()
        try {
            val file=apk(verified);val target=target();val store=ReleaseApkFiles(verified,staging)
            val part=staging.resolve(file.name.removeSuffix(".apk")+".part").apply {writeText("partial")}
            val unknown=staging.resolve("unknown.part").apply {writeText("unknown")}
            assertTrue(store.cleanup(file,target));assertFalse(part.exists());assertTrue(unknown.exists())
            file.writeBytes(ByteArray(100));verified.resolve(file.name+".handoff").writeText("corrupt")
            assertFalse(store.cleanup(file,target,true));assertTrue(file.exists())
            assertFalse(store.rememberHandoff(root.resolve("outside.apk").apply {writeText("outside")},target))
        } finally {root.deleteRecursively()}
    }
}
