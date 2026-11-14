package io.openhoyi.mobile

import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal enum class ReleaseSource { GITEE, GITHUB }
internal enum class ReleaseTransferStage { INDEX, APK, VERIFY }
internal enum class ReleaseTransferOutcome { SUCCESS, FAILED }
internal enum class ReleaseTransferFailure { NONE, TIMEOUT, DNS, TLS, HTTP, INTERRUPTED, REJECTED, IO }

/** Deliberately has no URL, raw exception text, headers, response body, file path or tokens. */
internal data class ReleaseTransferEvent(
    val stage: ReleaseTransferStage,
    val source: ReleaseSource,
    val attempt: Int,
    val outcome: ReleaseTransferOutcome,
    val failure: ReleaseTransferFailure = ReleaseTransferFailure.NONE,
    val httpStatus: Int? = null,
) {
    fun fields(): Map<String, String> = mapOf("stage" to stage.name, "source" to source.name,
        "attempt" to attempt.toString(), "outcome" to outcome.name, "failure" to failure.name,
        "httpStatus" to (httpStatus?.takeIf { it in 100..599 }?.toString() ?: "none"))
}

internal fun releaseTransferFailure(error: Exception): ReleaseTransferFailure = when (error) {
    is SocketTimeoutException -> ReleaseTransferFailure.TIMEOUT
    is UnknownHostException -> ReleaseTransferFailure.DNS
    is SSLException -> ReleaseTransferFailure.TLS
    is ReleaseUpdateHttpException -> ReleaseTransferFailure.HTTP
    is InterruptedIOException, is InterruptedException -> ReleaseTransferFailure.INTERRUPTED
    is IllegalArgumentException, is IllegalStateException -> ReleaseTransferFailure.REJECTED
    else -> ReleaseTransferFailure.IO
}

internal fun releaseTransferEvent(stage: ReleaseTransferStage, source: ReleaseSource, attempt: Int,
    error: Exception? = null) = ReleaseTransferEvent(stage, source, attempt,
    if (error == null) ReleaseTransferOutcome.SUCCESS else ReleaseTransferOutcome.FAILED,
    error?.let(::releaseTransferFailure) ?: ReleaseTransferFailure.NONE, (error as? ReleaseUpdateHttpException)?.code)
