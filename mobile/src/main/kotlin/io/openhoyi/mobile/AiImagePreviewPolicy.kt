package io.openhoyi.mobile

/** Display-only identity. A matching id with different metadata must never reuse decoded pixels. */
internal data class AiImagePreviewKey(val epoch: String, val id: String, val name: String,
    val mime: String, val size: Int, val kind: AiAttachmentKind) {
    companion object {
        fun of(epoch: String, item: AiAttachment) = AiImagePreviewKey(epoch, item.id, item.name, item.mimeType, item.size, item.kind)
    }
}
internal object AiImagePreviewPolicy {
    const val MAX_EDGE = 512
    const val CACHE_BYTES = 4 * 1024 * 1024
    fun sample(width: Int, height: Int): Int {
        require(width > 0 && height > 0 && width.toLong() * height <= AiAttachmentPolicy.MAX_PIXELS)
        var sample = 1
        while ((maxOf(width, height).toLong() + sample - 1) / sample > MAX_EDGE) sample *= 2
        return sample
    }
    /** No upscaling, cropping or aspect-ratio distortion. */
    fun fit(width: Int, height: Int, maxWidth: Int, maxHeight: Int): Pair<Int, Int> {
        require(width > 0 && height > 0 && maxWidth > 0 && maxHeight > 0)
        val scale = minOf(1.0, maxWidth.toDouble() / width, maxHeight.toDouble() / height)
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
    /** Failure is a display placeholder only; byte ownership always ends here. */
    fun <T> read(item: AiAttachment, load: (AiAttachment) -> AiPreparedAttachment, decode: (ByteArray) -> T): T? =
        try { load(item).use { decode(it.bytes) } }
        catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        catch (_: OutOfMemoryError) { null }
    fun visibleText(text: String, items: List<AiAttachment>) =
        AiAttachmentConversation.visible(text, items.filter { it.kind != AiAttachmentKind.IMAGE })
}
/** Main-thread confined, byte/entry-bounded LRU. Null is a cached unavailable result, not a retry loop. */
internal class AiImagePreviewCache<T>(private val maxBytes: Int = AiImagePreviewPolicy.CACHE_BYTES) {
    data class Entry<T>(val value: T?, val bytes: Int)
    private val values = LinkedHashMap<AiImagePreviewKey, Entry<T>>(16, .75f, true)
    private var session: String? = null
    var bytes = 0; private set
    fun begin(token: String?, live: Set<AiImagePreviewKey>) {
        if (session != token) { clear(); session = token }
        val iterator = values.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in live) { bytes -= entry.value.bytes; iterator.remove() }
        }
    }
    operator fun get(key: AiImagePreviewKey) = values[key]
    fun put(key: AiImagePreviewKey, value: T?, size: Int) {
        require(size >= 0 && (value != null || size == 0))
        values.remove(key)?.let { bytes -= it.bytes }
        if (size > maxBytes) return
        values[key] = Entry(value, size); bytes += size
        while (bytes > maxBytes || values.size > 128) {
            val iterator = values.iterator(); val first = iterator.next()
            bytes -= first.value.bytes; iterator.remove()
        }
    }
    fun clear() { values.clear(); bytes = 0; session = null }
}
