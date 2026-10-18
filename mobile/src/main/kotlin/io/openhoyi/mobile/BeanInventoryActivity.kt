package io.openhoyi.mobile

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.openhoyi.bean.BeanInventory

/** Local inventory only. Does not own any machine/scale session. */
class BeanInventoryActivity : ThemedActivity() {
    private val productStyle by lazy { TaskProductVisualStyle(this) }
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
        val body = productStyle.page(this, getString(R.string.beans_title), getString(R.string.beans_subtitle), navigation = BeanInventoryActivity::class.java)
        val inventory = beanInventory(body) ?: return
        val batches = inventory.batches()
        val header = (body.tag as BeanPageRegions).header.apply {
            tag = "product/inventory/header"; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
            foreground = inventoryRules(bottom = true)
        }
        (header.getChildAt(0) as? LinearLayout)?.let { oldWords ->
            val title = oldWords.getChildAt(0) as TextView
            val wordsParams = oldWords.layoutParams
            oldWords.removeView(title)
            header.removeView(oldWords)
            val count = batches.map { it.batch.bean.id }.distinct().size
            val total = BeanQuantity.formatGrams(batches.sumOf { it.balanceMg })
            val summary = productStyle.text("$count · $total g", TaskProductVisualStyle.auxiliary,
                "product/inventory/header-summary", true)
            title.tag = "product/inventory/header-title"
            val words = object : LinearLayout(this) {
                init {
                    tag = "product/inventory/header-words"; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
                    addView(title, LayoutParams(-2, -2))
                    addView(summary, LayoutParams(-2, -2).apply { marginStart = productStyle.dp(16) })
                }
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    title.measure(natural, natural); summary.measure(natural, natural)
                    val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
                    val stacked = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                        title.measuredWidth + productStyle.dp(16) + summary.measuredWidth > available
                    orientation = if (stacked) VERTICAL else HORIZONTAL
                    // The title came from a vertical container whose default child
                    // width was MATCH_PARENT. Never carry that width into a row.
                    (title.layoutParams as LayoutParams).apply {
                        width = if (stacked) -1 else -2; height = -2; weight = 0f
                    }
                    (summary.layoutParams as LayoutParams).apply {
                        width = -2; height = -2; weight = 0f
                        marginStart = if (stacked) 0 else productStyle.dp(16)
                        topMargin = if (stacked) productStyle.dp(8) else 0
                    }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
            header.addView(words, 0, wordsParams)
        }
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
        val headerTools = resources.configuration.screenWidthDp >= 1000 && resources.configuration.fontScale <= 1.1f
        val toolsParent = if (headerTools) (body.tag as BeanPageRegions).header else body
        toolsParent.addView(tools, LinearLayout.LayoutParams(if (headerTools) -2 else -1, -2))
        inventoryLink(tools,getString(R.string.beans_add)) {
            startActivity(Intent(this,BeanEntryActivity::class.java))
        }.apply {
            maxWidth = HoyiUi.dp(this@BeanInventoryActivity, 200)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP)
            }
        }
        inventoryLink(tools,getString(R.string.beans_ledger)) {
            startActivity(Intent(this,BeanLedgerActivity::class.java))
        }.apply {
            maxWidth = HoyiUi.dp(this@BeanInventoryActivity, 200)
            layoutParams = LinearLayout.LayoutParams(-2, -2).apply {
                marginStart = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP)
            }
        }
        val panes = TaskCollectionLayout(this).also { collection = it }
        panes.foreground = inventoryRules(vertical = {
            if (panes.orientation == LinearLayout.HORIZONTAL && panes.browserBody.isShown && panes.detailBody.isShown) {
                val browser = panes.browserBody; val detail = panes.detailBody
                listOf(if (browser.left < detail.left) (browser.right + detail.left) / 2f else (detail.right + browser.left) / 2f)
            } else emptyList()
        })
        body.addView(panes, LinearLayout.LayoutParams(-1, 0, 1f))
        productStyle.add(panes.browserBody, productStyle.text(getString(R.string.inventory_visual_in_stock), TaskProductVisualStyle.section,
            "product/inventory/list-title"))
        val list = beanList(panes.browserBody)
        listScroll = list.parent as ScrollView
        (listScroll!!.layoutParams as LinearLayout.LayoutParams).topMargin = productStyle.dp(14)
        pendingListRestore = true
        listScroll?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> restoreListWhenReady() }
        listScroll?.post { restoreListWhenReady() }
        val back = productStyle.button(HoyiUi.button(this, panes.detailBody, getString(R.string.beans_back_to_list)) { showList() })
        val metadata = beanList(panes.detailBody).apply { tag = "inventory-batch-metadata" }
        val actionRegion = object : LinearLayout(this) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val inline = View.MeasureSpec.getSize(widthMeasureSpec) >= productStyle.dp(480)
                orientation = if (inline) HORIZONTAL else VERTICAL
                for (index in 0 until childCount) (getChildAt(index).layoutParams as LayoutParams).apply {
                    width = if (inline) 0 else -1; weight = if (inline) 1f else 0f
                    marginStart = if (inline && index > 0) productStyle.dp(8) else 0
                    topMargin = if (!inline && index > 0) productStyle.dp(8) else 0
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false; tag = "product/inventory/actions" }
        val dock = productStyle.column("product/inventory/dock")
        panes.detailBody.addView(dock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = productStyle.dp(8) })
        dock.addView(actionRegion, LinearLayout.LayoutParams(-1, -2))
        val consumptionHint = productStyle.row("product/inventory/consumption-hint").apply { gravity = Gravity.TOP }
        consumptionHint.addView(HomeVisualIcon(this, "info", productStyle.muted), LinearLayout.LayoutParams(productStyle.dp(24), productStyle.dp(24)))
        consumptionHint.addView(productStyle.text(getString(R.string.inventory_visual_consumption_hint), TaskProductVisualStyle.auxiliary,
            "product/inventory/consumption-hint/text", true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = productStyle.dp(8) })
        productStyle.add(dock, consumptionHint, 8)
        val rowButtons = mutableMapOf<String, View>()
        fun showSelected() {
            val item = batches.single { it.batch.id == selectedBatchId }
            metadata.removeAllViews(); actionRegion.removeAllViews()
            val beanName = productStyle.text(item.batch.bean.name, TaskProductVisualStyle.title, "product/inventory/bean-name")
            val badge = productStyle.text(getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)),
                TaskProductVisualStyle.auxiliary, "product/inventory/batch-badge", true).apply {
                setPadding(productStyle.dp(8), productStyle.dp(4), productStyle.dp(8), productStyle.dp(4))
                background = productStyle.shape(productStyle.disabled)
            }
            val name = object : LinearLayout(this) {
                init {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
                    tag = "product/inventory/name"
                    addView(beanName, LayoutParams(-2, -2))
                    addView(badge, LayoutParams(-2, -2).apply { marginStart = productStyle.dp(16) })
                }
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    beanName.measure(natural, natural); badge.measure(natural, natural)
                    val available = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
                    val fits = View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED ||
                        beanName.measuredWidth + productStyle.dp(16) + badge.measuredWidth <= available
                    (beanName.layoutParams as LayoutParams).apply { width = if (fits) -2 else 0; weight = if (fits) 0f else 1f }
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
            productStyle.add(metadata, name)
            val balance = productStyle.row("product/inventory/balance-summary").apply { gravity = Gravity.CENTER_VERTICAL }
            val quantity = productStyle.quantity(BeanQuantity.formatGrams(item.balanceMg), "g", TaskProductVisualStyle.largeValue,
                "product/inventory/balance").apply {
                contentDescription = getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg))
            }
            balance.addView(quantity, LinearLayout.LayoutParams(0, -2, 1f))
            val initial = productStyle.column("product/inventory/initial")
            productStyle.add(initial, productStyle.text(getString(R.string.beans_balance_label), TaskProductVisualStyle.auxiliary, secondary = true))
            productStyle.add(initial, productStyle.text(getString(R.string.beans_initial, BeanQuantity.formatGrams(item.batch.initialMg)), TaskProductVisualStyle.body))
            balance.addView(initial, LinearLayout.LayoutParams(-2, -2).apply { marginStart = productStyle.dp(16) })
            productStyle.add(metadata, balance, 8)
            val ratio = if (item.batch.initialMg > 0) item.balanceMg.toDouble() / item.batch.initialMg else null
            val progress = productStyle.row("product/inventory/ratio").apply { gravity = Gravity.CENTER_VERTICAL }
            if (ratio != null) progress.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 1000; this.progress = (ratio.coerceIn(0.0, 1.0) * max).toInt()
                fun track(color: Int) = productStyle.shape(color, radius = 12)
                progressDrawable = android.graphics.drawable.LayerDrawable(arrayOf(track(productStyle.disabled),
                    android.graphics.drawable.ScaleDrawable(track(productStyle.primary), Gravity.START, 1f, -1f))).apply {
                    setId(0, android.R.id.background); setId(1, android.R.id.progress)
                }
                setPadding(0, 0, 0, 0); importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(0, productStyle.dp(8), 1f).apply { marginEnd = productStyle.dp(16) })
            progress.addView(productStyle.text(ratio?.let { java.text.NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(it) }
                ?: "—", TaskProductVisualStyle.auxiliary, "product/inventory/ratio-value", true).apply {
                contentDescription = getString(R.string.batch_remaining_ratio) + " " + text
            })
            productStyle.add(metadata, progress)
            productStyle.divider(metadata, 8, 8)
            val dates = productStyle.row("product/inventory/dates")
            listOf(R.string.beans_purchased to item.batch.purchasedOn, R.string.beans_roasted to item.batch.roastedOn,
                R.string.beans_opened to item.batch.openedOn).forEachIndexed { index, (label, date) ->
                val cell = productStyle.column("product/inventory/dates/$index").apply {
                    setPaddingRelative(if (index == 0) 0 else productStyle.dp(16), 0, productStyle.dp(16), 0)
                }
                productStyle.add(cell, productStyle.text(getString(label), TaskProductVisualStyle.auxiliary,
                    "product/inventory/dates/$index/label", true))
                productStyle.add(cell, productStyle.text(date?.toString() ?: getString(R.string.beans_not_set), TaskProductVisualStyle.body,
                    "product/inventory/dates/$index/value"), 8)
                dates.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            }
            dates.foreground = inventoryRules(vertical = {
                (1 until dates.childCount).map { index -> dates.getChildAt(index).let {
                    if (dates.layoutDirection == View.LAYOUT_DIRECTION_RTL) it.right.toFloat() else it.left.toFloat()
                } }
            })
            productStyle.add(metadata, dates)
            productStyle.divider(metadata, 8, 8)
            productStyle.add(metadata, productStyle.text(getString(R.string.batch_recent_events), TaskProductVisualStyle.section))
            val ledgerHeadings = productStyle.row("product/inventory/events/header").apply {
                setBackgroundColor(productStyle.disabled); foreground = inventoryRules(bottom = true)
            }
            metadata.addView(ledgerHeadings, LinearLayout.LayoutParams(-1, -2))
            listOf(R.string.ledger_type, R.string.ledger_change, R.string.beans_reason).forEachIndexed { index, label ->
                ledgerHeadings.addView(productStyle.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true),
                    LinearLayout.LayoutParams(0, -2, if (index == 2) 2f else 1f).apply { marginEnd = productStyle.dp(8) })
            }
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
                    tag = "product/inventory/events/row-${event.eventId}"
                    orientation = LinearLayout.HORIZONTAL; isBaselineAligned = false
                    minimumHeight = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.TOUCH_TARGET_DP)
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, 0, 0)
                    isFocusable = true
                    productStyle.clickable(this)
                    foreground = inventoryRules(bottom = true)
                    contentDescription = listOf(kind, delta, reason).joinToString(" · ")
                    setOnClickListener { beanDetails(getString(R.string.beans_ledger_title),
                        listOf(kind, delta, reason, getString(R.string.beans_event_balance,
                            BeanQuantity.formatGrams(event.balanceMg))).joinToString("\n")) }
                }
                metadata.addView(line, LinearLayout.LayoutParams(-1, -2))
                listOf(kind, delta, reason).forEachIndexed { index, value ->
                    productStyle.text(value, TaskProductVisualStyle.body,
                        "product/inventory/events/${event.eventId}/${listOf("type", "change", "reason")[index]}").apply {
                        line.addView(this)
                        layoutParams = LinearLayout.LayoutParams(0, -2, if (index == 2) 2f else 1f).apply {
                            marginEnd = HoyiUi.dp(this@BeanInventoryActivity, TaskVisualTokens.RELATED_GAP_DP)
                        }
                        maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                }
            }
            productStyle.action(actionRegion, getString(R.string.dose_title), "product/inventory/use", primary = true, top = 0) {
                startActivity(Intent(this, BeanPreparationActivity::class.java).putExtra(BeanPreparationActivity.EXTRA_BATCH_ID, item.batch.id))
            }.also { HomeVisualIcon.buttonIcon(it, "coffee") }
            productStyle.action(actionRegion, getString(R.string.beans_batch_title), "product/inventory/batch-details", outline = true, top = 0) {
                startActivity(Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }.also { HomeVisualIcon.buttonIcon(it, "chevron") }
            productStyle.action(actionRegion, getString(R.string.beans_batch_ledger), "product/inventory/batch-ledger", top = 0) {
                startActivity(Intent(this, BeanLedgerActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }.also { HomeVisualIcon.buttonIcon(it, "history") }
            rowButtons.forEach { (id, button) ->
                button.isActivated = id == selectedBatchId
                button.foreground = if (id == selectedBatchId) null else inventoryRules(bottom = true)
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(productStyle.selected),
                    productStyle.shape(if (id == selectedBatchId) productStyle.selected else android.graphics.Color.TRANSPARENT), null)
            }
        }
        batches.forEach { item ->
            val description = item.batch.bean.name + "\n" + getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)) +
                " · " + getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id))
            val row = productStyle.row("inventory-batch-${item.batch.id}").apply {
                gravity = Gravity.CENTER_VERTICAL
                productStyle.clickable(this)
                setPadding(productStyle.dp(16), productStyle.dp(8), productStyle.dp(16), productStyle.dp(8))
                minimumHeight = productStyle.dp(64)
                contentDescription = description
                if (Build.VERSION.SDK_INT >= 28) isScreenReaderFocusable = true
                setOnClickListener {
                    rememberList(); selectedBatchId = item.batch.id; showSelected(); detailRequested = true; panes.detailRequested = true
                }
            }
            val semantic = "product/inventory/list/${item.batch.id}"
            row.addView(HomeVisualIcon(this, "bean", productStyle.muted).apply { tag = "$semantic/icon" },
                LinearLayout.LayoutParams(productStyle.dp(24), productStyle.dp(24)).apply { marginEnd = productStyle.dp(12) })
            val identity = productStyle.column("$semantic/identity")
            productStyle.add(identity, productStyle.text(item.batch.bean.name, TaskProductVisualStyle.body, "$semantic/name"))
            productStyle.add(identity, productStyle.text(getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)),
                TaskProductVisualStyle.auxiliary, "$semantic/batch", true))
            row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(productStyle.text(BeanQuantity.formatGrams(item.balanceMg) + " g", TaskProductVisualStyle.body, "$semantic/balance"),
                LinearLayout.LayoutParams(-2, -2).apply { marginStart = productStyle.dp(16) })
            row.addView(HomeVisualIcon(this, "chevron", productStyle.muted).apply { tag = "$semantic/chevron" },
                LinearLayout.LayoutParams(productStyle.dp(16), productStyle.dp(16)).apply { marginStart = productStyle.dp(8) })
            list.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (list.childCount > 0) topMargin = productStyle.dp(8) })
            rowButtons[item.batch.id] = row
        }
        panes.onPresentationChanged = { twoPane, detailVisible ->
            compact = !twoPane
            back.visibility = if (compact) android.view.View.VISIBLE else android.view.View.GONE
            updateBack(compact && detailVisible)
        }
        showSelected()
        panes.detailRequested = detailRequested
    }
    /** Foreground-only 1dp rules: no new row/column width, height or touch geometry. */
    private fun inventoryRules(bottom: Boolean = false, vertical: () -> List<Float> = { emptyList() }) =
        object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = productStyle.border }
            override fun draw(canvas: android.graphics.Canvas) {
                val stroke = resources.displayMetrics.density
                if (bottom) canvas.drawRect(bounds.left.toFloat(), bounds.bottom - stroke, bounds.right.toFloat(), bounds.bottom.toFloat(), paint)
                vertical().forEach { x -> canvas.drawRect(x - stroke / 2, bounds.top.toFloat(), x + stroke / 2, bounds.bottom.toFloat(), paint) }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }

    private fun inventoryLink(parent: LinearLayout, value: String, action: () -> Unit) =
        productStyle.button(HoyiUi.button(this, parent, value, action = action)).apply {
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
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
    body.tag = BeanPageRegions(actions, header)
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
