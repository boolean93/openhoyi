package io.openhoyi.mobile

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.AbsListView
import android.widget.TextView
import kotlin.math.roundToInt

/** Renders a page or app navigation surface; system bars remain independent. */
internal class PageBackdrop(
    private val base: Int,
    private val dark: Boolean,
    private val applyPhotoMask: Boolean = true,
) : Drawable() {
    var snapshot = BackgroundSnapshot(AppBackgroundConfig())
        set(value) { field = value; invalidateSelf() }
    var protectedAreas: List<Rect>? = null
        set(value) { if (field != value) { field = value; invalidateSelf() } }
    var roundedAreas: List<RoundedBackdropArea> = emptyList()
        set(value) { if (field != value) { field = value; invalidateSelf() } }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    var photoBounds: (() -> Rect?)? = null
    var photoScrim: Int? = null
    private val crop = Rect()
    private var gradient: Shader? = null
    private var gradientChoice: AppBackground? = null

    override fun onBoundsChange(bounds: Rect) { gradient = null }

    override fun draw(canvas: Canvas) {
        paint.shader = null
        paint.color = base
        canvas.drawRect(bounds, paint)
        val regions = protectedAreas ?: return
        val choice = snapshot.config.choice
        paint.alpha = (snapshot.config.strength * 255f / 100f).roundToInt()
        if (choice is AppBackground.Photo && !bounds.isEmpty) {
            if (!applyPhotoMask) paint.alpha = 255
            snapshot.image?.let { image ->
                val destination = photoBounds?.invoke()?.takeUnless { it.isEmpty } ?: bounds
                val scale = maxOf(destination.width().toFloat() / image.width, destination.height().toFloat() / image.height)
                val width = (destination.width() / scale).roundToInt().coerceIn(1, image.width)
                val height = (destination.height() / scale).roundToInt().coerceIn(1, image.height)
                val left = (image.width - width) / 2
                val top = (image.height - height) / 2
                crop.set(left, top, left + width, top + height)
                val save = canvas.save()
                canvas.clipRect(bounds)
                canvas.drawBitmap(image, crop, destination, paint)
                canvas.restoreToCount(save)
                photoScrim?.let {
                    paint.color = it
                    canvas.drawRect(bounds, paint)
                }
            }
        } else if (choice == AppBackground.Mist || choice == AppBackground.Slate) {
            if (gradient == null || gradientChoice != choice) {
                gradientChoice = choice
                val colors = if (choice == AppBackground.Mist) {
                    if (dark) intArrayOf(0xff405e65.toInt(), 0xff182829.toInt())
                    else intArrayOf(0xffb5c9cc.toInt(), 0xfff8fcfc.toInt())
                } else {
                    if (dark) intArrayOf(0xff5a5e71.toInt(), 0xff221f2d.toInt())
                    else intArrayOf(0xffc2c5d1.toInt(), 0xfff9f8fb.toInt())
                }
                gradient = LinearGradient(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
                    bounds.bottom.toFloat(), colors, null, Shader.TileMode.CLAMP)
            }
            paint.shader = gradient
            canvas.drawRect(bounds, paint)
        }
        paint.shader = null
        paint.color = base
        regions.forEach { canvas.drawRect(it, paint) }
        roundedAreas.forEach { area ->
            val save = canvas.save()
            canvas.clipRect(area.clip)
            paint.color = area.fill
            val bounds = area.bounds
            canvas.drawRoundRect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(),
                bounds.bottom.toFloat(), area.radius, area.radius, paint)
            canvas.restoreToCount(save)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Deprecated("Drawable opacity") override fun getOpacity() = PixelFormat.OPAQUE
}

internal data class RoundedBackdropArea(val bounds: Rect, val clip: Rect, val radius: Float, val fill: Int)

private object OwnBackdropTreatment

private data class RoundedBackdropMark(
    val fill: Int, val radius: Float, val group: Any?, val horizontalOutset: Int,
    val horizontalBounds: View?, val leaveBottomPaddingVisible: Boolean, val clipContent: Boolean,
    val photoFill: Int?, val photoOnly: Boolean, val omitForPhoto: Boolean,
    val photoContentEnd: (() -> View?)?,
)

private data class BackdropProtection(
    val group: Any?,
    val horizontalBounds: View?,
    val leaveBottomPaddingVisible: Boolean,
)

/** Mark an existing semantic panel without wrapping children or changing their own drawable. */
internal fun View.protectBackdropContent(
    group: Any? = null,
    horizontalBounds: View? = null,
    leaveBottomPaddingVisible: Boolean = false,
) {
    setTag(R.id.page_backdrop_content, BackdropProtection(group, horizontalBounds, leaveBottomPaddingVisible))
}

/** A rounded piece of a native layout; realized only when the page displays a selected backdrop. */
internal fun View.protectRoundedBackdropContent(
    fill: Int, radius: Int, group: Any? = null, horizontalOutset: Int = 0,
    horizontalBounds: View? = null, leaveBottomPaddingVisible: Boolean = false, clipContent: Boolean = false,
    photoFill: Int? = null, photoOnly: Boolean = false, omitForPhoto: Boolean = false,
    photoContentEnd: (() -> View?)? = null,
) {
    require(!clipContent || group == null && horizontalBounds == null && horizontalOutset == 0 && !leaveBottomPaddingVisible)
    setTag(R.id.page_backdrop_content, RoundedBackdropMark(fill, radius.toFloat(), group, horizontalOutset,
        horizontalBounds, leaveBottomPaddingVisible, clipContent, photoFill, photoOnly, omitForPhoto,
        photoContentEnd))
}

/** A native surface or translucent overlay supplies its own contrast treatment, including its labels. */
internal fun View.keepOwnBackdropTreatment() {
    setTag(R.id.page_backdrop_content, OwnBackdropTreatment)
}

internal fun ThemedActivity.installPageBackdrop(view: View, base: Int, navigation: Boolean = false) {
    PageBackdropBinding(view, base, HoyiUi.dark(this), (application as MobileApplication).pageBackgrounds,
        navigation = navigation)
}

internal fun wallpaperTint(color: Int, alpha: Int = 96): Int =
    (color and 0x00ffffff) or (alpha shl 24)

/** A page-local geometry layer only: never changes child padding, backgrounds, input or navigation.
 * Explicit content panes and existing opaque surfaces take precedence. Bare labels are protected
 * with a row backing; adjacent labels are combined so protection need not look like text chips.
 */
internal class PageBackdropBinding(
    private val root: View,
    base: Int,
    dark: Boolean,
    private val store: AppBackgroundStore? = null,
    private val preview: (() -> BackgroundSnapshot?)? = null,
    private val navigation: Boolean = false,
) : View.OnAttachStateChangeListener, ViewTreeObserver.OnGlobalLayoutListener,
    ViewTreeObserver.OnScrollChangedListener {
    private val original = root.background
    private val paper = PageBackdrop(base, dark).apply {
        if (navigation) photoScrim = wallpaperTint(base)
        if (preview == null) photoBounds = photoBounds@{
            var ancestor = root.parent
            while (ancestor is View && ancestor !is TaskPageFrame) ancestor = ancestor.parent
            val frame = ancestor as? TaskPageFrame ?: return@photoBounds null
            val origin = IntArray(2).also(root::getLocationInWindow)
            val parentOrigin = IntArray(2).also(frame::getLocationInWindow)
            val left = parentOrigin[0] - origin[0]
            val top = parentOrigin[1] - origin[1]
            Rect(left, top, left + frame.width, top + frame.height)
        }
    }
    private val solid = BackgroundSnapshot(AppBackgroundConfig())
    private var stop: (() -> Unit)? = null
    private var regionsIn: ViewTreeObserver? = null
    private data class OriginalOutline(val provider: ViewOutlineProvider?, val clipToOutline: Boolean)
    private val clippedContent = mutableMapOf<View, OriginalOutline>()

    init {
        root.addOnAttachStateChangeListener(this)
        if (root.isAttachedToWindow) onViewAttachedToWindow(root)
    }

    override fun onViewAttachedToWindow(v: View) {
        if (store != null) stop = store.observe(::show)
        else preview?.invoke()?.let(::show)
        bindRegions()
    }

    override fun onViewDetachedFromWindow(v: View) {
        stop?.invoke(); stop = null
        unbindRegions()
        paper.protectedAreas = null
        paper.roundedAreas = emptyList()
        paper.snapshot = solid
    }

    fun show(snapshot: BackgroundSnapshot) {
        val active = (!navigation || snapshot.config.choice is AppBackground.Photo) &&
            snapshot.config.choice != AppBackground.Solid &&
            (snapshot.config.choice !is AppBackground.Photo || snapshot.image != null)
        if (!active) {
            unbindRegions()
            paper.snapshot = solid
            paper.protectedAreas = null
            paper.roundedAreas = emptyList()
            if (root.background === paper) root.background = original
            return
        }
        paper.snapshot = snapshot
        if (root.background !== paper) root.background = paper
        bindRegions()
        updateRegions()
    }

    private fun bindRegions() {
        if (!root.isAttachedToWindow || root.background !== paper || regionsIn != null) return
        regionsIn = root.viewTreeObserver.also {
            it.addOnGlobalLayoutListener(this)
            it.addOnScrollChangedListener(this)
        }
        updateRegions()
    }

    private fun unbindRegions() {
        clippedContent.forEach { (view, original) ->
            view.outlineProvider = original.provider
            view.clipToOutline = original.clipToOutline
        }
        clippedContent.clear()
        regionsIn?.takeIf { it.isAlive }?.let {
            it.removeOnGlobalLayoutListener(this)
            it.removeOnScrollChangedListener(this)
        }
        regionsIn = null
    }

    override fun onGlobalLayout() = updateRegions()
    override fun onScrollChanged() = updateRegions()

    private fun updateRegions() {
        if (root.background !== paper) return
        if (navigation) {
            paper.protectedAreas = emptyList()
            paper.invalidateSelf()
            return
        }
        val position = Point()
        if (!root.getGlobalVisibleRect(Rect(), position)) return
        // View.getGlobalVisibleRect includes the root's own scroll, whereas View draws
        // its background in viewport coordinates after compensating that scroll.
        position.offset(root.scrollX, root.scrollY)
        val exact = mutableListOf<Rect>()
        val groups = linkedMapOf<Any, Rect>()
        val rounded = mutableListOf<RoundedBackdropArea>()
        val roundedGroups = linkedMapOf<Any, RoundedBackdropArea>()
        val rows = mutableListOf<Rect>()
        val gap = (4 * root.resources.displayMetrics.density).roundToInt()
        val showingPhoto = paper.snapshot.config.choice is AppBackground.Photo
        fun relative(view: View) = Rect().takeIf { view.getGlobalVisibleRect(it) }
            ?.apply { offset(-position.x, -position.y) }
        fun protect(view: View, mark: BackdropProtection) {
            val bounds = Rect()
            val offset = Point()
            if (!view.getGlobalVisibleRect(bounds, offset)) return
            if (mark.leaveBottomPaddingVisible) {
                // Protection ends at the content's actual end, not a clipped viewport edge.
                bounds.bottom = minOf(bounds.bottom, offset.y + view.scrollY + view.height - view.paddingBottom)
            }
            bounds.offset(-position.x, -position.y)
            mark.horizontalBounds?.let(::relative)?.let { span ->
                bounds.left = span.left
                bounds.right = span.right
            }
            if (bounds.isEmpty) return
            val group = mark.group
            if (group == null) exact += bounds else groups.getOrPut(group) { Rect(bounds) }.union(bounds)
        }
        fun protectRounded(view: View, mark: RoundedBackdropMark) {
            val clip = Rect()
            val viewPosition = Point()
            if (!view.getGlobalVisibleRect(clip, viewPosition)) return
            // Build corners in layout coordinates, not at the cut edge of the scrolling viewport.
            val x = viewPosition.x + view.scrollX - position.x
            val y = viewPosition.y + view.scrollY - position.y
            val bounds = Rect(x, y, x + view.width, y + view.height)
            clip.offset(-position.x, -position.y)
            mark.horizontalBounds?.let(::relative)?.let { span ->
                bounds.left = span.left; bounds.right = span.right
                clip.left = span.left; clip.right = span.right
            }
            if (mark.leaveBottomPaddingVisible) {
                bounds.bottom -= view.paddingBottom
                clip.bottom = minOf(clip.bottom, bounds.bottom)
            }
            bounds.inset(-mark.horizontalOutset, 0)
            clip.left = (clip.left - mark.horizontalOutset).coerceAtLeast(0)
            clip.right = (clip.right + mark.horizontalOutset).coerceAtMost(root.width)
            if (bounds.isEmpty || clip.isEmpty) return
            if (mark.clipContent && view !in clippedContent) {
                // Selected rows and ripples must respect the rounded list viewport too.
                // Leaving the backdrop restores the precise native outline/clipping state.
                clippedContent[view] = OriginalOutline(view.outlineProvider, view.clipToOutline)
                view.outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(host: View, outline: android.graphics.Outline) {
                        outline.setRoundRect(0, 0, host.width, host.height, mark.radius)
                    }
                }
                view.clipToOutline = true
            }
            if (showingPhoto) {
                mark.photoContentEnd?.invoke()?.let(::relative)?.let { last ->
                    // Reveal the empty viewport without moving its rounded corners into a row.
                    clip.bottom = minOf(clip.bottom, last.bottom)
                }
            }
            val area = RoundedBackdropArea(bounds, clip, mark.radius, if (showingPhoto) mark.photoFill ?: mark.fill else mark.fill)
            val group = mark.group
            if (group == null) rounded += area else roundedGroups[group]?.let {
                it.bounds.union(bounds)
                it.clip.union(clip)
            } ?: run { roundedGroups[group] = area }
        }
        fun visit(view: View) {
            if (view.visibility != View.VISIBLE) return
            if (view !== root) {
                // This rectangle already hides everything it covers, including child labels.
                val color = view.background as? ColorDrawable
                if (color != null && Color.alpha(color.color) == 255 && view.alpha == 1f) return
                when (val mark = view.getTag(R.id.page_backdrop_content)) {
                    OwnBackdropTreatment -> return
                    is BackdropProtection -> { protect(view, mark); return }
                    is RoundedBackdropMark -> {
                        // The whole group handles contrast itself; don't fall through to
                        // automatic full-width backing behind its individual labels.
                        if (showingPhoto && mark.omitForPhoto) return
                        if (!mark.photoOnly || showingPhoto) { protectRounded(view, mark); return }
                    }
                }
                if (view is AbsListView ||
                    view is BoilerTemperatureDial || view is CurveStageView || view is CurveLibrarySparklineView ||
                    view is ShotChartView || view is LegacyShotChartView || view is TwoCupChartView ||
                    view is ExtractionTrendView || view is ScaleTrendView || view is AiCurveRecipeDiagramView) {
                    relative(view)?.let(exact::add)
                    return
                }
                if (view is TextView) {
                    val label = relative(view) ?: return
                    val parent = view.parent as? View
                    val parentBounds = parent?.let(::relative)
                    if (parentBounds != null) {
                        label.left = maxOf(parentBounds.left + parent.paddingLeft, 0)
                        label.right = minOf(parentBounds.right - parent.paddingRight, root.width)
                    }
                    label.top = (label.top - gap).coerceAtLeast(0)
                    label.bottom = (label.bottom + gap).coerceAtMost(root.height)
                    if (label.width() > 0 && label.height() > 0) rows += label
                    return
                }
            }
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index))
        }
        visit(root)
        val continuous = mutableListOf<Rect>()
        for (row in rows.sortedBy { it.top }) {
            val adjacent = continuous.lastOrNull()
            if (adjacent != null && adjacent.left == row.left && adjacent.right == row.right &&
                row.top <= adjacent.bottom + gap) adjacent.bottom = maxOf(adjacent.bottom, row.bottom)
            else continuous += row
        }
        paper.protectedAreas = exact + groups.values + continuous
        paper.roundedAreas = rounded + roundedGroups.values
    }
}

/** Observe a decoded custom photo only while the affected view is attached. */
internal fun View.observeActiveWallpaper(store: AppBackgroundStore, render: (Boolean) -> Unit) {
    var stop: (() -> Unit)? = null
    fun subscribe() {
        if (stop == null) stop = store.observe { snapshot ->
            render(snapshot.config.choice is AppBackground.Photo && snapshot.image != null)
        }
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = subscribe()
        override fun onViewDetachedFromWindow(v: View) {
            stop?.invoke()
            stop = null
        }
    })
    if (isAttachedToWindow) subscribe()
}

/** Preserve an outlined panel's shape, padding and default-theme drawable when no photo is used. */
internal fun View.tintPanelForWallpaper(store: AppBackgroundStore, tinted: Drawable) {
    val original = background
    observeActiveWallpaper(store) { photo ->
        val next = if (photo) tinted else original
        if (background !== next) background = next
    }
}
