package io.openhoyi.mobile

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.WindowInsets
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast

/** Browsing and shortcut assignment have no BLE side effects. */
class CurveActivity : Activity() {
    private lateinit var details: TextView
    private lateinit var select: Button
    private lateinit var assignPreset: Button
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var search: EditText
    private var category = "全部"
    private var visibleItems = emptyList<CurveLibraryItem>()
    private var selected: CurveLibraryItem? = null
    private val library get() = (application as MobileApplication).curves

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(244, 241, 235))
            setOnApplyWindowInsetsListener { view, insets ->
                val bars = if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(dp(20) + bars[0], dp(12) + bars[1], dp(20) + bars[2], dp(12) + bars[3])
                insets
            }
        }
        setContentView(root)
        label(root, "曲线库", 28, true)
        label(root, "3 条采集曲线 · 100 条旧版工厂曲线 · 5 个快捷槽位", 14)
        search = EditText(this).apply {
            hint = "搜索曲线名称"
            setSingleLine(true)
        }
        root.addView(search)
        val categories = listOf("全部", "已采集验证", "深烘", "中烘", "浅烘", "超萃")
        val filter = Spinner(this)
        filter.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categories)
        root.addView(filter)
        val list = ListView(this).apply { dividerHeight = dp(1) }
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf<String>())
        list.adapter = adapter
        root.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        list.setOnItemClickListener { _, _, position, _ -> show(visibleItems[position]) }
        val detailScroll = ScrollView(this)
        details = TextView(this).apply {
            textSize = 15f; setTextColor(Color.rgb(32, 38, 42)); setPadding(dp(12), dp(8), dp(12), dp(8))
            text = "点选曲线查看详情"
        }
        detailScroll.addView(details)
        root.addView(detailScroll, LinearLayout.LayoutParams(-1, dp(180)))
        select = Button(this).apply {
            text = "设为当前曲线"; isEnabled = false
            setOnClickListener {
                selected?.let { item ->
                    getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
                    finish()
                }
            }
        }
        root.addView(select)
        assignPreset = Button(this).apply {
            text = "放入快捷槽位"; isEnabled = false
            setOnClickListener {
                val item = selected?.takeIf { it.factoryCurve != null && library.canStart(it) } ?: return@setOnClickListener
                AlertDialog.Builder(this@CurveActivity).setTitle("选择快捷槽位")
                    .setItems(arrayOf("槽位 1", "槽位 2", "槽位 3", "槽位 4", "槽位 5")) { _, index ->
                        val slot = index + 1
                        getSharedPreferences("presets", MODE_PRIVATE).edit()
                            .putString(PresetSlots.key(slot), item.id).apply()
                        Toast.makeText(this@CurveActivity, "已将${item.name}放入槽位 $slot", Toast.LENGTH_SHORT).show()
                    }.show()
            }
        }
        root.addView(assignPreset)
        filter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                category = categories[position]
                refreshList()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refreshList()
            override fun afterTextChanged(s: Editable?) = Unit
        })
        refreshList()
        val initialId = savedInstanceState?.getString("selected")
            ?: getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        initialId?.let(library::find)?.let(::show)
    }

    private fun show(item: CurveLibraryItem) {
        selected = item
        details.text = "${item.name}\n${item.category}\n\n${item.details}"
        select.isEnabled = library.canStart(item)
        select.text = if (!library.canStart(item)) "设为当前曲线（不可萃取）" else "设为当前曲线"
        assignPreset.isEnabled = item.factoryCurve != null && library.canStart(item)
    }

    private fun refreshList() {
        if (!::adapter.isInitialized || !::search.isInitialized) return
        visibleItems = CurveSearch.filter(library.items, category, search.text.toString())
        adapter.clear()
        adapter.addAll(visibleItems.map { "${it.name}  ·  ${it.category}${if (!library.canStart(it)) " · 仅浏览" else ""}" })
        if (selected != null && selected !in visibleItems) {
            selected = null
            details.text = "点选曲线查看详情"
            select.isEnabled = false
            assignPreset.isEnabled = false
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected", selected?.id)
        super.onSaveInstanceState(outState)
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun label(parent: LinearLayout, value: String, size: Int, bold: Boolean = false) {
        parent.addView(TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(Color.rgb(32, 38, 42))
            setPadding(0, dp(4), 0, dp(4))
            if (bold) setTypeface(null, Typeface.BOLD)
        })
    }
}
