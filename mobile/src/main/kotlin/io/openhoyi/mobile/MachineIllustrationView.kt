package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/** Lightweight decorative drawing; it never implies device readiness. */
internal class MachineIllustrationView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val save = canvas.save()
        canvas.translate(width * .17f, height * .08f)
        canvas.scale(width * .66f / 300f, height * .84f / 240f)
        paint.color = context.getColor(R.color.mobile_muted)
        canvas.drawRoundRect(RectF(25f, 24f, 275f, 203f), 14f, 14f, paint)
        canvas.drawLine(25f, 80f, 275f, 80f, paint)
        canvas.drawRoundRect(RectF(8f, 203f, 292f, 223f), 6f, 6f, paint)
        canvas.drawRoundRect(RectF(55f, 44f, 155f, 65f), 5f, 5f, paint)
        canvas.drawCircle(233f, 54f, 13f, paint)
        canvas.drawLine(91f, 80f, 91f, 126f, paint)
        canvas.drawLine(72f, 126f, 128f, 126f, paint)
        canvas.drawLine(110f, 126f, 110f, 146f, paint)
        canvas.drawLine(229f, 80f, 242f, 145f, paint)
        canvas.drawLine(242f, 145f, 244f, 170f, paint)
        paint.color = context.getColor(R.color.mobile_accent)
        canvas.drawRoundRect(RectF(105f, 155f, 170f, 200f), 7f, 7f, paint)
        canvas.drawArc(RectF(161f, 160f, 190f, 187f), -95f, 260f, false, paint)
        canvas.restoreToCount(save)
    }
}
