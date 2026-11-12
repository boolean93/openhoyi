package io.openhoyi.mobile

import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Response metadata only; no remote error text is surfaced or persisted. */
internal class ReleaseUpdateHttpException(val code: Int, val retryAfter: String?) : IOException("Update HTTP $code")

internal fun updateRetryDelay(header: String?, now: Long): Long? {
    val value = header?.trim() ?: return null
    if (value.isNotEmpty() && value.all { it in '0'..'9' }) {
        val seconds = value.toLongOrNull() ?: return Long.MAX_VALUE
        return if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000
    }
    /* HTTP dates are absolute server limits. Past dates permit an immediate retry. */
    return runCatching {
        (ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now).coerceAtLeast(0)
    }.getOrNull()
}

/** Main-thread process authority. UI instances never own requests, deadlines or version dismissal. */
internal class ReleaseUpdateCoordinator(
    private val storage: Storage,
    private val now: () -> Long,
    private val jitter: () -> Long,
    private val request: ((Result<ReleaseUpdate>) -> Unit) -> (() -> Unit),
    private val automaticEligible: Boolean = true,
) {
    data class Saved(val enabled: Boolean = true, val nextAutoAt: Long = 0,
        val rateLimitedUntil: Long = 0, val rateFailures: Int = 0, val cachedIndex: String? = null,
        val autoFailures: Int = 0)
    interface Storage { fun read(): Saved; fun write(value: Saved): Boolean }
    data class State(val checking: Boolean = false, val update: ReleaseUpdate? = null,
        val failed: Boolean = false, val limited: Boolean = false)
    private var saved = runCatching(storage::read).getOrDefault(Saved(nextAutoAt = Long.MAX_VALUE))
    var state = State(update = saved.cachedIndex?.let { runCatching { ReleaseUpdateIndex.parse(it) }.getOrNull() })
        private set
    val enabled get() = saved.enabled
    val nextCheckAt get() = maxOf(saved.nextAutoAt, saved.rateLimitedUntil)
    private var foreground = false
    private var generation = 0L
    private var cancel: (() -> Unit)? = null
    private var manualRequested = false
    private val observers = mutableSetOf<(State) -> Unit>()
    private val dismissed = mutableSetOf<String>()
    private fun key(update: ReleaseUpdate) = "${update.versionCode}:${update.signerSha256}:${update.apkSha256}"
    fun observe(observer: (State) -> Unit) { observers += observer; observer(state) }
    fun unobserve(observer: (State) -> Unit) { observers -= observer }
    fun setForeground(value: Boolean) {
        foreground = value
        // Leaving the app retires every UI request lease, including manual checks.
        if (!value) stop() else automaticCheck()
    }
    fun setEnabled(value: Boolean): Boolean {
        if (!save(saved.copy(enabled = value))) return false
        if (!value) { if (!manualRequested) stop() } else automaticCheck()
        publish(state)
        return true
    }
    fun automaticCheck() {
        if (automaticEligible && foreground && enabled && now() >= nextCheckAt) start(manual = false)
    }
    fun manualCheck() { if (foreground) start(manual = true) }
    fun promptCandidate(identity: InstalledUpdateIdentity?, safe: Boolean): ReleaseUpdate? = state.update?.takeIf {
        foreground && enabled && safe && key(it) !in dismissed && releaseUpdateStatus(identity, it) == ReleaseUpdateStatus.AVAILABLE
    }
    fun dismiss(update: ReleaseUpdate) { dismissed += key(update) }
    fun owns(update: ReleaseUpdate) = state.update == update
    private fun save(value: Saved): Boolean {
        if (!runCatching { storage.write(value) }.getOrDefault(false)) return false
        saved = value
        return true
    }
    private fun start(manual: Boolean) {
        if (state.checking) {
            if (manual) manualRequested = true
            return
        }
        val clock = now()
        if (clock < saved.rateLimitedUntil) {
            if (manual) publish(state.copy(failed = false, limited = true))
            return
        }
        // Reserve before networking: cancellation, process death and repeated resumes cannot hammer the index.
        val deadline = plus(clock, DAY + jitter().coerceIn(0, MAX_JITTER))
        if (!save(saved.copy(nextAutoAt = deadline))) {
            if (manual) publish(state.copy(failed = true))
            return
        }
        val token = ++generation
        manualRequested = manual
        publish(state.copy(checking = true, failed = false, limited = false))
        try {
            val stop = request { result ->
                if (token == generation && foreground && state.checking) {
                    cancel = null
                    val userInitiated = manualRequested
                    manualRequested = false
                    result.fold({ update ->
                        save(saved.copy(rateFailures = 0, rateLimitedUntil = 0, cachedIndex = update.indexJson, autoFailures = 0))
                        publish(State(update = update))
                    }, { error ->
                        // Ordinary errors back off automatic checks without cooling down manual retries.
                        val failures = (saved.autoFailures + 1).coerceAtMost(5)
                        val delay = DAY * (1L shl (failures - 1))
                        save(saved.copy(autoFailures = failures,
                            nextAutoAt = plus(now(), delay + jitter().coerceIn(0, MAX_JITTER))))
                        val limited = error is ReleaseUpdateHttpException && error.code in setOf(429, 503) &&
                            (error.code == 429 || error.retryAfter != null)
                        if (limited) {
                            val rateFailures = (saved.rateFailures + 1).coerceAtMost(8)
                            val fallback = (15 * 60_000L * (1L shl (rateFailures - 1))).coerceAtMost(DAY)
                            val delay = updateRetryDelay((error as ReleaseUpdateHttpException).retryAfter, now()) ?: fallback
                            // A failed disk write must never remove the in-process server cooldown.
                            saved = saved.copy(rateFailures = rateFailures,
                                rateLimitedUntil = plus(now(), delay))
                            save(saved)
                        }
                        publish(state.copy(checking = false, failed = userInitiated && !limited, limited = userInitiated && limited))
                    })
                }
            }
            if (token == generation && foreground && state.checking) cancel = stop else runCatching(stop)
        } catch (_: Exception) {
            if (token == generation) {
                val userInitiated = manualRequested
                manualRequested = false
                publish(state.copy(checking = false, failed = userInitiated))
            }
        }
    }
    private fun stop() {
        generation++
        manualRequested = false
        cancel?.let { runCatching(it) }; cancel = null
        if (state.checking) publish(state.copy(checking = false))
    }
    private fun publish(value: State) { state = value; observers.toList().forEach { it(value) } }
    companion object {
        const val DAY = 24 * 60 * 60_000L
        const val MAX_JITTER = 2 * 60 * 60_000L
        private fun plus(a: Long, b: Long) = if (a > Long.MAX_VALUE - b) Long.MAX_VALUE else a + b
    }
}
