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
        val body = beanPage(getString(R.string.beans_entry_title))
        val inventory = beanInventory(body) ?: return
        section = savedInstanceState?.getInt("section") ?: 0
        val sections = TaskFormSections(this, body, listOf(getString(R.string.task_form_bean_identity), getString(R.string.task_form_stock), getString(R.string.task_form_dates)), section) { section = it }
        form = sections
        val card = sections.panels[0]
        val beans = inventory.batches().map { it.batch.bean }.distinctBy { it.id }
        if (beans.isEmpty()) existingMode = false
        val modeRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            isBaselineAligned = false
            background = HoyiUi.shape(this@BeanEntryActivity, R.color.mobile_surface, 8, R.color.mobile_border)
            card.addView(this, android.widget.LinearLayout.LayoutParams(-1, -2).apply {
                bottomMargin = HoyiUi.dp(this@BeanEntryActivity, 8)
            })
        }
        val selector = Spinner(this).apply {
            tag = "bean-existing-choice"
            adapter = beanChoiceAdapter(beans.map { it.name })
            minimumHeight = HoyiUi.dp(this@BeanEntryActivity, 52)
            contentDescription = getString(R.string.beans_identity)
            card.addView(this)
        }
        fun input(key: String, label: Int, numeric: Boolean = false) = beanInput(when (key) { "quantity" -> sections.panels[1]; "purchased", "roasted", "opened" -> sections.panels[2]; else -> card }, label, numeric).also {
            fields[key] = it
            it.textSize = TaskVisualTokens.BODY_SP.toFloat()
            it.setText(savedInstanceState?.getString(key).orEmpty())
        }
        val name = input("name", R.string.beans_name)
        val quantity = input("quantity", R.string.beans_initial_input, true)
        listOf(name, quantity).forEach { field ->
            val parent = field.parent as android.widget.LinearLayout
            (0 until parent.childCount).map { parent.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().single { it.labelFor == field.id }.apply {
                    textSize = TaskVisualTokens.SUPPORTING_SP.toFloat()
                    setPadding(0, 0, 0, HoyiUi.dp(this@BeanEntryActivity, TaskVisualTokens.RELATED_GAP_DP))
                }
        }
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
            label.textSize = 14f
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
                imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_muted))
                setPadding(HoyiUi.dp(this@BeanEntryActivity, 12), HoyiUi.dp(this@BeanEntryActivity, 12),
                    HoyiUi.dp(this@BeanEntryActivity, 12), HoyiUi.dp(this@BeanEntryActivity, 12))
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@BeanEntryActivity, android.R.color.transparent, 8, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@BeanEntryActivity, android.R.color.transparent, 8))
                }
                background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)), states,
                    HoyiUi.shape(this@BeanEntryActivity, android.R.color.white, 8))
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
            name.setText(if (existingMode) bean?.name.orEmpty() else newNameDraft)
            modeButtons.forEach { (existing, button) ->
                button.isSelected = existing == existingMode
                button.setTextColor(getColor(if (!button.isEnabled) R.color.mobile_muted else R.color.mobile_text))
                val fill = if (button.isSelected) R.color.mobile_accent_soft else android.R.color.transparent
                val states = android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_focused), HoyiUi.shape(this@BeanEntryActivity, fill, 8, R.color.mobile_accent))
                    addState(intArrayOf(), HoyiUi.shape(this@BeanEntryActivity, fill, 8))
                }
                button.background = android.graphics.drawable.RippleDrawable(
                    android.content.res.ColorStateList.valueOf(getColor(R.color.mobile_accent_soft)), states, null)
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
                asTaskTextAction()
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
        sections.panels[2].addView(View(this).apply {
            setBackgroundColor(getColor(R.color.mobile_border))
        }, 0, android.widget.LinearLayout.LayoutParams(-1, HoyiUi.dp(this, 1)))
        sections.restore(savedInstanceState, fields)
        val error = beanError(body)
        HoyiUi.label(this, sections.actions, getString(R.string.task_form_new_batch_notice),
            TaskVisualTokens.SUPPORTING_SP, muted = true).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2)
            setPadding(0, 0, 0, HoyiUi.dp(this@BeanEntryActivity, TaskVisualTokens.RELATED_GAP_DP))
        }
        val dock = TaskActionDock(this, edgeInsetDp = 0)
        sections.actions.addView(dock, android.widget.LinearLayout.LayoutParams(-1, -2))
        val actionRow = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            isBaselineAligned = false
        }
        dock.addView(actionRow, android.widget.LinearLayout.LayoutParams(-1, -2))
        HoyiUi.button(this, actionRow, getString(R.string.beans_cancel)) { finish() }.apply {
            asTaskTextAction()
            layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 1f).apply {
                marginEnd = HoyiUi.dp(this@BeanEntryActivity, TaskVisualTokens.RELATED_GAP_DP)
            }
        }
        val save = HoyiUi.button(this, actionRow, getString(R.string.beans_save), primary = true) {}.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(0, -2, 2f)
        }
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
