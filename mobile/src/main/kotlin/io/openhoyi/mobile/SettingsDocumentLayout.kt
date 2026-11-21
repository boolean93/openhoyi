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
import android.widget.TextView
import androidx.core.view.ViewCompat

/** Fixed category index above/beside one continuous Settings document. */
internal class SettingsDocumentLayout(private val host: MainActivity, state: Bundle?) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private data class Section(val key: String, val heading: View, val block: LinearLayout)
    private data class Position(val anchor: String, val offset: Int = 0, val end: Boolean = false)
    private val sections = linkedMapOf<String, Section>()
    private val statuses = mutableMapOf<String, String>()
    private val buttons = linkedMapOf<SettingsCategory, Button>()
    private val disclosureValues = mutableMapOf<String, String>()
    private val disclosureRenders = mutableMapOf<String, () -> Unit>()
    private val disclosureKeys = state?.getStringArrayList("settings.disclosures").orEmpty().toMutableSet()
    private var brewingChapter: View? = null
    private var brewingCard: View? = null
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
        overScrollMode = OVER_SCROLL_NEVER // Do not translate the document at either end.
        isFocusableInTouchMode = true
        isSaveEnabled = false // Restore a heading and offset, not a pre-rotation absolute Y.
        addView(body)
    }
    private var active = SettingsCategory.entries.firstOrNull { it.key == state?.getString("settings.category") }
        ?: state?.getString("settings.anchor")?.let(SettingsCategory::forSection) ?: SettingsCategory.DEVICE
    private val formerState = state?.getBundle("settings.category.${active.key}")
    private var position = Position(
        state?.getString("settings.anchor") ?: formerState?.getString("anchor") ?: active.sections.first(),
        state?.getInt("settings.offset")?.takeIf { state.containsKey("settings.offset") } ?: formerState?.getInt("offset") ?: 0,
        state?.getBoolean("settings.atEnd")?.takeIf { state.containsKey("settings.atEnd") } ?: formerState?.getBoolean("end") ?: false,
    )
    private var wide: Boolean? = null
    private var restoring = true
    private var requestedHeading = state?.getString("settings.requested")
    private var previousWidth = 0
    private var completingPosition: Position? = null

    init {
        SettingsCategory.entries.forEach { category ->
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
        scroll.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!restoring) rememberPosition(updateHighlight = true)
        }
        viewTreeObserver.addOnGlobalLayoutListener {
            if (!restoring || !ViewCompat.isLaidOut(scroll) || scroll.height == 0) return@addOnGlobalLayoutListener
            val pending = position
            if (completingPosition === pending) return@addOnGlobalLayoutListener
            completingPosition = pending
            val target = sections[pending.anchor] ?: sections["devices"] ?: return@addOnGlobalLayoutListener
            val focus = requestedHeading?.let(sections::get)?.heading
            if (focus != null) {
                focus.requestFocus()
                if (host.getSystemService(AccessibilityManager::class.java)?.isEnabled == true)
                    focus.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)
            }
            fun land() = scroll.scrollTo(0, if (pending.end) maxScroll()
                else (top(target) + pending.offset).coerceIn(0, maxScroll()))
            land()
            scroll.post {
                // ScrollView can finish a pending focus scroll during layout. Resolve the explicit
                // anchor after that pass as well, before allowing scroll-driven highlight updates.
                if (!restoring || position !== pending || !isAttachedToWindow) return@post
                land()
                requestedHeading = null
                previousWidth = width
                restoring = false
                completingPosition = null
                rememberPosition(updateHighlight = false)
            }
        }
        updateButtons()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val usable = ((MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight) /
            resources.displayMetrics.density).toInt()
        val rail = SettingsCategory.usesRail(usable)
        if (wide != rail) {
            wide = rail
            brewingChapter?.visibility = if (rail) VISIBLE else GONE
            brewingCard?.background = settingsRaisedGroup(host, style, horizontalInsetDp = if (rail) 4 else 0)
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
            body.applySettingsWideMaterial(rail)
            updateButtons()
        }
        if (previousWidth != MeasureSpec.getSize(widthMeasureSpec)) restoring = true
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    fun section(key: String, title: String): LinearLayout {
        requireNotNull(SettingsCategory.forSection(key))
        check(key !in sections)
        val block = style.column("settings/section/$key").apply {
            setPadding(style.dp(10), 0, style.dp(10), style.dp(14))
            protectRoundedBackdropContent(style.surface, style.dp(12), leaveBottomPaddingVisible = true)
        }
        val heading = style.text(title, TaskProductVisualStyle.settingsChapter, "settings/heading/$key").apply {
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
            body.addView(this, at, LayoutParams(-1, -2))
        }
        return style.column("settings/devices/scan-section").apply {
            setPadding(style.dp(10), 0, style.dp(10), style.dp(12))
            body.addView(this, at + 1, LayoutParams(-1, -2))
            scanning = this
        }
    }

    fun machineStatus(status: TextView): LinearLayout {
        val section = sections.getValue("machine")
        section.block.removeView(section.heading)
        style.apply(section.heading as TextView, TaskProductVisualStyle.Type(22, 700, 32))
        val trailing = style.row().apply {
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            addView(status, LayoutParams(-2, -2))
        }
        val row = SettingsVisualStyle(host).pair(section.heading, trailing, "settings/machine/header", gapDp = 12)
        section.block.addView(row, 0, LayoutParams(-1, -2))
        return trailing
    }

    fun brewingRow(key: String, label: Int, hint: Int?, toggle: android.widget.Switch) {
        val section = sections.getValue(key)
        val content = section.block.getChildAt(1) as LinearLayout
        val heading = section.heading as TextView
        content.removeView(toggle)
        section.block.removeView(heading)
        heading.text = host.getString(label)
        style.apply(heading, TaskProductVisualStyle.Type(16, 550, 24))
        heading.setPadding(0, 0, 0, 0)
        val words = style.column().apply {
            addView(heading, LayoutParams(-1, -2))
            hint?.let { style.add(this, style.text(host.getString(it), TaskProductVisualStyle.auxiliary, secondary = true), 2) }
        }
        (toggle as? HoyiSwitch)?.useSettingsPanelInk()
        toggle.contentDescription = heading.text
        toggle.text = ""
        toggle.switchPadding = 0
        val row = style.row("settings/brewing/row/$key").apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = style.dp(if (hint == null) 56 else 64)
            addView(words, LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(12) })
            addView(toggle, LayoutParams(-2, -2))
        }
        content.addView(row, 0, LayoutParams(-1, -2))
        section.block.setPadding(style.dp(12), 0, style.dp(12), 0)
    }

    /** Build each panel once; reorder before attachment so View identity survives category taps. */
    fun finishSections() {
        val ordered = listOfNotNull(pairedDevices, scanning) + SettingsCategory.entries
            .filter { it != SettingsCategory.DEVICE }
            .flatMap { it.sections }.map { sections.getValue(it).block }
        check(ordered.size == body.childCount)
        body.removeAllViews()
        val brew = style.column("settings/brewing/group").apply {
            brewingCard = this
            background = settingsRaisedGroup(host, style, horizontalInsetDp = if (wide == true) 4 else 0)
            setPadding(0, style.dp(4), 0, style.dp(4))
        }
        ordered.forEach { child ->
            val section = sections.values.firstOrNull { it.block === child }
            if (section?.key !in SettingsCategory.BREWING.sections) body.addView(child, LayoutParams(-1, -2))
            else {
                if (brew.parent == null) {
                    brewingChapter = style.text(host.getString(R.string.settings_category_brewing),
                        TaskProductVisualStyle.Type(22, 700, 32), "settings/brewing/title").apply {
                        visibility = if (wide == true) VISIBLE else GONE
                        setPadding(style.dp(10), style.dp(8), style.dp(10), style.dp(8))
                        ViewCompat.setAccessibilityHeading(this, true)
                        body.addView(this, LayoutParams(-1, -2))
                    }
                    body.addView(brew, LayoutParams(-1, -2))
                }
                if (brew.childCount > 0) brew.addView(hairline(), LayoutParams(-1, style.dp(1)).apply {
                    marginStart = style.dp(12); marginEnd = style.dp(12)
                })
                brew.addView(child, LayoutParams(-1, -2))
            }
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
            val value = disclosureValues[key]?.takeIf { !expanded && it.isNotBlank() }
            val caption = if (value != null) "$title   $value" else title
            val rendered = caption + if (expanded) "  ▾" else "  ›"
            if (header.text.toString() != rendered) header.text = rendered
            if (header.contentDescription != caption) header.contentDescription = caption
            ViewCompat.setStateDescription(header, host.getString(
                if (expanded) R.string.settings_category_expanded else R.string.settings_category_collapsed))
        }
        header.setOnClickListener {
            if (!disclosureKeys.add(key)) disclosureKeys.remove(key)
            render()
        }
        style.add(parent, header, 8)
        style.add(parent, content, 4)
        disclosureRenders[key] = ::render
        render()
        return content
    }

    fun setDisclosureValue(key: String, value: String) {
        if (disclosureValues.put(key, value) != value) disclosureRenders[key]?.invoke()
    }

    fun setStatus(key: String, status: String?) {
        val normalized = status.orEmpty()
        if (statuses[key].orEmpty() == normalized) return
        if (normalized.isEmpty()) statuses.remove(key) else statuses[key] = normalized
        updateButtons()
    }

    fun navigate(key: String) = moveTo(key, focus = true)

    private fun select(category: SettingsCategory) = moveTo(category.sections.first(), focus = false)

    private fun moveTo(key: String, focus: Boolean) {
        val category = SettingsCategory.forSection(key) ?: return
        if (key !in sections) return
        active = category
        position = Position(key)
        requestedHeading = key.takeIf { focus }
        restoring = true
        if (!focus && scroll.findFocus() != null && !scroll.isFocused) {
            // An explicit destination tap may leave an underlying editor focused. Keep its draft,
            // but don't let the framework pull the viewport back to that offscreen control.
            val policy = scroll.descendantFocusability
            scroll.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            try { scroll.requestFocus() }
            finally { scroll.descendantFocusability = policy }
        }
        updateButtons()
        requestLayout()
    }

    internal fun isAtSectionLanding(key: String): Boolean =
        !restoring && sections[key]?.let {
            kotlin.math.abs(scroll.scrollY - top(it).coerceIn(0, maxScroll())) <= style.dp(2)
        } == true

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
    internal fun sectionAnchor(key: String): View = if (key == "shortcuts")
        brewingChapter?.takeIf { it.visibility == VISIBLE } ?: brewingCard ?: sections.getValue(key).block
    else sections.getValue(key).block
    private fun top(section: Section): Int = Rect(0, 0, 1, 1).also {
        body.offsetDescendantRectToMyCoords(sectionAnchor(section.key), it)
    }.top
    private fun maxScroll() = (body.height - scroll.height).coerceAtLeast(0)
    private fun rememberPosition(updateHighlight: Boolean) {
        val naturalOrder = SettingsCategory.entries.flatMap { it.sections }.mapNotNull(sections::get)
        val first = naturalOrder.firstOrNull() ?: return
        val end = maxScroll() > 0 && !scroll.canScrollVertically(1)
        val current = naturalOrder.filter { top(it) <= scroll.scrollY + style.dp(12) }.maxByOrNull(::top) ?: first
        position = Position(current.key, scroll.scrollY - top(current), end)
        if (updateHighlight) {
            val category = if (end) SettingsCategory.MAINTENANCE else SettingsCategory.forSection(current.key)
            if (category != null && active != category) {
                active = category
                updateButtons()
            }
        }
    }

    fun save(state: Bundle) {
        if (!restoring) rememberPosition(updateHighlight = false)
        state.putString("settings.category", active.key)
        state.putString("settings.anchor", position.anchor)
        state.putInt("settings.offset", position.offset)
        state.putBoolean("settings.atEnd", position.end)
        state.putString("settings.requested", requestedHeading)
        state.putStringArrayList("settings.disclosures", ArrayList(disclosureKeys))
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
