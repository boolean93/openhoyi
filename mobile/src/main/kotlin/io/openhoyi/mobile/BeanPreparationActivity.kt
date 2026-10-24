package io.openhoyi.mobile

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.LinearLayout
import android.widget.ScrollView
import android.view.View
import android.view.Gravity
import android.view.WindowInsets
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
    val productLayoutEvidence = org.json.JSONObject()

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
        val style = TaskProductVisualStyle(this)
        val formStyle = TaskProductFormStyle(this)
        val body = style.page(this, getString(R.string.dose_title), getString(R.string.dose_subtitle))
        val app = application as MobileApplication
        val preparation = app.beanPreparationResult.getOrNull()
        if (preparation == null) { style.add(body, style.text(getString(R.string.dose_start_unavailable), TaskProductVisualStyle.body)); return }
        val inventory = beanInventory(body) ?: return
        val current = preparation.current()
        error = formStyle.error(beanError(body)).also { view -> errorResource?.let(view::setText) }
        val workspace = PreparationWorkspace(this, style, productLayoutEvidence)
        body.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f))
        workspace.primaryBody.setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
        workspace.secondaryBody.setPadding(style.dp(16), style.dp(16), style.dp(16), style.dp(16))
        workspace.primaryBody.tag = "product/preparation/choices-pane"
        workspace.secondaryBody.tag = "product/preparation/support-pane"
        workspace.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = style.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                canvas.drawLine(0f, paint.strokeWidth / 2f, bounds.right.toFloat(), paint.strokeWidth / 2f, paint)
                if (workspace.orientation == LinearLayout.HORIZONTAL) {
                    val x = if (workspace.layoutDirection == View.LAYOUT_DIRECTION_RTL) workspace.primaryBody.left - style.dp(12) else workspace.primaryBody.right + style.dp(12)
                    canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bounds.bottom.toFloat(), paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }
        workspace.primaryBody.addView(ScrollView(this).apply {
            tag = "dose-content-scroll"
            addView(content)
        }, LinearLayout.LayoutParams(-1, -1))
        content.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = style.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                for (index in 0 until content.childCount) {
                    val child = content.getChildAt(index)
                    if (child.tag?.toString()?.startsWith("dose-batch-") == true) {
                        val y = child.bottom + style.dp(4).toFloat()
                        canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint)
                    }
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val support = style.column("task-inline-form")
        support.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = style.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                for (index in 0 until support.childCount) {
                    val child = support.getChildAt(index)
                    val margin = (child.layoutParams as LinearLayout.LayoutParams).topMargin
                    if (child is DoseBalanceView && margin >= style.dp(16)) {
                        val y = child.top - style.dp(8).toFloat()
                        canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint)
                    }
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        workspace.secondaryBody.addView(ScrollView(this).apply {
            tag = "product/preparation/support-scroll"; addView(support)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = style.column("product/preparation/actions").also { workspace.actions = it }
        workspace.secondaryBody.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
        fun action(value: String, primary: Boolean = false, onClick: () -> Unit): Button =
            HoyiUi.button(this, actions, value, primary = primary, action = onClick).apply {
                style.button(this, primary, outline = !primary)
                val icon = when (value) {
                    getString(R.string.dose_save_selection), getString(R.string.dose_confirm) -> "check"
                    getString(R.string.dose_return) -> "back"
                    getString(R.string.dose_add_beans) -> "bean"
                    else -> null
                }
                icon?.let { HomeVisualIcon.buttonIcon(this, it) }
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { if (actions.childCount > 1) topMargin = style.dp(8) }
            }
        fun message(parent: LinearLayout, value: String, strong: Boolean = false) = style.text(value,
            if (strong) TaskProductVisualStyle.section else TaskProductVisualStyle.body).also { style.add(parent, it, 8) }
        fun heading() {
            content.addView(style.text(getString(R.string.dose_batch), TaskProductVisualStyle.section),
                LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = style.dp(16) })
        }
        fun choice(id: String, name: String, balance: Long, description: String, interactive: Boolean): LinearLayout {
            val row = style.row().apply {
                tag = (if (interactive) "dose-batch-" else "dose-batch-summary-") + id
                contentDescription = description; minimumHeight = style.dp(72)
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
                if (interactive) { isClickable = true; isFocusable = true }
            }
            row.addView(android.widget.FrameLayout(this).apply {
                tag = "product/preparation/choice/$id/icon"
                addView(HomeVisualIcon(this@BeanPreparationActivity, "bean", style.ink))
            }, LinearLayout.LayoutParams(style.dp(24), style.dp(24)).apply { marginEnd = style.dp(12) })
            val identity = style.column()
            row.addView(identity, LinearLayout.LayoutParams(0, -2, 1f))
            style.add(identity, style.text(name, TaskProductVisualStyle.body, "product/preparation/choice/$id/name"))
            style.add(identity, style.text(getString(R.string.beans_batch_id, beanBatchNumber(inventory, id)), TaskProductVisualStyle.auxiliary,
                "product/preparation/choice/$id/batch", true))
            row.addView(style.quantity(BeanQuantity.formatGrams(balance), "g", TaskProductVisualStyle.compactMetric,
                "product/preparation/choice/$id/balance"), LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(16) })
            if (interactive) row.addView(HomeVisualIcon(this, "chevron", style.muted).apply { scaleX = if (layoutDirection == View.LAYOUT_DIRECTION_RTL) -1f else 1f },
                LinearLayout.LayoutParams(style.dp(24), style.dp(24)).apply { marginStart = style.dp(8) })
            content.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = style.dp(8) })
            return row
        }
        fun decorateChoice(row: LinearLayout, selected: Boolean) {
            row.isSelected = selected
            (row.getChildAt(0) as android.widget.FrameLayout).apply {
                removeAllViews(); addView(HomeVisualIcon(this@BeanPreparationActivity, "bean", if (selected) style.accent else style.ink))
            }
            val fill = if (selected) style.selected else style.page
            row.background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected),
                style.shape(fill), style.shape(android.graphics.Color.WHITE))
        }
        support.addView(style.text(getString(R.string.bean_workflow_selection), TaskProductVisualStyle.section),
            LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = style.dp(16) })
        fun showCurrentContext() {
            val selected = current ?: return
            heading()
            inventory.batches().forEach { batch ->
                val row = choice(batch.batch.id, batch.batch.bean.name, batch.balanceMg,
                    getString(R.string.dose_batch_choice, batch.batch.bean.name, BeanQuantity.formatGrams(batch.balanceMg),
                        getString(R.string.beans_batch_id, beanBatchNumber(inventory, batch.batch.id))), false)
                decorateChoice(row, batch.batch.id == selected.batchId)
            }
            message(support, getString(R.string.dose_summary, selected.beanName,
                beanBatchNumber(inventory, selected.batchId), BeanQuantity.formatGrams(selected.amountMg)), true)
        }
        fun showReceipt() {
            val selected = current ?: return
            val event = inventory.events().find { it.eventId == selected.eventId && it.batchId == selected.batchId &&
                it.kind == InventoryEventKind.CONSUME && it.deltaMg == -selected.amountMg } ?: return
            val before = runCatching { Math.subtractExact(event.balanceMg, event.deltaMg) }.getOrNull() ?: return
            support.addView(DoseBalanceView(this, receipt = true, style = style).apply { update(before, event.balanceMg) },
                LinearLayout.LayoutParams(-1, -2))
        }
        if (current != null && current.shotId != null && !current.associated) {
            showCurrentContext()
            val consumed = inventory.events().any { it.eventId == current.eventId && it.kind == InventoryEventKind.CONSUME &&
                it.batchId == current.batchId && it.deltaMg == -current.amountMg }
            showReceipt()
            message(support, getString(if (consumed) R.string.dose_association_pending else R.string.dose_ledger_conflict))
            val retry = action(getString(R.string.dose_association_retry), primary = true) {}
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
            action(getString(R.string.dose_return)) { finish() }
            return
        }
        if (current != null && current.shotId == null) {
            showCurrentContext()
            val ledger = inventory.events().find { it.eventId == current.eventId }
            val matches = ledger?.let { it.kind == InventoryEventKind.CONSUME && it.batchId == current.batchId && it.deltaMg == -current.amountMg } == true
            showReceipt()
            if (!current.attempted) {
                inventory.batches().find { it.batch.id == current.batchId }?.let { batch ->
                    if (current.amountMg <= batch.balanceMg) support.addView(DoseBalanceView(this, style = style).apply {
                        update(batch.balanceMg, batch.balanceMg - current.amountMg)
                    }, LinearLayout.LayoutParams(-1, -2))
                }
            }
            if (current.confirmed) {
                message(support, getString(if (matches) R.string.dose_confirmed else R.string.dose_ledger_conflict,
                    BeanQuantity.formatGrams(current.amountMg)))
            } else {
                message(support, getString(if (current.attempted) R.string.dose_attempted else R.string.dose_saved))
                val confirm = action(getString(if (current.attempted) R.string.dose_retry else R.string.dose_confirm), primary = true) {}
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
            if (!current.attempted) action(getString(R.string.dose_cancel_selection)) {
                val result = runCatching { synchronized(preparation) {
                    check(preparation.current() == current && !current.attempted)
                    preparation.cancel()
                } }
                if (result.isSuccess) { nextEventId = UUID.randomUUID().toString(); draftAmount = ""; errorResource = null }
                else errorResource = R.string.dose_save_failed
                renderPreparation()
            } else action(getString(R.string.dose_discard)) {
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
            action(getString(R.string.dose_return)) { finish() }
            return
        }
        newSelection = true
        if (current?.shotId != null) {
            style.add(content, style.text(getString(R.string.dose_next_cup), TaskProductVisualStyle.auxiliary, secondary = true), 8)
            if (nextEventId == current.eventId) { nextEventId = UUID.randomUUID().toString(); draftAmount = "" }
        }
        val batches = inventory.batches()
        if (batches.isEmpty()) {
            message(content, getString(R.string.dose_empty))
            action(getString(R.string.dose_add_beans), primary = true) { startActivity(Intent(this, BeanEntryActivity::class.java)) }
            return
        }
        heading()
        if (batches.none { it.batch.id == draftBatchId }) draftBatchId = batches.first().batch.id
        val form = style.column("bean-full-field")
        support.addView(form, LinearLayout.LayoutParams(-1, -2))
        val selectedBean = style.text("", TaskProductVisualStyle.title, "product/preparation/selected-bean")
        form.addView(selectedBean, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = style.dp(16) })
        val preview = DoseBalanceView(this, style = style)
        fun updatePreview() {
            val batch = batches.find { it.batch.id == draftBatchId }
            val mg = runCatching { BeanQuantity.parseGrams(amount?.text?.toString().orEmpty()) }.getOrNull()
            if (mg != null && mg > 0 && errorResource == R.string.dose_bad_amount) {
                errorResource = null
                error?.text = ""
            }
            if (batch != null && mg != null && mg > 0 && mg <= batch.balanceMg)
                preview.update(batch.balanceMg, batch.balanceMg - mg)
            else preview.update(null, null)
        }
        val choices = linkedMapOf<String, LinearLayout>()
        fun updateChoice() {
            val selected = batches.first { it.batch.id == draftBatchId }
            selectedBean.text = selected.batch.bean.name + " · " +
                getString(R.string.beans_batch_id, beanBatchNumber(inventory, selected.batch.id))
            choices.forEach { (id, row) -> decorateChoice(row, id == draftBatchId) }
            updatePreview()
        }
        batches.forEach { batch ->
            choices[batch.batch.id] = choice(batch.batch.id, batch.batch.bean.name, batch.balanceMg,
                getString(R.string.dose_batch_choice, batch.batch.bean.name, BeanQuantity.formatGrams(batch.balanceMg),
                    getString(R.string.beans_batch_id, beanBatchNumber(inventory, batch.batch.id))), true).apply {
                setOnClickListener {
                    draftBatchId = batch.batch.id
                    updateChoice()
                }
            }
        }
        amount = beanInput(form, R.string.dose_amount, true).apply {
            formStyle.input(this, "preparation", "amount"); setText(draftAmount)
        }
        workspace.editor = amount
        val doseLabel = (0 until form.childCount).map(form::getChildAt).filterIsInstance<TextView>().first { it.labelFor == amount?.id }
        formStyle.label(doseLabel, belowGap = false)
        formStyle.unit(requireNotNull(amount), "g", "preparation", "amount")
        val doseInput = amount!!.parent as View
        form.removeView(doseLabel); form.removeView(doseInput)
        val doseRow = object : LinearLayout(this) {
            init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(doseLabel); addView(doseInput) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                doseLabel.measure(natural, natural); doseInput.measure(natural, natural)
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                val inputMinimum = maxOf(style.dp(52), doseInput.measuredWidth)
                val labelNatural = doseLabel.measuredWidth
                val inline = labelNatural + style.dp(8) + inputMinimum <= width
                productLayoutEvidence.put("doseRowAvailableWidthPx", width).put("doseLabelNaturalWidthPx", labelNatural)
                    .put("doseInputNaturalMinimumWidthPx", inputMinimum).put("doseRowInline", inline)
                orientation = if (inline) HORIZONTAL else VERTICAL
                (doseLabel.layoutParams as LayoutParams).apply { this.width = if (inline) -2 else -1; height = -2; weight = 0f }
                val direction = this.layoutDirection
                (doseInput.layoutParams as LayoutParams).apply {
                    this.width = if (inline) 0 else -1; height = -2; weight = if (inline) 1f else 0f
                    marginStart = if (inline) style.dp(8) else 0; marginEnd = 0
                    topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(direction)
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        form.addView(doseRow, LinearLayout.LayoutParams(-1, -2))
        amount?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updatePreview() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        updateChoice()
        capture = HoyiUi.button(this, support, getString(R.string.dose_scale)) {
            if (!captureAllowed()) { errorResource = R.string.dose_scale_blocked; error?.setText(R.string.dose_scale_blocked); return@button }
            rememberDraft()
            startActivityForResult(Intent(this, ScaleActivity::class.java).putExtra(ScaleActivity.EXTRA_CAPTURE_BEAN_DOSE, true), CAPTURE)
        }
        capture?.let { style.button(it, outline = true); HomeVisualIcon.buttonIcon(it, "scale")
            it.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(8) } }
        style.add(support, style.text(getString(R.string.dose_scale_hint), TaskProductVisualStyle.auxiliary, secondary = true), 8)
        scaleGuard = style.text("", TaskProductVisualStyle.auxiliary, "product/preparation/scale-guard", true).also { style.add(support, it, 8) }
        support.addView(preview, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(16) })
        val select = action(getString(R.string.dose_save_selection), primary = true) {}
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
        val back = action(getString(R.string.dose_return)) { finish() }
        workspace.actionRowButtons = select to back
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

/** Width, not IME height, decides whether this form has room for its two permanent panes. */
private class PreparationWorkspace(context: android.content.Context, private val style: TaskProductVisualStyle,
    private val evidence: org.json.JSONObject) : LinearLayout(context) {
    val primaryBody = style.column()
    val secondaryBody = style.column()
    var editor: EditText? = null
    var actions: View? = null
    var actionRowButtons: Pair<Button, Button>? = null
    private var originalActionGravity: Int? = null
    private var originalActionBaselineAligned: Boolean? = null
    init { isBaselineAligned = false; tag = "task-workspace"; addView(primaryBody); addView(secondaryBody) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
        val height = (View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
        val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        val split = width >= style.dp(320 + 280 + 24)
        val secondaryWidth = if (split) minOf((width * .5f).toInt().coerceIn(style.dp(280), style.dp(680)), width - style.dp(320 + 24)) else width
        val innerWidth = (secondaryWidth - secondaryBody.paddingLeft - secondaryBody.paddingRight).coerceAtLeast(0)
        val exact = View.MeasureSpec.makeMeasureSpec(innerWidth, View.MeasureSpec.EXACTLY)
        val buttons = actionRowButtons
        val dock = actions as? LinearLayout
        val gap = style.dp(8)
        fun setActionRow(horizontal: Boolean) {
            if (buttons == null || dock == null) return
            if (originalActionGravity == null) originalActionGravity = dock.gravity
            if (originalActionBaselineAligned == null) originalActionBaselineAligned = dock.isBaselineAligned
            dock.orientation = if (horizontal) HORIZONTAL else VERTICAL
            dock.gravity = if (horizontal) Gravity.CENTER_VERTICAL else requireNotNull(originalActionGravity)
            dock.isBaselineAligned = if (horizontal) false else requireNotNull(originalActionBaselineAligned)
            val (save, back) = buttons
            (save.layoutParams as LayoutParams).apply {
                this.width = if (horizontal) 0 else -1; this.height = -2; weight = if (horizontal) 1f else 0f
                marginStart = 0; marginEnd = 0; topMargin = 0; bottomMargin = 0
                resolveLayoutDirection(dock.layoutDirection)
            }
            (back.layoutParams as LayoutParams).apply {
                this.width = if (horizontal) -2 else -1; this.height = -2; weight = 0f
                marginStart = if (horizontal) gap else 0; marginEnd = 0
                topMargin = if (horizontal) 0 else gap; bottomMargin = 0
                resolveLayoutDirection(dock.layoutDirection)
            }
            save.forceLayout(); back.forceLayout(); dock.forceLayout()
        }
        // Always measure the original vertical arrangement first, never the previous compact result.
        setActionRow(false)
        actions?.measure(exact, free)
        val actionParams = actions?.layoutParams as? LayoutParams
        val verticalActionHeight = actions?.measuredHeight ?: 0
        val actionMargins = (actionParams?.topMargin ?: 0) + (actionParams?.bottomMargin ?: 0)
        val editorMinimum = editor?.let { field ->
            field.measure(exact, free)
            maxOf(style.dp(52), field.measuredHeight, field.minimumHeight, field.minHeight)
        } ?: 0
        val panePadding = secondaryBody.paddingTop + secondaryBody.paddingBottom
        val editorBudget = maxOf(editorMinimum, style.dp(48))
        val verticalRequired = panePadding + verticalActionHeight + actionMargins + editorBudget
        val imeVisible = android.os.Build.VERSION.SDK_INT >= 30 &&
            rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true
        val eligible = buttons != null && dock?.childCount == 2 && editor?.isFocused == true
        var saveNaturalWidth = 0
        var backNaturalWidth = 0
        var saveAllocatedWidth = 0
        var compactNaturalWidth = 0
        var compactNaturalHeight = 0
        var compactReason = "not_needed"
        var compact = false
        if (imeVisible && split && eligible && verticalRequired > height) {
            val (save, back) = requireNotNull(buttons)
            save.measure(free, free); back.measure(free, free)
            saveNaturalWidth = maxOf(save.measuredWidth, save.minimumWidth, save.minWidth)
            backNaturalWidth = maxOf(back.measuredWidth, back.minimumWidth, back.minWidth)
            compactNaturalWidth = saveNaturalWidth + gap + backNaturalWidth
            if (compactNaturalWidth <= innerWidth) {
                saveAllocatedWidth = innerWidth - backNaturalWidth - gap
                save.measure(View.MeasureSpec.makeMeasureSpec(saveAllocatedWidth, View.MeasureSpec.EXACTLY), free)
                back.measure(View.MeasureSpec.makeMeasureSpec(backNaturalWidth, View.MeasureSpec.EXACTLY), free)
                compactNaturalHeight = maxOf(save.measuredHeight, back.measuredHeight)
                if (height - panePadding - actionMargins - compactNaturalHeight >= editorBudget) {
                    compact = true; compactReason = "enabled"
                } else compactReason = "compact_budget_insufficient"
            } else compactReason = "natural_width_insufficient"
        } else if (!imeVisible) compactReason = "ime_hidden"
        else if (!split) compactReason = "not_split"
        else if (!eligible) compactReason = "not_editing"
        if (compact) setActionRow(true) else setActionRow(false)
        actions?.measure(exact, free)
        var actionHeight = actions?.measuredHeight ?: 0
        if (compact && height - panePadding - actionMargins - actionHeight < editorBudget) {
            compact = false; compactReason = "measured_compact_budget_insufficient"
            setActionRow(false); actions?.measure(exact, free)
            actionHeight = actions?.measuredHeight ?: 0
        }
        val minimumSecondary = panePadding + actionHeight + actionMargins + editorBudget
        val stackedGap = if (split) 0 else style.dp(24)
        val secondaryHeight = if (split) height else minOf(minimumSecondary, (height - stackedGap).coerceAtLeast(0))
        val primaryHeight = (height - secondaryHeight - stackedGap).coerceAtLeast(0)
        orientation = if (split) HORIZONTAL else VERTICAL
        primaryBody.layoutParams = if (split) LayoutParams(0, -1, 1f) else LayoutParams(-1, primaryHeight)
        secondaryBody.layoutParams = if (split) LayoutParams(secondaryWidth, -1).apply { marginStart = style.dp(24) }
            else LayoutParams(-1, secondaryHeight).apply { topMargin = stackedGap }
        // The budget probe measured the real editor with a different width spec.
        // Invalidate every cached ancestor up to this bounded pane, including the
        // unit wrapper/dose row/local ScrollView, before the actual layout pass.
        var measured: View? = editor
        while (measured != null) {
            measured.forceLayout()
            if (measured === secondaryBody) break
            measured = measured.parent as? View
        }
        // These are direct pane children, never descendants of an UNSPEC supporting ScrollView.
        primaryBody.forceLayout(); secondaryBody.forceLayout()
        evidence.put("workspaceAvailableWidthPx", width).put("workspaceAvailableHeightPx", height)
            .put("workspaceWidthFirstSplit", split).put("secondaryWidthPx", secondaryWidth).put("secondaryHeightPx", secondaryHeight)
            .put("secondaryPaddingPx", panePadding).put("actionsNaturalHeightPx", actionHeight).put("actionsMarginsPx", actionMargins)
            .put("editorNaturalMinimumPx", editorMinimum).put("minimumSecondaryHeightPx", minimumSecondary)
            .put("secondaryBudgetSatisfiable", secondaryHeight >= minimumSecondary)
            .put("supportViewportBudgetPx", (secondaryHeight - panePadding - actionHeight - actionMargins).coerceAtLeast(0))
            .put("actionRowImeVisible",imeVisible).put("actionRowEligible",eligible)
            .put("actionsVerticalRequiredPx",verticalRequired).put("actionsVerticalNaturalHeightPx",verticalActionHeight)
            .put("actionsInnerWidthPx",innerWidth).put("saveNaturalWidthPx",saveNaturalWidth)
            .put("backNaturalWidthPx",backNaturalWidth).put("saveAllocatedWidthPx",saveAllocatedWidth)
            .put("actionsCompactNaturalWidthPx",compactNaturalWidth).put("actionsCompactNaturalHeightPx",compactNaturalHeight)
            .put("actionsCompactEnabled",compact).put("actionsCompactReason",compactReason)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        actions?.let { dock ->
            val location=IntArray(2); dock.getLocationOnScreen(location)
            evidence.put("actionsFinalRectPx",org.json.JSONArray(listOf(location[0],location[1],
                location[0]+dock.width,location[1]+dock.height))).put("actionsFinalHeightPx",dock.height)
        }
    }
}
