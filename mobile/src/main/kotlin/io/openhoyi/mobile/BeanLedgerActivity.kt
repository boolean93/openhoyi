package io.openhoyi.mobile

import io.openhoyi.bean.InventoryEventKind
import android.os.Bundle
import android.widget.ScrollView
import android.widget.LinearLayout
import android.view.View

class BeanLedgerActivity : ThemedActivity() {
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
        val body = beanPage(getString(R.string.beans_ledger_title))
        val inventory = beanInventory(body) ?: return
        val batchId = intent.getStringExtra(BeanBatchActivity.BATCH_ID)
        val batches = inventory.batches().associateBy { it.batch.id }
        batches[batchId]?.let { item ->
            val summary = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(summary)
            HoyiUi.label(this, summary, "${item.batch.bean.name} · ${getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id))}\n" +
                "${getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg))} · ${getString(R.string.beans_initial, BeanQuantity.formatGrams(item.batch.initialMg))}", 16).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
                setPadding(0, 0, HoyiUi.dp(this@BeanLedgerActivity, 16), HoyiUi.dp(this@BeanLedgerActivity, 16))
            }
            HoyiUi.button(this, summary, getString(R.string.beans_batch_title)) {
                startActivity(android.content.Intent(this, BeanBatchActivity::class.java).putExtra(BeanBatchActivity.BATCH_ID, item.batch.id))
            }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2) }
        }
        HoyiUi.label(this, body, getString(R.string.ledger_order), 14, muted = true).apply {
            setPadding(0, HoyiUi.dp(this@BeanLedgerActivity, 8), 0, HoyiUi.dp(this@BeanLedgerActivity, 16))
        }
        ledgerRow(body, listOf(getString(R.string.ledger_type), getString(R.string.ledger_change),
            getString(R.string.ledger_balance), getString(R.string.beans_batch_id, "").trim(), getString(R.string.beans_reason)), true)
        val list = beanList(body)
        listScroll = list.parent as ScrollView
        listScroll?.post { listScroll?.scrollTo(0, savedScrollY) }
        val events = inventory.events().filter { batchId == null || it.batchId == batchId }.asReversed()
        if (events.isEmpty()) HoyiUi.label(this, list, getString(R.string.beans_ledger_empty), 17, muted = true)
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
                isClickable = true
                isFocusable = true
                minimumHeight = HoyiUi.dp(this@BeanLedgerActivity, 48)
                contentDescription = values.joinToString(" · ")
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@BeanLedgerActivity,
                        R.color.mobile_surface, 0, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@BeanLedgerActivity, R.color.mobile_surface, 0))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)), states, null)
                setOnClickListener { beanDetails(getString(R.string.beans_ledger_title),
                    getString(R.string.beans_event_title, kind, amount) + "\n" +
                        getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg)) + "\n" +
                        "$bean · $batch\n" + getString(R.string.beans_event_reason, reason)) }
            }
            list.addView(View(this).apply { setBackgroundColor(getColor(R.color.mobile_border)) },
                LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        }
    }
    private fun ledgerRow(parent: LinearLayout, values: List<String>, heading: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            setBackgroundColor(getColor(if (heading) R.color.mobile_background else R.color.mobile_surface))
            setPadding(0, HoyiUi.dp(this@BeanLedgerActivity, if (heading) 8 else 16), 0,
                HoyiUi.dp(this@BeanLedgerActivity, if (heading) 8 else 16))
        }
        parent.addView(row, LinearLayout.LayoutParams(-1, -2))
        values.forEachIndexed { index, value ->
            HoyiUi.label(this, row, value, if (heading) 14 else 16, bold = heading, muted = heading).apply {
                layoutParams = LinearLayout.LayoutParams(0, -2, if (index < 3) 1f else 1.6f)
                setPadding(HoyiUi.dp(this@BeanLedgerActivity, 8), 0, HoyiUi.dp(this@BeanLedgerActivity, 8), 0)
                if (!heading) { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details" }
            }
        }
        return row
    }

}
