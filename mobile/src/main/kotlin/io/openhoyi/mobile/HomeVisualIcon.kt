package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** M1's explicit native glyphs. Decorative only; owning action containers retain their callbacks. */
internal class HomeVisualIcon(context: Context, private val kind: String, private val tint: Int) : View(context) {
    var productDrawEvidence = JSONObject()
        private set
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        productDrawEvidence = drawGlyph(context, canvas, width, height, kind, tint, tag)
    }
    companion object {
        fun buttonIcon(button: android.widget.Button, kind: String, atEnd: Boolean = false) {
            if (atEnd) {
                val gap = (8 * button.resources.displayMetrics.density).toInt()
                val size = (24 * button.resources.displayMetrics.density).toInt()
                button.gravity = android.view.Gravity.CENTER
                button.compoundDrawablePadding = gap
                button.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null,
                    drawable(button.context, kind, button.currentTextColor,
                        currentTint = { button.currentTextColor }, horizontalOffset = {
                            val textLayout = button.layout
                            if (textLayout == null || textLayout.lineCount == 0) 0f else {
                                // Mirror the existing start-placement contract using real Layout edges.
                                if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                                    val textLeft = button.compoundPaddingLeft + (0 until textLayout.lineCount).minOf { textLayout.getLineLeft(it) }
                                    textLeft - gap - size - button.paddingLeft
                                } else {
                                    val textRight = button.compoundPaddingLeft + (0 until textLayout.lineCount).maxOf { textLayout.getLineRight(it) }
                                    textRight + gap - (button.width - button.paddingRight - size)
                                }
                            }
                        }, mirrorHorizontally = {
                            (kind == "back" && button.layoutDirection != View.LAYOUT_DIRECTION_RTL) ||
                                (kind == "chevron" && button.layoutDirection == View.LAYOUT_DIRECTION_RTL)
                        }), null)
                return
            }
            val gap = (8 * button.resources.displayMetrics.density).toInt()
            val size = (24 * button.resources.displayMetrics.density).toInt()
            button.gravity = android.view.Gravity.CENTER
            button.compoundDrawablePadding = gap
            button.setCompoundDrawablesRelativeWithIntrinsicBounds(drawable(button.context, kind, button.currentTextColor,
                currentTint = { button.currentTextColor }, horizontalOffset = {
                    val textLayout = button.layout
                    if (textLayout == null || textLayout.lineCount == 0) 0f else {
                        // TextView.onDraw places start drawables at physical padding,
                        // but centres text inside compoundPaddingLeft/Right. Use the
                        // actual Layout line edges, not paint/character estimates,
                        // to move only the glyph next to that centred text block.
                        if (button.layoutDirection == View.LAYOUT_DIRECTION_RTL) {
                            val textRight = button.compoundPaddingLeft + (0 until textLayout.lineCount).maxOf { textLayout.getLineRight(it) }
                            textRight + gap - (button.width - button.paddingRight - size)
                        } else {
                            val textLeft = button.compoundPaddingLeft + (0 until textLayout.lineCount).minOf { textLayout.getLineLeft(it) }
                            textLeft - gap - size - button.paddingLeft
                        }
                    }
                }), null, null, null)
        }
        fun drawable(context: Context, kind: String, tint: Int, currentTint: (() -> Int)? = null,
            horizontalOffset: (() -> Float)? = null, mirrorHorizontally: (() -> Boolean)? = null): Drawable = object : Drawable() {
            private val size = (24 * context.resources.displayMetrics.density).toInt()
            override fun getIntrinsicWidth() = size
            override fun getIntrinsicHeight() = size
            override fun draw(canvas: Canvas) {
                canvas.save(); canvas.translate(bounds.left + (horizontalOffset?.invoke() ?: 0f), bounds.top.toFloat())
                if (mirrorHorizontally?.invoke() == true) { canvas.translate(bounds.width().toFloat(), 0f); canvas.scale(-1f, 1f) }
                drawGlyph(context, canvas, bounds.width(), bounds.height(), kind, currentTint?.invoke() ?: tint, null)
                canvas.restore()
            }
            override fun isStateful() = currentTint != null
            override fun onStateChange(state: IntArray): Boolean { invalidateSelf(); return currentTint != null }
            override fun setAlpha(alpha: Int) = Unit
            override fun setColorFilter(filter: android.graphics.ColorFilter?) = Unit
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        private fun drawGlyph(context: Context, canvas: Canvas, width: Int, height: Int,
            kind: String, tint: Int, id: Any?): JSONObject {
        val scale = width / 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = context.resources.displayMetrics.density }
        canvas.save(); canvas.scale(scale, scale)
        paint.strokeWidth /= scale
        val operations = JSONArray()
        fun line(x: Float, y: Float, x2: Float, y2: Float) {
            canvas.drawLine(x, y, x2, y2, paint); operations.put(JSONObject().put("line24", JSONArray(listOf(x,y,x2,y2))))
        }
        when (kind) {
            "check" -> { line(5f,12f,10f,17f); line(10f,17f,20f,6f) }
            "new" -> { canvas.drawCircle(12f,12f,9f,paint); line(7f,12f,17f,12f); line(12f,7f,12f,17f) }
            "edit" -> { canvas.drawPath(Path().apply { moveTo(4f,16f); lineTo(16f,4f); lineTo(20f,8f); lineTo(8f,20f); lineTo(3f,21f); close() },paint); line(13f,7f,17f,11f) }
            "manage" -> { line(3f,6f,21f,6f); line(3f,12f,21f,12f); line(3f,18f,21f,18f)
                canvas.drawCircle(9f,6f,2f,paint); canvas.drawCircle(16f,12f,2f,paint); canvas.drawCircle(7f,18f,2f,paint) }
            "moon" -> canvas.drawPath(Path().apply {
                moveTo(19f, 16f)
                cubicTo(8f, 19f, 4f, 10f, 10f, 3f)
                cubicTo(1f, 5f, 1f, 17f, 9f, 21f)
                cubicTo(13f, 23f, 18f, 20f, 19f, 16f)
                close()
            }, paint)
            "disconnect" -> {
                // Two open chain halves, separated at the break.
                canvas.save(); canvas.rotate(-45f, 12f, 12f)
                canvas.drawArc(RectF(7f, 1f, 17f, 12f), 0f, -270f, false, paint)
                canvas.drawArc(RectF(7f, 12f, 17f, 23f), 180f, -270f, false, paint)
                canvas.restore()
                line(3f, 8f, 6f, 8f); line(18f, 16f, 21f, 16f)
            }
            "retry" -> { canvas.drawArc(RectF(4f,4f,20f,20f),-50f,300f,false,paint)
                line(18f,3f,20f,8f); line(20f,8f,15f,8f) }
            "select" -> { canvas.drawRoundRect(RectF(3f,3f,21f,21f),2f,2f,paint)
                line(7f,8f,17f,8f); line(7f,12f,17f,12f); line(7f,16f,14f,16f) }
            "progress" -> { canvas.drawArc(RectF(4f,4f,20f,20f),-90f,230f,false,paint)
                line(19f,14f,20f,19f); line(20f,19f,15f,19f) }
            "more" -> { canvas.drawCircle(5f,12f,1.5f,paint); canvas.drawCircle(12f,12f,1.5f,paint); canvas.drawCircle(19f,12f,1.5f,paint) }
            "search" -> { canvas.drawCircle(10f,10f,7f,paint); line(15f,15f,22f,22f) }
            "statusDot" -> { paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 12f, 11f, paint) }
            "temperature" -> { canvas.drawRoundRect(RectF(8f, 2f, 14f, 16f), 3f, 3f, paint); canvas.drawCircle(11f, 18f, 4f, paint); line(11f, 7f, 11f, 18f); line(17f, 6f, 21f, 6f); line(17f, 10f, 20f, 10f) }
            "stages" -> { line(2f, 20f, 8f, 20f); line(8f, 20f, 8f, 14f); line(8f, 14f, 14f, 14f); line(14f, 14f, 14f, 8f); line(14f, 8f, 21f, 8f); line(21f, 8f, 21f, 2f) }
            "coffeeMachine" -> { canvas.drawRoundRect(RectF(3f, 2f, 21f, 22f), 2f, 2f, paint); line(4f, 7f, 20f, 7f); line(8f, 11f, 16f, 11f); line(12f, 11f, 12f, 14f); canvas.drawRect(RectF(8f, 16f, 16f, 20f), paint); canvas.drawCircle(17f, 4.5f, .8f, paint) }
            "info" -> { canvas.drawCircle(12f, 12f, 9f, paint); line(12f, 10f, 12f, 17f); line(12f, 6f, 12f, 7f) }
            "share" -> { canvas.drawCircle(5f,12f,3f,paint); canvas.drawCircle(19f,5f,3f,paint); canvas.drawCircle(19f,19f,3f,paint); line(8f,10.5f,16f,6.5f); line(8f,13.5f,16f,17.5f) }
            "copy" -> { canvas.drawRoundRect(RectF(8f,3f,21f,17f),2f,2f,paint); canvas.drawRoundRect(RectF(3f,8f,16f,22f),2f,2f,paint) }
            "export" -> { line(12f,2f,12f,15f); line(7f,10f,12f,15f); line(12f,15f,17f,10f); line(3f,16f,3f,22f); line(3f,22f,21f,22f); line(21f,22f,21f,16f) }
            "back" -> { line(12f,4f,4f,12f); line(4f,12f,12f,20f); line(4f,12f,22f,12f) }
            "chevron" -> { line(8f, 4f, 16f, 12f); line(16f, 12f, 8f, 20f) }
            "coffee" -> { canvas.drawRoundRect(RectF(4f,4f,18f,17f),2f,2f,paint); canvas.drawArc(RectF(16f,6f,23f,14f),-90f,180f,false,paint); line(3f,21f,21f,21f) }
            "curve" -> { line(3f,3f,3f,21f); line(3f,21f,22f,21f); line(4f,18f,9f,11f); line(9f,11f,13f,15f); line(13f,15f,20f,6f) }
            "history" -> { canvas.drawRoundRect(RectF(5f,3f,20f,22f),2f,2f,paint); line(9f,8f,16f,8f); line(9f,12f,16f,12f); line(9f,16f,14f,16f) }
            "bean" -> { canvas.drawOval(RectF(4f,2f,20f,22f),paint); canvas.drawPath(Path().apply { moveTo(14f,3f); cubicTo(5f,10f,19f,13f,10f,21f) },paint) }
            "scale" -> { canvas.drawPath(Path().apply { moveTo(5f,10f); lineTo(19f,10f); lineTo(22f,21f); lineTo(2f,21f); close() },paint); line(6f,5f,18f,5f); line(8f,2f,16f,2f) }
            "settings" -> { canvas.drawCircle(12f,12f,7f,paint); canvas.drawCircle(12f,12f,2.5f,paint); for (index in 0..7) { val angle=Math.PI*index/4; line((12+7*kotlin.math.cos(angle)).toFloat(),(12+7*kotlin.math.sin(angle)).toFloat(),(12+10*kotlin.math.cos(angle)).toFloat(),(12+10*kotlin.math.sin(angle)).toFloat()) } }
        }
        canvas.restore()
        val evidence = JSONObject().put("operation","nativeIcon").put("semanticId",id).put("kind",kind)
            .put("boundsPx",JSONArray(listOf(0,0,width,height))).put("coordinateScaleFrom24",scale)
            .put("colorArgb",String.format(Locale.ROOT,"#%08X",paint.color))
            .put("strokeWidthPx",if (paint.style == Paint.Style.FILL) JSONObject.NULL else paint.strokeWidth * scale)
            .put("style",paint.style.name).put("lineOperations24",operations).put("pathSource","HomeVisualIcon.drawGlyph").put("status","actual_production_draw")
        if (kind == "statusDot") evidence.put("circle", JSONObject()
            .put("centerPx", JSONArray(listOf(12f * scale, 12f * scale)))
            .put("radiusPx", 11f * scale).put("operation", "fillCircle"))
        return evidence
        }
    }
}

/** Display-only extraction shared by Home identity, recent rows and compact metrics. */
internal data class HomeRecipePresentation(val temperatureC: Int?, val weightHundredthsGram: Int?, val stages: Int?, val pressureMode: Boolean?) {
    fun mode(context: Context) = context.getString(when (pressureMode) {
        true -> R.string.home_visual_pressure_mode
        false -> R.string.home_visual_flow_mode
        null -> R.string.home_visual_unknown_mode
    })
    fun description(context: Context) = context.getString(R.string.home_visual_stage_mode, stages?.toString() ?: "—", mode(context))
    fun compact(context: Context) = listOf(temperatureC?.let { "$it °C" } ?: "—",
        weightHundredthsGram?.let { if (it == 0) context.getString(R.string.curve_details_no_weight) else "${CurveDraftNumber.format(it, 2)} g" } ?: "—",
        context.getString(R.string.home_visual_stage_count, stages?.toString() ?: "—")).joinToString(" · ")
    companion object {
        fun from(item: CurveLibraryItem): HomeRecipePresentation {
            val draft = item.customDocument; val factory = item.factoryCurve; val captured = item.controlProfile
            return HomeRecipePresentation(draft?.temperatureC ?: factory?.temperatureC ?: captured?.temperatureC,
                draft?.targetHundredthsGram ?: factory?.weightTenthsGram?.times(10) ?: captured?.targetHundredthsGram,
                draft?.stages?.size ?: factory?.segmentCount ?: captured?.parameters?.segmentCount,
                draft?.let { it.controlMode == CustomCurveDocument.ControlMode.PRESSURE }
                    ?: factory?.let { !it.variableFlowLogic }
                    ?: captured?.parameters?.let { !it.variableFlowLogic && !it.firstSegmentFlowMode })
        }
    }
}
