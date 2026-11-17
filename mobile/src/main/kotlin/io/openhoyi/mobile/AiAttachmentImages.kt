package io.openhoyi.mobile

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded decode, applies orientation, then re-encodes without EXIF/GPS or original metadata. */
internal class AndroidAiAttachmentImages(private val trace: AiAttachmentTrace = AiAttachmentTrace()) : AiAttachmentImageProcessor {
    override fun jpeg(source: ByteArray, mimeType: String): ByteArray {
        var decoded: Bitmap? = null; var scaled: Bitmap? = null; var rotated: Bitmap? = null; var encoding: Bitmap? = null
        val output = AiAttachmentBuffer()
        try {
            check(source.size <= AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES && AiAttachmentPolicy.imageSignature(source, mimeType))
            trace.event(AiAttachmentStage.METADATA)
            val orientation = AiAttachmentImageMetadata.orientation(source, mimeType)
            trace.event(AiAttachmentStage.DECODE)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            if (bounds.outMimeType != mimeType) throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
            val sample = AiAttachmentImageDecodePolicy.sample(bounds.outWidth, bounds.outHeight)
            trace.event(AiAttachmentStage.DECODE, width = bounds.outWidth, height = bounds.outHeight)
            decoded = BitmapFactory.decodeByteArray(source, 0, source.size, BitmapFactory.Options().apply {
                inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
            }) ?: error("Image")
            if (maxOf(decoded.width, decoded.height) > AiAttachmentImageDecodePolicy.MAX_DECODE_EDGE ||
                decoded.width.toLong() * decoded.height > AiAttachmentImageDecodePolicy.MAX_DECODE_PIXELS)
                throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
            trace.event(AiAttachmentStage.NORMALIZE, width = decoded.width, height = decoded.height)
            val ratio = minOf(1.0, AiAttachmentPolicy.MAX_EDGE.toDouble() / maxOf(decoded.width, decoded.height))
            scaled = if (ratio < 1) Bitmap.createScaledBitmap(decoded, maxOf(1, (decoded.width * ratio).toInt()),
                maxOf(1, (decoded.height * ratio).toInt()), true) else decoded
            val matrix = Matrix().apply {
                when (orientation) {
                    2 -> setScale(-1f, 1f); 3 -> setRotate(180f); 4 -> setScale(1f, -1f)
                    5 -> { setRotate(90f); postScale(-1f, 1f) }; 6 -> setRotate(90f)
                    7 -> { setRotate(-90f); postScale(-1f, 1f) }; 8 -> setRotate(-90f)
                }
            }
            rotated = if (orientation == 1) scaled else Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
            val byteBudget = AiAttachmentImageEncodeBudget.forSource(source.size)
            encoding = rotated
            // Preserve sampled detail first. Reduce dimensions only if quality 80 cannot fit.
            // At <=800px allow lower quality as a bounded final fallback.
            repeat(12) {
                val bitmap = encoding ?: error("Image")
                val edge = maxOf(bitmap.width, bitmap.height)
                val quality = try {
                    AiAttachmentJpegQuality.choose(AiAttachmentImageResizePolicy.minQuality(edge), byteBudget) { candidate ->
                        output.wipe()
                        check(bitmap.compress(Bitmap.CompressFormat.JPEG, candidate, output))
                        output.size()
                    }
                } catch (_: AiAttachmentFailure) { 0 }
                if (quality != 0) {
                    output.wipe()
                    check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output))
                    if (output.size() !in 1..byteBudget) throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
                    return output.toByteArray()
                }
                val next = AiAttachmentImageResizePolicy.nextEdge(edge)
                val ratio = next.toDouble() / edge
                encoding = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()),
                    maxOf(1, (bitmap.height * ratio).toInt()), true)
                if (bitmap !== rotated && bitmap !== scaled && bitmap !== decoded) bitmap.recycle()
            }
            throw AiAttachmentFailure(AiAttachmentProblem.IMAGE)
        } catch (failure: AiAttachmentFailure) { throw failure }
        catch (_: Exception) { throw AiAttachmentFailure(AiAttachmentProblem.IMAGE) }
        catch (_: OutOfMemoryError) { throw AiAttachmentFailure(AiAttachmentProblem.IMAGE) }
        finally {
            output.wipe()
            listOfNotNull(encoding, rotated, scaled, decoded).distinctBy { System.identityHashCode(it) }.forEach { it.recycle() }
        }
    }
}
/** Only bounded container metadata is inspected; animated input is rejected, never silently flattened. */
internal object AiAttachmentImageMetadata {
    enum class OrientationState { KNOWN, ABSENT, UNREADABLE, CONFLICT }
    enum class Reason { MIME, SOURCE_BUDGET, JPEG_MARKER, JPEG_SEGMENT, PNG_CHUNK, WEBP_RIFF, WEBP_CHUNK,
        ANIMATED, EXIF_HEADER, EXIF_IFD_BOUNDS, EXIF_ORIENTATION, EXIF_CONFLICT, EXIF_BUDGET }
    data class OrientationResult(val state: OrientationState, val value: Int? = null, val reason: Reason? = null)
    private class Invalid(val reason: Reason) : Exception(reason.name)
    private fun requireMetadata(valid: Boolean, reason: Reason) { if (!valid) throw Invalid(reason) }
    fun orientation(bytes: ByteArray, mime: String): Int = inspect(bytes, mime).let {
        when (it.state) {
            OrientationState.KNOWN -> requireNotNull(it.value)
            OrientationState.ABSENT -> 1
            else -> throw AiAttachmentFailure(AiAttachmentProblem.IMAGE, it.reason)
        }
    }
    /** Container errors never become absent orientation; no original text enters the result. */
    fun inspect(bytes: ByteArray, mime: String): OrientationResult {
        var known: Int? = null
        var hasExif = false
        fun readExif(start: Int, end: Int, allowRepeat: Boolean = false) {
            requireMetadata(!hasExif || allowRepeat, Reason.EXIF_CONFLICT)
            requireMetadata(end - start <= 256 * 1024, Reason.EXIF_BUDGET)
            hasExif = true
            val exif = bytes.copyOfRange(start, end)
            try {
                val value = tiffOrientation(exif)
                requireMetadata(value == null || known == null || known == value, Reason.EXIF_CONFLICT)
                if (value != null) known = value
            } finally { exif.fill(0) }
        }
        try {
            requireMetadata(bytes.size <= AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES, Reason.SOURCE_BUDGET)
            when (mime) {
                "image/jpeg" -> {
                    requireMetadata(bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xd8.toByte(), Reason.JPEG_MARKER)
                    var at = 2
                    var terminal = false
                    while (at < bytes.size) {
                        requireMetadata(bytes[at] == 0xff.toByte(), Reason.JPEG_MARKER)
                        while (at < bytes.size && bytes[at] == 0xff.toByte()) at++
                        requireMetadata(at < bytes.size, Reason.JPEG_MARKER)
                        val marker = bytes[at++].toInt() and 255
                        if (marker == 0xd9) { terminal = true; break }
                        if (marker == 0x01) continue
                        requireMetadata(marker != 0 && marker != 0xd8 && marker !in 0xd0..0xd7, Reason.JPEG_MARKER)
                        requireMetadata(at.toLong() + 2 <= bytes.size, Reason.JPEG_SEGMENT)
                        val length = ((bytes[at].toInt() and 255) shl 8) or (bytes[at + 1].toInt() and 255)
                        requireMetadata(length >= 2 && at.toLong() + length <= bytes.size, Reason.JPEG_SEGMENT)
                        if (marker == 0xda) { terminal = true; break } // Pixel decoder validates scan content.
                        if (marker == 0xe1 && length >= 8 && String(bytes, at + 2, 6, Charsets.US_ASCII) == "Exif\u0000\u0000")
                            readExif(at + 8, at + length, allowRepeat = true)
                        at += length
                    }
                    requireMetadata(terminal, Reason.JPEG_MARKER)
                }
                "image/png" -> {
                    val signature = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)
                    requireMetadata(bytes.size >= 8 && signature.indices.all { bytes[it] == signature[it] }, Reason.PNG_CHUNK)
                    var at = 8
                    var ended = false
                    while (at < bytes.size) {
                        requireMetadata(at.toLong() + 12 <= bytes.size, Reason.PNG_CHUNK)
                        val size = ByteBuffer.wrap(bytes, at, 4).order(ByteOrder.BIG_ENDIAN).int
                        val type = String(bytes, at + 4, 4, Charsets.US_ASCII)
                        val start = at + 8
                        requireMetadata(size >= 0 && start.toLong() + size + 4 <= bytes.size, Reason.PNG_CHUNK)
                        requireMetadata(type !in setOf("acTL", "fcTL", "fdAT", "ANIM", "ANMF"), Reason.ANIMATED)
                        if (type == "eXIf" || type == "EXIF") {
                            requireMetadata(size <= 256 * 1024, Reason.EXIF_BUDGET)
                            val prefix = if (size >= 6 && String(bytes, start, 6, Charsets.US_ASCII) == "Exif\u0000\u0000") 6 else 0
                            readExif(start + prefix, start + size)
                        }
                        at = start + size + 4
                        if (type == "IEND") {
                            requireMetadata(size == 0 && at == bytes.size, Reason.PNG_CHUNK)
                            ended = true; break
                        }
                    }
                    requireMetadata(ended, Reason.PNG_CHUNK)
                }
                "image/webp" -> {
                    requireMetadata(bytes.size >= 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" &&
                        String(bytes, 8, 4, Charsets.US_ASCII) == "WEBP", Reason.WEBP_RIFF)
                    val declared = ByteBuffer.wrap(bytes, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
                    requireMetadata(declared >= 4 && declared + 8 == bytes.size.toLong(), Reason.WEBP_RIFF)
                    var at = 12
                    while (at < bytes.size) {
                        requireMetadata(at.toLong() + 8 <= bytes.size, Reason.WEBP_CHUNK)
                        val size = ByteBuffer.wrap(bytes, at + 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                        val type = String(bytes, at, 4, Charsets.US_ASCII)
                        val start = at + 8
                        requireMetadata(size >= 0 && start.toLong() + size + size % 2 <= bytes.size, Reason.WEBP_CHUNK)
                        requireMetadata(type !in setOf("acTL", "ANIM", "ANMF"), Reason.ANIMATED)
                        if (type == "VP8X") {
                            requireMetadata(size >= 1, Reason.WEBP_CHUNK)
                            requireMetadata(bytes[start].toInt() and 2 == 0, Reason.ANIMATED)
                        }
                        if (type == "EXIF" || type == "eXIf") {
                            requireMetadata(size <= 256 * 1024, Reason.EXIF_BUDGET)
                            val prefix = if (size >= 6 && String(bytes, start, 6, Charsets.US_ASCII) == "Exif\u0000\u0000") 6 else 0
                            readExif(start + prefix, start + size)
                        }
                        at = start + size + size % 2
                    }
                }
                else -> throw Invalid(Reason.MIME)
            }
            return if (known == null) OrientationResult(OrientationState.ABSENT)
                else OrientationResult(OrientationState.KNOWN, known)
        } catch (failure: Invalid) {
            return OrientationResult(if (failure.reason == Reason.EXIF_CONFLICT) OrientationState.CONFLICT else OrientationState.UNREADABLE,
                reason = failure.reason)
        }
    }
    private fun tiffOrientation(bytes: ByteArray): Int? {
        requireMetadata(bytes.size >= 8, Reason.EXIF_HEADER)
        val b = ByteBuffer.wrap(bytes)
        b.order(when (String(bytes, 0, 2, Charsets.US_ASCII)) {
            "II" -> ByteOrder.LITTLE_ENDIAN; "MM" -> ByteOrder.BIG_ENDIAN; else -> throw Invalid(Reason.EXIF_HEADER)
        })
        requireMetadata(b.getShort(2).toInt() == 42, Reason.EXIF_HEADER)
        val root = b.getInt(4)
        if (root == 0) return null
        fun directory(offset: Int): Triple<Int?, List<Int>, Boolean> {
            requireMetadata(offset >= 8 && offset.toLong() + 2 <= bytes.size, Reason.EXIF_IFD_BOUNDS)
            val count = b.getShort(offset).toInt() and 65535
            requireMetadata(count <= 1024, Reason.EXIF_BUDGET)
            requireMetadata(offset.toLong() + 2 + count * 12L <= bytes.size, Reason.EXIF_IFD_BOUNDS)
            var orientation: Int? = null
            val children = mutableListOf<Int>()
            var invalidChild = false
            repeat(count) { i ->
                val at = offset + 2 + i * 12
                when (b.getShort(at).toInt() and 65535) {
                    0x112 -> {
                        requireMetadata(b.getShort(at + 2).toInt() == 3 && b.getInt(at + 4) == 1, Reason.EXIF_ORIENTATION)
                        val value = b.getShort(at + 8).toInt() and 65535
                        requireMetadata(value in 1..8, Reason.EXIF_ORIENTATION)
                        requireMetadata(orientation == null || orientation == value, Reason.EXIF_CONFLICT)
                        orientation = value
                    }
                    0x8769 -> {
                        // One bounded compatibility level, never a general TIFF graph traversal.
                        if (b.getShort(at + 2).toInt() == 4 && b.getInt(at + 4) == 1) children += b.getInt(at + 8)
                        else invalidChild = true
                    }
                    // GPS, descriptions and their value pointers are irrelevant to orientation.
                }
            }
            return Triple(orientation, children.distinct(), invalidChild)
        }
        val (primary, children, invalidChild) = directory(root)
        requireMetadata(primary != null || !invalidChild, Reason.EXIF_IFD_BOUNDS)
        requireMetadata(children.size <= 1, Reason.EXIF_BUDGET)
        var known = primary
        for (child in children) {
            if (child == 0) continue
            try {
                requireMetadata(child != root, Reason.EXIF_IFD_BOUNDS)
                val (value, nested, invalidNested) = directory(child)
                requireMetadata(value != null || (nested.isEmpty() && !invalidNested), Reason.EXIF_IFD_BOUNDS)
                requireMetadata(value == null || known == null || value == known, Reason.EXIF_CONFLICT)
                if (value != null) known = value
            } catch (failure: Invalid) {
                // A complete, valid primary orientation survives an unreadable ancillary IFD.
                // Invalid explicit directions/conflicts are never treated as harmless metadata.
                if (primary == null || failure.reason !in setOf(Reason.EXIF_IFD_BOUNDS, Reason.EXIF_BUDGET)) throw failure
            }
        }
        return known
    }
}
