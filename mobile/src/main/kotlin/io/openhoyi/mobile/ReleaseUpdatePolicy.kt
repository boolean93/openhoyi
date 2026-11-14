package io.openhoyi.mobile

import org.json.JSONTokener
import java.time.Instant

/** Verified public metadata, not proof that this binary matches a source commit. */
data class ReleaseUpdate(
    val versionCode: Long,
    val versionName: String,
    val signerSha256: String,
    val apkSha256: String,
    val downloadUrl: String,
    val releaseUrl: String,
    internal val indexJson: String? = null,
    val apkSize: Long = 0,
    val mirrorDownloadUrls: List<String> = emptyList(),
)

object ReleaseUpdateIndex {
    const val URL = "https://boolean93.github.io/openhoyi/updates/dev.json"
    const val ACCELERATED_URL = "https://openhoyi.ios.dog/boolean93.github.io/openhoyi/updates/dev.json"
    const val MAX_BYTES = 64 * 1024
    private const val REPOSITORY = "boolean93/openhoyi"
    private const val BASE = "https://github.com/$REPOSITORY"
    private val hex64 = Regex("[0-9a-f]{64}")
    private val keys = setOf("schemaVersion", "channel", "repository", "packageName", "versionCode", "versionName",
        "apkSha256", "signerCertificateSha256", "declaredSourceCommit", "sourceCommitVerified", "releaseTag",
        "publishedAt", "releaseUrl", "apkFile", "apkSize", "downloadUrl", "distributionUrl", "checksumsUrl")

    fun parse(json: String): ReleaseUpdate {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Update index is too large" }
        val fields = FlatJson(json).read()
        require(fields.keys == keys || fields.keys == keys + "mirrorDownloadUrls") { "Unexpected update schema fields" }
        fun string(key: String): String = fields[key] as? String ?: error("Expected string: $key")
        fun integer(key: String): Long = fields[key] as? Long ?: error("Expected integer: $key")
        require(integer("schemaVersion") == 1L)
        require(string("channel") == "develop-test" && string("repository") == REPOSITORY)
        require(string("packageName") == "io.openhoyi.mobile")
        val code = integer("versionCode")
        require(code in 1..Int.MAX_VALUE.toLong())
        val version = string("versionName")
        require(version.isNotBlank() && version.length <= 100 && version.none { it.isISOControl() })
        val cert = string("signerCertificateSha256")
        val hash = string("apkSha256")
        require(hex64.matches(cert) && hex64.matches(hash))
        require(Regex("[0-9a-f]{40}").matches(string("declaredSourceCommit")))
        require(fields["sourceCommitVerified"] == false)
        val tag = "dev-$code"
        require(string("releaseTag") == tag)
        val filename = string("apkFile")
        require(filename.length <= 200 && Regex("[A-Za-z0-9][A-Za-z0-9._-]*\\.apk").matches(filename))
        require(integer("apkSize") in 1..(256L * 1024 * 1024))
        val published = string("publishedAt")
        require(Regex("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z").matches(published))
        Instant.parse(published) // Reject invalid dates as well as non-UTC strings.
        val releaseUrl = "$BASE/releases/tag/$tag"
        val prefix = "$BASE/releases/download/$tag/"
        val downloadUrl = prefix + filename
        require(string("releaseUrl") == releaseUrl && string("downloadUrl") == downloadUrl)
        require(string("distributionUrl") == prefix + "distribution.json")
        require(string("checksumsUrl") == prefix + "SHA256SUMS.txt")
        val mirrors = if (fields.containsKey("mirrorDownloadUrls")) {
            val values = fields["mirrorDownloadUrls"] as? List<*> ?: error("Expected mirror URL array")
            require(values.size <= 1)
            values.map { value ->
                require(value == "https://gitee.com/$REPOSITORY/releases/download/$tag/$filename")
                value as String
            }
        } else emptyList()
        return ReleaseUpdate(code, version, cert, hash, downloadUrl, releaseUrl, json, integer("apkSize"), mirrors)
    }

    /** Schema v1 plus one bounded optional string array. Reject JSONTokener's Android leniencies and duplicate keys explicitly. */
    private class FlatJson(private val source: String) {
        private var at = 0
        private fun whitespace() { while (at < source.length && source[at] in " \r\n\t") at++ }
        private fun take(char: Char) { whitespace(); require(at < source.length && source[at++] == char) }
        private fun quoted(): String {
            whitespace()
            val start = at
            require(at < source.length && source[at++] == '"')
            while (at < source.length) {
                val char = source[at++]
                require(char >= ' ')
                if (char == '"') return JSONTokener(source.substring(start, at)).nextValue() as String
                if (char == '\\') {
                    require(at < source.length)
                    val escape = source[at++]
                    require(escape in "\"\\/bfnrtu")
                    if (escape == 'u') repeat(4) {
                        require(at < source.length && source[at++] in "0123456789abcdefABCDEF")
                    }
                }
            }
            error("Unterminated JSON string")
        }
        private fun value(): Any? {
            whitespace()
            if (at < source.length && source[at] == '"') return quoted()
            if (at < source.length && source[at] == '[') {
                take('[')
                whitespace()
                val values = mutableListOf<String>()
                if (at < source.length && source[at] != ']') {
                    values += quoted()
                    // Only one mirror is supported; no nested values or coercion.
                }
                take(']')
                return values
            }
            val start = at
            while (at < source.length && source[at] !in ",} \r\n\t") at++
            val token = source.substring(start, at)
            return when (token) {
                "true" -> true
                "false" -> false
                "null" -> null
                else -> {
                    // All numeric fields in this schema are integers, never coercible strings/floats.
                    require(Regex("-?(0|[1-9][0-9]*)").matches(token))
                    token.toLong()
                }
            }
        }
        fun read(): Map<String, Any?> {
            val fields = linkedMapOf<String, Any?>()
            take('{')
            whitespace()
            if (at < source.length && source[at] != '}') while (true) {
                val key = quoted()
                require(!fields.containsKey(key)) { "Duplicate JSON key" }
                take(':')
                fields[key] = value()
                whitespace()
                if (at < source.length && source[at] == ',') { at++; continue }
                break
            }
            take('}')
            whitespace()
            require(at == source.length) { "Trailing JSON data" }
            return fields
        }
    }
}

enum class ReleaseUpdateStatus {
    AVAILABLE, SAME_VERSION, INSTALLED_NEWER, DEBUG_PREVIEW, MOCK_PREVIEW, SIGNATURE_MISMATCH, IDENTITY_UNAVAILABLE,
}

data class InstalledUpdateIdentity(
    val packageName: String,
    val versionCode: Long,
    val debuggable: Boolean,
    val signerSha256: String?,
    val versionName: String? = null,
)

/** Local previews share the app identity, but are never allowed to bypass Android's downgrade rule. */
internal fun InstalledUpdateIdentity.isLocalDebugPreview(): Boolean = debuggable &&
    versionName?.matches(Regex("[0-9]+(?:\\.[0-9]+){1,3}-local\\.[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*")) == true

internal fun releaseAutomaticCheckEligible(identity: InstalledUpdateIdentity?): Boolean = identity?.let {
    it.packageName == "io.openhoyi.mobile" && (!it.debuggable || it.isLocalDebugPreview()) &&
        it.versionCode > 0 && it.signerSha256?.matches(Regex("[0-9a-f]{64}")) == true
} == true

fun releaseUpdateStatus(installed: InstalledUpdateIdentity?, update: ReleaseUpdate): ReleaseUpdateStatus = when {
    installed == null -> ReleaseUpdateStatus.IDENTITY_UNAVAILABLE
    installed.packageName == "io.openhoyi.mobile.mock" -> ReleaseUpdateStatus.MOCK_PREVIEW
    installed.packageName != "io.openhoyi.mobile" -> ReleaseUpdateStatus.IDENTITY_UNAVAILABLE
    installed.debuggable && !installed.isLocalDebugPreview() -> ReleaseUpdateStatus.DEBUG_PREVIEW
    installed.versionCode <= 0 || installed.signerSha256 == null ||
        !Regex("[0-9a-f]{64}").matches(installed.signerSha256) -> ReleaseUpdateStatus.IDENTITY_UNAVAILABLE
    installed.signerSha256 != update.signerSha256 -> ReleaseUpdateStatus.SIGNATURE_MISMATCH
    update.versionCode > installed.versionCode ||
        (update.versionCode == installed.versionCode && installed.isLocalDebugPreview()) -> ReleaseUpdateStatus.AVAILABLE
    update.versionCode == installed.versionCode -> ReleaseUpdateStatus.SAME_VERSION
    else -> ReleaseUpdateStatus.INSTALLED_NEWER
}
