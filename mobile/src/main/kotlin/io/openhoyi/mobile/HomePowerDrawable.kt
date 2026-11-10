package io.openhoyi.mobile

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable

internal class HomePowerDrawable(private val density: Float, color: Int) : Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=color; style=Paint.Style.STROKE; strokeWidth=1.2f*density; strokeCap=Paint.Cap.ROUND }
    init { setBounds(0,0,(32*density).toInt(),(32*density).toInt()) }
    override fun draw(canvas: Canvas) {
        val cx=bounds.exactCenterX();val cy=bounds.exactCenterY()
        canvas.drawCircle(cx,cy,15*density,paint)
        canvas.drawArc(RectF(cx-6*density,cy-6*density,cx+6*density,cy+6*density),-50f,280f,false,paint)
        canvas.drawLine(cx,cy-8*density,cx,cy-1*density,paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha=alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter=colorFilter }
    @Deprecated("Deprecated in Java") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
