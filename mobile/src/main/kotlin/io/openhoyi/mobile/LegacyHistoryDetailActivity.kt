package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject

class LegacyHistoryDetailActivity : ThemedActivity() {
    private val visual by lazy { LegacyVisualStyle(this) }
    private val style get() = visual.palette

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = style.page(this, getString(R.string.legacy_history_detail_title))
        val id = intent.getStringExtra("legacyId")
        val status = style.text(getString(R.string.legacy_history_detail_loading), TaskProductVisualStyle.body,
            "legacy/detail/status").also { style.add(body, it, 8) }
        Thread({
            val result = runCatching { (application as MobileApplication).legacyHistory.list().firstOrNull { it.id == id } }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val shot = result.getOrNull()
                if (shot == null) {
                    status.text = getString(if (result.isFailure) R.string.legacy_history_failed_message else R.string.legacy_history_unavailable)
                    return@runOnUiThread
                }
                body.removeView(status)
                showShot(body, shot)
            }
        }, "legacy-history-detail").start()
    }

    private fun showShot(body: LinearLayout, shot: LegacyShot) {
        val name = shot.profileName.ifBlank { getString(R.string.legacy_history_unnamed) }
        val heading = LegacyNaturalFlow(this, dp(16), dp(8)).apply {
            addView(style.text(name, TaskProductVisualStyle.title, "legacy/detail/name"))
            addView(style.text(legacyMetadata(this@LegacyHistoryDetailActivity, shot), TaskProductVisualStyle.auxiliary,
                "legacy/detail/metadata", true))
            addView(visual.readonly())
        }
        val content = style.column("legacy/detail/content")
        style.add(content, heading, 8)
        val workspace = TaskWorkspaceLayout(this, supportingWidthFraction = .34f,
            supportingMaxWidthDp = 400, boundedSecondaryViewport = true).apply {
            fillSecondaryViewport = true
            visual.panel(primaryBody); visual.panel(secondaryBody)
        }
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        val samplesTitle=style.text(getString(R.string.legacy_detail_samples_heading),
            TaskProductVisualStyle.section, "legacy/detail/samples/title")
        style.add(workspace.primaryBody,samplesTitle)
        val chart = LegacyShotChartView(this).apply { visualStyle = visual; points = shot.points; tag = "legacy/detail/chart" }
        workspace.primaryBody.addView(LegacyChartViewport(this, chart), LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        workspace.onPrimarySpaceAvailable = { width, budget -> minOf(budget,
            chart.minimumVisualHeight((width - dp(32)).coerceAtLeast(0)) + dp(48) + style.sp(30).toInt()) }
        // A permanent supporting subtree: only prose scrolls in a split pane, the real action stays fixed.
        val prose = style.column("legacy/detail/prose")
        val scroll = ScrollView(this).apply { addView(prose) }
        workspace.secondaryBody.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        fun section(heading: Int, message: Int, separated: Boolean) {
            if (separated) style.divider(prose)
            style.add(prose, style.text(getString(heading), TaskProductVisualStyle.section))
            style.add(prose, style.text(getString(message), TaskProductVisualStyle.auxiliary), 8)
        }
        section(R.string.legacy_detail_source_heading, R.string.legacy_detail_source_body, false)
        section(R.string.legacy_detail_missing_heading, R.string.legacy_detail_missing_body, true)
        section(R.string.legacy_detail_limits_heading, R.string.legacy_detail_limits_body, true)
        val fullNotice=style.action(workspace.secondaryBody, getString(R.string.ui_full_details), "legacy/detail/full-notice", outline = true, top = 16) {
            val summary = getString(R.string.legacy_history_summary, name,
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA).format(java.util.Date(shot.createdAtMs)),
                shot.durationSec.toString())
            val dialog = android.app.AlertDialog.Builder(this).setTitle(getString(R.string.legacy_history_detail_title))
                .setMessage(summary + "\n\n" + getString(R.string.legacy_history_chart_disclaimer))
                .setPositiveButton(android.R.string.ok, null).create()
            beanShowDialog(dialog)
        }
        val contentScroll=LegacyDetailContentScroll(this,content,heading,workspace,samplesTitle,fullNotice).apply {
            tag="legacy/detail/content-scroll"
        }
        body.addView(contentScroll,LinearLayout.LayoutParams(-1,0,1f))
    }
    private fun dp(value: Int) = style.dp(value)
}

/** The Detail-only host keeps the same subtree while a short body scrolls both heading and workspace. */
internal class LegacyDetailContentScroll(context: android.content.Context, private val content: LinearLayout,
    private val heading: View, private val workspace: TaskWorkspaceLayout, private val samplesTitle: View,
    private val fullNotice: Button) : ScrollView(context) {
    var budgetEvidence=JSONObject()
        private set
    private var targetContentHeight=0
    init { isFillViewport=false; addView(content,ViewGroup.LayoutParams(-1,-2)) }
    override fun measureChildWithMargins(child:View,parentWidthMeasureSpec:Int,widthUsed:Int,
        parentHeightMeasureSpec:Int,heightUsed:Int) {
        val params=child.layoutParams as ViewGroup.MarginLayoutParams
        val childWidth=getChildMeasureSpec(parentWidthMeasureSpec,
            paddingLeft+paddingRight+params.leftMargin+params.rightMargin+widthUsed,params.width)
        child.measure(childWidth,View.MeasureSpec.makeMeasureSpec(targetContentHeight,View.MeasureSpec.EXACTLY))
    }
    override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
        val width=(View.MeasureSpec.getSize(widthMeasureSpec)-paddingLeft-paddingRight).coerceAtLeast(0)
        val viewport=(View.MeasureSpec.getSize(heightMeasureSpec)-paddingTop-paddingBottom).coerceAtLeast(0)
        val density=resources.displayMetrics.density
        val headingMargin=(heading.layoutParams as LinearLayout.LayoutParams).topMargin
        heading.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val headingHeight=headingMargin+heading.measuredHeight
        val workspaceMargin=(workspace.layoutParams as LinearLayout.LayoutParams).topMargin
        val availableWorkspace=(viewport-headingHeight-workspaceMargin).coerceAtLeast(0)
        val twoPane=TaskLayoutPolicy.decide(resources.configuration.screenWidthDp.coerceAtLeast(1),
            (availableWorkspace/density).toInt()).twoPaneAllowed && width>=(624*density).toInt()
        val supportingWidth=((width*.34f).toInt()).coerceIn((280*density).toInt(),(400*density).toInt())
        val primaryWidth=if(twoPane) width-supportingWidth-(24*density).toInt() else width
        val innerPrimary=(primaryWidth-workspace.primaryBody.paddingLeft-workspace.primaryBody.paddingRight).coerceAtLeast(0)
        samplesTitle.measure(View.MeasureSpec.makeMeasureSpec(innerPrimary,View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        fullNotice.measure(View.MeasureSpec.makeMeasureSpec(
            ((if(twoPane) supportingWidth else width)-workspace.secondaryBody.paddingLeft-workspace.secondaryBody.paddingRight).coerceAtLeast(0),
            View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
        val chartMargin=(workspace.primaryBody.getChildAt(1).layoutParams as LinearLayout.LayoutParams).topMargin
        val primaryMinimum=workspace.primaryBody.paddingTop+samplesTitle.measuredHeight+chartMargin+
            (96*density).toInt()+workspace.primaryBody.paddingBottom
        val supportMinimum=maxOf((56*density).toInt(),(48*density).toInt(),fullNotice.measuredHeight)
        val workspaceMinimum=if(twoPane) maxOf(primaryMinimum,supportMinimum)
            else primaryMinimum+supportMinimum
        val naturalHeight=headingHeight+workspaceMargin+workspaceMinimum
        val contentHeight=maxOf(viewport,naturalHeight)
        targetContentHeight=contentHeight
        budgetEvidence=JSONObject().put("viewportPx",viewport).put("contentHeightPx",contentHeight)
            .put("headingNaturalHeightPx",heading.measuredHeight).put("headingTopMarginPx",headingMargin)
            .put("workspaceTopMarginPx",workspaceMargin).put("availableWorkspacePx",availableWorkspace)
            .put("workspaceMinimumPx",workspaceMinimum).put("primaryMinimumPx",primaryMinimum)
            .put("supportMinimumPx",supportMinimum).put("samplesTitleNaturalHeightPx",samplesTitle.measuredHeight)
            .put("fullNoticeNaturalHeightPx",fullNotice.measuredHeight).put("chartTopMarginPx",chartMargin)
            .put("twoPaneAtAvailableHeight",twoPane).put("shortFallback",naturalHeight>viewport)
        super.onMeasure(widthMeasureSpec,heightMeasureSpec)
    }
}
