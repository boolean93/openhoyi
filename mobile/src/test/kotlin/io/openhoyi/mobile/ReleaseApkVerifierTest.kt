package io.openhoyi.mobile

import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseApkVerifierTest {
    private fun directory() = File(requireNotNull(System.getProperty("hoyi.update.fixture.dir")))
    @Test fun actualSignedApkVerifiesForApi26And35AndTamperedOrUnsignedBytesAreRejected() {
        val root=directory();val metadata=JSONObject(root.resolve("identity.json").readText())
        for(sdk in listOf(26,35)) {
            assertEquals(metadata.getString("signerSha256"),verifyReleaseApkSignature(root.resolve("valid.apk"),sdk))
            for(name in listOf("unsigned.apk","tampered.apk")) {
                try { verifyReleaseApkSignature(root.resolve(name),sdk);fail("Unverified APK accepted: $name") } catch(_:Exception) {}
            }
        }
        assertEquals(metadata.getString("apkSha256"),releaseApkSha256(root.resolve("valid.apk")))
        assertNotEquals(metadata.getString("apkSha256"),releaseApkSha256(root.resolve("tampered.apk")))
    }
    @Test fun packageVersionRuntimeSignerAndInstalledIdentityMustAllMatch() {
        val meta=JSONObject(directory().resolve("identity.json").readText())
        val cert=meta.getString("signerSha256")
        val target=ReleaseUpdate(94,"fixture",cert,meta.getString("apkSha256"),"", "", apkSize=meta.getLong("apkSize"))
        val installed=InstalledUpdateIdentity("io.openhoyi.mobile",93,false,cert)
        val actual=ReleaseApkIdentity("io.openhoyi.mobile",94,false,cert,26,false)
        assertTrue(releaseApkIdentityMatches(installed,target,actual,26))
        for(bad in listOf(actual.copy(packageName="io.openhoyi.mobile.mock"),actual.copy(versionCode=95),
            actual.copy(debuggable=true),actual.copy(split=true),actual.copy(minSdk=27),actual.copy(signerSha256="f".repeat(64))))
            assertFalse(releaseApkIdentityMatches(installed,target,bad,26))
        for(bad in listOf(null,installed.copy(debuggable=true),installed.copy(versionCode=94),
            installed.copy(packageName="io.openhoyi.mobile.mock"),installed.copy(signerSha256="f".repeat(64))))
            assertFalse(releaseApkIdentityMatches(bad,target,actual,26))
    }
}
