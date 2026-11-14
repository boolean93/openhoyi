package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.util.TypedValue
import android.view.WindowInsets
import android.view.View
import android.widget.*
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.DeviceState
import io.openhoyi.session.StopReason
import java.util.Locale

/** A screen never owns BLE. Explicit start or a one-shot Home tap can start; navigation cannot. */
class ExtractionFragment : androidx.fragment.app.Fragment() {
    private val host get() = requireActivity() as MainActivity
    private val application get() = host.application
    private val MODE_PRIVATE=android.content.Context.MODE_PRIVATE
    private fun getColor(id:Int)=requireContext().getColor(id)
    private fun getSharedPreferences(name:String,mode:Int)=host.getSharedPreferences(name,mode)
    private fun checkSelfPermission(permission:String)=host.checkSelfPermission(permission)
    private val serviceObserver:(MobileService?)->Unit={service=it;render()}
    private val visual by lazy { ExtractionHistoryVisualStyle(host) }
    private val style get() = visual.palette
    private val readoutGroups = mutableMapOf<TextView, HistoryQuantity>()
    internal lateinit var feedbackUi: BrewFeedbackDialog
        private set
    private val machineAlarms by lazy { MachineAlarms(host) }
    private var presetSlot = 7
    fun setPresetSlot(slot:Int) {
        val next=slot.takeIf { it in 1..5 } ?: 7
        if (presetSlot!=next) {presetSlot=next;if(::readiness.isInitialized)render()}
    }
    private var service: MobileService? = null
    private var visible = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var curveSummary: TextView
    private lateinit var overviewHeading: TextView
    private lateinit var recipeSummary: TaskRecipeSummary
    private lateinit var deviceSummary: TextView
    private lateinit var shotSummary: TextView
    private lateinit var readiness: TextView
    private lateinit var chooseCurve: Button
    private lateinit var connectDevices: Button
    private lateinit var chooseBeans: Button
    private lateinit var detailsButton: Button
    private lateinit var live: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var pressureValue: TextView
    private lateinit var scaleReadout: ExtractionScaleReadoutView
    private lateinit var weightValue: TextView
    private lateinit var quantityLabel: TextView
    private lateinit var temperatureValue: TextView
    private lateinit var waterValue: TextView
    private lateinit var preparationTools: LinearLayout
    private lateinit var preparationSummary: TextView
    private lateinit var flowValue: TextView
    private lateinit var weightTarget: TextView
    private lateinit var preparationStatus: TextView
    private lateinit var notificationStatus: TextView
    private lateinit var alarmStatus: TextView
    private lateinit var chart: ExtractionOverlayView
    internal data class StatusNotice(val signal: ExtractionStatusPresentation.Signal,val detail: String)
    private var statusNotices: List<StatusNotice> = emptyList()
    private val statusTransitions = ExtractionStatusPresentation.Transitions()
    private var statusDetailsText: TextView? = null
    private var statusDetailsDialog: AlertDialog? = null
    private var statusMenuButton: Button? = null
    private lateinit var chartToolsButton: Button
    private var seriesSelector: ChartSeriesSelector? = null
    private var toolsDialog: AlertDialog? = null
    private val toolsMenuButtons = linkedMapOf<Button, Button>()
    private val transientDialogs = mutableListOf<AlertDialog>()
    private fun showTransient(builder: AlertDialog.Builder) {
        transientDialogs += AppDialog.show(builder.create())
    }
    private lateinit var referenceStatus: TextView
    private lateinit var traceCurve: TextView
    private lateinit var referenceChoice: Button
    private lateinit var stageTargets: TextView
    private var referenceId: String? = null
    private var referenceCurveId: String? = null
    private lateinit var start: Button
    private lateinit var stop: Button
    private lateinit var prepare: Button
    private lateinit var cancelPrepare: Button
    private lateinit var pageHeader: LinearLayout
    private lateinit var pageBody: LinearLayout
    private lateinit var compactClock: TextView
    private lateinit var compactElapsed: TextView
    private lateinit var compactMode: TextView
    private lateinit var compactGuidance: TextView
    private lateinit var compactIdentity: TextView
    private lateinit var identityBlock: ManualPendingRecipeTitle
    private lateinit var headerWords: LinearLayout
    private lateinit var supportReadings: LinearLayout
    private lateinit var chartHost: FrameLayout
    private lateinit var liveHeading: TextView
    private lateinit var modeBadge: TextView
    // Chrome compaction must not depend on height that this same chrome consumes.
    // The workspace independently uses its actual remaining width and height.
    private val compactChrome get() = resources.configuration.screenHeightDp < 500 || resources.configuration.screenWidthDp < 840
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 250) }
    }
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        setPresetSlot(savedInstanceState?.getInt(PresetSlots.EXTRA_SLOT) ?: arguments?.getInt(PresetSlots.EXTRA_SLOT) ?: 7)
        referenceId = savedInstanceState?.getString("referenceShotId")
        feedbackUi = BrewFeedbackDialog(host, savedInstanceState)
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.page)

        }
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        pageBody = content
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        pageHeader = HoyiUi.header(host, content, getString(R.string.extraction_title),
            if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_subtitle) else null)
        style.styleHeader(pageHeader)
        pageHeader.removeViewAt(0)
        compactIdentity = style.text("", TaskProductVisualStyle.Type(if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) 18 else 20, 500, if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) 26 else 28), "product/extraction/identity").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        identityBlock = ManualPendingRecipeTitle(compactIdentity, style)
        compactClock = style.text("", TaskProductVisualStyle.Type(17,400,24), "product/extraction/clock")
        compactElapsed = style.text("", TaskProductVisualStyle.Type(17,400,24), "product/extraction/elapsed")
        compactMode = style.text(if(BuildConfig.MOCK_MODE) "Mock" else "",TaskProductVisualStyle.Type(15,400,22),"product/extraction/mode",true).apply { visibility=if(BuildConfig.MOCK_MODE) View.VISIBLE else View.GONE }
        listOf(compactClock,compactElapsed,compactMode).forEach { field ->
            val divider=android.graphics.drawable.GradientDrawable().apply {setColor(visual.liveSeparator);setBounds(0,0,dp(1),dp(18))}
            field.setCompoundDrawablesRelative(divider,null,null,null);field.compoundDrawablePadding=dp(12)
        }
        compactGuidance = style.text("", TaskProductVisualStyle.auxiliary, "product/extraction/inline-guidance", true)
        val portraitHeader=resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT
        val portraitIdentityRow=if(portraitHeader) style.row().apply {gravity=android.view.Gravity.CENTER_VERTICAL} else null
        headerWords = if(portraitHeader) style.column().apply {
            portraitIdentityRow!!.addView(identityBlock,LinearLayout.LayoutParams(0,-2,1f))
            addView(portraitIdentityRow,LinearLayout.LayoutParams(-1,-2))
            compactClock.setCompoundDrawablesRelative(null,null,null,null)
            val metadata=style.row().apply {gravity=android.view.Gravity.CENTER_VERTICAL}
            listOf(compactClock,compactElapsed,compactMode).forEachIndexed {i,field ->
                metadata.addView(field,LinearLayout.LayoutParams(-2,-2).apply {if(i>0) marginStart=dp(8)})
            }
            addView(metadata,LinearLayout.LayoutParams(-1,-2))
            addView(compactGuidance,LinearLayout.LayoutParams(-1,-2))
        } else object : LinearLayout(host) {
            init { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                listOf(identityBlock,compactClock,compactElapsed,compactMode,compactGuidance).forEach {it.measure(natural,natural)}
                val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val stacked = available < dp(112) + compactClock.measuredWidth + compactElapsed.measuredWidth + compactMode.measuredWidth + compactGuidance.measuredWidth + dp(16)
                val naturalFits=identityBlock.measuredWidth+compactClock.measuredWidth+compactElapsed.measuredWidth+compactMode.measuredWidth+compactGuidance.measuredWidth+dp(24)<=available
                orientation = if (stacked) VERTICAL else HORIZONTAL
                listOf(identityBlock, compactClock, compactElapsed, compactMode, compactGuidance).forEachIndexed { index, child ->
                    val width = if (stacked) -1 else if (index == 0 && !naturalFits) 0 else -2
                    val weight = if (!stacked && index == 0 && !naturalFits) 1f else 0f
                    val gap = if (!stacked && index > 0) dp(8) else 0
                    val current = child.layoutParams as LayoutParams
                    if (current.width != width || current.weight != weight || current.marginStart != gap)
                        child.layoutParams = LayoutParams(width, -2, weight).apply { marginStart = gap }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        if(!portraitHeader) listOf(identityBlock, compactClock, compactElapsed, compactMode, compactGuidance).forEach { headerWords.addView(it) }
        pageHeader.addView(headerWords, 0, LinearLayout.LayoutParams(0, -2, 1f))
        pageHeader.minimumHeight = dp(56)
        pageHeader.tag = "product/extraction/header"
        // Secondary prose remains available in a details dialog, not in the plot's height budget.
        val details = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        overviewHeading = HoyiUi.label(host, details, getString(R.string.extraction_overview), 17, true)
        modeBadge = text(details, getString(R.string.extraction_mock_subtitle), 12)
        shotSummary = text(details, getString(R.string.extraction_shot_initial), 13)
        liveHeading = text(details, getString(R.string.extraction_live_data), 15)
        live = text(details, "", 13)
        weightTarget = text(details, "", 14)
        referenceStatus = text(details, getString(R.string.reference_off), 13)
        traceCurve = text(details, "", 13)
        stageTargets = text(details, "", 13)
        readiness = text(content, getString(R.string.extraction_waiting_service), 14, true).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val workspace = TaskExtractionWorkspace(host, visual)
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 0 })
        val plotColumn = workspace.primaryBody
        val sidePanel = workspace.secondaryBody.apply {
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        style.add(sidePanel, style.text(getString(R.string.task_extraction_supporting), TaskProductVisualStyle.auxiliary, secondary = true))
        curveSummary = text(sidePanel, getString(R.string.extraction_curve_initial), 16, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details"
            style.apply(this, TaskProductVisualStyle.title); setPadding(0, dp(16), 0, dp(16))
        }
        recipeSummary = TaskRecipeSummary(host, visual)
        sidePanel.addView(recipeSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        deviceSummary = text(sidePanel, getString(R.string.extraction_devices_initial), 14)
        val metrics = TaskLiveReadouts(host, visual)
        // Replaced by the chart's single four-metric strip; detached legacy readouts remain details inputs.
        fun reading(label: String, quantity: Boolean = false): TextView {
            val cell = style.column("product/extraction/" + if (quantity) "quantity-cell" else "elapsed-cell").apply {
                orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            }
            metrics.addView(cell)
            val heading = style.text(label, TaskProductVisualStyle.auxiliary, secondary = true)
            cell.addView(heading, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            if (quantity) quantityLabel = heading
            val group = HistoryQuantity(host, visual, if (quantity) "product/extraction/quantity" else "product/extraction/elapsed", focus = true)
            cell.addView(group, LinearLayout.LayoutParams(-2, -2))
            return group.number.also { readoutGroups[it] = group }
        }
        elapsedValue = reading(getString(R.string.task_extraction_elapsed))
        weightValue = reading(getString(R.string.task_extraction_water), quantity = true)
        val secondaryReadings = style.row("product/extraction/readouts-support")
        supportReadings = secondaryReadings
        metrics.addView(secondaryReadings)
        fun supportReading(label: Int, id: String): TextView {
            val row = style.row("$id/cell").apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            secondaryReadings.addView(row, LinearLayout.LayoutParams(-2, -2))
            row.addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            val group = HistoryQuantity(host, visual, id, large = false)
            row.addView(group)
            return group.number.also { readoutGroups[it] = group }
        }
        pressureValue = supportReading(R.string.task_chart_pressure, "product/extraction/pressure")
        waterValue = supportReading(R.string.task_chart_water, "product/extraction/water")
        temperatureValue = supportReading(R.string.task_chart_temperature, "product/extraction/temperature")
        flowValue = text(details, "", 14)
        chartHost = FrameLayout(host).apply { tag = "product/extraction/chart-host" }
        chart = ExtractionOverlayView(host, visual).apply {
            tag = "product/extraction/chart"; setBackgroundColor(style.surface)
            liveCompactChrome = true
        }
        chartHost.addView(chart, FrameLayout.LayoutParams(-1, -1))
        // One natural identity row (at least 56dp); live metrics share the footer actions.
        content.removeView(pageHeader)
        chartHost.addView(pageHeader, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP).apply { marginEnd = 0 })
        pageHeader.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> chart.liveHeaderHeightPx = pageHeader.height }
        seriesSelector = ChartSeriesSelector(chart)
        (portraitIdentityRow ?: pageHeader).addView(seriesSelector!!.button,
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(4) })
        val chartTools = object : Button(host) {
            private val badge = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(canvas: android.graphics.Canvas) {
                super.onDraw(canvas)
                if (statusNotices.isEmpty()) return
                val cx=width-dp(6).toFloat(); val cy=dp(6).toFloat()
                badge.style=android.graphics.Paint.Style.FILL
                badge.color=if(statusNotices.first().signal.severity==ExtractionStatusPresentation.Severity.CRITICAL) visual.stop else visual.warningInk
                canvas.drawCircle(cx,cy,dp(6).toFloat(),badge)
                badge.color=android.graphics.Color.WHITE; badge.strokeWidth=dp(1).toFloat().coerceAtLeast(1f)
                canvas.drawLine(cx,cy-dp(3),cx,cy+dp(1),badge)
                canvas.drawCircle(cx,cy+dp(3),dp(1).toFloat(),badge)
            }
        }.apply {
            text = getString(R.string.task_tools); contentDescription = text
            tag = "product/extraction/chart-tools"
            style.button(this)
            setTextColor(style.ink)
            setCompoundDrawablesRelativeWithIntrinsicBounds(ExtractionToolsDrawable(resources.displayMetrics.density,style.ink),null,null,null)
            compoundDrawablePadding=dp(8)
            minimumWidth = dp(48); minimumHeight = dp(48)
            setOnClickListener { showTools() }
        }
        chartToolsButton=chartTools
        (portraitIdentityRow ?: pageHeader).addView(chartTools, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        plotColumn.addView(chartHost, LinearLayout.LayoutParams(-1, 0, 1f))
        workspace.onPrimarySpaceAvailable = { width, height ->
            if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT)
                chart.liveHeightForPlotAspect(width,if(width<dp(480)) 1.4f else 1.6f)
                    .coerceAtLeast(chart.minimumLiveHeight())
                    .coerceAtMost(height.coerceAtLeast(chart.minimumLiveHeight()))
            else maxOf((dp(260) * resources.configuration.fontScale).toInt(), (height * .70f).toInt())
        }
        val toolbar = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        sidePanel.addView(toolbar)
        fun compact(parent: LinearLayout, title: String, action: () -> Unit): Button =
            HoyiUi.button(host, parent, title, action = action).apply {
                style.button(this)
                minHeight = dp(48); minimumHeight = dp(48)
                layoutParams = if (parent.orientation == LinearLayout.VERTICAL)
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                else LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) }
            }
        chooseCurve = compact(toolbar, getString(R.string.extraction_choose_curve)) { host.select(TaskDestination.CURVES) }
        connectDevices = compact(toolbar, getString(R.string.extraction_connect_devices)) {
            host.select(TaskDestination.BREW)
        }
        chooseBeans = compact(toolbar, getString(R.string.dose_title)) { startActivity(Intent(host, BeanPreparationActivity::class.java)) }
        val referenceTools = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        sidePanel.addView(referenceTools, LinearLayout.LayoutParams(-1, -2))
        preparationTools = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
        sidePanel.addView(preparationTools, LinearLayout.LayoutParams(-1, -2))
        referenceChoice = compact(referenceTools, getString(R.string.reference_choose)) { chooseReference() }
        detailsButton = compact(referenceTools, getString(R.string.extraction_overview)) {
            val info = listOf(curveSummary, deviceSummary, preparationSummary, readiness, shotSummary, live, flowValue, weightTarget, traceCurve, stageTargets, referenceStatus)
                .map { it.text.toString() }.filter { it.isNotBlank() }.joinToString("\n\n") +
                "\n\n" + recipeSummary.text + "\n\n" + chart.overviewExplanation() +
                "\n\n" + getString(R.string.live_cup_flow_source)
            AlertDialog.Builder(host).setTitle(getString(R.string.extraction_overview)).setMessage(info)
                .setPositiveButton(android.R.string.ok, null).let(::showTransient)
        }
        style.button(detailsButton)
        curveSummary.setOnLongClickListener { detailsButton.performClick(); true }
        prepare = compact(preparationTools, getString(R.string.extraction_prepare_button)) { confirmPrepare() }
        cancelPrepare = compact(preparationTools, getString(R.string.extraction_cancel_prepare)) { service?.cancelBrewPreparation()?.let(::toast); render() }
        preparationSummary = text(sidePanel, "", 12).apply { visibility = View.GONE }
        // These can describe UNKNOWN writes or stale alarms. Keep them outside the
        // tools panel so longer translations cannot push a safety message offscreen.
        preparationStatus = text(content, getString(R.string.extraction_prepare_initial), 12)
        notificationStatus = text(content, "", 12)
        alarmStatus = text(content, getString(R.string.home_alarm_initial), 13).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_ASSERTIVE
        }
        // Safety messages remain fixed and full-width, outside the scrolling tools.
        listOf(preparationStatus, notificationStatus, alarmStatus).forEach { status ->
            content.removeView(status)
            content.addView(status, content.indexOfChild(workspace))
        }
        val portraitLive=resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val compactPortrait = portraitLive && resources.configuration.screenHeightDp <= 720
        val actionDock = LinearLayout(host).apply {
            orientation = if(portraitLive) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(64)
            setBackgroundColor(style.surface)
        }
        // The low-frequency panel and endpoint labels already expose these same readings.
        // Do not let the duplicate two-row strip consume the short portrait plot's height.
        if (!compactPortrait) chart.attachLiveReadouts(if(portraitLive) plotColumn else actionDock)
        val controls = if (compactPortrait) LinearLayout(host).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL
            actionDock.addView(this, LinearLayout.LayoutParams(-1, -2))
        } else actionDock
        scaleReadout = ExtractionScaleReadoutView(host, visual, compact = compactPortrait)
        controls.addView(scaleReadout, LinearLayout.LayoutParams(if(compactPortrait) dp(150) else if(portraitLive) -1 else dp(180), -2).apply { marginStart = dp(8) })
        val actionButtons = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(actionButtons, if (compactPortrait) LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) }
            else LinearLayout.LayoutParams(if(portraitLive) -1 else dp(190), -2).apply { if(portraitLive) topMargin=dp(12) else marginStart=dp(12) })
        workspace.setFooter(actionDock)
        start = HoyiUi.button(host, actionButtons, getString(R.string.extraction_start), primary = true) { startSelectedCurve() }
        (weightTarget.parent as? android.view.ViewGroup)?.removeView(weightTarget)
        (if (compactPortrait) actionDock else actionButtons).addView(weightTarget,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(4)})
        (start.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)
        stop = HoyiUi.button(host, actionButtons, getString(R.string.extraction_stop), danger = true) {
            service?.stopShot(); render()
        }.apply {
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(host, R.color.mobile_stop_button, 10)
            visibility = View.GONE
        }
        (stop.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)
        listOf(deviceSummary, preparationSummary, referenceStatus, shotSummary, live, traceCurve, stageTargets, weightTarget).forEach {
            style.apply(it, TaskProductVisualStyle.auxiliary, true)
        }
        listOf(readiness, preparationStatus, notificationStatus, alarmStatus).forEach {
            it.visibility=View.GONE
            style.apply(it, TaskProductVisualStyle.auxiliary)
            it.setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        style.button(start, primary = true)
        start.backgroundTintList=android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(style.disabled,visual.liveStart))
        start.tag = "product/extraction/start"
        listOf(start,stop).forEach { it.layoutParams=LinearLayout.LayoutParams(-1,-2); it.minHeight=dp(54); it.minimumHeight=dp(54) }
        visual.stopButton(stop); listOf(start,stop).forEach { it.textSize=if (resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT) 18f else 21f }; stop.tag = "product/extraction/stop"
        actionDock.tag = "product/extraction/actions"; actionDock.setPadding(dp(8), dp(4), dp(8), dp(4))
        render()
        return root
    }
    override fun onResume() {
        super.onResume();feedbackUi.start();visible=true
        host.observeService(serviceObserver);handler.post(refresh)
        if (!BuildConfig.MOCK_MODE) requestSafetyNotificationsOnce()
    }
    override fun onPause() {
        host.revokeShortcutStart()
        feedbackUi.leave(host.isChangingConfigurations)
        visible=false;handler.removeCallbacks(refresh)
        seriesSelector?.close()
        if (::chart.isInitialized) chart.dismissInspection()
        toolsDialog?.dismiss();toolsDialog=null
        statusDetailsDialog?.dismiss();statusDetailsDialog=null
        transientDialogs.forEach { it.dismiss() }; transientDialogs.clear()
        host.removeServiceObserver(serviceObserver);service=null;super.onPause()
    }
    private fun toolVisible(source: View): Boolean {
        val body = sourceBodyForTools()
        var current: View? = source
        while (current != null) {
            if (current.visibility != View.VISIBLE) return false
            if (current === body) return true
            current = current.parent as? View
        }
        return false
    }
    private fun sourceBodyForTools(): View = requireNotNull(chooseCurve.parent?.parent as? View)
    private fun syncToolsMenu() {
        toolsMenuButtons.forEach { (source, menu) ->
            menu.text = source.text
            menu.contentDescription = source.contentDescription
            menu.isEnabled = source.isEnabled
            menu.visibility = if (toolVisible(source)) View.VISIBLE else View.GONE
        }
    }
    private fun showTools() {
        render()
        if (toolsDialog?.isShowing == true) return
        val body = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        val sources = linkedMapOf("choose-curve" to chooseCurve, "connect-devices" to connectDevices,
            "choose-beans" to chooseBeans, "reference-choice" to referenceChoice, "overview" to detailsButton,
            "prepare" to prepare, "cancel-prepare" to cancelPrepare)
        toolsMenuButtons.clear()
        statusMenuButton=HoyiUi.button(host,body,statusMenuTitle()) {
            toolsDialog?.dismiss(); showStatusDetails()
        }.apply { tag="product/extraction/tool/status" }
        sources.forEach { (key, source) ->
            val menu = HoyiUi.button(host, body, source.text.toString()) {
                render()
                if (toolVisible(source) && source.isEnabled) {
                    toolsDialog?.dismiss()
                    source.performClick()
                }
            }.apply { tag = "product/extraction/tool/$key" }
            toolsMenuButtons[source] = menu
        }
        syncToolsMenu()
        val dialog = AppDialog.show(host, getString(R.string.task_tools), body,
            AppDialog.Action(getString(android.R.string.ok)) {}, null)
        toolsDialog = dialog
        dialog.setOnDismissListener {
            if (toolsDialog === dialog) { toolsDialog = null; toolsMenuButtons.clear(); statusMenuButton=null }
        }
    }
    private fun notificationsAllowed(): Boolean = BuildConfig.MOCK_MODE ||
        host.getSystemService(android.app.NotificationManager::class.java)?.areNotificationsEnabled()==true &&
        (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    private fun requestSafetyNotificationsOnce() {
        if (notificationsAllowed() || Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        val prefs = getSharedPreferences("safety", MODE_PRIVATE)
        if (prefs.getBoolean("asked_for_notifications", false)) return
        prefs.edit().putBoolean("asked_for_notifications", true).apply()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATIONS)
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATIONS) render()
    }
    private fun selected(): CurveLibraryItem? {
        val library = (application as MobileApplication).curves
        val id = if (presetSlot == 7) getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        else PresetSlots.curveId(presetSlot, getSharedPreferences("presets", MODE_PRIVATE)
            .getString(PresetSlots.key(presetSlot), null), library)
        return id?.let(library::find)
    }
    internal val shortcutStartVisible get() = visible && isResumed && !isHidden

    /** Synchronous only: this is never called from bind, resume, restore, render or temperature updates. */
    internal fun tryShortcutStart(expected: CurveProfile, permit: ShortcutStartPermit) {
        val owner = service ?: run { permit.revoke(); toast(getString(R.string.service_unavailable)); return }
        val library = (application as MobileApplication).curves
        val current = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY, presetSlot) }
        if (!shortcutStartVisible || current != expected) {
            permit.revoke(); toast(getString(R.string.extraction_choose_startable)); return
        }
        owner.extractionStartBlock(current)?.let {
            permit.revoke(); toast(it); render(); return
        }
        owner.studioStartBlock(expected)?.let {
            permit.revoke(); toast(it); render(); return
        }
        if (!permit.claim()) { toast(getString(R.string.shortcut_start_cancelled)); render(); return }
        // Existing service gates and scale tare remain authoritative, including at final dispatch.
        owner.startShot(expected.id, expected.scaleMode, presetSlot, permit::allows)?.let {
            permit.revoke(); toast(it)
        }
        render()
    }

    private fun startSelectedCurve() {
        host.revokeShortcutStart()
        val owner = service ?: run { toast(getString(R.string.service_unavailable)); return }
        val library = (application as MobileApplication).curves
        val profile = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = owner.extractionStartBlock(profile, selected() != null && profile == null)
        if (!ExtractionStartAction.permit(blocked, ::toast)) { render(); return }
        requireNotNull(profile)
        owner.studioStartBlock(profile)?.let { block ->
            if(DirectExtractionStart.offerPreheat(owner.studioStartReason(profile),
                    owner.currentCorrectedBrewTemperature(),profile.temperatureC))confirmPrepare(profile)
            else toast(block)
            render();return
        }
        // The service resolves this exact selected identity/slot and rechecks scale mode and
        // dispatch safety. No request survives navigation, binding, resume or dialog dismissal.
        owner.startShot(profile.id,profile.scaleMode,presetSlot)?.let(::toast)
        render()
    }
    private fun confirmPrepare(expected:CurveProfile?=null) {
        host.revokeShortcutStart()
        val owner = service ?: return
        val profile = selected()?.let {
            (application as MobileApplication).curves.resolve(it.id,
                owner.snapshot.scaleState == DeviceState.READY, presetSlot)
        } ?: run { toast(getString(R.string.extraction_choose_startable)); return }
        if(expected!=null && expected!=profile) {toast(getString(R.string.extraction_choose_startable));return}
        val corrected = owner.currentCorrectedBrewTemperature()
        val current = corrected?.let { number(it) + " °C" } ?: getString(R.string.settings_unknown)
        AlertDialog.Builder(host).setTitle(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_title) else getString(R.string.extraction_real_prepare_title))
            .setMessage(getString(R.string.extraction_prepare_message, current, profile.temperatureC.toString()) +
                if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_effect)
                else getString(R.string.extraction_real_prepare_effect))
            .setPositiveButton(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_start) else getString(R.string.extraction_real_prepare_start)) { _, _ ->
                if(service!==owner || !visible) {toast(getString(R.string.service_unavailable));return@setPositiveButton}
                val current=selected()?.let { (application as MobileApplication).curves.resolve(it.id,
                    owner.snapshot.scaleState==DeviceState.READY,presetSlot) }
                if(current!=profile)toast(getString(R.string.extraction_choose_startable))
                else owner.prepareBrew(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).let(::showTransient)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        feedbackUi.save(outState)
        outState.putString("referenceShotId",referenceId)
        outState.putInt(PresetSlots.EXTRA_SLOT,presetSlot)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        if (!::readiness.isInitialized) return
        feedbackUi.update(service, visible)
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val state = owner?.shotState ?: ExtractionState.IDLE
        val active=owner?.manualShotActive==true || state in setOf(ExtractionState.STARTING,
            ExtractionState.RUNNING,ExtractionState.STOP_REQUESTED)
        chart.compactRunningExplanation = active
        pageHeader.visibility = View.VISIBLE
        val edge = dp(4)
        if (pageBody.paddingTop != edge) pageBody.setPadding(dp(4), edge, dp(4), edge)
        supportReadings.visibility = if (active) View.GONE else View.VISIBLE
        modeBadge.visibility = if (active && compactChrome && BuildConfig.MOCK_MODE) View.VISIBLE else View.GONE
        liveHeading.visibility = if (active && compactChrome) View.GONE else View.VISIBLE
        shotSummary.visibility = if (active && compactChrome) View.GONE else View.VISIBLE
        overviewHeading.visibility=if(active) View.GONE else View.VISIBLE
        // Running cups still need an identity and live connection context in the supporting pane.
        curveSummary.visibility = View.VISIBLE
        deviceSummary.visibility = View.VISIBLE
        chooseBeans.visibility=if(active) View.GONE else View.VISIBLE
        val item = selected()
        val library = (application as MobileApplication).curves
        val profile = item?.let { library.resolve(it.id, snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = if (owner == null) getString(R.string.service_unavailable)
            else owner.extractionStartBlock(profile, item != null && profile == null)
        val unknownAdvice = if (state == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != io.openhoyi.session.DeviceState.READY)
            getString(R.string.extraction_disconnect_advice_suffix) else
            owner?.manualSafetyMessage?.let { "\n$it" }.orEmpty()
        val studio = snapshot.settings?.flags?.and(0x04) == 0x04
        val corrected = owner?.currentCorrectedBrewTemperature()
        val temperatureReady = profile != null && corrected != null &&
            BrewPreparation.isAtTarget(corrected, profile.temperatureC)
        val preparation = owner?.brewPreparationState ?: BrewPreparation.State.IDLE
        val studioBlocked = if (owner != null && profile != null) owner.studioStartBlock(profile) else null
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING,
            SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
        val slotLabel = if (presetSlot == 7) "" else getString(R.string.extraction_slot_suffix, presetSlot.toString())
        val observedCup = owner?.chartPoints?.isNotEmpty() == true || active || state == ExtractionState.OUTCOME_UNKNOWN
        val displayCurve = if (observedCup) owner?.chartCurveId?.let(library::find) else item
        val recipe = if (observedCup) owner?.activeShotRecipe?.forTrace(owner.chartTraceIdentity)
            else profile?.let { ShotRecipeSnapshot.capture("preview", it) }
        val displayCurveName = recipe?.name ?: displayCurve?.name ?: getString(if (observedCup) {
            if (owner?.chartCurveId == "manual") R.string.extraction_manual_label else R.string.profiles_missing
        } else R.string.extraction_unselected)
        val elapsed = (LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt,
            MobileMonotonicClock.elapsedRealtime()) as? ExtractionTelemetry)?.takeUnless { it.brewWait }?.elapsedSeconds
        val elapsedText = elapsed?.let { String.format(Locale.ROOT, "%02d:%02d", it / 60, it % 60) } ?: "—"
        curveSummary.show(getString(R.string.extraction_curve_summary, displayCurveName,
            if (observedCup) "" else slotLabel))
        val cupNotes = if (observedCup) owner?.chartTraceIdentity?.let {
            (application as MobileApplication).journalResult.getOrNull()?.find(it)?.notes
        } else null
        recipeSummary.present(linkedMapOf(
            R.string.task_recipe_temperature to recipe?.temperatureC?.let { "$it °C" },
            R.string.task_recipe_target to recipe?.let {
                if (it.targetHundredthsGram > 0) "${number(it.targetHundredthsGram)} g" else getString(R.string.extraction_no_weight_target)
            },
            R.string.journal_bean to cupNotes?.beanName?.takeIf { it.isNotBlank() },
            R.string.task_recipe_dose to cupNotes?.doseMg?.let { "${BeanQuantity.formatGrams(it)} g" },
        ))
        deviceSummary.show(getString(R.string.extraction_device_summary, DeviceStatusText.label(host, snapshot.coffeeState), DeviceStatusText.label(host, snapshot.scaleState)))
        shotSummary.show(getString(R.string.extraction_shot_summary, if (owner?.scalePreflight == true) getString(R.string.extraction_preflight_label) else if (owner?.manualShotActive == true) getString(R.string.extraction_manual_label) else shotLabel(state), owner?.stopReason?.let { getString(R.string.extraction_stop_reason_suffix, stopLabel(it)) } ?: ""))
        val guidance = when {
            owner?.manualShotActive == true -> getString(R.string.extraction_manual_guidance)
            owner?.scalePreflight == true -> getString(R.string.extraction_preflight_guidance)
            state == ExtractionState.STARTING -> getString(R.string.extraction_starting_guidance)
            state == ExtractionState.RUNNING -> getString(R.string.extraction_running_guidance)
            state == ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_stopping_guidance)
            state == ExtractionState.OUTCOME_UNKNOWN -> getString(R.string.extraction_unknown_guidance)
            state == ExtractionState.ENDED_OBSERVED -> getString(R.string.extraction_ended_guidance)
            else -> owner?.machineControlSafetyMessage ?: blocked ?: studioBlocked ?: getString(R.string.extraction_ready_guidance)
        }
        readiness.show(guidance + unknownAdvice)
        // Only ordinary, complete guidance shares the compact chrome. Every warning keeps its full row.
        val inlineGuidance = owner?.manualShotActive != true && owner?.scalePreflight != true &&
            unknownAdvice.isEmpty() && owner?.machineControlSafetyMessage == null &&
            preparation == BrewPreparation.State.IDLE && !settingBusy && !sleepBusy &&
            (state == ExtractionState.RUNNING || state == ExtractionState.ENDED_OBSERVED ||
                state == ExtractionState.IDLE && blocked == null && studioBlocked == null)
        // Reuse the exact resolved selection used by startSelectedCurve(), not the last record.
        val pendingRecipe = ManualPendingRecipe.name(observedCup, owner?.chartCurveId,
            profile?.name, profile?.let(library::validated) == true)
        identityBlock.show(displayCurveName, pendingRecipe)
        compactClock.show(shotLabel(state))
        compactClock.setTextColor(getColor(if(state==ExtractionState.RUNNING) R.color.mobile_success else if(state==ExtractionState.OUTCOME_UNKNOWN) R.color.mobile_danger else R.color.mobile_text))
        compactElapsed.show(elapsedText)
        compactElapsed.contentDescription=elapsedText + if(BuildConfig.MOCK_MODE) " · Mock · " + getString(R.string.extraction_mock_subtitle) else ""
        compactMode.contentDescription=if(BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_subtitle) else ""
        compactGuidance.show("")
        compactGuidance.visibility = View.GONE
        compactIdentity.setOnLongClickListener { detailsButton.performClick(); true }
        readiness.setTextColor(getColor(if (state == ExtractionState.OUTCOME_UNKNOWN ||
            (state == ExtractionState.IDLE && (blocked != null || studioBlocked != null)))
            R.color.mobile_danger else R.color.mobile_text))
        chooseCurve.visibility = if (presetSlot == 7 && (item == null || profile == null || !library.canStart(item)) &&
            !ShotGate.active(state) && owner?.manualShotActive != true) View.VISIBLE else View.GONE
        chooseCurve.show(if (item == null) getString(R.string.extraction_choose_curve) else getString(R.string.home_curve_replace))
        readiness.setTextColor(if (state == ExtractionState.OUTCOME_UNKNOWN) visual.warningInk
            else if (state == ExtractionState.IDLE && (blocked != null || studioBlocked != null)) visual.stop else style.ink)
        readiness.background = if (state == ExtractionState.OUTCOME_UNKNOWN) android.graphics.drawable.GradientDrawable().apply {
            setColor(visual.warningSurface); setStroke(dp(1), visual.warningBorder); cornerRadius = dp(12).toFloat()
        } else null
        val coffeeMissing = snapshot.coffeeState != DeviceState.READY
        val requiredScaleMissing = profile?.targetHundredthsGram?.let { it > 0 } == true &&
            snapshot.scaleState != DeviceState.READY
        connectDevices.visibility = if (!ShotGate.active(state) && owner?.manualShotActive != true &&
            (coffeeMissing || requiredScaleMissing))
            View.VISIBLE else View.GONE
        connectDevices.show(when {
            coffeeMissing && requiredScaleMissing -> getString(R.string.extraction_connect_both)
            coffeeMissing -> getString(R.string.extraction_connect_coffee)
            else -> getString(R.string.extraction_connect_scale)
        })
        preparationStatus.show(if (snapshot.settings == null) getString(R.string.extraction_mode_missing) else if (!studio) getString(R.string.extraction_cafe_mode) else
            getString(R.string.extraction_studio_summary, corrected?.let(::number) ?: "—", (profile?.temperatureC ?: "—").toString()) +
                when (preparation) {
                    BrewPreparation.State.IDLE -> if (temperatureReady) getString(R.string.extraction_temperature_ready) else getString(R.string.extraction_temperature_not_ready)
                    BrewPreparation.State.WRITING -> getString(R.string.extraction_prepare_writing)
                    BrewPreparation.State.WAITING_TEMP -> getString(R.string.extraction_prepare_waiting)
                    BrewPreparation.State.READY -> if (temperatureReady) getString(R.string.extraction_prepare_ready) else getString(R.string.extraction_prepare_off_target)
                    BrewPreparation.State.CANCELLING -> getString(R.string.extraction_prepare_cancelling)
                    BrewPreparation.State.CANCEL_WRITTEN -> getString(R.string.extraction_prepare_cancel_written)
                    BrewPreparation.State.FAILED -> getString(R.string.extraction_prepare_failed)
                    BrewPreparation.State.UNKNOWN -> getString(R.string.extraction_prepare_unknown)
                })
        // Only fresh, non-actionable idle prose may live with the supporting details.
        // Writing, temperature wait, cancellation, failures and unknown outcomes stay fixed.
        val normalPreparation = snapshot.settings != null && owner?.machineBrewSettingsAvailable == true &&
            owner.machineControlSafetyMessage == null && preparation == BrewPreparation.State.IDLE &&
            (!studio || temperatureReady)
        preparationSummary.show(preparationStatus.text.toString())
        preparationSummary.visibility = if (normalPreparation) View.VISIBLE else View.GONE
        notificationStatus.show(if (BuildConfig.MOCK_MODE || notificationsAllowed()) "" else
            getString(R.string.extraction_notification_warning))
        alarmStatus.show(machineAlarms.describe(snapshot.alarmBits, snapshot.alarmAt,
            MobileMonotonicClock.elapsedRealtime()))
        val now = MobileMonotonicClock.elapsedRealtime()
        val frame = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt, now)
        elapsedValue.show(if (frame is ExtractionTelemetry && !frame.brewWait) "${frame.elapsedSeconds} s" else "—")
        pressureValue.show(getString(R.string.task_extraction_pressure, when (frame) {
            is ExtractionTelemetry -> String.format(Locale.ROOT, "%.1f", frame.pressureTenthsBar / 10.0)
            is IdleTelemetry -> String.format(Locale.ROOT, "%.1f", frame.brewPressureTenthsBar / 10.0)
            else -> "—"
        }))
        scaleReadout.present(owner?.extractionScaleValue(now)
            ?: ExtractionScaleReadout.live(snapshot.scaleObservation, snapshot.scaleState, now))
        val scale = LiveTelemetry.scale(snapshot.scaleObservation, snapshot.scaleState, now)?.hundredthsGram
        chart.unavailableLiveMetrics = buildSet {
            if (frame == null) addAll(listOf(ChartMetric.PRESSURE, ChartMetric.WATER, ChartMetric.TEMPERATURE))
            if (scale == null) addAll(listOf(ChartMetric.FLOW, ChartMetric.YIELD))
        }
        seriesSelector?.refreshReadings()
        // Connected but stale scales stay explicitly unknown; no cup/tare evidence is inferred.
        val scaleQuantity = scale != null || snapshot.scaleState == DeviceState.READY
        quantityLabel.show(getString(if (scaleQuantity) R.string.task_extraction_scale else R.string.task_extraction_water))
        val water = (frame as? ExtractionTelemetry)?.takeUnless { it.brewWait }?.totalWaterTenthsMl
        val waterText = "${water?.let { String.format(Locale.ROOT, "%.1f", it / 10.0) } ?: "—"} ml"
        weightValue.show(if (scaleQuantity) "${scale?.let(::number) ?: "—"} g" else waterText)
        waterValue.show(getString(R.string.task_extraction_water) + " " + waterText)
        (waterValue.parent.parent as View).visibility = if (scaleQuantity) View.VISIBLE else View.GONE
        val cupFlow = if (scale != null && frame is ExtractionTelemetry && !frame.brewWait)
            CupFlowProjection.values(owner?.chartPoints.orEmpty()).lastOrNull() else null
        flowValue.show(getString(R.string.task_extraction_flow,
            cupFlow?.let { String.format(Locale.ROOT, "%.1f", it) } ?: "—"))
        val rawTemperature = when (frame) {
            is ExtractionTelemetry -> number(frame.brewTemperatureHundredthsC)
            is IdleTelemetry -> number(frame.brewTemperatureHundredthsC)
            else -> "—"
        }
        temperatureValue.show(getString(R.string.task_extraction_temperature, rawTemperature))
        live.show(getString(R.string.extraction_live_temperature, rawTemperature))
        val target = if (state != ExtractionState.IDLE) owner?.activeShotTargetHundredthsGram
            else profile?.targetHundredthsGram
        val showStopModePreview=DirectExtractionStart.showStopModePreview(active,profile!=null,state)
        weightTarget.visibility=if(showStopModePreview) View.VISIBLE else View.GONE
        weightTarget.show(if(showStopModePreview && profile!=null) DirectExtractionStart.stopMode(profile).render {id,args->getString(id,*args)}
            else if (owner?.manualShotActive != true && target != null && target > 0) {
            val progress = getString(R.string.extraction_weight_progress, scale?.let(::number) ?: "—", number(target))
            val advice = when {
                owner?.scalePreflight == true -> getString(R.string.extraction_preflight_weight_advice)
                owner?.stopReason == StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_target_stop_advice)
                owner?.stopReason == StopReason.MANUAL.name -> getString(R.string.extraction_manual_stop_advice)
                owner?.stopReason == StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_scale_stop_advice)
                state == ExtractionState.RUNNING -> getString(R.string.extraction_auto_stop_advice)
                state == ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_pending_stop_advice)
                else -> getString(R.string.extraction_weight_control_advice)
            }
            "$progress\n$advice"
        } else if (target == 0 && profile != null) getString(R.string.extraction_no_weight_advice) else "")
        chart.traceIdentity = owner?.chartTraceIdentity
        // The selected recipe defines the final Y scale; manual/unknown cups keep the margin policy.
        chart.expectedRanges = if (owner?.manualShotActive == true || owner?.chartCurveId == "manual")
            emptyMap() else ChartAxisSeed.from(profile)
        val rawPoints=owner?.chartPoints ?: emptyList()
        val points = ExtractionReference.align(rawPoints).points
        if (chart.points != points) chart.points = points
        val plottedItem=if(rawPoints.isNotEmpty() || active || state == ExtractionState.OUTCOME_UNKNOWN) owner?.chartCurveId?.let(library::find) else item
        traceCurve.show(if(rawPoints.isNotEmpty()) getString(R.string.reference_trace_curve,
            plottedItem?.name ?: getString(if(owner?.chartCurveId=="manual") R.string.extraction_manual_label
                else R.string.profiles_missing)) else "")
        val referenceCurve = plottedItem?.id?.takeIf { owner?.manualShotActive != true }
        referenceChoice.isEnabled = referenceCurve != null
        if (referenceCurveId != referenceCurve) {
            referenceCurveId = referenceCurve
            loadReference(referenceId, referenceCurve)
        }
        stageTargets.show(plottedItem?.let { source ->
            val targets=CurveStageParameters.pressureTargets(source)
            if(targets.isNotEmpty())
                targets.mapIndexed {index,target -> getString(R.string.reference_stage,(index+1).toString(),
                    String.format(Locale.ROOT,"%.1f",target/10.0))}.joinToString("\n")
            else getString(R.string.reference_raw)
        }.orEmpty())
        prepare.isEnabled = owner?.running == true && owner.machineControlSafetyMessage == null &&
            blocked == null && owner.machineBrewSettingsAvailable && studio && !temperatureReady &&
            !settingBusy && !sleepBusy &&
            preparation == BrewPreparation.State.IDLE
        cancelPrepare.isEnabled = owner?.running == true && owner?.manualShotActive != true &&
            (preparation != BrewPreparation.State.IDLE ||
                owner?.machineWriteRecoveryKind == MachineWriteRecoveryState.Kind.BREW_WAIT && preparation == BrewPreparation.State.IDLE) &&
            owner?.brewWaitCancelBlock == null
        // Only remove non-actionable preparation controls during a running cup.
        // Unresolved preparation/recovery states retain their controls and fixed status.
        val hideIdlePreparation = active && normalPreparation && !prepare.isEnabled && !cancelPrepare.isEnabled &&
            owner?.machineWriteRecoveryKind != MachineWriteRecoveryState.Kind.BREW_WAIT
        preparationTools.visibility = if (hideIdlePreparation) View.GONE else View.VISIBLE
        cancelPrepare.show(if (preparation == BrewPreparation.State.CANCEL_WRITTEN)
            getString(R.string.extraction_cancel_prepare_again) else getString(R.string.extraction_cancel_prepare))
        // Grey means protected, not unresponsive: tapping only explains the current reason.
        start.isEnabled = true
        val startProtected = blocked != null || studioBlocked != null || settingBusy || sleepBusy
        start.backgroundTintList = android.content.res.ColorStateList.valueOf(
            if (startProtected) style.disabled else visual.liveStart)
        start.setTextColor(if (startProtected) style.onDisabled else android.graphics.Color.WHITE)
        val showPreheatStatus = AutoPreheatPresentation.showStudioStatus(snapshot.settings?.flags,
            owner?.autoPreheatInitialTemperature != null && preparation != BrewPreparation.State.IDLE,
            owner?.autoPreheatMessage)
        val preheatModel = AutoPreheatPresentation.describe(AutoPreheatPresentation.Facts(
            showPreheatStatus, owner?.running == true && snapshot.coffeeState == DeviceState.READY,
            preparation, profile?.id, profile?.temperatureC,
            owner?.brewPreparationProfileId, owner?.brewPreparationTargetC,
            corrected, owner?.autoPreheatInitialTemperature))
        AutoPreheatButton.bind(start, preheatModel, corrected, profile?.temperatureC,
            if (showPreheatStatus) owner?.autoPreheatMessage else null)
        start.contentDescription = listOfNotNull(start.contentDescription?.toString(), blocked ?: studioBlocked)
            .joinToString(" · ")
        val stopAction = StopActionPresentation.describe(state, snapshot.coffeeState, owner?.running == true)
        stop.isEnabled = stopAction.enabled
        stop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        start.visibility = if (stopAction.visible) View.GONE else View.VISIBLE
        stop.show(if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource))
        val facts=ExtractionStatusPresentation.Facts(state,active,owner?.running==true,blocked!=null || studioBlocked!=null,
            owner?.manualShotActive==true,owner?.scalePreflight==true,preparation,!normalPreparation,
            snapshot.alarmBits,snapshot.alarmAt,now,!owner?.machineControlSafetyMessage.isNullOrBlank(),!owner?.manualSafetyMessage.isNullOrBlank(),
            settingBusy || sleepBusy,owner?.settingWriteState==SettingsWriteTracker.State.UNKNOWN || owner?.sleepNowState==SleepNowTracker.State.UNKNOWN,
            notificationStatus.text.isNotBlank(),frame!=null,
            (profile?.targetHundredthsGram ?: 0)>0 || rawPoints.any { it.weightHundredthsGram!=null } || snapshot.scaleState==DeviceState.READY,
            LiveTelemetry.scale(snapshot.scaleObservation,snapshot.scaleState,now)!=null)
        val notices=ExtractionStatusPresentation.signals(facts).map { signal ->
            val detail=when(signal.kind) {
                ExtractionStatusPresentation.Kind.GUIDANCE -> readiness.text.toString()
                ExtractionStatusPresentation.Kind.PREPARATION -> preparationStatus.text.toString()
                ExtractionStatusPresentation.Kind.ALARM -> alarmStatus.text.toString()
                ExtractionStatusPresentation.Kind.MACHINE_SAFETY -> owner?.machineControlSafetyMessage.orEmpty()
                ExtractionStatusPresentation.Kind.MANUAL_SAFETY -> owner?.manualSafetyMessage.orEmpty()
                ExtractionStatusPresentation.Kind.WRITE -> listOfNotNull(
                    if(settingBusy) getString(when(owner?.settingWriteState) {
                        SettingsWriteTracker.State.WRITING -> R.string.machine_settings_setting_writing
                        SettingsWriteTracker.State.UNKNOWN -> R.string.machine_settings_setting_unknown
                        else -> R.string.machine_settings_setting_waiting
                    }) else null,
                    if(sleepBusy) getString(R.string.home_sleep_button) + getString(when(owner?.sleepNowState) {
                        SleepNowTracker.State.WRITING -> R.string.home_sleep_writing_suffix
                        SleepNowTracker.State.UNKNOWN -> R.string.home_sleep_unknown_suffix
                        else -> R.string.home_sleep_waiting_suffix
                    }) else null,
                ).joinToString("\n")
                ExtractionStatusPresentation.Kind.NOTIFICATION -> notificationStatus.text.toString()
                ExtractionStatusPresentation.Kind.MACHINE_DATA -> getString(R.string.home_coffee_status,DeviceStatusText.label(host,snapshot.coffeeState),"") + " · " + getString(R.string.live_chart_no_data)
                ExtractionStatusPresentation.Kind.SCALE_DATA -> getString(R.string.extraction_stop_scale)
            }
            StatusNotice(signal,detail)
        }.filter { it.detail.isNotBlank() }
        presentStatusNotices(notices)
        listOf(readiness,preparationStatus,notificationStatus,alarmStatus).forEach { it.visibility=View.GONE }
        val feedback=if(visible && owner?.running==true) statusTransitions.observe(state,notices.map { it.signal }) else ExtractionStatusPresentation.Transitions.Feedback(false,false)
        if(visible && feedback.announceCritical) chartToolsButton.announceForAccessibility(statusSummary())
        if(visible && feedback.completed) toast(getString(R.string.extraction_ended_guidance))
        syncToolsMenu()
    }
    private fun statusMenuTitle() = getString(R.string.home_live_status) + " (" + statusNotices.size + ")"
    private fun statusSummary() = if(statusNotices.isEmpty()) getString(R.string.task_tools) else
        getString(R.string.task_tools) + " · " + statusMenuTitle() + " · " + statusNotices.first().detail.lineSequence().first()
    private fun statusDetails() = if(statusNotices.isEmpty()) alarmStatus.text.toString() else statusNotices.mapIndexed { i,notice -> "${i+1}. ${notice.detail}" }.joinToString("\n\n")
    private fun presentStatusNotices(notices: List<StatusNotice>) {
        val badgeChanged=statusNotices.isEmpty()!=notices.isEmpty() || statusNotices.firstOrNull()?.signal?.severity!=notices.firstOrNull()?.signal?.severity
        statusNotices=notices
        val summary=statusSummary()
        if(chartToolsButton.contentDescription?.toString()!=summary) chartToolsButton.contentDescription=summary
        if(badgeChanged) chartToolsButton.invalidate()
        statusMenuButton?.let { if(it.text.toString()!=statusMenuTitle()) it.text=statusMenuTitle() }
        statusDetailsText?.let { if(it.text.toString()!=statusDetails()) it.text=statusDetails() }
    }
    private fun showStatusDetails() {
        val text=TextView(host).apply {
            tag="product/extraction/status-details"
            style.apply(this,TaskProductVisualStyle.auxiliary)
            setPadding(dp(8),dp(8),dp(8),dp(8)); this.text=statusDetails()
        }
        statusDetailsText=text
        val dialog=AppDialog.show(host,getString(R.string.home_live_status),text,AppDialog.Action(getString(android.R.string.ok)) {},null)
        statusDetailsDialog=dialog
        dialog.setOnDismissListener { if(statusDetailsText===text) statusDetailsText=null; if(statusDetailsDialog===dialog) statusDetailsDialog=null }
    }
    private fun TextView.show(value: String) {
        val group = readoutGroups[this]
        if (group != null) {
            // Source formatting and eligibility are still the original render() result.
            val match = Regex("[+-]?[0-9]+(?:\\.[0-9]+)?|—").find(value)
            val number = match?.value ?: "—"
            val suffix = when (this) {
                elapsedValue -> if (match == null || value == "—") null else "s"
                weightValue -> if (value.endsWith(" g")) "g" else if (value.endsWith(" ml")) "ml" else null
                pressureValue -> "bar"
                waterValue -> "ml"
                temperatureValue -> "°C"
                else -> null
            }
            group.present(number, suffix); contentDescription = value
        } else if (text.toString() != value) text = value
    }
    private fun chooseReference() {
        val curveId=referenceCurveId ?: return
        val app=application as MobileApplication
        val records=app.journalResult.getOrNull()?.entries().orEmpty().filter {
            it.observation.curveId==curveId && it.observation.status=="ENDED"
        }.take(20)
        val labels=listOf(getString(R.string.reference_off))+records.map {host.journalDate(it.observation.startedAtMs)}
        AlertDialog.Builder(host).setTitle(R.string.reference_choose)
            .setItems(labels.toTypedArray()) {_,index ->
                render()
                if (referenceCurveId == curveId) loadReference(if(index==0) null else records[index-1].observation.id,curveId)
            }
            .setNegativeButton(R.string.machine_settings_cancel,null).let(::showTransient)
    }
    private fun loadReference(id:String?,curveId:String?) {
        if (curveId != referenceCurveId) return
        val app=application as MobileApplication
        val record=id?.let {app.journalResult.getOrNull()?.find(it)}
            ?.takeIf {it.observation.curveId==curveId && it.observation.status=="ENDED"}
        val timeline=record?.let {runCatching {ExtractionReference.align(app.samples.load(it.observation.id))}.getOrNull()}
        val available=timeline?.points?.isNotEmpty()==true
        referenceId=if(available) id else null
        chart.reference=if(available) requireNotNull(timeline).points else emptyList()
        val source = if (available) getString(R.string.reference_source,
            host.journalDate(requireNotNull(record).observation.startedAtMs), "").trim() else null
        referenceStatus.show(if (source != null) source
            else getString(if (id == null) R.string.reference_off else R.string.reference_unavailable))
        chart.referenceLabel = source
    }
    private fun shotLabel(state: ExtractionState) = when (state) {
        ExtractionState.IDLE -> getString(R.string.machine_settings_tab_standby)
        ExtractionState.STARTING -> getString(R.string.extraction_state_starting)
        ExtractionState.RUNNING -> getString(R.string.extraction_state_running)
        ExtractionState.STOP_REQUESTED -> getString(R.string.extraction_state_stopping)
        ExtractionState.ENDED_OBSERVED -> getString(R.string.extraction_state_ended)
        ExtractionState.OUTCOME_UNKNOWN -> getString(R.string.extraction_state_unknown)
    }
    private fun stopLabel(reason: String) = when (reason) {
        StopReason.TARGET_WEIGHT.name -> getString(R.string.extraction_stop_target)
        StopReason.SCALE_UNAVAILABLE.name -> getString(R.string.extraction_stop_scale)
        StopReason.TARE_UNCONFIRMED.name -> getString(R.string.extraction_stop_tare)
        StopReason.START_CONDITIONS_CHANGED.name -> getString(R.string.extraction_stop_changed)
        StopReason.MANUAL.name -> getString(R.string.extraction_stop_manual)
        else -> getString(R.string.extraction_stop_unknown)
    }
    private fun number(value: Int): String = String.format(Locale.ROOT, "%.2f", value / 100.0)
    private fun toast(value: String) = Toast.makeText(host, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val NOTIFICATIONS = 31 }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(host).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6)); if (bold) typeface = TaskTypography.face(context, 700); parent.addView(this)
    }
    private fun card(parent: LinearLayout) = HoyiUi.card(host, parent).apply {
        if (compactChrome) {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
        }
    }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) =
        HoyiUi.button(host, parent, value, action = action)
}
