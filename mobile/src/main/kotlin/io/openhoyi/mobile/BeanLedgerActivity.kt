package io.openhoyi.mobile

import io.openhoyi.bean.InventoryEventKind
import android.os.Bundle
import android.widget.ScrollView
import android.widget.LinearLayout
import android.view.View

class BeanLedgerActivity : ThemedActivity() {
    private val style by lazy { TaskProductVisualStyle(this) }
    val productLayoutEvidence = org.json.JSONObject()
    private var listScroll: ScrollView? = null
    private var savedScrollY = 0
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); savedScrollY = savedInstanceState?.getInt("listScrollY") ?: 0 }
    override fun onStop() { savedScrollY = listScroll?.scrollY ?: savedScrollY; super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("listScrollY", listScroll?.scrollY ?: savedScrollY)
    }
    override fun onStart() {
        super.onStart()
        val body = style.page(this, getString(R.string.beans_ledger_title))
        val inventory = beanInventory(body) ?: return
        val batchId = intent.getStringExtra(BeanBatchActivity.BATCH_ID)
        val batches = inventory.batches().associateBy { it.batch.id }
        batches[batchId]?.let { item ->
            val summary = style.row("product/ledger/summary").apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            body.addView(summary, LinearLayout.LayoutParams(-1, -2))
            val identity = style.column()
            summary.addView(identity, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(16) })
            style.add(identity, style.text("${item.batch.bean.name} · ${getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id))}",
                TaskProductVisualStyle.body, "product/ledger/identity"))
            val quantityGroups = mutableListOf<LinearLayout>()
            val quantities = object : LinearLayout(this) {
                init { isBaselineAligned = false; tag = "product/ledger/quantities" }
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    quantityGroups.forEach { it.measure(natural, natural) }
                    val required = quantityGroups.sumOf { it.measuredWidth } + style.dp(16)
                    val available = View.MeasureSpec.getSize(widthMeasureSpec)
                    val inline = required <= available
                    orientation = if (inline) HORIZONTAL else VERTICAL
                    val direction = this.layoutDirection
                    quantityGroups.forEachIndexed { index, group -> (group.layoutParams as LayoutParams).apply {
                        width = if (inline) -2 else -1; height = -2; weight = 0f
                        marginStart = if (inline && index > 0) style.dp(16) else 0; marginEnd = 0
                        topMargin = if (!inline && index > 0) style.dp(8) else 0; resolveLayoutDirection(direction)
                    } }
                    productLayoutEvidence.put("summaryNaturalWidthPx", required).put("summaryAvailableWidthPx", available).put("summaryInline", inline)
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }
            identity.addView(quantities, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
            fun quantity(label: Int, value: Long, key: String) {
                val group = style.row().apply { isBaselineAligned = true; gravity = android.view.Gravity.BOTTOM }
                quantities.addView(group, LinearLayout.LayoutParams(-2, -2)); quantityGroups += group
                group.addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true),
                    LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) })
                group.addView(style.text(BeanQuantity.formatGrams(value), TaskProductVisualStyle.compactMetric, "product/ledger/$key/number"), LinearLayout.LayoutParams(-2, -2))
                group.addView(style.text("g", TaskProductVisualStyle.auxiliary, "product/ledger/$key/unit", true),
                    LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
            }
            quantity(R.string.beans_balance_label, item.balanceMg, "balance")
            quantity(R.string.batch_initial_weight, item.batch.initialMg, "initial")
            style.action(summary, getString(R.string.beans_batch_title), "product/ledger/batch", top = 0) {
                startActivity(android.content.Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2); HomeVisualIcon.buttonIcon(this, "chevron", atEnd = true) }
        }
        style.divider(body, 16, 16)
        style.add(body, style.text(getString(R.string.ledger_order), TaskProductVisualStyle.auxiliary,
            "product/ledger/order", true))
        ledgerRow(body, listOf(getString(R.string.ledger_type), getString(R.string.ledger_change),
            getString(R.string.ledger_balance), getString(R.string.beans_batch_id, "").trim(), getString(R.string.beans_reason)), true)
            .apply { tag = "product/ledger/header"; (layoutParams as LinearLayout.LayoutParams).topMargin = style.dp(16) }
        val list = beanList(body)
        listScroll = (list.parent as ScrollView).apply { tag = "product/ledger/list-scroll" }
        list.tag = "product/ledger/list"
        listScroll?.post { listScroll?.scrollTo(0, savedScrollY) }
        val events = inventory.events().filter { batchId == null || it.batchId == batchId }.asReversed()
        if (events.isEmpty()) style.add(list, style.text(getString(R.string.beans_ledger_empty), TaskProductVisualStyle.body, secondary = true), 16)
        events.forEach { event ->
            val kind = getString(when (event.kind) {
                InventoryEventKind.ADD -> R.string.beans_event_add
                InventoryEventKind.CONSUME -> R.string.beans_event_consume
                InventoryEventKind.ADJUST -> R.string.beans_event_adjust
            })
            val amount = BeanQuantity.formatGrams(event.deltaMg)
            val change = (if (event.deltaMg > 0) "+" else "") + amount + " g"
            val balance = BeanQuantity.formatGrams(event.balanceMg) + " g"
            val bean = batches[event.batchId]?.batch?.bean?.name ?: getString(R.string.beans_missing)
            val batch = getString(R.string.beans_batch_id, beanBatchNumber(inventory, event.batchId))
            val reason = event.reason ?: "—"
            val values = listOf(kind, change, balance, "$bean · $batch", reason)
            ledgerRow(list, values, false).apply {
                tag = "product/ledger/row/${event.eventId}"
                if (event.kind == InventoryEventKind.ADD) listOf(0, 1).forEach { index ->
                    (getChildAt(index) as android.widget.TextView).setTextColor(style.accent)
                }
                isClickable = true
                isFocusable = true
                minimumHeight = HoyiUi.dp(this@BeanLedgerActivity, 48)
                contentDescription = values.joinToString(" · ")
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), style.shape(style.surface, true, 0))
                    addState(intArrayOf(), style.shape(style.surface, radius = 0))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(style.selected), states, null)
                setOnClickListener { beanDetails(getString(R.string.beans_ledger_title),
                    getString(R.string.beans_event_title, kind, amount) + "\n" +
                        getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg)) + "\n" +
                        "$bean · $batch\n" + getString(R.string.beans_event_reason, reason)) }
            }
        }
    }
    private fun ledgerRow(parent: LinearLayout, values: List<String>, heading: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setBackgroundColor(if (heading) style.disabled else style.surface)
            tag = if (heading) "product/ledger/header" else null
            foreground = object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint().apply { color = this@BeanLedgerActivity.style.border; strokeWidth = resources.displayMetrics.density }
                override fun draw(canvas: android.graphics.Canvas) { val y = bounds.bottom - paint.strokeWidth / 2f
                    canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint) }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha }
                override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
                @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
            setPadding(0, HoyiUi.dp(this@BeanLedgerActivity, if (heading) 8 else 16), 0,
                HoyiUi.dp(this@BeanLedgerActivity, if (heading) 8 else 16))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        values.forEachIndexed { index, value ->
            style.text(value, if (heading) TaskProductVisualStyle.auxiliary.copy(weight = 500) else TaskProductVisualStyle.body, secondary = heading).apply {
                row.addView(this)
                layoutParams = LinearLayout.LayoutParams(0, -2, if (index < 3) 1f else 1.6f)
                setPadding(HoyiUi.dp(this@BeanLedgerActivity, 8), 0, HoyiUi.dp(this@BeanLedgerActivity, 8), 0)
                if (!heading) { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details" }
            }
        }
        return row
    }

}
