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
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState) }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val body = beanPage(getString(R.string.beans_title), getString(R.string.beans_subtitle), navigation = BeanInventoryActivity::class.java)
        val inventory = beanInventory(body) ?: return
        HoyiUi.button(this, body, getString(R.string.beans_add), primary = true) {
            startActivity(Intent(this, BeanEntryActivity::class.java))
        }
        val batches = inventory.batches()
        if (batches.isEmpty()) {
            val empty = HoyiUi.card(this, body, getString(R.string.beans_empty))
            HoyiUi.label(this, empty, getString(R.string.beans_empty_hint), 16, muted = true)
        }
        batches.forEach { item ->
            val card = HoyiUi.card(this, body, item.batch.bean.name)
            HoyiUi.label(this, card, getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)), 25, true)
            HoyiUi.label(this, card, getString(R.string.beans_batch_id, item.batch.id), 13, muted = true)
            HoyiUi.button(this, card, getString(R.string.beans_batch_title)) {
                startActivity(Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
        }
        if (batches.isNotEmpty()) HoyiUi.button(this, body, getString(R.string.beans_ledger)) {
            startActivity(Intent(this, BeanLedgerActivity::class.java))
        }
    }
}

/** Centered scrollable body accommodates tablets, font scaling and keyboard insets. */
internal fun ThemedActivity.beanPage(title: String, subtitle: String? = null,
    navigation: Class<out android.app.Activity>? = null): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
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
    val scroll = ScrollView(this).apply { isFillViewport = true }
    root.addView(scroll, if (navigation == null) LinearLayout.LayoutParams(-1, -1)
        else LinearLayout.LayoutParams(-1, 0, 1f))
    val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
    scroll.addView(container)
    val body = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(HoyiUi.dp(this@beanPage, 20), HoyiUi.dp(this@beanPage, 20), HoyiUi.dp(this@beanPage, 20), HoyiUi.dp(this@beanPage, 28))
    }
    container.addView(body, LinearLayout.LayoutParams(if (HoyiUi.wide(this)) HoyiUi.dp(this, 640) else -1, -2))
    HoyiUi.header(this, body, title, subtitle, back = navigation == null)
    navigation?.let { HoyiUi.navigation(this, root, it) }
    setContentView(root)
    return body
}

internal fun ThemedActivity.beanInventory(body: LinearLayout): BeanInventory? {
    val result = (application as MobileApplication).beanInventoryResult
    return result.getOrNull().also {
        if (it == null) HoyiUi.label(this, HoyiUi.card(this, body), getString(R.string.beans_unavailable), 17, true)
    }
}

internal fun ThemedActivity.beanInput(parent: LinearLayout, label: Int, numeric: Boolean = false): EditText {
    val title = HoyiUi.label(this, parent, getString(label), 15, muted = true).apply {
        setPadding(0, HoyiUi.dp(this@beanInput, 18), 0, HoyiUi.dp(this@beanInput, 6))
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
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
}

internal fun ThemedActivity.beanError(parent: LinearLayout): TextView = HoyiUi.label(this, parent, "", 15).apply {
    setTextColor(getColor(R.color.mobile_danger))
    accessibilityLiveRegion = android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE
    setPadding(0, HoyiUi.dp(this@beanError, 12), 0, 0)
}
