package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
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
    private var saved = false
    private var section = 0
    private var selectedBeanId: String? = null
    private var form: TaskFormSections? = null
    private val fields = linkedMapOf<String, EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        eventId = savedInstanceState?.getString("eventId") ?: eventId
        batchId = savedInstanceState?.getString("batchId") ?: batchId
        newBeanId = savedInstanceState?.getString("newBeanId") ?: newBeanId
        selectedBeanId = savedInstanceState?.getString("selectedBeanId")
        saved = savedInstanceState?.getBoolean("saved") ?: false
        if (saved) { finish(); return }
        val body = beanPage(getString(R.string.beans_entry_title))
        val inventory = beanInventory(body) ?: return
        section = savedInstanceState?.getInt("section") ?: 0
        val sections = TaskFormSections(this, body, listOf(getString(R.string.task_form_bean_identity), getString(R.string.task_form_stock), getString(R.string.task_form_dates)), section) { section = it }
        form = sections
        val card = sections.panels[0]
        val beans = inventory.batches().map { it.batch.bean }.distinctBy { it.id }
        val selector = Spinner(this).apply {
            adapter = beanChoiceAdapter(
                listOf(getString(R.string.beans_new_identity)) + beans.map { it.name })
            minimumHeight = HoyiUi.dp(this@BeanEntryActivity, 52)
            contentDescription = getString(R.string.beans_identity)
            card.addView(this)
        }
        fun input(key: String, label: Int, numeric: Boolean = false) = beanInput(when (key) { "quantity" -> sections.panels[1]; "purchased", "roasted", "opened" -> sections.panels[2]; else -> card }, label, numeric).also {
            fields[key] = it
            it.setText(savedInstanceState?.getString(key).orEmpty())
        }
        val name = input("name", R.string.beans_name)
        val quantity = input("quantity", R.string.beans_initial_input, true)
        listOf(name, quantity).forEach { field ->
            val parent = field.parent as android.widget.LinearLayout
            (0 until parent.childCount).map { parent.getChildAt(it) }
                .filterIsInstance<android.widget.TextView>().single { it.labelFor == field.id }.apply {
                    setPadding(0, HoyiUi.dp(this@BeanEntryActivity, 4), 0, HoyiUi.dp(this@BeanEntryActivity, 4))
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
            dateRow.addView(field, android.widget.LinearLayout.LayoutParams(0, -2, 2f))
        }
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBeanId = beans.getOrNull(position - 1)?.id
                name.isEnabled = selectedBeanId == null
                if (position > 0) name.setText(beans[position - 1].name)
            }
        }
        selector.setSelection(beans.indexOfFirst { it.id == selectedBeanId }.let { if (it < 0) 0 else it + 1 })
        sections.restore(savedInstanceState, fields)
        val error = beanError(body)
        val save = HoyiUi.button(this, sections.actions, getString(R.string.beans_save), primary = true) {}.apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(HoyiUi.dp(this@BeanEntryActivity, 240), -2)
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
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("eventId", eventId)
        outState.putString("batchId", batchId)
        outState.putString("newBeanId", newBeanId)
        outState.putString("selectedBeanId", selectedBeanId)
        outState.putBoolean("saved", saved)
        outState.putInt("section", section)
        fields.forEach { (key, field) -> outState.putString(key, field.text.toString()) }
        form?.save(outState, fields)
    }
}
