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
    private var pendingListRestore = false
    private var selectedBatchId: String? = null
    private var detailRequested = false
    private var collection: TaskCollectionLayout? = null
    private var compact = true
    private var backCallback: android.window.OnBackInvokedCallback? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedScrollY = savedInstanceState?.getInt("listScrollY") ?: 0
        selectedBatchId = savedInstanceState?.getString("selectedBatchId")
        detailRequested = savedInstanceState?.getBoolean("detailRequested") ?: false
    }
    private fun rememberList() {
        listScroll?.takeIf { !pendingListRestore && it.isShown && !it.isLayoutRequested }?.let { savedScrollY = it.scrollY }
    }
    override fun onStop() { rememberList(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        rememberList()
        outState.putInt("listScrollY", savedScrollY)
        outState.putString("selectedBatchId", selectedBatchId)
        outState.putBoolean("detailRequested", collection?.detailRequested ?: detailRequested)
        super.onSaveInstanceState(outState)
    }
    override fun onStart() { super.onStart(); render() }
    override fun onDestroy() { updateBack(false); super.onDestroy() }
    private fun restoreListWhenReady() {
        val scroll = listScroll ?: return
        if (pendingListRestore && scroll.isShown && scroll.height > 0 && scroll.getChildAt(0).height > 0) {
            scroll.scrollTo(0, savedScrollY)
            pendingListRestore = false
        }
    }
    private fun showList() {
        detailRequested = false; collection?.detailRequested = false; updateBack(false)
        listScroll?.post { restoreListWhenReady() }
    }
    private fun updateBack(active: Boolean) {
        if (Build.VERSION.SDK_INT < 33) return
        if (active && backCallback == null) {
            backCallback = android.window.OnBackInvokedCallback { showList() }.also {
                onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, it)
            }
        } else if (!active) {
            backCallback?.let { onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it) }
            backCallback = null
        }
    }
    @Deprecated("Platform back callback")
    override fun onBackPressed() {
        if (compact && collection?.detailRequested == true) showList() else super.onBackPressed()
    }
    private fun render() {
        rememberList()
        detailRequested = collection?.detailRequested ?: detailRequested
        collection = null; listScroll = null
        updateBack(false)
        val body = beanPage(getString(R.string.beans_title), getString(R.string.beans_subtitle), navigation = BeanInventoryActivity::class.java)
        val inventory = beanInventory(body) ?: return
        val batches = inventory.batches()
        HoyiUi.button(this, beanActions(body), getString(R.string.beans_add), primary = batches.isEmpty()) {
            startActivity(Intent(this, BeanEntryActivity::class.java))
        }
        if (batches.isEmpty()) {
            collection = null; selectedBatchId = null; detailRequested = false
            val list = beanList(body)
            listScroll = list.parent as ScrollView
            val empty = HoyiUi.card(this, list, getString(R.string.beans_empty))
            HoyiUi.label(this, empty, getString(R.string.beans_empty_hint), 16, muted = true)
            return
        }
        if (batches.none { it.batch.id == selectedBatchId }) {
            selectedBatchId = batches.first().batch.id
            detailRequested = false
        }
        val panes = TaskCollectionLayout(this).also { collection = it }
        body.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        val list = beanList(panes.browserBody)
        listScroll = list.parent as ScrollView
        pendingListRestore = true
        listScroll?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> restoreListWhenReady() }
        listScroll?.post { restoreListWhenReady() }
        val back = HoyiUi.button(this, panes.detailBody, getString(R.string.beans_back_to_list)) { showList() }
        val metadata = beanList(panes.detailBody).apply { tag = "inventory-batch-metadata" }
        val actions = BeanActionRows(this)
        panes.detailBody.addView(actions, LinearLayout.LayoutParams(-1, -2))
        val rowButtons = mutableMapOf<String, android.widget.Button>()
        fun showSelected() {
            val item = batches.single { it.batch.id == selectedBatchId }
            metadata.removeAllViews(); actions.removeAllViews()
            HoyiUi.label(this, metadata, item.batch.bean.name, 21, true)
            HoyiUi.label(this, metadata, getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)), 30, true)
            HoyiUi.label(this, metadata, getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)), 14, muted = true)
            HoyiUi.label(this, metadata, getString(R.string.beans_initial, BeanQuantity.formatGrams(item.batch.initialMg)), 15)
            listOf(R.string.beans_purchased to item.batch.purchasedOn, R.string.beans_roasted to item.batch.roastedOn,
                R.string.beans_opened to item.batch.openedOn).forEach { (label, date) ->
                HoyiUi.label(this, metadata, getString(R.string.beans_date_value, getString(label), date?.toString() ?: getString(R.string.beans_not_set)), 15)
            }
            HoyiUi.button(this, actions, getString(R.string.beans_batch_title)) {
                startActivity(Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
            HoyiUi.button(this, actions, getString(R.string.beans_batch_ledger)) {
                startActivity(Intent(this, BeanLedgerActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
            HoyiUi.button(this, actions, getString(R.string.dose_title), primary = true) {
                startActivity(Intent(this, BeanPreparationActivity::class.java).putExtra(BeanPreparationActivity.EXTRA_BATCH_ID, item.batch.id))
            }
            rowButtons.forEach { (id, button) ->
                button.isActivated = id == selectedBatchId
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                    HoyiUi.shape(this@BeanInventoryActivity, if (id == selectedBatchId) R.color.mobile_accent_soft else R.color.mobile_surface, 0), null)
            }
        }
        batches.forEach { item ->
            val description = item.batch.bean.name + "\n" + getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)) +
                " · " + getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id))
            rowButtons[item.batch.id] = HoyiUi.button(this, list, description) {
                rememberList(); selectedBatchId = item.batch.id; showSelected(); detailRequested = true; panes.detailRequested = true
            }.apply {
                tag = "inventory-batch-${item.batch.id}"
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textSize = 17f
                text = android.text.SpannableString(description).apply {
                    setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, item.batch.bean.name.length, 0)
                    setSpan(android.text.style.RelativeSizeSpan(14f / 17f), item.batch.bean.name.length, description.length, 0)
                }
                setPadding(HoyiUi.dp(this@BeanInventoryActivity, 12), HoyiUi.dp(this@BeanInventoryActivity, 10),
                    HoyiUi.dp(this@BeanInventoryActivity, 12), HoyiUi.dp(this@BeanInventoryActivity, 10))
                minimumHeight = HoyiUi.dp(this@BeanInventoryActivity, 64)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = HoyiUi.dp(this@BeanInventoryActivity, 1) }
            }
        }
        panes.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            back.visibility = if (compact) android.view.View.VISIBLE else android.view.View.GONE
            updateBack(compact && detailVisible)
        }
        showSelected()
        panes.detailRequested = detailRequested
        HoyiUi.button(this, beanActions(body), getString(R.string.beans_ledger)) {
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
