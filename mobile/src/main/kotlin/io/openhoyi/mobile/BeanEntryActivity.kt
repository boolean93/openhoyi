package io.openhoyi.mobile

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.EditText
import android.widget.Spinner
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import java.time.LocalDate
import java.util.UUID

class BeanEntryActivity : ThemedActivity() {
    private var eventId = UUID.randomUUID().toString()
    private var batchId = UUID.randomUUID().toString()
    private var newBeanId = UUID.randomUUID().toString()
    private var dateDialog: DatePickerDialog? = null
    private var saved = false
    private var section = 0
    private var selectedBeanId: String? = null
    private var rememberedBeanId: String? = null
    private var existingMode = false
    private var newNameDraft = ""
    private var form: TaskFormSections? = null
    private val fields = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = savedInstanceState?.getString("eventId") ?: eventId
        batchId = savedInstanceState?.getString("batchId") ?: batchId
        newBeanId = savedInstanceState?.getString("newBeanId") ?: newBeanId
        selectedBeanId = savedInstanceState?.getString("selectedBeanId")
        existingMode = savedInstanceState?.getBoolean("existingMode") ?: (selectedBeanId != null)
        rememberedBeanId = savedInstanceState?.getString("rememberedBeanId") ?: selectedBeanId
        newNameDraft = savedInstanceState?.getString("newNameDraft") ?: savedInstanceState?.getString("name").orEmpty()
        saved = savedInstanceState?.getBoolean("saved") ?: false
        if (saved) { finish(); return }
        val style = TaskProductFormStyle(this)
        val body = style.visual.page(this, getString(R.string.beans_entry_title))
        val inventory = beanInventory(body) ?: return
        section = savedInstanceState?.getInt("section") ?: 0
        val sections = TaskFormSections(this, body, listOf(getString(R.string.task_form_bean_identity), getString(R.string.task_form_stock), getString(R.string.task_form_dates)), section, style) { section = it }
        form = sections
        val card = sections.panels[0]
        val beans = inventory.batches().map { it.batch.bean }.distinctBy { it.id }
        if (beans.isEmpty()) existingMode = false
        val modeRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            isBaselineAligned = false
            background = style.visual.shape(style.visual.surface, true, 8)
            sections.modeHost.addView(this, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = HoyiUi.dp(this@BeanEntryActivity, 8)
            })
        }
        val selector = Spinner(this).apply {
            tag = "bean-existing-choice"
            adapter = style.choiceAdapter(beans.map { it.name })
            minimumHeight = HoyiUi.dp(this@BeanEntryActivity, 52)
            contentDescription = getString(R.string.beans_identity)
            card.addView(this)
        }
        fun input(key: String, label: Int, numeric: Boolean = false) = beanInput(when (key) { "quantity" -> sections.panels[1]; "purchased", "roasted", "opened" -> sections.panels[2]; else -> card }, label, numeric).also {
            fields[key] = it
            style.input(it, "bean-entry", key)
            it.setText(savedInstanceState?.getString(key).orEmpty())
        }
        val name = input("name", R.string.beans_name)
        val quantity = input("quantity", R.string.beans_initial_input, true)
        listOf(name, quantity).forEach { field ->
            val parent = field.parent as android.widget.LinearLayout
            (0 until parent.childCount).map { parent.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().single { it.labelFor == field.id }.apply {
                    style.label(this)
                }
        }
        style.unit(quantity, "g", "bean-entry", "quantity")
        val purchased = input("purchased", R.string.beans_purchased)
        val roasted = input("roasted", R.string.beans_roasted)
        val opened = input("opened", R.string.beans_opened)
        listOf(purchased, roasted, opened).forEach { field ->
            field.hint = "YYYY-MM-DD"
            field.tooltipText = getString(R.string.task_form_optional_iso_dates)
            val parent = field.parent as android.widget.LinearLayout
            val label = (0 until parent.childCount).map { parent.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().single { it.labelFor == field.id }
            parent.removeView(label); parent.removeView(field)
            val dateRow = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isBaselineAligned = false
                parent.addView(this, android.widget.LinearLayout.LayoutParams(-1, -2).apply { topMargin = HoyiUi.dp(this@BeanEntryActivity, 8) })
            }
            style.label(label, belowGap = false)
            label.setPadding(0, 0, HoyiUi.dp(this, 16), 0)
            dateRow.addView(label, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
            val dateInput = android.widget.FrameLayout(this)
            dateRow.addView(dateInput, android.widget.LinearLayout.LayoutParams(0, -2, 2f))
            field.setPaddingRelative(field.paddingStart, field.paddingTop, HoyiUi.dp(this, 56), field.paddingBottom)
            dateInput.addView(field, android.widget.FrameLayout.LayoutParams(-1, -2))
            val calendar = android.widget.ImageButton(this).apply {
                tag = "bean-date-picker-${fields.entries.single { it.value === field }.key}"
                contentDescription = label.text
                tooltipText = label.text
                setImageResource(R.drawable.form_calendar)
                imageTintList = android.content.res.ColorStateList.valueOf(style.visual.muted)
                setPadding(HoyiUi.dp(this@BeanEntryActivity, 12), HoyiUi.dp(this@BeanEntryActivity, 12),
                    HoyiUi.dp(this@BeanEntryActivity, 12), HoyiUi.dp(this@BeanEntryActivity, 12))
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(style.visual.selected),
                    android.graphics.drawable.StateListDrawable().apply {
                        addState(intArrayOf(android.R.attr.state_focused), style.visual.shape(android.graphics.Color.TRANSPARENT, true, 8))
                        addState(intArrayOf(), style.visual.shape(android.graphics.Color.TRANSPARENT, false, 8))
                    }, style.visual.shape(android.graphics.Color.WHITE, radius = 8))
                setOnClickListener {
                    val initial = runCatching { LocalDate.parse(field.text.toString().trim()) }.getOrNull() ?: LocalDate.now()
                    dateDialog?.dismiss()
                    dateDialog = DatePickerDialog(this@BeanEntryActivity, { _, year, month, day ->
                        field.setText(LocalDate.of(year, month + 1, day).toString())
                    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply { show() }
                }
            }
            dateInput.addView(calendar, android.widget.FrameLayout.LayoutParams(HoyiUi.dp(this, 48), HoyiUi.dp(this, 48),
                android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL))
        }
        val modeButtons = linkedMapOf<Boolean, android.widget.Button>()
        fun renderIdentity() {
            val bean = beans.find { it.id == rememberedBeanId } ?: beans.firstOrNull()
            rememberedBeanId = bean?.id
            selectedBeanId = if (existingMode) bean?.id else null
            selector.visibility = if (existingMode) View.VISIBLE else View.GONE
            name.isEnabled = !existingMode
            name.visibility = if (existingMode) View.GONE else View.VISIBLE
            val nameParent = name.parent as android.widget.LinearLayout
            (0 until nameParent.childCount).map { nameParent.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().single { it.labelFor == name.id }.visibility = name.visibility
            name.setText(if (existingMode) bean?.name.orEmpty() else newNameDraft)
            modeButtons.forEach { (existing, button) ->
                button.isSelected = existing == existingMode
                style.selected(button, button.isSelected)
            }
        }
        listOf(false to R.string.beans_new_identity, true to R.string.beans_existing_identity).forEach { (existing, label) ->
            modeButtons[existing] = HoyiUi.button(this, modeRow, getString(label)) {
                if (existing != existingMode) {
                    if (!existingMode) newNameDraft = name.text.toString()
                    existingMode = existing
                    renderIdentity()
                }
            }.apply {
                tag = if (existing) "bean-mode-existing" else "bean-mode-new"
                layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f)
                style.visual.button(this)
                setPadding(HoyiUi.dp(this@BeanEntryActivity, 8), HoyiUi.dp(this@BeanEntryActivity, 8),
                    HoyiUi.dp(this@BeanEntryActivity, 8), HoyiUi.dp(this@BeanEntryActivity, 8))
                isEnabled = !existing || beans.isNotEmpty()
            }
        }
        val initialChoice = beans.indexOfFirst { it.id == rememberedBeanId }.coerceAtLeast(0)
        if (beans.isNotEmpty()) selector.setSelection(initialChoice)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!existingMode) return
                rememberedBeanId = beans.getOrNull(position)?.id
                renderIdentity()
            }
        }
        renderIdentity()
        sections.restore(savedInstanceState, fields)
        val error = beanError(body)
        style.error(error)
        val actionRow = style.visual.row()
        style.visual.action(actionRow, getString(R.string.beans_cancel), "product/form/bean-entry/cancel", top = 0) { finish() }
            .layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) }
        val save = style.visual.action(actionRow, getString(R.string.beans_save), "product/form/bean-entry/save", primary = true, top = 0) {}
        save.layoutParams = android.widget.LinearLayout.LayoutParams(-2, -2)
        val notice = style.visual.text(getString(R.string.task_form_new_batch_notice), TaskProductVisualStyle.auxiliary,
            "product/form/bean-entry/scope", true)
        sections.actions.addView(style.footer(notice, actionRow, "bean-entry"), android.widget.LinearLayout.LayoutParams(-1, -2))
        save.setOnClickListener {
            if (saved) return@setOnClickListener
            error.text = ""
            val mg = runCatching { BeanQuantity.parseGrams(quantity.text.toString()) }.getOrElse {
                sections.jumpTo(1); error.setText(R.string.beans_bad_quantity); return@setOnClickListener
            }
            if (mg <= 0) { sections.jumpTo(1); error.setText(R.string.beans_positive_quantity); return@setOnClickListener }
            val bean = beans.find { it.id == selectedBeanId } ?: Bean(newBeanId, name.text.toString().trim())
            if (bean.name.isBlank() || bean.name.length > 4096) {
                sections.jumpTo(0); error.setText(R.string.beans_name_required); return@setOnClickListener
            }
            val dates = runCatching { listOf(purchased, roasted, opened).map { field ->
                field.text.toString().trim().takeIf { it.isNotEmpty() }?.let {
                    require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(it))
                    LocalDate.parse(it)
                }
            } }.getOrElse { sections.jumpTo(2); error.setText(R.string.beans_bad_date); return@setOnClickListener }
            save.isEnabled = false
            val result = runCatching { inventory.addBatch(eventId, BeanBatch(batchId, bean, mg, dates[0], dates[1], dates[2])) }
            if (result.isSuccess) { saved = true; finish() }
            else { error.setText(R.string.beans_save_failed); save.isEnabled = true }
        }
    }
    override fun onDestroy() {
        dateDialog?.dismiss()
        dateDialog = null
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("eventId", eventId)
        outState.putString("batchId", batchId)
        outState.putString("newBeanId", newBeanId)
        outState.putString("selectedBeanId", selectedBeanId)
        outState.putString("rememberedBeanId", rememberedBeanId)
        outState.putBoolean("existingMode", existingMode)
        outState.putString("newNameDraft", if (existingMode) newNameDraft else fields["name"]?.text?.toString().orEmpty())
        outState.putBoolean("saved", saved)
        outState.putInt("section", section)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
        form?.save(outState, fields)
    }
}
