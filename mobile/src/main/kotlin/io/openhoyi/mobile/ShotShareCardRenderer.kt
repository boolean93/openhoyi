package io.openhoyi.mobile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws a fixed 4:5 image export, independently of display density, theme and font size. */
internal object ShotShareCardRenderer {
    const val WIDTH = 1120
    const val HEIGHT = 1400
    private const val PAPER_WIDTH = 1000
    private const val PAPER_HEIGHT = 650
    private const val BACKGROUND = "shot-share/f-tableside.png"
    // Small OFL Latin subset of the app's Noto Sans SC typeface; same licence in assets/licenses.
    private const val NUMERALS = "shot-share/f-numerals.ttf"

    data class Sample(val seconds: Double, val value: Double)

    data class Model(
        val title: String,
        val curveName: String?,
        val durationSeconds: Double?,
        val outputGrams: Double?,
        val doseGrams: Double?,
        val samples: List<Sample>,
        val footer: String = "",
    )

    fun render(context: Context, model: Model): Bitmap {
        val plate = context.assets.open(BACKGROUND).use {
            checkNotNull(BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false }))
        }
        val output = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val paper = Bitmap.createBitmap(PAPER_WIDTH, PAPER_HEIGHT, Bitmap.Config.ARGB_8888)
        val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        try {
            check(plate.width == WIDTH && plate.height == HEIGHT)
            val canvas = Canvas(output)
            canvas.drawBitmap(plate, null, RectF(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat()), bitmapPaint)
            Ink(context, Canvas(paper)).draw(model)
            val projection = Matrix()
            check(projection.setPolyToPoly(
                floatArrayOf(0f, 0f, 1000f, 0f, 1000f, 650f, 0f, 650f), 0,
                floatArrayOf(130f, 423.33f, 1080f, 558.33f, 1005f, 1200f, 6.67f, 1071.67f), 0,
                4,
            ))
            canvas.drawBitmap(paper, projection, bitmapPaint)
            return output
        } catch (failure: Throwable) {
            output.recycle()
            throw failure
        } finally {
            paper.recycle()
            plate.recycle()
        }
    }

    private class Ink(private val context: Context, private val canvas: Canvas) {
        private val locale = context.resources.configuration.locales[0]
        private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        private val shape = Paint(Paint.ANTI_ALIAS_FLAG)
        private val titleFace = Typeface.create("serif", Typeface.BOLD)
        private val labelFace = TaskTypography.face(context, 500)
        private val badgeFace = TaskTypography.face(context, 550)
        private val footerFace = TaskTypography.face(context, 400)
        private val numericFamily = Typeface.Builder(context.assets, NUMERALS)
        private val numberFace = checkNotNull(numericFamily.setFontVariationSettings("'wght' 700").setWeight(700).build())
        private val unitFace = checkNotNull(Typeface.Builder(context.assets, NUMERALS)
            .setFontVariationSettings("'wght' 550").setWeight(550).build())
        private val brandFace = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 700, false)
            else Typeface.DEFAULT_BOLD

        fun draw(model: Model) {
            fitted(model.title.trim(), 94f, 150f, 790f, 82, 58, titleFace, 0xf03b190e.toInt())
            model.curveName?.trim()?.takeIf(String::isNotEmpty)?.let(::badge)
            chart(model.samples)

            val seconds = model.durationSeconds?.takeIf(::validMetric)
            val output = model.outputGrams?.takeIf(::validMetric)
            val dose = model.doseGrams?.takeIf { validMetric(it) && it > 0 }
            val ratio = if (output != null && output > 0 && dose != null) (output / dose).takeIf(::validMetric) else null
            metric(103f, context.getString(R.string.shot_share_card_ink_time), seconds?.let(::decimal), "s", 357f, 445f, .67f)
            metric(411f, context.getString(R.string.shot_share_card_ink_yield), output?.let(::decimal), "g", 350f, 440f, .72f)
            metric(734f, context.getString(R.string.shot_share_card_ink_ratio), ratio?.let { "1:${decimal(it)}" }, "", 346f, 435f, .67f)
            line(355f, 321f, 355f, 448f, 1.6f)
            line(678f, 316f, 678f, 445f, 1.6f)
            fitted("OpenHOYI", 85f, 578f, 205f, 39, 32, brandFace, INK)
            line(316f, 565f, 773f, 559f, 1.4f)
            fitted(model.footer.trim(), 805f, 566f, 145f, 28, 20, footerFace, FOOT, 1.55f)
        }

        private fun decimal(value: Double) = String.format(Locale.ROOT, "%.1f", value)

        private fun validMetric(value: Double) = value.isFinite() && value in 0.0..999.9

        private fun badge(name: String) {
            val width = 317f
            var size = 49
            while (size > 31 && measure(name, size, badgeFace, 1.15f) > width) size--
            val lines = if (measure(name, size, badgeFace, 1.15f) > width) {
                size = 29
                val split = breakpoints(name).takeWhile {
                    measure(name.substring(0, it), size, badgeFace, .8f) <= width
                }.lastOrNull() ?: 0
                listOf(name.substring(0, split), name.substring(split).trimStart())
            } else listOf(name)
            val spacing = if (lines.size == 1) 1.15f else .8f
            val inkWidth = lines.maxOf { min(width, measure(it, size, badgeFace, spacing)) }
            shape.style = Paint.Style.FILL
            shape.color = RUST
            canvas.drawRoundRect(91f, 195f, 91f + maxOf(72f, inkWidth + 39f), 259f, 14f, 14f, shape)
            lines.forEachIndexed { index, line ->
                val baseline = if (lines.size == 1) 239f else 219f + 30f * index
                fitted(line, 112f, baseline, width, size, size, badgeFace, CREAM, spacing)
            }
        }

        private fun metric(x: Float, label: String, value: String?, unit: String, labelY: Float,
                           valueY: Float, unitScale: Float) {
            fitted("$label /", x, labelY, 205f, 39, 32, labelFace, INK, .45f)
            if (value == null) {
                fitted("—", x, valueY, 210f, 76, 54, numberFace, INK)
                return
            }
            val suffix = if (unit.isEmpty()) "" else " $unit"
            var size = 77
            while (size > 45 && measure(value, size, numberFace) +
                measure(suffix, (size * unitScale).roundToInt(), unitFace) > 218f) size--
            val valueWidth = paint(value, x, valueY, size, numberFace, INK)
            paint(suffix, x + valueWidth - 2f, valueY, (size * unitScale).roundToInt(), unitFace, INK)
        }

        private fun line(x1: Float, y1: Float, x2: Float, y2: Float, stroke: Float) {
            shape.color = LINE
            shape.style = Paint.Style.STROKE
            shape.strokeCap = Paint.Cap.BUTT
            shape.strokeWidth = stroke
            canvas.drawLine(x1, y1, x2, y2, shape)
        }

        /** Uses monotonic time, with no invented data or in-segment extrema. */
        private fun chart(samples: List<Sample>) {
            if (samples.size < 2 || samples.any { !it.seconds.isFinite() || it.seconds < 0 || !it.value.isFinite() } ||
                samples.indices.drop(1).any { samples[it].seconds <= samples[it - 1].seconds }) return
            val first = samples.first().seconds
            val span = samples.last().seconds - first
            val min = samples.minOf(Sample::value)
            val max = samples.maxOf(Sample::value)
            val range = max - min
            if (!span.isFinite() || span <= 0 || !range.isFinite()) return
            // Preserve gaps in telemetry instead of implying that pressure was observed throughout.
            val cadence = samples.zipWithNext { a, b -> b.seconds - a.seconds }.sorted()
            val maxGap = maxOf(1.5, cadence[cadence.size / 2] * 3)
            fun splitAfter(i: Int) = samples[i + 1].seconds - samples[i].seconds > maxGap
            val x = FloatArray(samples.size) { index -> (464 + (samples[index].seconds - first) / span * 426).toFloat() }
            val y = FloatArray(samples.size) { index ->
                (145 + if (range == 0.0) 38.0 else (1 - (samples[index].value - min) / range) * 76).toFloat()
            }
            val path = Path().apply {
                moveTo(x[0], y[0])
                for (i in 0 until samples.lastIndex) {
                    val j = i + 1
                    if (splitAfter(i)) {
                        moveTo(x[j], y[j])
                        continue
                    }
                    val prev = if (i > 0 && !splitAfter(i - 1)) i - 1 else i
                    val next = if (j < samples.lastIndex && !splitAfter(j)) j + 1 else j
                    val dx = (x[j] - x[i]) / 3
                    val lower = minOf(y[i], y[j])
                    val upper = maxOf(y[i], y[j])
                    fun tangent(a: Int, b: Int): Float =
                        if (x[a] == x[b]) 0f else (y[b] - y[a]) / (x[b] - x[a])
                    cubicTo(
                        x[i] + dx, (y[i] + dx * tangent(prev, j)).coerceIn(lower, upper),
                        x[j] - dx, (y[j] - dx * tangent(i, next)).coerceIn(lower, upper),
                        x[j], y[j],
                    )
                }
            }
            shape.color = RUST
            shape.style = Paint.Style.STROKE
            shape.strokeWidth = 4f
            shape.strokeCap = Paint.Cap.ROUND
            shape.strokeJoin = Paint.Join.ROUND
            canvas.drawPath(path, shape)
        }

        private fun breakpoints(value: String): List<Int> {
            val iterator = BreakIterator.getCharacterInstance(locale).apply { setText(value) }
            return buildList {
                var end = iterator.next()
                while (end != BreakIterator.DONE) {
                    add(end)
                    end = iterator.next()
                }
            }
        }

        private fun fitted(value: String, x: Float, y: Float, width: Float, initialSize: Int, minSize: Int,
                           face: Typeface, color: Int, tracking: Float = 0f) {
            if (value.isEmpty()) return
            var size = initialSize
            while (size > minSize && measure(value, size, face, tracking) > width) size--
            val display = if (measure(value, size, face, tracking) <= width) value else {
                val last = breakpoints(value).takeWhile {
                    measure(value.substring(0, it) + "…", size, face, tracking) <= width
                }.lastOrNull() ?: 0
                value.substring(0, last) + "…"
            }
            paint(display, x, y, size, face, color, tracking)
        }

        private fun paint(value: String, x: Float, y: Float, size: Int, face: Typeface, color: Int,
                          tracking: Float = 0f): Float {
            val width = measure(value, size, face, tracking)
            text.color = color
            canvas.drawText(value, x, y, text)
            return width
        }

        // Approximate the reference's small tracking with scale whilst preserving shaped CJK/Latin runs.
        private fun measure(value: String, size: Int, face: Typeface, tracking: Float = 0f): Float {
            text.typeface = face
            text.textSize = size.toFloat()
            text.textScaleX = 1f
            val width = text.measureText(value)
            if (tracking != 0f && width > 0f) {
                text.textScaleX = 1f + tracking * (value.codePointCount(0, value.length) - 1).coerceAtLeast(0) / width
            }
            return text.measureText(value)
        }
    }

    private const val INK = 0xff3b190e.toInt()
    private const val RUST = 0xffa24920.toInt()
    private const val LINE = 0xff9d6545.toInt()
    private const val CREAM = 0xfffff0d9.toInt()
    private const val FOOT = 0xff8a6a53.toInt()
}
