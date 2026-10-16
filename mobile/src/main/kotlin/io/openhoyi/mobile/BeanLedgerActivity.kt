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
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(HoyiUi.dp(this@BeanLedgerActivity, 12), HoyiUi.dp(this@BeanLedgerActivity, 12),
                    HoyiUi.dp(this@BeanLedgerActivity, 12), HoyiUi.dp(this@BeanLedgerActivity, 12))
                setBackgroundColor(getColor(R.color.mobile_surface))
                list.addView(this, LinearLayout.LayoutParams(-1, -2))
            }
            val change = getString(R.string.beans_event_title, kind, BeanQuantity.formatGrams(event.deltaMg))
            val balance = getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg))
            HoyiUi.label(this, row, "$change · $balance", 17, true)
            val bean = batches[event.batchId]?.batch?.bean?.name ?: getString(R.string.beans_missing)
            val batch = getString(R.string.beans_batch_id, beanBatchNumber(inventory, event.batchId))
            HoyiUi.label(this, row, "$bean · $batch", 14, muted = true).apply {
                setPadding(0, HoyiUi.dp(this@BeanLedgerActivity, 6), 0, 0)
            }
            event.reason?.let {
                HoyiUi.label(this, row, getString(R.string.beans_event_reason, it), 15).apply {
                    setPadding(0, HoyiUi.dp(this@BeanLedgerActivity, 6), 0, 0)
                }
            }
            list.addView(View(this).apply { setBackgroundColor(getColor(R.color.mobile_border)) },
                LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        }
    }
}
