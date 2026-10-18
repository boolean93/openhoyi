package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import io.openhoyi.bean.InventoryEventKind
import java.util.UUID

/** Explicit human bean/dose intent. This screen never starts extraction or writes a device command. */
class BeanPreparationActivity : ScaleOwnerActivity() {
    companion object { private const val CAPTURE = 63; const val EXTRA_BATCH_ID = "prepareBeanBatchId" }
    private var nextEventId = UUID.randomUUID().toString()
    private var draftBatchId: String? = null
    private var draftAmount = ""
    private var amount: EditText? = null
    private var capture: Button? = null
    private var scaleGuard: TextView? = null
    private var error: TextView? = null
    private var errorResource: Int? = null
    private var dialog: AlertDialog? = null
    private var newSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        nextEventId = savedInstanceState?.getString("nextEventId") ?: nextEventId
        draftBatchId = savedInstanceState?.getString("draftBatchId") ?: intent.getStringExtra(EXTRA_BATCH_ID)
        draftAmount = savedInstanceState?.getString("draftAmount").orEmpty()
        errorResource = savedInstanceState?.getInt("errorResource", 0)?.takeIf { it != 0 }
        renderPreparation()
    }
    override fun onStart() { super.onStart(); rememberDraft(); renderPreparation() }
    override fun onDestroy() { dialog?.dismiss(); dialog = null; super.onDestroy() }
    private fun rememberDraft() { if (newSelection) draftAmount = amount?.text?.toString() ?: draftAmount }
    private fun renderPreparation() {
        capture = null; scaleGuard = null; amount = null; newSelection = false
        val body = beanPage(getString(R.string.dose_title), getString(R.string.dose_subtitle))
        val app = application as MobileApplication
        val preparation = app.beanPreparationResult.getOrNull()
        if (preparation == null) { HoyiUi.label(this, body, getString(R.string.dose_start_unavailable), 17, true); return }
        val inventory = beanInventory(body) ?: return
        val current = preparation.current()
        error = beanError(body).also { view -> errorResource?.let(view::setText) }
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .5f, supportingMaxWidthDp = 680)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }
        workspace.primaryBody.addView(ScrollView(this).apply {
            tag = "dose-content-scroll"
            addView(content)
        }, LinearLayout.LayoutParams(-1, -1))
        val support = workspace.secondaryBody
        fun showReceipt() {
            val selected = current ?: return
            val event = inventory.events().find { it.eventId == selected.eventId && it.batchId == selected.batchId &&
                it.kind == InventoryEventKind.CONSUME && it.deltaMg == -selected.amountMg } ?: return
            val before = runCatching { Math.subtractExact(event.balanceMg, event.deltaMg) }.getOrNull() ?: return
            support.addView(DoseBalanceView(this, receipt = true).apply { update(before, event.balanceMg) },
                LinearLayout.LayoutParams(-1, -2))
        }
        if (current != null && current.shotId != null && !current.associated) {
            val card = HoyiUi.card(this, content).apply { layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2) }
            HoyiUi.label(this, card, getString(R.string.dose_summary, current.beanName, beanBatchNumber(inventory, current.batchId), BeanQuantity.formatGrams(current.amountMg)), 20, true)
            val consumed = inventory.events().any { it.eventId == current.eventId && it.kind == InventoryEventKind.CONSUME &&
                it.batchId == current.batchId && it.deltaMg == -current.amountMg }
            showReceipt()
            HoyiUi.label(this, support, getString(if (consumed) R.string.dose_association_pending else R.string.dose_ledger_conflict), 17, true)
            val retry = HoyiUi.button(this, beanActions(body), getString(R.string.dose_association_retry), primary = true) {}
            retry.setOnClickListener {
                retry.isEnabled = false
                val result = runCatching {
                    val journal = app.reconcileJournal().getOrThrow()
                    // Same durable recovery path as process restart. Never consumes again.
                    preparation.associatePending(journal, inventory)
                }
                errorResource = if (result.isSuccess) null else R.string.dose_association_failed
                renderPreparation()
            }
            retry.isEnabled = consumed
            HoyiUi.button(this, beanActions(body), getString(R.string.dose_return)) { finish() }
            return
        }
        if (current != null && current.shotId == null) {
            val card = HoyiUi.card(this, content).apply { layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2) }
            HoyiUi.label(this, card, getString(R.string.dose_summary, current.beanName, beanBatchNumber(inventory, current.batchId), BeanQuantity.formatGrams(current.amountMg)), 20, true)
            val ledger = inventory.events().find { it.eventId == current.eventId }
            val matches = ledger?.let { it.kind == InventoryEventKind.CONSUME && it.batchId == current.batchId && it.deltaMg == -current.amountMg } == true
            showReceipt()
            if (!current.attempted) {
                inventory.batches().find { it.batch.id == current.batchId }?.let { batch ->
                    if (current.amountMg <= batch.balanceMg) support.addView(DoseBalanceView(this).apply {
                        update(batch.balanceMg, batch.balanceMg - current.amountMg)
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            if (current.confirmed) {
                HoyiUi.label(this, support, getString(if (matches) R.string.dose_confirmed else R.string.dose_ledger_conflict,
                    BeanQuantity.formatGrams(current.amountMg)), 17, true)
            } else {
                HoyiUi.label(this, support, getString(if (current.attempted) R.string.dose_attempted else R.string.dose_saved), 16)
                val confirm = HoyiUi.button(this, beanActions(body), getString(if (current.attempted) R.string.dose_retry else R.string.dose_confirm), primary = true) {}
                confirm.setOnClickListener {
                    confirm.isEnabled = false
                    val result = runCatching { synchronized(preparation) {
                        check(preparation.current()?.eventId == current.eventId && preparation.current()?.shotId == null)
                        preparation.confirm(inventory)
                    } }
                    errorResource = if (result.isSuccess) null else R.string.dose_confirm_failed
                    renderPreparation()
                }
            }
            if (!current.attempted) HoyiUi.button(this, beanActions(body), getString(R.string.dose_cancel_selection)) {
                val result = runCatching { synchronized(preparation) {
                    check(preparation.current() == current && !current.attempted)
                    preparation.cancel()
                } }
                if (result.isSuccess) { nextEventId = UUID.randomUUID().toString(); draftAmount = ""; errorResource = null }
                else errorResource = R.string.dose_save_failed
                renderPreparation()
            } else HoyiUi.button(this, beanActions(body), getString(R.string.dose_discard)) {
                dialog = AlertDialog.Builder(this).setTitle(R.string.dose_discard_title).setMessage(R.string.dose_discard_message)
                    .setNegativeButton(R.string.dose_cancel_dialog, null).setPositiveButton(R.string.dose_discard_confirm, null).create()
                dialog?.setOnShowListener {
                    val positive = dialog?.getButton(AlertDialog.BUTTON_POSITIVE)
                    positive?.setOnClickListener {
                        positive.isEnabled = false
                        val result = runCatching { synchronized(preparation) {
                            check(preparation.current() == current)
                            preparation.discard(inventory)
                        } }
                        if (result.isSuccess) { nextEventId = UUID.randomUUID().toString(); draftAmount = ""; errorResource = null }
                        else errorResource = R.string.dose_discard_failed
                        dialog?.dismiss(); dialog = null; renderPreparation()
                    }
                }
                dialog?.show()
            }
            HoyiUi.button(this, beanActions(body), getString(R.string.dose_return)) { finish() }
            return
        }
        newSelection = true
        if (current?.shotId != null) {
            HoyiUi.label(this, content, getString(R.string.dose_next_cup), 16, muted = true)
            if (nextEventId == current.eventId) { nextEventId = UUID.randomUUID().toString(); draftAmount = "" }
        }
        val batches = inventory.batches()
        if (batches.isEmpty()) {
            HoyiUi.label(this, content, getString(R.string.dose_empty), 17)
            HoyiUi.button(this, beanActions(body), getString(R.string.dose_add_beans), primary = true) { startActivity(Intent(this, BeanEntryActivity::class.java)) }
            return
        }
        HoyiUi.label(this, content, getString(R.string.dose_batch), 20, true).apply {
            setPadding(0, 0, 0, HoyiUi.dp(this@BeanPreparationActivity, 16))
        }
        if (batches.none { it.batch.id == draftBatchId }) draftBatchId = batches.first().batch.id
        val form = HoyiUi.card(this, support).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            tag = "bean-full-field"
        }
        val selectedBean = HoyiUi.label(this, form, "", 20, true).apply {
            setPadding(0, 0, 0, HoyiUi.dp(this@BeanPreparationActivity, 16))
        }
        val preview = DoseBalanceView(this)
        support.addView(preview, LinearLayout.LayoutParams(-1, -2))
        fun updatePreview() {
            val batch = batches.find { it.batch.id == draftBatchId }
            val mg = runCatching { BeanQuantity.parseGrams(amount?.text?.toString().orEmpty()) }.getOrNull()
            if (batch != null && mg != null && mg > 0 && mg <= batch.balanceMg)
                preview.update(batch.balanceMg, batch.balanceMg - mg)
            else preview.update(null, null)
        }
        val choices = linkedMapOf<String, Button>()
        fun updateChoice() {
            val selected = batches.first { it.batch.id == draftBatchId }
            selectedBean.text = selected.batch.bean.name + " · " +
                getString(R.string.beans_batch_id, beanBatchNumber(inventory, selected.batch.id))
            choices.forEach { (id, button) ->
                button.isSelected = id == draftBatchId
                val fill = if (button.isSelected) R.color.mobile_accent_soft else R.color.mobile_surface
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@BeanPreparationActivity, fill, 8, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@BeanPreparationActivity, fill, 8, R.color.mobile_border))
                }
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)), states, null)
            }
            updatePreview()
        }
        batches.forEach { batch ->
            choices[batch.batch.id] = HoyiUi.button(this, content,
                getString(R.string.dose_batch_choice, batch.batch.bean.name, BeanQuantity.formatGrams(batch.balanceMg),
                    getString(R.string.beans_batch_id, beanBatchNumber(inventory, batch.batch.id)))) {
                draftBatchId = batch.batch.id
                updateChoice()
            }.apply {
                tag = "dose-batch-${batch.batch.id}"
                asTaskTextAction()
                gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
                setTextColor(getColor(R.color.mobile_text))
                minHeight = HoyiUi.dp(this@BeanPreparationActivity, 72)
                minimumHeight = minHeight
                setPadding(HoyiUi.dp(this@BeanPreparationActivity, 16), HoyiUi.dp(this@BeanPreparationActivity, 12),
                    HoyiUi.dp(this@BeanPreparationActivity, 16), HoyiUi.dp(this@BeanPreparationActivity, 12))
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = HoyiUi.dp(this@BeanPreparationActivity, 8) }
            }
        }
        amount = beanInput(form, R.string.dose_amount, true).apply { setText(draftAmount) }
        amount?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateChoice()
        HoyiUi.label(this, support, getString(R.string.dose_scale_hint), 14, muted = true)
        capture = HoyiUi.button(this, support, getString(R.string.dose_scale)) {
            if (!captureAllowed()) { errorResource = R.string.dose_scale_blocked; error?.setText(R.string.dose_scale_blocked); return@button }
            rememberDraft()
            startActivityForResult(Intent(this, ScaleActivity::class.java).putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, true), CAPTURE)
        }
        scaleGuard = HoyiUi.label(this, support, "", 14, muted = true)
        val select = HoyiUi.button(this, beanActions(body), getString(R.string.dose_save_selection), primary = true) {}
        select.setOnClickListener {
            if (preparation.current()?.shotId == null && preparation.current() != null) { renderPreparation(); return@setOnClickListener }
            val mg = runCatching { BeanQuantity.parseGrams(amount?.text?.toString().orEmpty()).also { require(it > 0) } }.getOrElse {
                errorResource = R.string.dose_bad_amount; error?.setText(R.string.dose_bad_amount); return@setOnClickListener
            }
            val batch = batches.find { it.batch.id == draftBatchId }?.batch ?: return@setOnClickListener
            rememberDraft()
            select.isEnabled = false
            val result = runCatching { synchronized(preparation) {
                val active = preparation.current()
                check(active == null || active.shotId != null)
                preparation.select(nextEventId, batch.id, batch.bean.id, batch.bean.name, mg)
            } }
            errorResource = if (result.isSuccess) null else R.string.dose_save_failed
            if (result.isSuccess) renderPreparation() else { error?.setText(R.string.dose_save_failed); select.isEnabled = true }
        }
        HoyiUi.button(this, beanActions(body), getString(R.string.dose_return)) { finish() }
        renderScale()
    }
    private fun captureAllowed(): Boolean = !BuildConfig.MOCK_MODE && changesAllowed() && owner?.machineControlSafetyMessage == null
    override fun renderScale() {
        capture?.isEnabled = newSelection && captureAllowed()
        scaleGuard?.text = when {
            owner == null -> getString(R.string.dose_scale_wait)
            !captureAllowed() -> getString(R.string.dose_scale_blocked)
            else -> ""
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CAPTURE || resultCode != RESULT_OK) return
        val hundredths = data?.getIntExtra(ScaleActivity.EXTRA_BEAN_DOSE_HUNDREDTHS_GRAM, 0) ?: 0
        if (hundredths <= 0) { errorResource = R.string.dose_scale_invalid; error?.setText(R.string.dose_scale_invalid); return }
        draftAmount = BeanQuantity.formatGrams(hundredths * 10L)
        amount?.setText(draftAmount)
        errorResource = null; error?.text = ""
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        rememberDraft()
        outState.putInt("errorResource", errorResource ?: 0)
        outState.putString("nextEventId", nextEventId); outState.putString("draftBatchId", draftBatchId); outState.putString("draftAmount", draftAmount)
    }
}
