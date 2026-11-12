package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ReleaseUpdatePolicyTest {
    private val fixture = """
{
  "schemaVersion": 1,
  "channel": "develop-test",
  "repository": "boolean93/openhoyi",
  "packageName": "io.openhoyi.mobile",
  "versionCode": 94,
  "versionName": "0.2.0-dev.94",
  "apkSha256": "74dafd3096cf9d0afdb0fd2527de6a5fba24fa11e78f0120fb327965de0fa013",
  "signerCertificateSha256": "c0f426397c43ff9a8a4ecd3d1159b124584161b6163c375338e84ca6966a0f54",
  "declaredSourceCommit": "78c5391fdf334ba1f7bbc0f36f30335bbaca3a7c",
  "sourceCommitVerified": false,
  "releaseTag": "dev-94",
  "publishedAt": "2026-11-12T11:40:15Z",
  "releaseUrl": "https://github.com/boolean93/openhoyi/releases/tag/dev-94",
  "apkFile": "OpenHOYI-0.2.0-dev.94.apk",
  "apkSize": 17241551,
  "downloadUrl": "https://github.com/boolean93/openhoyi/releases/download/dev-94/OpenHOYI-0.2.0-dev.94.apk",
  "distributionUrl": "https://github.com/boolean93/openhoyi/releases/download/dev-94/distribution.json",
  "checksumsUrl": "https://github.com/boolean93/openhoyi/releases/download/dev-94/SHA256SUMS.txt"
}
    """.trimIndent()
    private fun changed(key: String, value: Any?): String = JSONObject(fixture).put(key, value ?: JSONObject.NULL).toString()
    private fun rejects(json: String) {
        try { ReleaseUpdateIndex.parse(json); fail("Untrusted update index accepted") }
        catch (_: IllegalArgumentException) { }
        catch (_: IllegalStateException) { }
        catch (_: java.time.format.DateTimeParseException) { }
    }

    @Test fun realDev94FixtureIsCompatibleWithSchema() {
        val index = ReleaseUpdateIndex.parse(fixture)
        assertEquals(94L, index.versionCode)
        assertEquals("0.2.0-dev.94", index.versionName)
        assertEquals("74dafd3096cf9d0afdb0fd2527de6a5fba24fa11e78f0120fb327965de0fa013", index.apkSha256)
        assertEquals("https://github.com/boolean93/openhoyi/releases/download/dev-94/OpenHOYI-0.2.0-dev.94.apk", index.downloadUrl)
    }

    @Test fun integerFieldsRejectCoercionOverflowAndInvalidRanges() {
        for (key in listOf("schemaVersion", "versionCode", "apkSize")) {
            for (value in listOf<Any>("94", true, 94.5, 0, -1, Long.MAX_VALUE)) rejects(changed(key, value))
            rejects(changed(key, null))
        }
        // JSONObject serializes 94.0 as 94: test original lexical forms, not round-tripped numbers.
        rejects(fixture.replace("\"versionCode\": 94", "\"versionCode\": 94.0"))
        rejects(fixture.replace("\"versionCode\": 94", "\"versionCode\": 9.4e1"))
        rejects(fixture.replace("\"versionCode\": 94", "\"versionCode\": 094"))
    }

    @Test fun noAlternateUrlsAreAccepted() {
        for (key in listOf("releaseUrl", "downloadUrl", "distributionUrl", "checksumsUrl")) {
            val original = JSONObject(fixture).getString(key)
            for (value in listOf(original.replace("https:", "http:"), original + "?token=bad", original + "#fragment",
                original.replace("github.com", "github.com.evil.test"), original.replace("github.com", "user@github.com"),
                original.replace("github.com", "github.com:443"), original.replace("/dev-94", "/dev-95"),
                original.replace("/openhoyi/", "/other/"), original.replace("/releases/", "/%72eleases/"))) {
                rejects(changed(key, value))
            }
        }
        for (file in listOf("../evil.apk", "a/b.apk", "a%2fb.apk", "a.apk?x=1", "-option.apk", ".hidden.apk")) {
            rejects(changed("apkFile", file))
        }
    }

    @Test fun identitySchemaAndDateMustBeExact() {
        for ((key, value) in listOf("schemaVersion" to 2, "channel" to "stable", "repository" to "attacker/openhoyi",
            "packageName" to "io.openhoyi.mobile.mock", "releaseTag" to "dev-094", "versionName" to " ",
            "versionName" to "x".repeat(101), "apkSha256" to "B".repeat(64), "signerCertificateSha256" to "bad",
            "declaredSourceCommit" to "a".repeat(39), "sourceCommitVerified" to true,
            "sourceCommitVerified" to "false", "publishedAt" to "2026-02-30T11:40:15Z",
            "publishedAt" to "2026-11-12T11:40:15+00:00")) rejects(changed(key, value))
        rejects(JSONObject(fixture).apply { remove("apkSha256") }.toString())
        rejects(changed("unknownField", "not schema v1"))
    }

    @Test fun malformedDuplicateOrOversizedJsonIsRejectedOnAndroidToo() {
        rejects("not JSON")
        rejects("[]")
        rejects(fixture + " trailing")
        rejects(fixture.replace("\"schemaVersion\": 1,", "\"schemaVersion\": 1, \"schemaVersion\": 1,"))
        rejects(fixture.replace("\"schemaVersion\": 1,", "schemaVersion: 1,"))
        rejects(fixture.replace("\"schemaVersion\": 1,", "/* comment */ \"schemaVersion\": 1,"))
        rejects(fixture.replace("\"channel\": \"develop-test\"", "\"channel\": 'develop-test'"))
        rejects(fixture.trimEnd().dropLast(1) + ",}")
        rejects(" ".repeat(ReleaseUpdateIndex.MAX_BYTES) + fixture)
    }

    @Test fun installedIdentityControlsUpgradeNotBuildConfigOrVersionName() {
        val update = ReleaseUpdateIndex.parse(fixture)
        val release = InstalledUpdateIdentity("io.openhoyi.mobile", 93, false, update.signerSha256)
        assertEquals(ReleaseUpdateStatus.AVAILABLE, releaseUpdateStatus(release, update))
        assertEquals(ReleaseUpdateStatus.SAME_VERSION, releaseUpdateStatus(release.copy(versionCode = 94), update))
        assertEquals(ReleaseUpdateStatus.INSTALLED_NEWER, releaseUpdateStatus(release.copy(versionCode = 95), update))
        assertEquals(ReleaseUpdateStatus.SIGNATURE_MISMATCH, releaseUpdateStatus(release.copy(signerSha256 = "f".repeat(64)), update))
        assertEquals(ReleaseUpdateStatus.DEBUG_PREVIEW, releaseUpdateStatus(release.copy(debuggable = true, signerSha256 = null), update))
        assertEquals(ReleaseUpdateStatus.MOCK_PREVIEW, releaseUpdateStatus(release.copy(packageName = "io.openhoyi.mobile.mock"), update))
        for (identity in listOf(null, release.copy(packageName = "attacker.app"), release.copy(signerSha256 = null),
            release.copy(signerSha256 = "bad"), release.copy(versionCode = 0))) {
            assertEquals(ReleaseUpdateStatus.IDENTITY_UNAVAILABLE, releaseUpdateStatus(identity, update))
        }
    }
}
