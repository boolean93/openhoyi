package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.bean.BeanInventory

/** Local inventory only. Does not own any machine/scale session. */
class BeanInventoryActivity : ThemedActivity() {
    private var listScroll: ScrollView? = null
    private var savedScrollY = 0
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); savedScrollY = savedInstanceState?.getInt("listScrollY") ?: 0 }
    override fun onStop() { savedScrollY = listScroll?.scrollY ?: savedScrollY; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("listScrollY", listScroll?.scrollY ?: savedScrollY)
    }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val body = beanPage(getString(R.string.beans_title), getString(R.string.beans_subtitle), navigation = BeanInventoryActivity::class.java)
        val inventory = beanInventory(body) ?: return
        HoyiUi.button(this, beanActions(body), getString(R.string.beans_add), primary = true) {
            startActivity(Intent(this, BeanEntryActivity::class.java))
        }
        val batches = inventory.batches()
        val list = beanList(body)
        listScroll = list.parent as ScrollView
        listScroll?.post { listScroll?.scrollTo(0, savedScrollY) }
        if (batches.isEmpty()) {
            val empty = HoyiUi.card(this, list, getString(R.string.beans_empty))
            HoyiUi.label(this, empty, getString(R.string.beans_empty_hint), 16, muted = true)
        }
        batches.forEach { item ->
            val card = HoyiUi.card(this, list, item.batch.bean.name)
            HoyiUi.label(this, card, getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)), 25, true)
            HoyiUi.label(this, card, getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)), 13, muted = true)
            HoyiUi.button(this, card, getString(R.string.beans_batch_title)) {
                startActivity(Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
        }
        if (batches.isNotEmpty()) HoyiUi.button(this, beanActions(body), getString(R.string.beans_ledger)) {
            startActivity(Intent(this, BeanLedgerActivity::class.java))
        }
    }
}

/** Fixed header, elastic workspace and fixed actions; never wraps the whole page in a ScrollView. */
internal fun ThemedActivity.beanPage(title: String, subtitle: String? = null,
    navigation: Class<out android.app.Activity>? = null): LinearLayout {
    window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        isFocusableInTouchMode = true
        setBackgroundColor(getColor(R.color.mobile_background))
        setOnApplyWindowInsetsListener { view, insets ->
            val area = if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                intArrayOf(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                @Suppress("DEPRECATION")
                intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            view.setPadding(area[0], area[1], area[2], area[3]); insets
        }
    }
    val shell = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(HoyiUi.dp(this@beanPage, 12), HoyiUi.dp(this@beanPage, 8), HoyiUi.dp(this@beanPage, 12), HoyiUi.dp(this@beanPage, 8))
    }
    root.addView(shell, LinearLayout.LayoutParams(-1, 0, 1f))
    val header = HoyiUi.header(this, shell, title, back = navigation == null)
    // Full explanations are available without letting long translations consume the workspace.
    subtitle?.let { description ->
        header.contentDescription = "$title. $description"
        HoyiUi.button(this, header, "ⓘ") { beanDetails(title, description) }.apply {
            layoutParams = LinearLayout.LayoutParams(HoyiUi.dp(this@beanPage, 48), -2)
            contentDescription = getString(R.string.ui_full_details)
        }
    }
    val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    shell.addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
    val actions = BeanActionRows(this)
    shell.addView(actions, LinearLayout.LayoutParams(-1, -2))
    body.tag = BeanPageRegions(actions)
    HoyiUi.navigation(this, root, navigation ?: javaClass)
    setContentView(root)
    return body
}

internal fun ThemedActivity.beanInventory(body: LinearLayout): BeanInventory? {
    val result = (application as MobileApplication).beanInventoryResult
    return result.getOrNull().also {
        if (it == null) HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.beans_unavailable), 17, true)
    }
}

/** Display-only ordinal from the append-only inventory order; durable IDs remain internal. */
internal fun ThemedActivity.beanBatchNumber(inventory: BeanInventory, batchId: String): String {
    val index = inventory.batches().indexOfFirst { it.batch.id == batchId }
    return if (index < 0) getString(R.string.beans_not_set)
    else java.text.NumberFormat.getIntegerInstance(resources.configuration.locales[0]).format(index + 1)
}

internal fun ThemedActivity.beanInput(parent: LinearLayout, label: Int, numeric: Boolean = false): EditText {
    val target = beanFieldCell(parent)
    val title = HoyiUi.label(this, target, getString(label), 15, muted = true).apply {
        setPadding(0, HoyiUi.dp(this@beanInput, 8), 0, HoyiUi.dp(this@beanInput, 6))
    }
    return EditText(this).apply {
        id = android.view.View.generateViewId()
        title.labelFor = id
        textSize = 18f
        setTextColor(getColor(R.color.mobile_text))
        setHintTextColor(getColor(R.color.mobile_muted))
        inputType = if (numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL else InputType.TYPE_CLASS_TEXT
        setSingleLine(true)
        minimumHeight = HoyiUi.dp(this@beanInput, 52)
        setPadding(HoyiUi.dp(this@beanInput, 12), HoyiUi.dp(this@beanInput, 8), HoyiUi.dp(this@beanInput, 12), HoyiUi.dp(this@beanInput, 8))
        background = HoyiUi.shape(this@beanInput, R.color.mobile_surface, 10, R.color.mobile_border)
        target.addView(this, LinearLayout.LayoutParams(-1, -2))
        beanCompactEditor(this, getString(label))
    }
}

internal fun ThemedActivity.beanError(parent: LinearLayout): TextView = HoyiUi.label(this, beanActions(parent), "", 15).apply {
    setTextColor(getColor(R.color.mobile_danger))
    accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    setPadding(0, HoyiUi.dp(this@beanError, 6), 0, 0)
    visibility = android.view.View.GONE
    addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            visibility = if (s.isNullOrEmpty()) android.view.View.GONE else android.view.View.VISIBLE
        }
        override fun afterTextChanged(s: android.text.Editable?) = Unit
    })
}
