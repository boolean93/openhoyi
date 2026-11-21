package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView

/** The shortcut strip owns presentation only; callers retain all recipe and device actions. */
internal class HomePresetGrid(
    context: Context,
    private val style: TaskProductVisualStyle,
    private val portrait: Boolean,
    private val short: Boolean,
    private val onSlotClick: (Int) -> Unit,
    private val onNameLongClick: ((String) -> Unit)? = null,
    onLastClick: () -> Unit,
) : LinearLayout(context) {
    data class Visual(val button: Button, val name: TextView, val sparkline: CurveLibrarySparklineView, val label: TextView)
    private val dark = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
    private val labelInk = if (dark) style.ink else 0xff101938.toInt()
    private val surface = wallpaperTint(style.surface, if (dark) 219 else 184)
    private val separator = wallpaperTint(style.ink, if (dark) 48 else 23)
    private val rim = if (dark) wallpaperTint(style.ink, 29) else 0x50ffffff
    private val radius = if (portrait) 10 else 15
    private val endGap = if (short) 7 else 17
    private val airyHeight = if (portrait) 59 else 66
    private val minHeightDp = if (short) 48 else airyHeight
    private val inner = if (portrait) 12 else if (short) 6 else 10
    private val nameInset = if (portrait || short) 0 else 3
    private val previewInset = if (portrait || short) 0 else 4
    private fun dp(value: Int) = style.dp(value)
    private fun LayoutParams.withStart(gap: Int) = apply { marginStart = dp(gap) }

    private val slots = (listOf(0) + PresetSlots.userFacing).map { slot ->
        val title = if (slot == 0) context.getString(R.string.lever_title)
            else context.getString(R.string.home_shortcut_preset, PresetSlots.label(slot))
        Tile("product/home/direct-preset/$slot", title, if (slot == 0) Glyph.LEVER else Glyph.BOOKMARK) {
            onSlotClick(slot)
        }
    }
    private val previous = Tile("product/home/last-curve", context.getString(R.string.home_shortcut_previous),
        Glyph.CLOCK, "product/home/quick-launch", "product/home/quick-recipe", onLastClick)
    val visuals = slots.map(Tile::visual)
    val last = previous.visual
    private var hasLever = false
    private var arranged: List<Int> = emptyList()
    private var columns = 0
    private val group = row().apply {
        tag = "product/home/preset-group"
        background = cardSurface()
        clipToOutline = true
        elevation = if (dark) dp(3).toFloat() else 0f
    }

    init {
        tag = "product/home/direct-presets"
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        keepOwnBackdropTreatment()
    }

    fun showSlots(lever: Boolean) {
        if (hasLever == lever) return
        hasLever = lever
        requestLayout()
    }

    private val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val contactCutout = Path()
    private data class ShadowSize(val width: Int, val height: Int)
    private val lightShadowCache = mutableListOf<Pair<ShadowSize, Bitmap>>()
    private val lightShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff1d263b.toInt() }
    private val shadowDensity = resources.displayMetrics.density
    private val lightShadowMargin = dp(8).coerceAtLeast(1)
    private val lightMaskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(36, 255, 255, 255)
        maskFilter = BlurMaskFilter(3f * shadowDensity, BlurMaskFilter.Blur.NORMAL)
    }

    private fun drawLightShadow(canvas: Canvas, left: Float, top: Float, card: View, radiusPx: Float) {
        val width = card.width + 2 * lightShadowMargin
        val height = card.height + 2 * lightShadowMargin
        if (card.width <= 0 || card.height <= 0 || width > 4096 || height > 768) return
        val byteEstimate = ((width + 3) and 3.inv()) * height
        if (byteEstimate > 1536 * 1024) return
        var hit: Bitmap? = null
        for (index in lightShadowCache.indices) {
            val entry = lightShadowCache[index]
            if (entry.first.width == width && entry.first.height == height) {
                hit = entry.second
                break
            }
        }
        val bitmap = hit ?: run {
            // Software rasterizes one smooth alpha mask per card size. The hardware
            // pass only composites it, and masked Bitmap bounds cap cache memory.
            val byteBudget = 3 * 1024 * 1024
            while (lightShadowCache.size >= 8 ||
                lightShadowCache.sumOf { it.second.allocationByteCount } + byteEstimate > byteBudget) {
                lightShadowCache.removeAt(0)
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8).also { mask ->
                // Width/height and translation are already exact device pixels.
                mask.density = Bitmap.DENSITY_NONE
                val inset = lightShadowMargin.toFloat()
                val drop = 1f * shadowDensity
                Canvas(mask).drawRoundRect(inset, inset + drop, width - inset, height - inset + drop,
                    radiusPx, radiusPx, lightMaskPaint)
                lightShadowCache += ShadowSize(width, height) to mask
            }
        }
        canvas.drawBitmap(bitmap, left - lightShadowMargin, top - lightShadowMargin, lightShadowPaint)
    }
    // Draw the contact shadow in the parent so the card's rounded ripple clipping
    // cannot cut it off. Tablet A–D has a single contact shadow around the group.
    override fun dispatchDraw(canvas: Canvas) {
        for (rowIndex in 0 until childCount) {
            val child = getChildAt(rowIndex)
            if (child is Tile) {
                drawContact(canvas, child, 0, 0)
            } else if (child is LinearLayout) {
                for (cardIndex in 0 until child.childCount) {
                    val card = child.getChildAt(cardIndex)
                    if (card === group || card is Tile) drawContact(canvas, card, child.left, child.top)
                }
            }
        }
        super.dispatchDraw(canvas)
    }

    private fun drawContact(canvas: Canvas, card: View, x: Int, y: Int) {
        val density = resources.displayMetrics.density
        val left = (x + card.left).toFloat()
        val top = (y + card.top).toFloat()
        val right = (x + card.right).toFloat()
        val bottom = (y + card.bottom).toFloat()
        val radiusPx = radius * density
        // Clip out the actual rounded card so the translucent fill keeps its own color.
        val saved = canvas.save()
        contactCutout.rewind()
        contactCutout.addRoundRect(left, top, right, bottom, radiusPx, radiusPx, Path.Direction.CW)
        canvas.clipOutPath(contactCutout)
        if (!dark) {
            drawLightShadow(canvas, left, top, card, radiusPx)
            canvas.restoreToCount(saved)
            return
        }
        for (layer in 3 downTo 1) {
            val spread = layer * density
            val offset = 1.25f * density
            contactPaint.color = Color.argb(when (layer) { 1 -> 56; 2 -> 32; else -> 18 }, 0, 0, 0)
            val corner = radiusPx + spread
            canvas.drawRoundRect(left - spread, top - spread + offset, right + spread,
                bottom + spread + offset, corner, corner, contactPaint)
        }
        canvas.restoreToCount(saved)
    }

    private fun cardSurface() = style.shape(surface, radius = radius).apply {
        if (dark) {
            orientation = GradientDrawable.Orientation.TOP_BOTTOM
            // A small palette-derived top lift reads above the shadow on a near-black
            // solid background, without a bright full outline or sacrificing contrast.
            colors = intArrayOf(
                wallpaperTint(androidx.core.graphics.ColorUtils.blendARGB(style.surface, style.ink, .07f), 219),
                surface,
            )
        }
        setStroke(dp(1), rim)
    }

    private fun row() = style.row().apply {
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
    }
    private fun tile(slot: Int) = if (slot == HomeDirectPresetSlots.LAST) previous else slots[slot]

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val visible = HomeDirectPresetSlots.visible(hasLever)
        val availableDp = (MeasureSpec.getSize(widthMeasureSpec) / resources.displayMetrics.density).toInt()
        val fit = HomeDirectPresetSlots.columns(availableDp, visible.size, portrait, resources.configuration.fontScale)
        if (arranged != visible || columns != fit) {
            arrange(visible, fit)
            arranged = visible
            columns = fit
        }
        if (!portrait && fit == visible.size) sizeWideTiles(visible, MeasureSpec.getSize(widthMeasureSpec))
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun sizeWideTiles(visible: List<Int>, widthPx: Int) {
        val dividers = dp(1) * 3
        val usable = (widthPx - dividers - dp(endGap) * (if (hasLever) 2 else 1)).coerceAtLeast(0)
        val weights = visible.map { when (it) {
            0 -> 1.02f
            3 -> 1.15f
            4 -> 1.03f
            HomeDirectPresetSlots.LAST -> 1.19f
            else -> .91f
        } }
        val measured = visible.map { slot ->
            val visual = tile(slot).visual
            fun required(text: TextView) =
                kotlin.math.ceil(android.text.Layout.getDesiredWidth(text.text, text.paint)).toInt()
            maxOf(dp(48), 2 * dp(inner) +
                maxOf(required(visual.name) + dp(nameInset), required(visual.label) + dp(21 + previewInset)))
        }
        val widths = HomeDirectPresetSlots.wideWidths(usable, weights, measured)
        var groupWidth = dividers
        var rowHeight = dp(minHeightDp)
        visible.forEachIndexed { index, slot ->
            val tile = tile(slot)
            val width = widths[index]
            (tile.layoutParams as LayoutParams).width = width
            rowHeight = maxOf(rowHeight, tile.contentHeightForWidth(width))
            if (slot in 1..4) groupWidth += width
        }
        (group.layoutParams as LayoutParams).width = groupWidth

        // A plain separator View asked to MATCH_PARENT in a wrap-content row consumes
        // all available AT_MOST height on Android. Establish one intrinsic height from the
        // text first, then give every tile and divider that exact height; two-line titles
        // can grow the strip, but a tall viewport cannot.
        val strip = group.parent as LinearLayout
        strip.layoutParams.height = rowHeight
        for (index in 0 until strip.childCount) strip.getChildAt(index).layoutParams.height = rowHeight
        for (index in 0 until group.childCount) group.getChildAt(index).layoutParams.height = rowHeight
    }

    private fun arrange(visible: List<Int>, fit: Int) {
        (slots + previous).forEach { (it.parent as? android.view.ViewGroup)?.removeView(it) }
        // Removing the old strip from this grid does not detach its children from the strip.
        (group.parent as? android.view.ViewGroup)?.removeView(group)
        group.removeAllViews()
        removeAllViews()
        if (!portrait && fit == visible.size) {
            val strip = row()
            addView(strip, LayoutParams(-1, -2))
            if (hasLever) {
                slots[0].standalone(true)
                strip.addView(slots[0], LayoutParams(0, -1))
            }
            strip.addView(group, LayoutParams(0, -1).withStart(if (hasLever) endGap else 0))
            PresetSlots.userFacing.forEach { index ->
                if (index > 1) group.addView(View(context).apply { setBackgroundColor(separator) }, LayoutParams(dp(1), -1))
                slots[index].standalone(false)
                group.addView(slots[index], LayoutParams(0, -1))
            }
            previous.standalone(true)
            strip.addView(previous, LayoutParams(0, -1).withStart(endGap))
        } else {
            visible.chunked(fit).forEachIndexed { rowIndex, entries ->
                val strip = row()
                addView(strip, LayoutParams(-1, -2).apply { if (rowIndex > 0) topMargin = dp(5) })
                val spanLast = portrait && resources.configuration.screenWidthDp in 1 until 600 &&
                    fit == 2 && visible.size == 5 && entries.singleOrNull() == HomeDirectPresetSlots.LAST
                // A sole weighted MATCH_PARENT tile has no intrinsic row height.
                val tileHeight = if (spanLast) LayoutParams.WRAP_CONTENT else LayoutParams.MATCH_PARENT
                entries.forEachIndexed { column, index ->
                    tile(index).standalone(true)
                    strip.addView(tile(index), LayoutParams(0, tileHeight, 1f).withStart(if (column == 0) 0 else 7))
                }
                // Preserve the existing sparse layout outside narrow portrait.
                if (!spanLast && entries.size < fit) repeat(fit - entries.size) {
                    strip.addView(Space(context), if (entries.last() == HomeDirectPresetSlots.LAST) 0 else strip.childCount,
                        LayoutParams(0, 1, 1f).withStart(7))
                }
            }
        }
        if (isAttachedToWindow) {
            // Reattaching a reused subtree clears Android's isLaidOut flags.
            // Force one real descendant traversal even if sizes/specs match before and after.
            // This runs only when arrange changes the hierarchy, not during ordinary measures.
            fun markReattached(view: View) {
                view.forceLayout()
                if (view is ViewGroup) {
                    for (index in 0 until view.childCount) markReattached(view.getChildAt(index))
                }
            }
            for (index in 0 until childCount) markReattached(getChildAt(index))
        }
    }

    private inner class Tile(
        id: String, title: String, glyph: Glyph, actionId: String = id, nameId: String = "$id/name", click: () -> Unit,
    ) : FrameLayout(context) {
        private val mask = style.shape(Color.WHITE, radius = 0)
        private val ripple = ColorStateList.valueOf(wallpaperTint(style.ink, if (dark) 40 else 24))
        val visual: Visual
        private val words: LinearLayout
        init {
            if (glyph == Glyph.CLOCK) tag = id
            minimumHeight = dp(minHeightDp)
            minimumWidth = dp(48)
            clipToOutline = true
            words = style.column().apply {
                gravity = Gravity.CENTER_VERTICAL
                val top = if (short) 3 else if (portrait) 5 else 8
                val bottom = if (short) 3 else if (portrait) 8 else 10
                setPadding(dp(inner), dp(top), dp(inner), dp(bottom))
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            val label = style.text(title, TaskProductVisualStyle.Type(12, 500, 17), "$id/label").apply { setTextColor(labelInk) }
            val spark = CurveLibrarySparklineView(context, style, compact = true).apply {
                tag = "$id/preview"; visibility = GONE
                importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val header = object : LinearLayout(context) {
                init {
                    gravity = Gravity.CENTER_VERTICAL
                    isBaselineAligned = false
                    setPaddingRelative(0, 0, dp(previewInset), 0)
                }
                override fun onMeasure(w: Int, h: Int) {
                    // Read intrinsic text width without changing the real child's measure spec.
                    // A speculative measure can clear isLaidOut even when the final bounds are cached.
                    val labelWidth = kotlin.math.ceil(android.text.Layout.getDesiredWidth(label.text, label.paint)).toInt()
                    val total = MeasureSpec.getSize(w)
                    val available = total - paddingStart - paddingEnd - labelWidth - dp(21)
                    // Decorative previews have lower priority than the full slot label.
                    val max = dp(if (portrait) 40 else 46)
                    val target = if (available >= dp(18)) (available - dp(4)).coerceIn(0, max) else 0
                    (spark.layoutParams as LinearLayout.LayoutParams).apply { width = target; marginStart = if (target > 0) dp(4) else 0 }
                    super.onMeasure(w, h)
                }
            }
            header.addView(PresetGlyph(context, glyph, labelInk), LinearLayout.LayoutParams(dp(16), dp(16)))
            header.addView(label, LinearLayout.LayoutParams(0, -2, 1f).withStart(5))
            header.addView(spark, LinearLayout.LayoutParams(dp(40), dp(16)))
            words.addView(header, LinearLayout.LayoutParams(-1, -2))
            // The reference longest name is 133dp wide on a 360dp screen.
            // Noto Sans SC 550 measures ~132dp at 13sp; preserve app font scaling and a 1dp
            // allowance for physical-pixel rounding on the 360dp acceptance viewport.
            val titleSp = if (portrait) 13 else if (short) 14 else 15
            val name = style.text("", TaskProductVisualStyle.Type(titleSp, 550, titleSp + 5), nameId).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPaddingRelative(dp(nameInset), 0, 0, 0)
            }
            words.addView(name, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (short) 0 else 3) })
            val button = Button(context).apply {
                tag = actionId
                setPadding(0, 0, 0, 0)
                minHeight = 0; minWidth = 0; minimumHeight = 0; minimumWidth = 0
                elevation = 0f; stateListAnimator = null; backgroundTintList = null
                setOnClickListener { click() }
                if (!portrait && glyph != Glyph.CLOCK && onNameLongClick != null) setOnLongClickListener {
                    onNameLongClick.invoke(name.text.toString())
                    true
                }
                // The content and accessibility name are both provided by the native card above.
                background = StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused),
                        RippleDrawable(ripple, style.shape(wallpaperTint(style.accent, 44), radius = 0), style.shape(Color.WHITE, radius = 0)))
                    addState(intArrayOf(android.R.attr.state_selected),
                        RippleDrawable(ripple, style.shape(wallpaperTint(style.accent, 18), radius = 0), style.shape(Color.WHITE, radius = 0)))
                    addState(intArrayOf(), RippleDrawable(ripple, null, mask))
                }
            }
            // Visual content first, real keyboard/TalkBack button overlays the full tile.
            addView(words, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL))
            addView(button, FrameLayout.LayoutParams(-1, -1))
            visual = Visual(button, name, spark, label)
        }
        fun contentHeightForWidth(width: Int): Int {
            words.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            return words.measuredHeight
        }
        fun standalone(value: Boolean) {
            background = if (value) cardSurface() else null
            elevation = if (value && dark) dp(3).toFloat() else 0f
        }
    }

    private enum class Glyph { LEVER, BOOKMARK, CLOCK }
    private class PresetGlyph(context: Context, val glyph: Glyph, color: Int) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 1.65f
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            style = Paint.Style.STROKE
        }
        private val outline = Path()
        override fun onDraw(canvas: Canvas) {
            val restore = canvas.save()
            canvas.scale(width / 20f, height / 20f)
            when (glyph) {
                Glyph.BOOKMARK -> {
                    outline.rewind(); outline.moveTo(4.5f, 3f); outline.lineTo(15.5f, 3f)
                    outline.lineTo(15.5f, 17f); outline.lineTo(10f, 13.5f); outline.lineTo(4.5f, 17f)
                    outline.close(); canvas.drawPath(outline, paint)
                }
                Glyph.CLOCK -> {
                    canvas.drawCircle(10f, 10f, 7.5f, paint)
                    canvas.drawLine(10f, 6f, 10f, 10f, paint)
                    canvas.drawLine(10f, 10f, 13f, 12.5f, paint)
                }
                Glyph.LEVER -> {
                    canvas.drawLine(1.5f, 16.5f, 17f, 16.5f, paint)
                    canvas.drawArc(5f, 11f, 12f, 22f, 180f, 180f, false, paint)
                    canvas.drawLine(9.5f, 11.5f, 15.5f, 5f, paint)
                    paint.style = Paint.Style.FILL
                    canvas.drawCircle(16.5f, 4f, 2.5f, paint)
                    paint.style = Paint.Style.STROKE
                }
            }
            canvas.restoreToCount(restore)
        }
    }
}
