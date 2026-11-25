package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import kotlin.math.roundToInt

/** Local, wide Settings material; no view elevation or out-of-bounds painting. */
internal fun settingsRaisedGroup(context: Context, style: TaskProductVisualStyle, horizontalInsetDp: Int = 4,
    topInsetDp: Int = 2): Drawable =
    SettingsRaisedGroup(context, style.surface, horizontalInsetDp, topInsetDp)

internal fun View.markSettingsGroupMaterial(style: TaskProductVisualStyle, raiseOnWide: Boolean = true) {
    val outline = style.shape(Color.TRANSPARENT, outline = true, radius = 14)
    val original = background
    setTag(R.id.settings_group_material, GroupMaterial(original, outline) {
        if (raiseOnWide) settingsRaisedGroup(context, style) else original
    })
}

internal fun View.applySettingsGroupMaterials(wide: Boolean, photo: Boolean) {
    (getTag(R.id.settings_group_material) as? GroupMaterial)?.let { binding ->
        if (binding.wide != wide || binding.photo != photo) {
            binding.wide = wide
            binding.photo = photo
            background = when {
                photo -> binding.outline
                wide -> binding.raised()
                else -> binding.narrow
            }
        }
    }
    if (this is SettingsAppearanceSurface) showParentPhotoSurface(photo)
    if (this is ViewGroup) {
        for (index in 0 until childCount) getChildAt(index).applySettingsGroupMaterials(wide, photo)
    }
}

private class GroupMaterial(val narrow: Drawable?, val outline: Drawable, val raised: () -> Drawable?) {
    var wide = false
    var photo = false
}

/** Stretch the middle of a small software-rasterized alpha mask, leaving blurred corners intact. */
private class SettingsRaisedGroup(context: Context, fill: Int, private val horizontalInsetDp: Int,
    private val topInsetDp: Int) : Drawable() {
    private val density = context.resources.displayMetrics.density
    private val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    private val surface = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = fill }
    private val shadow = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { color = 0xff202d3b.toInt() }
    private val parts = IntArray(4)
    private val rows = IntArray(4)
    private val source = Rect()
    private val target = Rect()

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty) return
        if (!dark) {
            val sample = masks.get(density, horizontalInsetDp, topInsetDp)
            val sliceX = minOf(sample.edge, bounds.width() / 2)
            val sliceY = minOf(sample.edge, bounds.height() / 2)
            parts[0] = bounds.left; parts[1] = bounds.left + sliceX
            parts[2] = bounds.right - sliceX; parts[3] = bounds.right
            rows[0] = bounds.top; rows[1] = bounds.top + sliceY
            rows[2] = bounds.bottom - sliceY; rows[3] = bounds.bottom
            for (y in 0..2) for (x in 0..2) {
                source.set(sample.edges[x], sample.edges[y], sample.edges[x + 1], sample.edges[y + 1])
                target.set(parts[x], rows[y], parts[x + 1], rows[y + 1])
                if (!target.isEmpty) canvas.drawBitmap(sample.bitmap, source, target, shadow)
            }
        }
        val x = horizontalInsetDp * density
        val top = topInsetDp * density
        val bottom = ((if (dark) 3 else 6) + 2 - topInsetDp) * density
        val radius = 16 * density
        canvas.drawRoundRect(bounds.left + x, bounds.top + top, bounds.right - x, bounds.bottom - bottom,
            radius, radius, surface)
    }

    override fun setAlpha(alpha: Int) { surface.alpha = alpha; shadow.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { surface.colorFilter = colorFilter }
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.TRANSLUCENT

    private data class ShadowMask(val bitmap: Bitmap, val edge: Int, val edges: IntArray)
    private object masks {
        private val cache = android.util.LruCache<Int, ShadowMask>(6)
        fun get(density: Float, insetDp: Int, topInsetDp: Int): ShadowMask = synchronized(cache) {
            val key = 31 * (density * 160).roundToInt() + 5 * insetDp + topInsetDp
            cache.get(key) ?: run {
                val edge = (22f * density).roundToInt().coerceAtLeast(1)
                val size = edge * 2 + 2
                val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ALPHA_8).apply { this.density = Bitmap.DENSITY_NONE }
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(36, 255, 255, 255)
                    maskFilter = BlurMaskFilter(3f * density, BlurMaskFilter.Blur.NORMAL)
                }
                val radius = 16 * density
                val drop = 2f * density
                Canvas(bitmap).drawRoundRect(insetDp * density, topInsetDp * density + drop, size - insetDp * density,
                    size - (8 - topInsetDp) * density + drop, radius, radius, paint)
                ShadowMask(bitmap, edge, intArrayOf(0, edge, size - edge, size)).also { cache.put(key, it) }
            }
        }
    }
}
