package io.openhoyi.mobile

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Test

/** Synthetic local bytes only. No provider, picker, Android decoder or real user files. */
class AiAttachmentTest {
    private val jpeg = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xd9.toByte())
    private fun preparer(process: (ByteArray, String) -> ByteArray = { _, _ -> jpeg.copyOf() }) = AiAttachmentPreparer(AiAttachmentImageProcessor(process))
    private fun prepare(bytes: ByteArray, mime: String = "text/plain", declared: Long? = null, existing: List<AiAttachment> = emptyList()) =
        preparer().prepare("../../sample\u202E\n.bad", mime, declared, existing) { ByteArrayInputStream(bytes) }
    private fun failure(problem: AiAttachmentProblem, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue(error is AiAttachmentFailure)
        assertEquals(problem, (error as AiAttachmentFailure).problem)
        assertEquals(problem.name, error.message); assertNull(error.cause)
    }
    private fun text(size: Int) = AiAttachment(UUID.randomUUID().toString(), "sample.txt", "text/plain", size, AiAttachmentKind.TEXT)
    @Test fun localTextMetadataIsSafeOwnedAndWiped() {
        val source = "synthetic中文".toByteArray()
        val prepared = prepare(source)
        assertEquals("sample.txt", prepared.attachment.name)
        assertEquals(AiAttachmentKind.TEXT, prepared.attachment.kind)
        assertArrayEquals(source, prepared.bytes)
        assertFalse(prepared.toString().contains("中文")); assertFalse(prepared.attachment.toString().contains("sample"))
        prepared.close(); assertTrue(prepared.bytes.all { it == 0.toByte() })
    }
    @Test fun boundedReadDoesNotTrustDeclaredSizeAndClosesProvider() {
        var closed = false; var consumed = 0
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 'a'.code }
            override fun close() { closed = true }
        }
        failure(AiAttachmentProblem.SIZE) { preparer().prepare("large.txt", "text/plain", 1, emptyList()) { stream } }
        assertTrue(closed); assertEquals(AiAttachmentPolicy.MAX_TEXT_BYTES + 1, consumed)
    }
    @Test fun countMimeAndDeclaredLimitRejectBeforeOpening() {
        fun rejected(mime: String, size: Long?, existing: List<AiAttachment>, problem: AiAttachmentProblem) {
            failure(problem) { preparer().prepare("sample", mime, size, existing) { fail("Must not open"); ByteArrayInputStream(byteArrayOf()) } }
        }
        rejected("text/plain", 1, List(3) { text(1) }, AiAttachmentProblem.COUNT)
        rejected("video/mp4", 1, emptyList(), AiAttachmentProblem.TYPE)
        rejected("text/plain", AiAttachmentPolicy.MAX_TEXT_BYTES + 1L, emptyList(), AiAttachmentProblem.SIZE)
        rejected("application/msword", 512, emptyList(), AiAttachmentProblem.DOCUMENT)
    }
    @Test fun strictUtf8ControlsAndAggregateTextBudget() {
        failure(AiAttachmentProblem.TEXT) { prepare(byteArrayOf(0xc3.toByte(), 0x28)) }
        failure(AiAttachmentProblem.TEXT) { prepare("abc\u0000secret".toByteArray()) }
        failure(AiAttachmentProblem.TEXT) { prepare("a\u202Etxt".toByteArray()) }
        failure(AiAttachmentProblem.SIZE) { prepare("two".toByteArray(), existing = listOf(text(AiAttachmentPolicy.MAX_TEXT_BYTES - 2))) }
        prepare("\uFEFFhello\n\tworld".toByteArray()).close()
    }
    @Test fun untrustedProviderErrorIsSanitized() {
        failure(AiAttachmentProblem.UNAVAILABLE) { preparer().prepare("private.txt", "text/plain", null, emptyList()) { error("content://private-token") } }
    }
    @Test fun imageSignaturePrecedesDecoderAndOutputIsNormalized() {
        var calls = 0
        val p = preparer { source, mime -> calls++; assertEquals("image/png", mime); assertEquals(8, source.size); jpeg.copyOf() }
        failure(AiAttachmentProblem.TYPE) { p.prepare("image.png", "image/png", null, emptyList()) { ByteArrayInputStream(jpeg) } }
        assertEquals(0, calls)
        val png = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10)
        p.prepare("image.png", "image/png", null, emptyList()) { ByteArrayInputStream(png) }.use {
            assertEquals("image.jpg", it.attachment.name); assertEquals("image/jpeg", it.attachment.mimeType)
        }
        assertEquals(1, calls)
    }
    @Test fun fakeProcessorOversizeRejectedAndSourceWiped() {
        var captured: ByteArray? = null
        val p = preparer { bytes, _ -> captured = bytes; ByteArray(AiAttachmentPolicy.MAX_IMAGE_BYTES + 1) }
        failure(AiAttachmentProblem.IMAGE) { p.prepare("image.jpg", "image/jpeg", null, emptyList()) { ByteArrayInputStream(jpeg) } }
        assertTrue(captured!!.all { it == 0.toByte() })
    }
    @Test fun documentsUseContentContainerNotExtension() {
        prepare("%PDF-1.7\nsynthetic".toByteArray(), "application/pdf").use {
            assertEquals("sample.pdf", it.attachment.name); assertEquals(AiAttachmentKind.DOCUMENT, it.attachment.kind)
        }
        failure(AiAttachmentProblem.DOCUMENT) { prepare("not a PDF".toByteArray(), "application/pdf") }
        val docx = zip("[Content_Types].xml", "word/document.xml")
        prepare(docx, "application/vnd.openxmlformats-officedocument.wordprocessingml.document").close()
        failure(AiAttachmentProblem.DOCUMENT) { prepare(docx, "application/vnd.openxmlformats-officedocument.presentationml.presentation") }
        failure(AiAttachmentProblem.DOCUMENT) { AiAttachmentDocuments.validate(zip("[Content_Types].xml", "../word/document.xml"), "application/vnd.openxmlformats-officedocument.wordprocessingml.document") }
    }
    @Test fun zipBombAndTooManyEntriesAreRejected() {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("[Content_Types].xml")); z.write(ByteArray(33 * 1024 * 1024)); z.closeEntry()
            z.putNextEntry(ZipEntry("word/document.xml")); z.closeEntry()
        }
        failure(AiAttachmentProblem.DOCUMENT) { AiAttachmentDocuments.validate(out.toByteArray(), "application/vnd.openxmlformats-officedocument.wordprocessingml.document") }
        failure(AiAttachmentProblem.DOCUMENT) { AiAttachmentDocuments.validate(zip(*Array(513) { "file$it" }), "application/vnd.openxmlformats-officedocument.wordprocessingml.document") }
    }
    @Test fun animationNeverSilentlyFlattens() {
        val webp = "RIFF".toByteArray() + byteArrayOf(14, 0, 0, 0) + "WEBPVP8X".toByteArray() + byteArrayOf(2, 0, 0, 0, 2, 0)
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentImageMetadata.orientation(webp, "image/webp") }
        val png = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10) + byteArrayOf(0, 0, 0, 0) + "acTL".toByteArray() + ByteArray(4)
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentImageMetadata.orientation(png, "image/png") }
    }
    @Test fun exifOrientationIsBoundedAndRespected() {
        val tiff = byteArrayOf(73, 73, 42, 0, 8, 0, 0, 0, 1, 0, 0x12, 1, 3, 0, 1, 0, 0, 0, 6, 0, 0, 0)
        val exif = "Exif\u0000\u0000".toByteArray() + tiff
        val image = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe1.toByte(), 0, (exif.size + 2).toByte()) + exif + byteArrayOf(0xff.toByte(), 0xd9.toByte())
        assertEquals(6, AiAttachmentImageMetadata.orientation(image, "image/jpeg"))
        image[18] = 0x7f // Corrupt IFD offset, not a guessed orientation.
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentImageMetadata.orientation(image, "image/jpeg") }
    }
    private fun zip(vararg names: String): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> names.forEach { z.putNextEntry(ZipEntry(it)); z.write("synthetic".toByteArray()); z.closeEntry() } }
    }.toByteArray()
    @Test fun knownProviderSizeMismatchNeverPublishesPartialAttachmentAndClosesStream() {
        for (declared in listOf(3L, 8L)) {
            var closed = false; var returned = false
            val stream = object : ByteArrayInputStream("short".toByteArray()) {
                override fun close() { closed = true; super.close() }
            }
            failure(AiAttachmentProblem.UNAVAILABLE) {
                preparer().prepare("partial.txt", "text/plain", declared, emptyList()) { stream }.use { returned = true }
            }
            assertTrue(closed); assertFalse(returned)
        }
        prepare("short".toByteArray(), declared = 5).close()
        prepare("short".toByteArray(), declared = null).close() // Unknown size cannot certify the original's completeness.
    }

    @Test fun largeImageSourceIsNormalizedButNewCapStillRejectsBeforeDecode() {
        val bytes = ByteArray(5 * 1024 * 1024).apply { jpeg.copyInto(this) }
        var calls = 0
        val p = preparer { source, _ -> calls++; assertEquals(bytes.size, source.size); jpeg.copyOf() }
        p.prepare("photo.jpg", "image/jpeg", bytes.size.toLong(), emptyList()) { ByteArrayInputStream(bytes) }.use {
            assertEquals(jpeg.size, it.bytes.size)
        }
        assertEquals(1, calls)
        failure(AiAttachmentProblem.SOURCE_SIZE) {
            p.prepare("photo.jpg", "image/jpeg", AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES + 1L, emptyList()) {
                fail("Must not open oversized source"); ByteArrayInputStream(bytes)
            }
        }
        var consumed = 0
        val endless = object : InputStream() {
            override fun read(): Int { consumed++; return 0 }
            override fun read(b: ByteArray, off: Int, len: Int): Int { consumed += len; return len }
        }
        failure(AiAttachmentProblem.SOURCE_SIZE) { p.prepare("photo.jpg", "image/jpeg", null, emptyList()) { endless } }
        assertEquals(AiAttachmentPolicy.MAX_SOURCE_IMAGE_BYTES + 1, consumed)
        assertEquals(1, calls)
        failure(AiAttachmentProblem.TYPE) { prepare(jpeg, "image/heic") }
    }
    @Test fun samplingBoundsBothCeilingDimensionsAndPixelBudget() {
        for ((w, h) in listOf(8000 to 6000, 10000 to 10000, 32768 to 3000,
                6401 to 3201, 3201 to 3201, 1600 to 1600, 1 to 32768)) {
            val sample = AiAttachmentImageDecodePolicy.sample(w, h)
            val width = (w.toLong() + sample - 1) / sample
            val height = (h.toLong() + sample - 1) / sample
            assertTrue(maxOf(width, height) <= AiAttachmentImageDecodePolicy.MAX_DECODE_EDGE)
            assertTrue(width * height <= AiAttachmentImageDecodePolicy.MAX_DECODE_PIXELS)
            assertEquals(0, sample and (sample - 1))
        }
        failure(AiAttachmentProblem.PIXELS) { AiAttachmentImageDecodePolicy.sample(10001, 10000) }
        failure(AiAttachmentProblem.PIXELS) { AiAttachmentImageDecodePolicy.sample(32769, 1) }
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentImageDecodePolicy.sample(-1, -1) }
    }

    @Test fun sourceReadOutOfMemoryIsSanitizedWithoutCauseOrDetails() {
        failure(AiAttachmentProblem.UNAVAILABLE) {
            preparer().prepare("photo.jpg", "image/jpeg", null, emptyList()) { throw OutOfMemoryError("private-source") }
        }
    }
    @Test fun jpegQualityPreservesHighestFittingQualityWithinBoundedAttempts() {
        var calls = 0
        assertEquals(100, AiAttachmentJpegQuality.choose { calls++; 700_000 })
        assertEquals(1, calls)
        calls = 0
        val chosen = AiAttachmentJpegQuality.choose { quality -> calls++; quality * 11_000 }
        assertEquals(95, chosen)
        assertTrue(calls <= 8)
        assertTrue(chosen * 11_000 <= AiAttachmentPolicy.MAX_IMAGE_BYTES)
        assertTrue((chosen + 1) * 11_000 > AiAttachmentPolicy.MAX_IMAGE_BYTES)
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentJpegQuality.choose { AiAttachmentPolicy.MAX_IMAGE_BYTES + 1 } }
    }

    @Test fun qualityFloorAndResizeAreBoundedAndPreserveDetailFirst() {
        assertEquals(3200, AiAttachmentPolicy.MAX_EDGE)
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentJpegQuality.choose(80) { quality -> quality * 14_000 } }
        assertEquals(74, AiAttachmentJpegQuality.choose { quality -> quality * 14_000 })
        var edge = 3200
        var steps = 0
        while (AiAttachmentImageResizePolicy.minQuality(edge) == 80) {
            val next = AiAttachmentImageResizePolicy.nextEdge(edge)
            assertTrue(next < edge); edge = next; steps++
        }
        assertTrue(edge <= 800); assertTrue(steps < 8)
    }

    @Test fun attachmentDiagnosticsUseClosedFieldsAndDoNotBreakPreparation() {
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val trace = AiAttachmentTrace { kind, fields -> events += kind to fields }
        trace.event(AiAttachmentStage.DECODE, bytes = 745_000, width = 2480, height = 2248)
        trace.failed(AiAttachmentProblem.IMAGE)
        assertEquals(setOf("stage", "bytes", "width", "height"), events[0].second.keys)
        assertEquals(mapOf("stage" to "DECODE", "problem" to "IMAGE"), events[1].second)
        AiAttachmentTrace { _, _ -> error("logging failure") }.event(AiAttachmentStage.READ)
    }

    @Test fun imageEncodingBudgetDoesNotGrowOrdinarySourcesAndBoundsTinyFormatOverhead() {
        assertEquals(756_190, AiAttachmentImageEncodeBudget.forSource(756_190))
        assertEquals(AiAttachmentPolicy.MAX_IMAGE_BYTES, AiAttachmentImageEncodeBudget.forSource(7_042_474))
        assertEquals(1024, AiAttachmentImageEncodeBudget.forSource(1024))
        assertEquals(1024, AiAttachmentImageEncodeBudget.forSource(80))
        var tinyEdge = 3200
        repeat(11) { tinyEdge = AiAttachmentImageResizePolicy.nextEdge(tinyEdge) }
        assertTrue(tinyEdge <= 64) // Tiny format-overhead budgets have a bounded viable fallback.
        val budget = AiAttachmentImageEncodeBudget.forSource(756_190)
        val quality = AiAttachmentJpegQuality.choose(maxBytes = budget) { it * 8_000 }
        assertEquals(94, quality)
        assertTrue(quality * 8_000 <= 756_190)
        assertTrue((quality + 1) * 8_000 > 756_190)
        assertEquals(100, AiAttachmentJpegQuality.choose(maxBytes = 1024) { 650 })
        failure(AiAttachmentProblem.IMAGE) { AiAttachmentJpegQuality.choose(maxBytes = 1024) { 1025 } }
    }

    @Test fun boundedLargerUtf8JsonIsImportedWholeRatherThanTruncated() {
        val prefix = "{\"notes\":\""; val suffix = "\"}"
        val unicode = "汉".repeat(40_000)
        val padding = 128 * 1024 - (prefix + unicode + suffix).toByteArray().size
        val raw = (prefix + unicode + "a".repeat(padding) + suffix).toByteArray()
        assertEquals(128 * 1024, raw.size)
        prepare(raw, "application/json").use {
            assertArrayEquals(raw, it.bytes)
            assertTrue(AiAttachmentPolicy.validateText(it.bytes).endsWith(suffix))
        }
        failure(AiAttachmentProblem.SIZE) { prepare(raw + byteArrayOf(32), "application/json") }
    }
    @Test fun largerTextStillSharesOneRoundBudgetAndKeepsOtherCaps() {
        val half = AiAttachmentPolicy.MAX_TEXT_BYTES / 2
        prepare(ByteArray(half) { 97 }, existing = listOf(text(half))).close()
        failure(AiAttachmentProblem.SIZE) { prepare(byteArrayOf(97), existing = listOf(text(half), text(half))) }
        assertEquals(20 * 1024 * 1024, AiAttachmentPolicy.MAX_TOTAL_BYTES)
        assertEquals(1024 * 1024, AiAttachmentPolicy.MAX_EXTRACTED_BYTES)
        val invalidUtf8 = ByteArray(AiAttachmentPolicy.MAX_TEXT_BYTES) { 97 }.apply { this[lastIndex] = 0xc3.toByte() }
        failure(AiAttachmentProblem.TEXT) { prepare(invalidUtf8) }
    }

}
