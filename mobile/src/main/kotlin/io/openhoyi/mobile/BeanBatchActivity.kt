package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.widget.LinearLayout
import android.widget.ScrollView
import io.openhoyi.bean.BeanInventory
import java.util.UUID

class BeanBatchActivity : ThemedActivity() {
    private val dialogs = mutableListOf<AlertDialog>()
    override fun onDestroy() { dialogs.toList().forEach { it.dismiss() }; dialogs.clear(); super.onDestroy() }
    companion object { const val BATCH_ID = "beanBatchId" }
    override fun onStart() { super.onStart(); render() }
    private fun render() {
        val body = beanPage(getString(R.string.beans_batch_title))
        val inventory = beanInventory(body) ?: return
        val id = intent.getStringExtra(BATCH_ID)
        val item = inventory.batches().find { it.batch.id == id }
        if (item == null) { HoyiUi.label(this, body, getString(R.string.beans_missing), 17); return }
        val card = HoyiUi.card(this, body, item.batch.bean.name)
        HoyiUi.label(this, card, getString(R.string.beans_balance, BeanQuantity.formatGrams(item.balanceMg)), 30, true)
        HoyiUi.label(this, card, getString(R.string.beans_initial, BeanQuantity.formatGrams(item.batch.initialMg)), 15, muted = true)
        HoyiUi.label(this, card, getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)), 13, muted = true)
        listOf(R.string.beans_purchased to item.batch.purchasedOn, R.string.beans_roasted to item.batch.roastedOn,
            R.string.beans_opened to item.batch.openedOn).forEach { (label, date) ->
            HoyiUi.label(this, card, getString(R.string.beans_date_value, getString(label), date?.toString() ?: getString(R.string.beans_not_set)), 16)
                .setPadding(0, HoyiUi.dp(this, 12), 0, 0)
        }
        HoyiUi.button(this, body, getString(R.string.beans_ledger)) {
            startActivity(Intent(this, BeanLedgerActivity::class.java).putExtra(BATCH_ID, item.batch.id))
        }
        HoyiUi.button(this, body, getString(R.string.beans_adjust)) { adjustment(inventory, item.batch.id) }
    }

    private fun adjustment(inventory: BeanInventory, batchId: String) {
        var current = inventory.batches().find { it.batch.id == batchId } ?: return
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(HoyiUi.dp(this@BeanBatchActivity, 20), HoyiUi.dp(this@BeanBatchActivity, 8), HoyiUi.dp(this@BeanBatchActivity, 20), HoyiUi.dp(this@BeanBatchActivity, 20))
        }
        HoyiUi.label(this, form, getString(R.string.beans_adjust_hint), 15, muted = true)
        val target = beanInput(form, R.string.beans_target, true).apply { setText(BeanQuantity.formatGrams(current.balanceMg)) }
        val reason = beanInput(form, R.string.beans_reason)
        val error = beanError(form)
        val dialog = AlertDialog.Builder(this).setTitle(R.string.beans_adjust)
            .setView(ScrollView(this).apply { addView(form) })
            .setNegativeButton(R.string.beans_cancel, null).setPositiveButton(R.string.beans_preview, null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val targetMg = runCatching { BeanQuantity.parseGrams(target.text.toString()) }.getOrElse {
                    error.setText(R.string.beans_bad_quantity); return@setOnClickListener
                }
                val note = reason.text.toString().trim()
                if (note.isBlank() || note.length > 4096) { error.setText(R.string.beans_reason_required); return@setOnClickListener }
                val delta = targetMg - current.balanceMg // Both nonnegative Long values; subtraction cannot overflow.
                if (delta == 0L) { error.setText(R.string.beans_no_change); return@setOnClickListener }
                error.text = ""
                val eventId = UUID.randomUUID().toString()
                var committed = false
                val preview = AlertDialog.Builder(this).setTitle(R.string.beans_adjust)
                    .setMessage(getString(R.string.beans_adjust_preview, BeanQuantity.formatGrams(current.balanceMg),
                        BeanQuantity.formatGrams(targetMg), BeanQuantity.formatGrams(delta), note))
                    .setNegativeButton(R.string.beans_edit, null).setPositiveButton(R.string.beans_confirm_adjust, null).create()
                dialogs += preview
                preview.setOnDismissListener { dialogs.remove(preview) }
                preview.setOnShowListener {
                    preview.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener confirm@ {
                        if (committed) return@confirm
                        val confirm = preview.getButton(AlertDialog.BUTTON_POSITIVE)
                        confirm.isEnabled = false
                        // The inventory uses this same monitor; no dose can land between comparison and commit.
                        val result = synchronized(inventory) {
                            if (inventory.batches().find { it.batch.id == batchId }?.balanceMg != current.balanceMg) null
                            else runCatching { inventory.adjust(eventId, batchId, delta, note) }
                        }
                        if (result?.isSuccess == true) { committed = true; preview.dismiss(); dialog.dismiss(); render() }
                        else {
                            error.setText(if (result == null) R.string.beans_changed else R.string.beans_save_failed)
                            if (result == null) current = inventory.batches().find { it.batch.id == batchId } ?: current
                            preview.dismiss()
                        }
                    }
                }
                preview.show()
            }
        }
        dialogs += dialog
        dialog.setOnDismissListener { dialogs.remove(dialog) }
        dialog.show()
    }
}
