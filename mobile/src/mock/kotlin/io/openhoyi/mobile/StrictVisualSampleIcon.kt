package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Small proposed native line icons; paths and actual Paint stay inspectable, never bitmap labels. */
internal class StrictVisualSampleIcon(context: Context, private val kind: String, private val tint: Int) : View(context) {
    var evidence = JSONObject()
        private set
    override fun onDraw(canvas: Canvas) {
        val scale = width / 24f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint; style = Paint.Style.STROKE; strokeWidth = context.resources.displayMetrics.density }
        canvas.save(); canvas.scale(scale, scale)
        paint.strokeWidth /= scale
        val operations = JSONArray()
        fun line(x: Float, y: Float, x2: Float, y2: Float) {
            canvas.drawLine(x, y, x2, y2, paint); operations.put(JSONObject().put("line24", JSONArray(listOf(x,y,x2,y2))))
        }
        when (kind) {
            "statusDot" -> { paint.style = Paint.Style.FILL; canvas.drawCircle(12f, 12f, 11f, paint); paint.style = Paint.Style.STROKE }
            "temperature" -> { canvas.drawRoundRect(RectF(8f, 2f, 14f, 16f), 3f, 3f, paint); canvas.drawCircle(11f, 18f, 4f, paint); line(11f, 7f, 11f, 18f); line(17f, 6f, 21f, 6f); line(17f, 10f, 20f, 10f) }
            "stages" -> { line(2f, 20f, 8f, 20f); line(8f, 20f, 8f, 14f); line(8f, 14f, 14f, 14f); line(14f, 14f, 14f, 8f); line(14f, 8f, 21f, 8f); line(21f, 8f, 21f, 2f) }
            "coffeeMachine" -> { canvas.drawRoundRect(RectF(3f, 2f, 21f, 22f), 2f, 2f, paint); line(4f, 7f, 20f, 7f); line(8f, 11f, 16f, 11f); line(12f, 11f, 12f, 14f); canvas.drawRect(RectF(8f, 16f, 16f, 20f), paint); canvas.drawCircle(17f, 4.5f, .8f, paint) }
            "info" -> { canvas.drawCircle(12f, 12f, 9f, paint); line(12f, 10f, 12f, 17f); line(12f, 6f, 12f, 7f) }
            "play" -> { canvas.drawPath(Path().apply { moveTo(7f, 3f); lineTo(21f, 12f); lineTo(7f, 21f); close() }, paint) }
            "back" -> { line(20f, 12f, 4f, 12f); line(4f, 12f, 11f, 5f); line(4f, 12f, 11f, 19f) }
            "chevron" -> { line(8f, 4f, 16f, 12f); line(16f, 12f, 8f, 20f) }
            "share" -> { canvas.drawCircle(5f, 12f, 3f, paint); canvas.drawCircle(19f, 5f, 3f, paint); canvas.drawCircle(19f, 19f, 3f, paint); line(8f, 10.5f, 16f, 6.5f); line(8f, 13.5f, 16f, 17.5f) }
            "copy" -> { canvas.drawRoundRect(RectF(8f, 7f, 21f, 22f), 2f, 2f, paint); line(16f, 3f, 3f, 3f); line(3f, 3f, 3f, 17f) }
            "export" -> { line(12f, 2f, 12f, 16f); line(7f, 11f, 12f, 16f); line(12f, 16f, 17f, 11f); line(3f, 15f, 3f, 22f); line(3f, 22f, 21f, 22f); line(21f, 22f, 21f, 15f) }
            "cube" -> { canvas.drawPath(Path().apply { moveTo(12f, 2f); lineTo(22f, 7f); lineTo(22f, 18f); lineTo(12f, 23f); lineTo(2f, 18f); lineTo(2f, 7f); close() }, paint); line(2f, 7f, 12f, 12f); line(22f, 7f, 12f, 12f); line(12f, 12f, 12f, 23f) }
            "coffee" -> { canvas.drawRoundRect(RectF(4f,4f,18f,17f),2f,2f,paint); canvas.drawArc(RectF(16f,6f,23f,14f),-90f,180f,false,paint); line(3f,21f,21f,21f) }
            "curve" -> { line(3f,3f,3f,21f); line(3f,21f,22f,21f); line(4f,18f,9f,11f); line(9f,11f,13f,15f); line(13f,15f,20f,6f) }
            "history" -> { canvas.drawRoundRect(RectF(5f,3f,20f,22f),2f,2f,paint); line(9f,8f,16f,8f); line(9f,12f,16f,12f); line(9f,16f,14f,16f) }
            "bean" -> { canvas.drawOval(RectF(4f,2f,20f,22f),paint); canvas.drawPath(Path().apply { moveTo(14f,3f); cubicTo(5f,10f,19f,13f,10f,21f) },paint) }
            "scale" -> { canvas.drawPath(Path().apply { moveTo(5f,10f); lineTo(19f,10f); lineTo(22f,21f); lineTo(2f,21f); close() },paint); line(6f,5f,18f,5f); line(8f,2f,16f,2f) }
            "settings" -> { canvas.drawCircle(12f,12f,7f,paint); canvas.drawCircle(12f,12f,2.5f,paint); for (index in 0..7) { val angle=Math.PI*index/4; line((12+7*kotlin.math.cos(angle)).toFloat(),(12+7*kotlin.math.sin(angle)).toFloat(),(12+10*kotlin.math.cos(angle)).toFloat(),(12+10*kotlin.math.sin(angle)).toFloat()) } }
        }
        canvas.restore()
        evidence = JSONObject().put("operation","nativeIcon").put("semanticId",tag).put("kind",kind)
            .put("boundsPx",JSONArray(listOf(0,0,width,height))).put("coordinateScaleFrom24",scale)
            .put("colorArgb",String.format(Locale.ROOT,"#%08X",tint)).put("strokeWidthPx",context.resources.displayMetrics.density)
            .put("style","STROKE").put("lineOperations24",operations).put("pathSource","StrictVisualSampleIcon.onDraw").put("status","proposed")
    }
}
