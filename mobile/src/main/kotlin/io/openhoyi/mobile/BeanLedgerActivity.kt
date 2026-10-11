package io.openhoyi.mobile

import io.openhoyi.bean.InventoryEventKind

class BeanLedgerActivity : ThemedActivity() {
    override fun onStart() {
        super.onStart()
        val body = beanPage(getString(R.string.beans_ledger_title))
        val inventory = beanInventory(body) ?: return
        val batchId = intent.getStringExtra(BeanBatchActivity.BATCH_ID)
        val batches = inventory.batches().associateBy { it.batch.id }
        val events = inventory.events().filter { batchId == null || it.batchId == batchId }.asReversed()
        if (events.isEmpty()) HoyiUi.label(this, body, getString(R.string.beans_ledger_empty), 17, muted = true)
        events.forEach { event ->
            val kind = getString(when (event.kind) {
                InventoryEventKind.ADD -> R.string.beans_event_add
                InventoryEventKind.CONSUME -> R.string.beans_event_consume
                InventoryEventKind.ADJUST -> R.string.beans_event_adjust
            })
            val card = HoyiUi.card(this, body, getString(R.string.beans_event_title, kind, BeanQuantity.formatGrams(event.deltaMg)))
            HoyiUi.label(this, card, batches[event.batchId]?.batch?.bean?.name ?: getString(R.string.beans_missing), 17, true)
            HoyiUi.label(this, card, getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg)), 16)
            event.reason?.let { HoyiUi.label(this, card, getString(R.string.beans_event_reason, it), 15) }
            HoyiUi.label(this, card, getString(R.string.beans_batch_id, beanBatchNumber(inventory, event.batchId)), 13, muted = true)
        }
    }
}
