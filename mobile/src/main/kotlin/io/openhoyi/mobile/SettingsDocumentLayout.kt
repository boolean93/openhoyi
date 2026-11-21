package io.openhoyi.mobile

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ImageSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat

/** Category selection is independent of scroll; one existing instance of every section. */
internal class SettingsDocumentLayout(private val host: MainActivity, state: Bundle?) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private data class Section(val key: String, val heading: View, val block: LinearLayout)
    private data class Position(val anchor: String, val offset: Int = 0, val end: Boolean = false)
    private val sections = linkedMapOf<String, Section>()
    private val positions = mutableMapOf<SettingsCategory, Position>()
    private val statuses = mutableMapOf<String, String>()
    private val buttons = linkedMapOf<SettingsCategory, Button>()
    private val disclosureKeys = state?.getStringArrayList("settings.disclosures").orEmpty().toMutableSet()
    private var pairedDevices: View? = null
    private var scanning: View? = null
    private val headerBackdrop = Any()
    private val indexBody = style.column("settings/document/category-rail")
    private val compact = style.column("settings/document/category-grid")
    private val panel = style.row("settings/document/columns")
    private val divider = View(host).apply { setBackgroundColor(style.border) }
    val body = style.column("settings/document/body")
    val scroll = ScrollView(host).apply {
        id = R.id.m8_settings_scroll
        tag = "settings/document/scroll"
        isFillViewport = true
        isSaveEnabled = false // Scroll restoration belongs to the active category, not Android's old absolute Y.
        addView(body)
    }
    private var active = SettingsCategory.entries.firstOrNull { it.key == state?.getString("settings.category") }
        ?: state?.getString("settings.anchor")?.let(SettingsCategory::forSection) ?: SettingsCategory.DEVICE
    private var wide: Boolean? = null
    private var restoring = true
    private var requestedHeading = state?.getString("settings.requested")
    private var resolvedTop = -1
    private var previousWidth = 0

    init {
        SettingsCategory.entries.forEach { category ->
            val saved = state?.getBundle("settings.category.${category.key}")
            val anchor = saved?.getString("anchor")
                ?: state?.getString("settings.anchor")?.takeIf { it in category.sections }
            if (anchor != null) positions[category] = Position(anchor,
                saved?.getInt("offset") ?: state?.getInt("settings.offset") ?: 0,
                saved?.getBoolean("end") ?: state?.getBoolean("settings.atEnd") ?: false)
            buttons[category] = Button(host).apply {
                tag = "settings/category/${category.key}"
                text = host.getString(label(category))
                style.apply(this, TaskProductVisualStyle.action)
                asTaskTextAction()
                isAllCaps = false
                backgroundTintList = null
                stateListAnimator = null
                elevation = 0f
                minHeight = style.dp(48)
                minimumHeight = minHeight
                setOnClickListener { select(category) }
            }
        }
        orientation = VERTICAL
        setBackgroundColor(style.page)
        host.installPageBackdrop(this, style.page)
        setPadding(style.dp(16), style.dp(8), style.dp(16), 0)
        addView(style.text(host.getString(R.string.app_settings_title), TaskProductVisualStyle.section).apply {
            tag = "settings/document/title"
            protectRoundedBackdropContent(style.surface, style.dp(12), group = headerBackdrop)
            setPadding(style.dp(8), style.dp(8), 0, style.dp(14))
            ViewCompat.setAccessibilityHeading(this, true)
        }, LayoutParams(-1, -2))
        compact.protectRoundedBackdropContent(style.surface, style.dp(12), group = headerBackdrop)
        addView(compact, LayoutParams(-1, -2).apply { bottomMargin = style.dp(12) })
        indexBody.protectRoundedBackdropContent(style.surface, style.dp(12))
        panel.addView(ScrollView(host).apply {
            tag = "settings/document/category-sidebar"
            isVerticalScrollBarEnabled = false
            addView(indexBody)
        }, LayoutParams(style.dp(134), -1))
        panel.addView(divider, LayoutParams(style.dp(1), -1).apply {
            marginStart = style.dp(10); marginEnd = style.dp(14)
        })
        panel.addView(scroll, LayoutParams(0, -1, 1f))
        addView(panel, LayoutParams(-1, 0, 1f))
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> if (!restoring) rememberPosition() }
        viewTreeObserver.addOnGlobalLayoutListener {
            val position = position()
            val target = sections[position.anchor] ?: return@addOnGlobalLayoutListener
            if (!restoring && width == previousWidth && top(target) == resolvedTop) return@addOnGlobalLayoutListener
            if (!ViewCompat.isLaidOut(scroll) || scroll.height == 0 || target.block.visibility != VISIBLE) return@addOnGlobalLayoutListener
            restoring = true
            val focus = requestedHeading?.let(sections::get)?.heading
            if (focus != null) {
                focus.requestFocus()
                if (host.getSystemService(AccessibilityManager::class.java)?.isEnabled == true)
                    focus.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
            scroll.scrollTo(0, if (position.end) maxScroll() else (top(target) + position.offset).coerceAtLeast(0))
            previousWidth = width
            if (focus != null) requestedHeading = null
            restoring = false
            rememberPosition()
        }
        updateButtons()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val usable = ((MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) /
            resources.displayMetrics.density).toInt()
        val rail = SettingsCategory.usesRail(usable)
        if (wide != rail) {
            wide = rail
            indexBody.removeAllViews()
            for (button in buttons.values) (button.parent as? ViewGroup)?.removeView(button)
            compact.removeAllViews()
            compact.visibility = if (rail) GONE else VISIBLE
            panel.getChildAt(0).visibility = if (rail) VISIBLE else GONE
            divider.visibility = panel.getChildAt(0).visibility
            body.setPadding(style.dp(if (rail) 6 else 0), 0, style.dp(if (rail) 8 else 0), style.dp(16))
            if (rail) {
                buttons.values.forEachIndexed { i, button ->
                    button.gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    button.setPaddingRelative(style.dp(9), style.dp(5), style.dp(9), style.dp(5))
                    indexBody.addView(button, LayoutParams(-1, -2))
                    if (i != buttons.size - 1) indexBody.addView(hairline(),
                        LayoutParams(-1, style.dp(1)).apply { topMargin = style.dp(3); bottomMargin = style.dp(3) })
                }
            } else {
                compact.background = style.shape(style.surface, true, 12)
                buttons.values.chunked(3).forEachIndexed { i, cells ->
                    if (i > 0) compact.addView(hairline(), LayoutParams(-1, style.dp(1)))
                    val row = CompactCategoryRow(host, style.dp(1), style.dp(30), style.dp(48))
                    compact.addView(row, LayoutParams(-1, -2))
                    cells.forEachIndexed { j, button ->
                        // Compound controls can wrap at larger app sizes instead of clipping words.
                        button.gravity = Gravity.CENTER
                        button.setPaddingRelative(style.dp(3), style.dp(5), style.dp(3), style.dp(5))
                        if (j > 0) row.addView(hairline())
                        row.addView(button)
                    }
                }
            }
            updateButtons()
        }
        if (previousWidth != MeasureSpec.getSize(widthMeasureSpec)) restoring = true
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun section(key: String, title: String): LinearLayout {
        val category = requireNotNull(SettingsCategory.forSection(key))
        check(key !in sections)
        val block = style.column("settings/section/$key").apply {
            visibility = if (category == active) VISIBLE else GONE
            setPadding(style.dp(10), 0, style.dp(10), style.dp(14))
            protectRoundedBackdropContent(style.surface, style.dp(12), leaveBottomPaddingVisible = true)
        }
        val heading = style.text(title, TaskProductVisualStyle.section, "settings/heading/$key").apply {
            ViewCompat.setAccessibilityHeading(this, true)
            isFocusable = true
            isFocusableInTouchMode = true
            setPadding(0, style.dp(8), 0, style.dp(8))
        }
        block.addView(heading, LayoutParams(-1, -2))
        body.addView(block, LayoutParams(-1, -2))
        sections[key] = Section(key, heading, block)
        return style.column().also { block.addView(it, LayoutParams(-1, -2)) }
    }

    /** Only structural reparenting at creation, before listeners ever run. */
    fun arrangeDeviceOverview(): LinearLayout {
        val devices = sections.getValue("devices").block
        val scale = sections.getValue("scale").block
        val at = body.indexOfChild(devices)
        body.removeView(devices)
        body.removeView(scale)
        pairedDevices = SettingsColumns(host, style, devices, scale, org.json.JSONObject(), firstFraction = .5f,
            minimumFirstWidth = { style.dp(300) }, minimumSecondWidth = { style.dp(250) }, stackGapDp = 3).apply {
            tag = "settings/devices/overview"
            visibility = if (active == SettingsCategory.DEVICE) VISIBLE else GONE
            body.addView(this, at, LayoutParams(-1, -2))
        }
        return style.column("settings/devices/scan-section").apply {
            setPadding(style.dp(10), 0, style.dp(10), style.dp(12))
            visibility = if (active == SettingsCategory.DEVICE) VISIBLE else GONE
            body.addView(this, at + 1, LayoutParams(-1, -2))
            scanning = this
        }
    }

    fun disclosure(parent: LinearLayout, key: String, title: String): LinearLayout {
        val header = Button(host).apply {
            tag = "settings/details/$key/action"
            style.button(this, outline = true)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPaddingRelative(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
        }
        val content = style.column("settings/details/$key/content")
        fun render() {
            val expanded = key in disclosureKeys
            content.visibility = if (expanded) VISIBLE else GONE
            header.text = title + if (expanded) "  ▾" else "  ›"
            header.contentDescription = title
            ViewCompat.setStateDescription(header, host.getString(
                if (expanded) R.string.settings_category_expanded else R.string.settings_category_collapsed))
        }
        header.setOnClickListener {
            if (!disclosureKeys.add(key)) disclosureKeys.remove(key)
            render()
        }
        style.add(parent, header, 8)
        style.add(parent, content, 4)
        render()
        return content
    }

    fun setStatus(key: String, status: String?) {
        val normalized = status.orEmpty()
        if (statuses[key].orEmpty() == normalized) return
        if (normalized.isEmpty()) statuses.remove(key) else statuses[key] = normalized
        updateButtons()
    }

    fun navigate(key: String) {
        val category = SettingsCategory.forSection(key) ?: return
        if (key !in sections) return
        if (!restoring) rememberPosition()
        positions[category] = Position(key)
        requestedHeading = key
        show(category)
    }

    private fun select(category: SettingsCategory) {
        if (active == category) return
        if (!restoring) rememberPosition()
        requestedHeading = null
        show(category)
    }

    private fun show(category: SettingsCategory) {
        // No control/listener (or typed draft) is reconstructed when the index changes.
        findFocus()?.takeIf { focused -> sections.values.any { it.block.hasFocus() && it.block.findFocus() === focused } }
            ?.clearFocus()
        active = category
        pairedDevices?.visibility = if (category == SettingsCategory.DEVICE) VISIBLE else GONE
        scanning?.visibility = if (category == SettingsCategory.DEVICE) VISIBLE else GONE
        restoring = true
        sections.values.forEach { it.block.visibility = if (it.key in category.sections) VISIBLE else GONE }
        updateButtons()
        requestLayout()
    }

    private fun updateButtons() {
        buttons.forEach { (category, button) ->
            val selected = category == active
            val ink = if (selected) style.accent else style.ink
            button.isSelected = selected
            button.setTextColor(ink)
            val icon = host.getDrawable(icon(category))?.mutate()?.apply {
                setTint(ink)
                val side = style.dp(if (wide == true) 20 else 16)
                setBounds(0, 0, side, side)
            }
            val badges = category.sections.mapNotNull(statuses::get).distinct()
            val status = if (badges.isEmpty()) null else GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(style.accent)
                setBounds(0, 0, style.dp(5), style.dp(5))
            }
            val title = host.getString(label(category))
            button.text = icon?.let {
                SpannableString("\uFFFC  $title").apply {
                    setSpan(ImageSpan(it, if (Build.VERSION.SDK_INT >= 29) ImageSpan.ALIGN_CENTER else ImageSpan.ALIGN_BASELINE),
                        0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } ?: title
            button.setCompoundDrawablesRelative(null, null, status, null)
            button.compoundDrawablePadding = style.dp(4)
            val fill = if (selected) style.selected else Color.TRANSPARENT
            val states = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused), style.shape(fill, radius = 8).apply {
                    setStroke(style.dp(1), style.accent)
                })
                addState(intArrayOf(), style.shape(fill, radius = 8))
            }
            button.background = RippleDrawable(ColorStateList.valueOf(style.selected), states,
                style.shape(Color.WHITE, radius = 8))
            button.contentDescription = (listOf(title) + badges).joinToString(" · ")
        }
    }

    private fun hairline() = View(host).apply {
        setBackgroundColor(style.border)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private fun label(category: SettingsCategory): Int = when (category) {
        SettingsCategory.DEVICE -> R.string.settings_category_device
        SettingsCategory.MACHINE -> R.string.settings_category_machine
        SettingsCategory.BREWING -> R.string.settings_category_brewing
        SettingsCategory.APPEARANCE -> R.string.settings_category_appearance
        SettingsCategory.AI -> R.string.settings_category_ai
        SettingsCategory.MAINTENANCE -> R.string.settings_category_maintenance
    }
    private fun icon(category: SettingsCategory): Int = when (category) {
        SettingsCategory.DEVICE -> R.drawable.settings_category_device
        SettingsCategory.MACHINE -> R.drawable.settings_category_machine
        SettingsCategory.BREWING -> R.drawable.settings_category_brewing
        SettingsCategory.APPEARANCE -> R.drawable.settings_category_appearance
        SettingsCategory.AI -> R.drawable.settings_category_ai
        SettingsCategory.MAINTENANCE -> R.drawable.settings_category_maintenance
    }
    private fun position(): Position = positions[active] ?: Position(active.sections.first())
    private fun top(section: Section): Int = Rect(0, 0, 1, 1).also {
        body.offsetDescendantRectToMyCoords(section.block, it)
    }.top
    private fun maxScroll() = (body.height - scroll.height).coerceAtLeast(0)
    private fun rememberPosition() {
        val candidates = active.sections.mapNotNull(sections::get)
        if (candidates.isEmpty()) return
        val end = maxScroll() > 0 && scroll.scrollY >= maxScroll() - 2
        val current = candidates.filter { top(it) <= scroll.scrollY + style.dp(12) }.maxByOrNull(::top)
            ?: candidates.first()
        val top = top(current)
        positions[active] = Position(current.key, scroll.scrollY - top, end)
        resolvedTop = top
    }
    fun save(state: Bundle) {
        if (!restoring) rememberPosition()
        state.putString("settings.category", active.key)
        state.putString("settings.requested", requestedHeading)
        state.putStringArrayList("settings.disclosures", ArrayList(disclosureKeys))
        positions.forEach { (category, position) ->
            state.putBundle("settings.category.${category.key}", Bundle().apply {
                putString("anchor", position.anchor)
                putInt("offset", position.offset)
                putBoolean("end", position.end)
            })
        }
    }
}

/** Fixed-width cells keep their tallest natural line, independent of decorative separators. */
private class CompactCategoryRow(context: android.content.Context, private val dividerWidth: Int,
    private val dividerHeight: Int, private val minimum: Int) : ViewGroup(context) {
    override fun generateDefaultLayoutParams() = LayoutParams(-2, -2)
    private fun exact(value: Int) = MeasureSpec.makeMeasureSpec(value, MeasureSpec.EXACTLY)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        check(childCount == 5) // three controls separated by two decorative hairlines.
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val available = (width - 2 * dividerWidth).coerceAtLeast(0)
        val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        var rowHeight = minimum
        for (i in 0..2) {
            val child = getChildAt(i * 2)
            val extent = available * (i + 1) / 3 - available * i / 3
            child.measure(exact(extent), free)
            rowHeight = maxOf(rowHeight, child.measuredHeight)
        }
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (i % 2 == 0) child.measure(exact(child.measuredWidth), exact(rowHeight))
            else child.measure(exact(dividerWidth), exact(minOf(dividerHeight, rowHeight)))
        }
        setMeasuredDimension(width, rowHeight)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        var x = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val start = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - x - child.measuredWidth else x
            val top = (height - child.measuredHeight) / 2
            child.layout(start, top, start + child.measuredWidth, top + child.measuredHeight)
            x += child.measuredWidth
        }
    }
}
