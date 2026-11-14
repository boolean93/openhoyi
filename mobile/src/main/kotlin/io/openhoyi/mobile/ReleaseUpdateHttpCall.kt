package io.openhoyi.mobile

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

/** Anonymous fixed-index fallback. Never fetches APKs or follows arbitrary redirects. */
internal class ReleaseUpdateHttpCall(
    private val installedVersionCode: Long? = null,
    private val installedSignerSha256: String? = null,
    private val acceleratedEnabled: Boolean = true,
    private val diagnostic: (ReleaseTransferEvent) -> Unit = {},
    private val clock: () -> Long = System::nanoTime,
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true }
    fun execute(): ReleaseUpdate {
        val started = clock()
        fun active() {
            if (cancelled || Thread.currentThread().isInterrupted || clock() - started > 10_000_000_000L)
                throw InterruptedIOException("Update check cancelled or timed out")
        }
        var known: ReleaseUpdate? = null
        var last: Exception? = null
        var limited: ReleaseUpdateHttpException? = null
        val sources = if (acceleratedEnabled) listOf(ReleaseSource.CLOUDFLARE to ReleaseUpdateIndex.ACCELERATED_URL,
            ReleaseSource.GITHUB to ReleaseUpdateIndex.URL) else listOf(ReleaseSource.GITHUB to ReleaseUpdateIndex.URL)
        for ((source, url) in sources) {
            active()
            try {
                val result = request(url) { active() }
                if (source == ReleaseSource.CLOUDFLARE && installedSignerSha256 != null) {
                    require(result.signerSha256 == installedSignerSha256) { "Mirror signer mismatch" }
                }
                active()
                runCatching { diagnostic(releaseTransferEvent(ReleaseTransferStage.INDEX, source, 1)) }
                if (known == null || result.versionCode > known.versionCode) known = result
                // An old but healthy mirror must not trap users behind a failed mirror publication.
                if (source == ReleaseSource.GITHUB || installedVersionCode == null || result.versionCode > installedVersionCode)
                    return requireNotNull(known)
            } catch (error: Exception) {
                active()
                last = error
                if (error is ReleaseUpdateHttpException &&
                    (error.code == 429 || (error.code == 503 && error.retryAfter != null))) limited = error
                runCatching { diagnostic(releaseTransferEvent(ReleaseTransferStage.INDEX, source, 1, error)) }
            }
        }
        known?.let { return it }
        throw (limited ?: last ?: IOException("No update index"))
    }
    private fun request(initial: String, active: () -> Unit): ReleaseUpdate {
        val sourceStarted = clock()
        fun checkActive() {
            active()
            if (clock() - sourceStarted > 4_000_000_000L) throw SocketTimeoutException("Update source timed out")
        }
        val address = initial
        while (true) {
            checkActive()
            val current = open(URL(address))
            try {
                current.instanceFollowRedirects = false
                current.useCaches = false
                current.connectTimeout = 2_000
                current.readTimeout = 2_000
                current.requestMethod = "GET"
                current.setRequestProperty("Accept", "application/json")
                current.setRequestProperty("Cache-Control", "no-cache")
                val responseCode = current.responseCode
                checkActive()
                if (responseCode != 200) throw ReleaseUpdateHttpException(responseCode, current.getHeaderField("Retry-After"))
                if (current.contentLengthLong > ReleaseUpdateIndex.MAX_BYTES) throw IOException("Update index too large")
                val bytes = current.inputStream.use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        checkActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        if (output.size() + count > ReleaseUpdateIndex.MAX_BYTES) throw IOException("Update index too large")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
                checkActive()
                val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                return ReleaseUpdateIndex.parse(text)
            } finally { current.disconnect() }
        }
    }
}
