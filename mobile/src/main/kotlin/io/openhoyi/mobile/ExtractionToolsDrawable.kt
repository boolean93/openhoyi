package io.openhoyi.mobile

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable

/** Decorative glyph; the existing tools button owns interaction and status badge. */
internal class ExtractionToolsDrawable(private val density:Float,color:Int):Drawable() {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply {this.color=color;style=Paint.Style.STROKE;strokeWidth=1.3f;strokeJoin=Paint.Join.ROUND;strokeCap=Paint.Cap.ROUND}
    override fun getIntrinsicWidth()=(24*density).toInt()
    override fun getIntrinsicHeight()=intrinsicWidth
    override fun draw(canvas:Canvas) {
        canvas.save();canvas.translate(bounds.left.toFloat(),bounds.top.toFloat());canvas.scale(bounds.width()/24f,bounds.height()/24f)
        val path=Path().apply {moveTo(5f,21f);lineTo(2f,18f);lineTo(12f,8f);cubicTo(10f,4f,14f,1f,18f,3f);lineTo(15f,6f);lineTo(18f,9f);lineTo(21f,6f);cubicTo(23f,10f,19f,14f,15f,12f);close()}
        canvas.drawPath(path,paint);canvas.restore()
    }
    override fun setAlpha(alpha:Int){paint.alpha=alpha}
    override fun setColorFilter(colorFilter:ColorFilter?){paint.colorFilter=colorFilter}
    @Deprecated("Drawable opacity") override fun getOpacity()=PixelFormat.TRANSLUCENT
}
