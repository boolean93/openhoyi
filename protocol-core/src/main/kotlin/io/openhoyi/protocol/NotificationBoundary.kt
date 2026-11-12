package io.openhoyi.protocol

internal enum class NotificationBoundary { CONTINUE, RESYNC, AMBIGUOUS }

/** A header alone is not a boundary: payloads may contain the same bytes. Compare complete
 * candidates before consuming any incoming bytes (notably reserved-zero TIMEMORE CRCs).
 */
internal fun <T> notificationBoundary(
    pending: ByteArray, incoming: ByteArray, prefixSize: Int,
    first: Int, second: Int, totalSize: (ByteArray) -> Int,
    decode: (ByteArray) -> DecodeResult<T>, checksumError: String,
): NotificationBoundary {
    fun total(bytes: ByteArray): Int? {
        if (bytes.size < prefixSize || bytes[0].toInt() and 255 != first ||
            bytes[1].toInt() and 255 != second) return null
        return totalSize(bytes).takeIf { it in prefixSize + 1..256 }
    }
    if (pending.isEmpty()) return NotificationBoundary.CONTINUE
    val freshTotal = total(incoming) ?: return NotificationBoundary.CONTINUE
    if (incoming.size < freshTotal) return NotificationBoundary.CONTINUE
    val fresh = decode(incoming.copyOf(freshTotal))
    if (fresh is DecodeResult.Invalid) return NotificationBoundary.CONTINUE
    if (fresh is DecodeResult.Unknown) return NotificationBoundary.AMBIGUOUS
    val joined = pending + incoming.take(256 - pending.size).toByteArray()
    val joinedTotal = total(joined) ?: return NotificationBoundary.CONTINUE
    if (joined.size < joinedTotal) return NotificationBoundary.AMBIGUOUS
    return when (val result = decode(joined.copyOf(joinedTotal))) {
        is DecodeResult.Invalid -> if (result.reason == checksumError) NotificationBoundary.RESYNC
            else NotificationBoundary.CONTINUE
        else -> NotificationBoundary.AMBIGUOUS
    }
}

/** Delay a possible fresh notification until its integrity can be checked. Stored candidate
 * bytes are bounded to two protocol frames; a stalled fragment cannot publish a reading.
 */
internal class NotificationBoundaryCandidate(
    private val first: Int, private val second: Int, private val prefixSize: Int,
    private val totalSize: (ByteArray) -> Int,
    private val valid: (ByteArray) -> Boolean,
) {
    var ambiguous = false
        private set
    private val nested = mutableListOf<Int>()
    private var held = byteArrayOf()
    fun prepare(hasPending: Boolean, bytes: ByteArray): ByteArray? {
        ambiguous = false
        if (held.isNotEmpty() && bytes.isNotEmpty() && bytes[0].toInt() and 255 == first)
            nested += held.size
        val candidate = if (held.isNotEmpty()) held + bytes else bytes
        held = byteArrayOf()
        var waiting = false
        val iterator = nested.iterator()
        while (iterator.hasNext()) {
            val offset = iterator.next()
            val tail = candidate.copyOfRange(offset, minOf(candidate.size, offset + 256))
            if (tail.size >= 2 && tail[1].toInt() and 255 != second) { iterator.remove(); continue }
            if (tail.size < prefixSize) { waiting = true; continue }
            val total = totalSize(tail)
            if (total !in prefixSize + 1..256) { iterator.remove(); continue }
            if (tail.size < total) { waiting = true; continue }
            if (valid(tail.copyOf(total))) ambiguous = true
            iterator.remove()
        }
        if (ambiguous || (waiting && candidate.size >= 512)) {
            ambiguous = true
            nested.clear()
            return candidate
        }
        if (waiting) { held = candidate; return null }
        if (!hasPending || candidate.isEmpty() || candidate[0].toInt() and 255 != first)
            return candidate
        if (candidate.size >= 2 && candidate[1].toInt() and 255 != second) return candidate
        val required = if (candidate.size < prefixSize) prefixSize else totalSize(candidate)
        if (required !in prefixSize + 1..256 && candidate.size >= prefixSize) return candidate
        if (candidate.size < required) {
            held = candidate // A single candidate frame is bounded to 256 bytes.
            return null
        }
        return candidate
    }
}
