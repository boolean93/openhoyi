package io.openhoyi.mobile

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.ceil

/** One shared recipe plot. Stage boundaries are ordinal, never elapsed time or sampled transitions. */
internal class AiCurveRecipeDiagramView(
    context: Context, private val model: AiCurveRecipeDiagram, private val style: TaskProductVisualStyle,
) : LinearLayout(context) {
    private val english = resources.configuration.locales[0].language != "zh"
    // Reuse the same resolved typeface/SP configuration as the visible parameter labels.
    private val parameterPaint = style.text("", TaskProductVisualStyle.auxiliary).paint
    private fun unit(stage: AiCurveRecipeDiagram.Stage) = if (stage.unit == CurveStageParameters.Unit.PRESSURE_BAR) "bar" else "mL/s"
    private fun number(raw: Int) = CurveDraftNumber.format(raw, 1)
    private fun targetLabel(stage: AiCurveRecipeDiagram.Stage) = number(stage.targetTenths) + " " + unit(stage)
    private fun waterLabel(stage: AiCurveRecipeDiagram.Stage) = (stage.waterTenthsMl?.let(::number) ?: "—") + " mL"
    // Each stage is two complete lines, not a wide combined target-and-water sentence.
    private val leftInset = style.dp(4)
    private val rightInset = style.dp(4)
    private val stageMinimum = model.stages.maxOfOrNull { stage ->
        AiCurveCardLayoutPolicy.stageLabelWidth(parameterPaint.measureText(targetLabel(stage)),
            parameterPaint.measureText(waterLabel(stage)), style.dp(16))
    } ?: 0
    val requiredWidth: Int = leftInset + rightInset + stageMinimum * model.stages.size
    private val parameterLabels = mutableListOf<TextView>()
    init {
        orientation = VERTICAL
        tag = "ai/curve-card/stage-diagram"
        minimumWidth = requiredWidth
        addView(Plot(context), LayoutParams(-1, -2))
        val parameters = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
        model.stages.forEach { stage ->
            parameters.addView(style.text("", TaskProductVisualStyle.auxiliary).apply {
                gravity = Gravity.CENTER
                setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
                contentDescription = (if (english) "Stage ${stage.ordinal}" else "第 ${stage.ordinal} 段") + ": " +
                    number(stage.targetTenths) + " " + unit(stage) + "; " + (stage.waterTenthsMl?.let { number(it) + " mL" } ?: "—")
                parameterLabels += this
            }, LayoutParams(0, -2, 1f))
        }
        addView(parameters, LayoutParams(-1, -2).apply { marginStart = leftInset; marginEnd = rightInset })
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val offered = View.MeasureSpec.getSize(widthMeasureSpec)
        val width = if (View.MeasureSpec.getMode(widthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) requiredWidth else maxOf(offered, requiredWidth)
        model.stages.forEachIndexed { index, stage ->
            parameterLabels[index].text = targetLabel(stage) + "\n" + waterLabel(stage)
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
    }
    private inner class Plot(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val plotHeight = style.dp(36).toFloat()
        private val top = style.dp(8).toFloat()
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            setMeasuredDimension(View.resolveSize(requiredWidth, widthMeasureSpec), ceil(top + plotHeight + style.dp(4)).toInt())
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (!model.renderable || model.stages.isEmpty()) return
            val left = leftInset.toFloat(); val right = width - rightInset.toFloat()
            val bottom = top + plotHeight; val step = (right - left) / model.stages.size
            if (step <= 0) return
            // No ordinal headings, grid or synthetic time axis. Each independent target uses
            // its own unit's scale; unlike pressure/flow targets are never joined.
            model.stages.forEachIndexed { index, stage ->
                val fraction = model.targetFraction(stage) ?: return@forEachIndexed
                val y = bottom - plotHeight * fraction
                paint.color = if (stage.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND ||
                    resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
                    style.accent else style.pressure
                paint.strokeWidth = style.dp(3).toFloat()
                // Only the target of this stage: no cross-stage join and no sample marker.
                val center = left + step * (index + .5f)
                val half = AiCurveCardLayoutPolicy.stageMarkerLength(step, style.dp(8).toFloat(), style.dp(40).toFloat()) / 2
                canvas.drawLine(center - half, y, center + half, y, paint)
                paint.strokeWidth = style.dp(1).toFloat()
            }
        }
    }
}


/** Resolve the final content width BEFORE FrameLayout derives this scroll view's wrap-content height.
 * Native fillViewport widens its child after that first height calculation, leaving the earlier
 * multi-line parameter height behind when the widened labels become one line.
 */
internal class AiCurveRecipeDiagramScrollView(context: Context, private val diagram: AiCurveRecipeDiagramView) : HorizontalScrollView(context) {
    init {
        tag = "ai/curve-card/stage-scroll"
        isFillViewport = false
        isHorizontalScrollBarEnabled = true
        isScrollbarFadingEnabled = false
        scrollBarStyle = View.SCROLLBARS_INSIDE_INSET
        addView(diagram, FrameLayout.LayoutParams(-2, -2))
    }
    override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
        parentHeightMeasureSpec: Int, heightUsed: Int) {
        val margins = child.layoutParams as ViewGroup.MarginLayoutParams
        val viewport = if (View.MeasureSpec.getMode(parentWidthMeasureSpec) == View.MeasureSpec.UNSPECIFIED) 0
            else (View.MeasureSpec.getSize(parentWidthMeasureSpec) - paddingLeft - paddingRight -
                margins.leftMargin - margins.rightMargin - widthUsed).coerceAtLeast(0)
        val contentWidth = AiCurveCardLayoutPolicy.diagramWidth(viewport, diagram.requiredWidth)
        val heightSpec = ViewGroup.getChildMeasureSpec(parentHeightMeasureSpec,
            paddingTop + paddingBottom + margins.topMargin + margins.bottomMargin + heightUsed, margins.height)
        child.measure(View.MeasureSpec.makeMeasureSpec(contentWidth, View.MeasureSpec.EXACTLY), heightSpec)
    }
}
