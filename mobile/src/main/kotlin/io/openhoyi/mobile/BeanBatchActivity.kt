package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import io.openhoyi.bean.BeanInventory
import java.util.UUID

class BeanBatchActivity : ThemedActivity() {
    private val dialogs = mutableListOf<AlertDialog>()
    private var adjustmentOpen = false
    private var draftTarget: String? = null
    private var draftReason = ""
    private var targetField: EditText? = null
    private var reasonField: EditText? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adjustmentOpen = savedInstanceState?.getBoolean("adjustmentOpen") ?: false
        draftTarget = savedInstanceState?.getString("draftTarget")
        draftReason = savedInstanceState?.getString("draftReason").orEmpty()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("adjustmentOpen", adjustmentOpen)
        outState.putString("draftTarget", targetField?.text?.toString() ?: draftTarget)
        outState.putString("draftReason", reasonField?.text?.toString() ?: draftReason)
    }
    override fun onDestroy() { dialogs.toList().forEach { it.dismiss() }; dialogs.clear(); super.onDestroy() }
    companion object { const val BATCH_ID = "beanBatchId" }
    override fun onStart() {
        super.onStart(); render()
        if (adjustmentOpen && dialogs.none { it.isShowing }) {
            val inventory = (application as MobileApplication).beanInventoryResult.getOrNull()
            val id = intent.getStringExtra(BATCH_ID)
            if (inventory != null && id != null) adjustment(inventory, id)
        }
    }
    private fun render() {
        val body = beanPage(getString(R.string.beans_batch_title))
        val inventory = beanInventory(body) ?: return
        val id = intent.getStringExtra(BATCH_ID)
        val item = inventory.batches().find { it.batch.id == id }
        if (item == null) { HoyiUi.label(this, body, getString(R.string.beans_missing), 17); return }
        fun row(parent: LinearLayout) = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            isBaselineAligned = false
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        fun cell(parent: LinearLayout) = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, HoyiUi.dp(this@BeanBatchActivity, 8), HoyiUi.dp(this@BeanBatchActivity, 16), HoyiUi.dp(this@BeanBatchActivity, 8))
            parent.addView(this, LinearLayout.LayoutParams(0, -2, 1f))
        }
        val context = row(body)
        HoyiUi.label(this, context, item.batch.bean.name + " · " +
            getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)), 16).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        HoyiUi.button(this, context, getString(R.string.ui_full_details)) {
            val details = listOf(R.string.beans_purchased to item.batch.purchasedOn,
                R.string.beans_roasted to item.batch.roastedOn, R.string.beans_opened to item.batch.openedOn)
                .joinToString("\n") { (label, date) -> getString(R.string.beans_date_value, getString(label), date?.toString() ?: getString(R.string.beans_not_set)) }
            beanDetails(getString(R.string.beans_batch_title), item.batch.bean.name + "\n" + details)
        }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2); asTaskTextAction() }
        fun divider(parent: LinearLayout) = parent.addView(android.view.View(this).apply {
            setBackgroundColor(getColor(R.color.mobile_border))
        }, LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        divider(body)
        val summary = row(body)
        val balance = cell(summary)
        HoyiUi.label(this, balance, getString(R.string.beans_balance_label), 14, muted = true)
        HoyiUi.label(this, balance, "", 32).apply {
            typeface = TaskTypography.face(this@BeanBatchActivity, 700)
            val value = BeanQuantity.formatGrams(item.balanceMg) + " g"
            text = android.text.SpannableString(value).apply {
                setSpan(android.text.style.RelativeSizeSpan(0.55f), value.length - 1, value.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        val initial = cell(summary)
        HoyiUi.label(this, initial, getString(R.string.batch_initial_weight), 14, muted = true)
        HoyiUi.label(this, initial, BeanQuantity.formatGrams(item.batch.initialMg) + " g", 24)
        val ratioCell = cell(summary).apply {
            (layoutParams as LinearLayout.LayoutParams).weight = 2f
        }
        HoyiUi.label(this, ratioCell, getString(R.string.batch_remaining_ratio), 14, muted = true)
        beanRemainingRatio(ratioCell, item.balanceMg, item.batch.initialMg)
        divider(body)
        HoyiUi.label(this, body, getString(R.string.batch_information), 20, true).apply {
            setPadding(0, HoyiUi.dp(this@BeanBatchActivity, 16), 0, HoyiUi.dp(this@BeanBatchActivity, 8))
        }
        val dates = row(body)
        listOf(R.string.beans_purchased to item.batch.purchasedOn,
            R.string.beans_roasted to item.batch.roastedOn, R.string.beans_opened to item.batch.openedOn)
            .forEach { (label, date) ->
                val dateCell = cell(dates)
                HoyiUi.label(this, dateCell, getString(label), 14, muted = true)
                HoyiUi.label(this, dateCell, date?.toString() ?: getString(R.string.beans_not_set), 16)
            }
        divider(body)
        HoyiUi.label(this, body, getString(R.string.batch_recent_events), 20, true).apply {
            setPadding(0, HoyiUi.dp(this@BeanBatchActivity, 16), 0, HoyiUi.dp(this@BeanBatchActivity, 8))
        }
        fun eventRow(parent: LinearLayout, values: List<String>, heading: Boolean): LinearLayout = row(parent).also { line ->
            values.forEach { value ->
                HoyiUi.label(this, cell(line), value, if (heading) 14 else 16, muted = heading).apply {
                    if (!heading) { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details" }
                }
            }
        }
        eventRow(body, listOf(getString(R.string.ledger_type), getString(R.string.ledger_change), getString(R.string.beans_reason)), true)
        val recent = beanList(body)
        val events = inventory.events().asReversed().filter { it.batchId == item.batch.id }.take(3)
        if (events.isEmpty()) HoyiUi.label(this, recent, getString(R.string.beans_ledger_empty), 16, muted = true)
        events.forEach { event ->
            val kind = getString(when (event.kind) {
                io.openhoyi.bean.InventoryEventKind.ADD -> R.string.beans_event_add
                io.openhoyi.bean.InventoryEventKind.CONSUME -> R.string.beans_event_consume
                io.openhoyi.bean.InventoryEventKind.ADJUST -> R.string.beans_event_adjust
            })
            val change = (if (event.deltaMg > 0) "+" else "") + BeanQuantity.formatGrams(event.deltaMg) + " g"
            val values = listOf(kind, change, event.reason ?: "—")
            divider(recent)
            eventRow(recent, values, false).apply {
                minimumHeight = HoyiUi.dp(this@BeanBatchActivity, 48)
                isFocusable = true
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)),
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@BeanBatchActivity,
                            R.color.mobile_surface, 0, R.color.mobile_accent))
                        addState(intArrayOf(), HoyiUi.shape(this@BeanBatchActivity, R.color.mobile_surface, 0))
                    }, null)
                contentDescription = values.joinToString(" · ")
                setOnClickListener { beanDetails(getString(R.string.beans_ledger_title), values.joinToString("\n") + "\n" +
                    getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg))) }
            }
        }
        val footer = row(body)
        footer.gravity = android.view.Gravity.BOTTOM
        val ledgerActions = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        footer.addView(ledgerActions, LinearLayout.LayoutParams(0, -2, 1f))
        HoyiUi.button(this, ledgerActions, getString(R.string.beans_ledger)) {
            startActivity(Intent(this, BeanLedgerActivity::class.java).putExtra(BATCH_ID, item.batch.id))
        }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2); asTaskTextAction() }
        HoyiUi.label(this, ledgerActions, getString(R.string.batch_ledger_retained),
            TaskVisualTokens.SUPPORTING_SP, muted = true).apply {
            setPadding(0, HoyiUi.dp(this@BeanBatchActivity, TaskVisualTokens.RELATED_GAP_DP),
                HoyiUi.dp(this@BeanBatchActivity, TaskVisualTokens.GROUP_GAP_DP), 0)
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0)
        footer.addView(dock, LinearLayout.LayoutParams(0, -2, 1f))
        HoyiUi.button(this, dock, getString(R.string.beans_adjust), primary = true) { draftTarget = null; draftReason = ""; adjustment(inventory, item.batch.id) }
    }

    private fun adjustment(inventory: BeanInventory, batchId: String) {
        var current = inventory.batches().find { it.batch.id == batchId } ?: return
        adjustmentOpen = true
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true
            setPadding(HoyiUi.dp(this@BeanBatchActivity, 20), HoyiUi.dp(this@BeanBatchActivity, 8), HoyiUi.dp(this@BeanBatchActivity, 20), HoyiUi.dp(this@BeanBatchActivity, 20))
        }
        HoyiUi.label(this, form, getString(R.string.beans_adjust_hint), 15, muted = true)
        val target = beanInput(form, R.string.beans_target, true).apply { setText(draftTarget ?: BeanQuantity.formatGrams(current.balanceMg)) }
        val reason = beanInput(form, R.string.beans_reason).apply { setText(draftReason) }
        targetField = target; reasonField = reason
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
        dialog.setOnDismissListener {
            draftTarget = target.text.toString(); draftReason = reason.text.toString()
            targetField = null; reasonField = null; adjustmentOpen = false
            dialogs.remove(dialog)
        }
        dialog.show()
    }
}
