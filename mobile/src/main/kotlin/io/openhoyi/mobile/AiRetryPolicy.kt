package io.openhoyi.mobile

import kotlinx.coroutines.delay
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** No message parsing or HTTP status retry. Eligibility uses a local phase and a separate write boundary. */
internal object AiRetryPolicy {
    const val MAX_RETRIES = 5
    fun connectionFailure(failure: Exception, stage: AiStage, writeStarted: Boolean): AiFailureKind? {
        if (writeStarted || stage != AiStage.CONNECT) return null
        return when (failure) {
            is UnknownHostException -> AiFailureKind.DNS
            is ConnectException -> AiFailureKind.CONNECT
            is SocketTimeoutException -> AiFailureKind.CONNECT_TIMEOUT
            else -> null
        }
    }
    fun waitMillis(index: Int): Long { require(index in 1..MAX_RETRIES); return 1000L shl (index - 1) }
}
internal data class AiRetryProgress(val index: Int, val waitMillis: Long, val kind: AiFailureKind)

/** Shared by one reply's summary/chat/search transports. Existing timeout windows are never reset. */
internal class AiRetryState(private val wait: suspend (Long) -> Unit = { delay(it) }) {
    private var used = 0
    @Synchronized fun reserve(kind: AiFailureKind): AiRetryProgress? {
        require(kind in setOf(AiFailureKind.DNS, AiFailureKind.CONNECT, AiFailureKind.CONNECT_TIMEOUT))
        if (used >= AiRetryPolicy.MAX_RETRIES) return null
        used++
        return AiRetryProgress(used, AiRetryPolicy.waitMillis(used), kind)
    }
    suspend fun await(progress: AiRetryProgress) { wait(progress.waitMillis) }
}
