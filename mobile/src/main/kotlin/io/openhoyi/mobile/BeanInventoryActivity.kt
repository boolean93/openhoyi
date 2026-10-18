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
        if (batches.isEmpty()) {
            val dock=TaskActionDock(this,edgeInsetDp=0)
            beanActions(body).addView(dock,LinearLayout.LayoutParams(-1,-2))
            HoyiUi.button(this,dock,getString(R.string.beans_add),primary=true) {
                startActivity(Intent(this,BeanEntryActivity::class.java))
            }
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
        val tools=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.END; isBaselineAligned=false }
        body.addView(tools,LinearLayout.LayoutParams(-1,-2))
        inventoryLink(tools,getString(R.string.beans_add)) {
            startActivity(Intent(this,BeanEntryActivity::class.java))
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
        inventoryLink(tools,getString(R.string.beans_ledger)) {
            startActivity(Intent(this,BeanLedgerActivity::class.java))
        }.layoutParams=LinearLayout.LayoutParams(0,-2,1f)
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
        val primaryDock=TaskActionDock(this,edgeInsetDp=0,wideMinimumDp=360)
        panes.detailBody.addView(primaryDock,LinearLayout.LayoutParams(-1,-2))
        val rowButtons = mutableMapOf<String, android.widget.Button>()
        fun showSelected() {
            val item = batches.single { it.batch.id == selectedBatchId }
            metadata.removeAllViews(); actions.removeAllViews(); primaryDock.removeAllViews()
            HoyiUi.label(this, metadata, item.batch.bean.name, 24, true).apply {
                setPadding(0, HoyiUi.dp(this@BeanInventoryActivity, 8), 0, HoyiUi.dp(this@BeanInventoryActivity, 16))
            }
            HoyiUi.label(this, metadata, getString(R.string.beans_balance_label), 14, muted = true)
            HoyiUi.label(this, metadata, getString(R.string.beans_quantity_g, BeanQuantity.formatGrams(item.balanceMg)), 40, true).apply {
                typeface = TaskTypography.face(this@BeanInventoryActivity, 700)
                text = android.text.SpannableString(text).apply {
                    Regex("g$").find(toString())?.let { unit ->
                        setSpan(android.text.style.RelativeSizeSpan(0.55f), unit.range.first, length,
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
                setPadding(0, HoyiUi.dp(this@BeanInventoryActivity, 8), 0, HoyiUi.dp(this@BeanInventoryActivity, 16))
                contentDescription = getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg))
            }
            HoyiUi.label(this, metadata, getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)), 14, muted = true)
            HoyiUi.label(this, metadata, getString(R.string.beans_initial, BeanQuantity.formatGrams(item.batch.initialMg)), TaskVisualTokens.BODY_SP)
            fun divider() = metadata.addView(android.view.View(this).apply {
                setBackgroundColor(getColor(R.color.mobile_border))
            }, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)).apply {
                topMargin = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.GROUP_GAP_DP)
                bottomMargin = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.GROUP_GAP_DP)
            })
            divider()
            val dates = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false }
            metadata.addView(dates, LinearLayout.LayoutParams(-1, -2))
            listOf(R.string.beans_purchased to item.batch.purchasedOn, R.string.beans_roasted to item.batch.roastedOn,
                R.string.beans_opened to item.batch.openedOn).forEach { (label, date) ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(0, 0, HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP), 0)
                }
                dates.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
                HoyiUi.label(this, cell, getString(label), TaskVisualTokens.SUPPORTING_SP, muted = true)
                HoyiUi.label(this, cell, date?.toString() ?: getString(R.string.beans_not_set), TaskVisualTokens.BODY_SP)
            }
            divider()
            HoyiUi.label(this, metadata, getString(R.string.batch_recent_events), TaskVisualTokens.SECTION_SP, true)
            val events = inventory.events().asReversed().filter { it.batchId == item.batch.id }.take(3)
            if (events.isEmpty()) HoyiUi.label(this, metadata, getString(R.string.beans_ledger_empty), TaskVisualTokens.BODY_SP, muted = true)
            events.forEach { event ->
                val kind = getString(when (event.kind) {
                    io.openhoyi.bean.InventoryEventKind.ADD -> R.string.beans_event_add
                    io.openhoyi.bean.InventoryEventKind.CONSUME -> R.string.beans_event_consume
                    io.openhoyi.bean.InventoryEventKind.ADJUST -> R.string.beans_event_adjust
                })
                val delta = (if (event.deltaMg > 0) "+" else "") + BeanQuantity.formatGrams(event.deltaMg) + " g"
                val reason = event.reason ?: "—"
                val line = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
                    minimumHeight = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.TOUCH_TARGET_DP)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP),
                        0, HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP))
                    isFocusable = true
                    background = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                        null, HoyiUi.shape(this@BeanInventoryActivity, android.R.color.white, 0))
                    contentDescription = listOf(kind, delta, reason).joinToString(" · ")
                    setOnClickListener { beanDetails(getString(R.string.beans_ledger_title),
                        listOf(kind, delta, reason, getString(R.string.beans_event_balance,
                            BeanQuantity.formatGrams(event.balanceMg))).joinToString("\n")) }
                }
                metadata.addView(line, LinearLayout.LayoutParams(-1, -2))
                listOf(kind, delta, reason).forEachIndexed { index, value ->
                    HoyiUi.label(this, line, value, TaskVisualTokens.BODY_SP).apply {
                        layoutParams = LinearLayout.LayoutParams(0, -2, if (index == 2) 2f else 1f).apply {
                            marginEnd = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP)
                        }
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details"
                    }
                }
            }
            inventoryLink(actions, getString(R.string.beans_batch_title)) {
                startActivity(Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
            inventoryLink(actions, getString(R.string.beans_batch_ledger)) {
                startActivity(Intent(this, BeanLedgerActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }
            HoyiUi.button(this, primaryDock, getString(R.string.dose_title), primary = true) {
                startActivity(Intent(this, BeanPreparationActivity::class.java).putExtra(BeanPreparationActivity.EXTRA_BATCH_ID, item.batch.id))
            }
            rowButtons.forEach { (id, button) ->
                button.isActivated = id == selectedBatchId
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                    HoyiUi.shape(this@BeanInventoryActivity, if (id == selectedBatchId) R.color.mobile_accent_soft else android.R.color.transparent, 0), null)
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
    }
    private fun inventoryLink(parent:LinearLayout,value:String,action:()->Unit)=
        HoyiUi.button(this,parent,value,action=action).apply {
            gravity=Gravity.START or Gravity.CENTER_VERTICAL
            setTextColor(android.content.res.ColorStateList(
                arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),
                intArrayOf(getColor(R.color.mobile_muted),getColor(R.color.mobile_accent))))
            val states=android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_focused),HoyiUi.shape(this@BeanInventoryActivity,android.R.color.transparent,8,R.color.mobile_accent))
                addState(intArrayOf(),HoyiUi.shape(this@BeanInventoryActivity,android.R.color.transparent,8))
            }
            background=android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                states,HoyiUi.shape(this@BeanInventoryActivity,android.R.color.white,8))
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
            // This is a square icon target, not a text action with 16dp side insets.
            setPadding(HoyiUi.dp(this@beanPage, 8), HoyiUi.dp(this@beanPage, 8),
                HoyiUi.dp(this@beanPage, 8), HoyiUi.dp(this@beanPage, 8))
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

internal fun ThemedActivity.beanInput(parent: LinearLayout, label: Int, numeric: Boolean = false, emphasizeLabel: Boolean = false): EditText {
    val target = beanFieldCell(parent)
    val title = HoyiUi.label(this, target, getString(label), if (emphasizeLabel) 16 else 15, bold = emphasizeLabel, muted = !emphasizeLabel).apply {
        setPadding(0, HoyiUi.dp(this@beanInput, 8), 0, HoyiUi.dp(this@beanInput, 6))
    }
    return EditText(this).apply {
        id = android.view.View.generateViewId()
        title.labelFor = id
        textSize = 18f
        typeface = TaskTypography.face(this@beanInput, 400)
        includeFontPadding = false
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
