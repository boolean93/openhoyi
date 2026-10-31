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

/** A screen never owns BLE. Start requires an explicit confirmation; Stop is one tap. */
class ExtractionActivity : ThemedActivity() {
    private val visual by lazy { ExtractionHistoryVisualStyle(this) }
    private val style get() = visual.palette
    private val readoutGroups = mutableMapOf<TextView, HistoryQuantity>()
    internal lateinit var feedbackUi: BrewFeedbackDialog
        private set
    private val machineAlarms by lazy { MachineAlarms(this) }
    private val shotGate by lazy { ShotGate(this) }
    private val presetSlot: Int by lazy { intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7).takeIf { it in 1..5 } ?: 7 }
    private var service: MobileService? = null
    private var bound = false
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
    private lateinit var detailsButton: Button
    private var toolsDialog: AlertDialog? = null
    private lateinit var chooseBeans: Button
    private lateinit var live: TextView
    private lateinit var elapsedValue: TextView
    private lateinit var pressureValue: TextView
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
    private lateinit var chartHost: FrameLayout
    private lateinit var compactClock: TextView
    private lateinit var compactGuidance: TextView
    private lateinit var compactIdentity: TextView
    private lateinit var headerWords: LinearLayout
    private lateinit var pageHeader: LinearLayout
    private lateinit var liveHeading: TextView
    private lateinit var modeBadge: TextView
    // Chrome compaction must not depend on height that this same chrome consumes.
    // The workspace independently uses its actual remaining width and height.
    private val compactChrome get() = resources.configuration.screenWidthDp < 840
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 250) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirectRoot(TaskDestination.BREW, extraction = true)) return
        referenceId = savedInstanceState?.getString("referenceShotId")
        feedbackUi = BrewFeedbackDialog(this, savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.page)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        pageHeader = HoyiUi.header(this, content, getString(R.string.extraction_title),
            if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_subtitle) else null)
        style.styleHeader(pageHeader)
        pageHeader.removeViewAt(0)
        compactIdentity = style.text("", TaskProductVisualStyle.Type(14, 500, 20), "product/extraction/identity").apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        compactClock = style.text("", TaskProductVisualStyle.auxiliary, "product/extraction/clock")
        compactGuidance = style.text("", TaskProductVisualStyle.auxiliary, "product/extraction/inline-guidance", true)
        headerWords = object : LinearLayout(this) {
            init { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                compactClock.measure(natural, natural); compactGuidance.measure(natural, natural)
                val available = View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val stacked = available < dp(112) + compactClock.measuredWidth + compactGuidance.measuredWidth + dp(16)
                orientation = if (stacked) VERTICAL else HORIZONTAL
                listOf(compactIdentity, compactClock, compactGuidance).forEachIndexed { index, child ->
                    val width = if (stacked) -1 else if (index == 0) 0 else -2
                    val weight = if (!stacked && index == 0) 1f else 0f
                    val gap = if (!stacked && index > 0) dp(8) else 0
                    val current = child.layoutParams as LayoutParams
                    if (current.width != width || current.weight != weight || current.marginStart != gap)
                        child.layoutParams = LayoutParams(width, -2, weight).apply { marginStart = gap }
                }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        listOf(compactIdentity, compactClock, compactGuidance).forEach { headerWords.addView(it) }
        pageHeader.addView(headerWords, 0, LinearLayout.LayoutParams(0, -2, 1f))
        pageHeader.minimumHeight = dp(48)
        pageHeader.tag = "product/extraction/header"
        // Secondary prose remains available in a details dialog, not in the plot's height budget.
        val details = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        overviewHeading = HoyiUi.label(this, details, getString(R.string.extraction_overview), 17, true)
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
        val workspace = TaskExtractionWorkspace(this, visual)
        content.addView(workspace, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = 0 })
        val plotColumn = workspace.primaryBody
        val sidePanel = workspace.secondaryBody.apply {
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        style.add(sidePanel, style.text(getString(R.string.task_extraction_supporting), TaskProductVisualStyle.auxiliary, secondary = true))
        curveSummary = text(sidePanel, getString(R.string.extraction_curve_initial), 16, true).apply {
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END; tag = "full-text-details"
            style.apply(this, TaskProductVisualStyle.title); setPadding(0, dp(16), 0, dp(16))
        }
        recipeSummary = TaskRecipeSummary(this, visual)
        sidePanel.addView(recipeSummary, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8); bottomMargin = dp(8) })
        deviceSummary = text(sidePanel, getString(R.string.extraction_devices_initial), 14)
        val metrics = TaskLiveReadouts(this, visual)
        // Detached legacy readouts remain source inputs; the chart owns the visible metric strip.
        fun reading(label: String, quantity: Boolean = false): TextView {
            val cell = style.column("product/extraction/" + if (quantity) "quantity-cell" else "elapsed-cell")
            metrics.addView(cell)
            val heading = style.text(label, TaskProductVisualStyle.auxiliary, secondary = true)
            style.add(cell, heading)
            if (quantity) quantityLabel = heading
            val group = HistoryQuantity(this, visual, if (quantity) "product/extraction/quantity" else "product/extraction/elapsed", focus = true)
            style.add(cell, group, 8)
            return group.number.also { readoutGroups[it] = group }
        }
        elapsedValue = reading(getString(R.string.task_extraction_elapsed))
        weightValue = reading(getString(R.string.task_extraction_water), quantity = true)
        val secondaryReadings = style.row("product/extraction/readouts-support")
        metrics.addView(secondaryReadings)
        fun supportReading(label: Int, id: String): TextView {
            val row = style.row("$id/cell").apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            secondaryReadings.addView(row, LinearLayout.LayoutParams(-2, -2))
            row.addView(style.text(getString(label), TaskProductVisualStyle.auxiliary, "$id/label", true),
                LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            val group = HistoryQuantity(this, visual, id, large = false)
            row.addView(group)
            return group.number.also { readoutGroups[it] = group }
        }
        pressureValue = supportReading(R.string.task_chart_pressure, "product/extraction/pressure")
        waterValue = supportReading(R.string.task_chart_water, "product/extraction/water")
        temperatureValue = supportReading(R.string.task_chart_temperature, "product/extraction/temperature")
        flowValue = text(details, "", 14)
        chartHost = FrameLayout(this).apply { tag = "product/extraction/chart-host" }
        chart = ExtractionOverlayView(this, visual).apply {
            tag = "product/extraction/chart"; background = style.shape(style.surface)
            liveCompactChrome = true
        }
        chartHost.addView(chart, FrameLayout.LayoutParams(-1, -1))
        // One shared 96dp budget: identity above metrics, pressure dial on their right.
        content.removeView(pageHeader)
        chartHost.addView(pageHeader, FrameLayout.LayoutParams(-1, -2, android.view.Gravity.TOP).apply { marginEnd = dp(112) })
        pageHeader.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> chart.liveHeaderHeightPx = pageHeader.height }
        val chartTools = Button(this).apply {
            text = getString(R.string.task_tools); contentDescription = text
            tag = "product/extraction/chart-tools"
            style.button(this, outline = true)
            minimumWidth = dp(48); minimumHeight = dp(48)
            setOnClickListener { showTools() }
        }
        pageHeader.addView(chartTools, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        plotColumn.addView(chartHost, LinearLayout.LayoutParams(-1, 0, 1f))
        workspace.onPrimarySpaceAvailable = { _, height ->
            maxOf((dp(260) * resources.configuration.fontScale).toInt(), (height * .70f).toInt())
        }
        val toolbar = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sidePanel.addView(toolbar)
        fun compact(parent: LinearLayout, title: String, action: () -> Unit): Button =
            HoyiUi.button(this, parent, title, action = action).apply {
                style.button(this)
                minHeight = dp(48); minimumHeight = dp(48)
                layoutParams = if (parent.orientation == LinearLayout.VERTICAL)
                    LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) }
                else LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(4) }
            }
        chooseCurve = compact(toolbar, getString(R.string.extraction_choose_curve)) { startActivity(Intent(this, CurveActivity::class.java)) }
        connectDevices = compact(toolbar, getString(R.string.extraction_connect_devices)) {
            startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        }
        chooseBeans = compact(toolbar, getString(R.string.dose_title)) { startActivity(Intent(this, BeanPreparationActivity::class.java)) }
        val referenceTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sidePanel.addView(referenceTools, LinearLayout.LayoutParams(-1, -2))
        preparationTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sidePanel.addView(preparationTools, LinearLayout.LayoutParams(-1, -2))
        referenceChoice = compact(referenceTools, getString(R.string.reference_choose)) { chooseReference() }
        detailsButton = compact(referenceTools, getString(R.string.extraction_overview)) {
            val info = listOf(curveSummary, deviceSummary, readiness, shotSummary, live, flowValue, weightTarget, traceCurve, stageTargets, referenceStatus)
                .map { it.text.toString() }.filter { it.isNotBlank() }.joinToString("\n\n") +
                "\n\n" + recipeSummary.text + "\n\n" + getString(R.string.live_cup_flow_source)
            AlertDialog.Builder(this).setTitle(getString(R.string.extraction_overview)).setMessage(info)
                .setPositiveButton(android.R.string.ok, null).show()
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
        val actionDock = TaskActionDock(this, edgeInsetDp = 0)
        workspace.setFooter(actionDock)
        start = HoyiUi.button(this, actionDock, getString(R.string.extraction_start), primary = true) { confirmStart() }
        (start.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)
        stop = HoyiUi.button(this, actionDock, getString(R.string.extraction_stop), danger = true) {
            service?.stopShot(); render()
        }.apply {
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(this@ExtractionActivity, R.color.mobile_stop_button, 10)
            visibility = View.GONE
        }
        (stop.layoutParams as LinearLayout.LayoutParams).setMargins(0, 0, 0, 0)
        listOf(deviceSummary, preparationSummary, referenceStatus, shotSummary, live, traceCurve, stageTargets, weightTarget).forEach {
            style.apply(it, TaskProductVisualStyle.auxiliary, true)
        }
        listOf(readiness, preparationStatus, notificationStatus, alarmStatus).forEach {
            style.apply(it, TaskProductVisualStyle.auxiliary)
            it.setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        style.button(start, primary = true); start.tag = "product/extraction/start"
        visual.stopButton(stop); stop.tag = "product/extraction/stop"
        actionDock.tag = "product/extraction/actions"; actionDock.setPadding(dp(16), dp(8), dp(16), 0)
        TaskPageFrame.install(this, root, style)
        window.statusBarColor = style.page; window.navigationBarColor = style.surface
        render()
    }
    override fun onStart() {
        if (redirectedRoot) { super.onStart(); return }
        feedbackUi.start()
        super.onStart(); visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
        if (!BuildConfig.MOCK_MODE) requestSafetyNotificationsOnce()
    }
    override fun onStop() {
        if (redirectedRoot) { super.onStop(); return }
        feedbackUi.leave(isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        toolsDialog?.dismiss(); toolsDialog = null
        release(); super.onStop()
    }
    private fun showTools() {
        if (toolsDialog?.isShowing == true) return
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listOf(chooseCurve, connectDevices, chooseBeans, referenceChoice, detailsButton, prepare, cancelPrepare).forEach { original ->
            HoyiUi.button(this, body, original.text.toString()) { original.performClick() }.isEnabled = original.isEnabled
        }
        toolsDialog = AppDialog.show(this, getString(R.string.task_tools), body,
            AppDialog.Action(getString(android.R.string.ok)) {}, null)
    }
    private fun release() { if (bound) { unbindService(connection); bound = false }; service = null }
    private fun notificationsAllowed(): Boolean = BuildConfig.MOCK_MODE || Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    private fun requestSafetyNotificationsOnce() {
        if (notificationsAllowed()) return
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
            .getString(PresetSlots.key(presetSlot), null))
        return id?.let(library::find)
    }
    private fun confirmStart() {
        val owner = service ?: return
        val library = (application as MobileApplication).curves
        val profile = selected()?.let { library.resolve(it.id, owner.snapshot.scaleState == DeviceState.READY, presetSlot) }
        val blocked = owner.tareStartBlock ?: shotGate.startBlock(profile, owner.snapshot.coffeeState, owner.snapshot.coffee, owner.snapshot.coffeeAt, owner.snapshot.scaleState,
            owner.snapshot.weightAt, MobileMonotonicClock.elapsedRealtime(), owner.shotState,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) { toast(blocked); render(); return }
        requireNotNull(profile)
        owner.studioStartBlock(profile)?.let { toast(it); render(); return }
        val effect = if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_effect)
            else getString(R.string.extraction_real_effect)
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_start_title) else getString(R.string.extraction_real_start_title))
            .setMessage(getString(R.string.extraction_start_message, profile.name, if (profile.parameters.slot == 7) getString(R.string.home_current_curve) else getString(R.string.extraction_shortcut_slot, profile.parameters.slot.toString()), profile.temperatureC.toString(), profile.maximumWaterMl.toString(), if (profile.targetHundredthsGram > 0) getString(R.string.extraction_target_weight_format, number(profile.targetHundredthsGram)) else getString(R.string.extraction_no_weight_target), effect) +
                if (notificationsAllowed()) "" else getString(R.string.extraction_notification_warning_suffix))
            .setPositiveButton(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_start) else getString(R.string.extraction_real_start)) { _, _ ->
                owner.startShot(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    private fun confirmPrepare() {
        val owner = service ?: return
        val profile = selected()?.let {
            (application as MobileApplication).curves.resolve(it.id,
                owner.snapshot.scaleState == DeviceState.READY, presetSlot)
        } ?: run { toast(getString(R.string.extraction_choose_startable)); return }
        val corrected = owner.currentCorrectedBrewTemperature()
        val current = corrected?.let { number(it) + " °C" } ?: getString(R.string.settings_unknown)
        AlertDialog.Builder(this).setTitle(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_title) else getString(R.string.extraction_real_prepare_title))
            .setMessage(getString(R.string.extraction_prepare_message, current, profile.temperatureC.toString()) +
                if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_effect)
                else getString(R.string.extraction_real_prepare_effect))
            .setPositiveButton(if (BuildConfig.MOCK_MODE) getString(R.string.extraction_mock_prepare_start) else getString(R.string.extraction_real_prepare_start)) { _, _ ->
                owner.prepareBrew(profile.id, profile.scaleMode, presetSlot)?.let(::toast)
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        if (redirectedRoot) { super.onSaveInstanceState(outState); return }
        feedbackUi.save(outState)
        outState.putString("referenceShotId",referenceId)
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
        pageHeader.visibility = View.VISIBLE
        chart.compactRunningExplanation = active
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
        val blocked = if (owner?.manualShotActive == true) getString(R.string.extraction_manual_block)
            else if (item != null && profile == null) getString(R.string.extraction_invalid_curve_block)
            else owner?.tareStartBlock ?: shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
                snapshot.weightAt, MobileMonotonicClock.elapsedRealtime(), state,
                validated = profile?.let(library::validated) == true)
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
        deviceSummary.show(getString(R.string.extraction_device_summary, DeviceStatusText.label(this, snapshot.coffeeState), DeviceStatusText.label(this, snapshot.scaleState)))
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
        compactIdentity.show(displayCurveName)
        compactIdentity.contentDescription = displayCurveName
        compactClock.show((if (inlineGuidance) "" else shotLabel(state) + " · ") + elapsedText +
            if (BuildConfig.MOCK_MODE) " · Mock" else "")
        compactClock.contentDescription = compactClock.text.toString() +
            if (BuildConfig.MOCK_MODE) " · " + getString(R.string.extraction_mock_subtitle) else ""
        compactGuidance.show(if (inlineGuidance) guidance else "")
        compactGuidance.visibility = if (inlineGuidance) View.VISIBLE else View.GONE
        readiness.visibility = if (inlineGuidance) View.GONE else View.VISIBLE
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
        preparationStatus.visibility = if (normalPreparation) View.GONE else View.VISIBLE
        notificationStatus.show(if (BuildConfig.MOCK_MODE || notificationsAllowed()) "" else
            getString(R.string.extraction_notification_warning))
        notificationStatus.visibility = if (notificationStatus.text.isEmpty()) View.GONE else View.VISIBLE
        alarmStatus.show(machineAlarms.describe(snapshot.alarmBits, snapshot.alarmAt,
            MobileMonotonicClock.elapsedRealtime()))
        val now = MobileMonotonicClock.elapsedRealtime()
        alarmStatus.visibility = if (machineAlarms.showExtractionStatus(active, snapshot.alarmBits, snapshot.alarmAt, now))
            View.VISIBLE else View.GONE
        val frame = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt, now)
        elapsedValue.show(if (frame is ExtractionTelemetry && !frame.brewWait) "${frame.elapsedSeconds} s" else "—")
        pressureValue.show(getString(R.string.task_extraction_pressure, when (frame) {
            is ExtractionTelemetry -> String.format(Locale.ROOT, "%.1f", frame.pressureTenthsBar / 10.0)
            is IdleTelemetry -> String.format(Locale.ROOT, "%.1f", frame.brewPressureTenthsBar / 10.0)
            else -> "—"
        }))
        val scale = LiveTelemetry.scale(snapshot.scaleObservation, snapshot.scaleState, now)?.hundredthsGram
            ?: LiveTelemetry.scale(snapshot.weight, snapshot.scaleState, snapshot.weightAt, now)?.weightHundredthsGram
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
        weightTarget.show(if (owner?.manualShotActive != true && target != null && target > 0) {
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
        start.isEnabled = owner?.running == true && owner.machineControlSafetyMessage == null &&
            blocked == null && studioBlocked == null &&
            !settingBusy && !sleepBusy
        val stopAction = StopActionPresentation.describe(state, snapshot.coffeeState, owner?.running == true)
        stop.isEnabled = stopAction.enabled
        stop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        start.visibility = if (stopAction.visible) View.GONE else View.VISIBLE
        stop.show(if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource))
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
        val labels=listOf(getString(R.string.reference_off))+records.map {journalDate(it.observation.startedAtMs)}
        AlertDialog.Builder(this).setTitle(R.string.reference_choose)
            .setItems(labels.toTypedArray()) {_,index ->
                render()
                if (referenceCurveId == curveId) loadReference(if(index==0) null else records[index-1].observation.id,curveId)
            }
            .setNegativeButton(R.string.machine_settings_cancel,null).show()
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
            journalDate(requireNotNull(record).observation.startedAtMs),
            getString(if (requireNotNull(timeline).observedOrigin) R.string.reference_align_observed else R.string.reference_align_recorded)) else null
        referenceStatus.show(if (source != null) getString(R.string.reference_legend) + "\n" + source
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
    private fun toast(value: String) = Toast.makeText(this, value, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private companion object { const val NOTIFICATIONS = 31 }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6)); if (bold) typeface = TaskTypography.face(context, 700); parent.addView(this)
    }
    private fun card(parent: LinearLayout) = HoyiUi.card(this, parent).apply {
        if (compactChrome) {
            setPadding(dp(12), dp(8), dp(12), dp(8))
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
        }
    }
    private fun button(parent: LinearLayout, value: String, action: () -> Unit) =
        HoyiUi.button(this, parent, value, action = action)
}
