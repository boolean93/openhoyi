package io.openhoyi.mobile

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** One scroll owner. The index always refers to persistent document headings. */
internal class SettingsDocumentLayout(private val host: MainActivity, state: Bundle?) : LinearLayout(host) {
    private val style = TaskProductVisualStyle(host)
    private data class Section(val key: String, val title: String, val block: LinearLayout, val button: Button)
    private val sections = mutableListOf<Section>()
    private val picker = Spinner(host)
    private val pickerLabels=mutableListOf<String>()
    private val pickerAdapter=ArrayAdapter(host,android.R.layout.simple_spinner_dropdown_item,pickerLabels)
    private val badges=mutableMapOf<String,String>()
    private val indexBody = LinearLayout(host).apply { orientation = VERTICAL }
    private val index = ScrollView(host).apply { addView(indexBody); isFillViewport = false }
    private val divider = View(host).apply { setBackgroundColor(style.border) }
    val body = LinearLayout(host).apply { orientation = VERTICAL; setPadding(style.dp(20), 0, style.dp(20), style.dp(24)) }
    val scroll = ScrollView(host).apply { id = R.id.m8_settings_scroll; tag = "settings/document/scroll"; isFillViewport = true; addView(body) }
    private val columns = LinearLayout(host).apply { orientation = HORIZONTAL }
    private var anchor = state?.getString("settings.anchor") ?: "devices"
    private var offset = state?.getInt("settings.offset") ?: 0
    private var atEnd = state?.getBoolean("settings.atEnd") ?: false
    private var applying = false
    private var pickerUpdating = false
    private var selected = -1
    private var laidOut = false
    private var restoredWidth = 0
    init {
        orientation = VERTICAL; setBackgroundColor(style.page)
        setPadding(style.dp(16), style.dp(8), style.dp(16), 0)
        addView(style.text(host.getString(R.string.app_settings_title), TaskProductVisualStyle.section).apply {
            setPadding(style.dp(8), style.dp(8), 0, style.dp(16)); androidx.core.view.ViewCompat.setAccessibilityHeading(this,true)
        }, LayoutParams(-1, -2))
        picker.adapter=pickerAdapter
        picker.minimumHeight = style.dp(48); picker.contentDescription = host.getString(R.string.app_settings_title)
        addView(picker, LayoutParams(-1, style.dp(48)))
        columns.addView(index, LayoutParams(style.dp(160), -1))
        columns.addView(divider, LayoutParams(style.dp(1), -1).apply { marginStart=style.dp(8); marginEnd=style.dp(8) })
        columns.addView(scroll, LayoutParams(0, -1, 1f)); addView(columns, LayoutParams(-1, 0, 1f))
        scroll.setOnScrollChangeListener { _, _, _, _, _ -> if (!applying && laidOut) rememberPosition() }
        picker.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!pickerUpdating && laidOut && position != selected) navigate(sections[position].key)
            }
        }
        viewTreeObserver.addOnGlobalLayoutListener {
            if (sections.isNotEmpty() && (!laidOut || restoredWidth != width)) {
                applying = true
                val target = sections.firstOrNull { it.key == anchor } ?: sections.first()
                scroll.scrollTo(0, if (atEnd) maxScroll() else (target.block.top + offset).coerceAtLeast(0))
                applying = false; laidOut = true; restoredWidth=width; rememberPosition()
            }
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val wide = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight >= style.dp(800)
        index.visibility = if (wide) VISIBLE else GONE; divider.visibility = index.visibility
        picker.visibility = if (wide) GONE else VISIBLE
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    fun section(key: String, title: String): LinearLayout {
        val block = LinearLayout(host).apply { orientation = VERTICAL; tag = "settings/section/$key"; setPadding(0, 0, 0, style.dp(24)) }
        val heading = style.text(title, TaskProductVisualStyle.section, "settings/heading/$key").apply {
            androidx.core.view.ViewCompat.setAccessibilityHeading(this,true); isFocusable = true; setPadding(0, style.dp(8), 0, style.dp(8))
        }
        block.addView(heading); body.addView(block, LayoutParams(-1, -2))
        val button = HoyiUi.button(host, indexBody, title) { navigate(key) }.apply {
            asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            minimumHeight = style.dp(48); tag = "settings/index/$key"
            setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
        }
        sections += Section(key, title, block, button)
        pickerUpdating = true
        pickerLabels.add(title);pickerAdapter.notifyDataSetChanged()
        pickerUpdating = false
        return LinearLayout(host).apply { orientation = VERTICAL; block.addView(this, LayoutParams(-1, -2)) }
    }
    fun setStatus(key:String,status:String?) {
        val index=sections.indexOfFirst {it.key==key};if(index<0)return
        val value=status.orEmpty();if(badges[key]==value)return
        badges[key]=value
        val label=sections[index].title+if(value.isEmpty())"" else "\n$value"
        sections[index].button.text=label
        pickerLabels[index]=label;pickerAdapter.notifyDataSetChanged()
    }
    fun navigate(key: String) {
        anchor = key; offset = 0; atEnd = false
        scroll.post {
            val section = sections.firstOrNull { it.key == key } ?: return@post
            applying = true; scroll.scrollTo(0, section.block.top); applying = false
            rememberPosition()
            val heading=section.block.getChildAt(0)
            heading.requestFocus()
            if(host.getSystemService(android.view.accessibility.AccessibilityManager::class.java).isEnabled)
                heading.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS,null)
        }
    }
    private fun maxScroll() = (body.height - scroll.height).coerceAtLeast(0)
    private fun rememberPosition() {
        if (sections.isEmpty()) return
        atEnd = maxScroll() > 0 && scroll.scrollY >= maxScroll() - 2
        val active = if (atEnd) sections.lastIndex else sections.indexOfLast { it.block.top <= scroll.scrollY + style.dp(12) }.coerceAtLeast(0)
        anchor = sections[active].key; offset = scroll.scrollY - sections[active].block.top
        if (active != selected) {
            selected = active
            sections.forEachIndexed { i, section ->
                section.button.isSelected = i == active
                section.button.setTextColor(if (i == active) style.accent else style.muted)
                section.button.background = if (i == active) style.shape(style.selected) else null
            }
            pickerUpdating = true; picker.setSelection(active, false); pickerUpdating = false
        }
    }
    fun save(state: Bundle) {
        if (laidOut) rememberPosition()
        state.putString("settings.anchor", anchor); state.putInt("settings.offset", offset); state.putBoolean("settings.atEnd", atEnd)
    }
}
