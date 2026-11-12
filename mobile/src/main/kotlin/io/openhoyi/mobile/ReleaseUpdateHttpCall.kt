package io.openhoyi.mobile

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.net.ssl.HttpsURLConnection

/** Anonymous, single-use GET of one fixed public index. Never fetches APKs or follows redirects. */
internal class ReleaseUpdateHttpCall(
    private val open: (URL) -> HttpsURLConnection = { it.openConnection() as HttpsURLConnection },
) {
    @Volatile private var cancelled = false
    // UI cancellation is non-blocking. The worker owns and closes its socket in finally.
    fun cancel() { cancelled = true }
    fun execute(): ReleaseUpdate {
        val started = System.nanoTime()
        fun checkActive() {
            if (cancelled || Thread.currentThread().isInterrupted || System.nanoTime() - started > 10_000_000_000L)
                throw InterruptedIOException("Update check cancelled or timed out")
        }
        checkActive()
        val current = open(URL(ReleaseUpdateIndex.URL))
        try {
            checkActive()
            current.instanceFollowRedirects = false
            current.useCaches = false
            current.connectTimeout = 5_000
            current.readTimeout = 5_000
            current.requestMethod = "GET"
            current.setRequestProperty("Accept", "application/json")
            current.setRequestProperty("Cache-Control", "no-cache")
            if (current.responseCode != 200) throw IOException("Update index is unavailable")
            checkActive()
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
        } finally {
            current.disconnect()
        }
    }
}
