package io.openhoyi.mobile

import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

internal enum class AiAttachmentKind { IMAGE, TEXT, DOCUMENT, OCR_TEXT }
internal enum class AiAttachmentProblem { COUNT, STORAGE_QUOTA, SIZE, SOURCE_SIZE, PIXELS, TYPE, IMAGE, TEXT, UNAVAILABLE, DOCUMENT }
/** Provider exceptions, filenames and content must not enter logs or error messages. */
internal class AiAttachmentFailure(val problem: AiAttachmentProblem, val metadataReason: AiAttachmentImageMetadata.Reason? = null) : Exception(problem.name)
internal class AiAttachment(val id: String, val name: String, val mimeType: String, val size: Int, val kind: AiAttachmentKind) {
    init {
        require(id.matches(Regex("[a-f0-9-]{36}")) && runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false))
        require(name == AiAttachmentPolicy.safeName(name) && name.isNotBlank())
        require(size in 1..AiAttachmentPolicy.MAX_DOCUMENT_BYTES)
        require((kind == AiAttachmentKind.IMAGE && mimeType == "image/jpeg" && size <= AiAttachmentPolicy.MAX_IMAGE_BYTES) ||
            (kind == AiAttachmentKind.DOCUMENT && mimeType in AiAttachmentPolicy.DOCUMENT_MIMES) ||
            (kind == AiAttachmentKind.OCR_TEXT && mimeType == "text/plain" && size <= AiAttachmentPolicy.MAX_EXTRACTED_BYTES) ||
            (kind == AiAttachmentKind.TEXT && mimeType in AiAttachmentPolicy.TEXT_MIMES && size <= AiAttachmentPolicy.MAX_TEXT_BYTES))
    }
    override fun toString() = "AiAttachment"
}
/** A transient owned buffer. The caller must close after saving/sending, including failures. */
internal class AiPreparedAttachment(val attachment: AiAttachment, val bytes: ByteArray) : AutoCloseable {
    override fun close() { bytes.fill(0) }
    override fun toString() = "AiPreparedAttachment"
}
internal object AiAttachmentPolicy {
    const val MAX_COUNT = 3
    const val MAX_SOURCE_IMAGE_BYTES = 20 * 1024 * 1024
    const val MAX_IMAGE_BYTES = 1024 * 1024
    const val MAX_TEXT_BYTES = 128 * 1024
    const val MAX_EXTRACTED_BYTES = 1024 * 1024
    const val MAX_DOCUMENT_BYTES = 10 * 1024 * 1024
    const val MAX_TOTAL_BYTES = 20 * 1024 * 1024
    const val MAX_PIXELS = 100_000_000L
    const val MAX_EDGE = 3200
    val IMAGE_MIMES = setOf("image/jpeg", "image/png", "image/webp")
    val TEXT_MIMES = setOf("text/plain", "text/markdown", "text/csv", "application/json")
    val DOCUMENT_MIMES = setOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation")
    fun safeName(name: String): String {
        val leaf = name.substringAfterLast('/').substringAfterLast('\\')
        val safe = StringBuilder()
        var at = 0
        while (at < leaf.length && safe.length < 96) {
            val point = leaf.codePointAt(at); at += Character.charCount(point)
            if (Character.getType(point) in setOf(Character.CONTROL.toInt(), Character.FORMAT.toInt(),
                    Character.LINE_SEPARATOR.toInt(), Character.PARAGRAPH_SEPARATOR.toInt(), Character.SURROGATE.toInt())) continue
            if (safe.length + Character.charCount(point) > 96) break
            safe.appendCodePoint(point)
        }
        return safe.toString().trim().ifBlank { "attachment" }
    }
    fun typedName(name: String, mime: String): String {
        val extension = when (mime) {
            "image/jpeg" -> "jpg"; "application/pdf" -> "pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
            "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
            "application/json" -> "json"; "text/markdown" -> "md"; "text/csv" -> "csv"; else -> "txt"
        }
        return safeName(safeName(name).substringBeforeLast('.').take(88)).ifBlank { "attachment" } + "." + extension
    }
    fun kind(mime: String): AiAttachmentKind = when {
        mime in IMAGE_MIMES -> AiAttachmentKind.IMAGE
        mime in TEXT_MIMES -> AiAttachmentKind.TEXT
        mime in DOCUMENT_MIMES -> AiAttachmentKind.DOCUMENT
        mime in setOf("application/msword", "application/vnd.ms-powerpoint") -> throw AiAttachmentFailure(AiAttachmentProblem.DOCUMENT)
        else -> throw AiAttachmentFailure(AiAttachmentProblem.TYPE)
    }
    fun validateText(bytes: ByteArray, maxBytes: Int = MAX_TEXT_BYTES): String {
        require(maxBytes in MAX_TEXT_BYTES..MAX_EXTRACTED_BYTES)
        if (bytes.isEmpty() || bytes.size > maxBytes) throw AiAttachmentFailure(AiAttachmentProblem.SIZE)
        val text = try { Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
        catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.TEXT) }
        if (text.isBlank() || text.any { (Character.isISOControl(it) && it !in "\n\r\t") || it in '\u202A'..'\u202E' ||
                it in '\u2066'..'\u2069' || it in "\u061C\u200E\u200F" }) throw AiAttachmentFailure(AiAttachmentProblem.TEXT)
        return text
    }
    /** Content and picker MIME must agree before invoking any image decoder. */
    fun imageSignature(bytes: ByteArray, mime: String): Boolean = when (mime) {
        "image/jpeg" -> bytes.size >= 3 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte() && bytes[2] == 0xff.toByte()
        "image/png" -> bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10))
        "image/webp" -> bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP"
        else -> false
    }
}
internal fun interface AiAttachmentImageProcessor {
    /** Returns a metadata-free, static JPEG with dimensions <= MAX_EDGE and bytes <= MAX_IMAGE_BYTES. */
    fun jpeg(source: ByteArray, mimeType: String): ByteArray
}
/** No network, resolver URI persistence, logging, API keys or disk plaintext in this layer. Run on IO, not UI. */
internal class AiAttachmentPreparer(private val images: AiAttachmentImageProcessor, private val trace: AiAttachmentTrace) {
    constructor(images: AiAttachmentImageProcessor) : this(images, AiAttachmentTrace())
    fun prepare(name: String, mimeType: String, declaredSize: Long?, existing: List<AiAttachment>, open: () -> InputStream): AiPreparedAttachment {
        if (existing.size >= AiAttachmentPolicy.MAX_COUNT) throw AiAttachmentFailure(AiAttachmentProblem.COUNT)
        val kind = AiAttachmentPolicy.kind(mimeType)
        val limit = when (kind) { AiAttachmentKind.IMAGE -> AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES
            AiAttachmentKind.TEXT -> AiAttachmentPolicy.MAX_TEXT_BYTES; AiAttachmentKind.OCR_TEXT -> throw AiAttachmentFailure(AiAttachmentProblem.TYPE); AiAttachmentKind.DOCUMENT -> AiAttachmentPolicy.MAX_DOCUMENT_BYTES }
        val sizeProblem = if (kind == AiAttachmentKind.IMAGE) AiAttachmentProblem.SOURCE_SIZE else AiAttachmentProblem.SIZE
        if (declaredSize != null && declaredSize <= 0)
            throw AiAttachmentFailure(if (kind == AiAttachmentKind.IMAGE) AiAttachmentProblem.IMAGE else AiAttachmentProblem.SIZE)
        if (declaredSize != null && declaredSize > limit) throw AiAttachmentFailure(sizeProblem)
        var source: ByteArray? = null
        var result: ByteArray? = null
        try {
            trace.event(AiAttachmentStage.READ)
            source = open().use { input -> readBounded(input, limit, sizeProblem) }
            trace.event(AiAttachmentStage.HEADER, bytes = source.size)
            // Known provider size is a consistency check, never a reason to stop reading early.
            if (declaredSize != null && source.size.toLong() != declaredSize)
                throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE)
            val preparedBytes = if (kind == AiAttachmentKind.IMAGE) {
                if (!AiAttachmentPolicy.imageSignature(source, mimeType)) throw AiAttachmentFailure(AiAttachmentProblem.TYPE)
                val normalized = images.jpeg(source, mimeType).let { if (it === source) it.copyOf() else it }
                result = normalized // Own and erase even when validation below fails.
                if (normalized.isEmpty() || normalized.size > AiAttachmentPolicy.MAX_IMAGE_BYTES || !AiAttachmentPolicy.imageSignature(normalized, "image/jpeg"))
                    throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
                normalized
            } else if (kind == AiAttachmentKind.TEXT) {
                AiAttachmentPolicy.validateText(source)
                if (existing.filter { it.kind == AiAttachmentKind.TEXT }.sumOf { it.size.toLong() } + source.size > AiAttachmentPolicy.MAX_TEXT_BYTES)
                    throw AiAttachmentFailure(AiAttachmentProblem.SIZE)
                source.copyOf()
            } else {
                AiAttachmentDocuments.validate(source, mimeType)
                source.copyOf()
            }
            result = preparedBytes
            if (existing.sumOf { it.size.toLong() } + preparedBytes.size > AiAttachmentPolicy.MAX_TOTAL_BYTES)
                throw AiAttachmentFailure(AiAttachmentProblem.SIZE)
            val metadata = AiAttachment(UUID.randomUUID().toString(), AiAttachmentPolicy.typedName(name, if (kind == AiAttachmentKind.IMAGE) "image/jpeg" else mimeType),
                if (kind == AiAttachmentKind.IMAGE) "image/jpeg" else mimeType, preparedBytes.size, kind)
            return AiPreparedAttachment(metadata, preparedBytes).also { result = null }
        } catch (failure: AiAttachmentFailure) { throw failure }
        catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        catch (_: OutOfMemoryError) { throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE) }
        finally { source?.fill(0); result?.fill(0) }
    }
    private fun readBounded(input: InputStream, limit: Int, sizeProblem: AiAttachmentProblem): ByteArray {
        val output = AiAttachmentBuffer()
        val buffer = ByteArray(8192)
        try {
            while (true) {
                // Read at most one byte past the cap, even if provider metadata lies.
                val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size() + 1))
                if (count == -1) break
                if (count == 0) throw AiAttachmentFailure(AiAttachmentProblem.UNAVAILABLE)
                if (output.size() + count > limit) throw AiAttachmentFailure(sizeProblem)
                output.write(buffer, 0, count)
            }
            if (output.size() == 0) throw AiAttachmentFailure(if (sizeProblem == AiAttachmentProblem.SOURCE_SIZE) AiAttachmentProblem.IMAGE else sizeProblem)
            return output.toByteArray()
        } finally { buffer.fill(0); output.wipe() }
    }
}

internal object AiAttachmentImageDecodePolicy {
    const val MAX_DECODE_EDGE = 3200
    const val MAX_DECODE_PIXELS = 6_000_000L
    fun sample(width: Int, height: Int): Int {
        if (width <= 0 || height <= 0) throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
        if (width > 32768 || height > 32768 || width.toLong() * height > AiAttachmentPolicy.MAX_PIXELS)
            throw AiAttachmentFailure(AiAttachmentProblem.PIXELS)
        var sample = 1
        fun edge(value: Int) = (value.toLong() + sample - 1) / sample
        while (maxOf(edge(width), edge(height)) > MAX_DECODE_EDGE || edge(width) * edge(height) > MAX_DECODE_PIXELS) sample *= 2
        return sample
    }
}

/** Highest fitting JPEG quality; bounded attempts, no fixed low-quality jumps. */
internal object AiAttachmentJpegQuality {
    fun choose(minQuality: Int = 1, maxBytes: Int = AiAttachmentPolicy.MAX_IMAGE_BYTES, sizeAt: (Int) -> Int): Int {
        require(minQuality in 1..100 && maxBytes in 1..AiAttachmentPolicy.MAX_IMAGE_BYTES)
        fun fits(quality: Int) = sizeAt(quality) in 1..maxBytes
        if (fits(100)) return 100
        var low = minQuality; var high = 99; var best = 0
        while (low <= high) {
            val middle = (low + high) / 2
            if (fits(middle)) { best = middle; low = middle + 1 } else high = middle - 1
        }
        if (best == 0) throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
        return best
    }
}

internal object AiAttachmentImageResizePolicy {
    fun nextEdge(edge: Int): Int = maxOf(1, if (edge <= 800) edge / 2 else edge * 4 / 5)
    fun minQuality(edge: Int): Int = if (edge > 800) 80 else 1
}

internal enum class AiAttachmentStage { PICKER, READ, HEADER, METADATA, DECODE, NORMALIZE, STORE, STORE_LOCK, STORE_ENCODE, STORE_KEY_LOOKUP, STORE_KEY_CREATE, STORE_ENCRYPT, STORE_QUOTA, STORE_TEMP, STORE_WRITE, STORE_PUBLISH, STORE_SYNC, READY }
/** Closed vocabulary and bounded numbers only. Never accepts URI, names, MIME or exception text. */
internal class AiAttachmentTrace(private val record: (String, Map<String, String>) -> Unit = { _, _ -> }) {
    private var stage = AiAttachmentStage.PICKER
    fun event(next: AiAttachmentStage, bytes: Int? = null, width: Int? = null, height: Int? = null) {
        stage = next
        emit(null, bytes, width, height)
    }
    fun failed(problem: AiAttachmentProblem, failure: Exception? = null) = emit(problem, null, null, null, failure)
    private fun emit(problem: AiAttachmentProblem?, bytes: Int?, width: Int?, height: Int?, failure: Exception? = null) {
        val fields = linkedMapOf("stage" to stage.name)
        problem?.let { fields["problem"] = it.name }
        (failure as? AiAttachmentFailure)?.metadataReason?.let { fields["metadataReason"] = it.name }
        failure?.let {
            fields["failureKind"] = when (it) {
                is android.system.ErrnoException -> "NATIVE"
                is SecurityException -> "SECURITY"
                is java.security.GeneralSecurityException -> "CRYPTO"
                is java.io.IOException -> "IO"
                is IllegalStateException -> "STATE"
                is IllegalArgumentException -> "ARGUMENT"
                else -> "OTHER"
            }
            if (it is android.system.ErrnoException && it.errno in 1..133) fields["errno"] = it.errno.toString()
        }
        bytes?.takeIf { it in 0..AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES }?.let { fields["bytes"] = it.toString() }
        width?.takeIf { it in 1..32768 }?.let { fields["width"] = it.toString() }
        height?.takeIf { it in 1..32768 }?.let { fields["height"] = it.toString() }
        try { record(if (problem == null) "ai.attachment.stage" else "ai.attachment.failed", fields) }
        catch (_: Exception) { } // Logging must never alter attachment behavior.
    }
}

internal object AiAttachmentImageEncodeBudget {
    // Very small PNG/WebP (or JPEG) can be smaller than a metadata-free JPEG envelope.
    // Only sub-1KiB sources get this bounded format-overhead exception, never normal photos.
    const val MIN_JPEG_BUDGET = 1024
    fun forSource(sourceBytes: Int): Int {
        require(sourceBytes in 1..AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES)
        return sourceBytes.coerceIn(MIN_JPEG_BUDGET, AiAttachmentPolicy.MAX_IMAGE_BYTES)
    }
}
