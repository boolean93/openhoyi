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
