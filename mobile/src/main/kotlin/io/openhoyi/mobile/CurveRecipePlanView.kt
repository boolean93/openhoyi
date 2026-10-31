package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** Visible stage cards and water allocation; all values come from immutable recipe metadata. */
internal class CurveRecipePlanView(context: Context, private val style: TaskProductVisualStyle) : LinearLayout(context) {
    private fun dp(value: Int) = style.dp(value)
    private val dark = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    init { orientation = VERTICAL; tag = "product/curve-library/recipe-plan" }

    private fun label(text: String, size: TaskProductVisualStyle.Type = TaskProductVisualStyle.auxiliary,
        color: Int? = null): TextView = style.text(text, size).apply { if (color != null) setTextColor(color) }
    private fun amount(tenths: Int): String = if (tenths % 10 == 0) "${tenths / 10} mL"
        else String.format(Locale.ROOT, "%.1f mL", tenths / 10.0)
    private fun target(stage: CurveStageParameters.Stage): Pair<String, String> = when (stage.unit) {
        CurveStageParameters.Unit.PRESSURE_BAR -> String.format(Locale.ROOT, "%.1f", stage.target / 10.0) to "bar"
        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> String.format(Locale.ROOT, "%.1f", stage.target / 10.0) to "mL/s"
        CurveStageParameters.Unit.RAW_UNKNOWN -> stage.target.toString() to context.getString(R.string.curve_control_mode_raw)
    }

    fun present(item: CurveLibraryItem) {
        removeAllViews()
        val plan = CurveRecipePlan.from(item)
        addView(label(context.getString(R.string.curve_plan_title), TaskProductVisualStyle.section), LayoutParams(-1, -2))
        val stages = plan.preview.stages
        stages.chunked(2).forEachIndexed { rowIndex, pair ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            addView(row, LayoutParams(-1, -2).apply { topMargin = dp(8) })
            pair.forEachIndexed { columnIndex, stage ->
                val index = rowIndex * 2 + columnIndex
                val flow = stage.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND
                val color = if (flow) style.accent else if (stage.unit == CurveStageParameters.Unit.PRESSURE_BAR)
                    style.pressure else style.muted
                val card = LinearLayout(context).apply {
                    orientation = VERTICAL; tag = "product/curve-library/stage/${index + 1}"
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    background = style.shape(if (dark) if (flow) 0xFF193431.toInt() else 0xFF1D2D40.toInt()
                        else if (flow) 0xFFEAF8F5.toInt() else 0xFFEAF2FE.toInt(), true)
                }
                row.addView(card, LayoutParams(0, -2, 1f).apply { if (columnIndex > 0) marginStart = dp(12) })
                card.addView(label(String.format(Locale.ROOT, "%02d", index + 1) + "  " +
                    context.getString(when (stage.unit) {
                        CurveStageParameters.Unit.PRESSURE_BAR -> R.string.curve_plan_pressure_control
                        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> R.string.curve_plan_flow_control
                        CurveStageParameters.Unit.RAW_UNKNOWN -> R.string.curve_control_mode_raw
                    }), TaskProductVisualStyle.action, color))
                val (number, unit) = target(stage)
                val valueRow = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.BOTTOM }
                card.addView(valueRow, LayoutParams(-1, -2).apply { topMargin = dp(8) })
                valueRow.addView(label(number, TaskProductVisualStyle.Type(32, 700, 42), style.ink))
                valueRow.addView(label(unit, TaskProductVisualStyle.auxiliary, color), LayoutParams(-2, -2).apply {
                    marginStart = dp(6)
                })
                val water = plan.stageWaterTenthsMl.getOrNull(index)
                card.addView(label(context.getString(R.string.curve_plan_stage_water) + "  " +
                    (water?.let(::amount) ?: "—"), TaskProductVisualStyle.auxiliary, color),
                    LayoutParams(-1, -2).apply { topMargin = dp(8) })
            }
        }
        val assigned = plan.assignedWaterTenthsMl
        addView(label(context.getString(R.string.curve_plan_water_allocation), TaskProductVisualStyle.section),
            LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val limit = plan.totalWaterLimitMl?.let { context.getString(R.string.curve_plan_total_limit, it) } ?: "—"
        addView(label(context.getString(R.string.curve_plan_assigned_total, amount(assigned), limit)), LayoutParams(-1, -2))
        if (assigned > 0 && plan.stageWaterTenthsMl.size == stages.size) {
            plan.stageWaterTenthsMl.chunked(2).forEachIndexed { rowIndex, pair ->
                val labels = LinearLayout(context).apply { orientation = HORIZONTAL }
                addView(labels, LayoutParams(-1, -2).apply { topMargin = dp(4) })
                pair.forEachIndexed { columnIndex, water ->
                    val index = rowIndex * 2 + columnIndex
                    val color = when (stages[index].unit) {
                        CurveStageParameters.Unit.PRESSURE_BAR -> style.pressure
                        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> style.accent
                        CurveStageParameters.Unit.RAW_UNKNOWN -> style.muted
                    }
                    labels.addView(label(context.getString(R.string.curve_plan_stage_water_label,
                        index + 1, amount(water), String.format(Locale.ROOT, "%.1f", water * 100.0 / assigned)), color = color),
                        LayoutParams(0, -2, 1f))
                }
            }
            val bar = LinearLayout(context).apply { orientation = HORIZONTAL; tag = "product/curve-library/water-allocation" }
            addView(bar, LayoutParams(-1, dp(16)).apply { topMargin = dp(8) })
            plan.stageWaterTenthsMl.forEachIndexed { index, water ->
                if (water > 0) bar.addView(View(context).apply {
                    background = style.shape(when (stages[index].unit) {
                        CurveStageParameters.Unit.PRESSURE_BAR -> style.pressure
                        CurveStageParameters.Unit.FLOW_ML_PER_SECOND -> style.accent
                        CurveStageParameters.Unit.RAW_UNKNOWN -> style.muted
                    }, true, 0)
                }, LayoutParams(0, -1, water.toFloat()))
            }
        } else {
            addView(label(context.getString(R.string.curve_plan_allocation_unavailable), color = style.muted),
                LayoutParams(-1, -2).apply { topMargin = dp(8) })
        }
        val info = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
        addView(info, LayoutParams(-1, -2).apply { topMargin = dp(16) })
        fun fact(title: Int, value: String) {
            val cell = LinearLayout(context).apply {
                orientation = VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8))
                background = style.shape(style.surface, true)
            }
            info.addView(cell, LayoutParams(0, -2, 1f).apply { if (info.childCount > 0) marginStart = dp(8) })
            cell.addView(label(context.getString(title), color = style.muted))
            cell.addView(label(value, TaskProductVisualStyle.action), LayoutParams(-1, -2).apply { topMargin = dp(4) })
        }
        fact(R.string.curve_plan_preinfusion, plan.preinfusionSeconds?.let {
            context.getString(R.string.curve_plan_seconds_setting, it) } ?: "—")
        fact(R.string.curve_plan_end_condition, plan.endMode ?: when {
            item.factoryCurve?.weightTenthsGram == 0 -> context.getString(R.string.curve_plan_end_water)
            item.factoryCurve != null -> context.getString(R.string.curve_plan_end_weight_conditional)
            else -> context.getString(R.string.curve_plan_end_unknown)
        })
        fact(R.string.curve_plan_weight, plan.targetWeightHundredthsGram?.let {
            if (it == 0) context.getString(R.string.curve_details_no_weight)
            else String.format(Locale.ROOT, "%.2f g", it / 100.0)
        } ?: "—")
        addView(label(context.getString(R.string.curve_plan_first_stage_setting) + "  " +
            (plan.firstStageDurationSettingSeconds?.let { context.getString(R.string.curve_plan_seconds_setting, it) } ?: "—")),
            LayoutParams(-1, -2).apply { topMargin = dp(8) })
        plan.advice?.takeIf { it.isNotBlank() }?.let { advice ->
            addView(label(context.getString(R.string.curve_plan_advice), TaskProductVisualStyle.action),
                LayoutParams(-1, -2).apply { topMargin = dp(12) })
            addView(label(advice, TaskProductVisualStyle.body), LayoutParams(-1, -2))
        }
    }
}
