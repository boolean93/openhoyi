package io.openhoyi.mobile

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

/** Synthetic container metadata only; never decodes pixels, reads a store or sends an API call. */
class AiAttachmentImageMetadataTest {
    private val exif = "Exif\u0000\u0000".toByteArray()
    private fun tiff(value: Int?, order: ByteOrder = ByteOrder.LITTLE_ENDIAN, nullIfd: Boolean = false): ByteArray {
        val out = ByteBuffer.allocate(if (value == null) 14 else 26).order(order)
        out.put(if (order == ByteOrder.LITTLE_ENDIAN) byteArrayOf(73, 73) else byteArrayOf(77, 77))
        out.putShort(42).putInt(if (nullIfd) 0 else 8)
        out.putShort(if (value == null) 0 else 1)
        if (value != null) out.putShort(0x112).putShort(3).putInt(1).putShort(value.toShort()).putShort(0)
        out.putInt(0)
        return out.array()
    }
    private fun app1(payload: ByteArray, fill: Int = 1): ByteArray =
        ByteArray(fill) { 0xff.toByte() } + byteArrayOf(0xe1.toByte(), ((payload.size + 2) shr 8).toByte(), (payload.size + 2).toByte()) + payload
    private fun jpeg(vararg segments: ByteArray) = byteArrayOf(0xff.toByte(), 0xd8.toByte()) +
        segments.fold(byteArrayOf()) { a, b -> a + b } + byteArrayOf(0xff.toByte(), 0xd9.toByte())
    private fun pngChunk(type: String, data: ByteArray) = ByteBuffer.allocate(4).putInt(data.size).array() + type.toByteArray() + data + ByteArray(4)
    private fun png(vararg chunks: ByteArray) = byteArrayOf(0x89.toByte(), 80, 78, 71, 13, 10, 26, 10) + chunks.fold(byteArrayOf()) { a, b -> a + b } + pngChunk("IEND", byteArrayOf())
    private fun webpChunk(type: String, data: ByteArray) = type.toByteArray() + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(data.size).array() + data + ByteArray(data.size % 2)
    private fun webp(vararg chunks: ByteArray): ByteArray {
        val body = "WEBP".toByteArray() + chunks.fold(byteArrayOf()) { a, b -> a + b }
        return "RIFF".toByteArray() + ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(body.size).array() + body
    }
    private fun rejected(bytes: ByteArray, mime: String = "image/jpeg") {
        try { AiAttachmentImageMetadata.orientation(bytes, mime); fail("Expected metadata rejection") }
        catch (e: AiAttachmentFailure) { assertEquals(AiAttachmentProblem.IMAGE, e.problem) }
    }
    @Test fun jpegMarkerFillTemAndUnrelatedApp1RemainCompatible() {
        val xmp = app1("http://ns.adobe.com/xap/1.0/\u0000synthetic".toByteArray(), 3)
        val tem = byteArrayOf(0xff.toByte(), 1)
        assertEquals(6, AiAttachmentImageMetadata.orientation(jpeg(xmp, tem, app1(exif + tiff(6), 4)), "image/jpeg"))
    }
    @Test fun bothTiffByteOrdersPreserveAllEightDirectionsAcrossContainers() {
        for (order in listOf(ByteOrder.LITTLE_ENDIAN, ByteOrder.BIG_ENDIAN)) for (value in 1..8) {
            val raw = tiff(value, order)
            assertEquals(value, AiAttachmentImageMetadata.orientation(jpeg(app1(exif + raw)), "image/jpeg"))
            assertEquals(value, AiAttachmentImageMetadata.orientation(png(pngChunk("eXIf", raw)), "image/png"))
            assertEquals(value, AiAttachmentImageMetadata.orientation(png(pngChunk("EXIF", exif + raw)), "image/png"))
            assertEquals(value, AiAttachmentImageMetadata.orientation(webp(webpChunk("eXIf", raw)), "image/webp"))
            assertEquals(value, AiAttachmentImageMetadata.orientation(webp(webpChunk("JUNK", byteArrayOf(9)), webpChunk("EXIF", exif + raw)), "image/webp"))
        }
    }
    @Test fun nullOrEmptyIfdAndAgreeingJpegDuplicatesDoNotLoseExplicitOrientation() {
        val empty = app1(exif + tiff(null))
        val nullIfd = app1(exif + tiff(null, nullIfd = true))
        val six = app1(exif + tiff(6))
        assertEquals(1, AiAttachmentImageMetadata.orientation(jpeg(nullIfd), "image/jpeg"))
        assertEquals(6, AiAttachmentImageMetadata.orientation(jpeg(empty, six, nullIfd, six), "image/jpeg"))
        rejected(jpeg(six, app1(exif + tiff(8))))
    }
    @Test fun corruptExifNeverFallsBackToAnAssumedDirection() {
        val wrongOffset = tiff(6).apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(4, Int.MAX_VALUE) }
        rejected(jpeg(app1(exif + wrongOffset)))
        rejected(jpeg(app1(exif + byteArrayOf(1, 2))))
        rejected(jpeg(app1(exif + tiff(0))))
        rejected(jpeg(app1(exif + tiff(9))))
        val badType = tiff(6).apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putShort(12, 4) }
        rejected(jpeg(app1(exif + badType)))
    }
    @Test fun malformedHeaderAndOutOfScanStuffingOrRestartAreRejected() {
        for (marker in listOf(0, 0xd0, 0xd7, 0xd8)) rejected(jpeg(byteArrayOf(0xff.toByte(), marker.toByte(), 0, 2)))
        rejected(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xff.toByte()))
        rejected(jpeg(byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0, 1)))
        rejected(jpeg(byteArrayOf(0xff.toByte(), 0xe1.toByte(), 0x7f, 0x7f)))
    }
    @Test fun findingExifNeverSkipsLaterAnimationMarkers() {
        val raw = tiff(6)
        for (type in listOf("acTL", "fcTL", "fdAT"))
            rejected(png(pngChunk("eXIf", raw), pngChunk(type, ByteArray(8))), "image/png")
        for (type in listOf("ANIM", "ANMF")) rejected(webp(webpChunk("EXIF", raw), webpChunk(type, ByteArray(2))), "image/webp")
        rejected(webp(webpChunk("EXIF", raw), webpChunk("VP8X", byteArrayOf(2))), "image/webp")
    }
    @Test fun chunkPayloadBoundsAndExifAllocationLimitStayEnforced() {
        val pngTruncated = png(pngChunk("eXIf", tiff(6))).dropLast(6).toByteArray()
        rejected(pngTruncated, "image/png")
        val webpTruncated = webp(webpChunk("EXIF", tiff(6))).dropLast(2).toByteArray()
        rejected(webpTruncated, "image/webp")
        rejected(png(pngChunk("eXIf", ByteArray(256 * 1024 + 1))), "image/png")
        val manyEntries = tiff(null).apply { ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putShort(8, 1025) }
        rejected(jpeg(app1(exif + manyEntries)))
    }
    private fun subIfd(primary: Int? = null, child: Int = 6, badPointer: Boolean = false): ByteArray {
        val count = if (primary == null) 1 else 2
        val childOffset = 8 + 2 + count * 12 + 4
        val out = ByteBuffer.allocate(childOffset + 18).order(ByteOrder.LITTLE_ENDIAN)
        out.put(byteArrayOf(73, 73)).putShort(42).putInt(8).putShort(count.toShort())
        if (primary != null) out.putShort(0x112).putShort(3).putInt(1).putShort(primary.toShort()).putShort(0)
        out.putShort(0x8769.toShort()).putShort(4).putInt(1).putInt(if (badPointer) Int.MAX_VALUE else childOffset).putInt(0)
        out.putShort(1).putShort(0x112).putShort(3).putInt(1).putShort(child.toShort()).putShort(0).putInt(0)
        return out.array()
    }
    @Test fun boundedExifSubIfdRecoversDirectionInsteadOfSilentlyReturningOne() {
        for (value in 1..8) assertEquals(value,
            AiAttachmentImageMetadata.orientation(jpeg(app1(exif + subIfd(child = value))), "image/jpeg"))
        rejected(jpeg(app1(exif + subIfd(primary = 6, child = 8))))
    }
    @Test fun completePrimaryDirectionSurvivesOnlyIrrelevantBrokenAncillaryPointers() {
        assertEquals(6, AiAttachmentImageMetadata.orientation(jpeg(app1(exif + subIfd(primary = 6, badPointer = true))), "image/jpeg"))
        rejected(jpeg(app1(exif + subIfd(badPointer = true))))
        rejected(jpeg(app1(exif + subIfd(primary = 6, child = 9))))
        val unrelatedGps = subIfd(primary = 6, badPointer = true).apply {
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putShort(22, 0x8825.toShort())
        }
        assertEquals(6, AiAttachmentImageMetadata.orientation(jpeg(app1(exif + unrelatedGps)), "image/jpeg"))
    }
    @Test fun shortPngTailAndRiffSizeMismatchCannotBecomeAbsentOrientation() {
        rejected(png(pngChunk("JUNK", byteArrayOf(1))) + byteArrayOf(1), "image/png")
        val riff = webp(webpChunk("JUNK", byteArrayOf(1))).apply {
            ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN).putInt(4, 4)
        }
        rejected(riff, "image/webp")
        val tail = webp() + byteArrayOf(1, 2, 3)
        ByteBuffer.wrap(tail).order(ByteOrder.LITTLE_ENDIAN).putInt(4, tail.size - 8)
        rejected(tail, "image/webp")
    }
    @Test fun orientationEvidenceAndClosedReasonsNeverCollapseUnknownOrConflictToAbsent() {
        val absent = AiAttachmentImageMetadata.inspect(jpeg(), "image/jpeg")
        assertEquals(AiAttachmentImageMetadata.OrientationState.ABSENT, absent.state)
        assertNull(absent.value); assertNull(absent.reason)
        val known = AiAttachmentImageMetadata.inspect(jpeg(app1(exif + tiff(6))), "image/jpeg")
        assertEquals(AiAttachmentImageMetadata.OrientationState.KNOWN, known.state)
        assertEquals(6, known.value)
        val unknown = AiAttachmentImageMetadata.inspect(jpeg(app1(exif + byteArrayOf(1))), "image/jpeg")
        assertEquals(AiAttachmentImageMetadata.OrientationState.UNREADABLE, unknown.state)
        assertEquals(AiAttachmentImageMetadata.Reason.EXIF_HEADER, unknown.reason)
        val conflict = AiAttachmentImageMetadata.inspect(jpeg(app1(exif + tiff(6)), app1(exif + tiff(8))), "image/jpeg")
        assertEquals(AiAttachmentImageMetadata.OrientationState.CONFLICT, conflict.state)
        assertEquals(AiAttachmentImageMetadata.Reason.EXIF_CONFLICT, conflict.reason)
        try { AiAttachmentImageMetadata.orientation(jpeg(app1(exif + tiff(9))), "image/jpeg"); fail() }
        catch (e: AiAttachmentFailure) { assertEquals(AiAttachmentImageMetadata.Reason.EXIF_ORIENTATION, e.metadataReason) }
    }
    @Test fun closedAnimationAndContainerReasonsRemainDistinct() {
        val animated = AiAttachmentImageMetadata.inspect(png(pngChunk("acTL", ByteArray(8))), "image/png")
        assertEquals(AiAttachmentImageMetadata.Reason.ANIMATED, animated.reason)
        val bad = AiAttachmentImageMetadata.inspect(byteArrayOf(1, 2, 3), "image/jpeg")
        assertEquals(AiAttachmentImageMetadata.Reason.JPEG_MARKER, bad.reason)
    }

    @Test fun metadataDiagnosticUsesOnlyClosedReasonAndSurvivesRecorderFailure() {
        val records = mutableListOf<Map<String, String>>()
        val trace = AiAttachmentTrace { _, fields -> records += fields }
        trace.event(AiAttachmentStage.METADATA)
        val failure = AiAttachmentFailure(AiAttachmentProblem.IMAGE, AiAttachmentImageMetadata.Reason.EXIF_IFD_BOUNDS)
        trace.failed(failure.problem, failure)
        assertEquals(setOf("stage", "problem", "metadataReason", "failureKind"), records.last().keys)
        assertEquals("EXIF_IFD_BOUNDS", records.last()["metadataReason"])
        assertEquals("IMAGE", failure.message)
        AiAttachmentTrace { _, _ -> error("synthetic recorder failure") }.failed(failure.problem, failure)
    }

}
