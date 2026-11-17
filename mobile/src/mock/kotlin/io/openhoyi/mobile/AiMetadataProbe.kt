package io.openhoyi.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.ScrollView
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Mock isolated-process probe: generated corner colours only, no files, keys, session or API. */
internal object AiMetadataProbe {
    private data class Case(val name: String, val source: ByteArray, val direction: Int = 1,
        val reject: AiAttachmentImageMetadata.Reason? = null)
    private val colours = intArrayOf(0xFFE03030.toInt(), 0xFF30C030.toInt(), 0xFF3030E0.toInt(), 0xFFE0C030.toInt())
    private val corners = arrayOf(intArrayOf(0, 1, 2, 3), intArrayOf(1, 0, 3, 2), intArrayOf(3, 2, 1, 0),
        intArrayOf(2, 3, 0, 1), intArrayOf(0, 2, 1, 3), intArrayOf(2, 0, 3, 1), intArrayOf(3, 1, 2, 0), intArrayOf(1, 3, 0, 2))
    fun view(context: Context): android.view.View {
        check(BuildConfig.MOCK_MODE && (context.applicationContext as MobileApplication).offlinePreviewProcessReady)
        val style = TaskProductVisualStyle(context)
        val base = syntheticJpeg()
        val results = mutableListOf<String>()
        var passed = 0
        var total = 0
        try {
            val six = exif(6)
            val cases = (1..8).map { Case("ORIENTATION_$it", jpeg(base, app1(exif(it))), it) } + listOf(
                Case("FF_FILL", jpeg(base, app1(six, 4)), 6),
                Case("NULL_IFD", jpeg(base, app1(exif(null)))),
                Case("REPEATED_EXIF", jpeg(base, app1(six), app1(six)), 6),
                Case("SUB_IFD", jpeg(base, app1(subIfd(false))), 6),
                Case("PRIMARY_BAD_ANCILLARY", jpeg(base, app1(subIfd(true))), 6),
                Case("UNKNOWN_EXIF", jpeg(base, app1("Exif\u0000\u0000".toByteArray() + byteArrayOf(1))),
                    reject = AiAttachmentImageMetadata.Reason.EXIF_HEADER),
                Case("CONFLICT", jpeg(base, app1(six), app1(exif(8))), reject = AiAttachmentImageMetadata.Reason.EXIF_CONFLICT),
            )
            total = cases.size
            cases.forEach { case ->
                var output: ByteArray? = null
                var pixels: Bitmap? = null
                try {
                    output = AndroidAiAttachmentImages().jpeg(case.source, "image/jpeg")
                    check(case.reject == null)
                    pixels = BitmapFactory.decodeByteArray(output, 0, output.size) ?: error("Synthetic decode")
                    val swapped = case.direction in 5..8
                    check(pixels.width == if (swapped) 48 else 80)
                    check(pixels.height == if (swapped) 80 else 48)
                    val actual = intArrayOf(pixels.getPixel(pixels.width / 4, pixels.height / 4),
                        pixels.getPixel(pixels.width * 3 / 4, pixels.height / 4),
                        pixels.getPixel(pixels.width / 4, pixels.height * 3 / 4),
                        pixels.getPixel(pixels.width * 3 / 4, pixels.height * 3 / 4)).map(::nearestColour)
                    check(actual == corners[case.direction - 1].toList())
                    check(AiAttachmentImageMetadata.orientation(output, "image/jpeg") == 1)
                    check(!output.asList().windowed(6).any { it == "Exif\u0000\u0000".toByteArray().asList() })
                    passed++
                    results += "${case.name}: PASS ${pixels.width}×${pixels.height} PIXELS_OK EXIF_STRIPPED"
                } catch (failure: AiAttachmentFailure) {
                    if (case.reject != null && failure.metadataReason == case.reject) {
                        passed++; results += "${case.name}: PASS REJECTED ${case.reject.name}"
                    } else results += "${case.name}: FAIL"
                } catch (_: Exception) { results += "${case.name}: FAIL" }
                finally { pixels?.recycle(); case.source.fill(0); output?.fill(0) }
            }
        } finally { base.fill(0) }
        val content = style.column("ai/mock/metadata-probe").apply {
            setPadding(style.dp(16), style.dp(24), style.dp(16), style.dp(24))
            setBackgroundColor(style.page)
            addView(style.text("OFFLINE SYNTHETIC METADATA PROBE $passed/$total", TaskProductVisualStyle.section))
            results.forEach { addView(style.text(it, TaskProductVisualStyle.body)) }
        }
        return ScrollView(context).apply { addView(content) }
    }
    private fun nearestColour(pixel: Int): Int = colours.indices.minBy { index ->
        listOf(0, 8, 16).sumOf { shift ->
            val delta = ((pixel shr shift) and 255) - ((colours[index] shr shift) and 255)
            delta * delta
        }
    }
    private fun syntheticJpeg(): ByteArray {
        val bitmap = Bitmap.createBitmap(80, 48, Bitmap.Config.ARGB_8888)
        val output = AiAttachmentBuffer()
        try {
            val canvas = Canvas(bitmap)
            val paint = Paint()
            colours.indices.forEach { i ->
                paint.color = colours[i]
                val left = if (i % 2 == 0) 0f else 40f
                val top = if (i < 2) 0f else 24f
                canvas.drawRect(left, top, left + 40, top + 24, paint)
            }
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output))
            return output.toByteArray()
        } finally { bitmap.recycle(); output.wipe() }
    }
    private fun exif(value: Int?): ByteArray {
        val tiff = ByteBuffer.allocate(if (value == null) 8 else 26).order(ByteOrder.LITTLE_ENDIAN)
        tiff.put(byteArrayOf(73, 73)).putShort(42).putInt(if (value == null) 0 else 8)
        if (value != null) tiff.putShort(1).putShort(0x112).putShort(3).putInt(1).putShort(value.toShort()).putShort(0).putInt(0)
        return "Exif\u0000\u0000".toByteArray() + tiff.array()
    }
    private fun subIfd(primary: Boolean): ByteArray {
        val count = if (primary) 2 else 1
        val childOffset = 8 + 2 + count * 12 + 4
        val out = ByteBuffer.allocate(childOffset + 18).order(ByteOrder.LITTLE_ENDIAN)
        out.put(byteArrayOf(73, 73)).putShort(42).putInt(8).putShort(count.toShort())
        if (primary) out.putShort(0x112).putShort(3).putInt(1).putShort(6).putShort(0)
        out.putShort(0x8769.toShort()).putShort(4).putInt(1).putInt(if (primary) Int.MAX_VALUE else childOffset).putInt(0)
        out.putShort(1).putShort(0x112).putShort(3).putInt(1).putShort(6).putShort(0).putInt(0)
        return "Exif\u0000\u0000".toByteArray() + out.array()
    }
    private fun app1(payload: ByteArray, fill: Int = 1): ByteArray =
        ByteArray(fill) { 0xff.toByte() } + byteArrayOf(0xe1.toByte(), ((payload.size + 2) shr 8).toByte(),
            (payload.size + 2).toByte()) + payload
    private fun jpeg(base: ByteArray, vararg segments: ByteArray): ByteArray =
        base.copyOfRange(0, 2) + segments.fold(byteArrayOf()) { a, b -> a + b } + base.copyOfRange(2, base.size)
}
