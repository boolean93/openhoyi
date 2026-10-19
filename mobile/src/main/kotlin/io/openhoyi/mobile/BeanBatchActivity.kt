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
    private val style by lazy { TaskProductVisualStyle(this) }
    val productLayoutEvidence = org.json.JSONObject()
    private fun render() {
        val formStyle = TaskProductFormStyle(this)
        val body = style.page(this, getString(R.string.beans_batch_title))
        val inventory = beanInventory(body) ?: return
        val id = intent.getStringExtra(BATCH_ID)
        val item = inventory.batches().find { it.batch.id == id }
        if (item == null) { style.add(body, style.text(getString(R.string.beans_missing))); return }
        val free = android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
        fun pair(first: android.view.View, second: android.view.View, key: String, expandFirst: Boolean) = object : LinearLayout(this) {
            init { tag = "product/batch/$key"; isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
                addView(first); addView(second) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                first.measure(free, free); second.measure(free, free)
                val naturalWidth = first.measuredWidth + second.measuredWidth + style.dp(16)
                val available = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
                val inline = naturalWidth <= available
                orientation = if (inline) HORIZONTAL else VERTICAL
                (first.layoutParams as LayoutParams).apply {
                    width = if (!inline) -1 else if (expandFirst) 0 else -2
                    height = -2; weight = if (inline && expandFirst) 1f else 0f
                }
                val direction = this.layoutDirection
                (second.layoutParams as LayoutParams).apply {
                    width = if (!inline) -1 else if (expandFirst) -2 else 0
                    height = -2; weight = if (inline && !expandFirst) 1f else 0f
                    marginStart = if (inline) style.dp(16) else 0; marginEnd = 0
                    topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(direction)
                }
                productLayoutEvidence.put(key, org.json.JSONObject().put("naturalWidthPx", naturalWidth).put("availableWidthPx", available).put("inline", inline))
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        fun rules(columns: Boolean = false) = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = this@BeanBatchActivity.style.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                val y = bounds.bottom - paint.strokeWidth / 2f
                if (!columns) canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint)
                else {
                    val parent = callback as? LinearLayout ?: return
                    for (index in 1 until parent.childCount) {
                        val child = parent.getChildAt(index)
                        val x = if (parent.layoutDirection == android.view.View.LAYOUT_DIRECTION_RTL) child.right else child.left
                        canvas.drawLine(x.toFloat(), 0f, x.toFloat(), bounds.bottom.toFloat(), paint)
                    }
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val header = (body.tag as BeanPageRegions).header.apply { isBaselineAligned = false }
        val words = (0 until header.childCount).map(header::getChildAt).filterIsInstance<LinearLayout>().first()
        val pageTitle = words.getChildAt(0) as android.widget.TextView
        words.removeView(pageTitle)
        val identity = style.text(item.batch.bean.name + " · " + getString(R.string.beans_batch_id, beanBatchNumber(inventory, item.batch.id)),
            TaskProductVisualStyle.body, "product/batch/identity")
        words.addView(pair(pageTitle, identity, "identity-layout", false), LinearLayout.LayoutParams(-1, -2))
        style.action(header, getString(R.string.ui_full_details), "product/batch/details", top = 0) {
            val details = listOf(R.string.beans_purchased to item.batch.purchasedOn,
                R.string.beans_roasted to item.batch.roastedOn, R.string.beans_opened to item.batch.openedOn)
                .joinToString("\n") { (label, date) -> getString(R.string.beans_date_value, getString(label), date?.toString() ?: getString(R.string.beans_not_set)) }
            beanDetails(getString(R.string.beans_batch_title), item.batch.bean.name + "\n" + details)
        }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(16) }
            HomeVisualIcon.buttonIcon(this, "chevron", atEnd = true) }
        val summary = style.row("product/batch/summary").apply { foreground = rules(true) }
        class MetricCell(label: String, val value: android.view.View, val index: Int) : LinearLayout(this@BeanBatchActivity) {
            var inline = false
            val caption = style.text(label, TaskProductVisualStyle.auxiliary, secondary = true)
            init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
                setPaddingRelative(if (index == 0) 0 else style.dp(16), 0, if (index == 2) 0 else style.dp(16), 0)
                addView(caption); addView(value) }
            fun fits(width: Int): Boolean {
                caption.measure(free, free); value.measure(free, free)
                return caption.measuredWidth + style.dp(8) + value.measuredWidth + paddingLeft + paddingRight <= width
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                orientation = if (inline) HORIZONTAL else VERTICAL
                (caption.layoutParams as LayoutParams).apply { width = -2; height = -2; weight = 0f }
                val direction = this.layoutDirection
                (value.layoutParams as LayoutParams).apply { width = if (inline) 0 else -1; height = -2; weight = if (inline) 1f else 0f
                    marginStart = if (inline) style.dp(8) else 0; marginEnd = 0
                    topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(direction) }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        val ratio = if (item.batch.initialMg > 0) item.balanceMg.toDouble() / item.batch.initialMg else null
        val ratioLine = style.row("product/batch/ratio").apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        if (ratio != null) ratioLine.addView(android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000; progress = (ratio.coerceIn(0.0, 1.0) * max).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(style.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(style.disabled)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO; setPadding(0, 0, 0, 0)
        }, LinearLayout.LayoutParams(0, style.dp(8), 1f).apply { marginEnd = style.dp(16) })
        ratioLine.addView(style.text(ratio?.let { java.text.NumberFormat.getPercentInstance(resources.configuration.locales[0]).format(it) } ?: "—",
            TaskProductVisualStyle.body, "product/batch/ratio/value"), LinearLayout.LayoutParams(-2, -2))
        val metrics = listOf(
            MetricCell(getString(R.string.beans_balance_label), style.quantity(BeanQuantity.formatGrams(item.balanceMg), "g", TaskProductVisualStyle.largeValue, "product/batch/balance"), 0),
            MetricCell(getString(R.string.batch_initial_weight), style.quantity(BeanQuantity.formatGrams(item.batch.initialMg), "g", TaskProductVisualStyle.title.copy(weight = 400), "product/batch/initial"), 1),
            MetricCell(getString(R.string.batch_remaining_ratio), ratioLine, 2))
        metrics.forEachIndexed { index, cell -> summary.addView(cell, LinearLayout.LayoutParams(0, -2, if (index == 2) 2f else 1f)) }
        val dates = style.row("product/batch/dates").apply { foreground = rules(true) }
        listOf(R.string.beans_purchased to item.batch.purchasedOn, R.string.beans_roasted to item.batch.roastedOn,
            R.string.beans_opened to item.batch.openedOn).forEachIndexed { index, (label, date) ->
            val cell = style.column().apply { setPaddingRelative(if (index == 0) 0 else style.dp(16), 0, style.dp(16), 0) }
            dates.addView(cell, LinearLayout.LayoutParams(0, -2, 1f))
            style.add(cell, style.text(getString(label), TaskProductVisualStyle.auxiliary, "product/batch/date/$index/label", true))
            style.add(cell, style.text(date?.toString() ?: getString(R.string.beans_not_set), TaskProductVisualStyle.body, "product/batch/date/$index/value"), 8)
        }
        val dateRegion = pair(style.text(getString(R.string.batch_information), TaskProductVisualStyle.section), dates, "date-layout", false)
        val ledgerButton = style.button(android.widget.Button(this)).apply {
            text = getString(R.string.beans_ledger); tag = "product/batch/ledger"
            HomeVisualIcon.buttonIcon(this, "chevron", atEnd = true)
            setOnClickListener { startActivity(Intent(this@BeanBatchActivity, BeanLedgerActivity::class.java).putExtra(BATCH_ID, item.batch.id)) }
        }
        val recentHeading = pair(style.text(getString(R.string.batch_recent_events), TaskProductVisualStyle.section), ledgerButton, "recent-heading", true)
        fun eventRow(values: List<String>, heading: Boolean, key: String): LinearLayout = style.row(key).apply {
            setPadding(0, style.dp(8), 0, style.dp(8)); foreground = rules()
            if (heading) setBackgroundColor(style.disabled) else minimumHeight = style.dp(48)
            values.forEachIndexed { index, value -> addView(style.text(value,
                if (heading) TaskProductVisualStyle.auxiliary.copy(weight = 500) else TaskProductVisualStyle.body, secondary = heading).apply {
                setPaddingRelative(style.dp(8), 0, style.dp(8), 0)
                if (!heading) { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details" }
            }, LinearLayout.LayoutParams(0, -2, if (index == 2) 2f else 1f)) }
        }
        val tableHeading = eventRow(listOf(getString(R.string.ledger_type), getString(R.string.ledger_change), getString(R.string.beans_reason)), true, "product/batch/events/header")
        val recent = style.column("product/batch/events")
        val recentScroll = ScrollView(this).apply { tag = "product/batch/events-scroll"; addView(recent) }
        val events = inventory.events().asReversed().filter { it.batchId == item.batch.id }.take(3)
        if (events.isEmpty()) style.add(recent, style.text(getString(R.string.beans_ledger_empty), TaskProductVisualStyle.body, secondary = true), 8)
        events.forEach { event ->
            val kind = getString(when (event.kind) {
                io.openhoyi.bean.InventoryEventKind.ADD -> R.string.beans_event_add
                io.openhoyi.bean.InventoryEventKind.CONSUME -> R.string.beans_event_consume
                io.openhoyi.bean.InventoryEventKind.ADJUST -> R.string.beans_event_adjust
            })
            val change = (if (event.deltaMg > 0) "+" else "") + BeanQuantity.formatGrams(event.deltaMg) + " g"
            val values = listOf(kind, change, event.reason ?: "—")
            recent.addView(eventRow(values, false, "product/batch/events/row/${event.eventId}").apply {
                isFocusable = true; minimumHeight = style.dp(48)
                background = android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(style.selected),
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), style.shape(style.surface, true, 0))
                        addState(intArrayOf(), style.shape(style.surface, radius = 0))
                    }, null)
                contentDescription = values.joinToString(" · ")
                setOnClickListener { beanDetails(getString(R.string.beans_ledger_title), values.joinToString("\n") + "\n" +
                    getString(R.string.beans_event_balance, BeanQuantity.formatGrams(event.balanceMg))) }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        var contentViewport = 0
        var naturalContent = false
        val content = object : LinearLayout(this) {
            init { orientation = VERTICAL; tag = "product/batch/content"
                addView(summary, LayoutParams(-1, -2)); addView(dateRegion, LayoutParams(-1, -2).apply { topMargin = style.dp(16) })
                addView(recentHeading, LayoutParams(-1, -2).apply { topMargin = style.dp(16) })
                addView(tableHeading, LayoutParams(-1, -2).apply { topMargin = style.dp(8) })
                addView(recentScroll, LayoutParams(-1, 0, 1f)) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = android.view.View.MeasureSpec.getSize(widthMeasureSpec)
                val height = contentViewport
                fun force(view: android.view.View) { if (view is android.view.ViewGroup) for (i in 0 until view.childCount) force(view.getChildAt(i)); view.forceLayout() }
                fun natural(view: android.view.View): Int { force(view); view.measure(widthMeasureSpec, free)
                    val lp = view.layoutParams as LayoutParams; return view.measuredHeight + lp.topMargin + lp.bottomMargin }
                metrics.forEach { it.inline = false }
                val verticalSummary = natural(summary)
                val other = natural(dateRegion) + natural(recentHeading) + natural(tableHeading)
                val eventHeights = (0 until recent.childCount).map { index ->
                    val row = recent.getChildAt(index); row.measure(widthMeasureSpec, free); row.measuredHeight
                }
                val firstTwo = eventHeights.take(2).sum()
                val fits = metrics.mapIndexed { index, cell -> cell.fits(width * (if (index == 2) 2 else 1) / 4) }
                val inline = verticalSummary + other + firstTwo > height && fits.all { it }
                metrics.forEach { it.inline = inline }; val resolvedSummary = natural(summary)
                val minimum = resolvedSummary + other + (eventHeights.firstOrNull() ?: 0)
                naturalContent = minimum > height
                (recentScroll.layoutParams as LayoutParams).apply {
                    this.height = if (naturalContent) eventHeights.sum() else 0
                    weight = if (naturalContent) 0f else 1f
                }
                recentScroll.forceLayout()
                productLayoutEvidence.put("availableContentPx", height).put("summaryVerticalNaturalPx", verticalSummary)
                    .put("summaryResolvedNaturalPx", resolvedSummary).put("otherNaturalPx", other).put("firstTwoEventsNaturalPx", firstTwo)
                    .put("verticalWithTwoEventsPx", verticalSummary + other + firstTwo).put("summaryInlineFits", org.json.JSONArray(fits)).put("summaryInline", inline)
                    .put("resolvedWithTwoEventsPx", resolvedSummary + other + firstTwo).put("resolvedChromePx", resolvedSummary + other)
                    .put("minimumContentNaturalPx", minimum).put("fullContentNaturalPx", resolvedSummary + other + eventHeights.sum())
                    .put("naturalContentScroll", naturalContent)
                super.onMeasure(widthMeasureSpec, if (naturalContent) free else android.view.View.MeasureSpec.makeMeasureSpec(height, android.view.View.MeasureSpec.EXACTLY))
            }
        }
        val middle = object : ScrollView(this) {
            override fun measureChildWithMargins(child: android.view.View, parentWidthMeasureSpec: Int, widthUsed: Int,
                parentHeightMeasureSpec: Int, heightUsed: Int) {
                val params = child.layoutParams as android.view.ViewGroup.MarginLayoutParams
                val width = getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + params.leftMargin + params.rightMargin + widthUsed, params.width)
                child.measure(width, if (naturalContent) free else android.view.View.MeasureSpec.makeMeasureSpec(contentViewport, android.view.View.MeasureSpec.EXACTLY))
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                contentViewport = (android.view.View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
                content.forceLayout(); content.measure(widthMeasureSpec, free)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { tag = "product/batch/content-scroll"; isFillViewport = false; addView(content) }
        body.addView(middle, LinearLayout.LayoutParams(-1, 0, 1f))
        val actions = style.row()
        style.action(actions, getString(R.string.beans_adjust), "product/batch/adjust", primary = true, top = 0) {
            draftTarget = null; draftReason = ""; adjustment(inventory, item.batch.id)
        }.apply { layoutParams = LinearLayout.LayoutParams(-2, -2); HomeVisualIcon.buttonIcon(this, "edit") }
        val notice = style.text(getString(R.string.batch_ledger_retained), TaskProductVisualStyle.auxiliary, "product/batch/notice", true).apply {
            setCompoundDrawablesRelativeWithIntrinsicBounds(HomeVisualIcon.drawable(this@BeanBatchActivity, "info", style.muted), null, null, null)
            compoundDrawablePadding = style.dp(8)
        }
        body.addView(formStyle.footer(notice, actions, "batch", topRule = true), LinearLayout.LayoutParams(-1, -2))
        body.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            productLayoutEvidence.put("headerActualHeightPx", header.height).put("footerActualHeightPx", body.getChildAt(body.childCount - 1).height)
                .put("eventsViewportActualPx", recentScroll.height).put("eventsContentActualPx", recent.height)
        }
    }

    private fun adjustment(inventory: BeanInventory, batchId: String) {
        var current = inventory.batches().find { it.batch.id == batchId } ?: return
        adjustmentOpen = true
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            isFocusableInTouchMode = true; tag = "task-inline-form"
            setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(16))
        }
        style.add(form, style.text(getString(R.string.beans_adjust_hint), TaskProductVisualStyle.auxiliary, secondary = true))
        val fields = style.column("bean-full-field").also { style.add(form, it, 16) }
        val target = beanInput(fields, R.string.beans_target, true).apply { setText(draftTarget ?: BeanQuantity.formatGrams(current.balanceMg)) }
        val reason = beanInput(fields, R.string.beans_reason).apply { setText(draftReason) }
        val formStyle = TaskProductFormStyle(this)
        listOf(target to "target", reason to "reason").forEach { (field, key) ->
            formStyle.input(field, "batch", key)
            (0 until fields.childCount).map(fields::getChildAt).filterIsInstance<android.widget.TextView>().single { it.labelFor == field.id }
                .let { label -> formStyle.label(label); if (key == "reason") (label.layoutParams as LinearLayout.LayoutParams).topMargin = style.dp(16) }
        }
        formStyle.unit(target, "g", "batch", "target")
        targetField = target; reasonField = reason
        val error = formStyle.error(beanError(form))
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
                styleDialog(preview)
            }
        }
        dialogs += dialog
        dialog.setOnDismissListener {
            draftTarget = target.text.toString(); draftReason = reason.text.toString()
            targetField = null; reasonField = null; adjustmentOpen = false
            dialogs.remove(dialog)
        }
        dialog.show()
        styleDialog(dialog)
    }
    private fun styleDialog(dialog: AlertDialog) {
        dialog.window?.setBackgroundDrawable(style.shape(style.surface))
        val titleId = resources.getIdentifier("alertTitle", "id", "android")
        dialog.findViewById<android.widget.TextView>(titleId)?.let { style.apply(it, TaskProductVisualStyle.section) }
        dialog.findViewById<android.widget.TextView>(android.R.id.message)?.let { style.apply(it, TaskProductVisualStyle.body) }
        listOf(AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_POSITIVE).forEach { which ->
            dialog.getButton(which)?.let { style.button(it, primary = which == AlertDialog.BUTTON_POSITIVE,
                outline = which == AlertDialog.BUTTON_NEGATIVE) }
        }
    }
}
