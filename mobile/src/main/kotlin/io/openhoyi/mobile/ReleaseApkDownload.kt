package io.openhoyi.mobile

import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLException

/** Up to two whole-file attempts per source. No Range, persistent queue or unbounded retry loop. */
internal class ReleaseApkDownload(
    private val transfer: ReleaseApkTransfer = ReleaseApkTransfer(),
    private val verify: (File, ReleaseUpdate) -> Unit,
    private val diagnostic: (ReleaseTransferEvent) -> Unit = {},
    private val pause: (Long) -> Unit = Thread::sleep,
) {
    @Volatile private var cancelled = false
    fun cancel() { cancelled = true; transfer.cancel() }
    private fun active() {
        if (cancelled || Thread.currentThread().isInterrupted) throw InterruptedIOException("Download interrupted")
    }
    fun download(target: ReleaseUpdate, part: File, progress: (Long, Boolean) -> Unit) {
        // A caller's pre-existing file never belongs to this operation, including retries.
        require(!part.exists())
        require(target.mirrorDownloadUrls.size <= 1)
        var last: Exception? = null
        var rateLimit: ReleaseUpdateHttpException? = null
        for (url in target.mirrorDownloadUrls + target.downloadUrl) {
            val source = if (url == target.downloadUrl) ReleaseSource.GITHUB else ReleaseSource.GITEE
            for (attempt in 1..2) {
                active()
                var transferred = false
                var stage = ReleaseTransferStage.APK
                try {
                    transfer.downloadFrom(target, url, part) { progress(it, false) }
                    transferred = true
                    active()
                    stage = ReleaseTransferStage.VERIFY
                    progress(target.apkSize, true)
                    verify(part, target)
                    active()
                    runCatching { diagnostic(releaseTransferEvent(stage, source, attempt)) }
                    return
                } catch (error: Exception) {
                    if (transferred) check(part.delete() || !part.exists()) { "Cannot discard failed attempt" }
                    active() // Cancellation, including interrupted backoff, never changes source.
                    last = error
                    runCatching { diagnostic(releaseTransferEvent(stage, source, attempt, error)) }
                    if (error is ReleaseUpdateHttpException &&
                        (error.code == 429 || (error.code == 503 && error.retryAfter != null))) rateLimit = error
                    if (stage == ReleaseTransferStage.VERIFY || !retryable(error) || attempt == 2) break
                    try { pause(1_000) } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt(); throw InterruptedIOException("Download interrupted")
                    }
                }
            }
        }
        // Preserve server cooldown when every source failed; success above never inherits it.
        throw (rateLimit ?: last ?: IOException("No download source"))
    }
    private fun retryable(error: Exception): Boolean = when (error) {
        is SSLException -> false
        is SocketTimeoutException -> true
        is InterruptedIOException -> false
        is ReleaseUpdateHttpException -> error.code in setOf(408, 500, 502, 503, 504) && error.retryAfter == null
        is IOException -> true
        else -> false
    }
}
