package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.StandaloneTare
import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.SpannableString
import android.text.Spanned
import android.text.InputFilter
import android.text.InputType
import android.text.style.RelativeSizeSpan
import android.view.View
import android.view.WindowInsets
import android.widget.*
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import java.util.Locale

/** First native product screen: BLE connection, live values, and a selected captured curve. */
class HomeFragment : androidx.fragment.app.Fragment() {
    private val host get() = requireActivity() as MainActivity
    private val application get() = host.application
    private val MODE_PRIVATE=android.content.Context.MODE_PRIVATE
    private val RESULT_OK=android.app.Activity.RESULT_OK
    private val serviceObserver:(MobileService?)->Unit={service=it;render()}
    private fun getColor(id:Int)=requireContext().getColor(id)
    private fun getSharedPreferences(name:String,mode:Int)=host.getSharedPreferences(name,mode)
    private fun checkSelfPermission(permission:String)=host.checkSelfPermission(permission)
    private fun <T> getSystemService(serviceClass:Class<T>):T?=host.getSystemService(serviceClass)
    internal lateinit var feedbackUi: BrewFeedbackDialog
        private set
    private val machineAlarms by lazy { MachineAlarms(host) }
    private val settingsPresentation by lazy { MachineSettingsPresentation(host) }
    private val firmwarePresentation by lazy { FirmwarePresentation(host) }
    private var service: MobileService? = null
    private var visible = false
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var safetyWarning: TextView
    private lateinit var prominentAlarm: TextView
    private lateinit var acknowledgeManual: Button
    private lateinit var coffee: TextView
    private lateinit var coffeeDot: View
    private lateinit var brewTemperature: TextView
    private lateinit var brewBoilerTemperature: TextView
    private lateinit var brewBoilerPressure: TextView
    private lateinit var steamBoilerTemperature: TextView
    private lateinit var steamBoilerPressure: TextView
    private lateinit var brewBoilerTarget: TextView
    private lateinit var steamBoilerTarget: TextView
    private lateinit var brewBoilerToggle: Button
    private lateinit var steamBoilerToggle: Button
    private lateinit var quickRecipeTitle: TextView
    private lateinit var quickLaunch: Button
    private lateinit var quickRow: LinearLayout
    private lateinit var machineConnect: Button
    private lateinit var brewPressure: TextView
    private lateinit var steamTemperature: TextView
    private lateinit var steamPressure: TextView
    private lateinit var leverStatus: TextView
    private lateinit var leverButton: Button
    private lateinit var sleepStatus: TextView
    private lateinit var sleepButton: Button
    private lateinit var emergencyStop: Button
    private lateinit var firmwareStatus: TextView
    private lateinit var alarmStatus: TextView
    private lateinit var scale: TextView
    private lateinit var scaleDot: View
    private lateinit var scaleWeight: TextView
    private lateinit var tareStatus: TextView
    private lateinit var selection: TextView
    private lateinit var recipeDescription: TextView
    private lateinit var brewButton: Button
    private lateinit var browseCurvesButton: Button
    private lateinit var presetButtons: List<Button>
    private lateinit var directPresetButtons: List<Button>
    private lateinit var candidates: LinearLayout
    private lateinit var scanButton: Button
    private lateinit var coffeeDisconnect: Button
    private lateinit var scaleDisconnect: Button
    private lateinit var tareButton: Button
    private var candidateKeys = emptyList<String>()
    private lateinit var devicePanel: LinearLayout
    private var deviceDialog: AlertDialog? = null
    private lateinit var presetPanel: LinearLayout
    private var presetDialog: AlertDialog? = null
    private val transientDialogs = mutableListOf<AlertDialog>()
    private fun showTransient(builder: AlertDialog.Builder) {
        transientDialogs += AppDialog.show(builder.create())
    }
    private lateinit var stagePreview: CurveStageView
    private var hasRecipeMetrics = false
    private lateinit var recipeMetrics: TaskProductRecipeMetrics
    private lateinit var previewNote: TextView
    private lateinit var previewUnavailable: TextView
    private lateinit var stageFallback: ScrollView
    private lateinit var stageFallbackText: TextView
    private var textStagePreview = false
    private lateinit var beanSummary: TextView
    private lateinit var doseStateSummary: TextView
    private lateinit var doseQuantity: TextView
    private lateinit var doseUnit: TextView
    private val productStyle by lazy { TaskProductVisualStyle(host) }
    private lateinit var temperatureSummary: TextView
    private lateinit var usageSummary: TextView
    private lateinit var recentCurves: HomeRecentCurves
    private var selectionDetails = ""
    private lateinit var quickPresets: Button
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 250) }
    }
    override fun onCreateView(inflater: android.view.LayoutInflater, container: android.view.ViewGroup?, savedInstanceState: Bundle?): View {
        feedbackUi = BrewFeedbackDialog(host, savedInstanceState)
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(productStyle.page)
        }
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(8)) }
        root.addView(ScrollView(host).apply {
            tag = "product/home/page-scroll"
            isFillViewport = false
            addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        emergencyStop = Button(host).apply {
            text = getString(R.string.home_stop)
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(host, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        root.addView(emergencyStop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(12))
        })
        safetyWarning = text(content, "", 17, true).apply {
            setTextColor(getColor(R.color.mobile_danger)); visibility = View.GONE
        }
        acknowledgeManual = button(content, getString(R.string.home_recovery_button)) {
            val recoveryKind = if (service?.manualSafetyMessage == null)
                service?.machineWriteRecoveryKind else null
            val title = when (recoveryKind) {
                MachineWriteRecoveryState.Kind.CUP_RESET -> getString(R.string.home_recovery_cups_title)
                MachineWriteRecoveryState.Kind.SETTING -> getString(R.string.home_recovery_setting_title)
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> getString(R.string.home_recovery_schedule_title)
                MachineWriteRecoveryState.Kind.SLEEP_NOW -> getString(R.string.home_recovery_sleep_title)
                MachineWriteRecoveryState.Kind.BREW_WAIT -> getString(R.string.home_recovery_preheat_title)
                else -> getString(R.string.home_recovery_shot_title)
            }
            val message = when (recoveryKind) {
                MachineWriteRecoveryState.Kind.CUP_RESET ->
                    getString(R.string.home_recovery_cups_message)
                MachineWriteRecoveryState.Kind.SETTING ->
                    getString(R.string.home_recovery_setting_message)
                MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE ->
                    getString(R.string.home_recovery_schedule_message)
                MachineWriteRecoveryState.Kind.SLEEP_NOW ->
                    getString(R.string.home_recovery_sleep_message)
                MachineWriteRecoveryState.Kind.BREW_WAIT ->
                    getString(R.string.home_recovery_preheat_message)
                else -> getString(R.string.home_recovery_shot_message)
            }
            AlertDialog.Builder(host).setTitle(title).setMessage(message)
                .setPositiveButton(getString(R.string.home_recovery_clear)) { _, _ -> service?.acknowledgeManualSafety(); render() }
                .setNegativeButton(getString(R.string.machine_settings_cancel), null).let(::showTransient)
        }.apply { visibility = View.GONE }
        prominentAlarm = TextView(host).apply {
            textSize = 16f
            typeface = TaskTypography.face(context, 700)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = HoyiUi.shape(host, R.color.mobile_accent_soft, 12, R.color.mobile_border)
            visibility = View.GONE
        }
        content.addView(prominentAlarm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })

        // Device controls retain their live views and write gates in an explicit panel.
        // Only this auxiliary panel scrolls; the main task surface remains fixed.
        devicePanel = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(8), dp(16), dp(16))
        }
        val deviceCard = devicePanel
        button(deviceCard, getString(R.string.app_settings_title)) {
            host.select(TaskDestination.SETTINGS)
        }
        button(deviceCard, getString(R.string.extraction_title)) {
            host.showExtraction()
        }
        status = text(content, getString(R.string.home_not_connected_initial), 17, true).apply { tag = "product/home/service-status" }
        content.removeView(status)
        if (resources.configuration.screenWidthDp >= 800 && resources.configuration.screenHeightDp > 420)
            content.addView(productStyle.text(getString(R.string.home_machine_title), TaskProductVisualStyle.title,
                "product/home/title"))
        val machineRow = productStyle.row("product/home/machine-status").apply {
            gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = dp(48)
        }
        content.addView(machineRow, LinearLayout.LayoutParams(-1, -2))
        machineRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        machineConnect = button(machineRow, getString(R.string.home_connect)) { showDevicePanel() }.apply {
            tag = "product/home/connect"; layoutParams = LinearLayout.LayoutParams(-2, dp(48))
        }
        val spaciousHome = resources.configuration.screenWidthDp >= 800 && resources.configuration.screenHeightDp > 420
        val boilerRow = MachineHomeView(host, spaciousHome).apply { tag = "product/home/boilers" }
        content.addView(boilerRow, LinearLayout.LayoutParams(-1, if (spaciousHome) dp(360) else -2).apply { topMargin = dp(8) })
        fun boiler(label: Int, tagName: String, change: () -> MachineSettingChange): List<View> {
            val card = productStyle.column("product/home/$tagName").apply {
                setPadding(dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 8 else 4),
                    dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 8 else 4))
                background = productStyle.shape(productStyle.surface, true)
            }
            boilerRow.addBoiler(card)
            val heading = productStyle.text(getString(label), TaskProductVisualStyle.auxiliary, secondary = true)
            heading.text = getString(if (tagName == "brew-boiler") R.string.home_brew_boiler else R.string.home_steam_boiler)
            val value = productStyle.text("—", TaskProductVisualStyle.Type(if (spaciousHome) 40 else 19, 700,
                if (spaciousHome) 48 else 25), "product/home/$tagName/temperature")
            val pressure = productStyle.text("—", TaskProductVisualStyle.Type(if (spaciousHome) 40 else 19, 700,
                if (spaciousHome) 48 else 25), "product/home/$tagName/pressure")
            val target = productStyle.text("—", TaskProductVisualStyle.auxiliary, "product/home/$tagName/target", true)
            val toggle = button(card, getString(R.string.setting_on)) { confirmBoilerChange(change()) }.apply {
                tag = "product/home/$tagName/toggle"; minimumHeight = dp(48)
            }
            val controls = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            controls.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
            (toggle.parent as? android.view.ViewGroup)?.removeView(toggle)
            controls.addView(toggle, LinearLayout.LayoutParams(-2, dp(48)))
            card.addView(controls)
            val readings = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            fun reading(labelId: Int, field: TextView) {
                val column = productStyle.column()
                column.addView(productStyle.text(getString(labelId), TaskProductVisualStyle.auxiliary, secondary = true))
                column.addView(field)
                readings.addView(column, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (readings.childCount > 0) marginStart = dp(if (spaciousHome) 16 else 4)
                })
            }
            reading(R.string.home_brew_temperature.takeIf { tagName == "brew-boiler" } ?: R.string.home_steam_temperature, value)
            reading(R.string.home_brew_pressure.takeIf { tagName == "brew-boiler" } ?: R.string.home_steam_pressure, pressure)
            card.addView(readings, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(if (spaciousHome) 4 else 0) })
            productStyle.add(card, target)
            return listOf(value, pressure, target, toggle)
        }
        boiler(R.string.home_brew_temperature, "brew-boiler") {
            MachineSettingChange.BrewHeating(!(service?.snapshot?.settings?.brewHeating ?: false))
        }.let { (value, pressure, target, toggle) -> brewBoilerTemperature=value as TextView; brewBoilerPressure=pressure as TextView;
            brewBoilerTarget=target as TextView; brewBoilerToggle=toggle as Button }
        boiler(R.string.home_steam_temperature, "steam-boiler") {
            MachineSettingChange.SteamHeating(!(service?.snapshot?.settings?.steamHeating ?: false))
        }.let { (value, pressure, target, toggle) -> steamBoilerTemperature=value as TextView; steamBoilerPressure=pressure as TextView;
            steamBoilerTarget=target as TextView; steamBoilerToggle=toggle as Button }
        quickRow = if (spaciousHome) productStyle.column("product/home/quick-extraction")
            else productStyle.row("product/home/quick-extraction")
        quickRow.gravity = android.view.Gravity.CENTER_VERTICAL
        content.addView(quickRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        if (spaciousHome) quickRow.addView(productStyle.text(getString(R.string.home_quick_curves),
            TaskProductVisualStyle.section, "product/home/quick-title"))
        val launchRow = if (spaciousHome) productStyle.row().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
        } else quickRow
        quickRecipeTitle = productStyle.text(getString(R.string.home_curve_missing), TaskProductVisualStyle.action,
            "product/home/quick-recipe").also { launchRow.addView(it, LinearLayout.LayoutParams(0, -2, 1f)) }
        quickRecipeTitle.maxLines = 1
        quickRecipeTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        quickRecipeTitle.setOnClickListener {
            AlertDialog.Builder(host).setTitle(getString(R.string.home_current_curve))
                .setMessage(selectionDetails).setPositiveButton(android.R.string.ok, null).let(::showTransient)
        }
        quickLaunch = button(launchRow, getString(R.string.home_curve_select), primary = true) {
            val library = (application as MobileApplication).curves
            val item = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
            if (service?.let { ShotGate.active(it.shotState) || it.manualShotActive } == true) host.showExtraction()
            else if (item != null && library.canStart(item)) host.showExtraction(confirmStart = true)
            else host.select(TaskDestination.CURVES)
        }.apply { tag = "product/home/quick-launch"; layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) } }
        val presetRow = productStyle.row("product/home/direct-presets")
        quickRow.addView(presetRow, if (spaciousHome) LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        } else LinearLayout.LayoutParams(0, -2, 3f).apply { marginStart = dp(8) })
        directPresetButtons = (1..5).map { slot ->
            button(presetRow, slot.toString()) { host.showExtraction(slot, confirmStart = true) }.apply {
                tag = "product/home/direct-preset/$slot"
                if (spaciousHome) productStyle.button(this, outline = true)
                minimumHeight = dp(if (spaciousHome) 80 else 48); maxLines = Int.MAX_VALUE
                ellipsize = null
                textSize = if (spaciousHome) 14f else 11f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply {
                    if (slot > 1) marginStart = dp(if (spaciousHome) 8 else 4)
                }
            }
        }
        if (spaciousHome) quickRow.addView(launchRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
        })
        val machineChip = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        val scaleChip = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        coffeeDot = statusDot(machineChip)
        coffee = text(machineChip, getString(R.string.home_coffee_initial), 14, true)
        scaleDot = statusDot(scaleChip)
        scale = text(scaleChip, getString(R.string.home_scale_initial), 14, true)
        scanButton = button(deviceCard, getString(R.string.home_scan)) { enableAndScan() }
        candidates = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        deviceCard.addView(candidates)
        firmwareStatus = text(deviceCard, getString(R.string.home_firmware_initial), 14)
        alarmStatus = text(deviceCard, getString(R.string.home_alarm_initial), 14)
        leverStatus = text(deviceCard, getString(R.string.settings_lever_missing), 14)
        sleepStatus = text(deviceCard, getString(R.string.home_sleep_initial), 14)
        leverButton = button(deviceCard, getString(R.string.home_lever_button)) { chooseLeverMode() }
        sleepButton = button(deviceCard, getString(R.string.home_sleep_button)) { confirmSleepNow() }
        button(deviceCard, getString(R.string.home_settings_button)) { startActivity(Intent(host, MachineSettingsActivity::class.java)) }
        coffeeDisconnect = button(deviceCard, getString(R.string.home_coffee_disconnect)) { service?.disconnect(DeviceRole.COFFEE) }
        scaleWeight = text(deviceCard, "— g", 27, true)
        tareStatus = text(deviceCard, getString(R.string.home_tare_initial), 14)
        tareButton = button(deviceCard, getString(R.string.home_tare_button)) { service?.tareScale()?.let(::toast); render() }
        scaleDisconnect = button(deviceCard, getString(R.string.home_scale_disconnect)) { service?.disconnect(DeviceRole.BOOKOO) }
        button(deviceCard, getString(R.string.home_shutdown)) {
            val owner = service
            owner?.shutdown()
            if (owner?.running == true) toast(getString(R.string.home_shutdown_busy)) else host.releaseServiceBinding()
            render()
        }

        val metrics = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        text(deviceCard, getString(R.string.task_brew_machine_readings), 16, true)
        deviceCard.addView(metrics, LinearLayout.LayoutParams(-1, -2))
        var metricRow: LinearLayout? = null
        var metricIndex = 0
        val metricColumns = if (HoyiUi.wide(host)) 4 else 2
        fun metric(label: String): TextView {
            if (metricIndex++ % metricColumns == 0) {
                metricRow = LinearLayout(host).apply { orientation = LinearLayout.HORIZONTAL }
                metrics.addView(metricRow, LinearLayout.LayoutParams(-1, -2))
            }
            val box = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(6), dp(4), dp(6))
            }
            metricRow!!.addView(box, LinearLayout.LayoutParams(0, -2, 1f))
            HoyiUi.label(host, box, label, 12, muted = true)
            return HoyiUi.label(host, box, "—", 16, true)
        }
        brewTemperature = metric(getString(R.string.home_brew_temperature))
        brewPressure = metric(getString(R.string.home_brew_pressure))
        steamTemperature = metric(getString(R.string.home_steam_temperature))
        steamPressure = metric(getString(R.string.home_steam_pressure))

        val workspace = TaskWorkspaceLayout(host, boundedSecondaryViewport = true)
        devicePanel.addView(workspace, LinearLayout.LayoutParams(-1, dp(360)).apply { topMargin = dp(8) })
        val primary = workspace.primaryBody.apply { tag = "product/home/main" }
        workspace.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = productStyle.border; strokeWidth = resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                // The actual 24dp split-pane gap is untouched; only its centre is drawn.
                val secondaryHost = workspace.getChildAt(1)
                if (workspace.orientation == LinearLayout.HORIZONTAL) {
                    val x = (primary.right + secondaryHost.left) / 2f
                    canvas.drawLine(x, primary.top.toFloat(), x, primary.bottom.toFloat(), paint)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        val right = workspace.secondaryBody
        workspace.fillSecondaryViewport = true
        val secondary = productStyle.column("product/home/cup/body")
        right.addView(ScrollView(host).apply {
            tag = "product/home/cup/body-scroll"; isFillViewport = false; addView(secondary)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        val recipeIdentity = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        primary.addView(recipeIdentity, LinearLayout.LayoutParams(-1, -2))
        val recipeWords = object : LinearLayout(host) {
            init { isBaselineAligned = true; gravity = android.view.Gravity.BOTTOM }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val needed = selection.paint.measureText(selection.text.toString()) +
                    recipeDescription.paint.measureText(recipeDescription.text.toString()) + dp(16)
                val stacked = View.MeasureSpec.getMode(widthMeasureSpec) != View.MeasureSpec.UNSPECIFIED &&
                    needed > View.MeasureSpec.getSize(widthMeasureSpec)
                orientation = if (stacked) VERTICAL else HORIZONTAL
                (selection.layoutParams as LayoutParams).width = if (stacked) -1 else -2
                (recipeDescription.layoutParams as LayoutParams).apply { width = if (stacked) -1 else -2; marginStart = if (stacked) 0 else dp(16) }
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        recipeIdentity.addView(recipeWords, LinearLayout.LayoutParams(0, -2, 1f))
        selection = text(recipeWords, getString(R.string.home_curve_missing), 24, true).apply {
            productStyle.apply(this, TaskProductVisualStyle.title)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
            minimumHeight = dp(48)
            setOnClickListener {
                AlertDialog.Builder(host).setTitle(getString(R.string.home_current_curve))
                    .setMessage(selectionDetails).setPositiveButton(android.R.string.ok, null).let(::showTransient)
            }
        }
        recipeDescription = productStyle.text(getString(R.string.home_visual_unknown_mode), TaskProductVisualStyle.auxiliary,
            "product/home/recipe-description", true)
        recipeWords.addView(recipeDescription)
        browseCurvesButton = workbenchLink(recipeIdentity, getString(R.string.home_curve_browse)) {
            host.select(TaskDestination.CURVES)
        }.apply { layoutParams = LinearLayout.LayoutParams(dp(144), -2).apply { marginStart = dp(16) } }
        recipeMetrics = TaskProductRecipeMetrics(host, productStyle).apply { visibility = View.GONE; tag = "home-recipe-metrics" }
        primary.addView(recipeMetrics, LinearLayout.LayoutParams(-1, -2))
        val summaryRule = View(host).apply { setBackgroundColor(productStyle.border); tag = "product/home/summary-divider" }
        primary.addView(summaryRule, LinearLayout.LayoutParams(-1, dp(1)))
        val stageHeading = productStyle.text(getString(R.string.curve_library_stage_heading), TaskProductVisualStyle.section, "product/home/stage-title")
        productStyle.add(primary, stageHeading, 8)
        stagePreview = CurveStageView(host).apply {
            minimumHeight = dp(160); tag = "stage-preview"; pressureColor = true; productVisualStyle = this@HomeFragment.productStyle
        }
        primary.addView(stagePreview, LinearLayout.LayoutParams(-1, 0, 1f))
        previewUnavailable = text(primary, "", 14).apply { visibility = View.GONE }
        previewNote = text(primary, getString(R.string.task_brew_preview_note), 14)
        val fallbackBody = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL }
        stageFallbackText = text(fallbackBody, "", 14)
        stageFallback = ScrollView(host).apply {
            tag = "stage-fallback"; isFillViewport = false; visibility = View.GONE
            addView(fallbackBody)
        }
        primary.addView(stageFallback, LinearLayout.LayoutParams(-1, 0, 1f))
        recentCurves = HomeRecentCurves(host, primary, secondary,
            active = { service?.let { ShotGate.active(it.shotState) || it.manualShotActive } == true }) { item ->
            getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", item.id).apply()
            render()
        }
        recentCurves.refresh()
        workspace.onPrimarySpaceAvailable = { width, height ->
            val lines = if (height < dp(240)) 1 else 2
            if (selection.maxLines != lines) selection.maxLines = lines
            val exactWidth = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
            val naturalHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            // Measure actual localized text, including font scale and padding. Do not infer its height.
            val fixed = listOf(recipeIdentity, stageHeading, previewNote) +
                (if (hasRecipeMetrics) listOf(recipeMetrics, summaryRule) else emptyList()) +
                (if (stagePreview.targets.isEmpty()) listOf(previewUnavailable) else emptyList())
            val fixedHeight = fixed.sumOf { child ->
                val params = child.layoutParams as LinearLayout.LayoutParams
                child.measure(exactWidth, if (params.height > 0) View.MeasureSpec.makeMeasureSpec(params.height, View.MeasureSpec.EXACTLY) else naturalHeight)
                child.measuredHeight + params.topMargin + params.bottomMargin
            }
            val required = fixedHeight + if (stagePreview.targets.isEmpty()) 0 else dp(160)
            val recentHeight = recentCurves.fit(width, height - required)
            textStagePreview = height < required
            updateStagePreviewVisibility()
            summaryRule.visibility = if (hasRecipeMetrics && !textStagePreview) View.VISIBLE else View.GONE
            stageHeading.visibility = if (textStagePreview) View.GONE else View.VISIBLE
            // This budget already excludes the supporting viewport; only stage details scroll here.
            if (textStagePreview) height else required + recentHeight
        }
        val cupTitle = productStyle.row().apply { minimumHeight = dp(42); gravity = android.view.Gravity.CENTER_VERTICAL }
        productStyle.add(cupTitle, productStyle.text(getString(R.string.task_brew_cup_summary), TaskProductVisualStyle.section))
        productStyle.add(secondary, cupTitle)
        productStyle.divider(secondary, 2, 7)
        productStyle.add(secondary, productStyle.text(getString(R.string.home_visual_bean_label), TaskProductVisualStyle.auxiliary, secondary = true))
        val beanRow = productStyle.row().apply { minimumHeight = dp(48); gravity = android.view.Gravity.CENTER_VERTICAL }
        beanSummary = productStyle.text("", TaskProductVisualStyle.title).apply {
            minimumHeight = dp(48)
            setOnClickListener { startActivity(Intent(host, BeanPreparationActivity::class.java)) }
        }
        beanRow.addView(beanSummary, LinearLayout.LayoutParams(0, -2, 1f))
        productStyle.action(beanRow, getString(R.string.home_visual_change), "product/home/change-bean", top = 0) {
            startActivity(Intent(host, BeanPreparationActivity::class.java))
        }.layoutParams = LinearLayout.LayoutParams(dp(64), -2).apply { marginStart = dp(8) }
        productStyle.add(secondary, beanRow)
        productStyle.divider(secondary, 8, 7)
        val doseGroup = productStyle.column()
        productStyle.add(secondary, doseGroup)
        doseStateSummary = text(doseGroup, "", TaskVisualTokens.SUPPORTING_SP)
        val doseRow = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        val doseValues = productStyle.row("product/home/dose").apply { isBaselineAligned = true; gravity = android.view.Gravity.BOTTOM }
        doseRow.addView(doseValues, LinearLayout.LayoutParams(0, -2, 1f))
        doseQuantity = text(doseValues, "", 40, true).apply {
            tag = "home-dose-quantity"; fontFeatureSettings = "tnum"; visibility = View.GONE
        }
        doseUnit = productStyle.text("g", TaskProductVisualStyle.auxiliary, "product/home/dose/unit", true).apply { visibility = View.GONE }
        doseValues.addView(doseUnit, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        productStyle.action(doseRow, getString(R.string.home_visual_edit), "product/home/edit-dose", top = 0) {
            startActivity(Intent(host, BeanPreparationActivity::class.java))
        }.layoutParams = LinearLayout.LayoutParams(dp(64), -2).apply { marginStart = dp(24) }
        productStyle.add(doseGroup, doseRow, 8)
        productStyle.divider(secondary, 8, 7)
        coffee.tag = "product/home/state-machine"; scale.tag = "product/home/state-scale"
        productStyle.apply(coffee, TaskProductVisualStyle.body); productStyle.apply(scale, TaskProductVisualStyle.body)
        secondary.addView(machineChip, LinearLayout.LayoutParams(-1, -2))
        productStyle.divider(secondary, 8, 7)
        secondary.addView(scaleChip, LinearLayout.LayoutParams(-1, -2))
        productStyle.divider(secondary, 8, 7)
        val preparationDetails = productStyle.column("product/home/details-group")
        productStyle.add(devicePanel, preparationDetails, 16)
        productStyle.add(preparationDetails, productStyle.text(getString(R.string.home_visual_details), TaskProductVisualStyle.section))
        temperatureSummary = text(preparationDetails, "", 14)
        usageSummary = text(preparationDetails, "", 14).apply {
            minimumHeight = dp(48)
            setOnClickListener { host.select(TaskDestination.HISTORY) }
        }
        presetPanel = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), 0, dp(16), 0) }
        presetButtons = (1..5).map { slot ->
            button(presetPanel, getString(R.string.home_slot, slot.toString())) {
                presetDialog?.dismiss()
                host.showExtraction(slot, confirmStart = true)
            }
        }
        quickPresets = workbenchLink(preparationDetails, getString(R.string.home_quick_curves)) { showQuickPresets() }
        workbenchLink(preparationDetails, getString(R.string.dose_title)) { startActivity(Intent(host, BeanPreparationActivity::class.java)) }
        workbenchLink(preparationDetails, getString(R.string.scale_title)) { host.select(TaskDestination.SCALE) }
        val actionDock = TaskActionDock(host, edgeInsetDp = 0, fullWidthContent = true).apply { tag = "product/home/dock" }
        right.addView(actionDock, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val confirmation = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
        confirmation.addView(HomeVisualIcon(host, "info", productStyle.muted), LinearLayout.LayoutParams(dp(24), dp(24)))
        confirmation.addView(productStyle.text(getString(R.string.home_quick_curves_hint),
            TaskProductVisualStyle.auxiliary, "product/home/confirmation", true), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        productStyle.add(actionDock, confirmation)
        brewButton = button(actionDock, getString(R.string.home_curve_select), primary = true) {
            val library = (application as MobileApplication).curves
            val item = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
            val active = service?.let { ShotGate.active(it.shotState) || it.manualShotActive } == true
            if (active || (item != null && library.canStart(item))) host.showExtraction()
            else host.select(TaskDestination.CURVES)
        }
        HomeVisualIcon.buttonIcon(brewButton, "chevron")
        productStyle.action(actionDock, getString(R.string.home_visual_show_details), "product/home/details") { showDevicePanel() }
        render()
        return root
    }
    private fun showQuickPresets() {
        if (presetDialog?.isShowing == true) return
        (presetPanel.parent as? android.view.ViewGroup)?.removeView(presetPanel)
        presetDialog = AlertDialog.Builder(host).setTitle(getString(R.string.home_quick_curves))
            .setMessage(getString(R.string.home_quick_curves_hint))
            .setView(ScrollView(host).apply { addView(presetPanel) })
            .setNegativeButton(android.R.string.cancel, null).create().also { dialog ->
                dialog.setOnDismissListener {
                    (presetPanel.parent as? android.view.ViewGroup)?.removeView(presetPanel)
                    presetDialog = null
                }
                AppDialog.show(dialog)
            }
    }
    private fun showDevicePanel() {
        if (deviceDialog?.isShowing == true) return
        (devicePanel.parent as? android.view.ViewGroup)?.removeView(devicePanel)
        val scroll = ScrollView(host).apply { addView(devicePanel) }
        deviceDialog = AlertDialog.Builder(host).setTitle(getString(R.string.home_visual_panel_title))
            .setView(scroll).setPositiveButton(android.R.string.ok, null).create().also { dialog ->
                dialog.setOnDismissListener { (devicePanel.parent as? android.view.ViewGroup)?.removeView(devicePanel); deviceDialog = null }
                AppDialog.show(dialog)
            }
        render()
    }
    override fun onResume() {
        super.onResume()
        feedbackUi.start()
        recentCurves.refresh()
        visible = true
        host.observeService(serviceObserver)
        handler.post(refresh)
    }
    override fun onPause() {
        feedbackUi.leave(host.isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        transientDialogs.forEach { it.dismiss() }; transientDialogs.clear()
        deviceDialog?.dismiss()
        presetDialog?.dismiss()
        recentCurves.dismiss()
        host.removeServiceObserver(serviceObserver); service=null;super.onPause()
    }
    private fun missingBle(): List<String> {
        val required = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        return required.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    }
    private fun enableAndScan() {
        val missing = missingBle()
        if (missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), PERMISSIONS); return }
        if (!BuildConfig.MOCK_MODE) {
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) { toast(getString(R.string.home_bluetooth_unsupported)); return }
            try { if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE); return } }
            catch (_: SecurityException) { toast(getString(R.string.home_permission_expired)); return }
        }
        if (!host.ensureServiceForScan()) toast(getString(R.string.home_bind_failed))
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSIONS) {
            if (missingBle().isEmpty()) enableAndScan() else toast(getString(R.string.home_permission_required))
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE && resultCode == RESULT_OK) enableAndScan()
        if (requestCode == EXPORT && resultCode == RESULT_OK) data?.data?.let { (application as MobileApplication).export(it) }
    }
    private fun choose(device: DiscoveredDevice) {
        if (BuildConfig.MOCK_MODE) {
            if (device.candidateRole == DeviceRole.BOOKOO) device.scaleProtocolId?.let { service?.connectScale(device.address, it) }
            else service?.connectCoffee(device.address, "000000")
            render()
            return
        }
        if (device.candidateRole == DeviceRole.BOOKOO) { device.scaleProtocolId?.let { service?.connectScale(device.address, it) }; return }
        if (service?.connectRememberedCoffee(device.address) == true) return
        val input = EditText(host).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(6)); hint = getString(R.string.home_password_hint)
            isSaveEnabled = false; importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        val dialog = AlertDialog.Builder(host).setTitle(getString(R.string.home_connect_coffee_title))
            .setMessage(getString(R.string.home_password_message))
            .setView(input).setPositiveButton(getString(R.string.home_connect), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val password = input.text.toString()
                if (!password.matches(Regex("[0-9]{6}"))) input.error = getString(R.string.home_password_error)
                else { service?.connectCoffee(device.address, password); input.text.clear(); dialog.dismiss() }
            }
        }
        dialog.setOnDismissListener { input.text.clear() }
        AppDialog.show(dialog)
        transientDialogs += dialog
    }
    private fun chooseLeverMode() {
        if (BuildConfig.MOCK_MODE) { toast(getString(R.string.home_lever_mock)); return }
        val modes = listOf(
            MachineSettingChange.LeverMode(false, false),
            MachineSettingChange.LeverMode(true, false),
            MachineSettingChange.LeverMode(true, true),
        )
        AlertDialog.Builder(host).setTitle(getString(R.string.home_lever_choose_title))
            .setItems(arrayOf(getString(R.string.home_lever_manual),
                getString(R.string.home_lever_pressure), getString(R.string.home_lever_flow))) { _, index ->
                val change = modes[index]
                AlertDialog.Builder(host).setTitle(getString(R.string.home_lever_confirm_title))
                    .setMessage(getString(R.string.home_lever_confirmation_message, settingsPresentation.change(change)))
                    .setPositiveButton(getString(R.string.machine_settings_send)) { _, _ -> service?.changeMachineSetting(change)?.let(::toast); render() }
                    .setNegativeButton(getString(R.string.machine_settings_cancel), null).let(::showTransient)
            }.let(::showTransient)
    }
    private fun confirmSleepNow() {
        if (BuildConfig.MOCK_MODE) { toast(getString(R.string.home_sleep_mock)); return }
        AlertDialog.Builder(host).setTitle(getString(R.string.home_sleep_confirm_title))
            .setMessage(getString(R.string.home_sleep_confirm_message))
            .setPositiveButton(getString(R.string.home_sleep_send)) { _, _ -> service?.enterSleepNow()?.let(::toast); render() }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).let(::showTransient)
    }
    private fun confirmBoilerChange(change: MachineSettingChange) {
        if (!MachineSettingsEditGate.allowed(service)) return
        val name = settingsPresentation.change(change)
        val title = getString(if (BuildConfig.MOCK_MODE) R.string.machine_settings_mock_confirm_title
            else R.string.machine_settings_confirm_title)
        val message = if (BuildConfig.MOCK_MODE)
            getString(R.string.machine_settings_mock_confirm_message, name)
        else name + "\n" + getString(R.string.machine_settings_readback_warning)
        transientDialogs += AppDialog.confirm(host, title, message, AppDialog.Action(getString(if (BuildConfig.MOCK_MODE)
            R.string.machine_settings_done else R.string.machine_settings_send)) {
            if (!BuildConfig.MOCK_MODE) service?.changeMachineSetting(change)?.let(::toast)
            render()
        }, getString(R.string.machine_settings_cancel))
    }
    override fun onSaveInstanceState(outState: Bundle) {
        feedbackUi.save(outState)
        super.onSaveInstanceState(outState)
    }
    private fun render() {
        if (!::status.isInitialized) return
        feedbackUi.update(service, visible)
        val owner = service
        val s = owner?.snapshot ?: MobileSnapshot()
        val now = SystemClock.elapsedRealtime()
        val running = owner?.running == true
        val warning = ShotSafetyAlert.resource(owner?.shotState ?: ExtractionState.IDLE, s.coffeeState)?.let { getString(it) }
            ?: owner?.manualSafetyMessage ?: owner?.machineWriteSafetyMessage
        safetyWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
        safetyWarning.show(warning.orEmpty())
        acknowledgeManual.visibility = if (owner?.manualSafetyMessage == null &&
            owner?.machineWriteAcknowledgementAvailable != true) View.GONE else View.VISIBLE
        acknowledgeManual.isEnabled = if (owner?.manualSafetyMessage != null)
            owner.shotRecoveryClearBlock == null else owner?.machineWriteAcknowledgementAvailable == true
        val alarmBanner = machineAlarms.banner(s.alarmBits, s.alarmAt, now)
        prominentAlarm.visibility = if (alarmBanner == null) View.GONE else View.VISIBLE
        prominentAlarm.show(alarmBanner?.message.orEmpty())
        prominentAlarm.setTextColor(getColor(if (alarmBanner?.blocking == true)
            R.color.mobile_danger else R.color.mobile_accent))
        status.show(getString(R.string.home_machine_title) + " · " + if (owner?.manualShotActive == true)
            getString(R.string.home_manual_shot_status) else label(s.coffeeState) +
            if (LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now) != null)
                getString(R.string.home_live_suffix) else "")
        val bothReady = s.coffeeState == DeviceState.READY && s.scaleState == DeviceState.READY
        status.visibility = View.VISIBLE
        machineConnect.show(getString(if (s.coffeeState == DeviceState.READY) R.string.home_visual_change else R.string.home_connect))
        scanButton.visibility = if (bothReady && !s.scanning) View.GONE else View.VISIBLE
        scanButton.isEnabled = !s.scanning
        val shotActive = owner?.shotState?.let(ShotGate::active) == true
        val stopAction = StopActionPresentation.describe(owner?.shotState ?: ExtractionState.IDLE,
            s.coffeeState, running)
        emergencyStop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        emergencyStop.isEnabled = stopAction.enabled
        emergencyStop.show(if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource))
        val settingBusy = owner?.settingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)
        val preparationIdle = owner?.brewPreparationState == BrewPreparation.State.IDLE
        val freshAwakeIdle = s.coffee is IdleTelemetry && s.coffee.sleepStateRaw == 0 &&
            s.coffeeAt?.let { now >= it && now - it <= 1500 } == true
        leverButton.isEnabled = running && !shotActive && owner?.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null && !settingBusy &&
            owner?.sleepNowState != SleepNowTracker.State.UNKNOWN && preparationIdle &&
            s.coffeeState == DeviceState.READY && owner?.machineSettingsFresh == true && freshAwakeIdle
        leverStatus.show(settingsPresentation.leverMode(s.settings) +
            when (owner?.pendingSetting) {
                is MachineSettingChange.LeverMode -> " · " + when (owner?.settingWriteState) {
                    SettingsWriteTracker.State.WRITING -> getString(R.string.home_setting_writing)
                    SettingsWriteTracker.State.WAITING_READBACK -> getString(R.string.home_setting_waiting)
                    SettingsWriteTracker.State.CONFIRMED -> getString(R.string.home_setting_confirmed)
                    SettingsWriteTracker.State.FAILED -> getString(R.string.home_setting_failed)
                    SettingsWriteTracker.State.UNKNOWN -> getString(R.string.home_setting_unknown)
                    SettingsWriteTracker.State.RECONCILED -> getString(R.string.home_setting_reconciled)
                    SettingsWriteTracker.State.IDLE, null -> ""
                }
                else -> ""
            })
        firmwareStatus.show(firmwarePresentation.describe(owner?.coffeeFirmware,s.coffeeState,BuildConfig.MOCK_MODE))
        coffeeDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.coffeeState != DeviceState.DISCONNECTED
        scaleDisconnect.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState != DeviceState.DISCONNECTED
        tareButton.isEnabled = running && !shotActive && owner?.manualShotActive != true && s.scaleState == DeviceState.READY &&
            owner?.tareState !in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO)
        tareStatus.show(getString(R.string.home_tare_prefix) + when (owner?.tareState ?: StandaloneTare.State.IDLE) {
            StandaloneTare.State.IDLE -> getString(R.string.home_tare_idle)
            StandaloneTare.State.WRITING -> getString(R.string.home_tare_writing)
            StandaloneTare.State.WAITING_ZERO -> getString(R.string.home_tare_waiting)
            StandaloneTare.State.CONFIRMED -> getString(R.string.home_tare_confirmed)
            StandaloneTare.State.FAILED -> getString(R.string.home_setting_failed)
            StandaloneTare.State.UNKNOWN -> getString(R.string.home_tare_unknown)
        })
        val liveMachine = LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now)
        val coffeeFresh = liveMachine != null
        val idle = liveMachine as? IdleTelemetry
        val sleepBusy = owner?.sleepNowState in setOf(SleepNowTracker.State.WRITING,
            SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
        sleepButton.isEnabled = running && !shotActive && owner?.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null && !settingBusy && !sleepBusy && preparationIdle && coffeeFresh &&
            idle?.sleepStateRaw == 0
        val reportedSleep = if (!coffeeFresh) getString(R.string.home_sleep_stale) else when (idle?.sleepStateRaw) {
            0 -> getString(R.string.home_sleep_awake)
            1 -> getString(R.string.home_sleep_asleep)
            else -> getString(R.string.settings_unknown)
        }
        val sleepProgress = when (owner?.sleepNowState) {
            SleepNowTracker.State.WRITING -> getString(R.string.home_sleep_writing_suffix)
            SleepNowTracker.State.WAITING_ASLEEP -> getString(R.string.home_sleep_waiting_suffix)
            SleepNowTracker.State.CONFIRMED -> if (idle?.sleepStateRaw == 1) getString(R.string.home_sleep_confirmed_suffix) else ""
            SleepNowTracker.State.FAILED -> getString(R.string.home_sleep_failed_suffix)
            SleepNowTracker.State.UNKNOWN -> getString(R.string.home_sleep_unknown_suffix)
            SleepNowTracker.State.RECONCILED -> getString(R.string.home_sleep_reconciled_suffix)
            else -> ""
        }
        sleepStatus.show(getString(R.string.home_sleep_summary, reportedSleep, sleepProgress))
        alarmStatus.show(machineAlarms.describe(s.alarmBits, s.alarmAt, now))
        val compactStatus = resources.configuration.screenWidthDp < 400
        coffee.show(if (compactStatus) getString(R.string.home_coffee_compact, if (coffeeFresh) getString(R.string.home_live) else label(s.coffeeState))
            else getString(R.string.home_coffee_status, label(s.coffeeState), if (coffeeFresh) getString(R.string.home_live_suffix) else ""))
        coffeeDot.showDot(when (s.coffeeState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        val readings = MachineHomeReadings.from(liveMachine)
        brewTemperature.show(readings.brewTemperature)
        brewBoilerTemperature.showMetric(readings.brewTemperature)
        brewPressure.show(readings.brewPressure)
        brewBoilerPressure.showMetric(readings.brewPressure)
        steamTemperature.show(readings.steamTemperature)
        steamBoilerTemperature.showMetric(readings.steamTemperature)
        steamPressure.show(readings.steamPressure)
        steamBoilerPressure.showMetric(readings.steamPressure)
        brewBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.brewTemperatureC?.let { "${it} °C" } ?: "—"))
        steamBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.steamTemperatureC?.let { "${it} °C" } ?: "—"))
        val boilerEditable = MachineSettingsEditGate.allowed(owner, s, now) && !BuildConfig.MOCK_MODE
        brewBoilerToggle.isEnabled = boilerEditable
        steamBoilerToggle.isEnabled = boilerEditable
        brewBoilerToggle.show(getString(if (s.settings?.brewHeating == true) R.string.setting_off else R.string.setting_on))
        steamBoilerToggle.show(getString(if (s.settings?.steamHeating == true) R.string.setting_off else R.string.setting_on))
        val writeState = owner?.settingWriteState
        if (writeState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN)) {
            val message = getString(when (writeState) {
                SettingsWriteTracker.State.WRITING -> R.string.machine_settings_setting_writing
                SettingsWriteTracker.State.WAITING_READBACK -> R.string.machine_settings_setting_waiting
                else -> R.string.machine_settings_setting_unknown
            })
            brewBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.brewTemperatureC?.let { "${it} °C" } ?: "—") + " · " + message)
            steamBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.steamTemperatureC?.let { "${it} °C" } ?: "—") + " · " + message)
        }
        val liveScale = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.hundredthsGram
            ?: LiveTelemetry.scale(s.weight, s.scaleState, s.weightAt, now)?.weightHundredthsGram
        scale.show(if (compactStatus) getString(R.string.home_scale_compact, if (liveScale != null) getString(R.string.home_live) else label(s.scaleState))
            else getString(R.string.home_scale_status, label(s.scaleState), if (liveScale != null) getString(R.string.home_live_suffix) else ""))
        scaleDot.showDot(when (s.scaleState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        val liveDeviceFlow = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.let {
            it.deviceFlowHundredths.takeIf { _ -> it.capabilities.deviceFlow }
        } ?: LiveTelemetry.scale(s.weight, s.scaleState, s.weightAt, now)?.deviceFlowHundredths
        scaleWeight.show("${liveScale?.let(::number) ?: "—"} g" +
            "  ·  ${liveDeviceFlow?.let(::number) ?: "—"} g/s")
        val app = application as MobileApplication
        val library = app.curves
        val selected = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
        val cupActive = shotActive || owner?.manualShotActive == true
        quickRow.visibility = if (cupActive) View.GONE else View.VISIBLE
        recentCurves.updateAvailability()
        val displayed = if (cupActive) owner?.chartCurveId?.let(library::find) else selected
        val title = displayed?.name ?: getString(if (cupActive && owner?.chartCurveId == "manual") R.string.extraction_manual_label
            else if (cupActive) R.string.task_brew_curve_unknown else R.string.home_curve_missing)
        selection.show(title)
        quickRecipeTitle.show(title)
        recipeDescription.show(displayed?.let { HomeRecipePresentation.from(it).description(host) }
            ?: getString(R.string.home_visual_unknown_mode))
        displayed?.let(recipeMetrics::present)
        hasRecipeMetrics = displayed != null
        selectionDetails = title + "\n\n" + (displayed?.let { getString(if (library.canStart(it))
            R.string.home_curve_startable else R.string.home_curve_readonly) + "\n" + it.details } ?: "") + "\n\n" + getString(R.string.task_brew_preview_note)
        val targets = displayed?.let(CurveStageParameters::pressureTargets).orEmpty()
        if (stagePreview.targets != targets) stagePreview.targets = targets
        val previewDescription = getString(R.string.task_brew_preview_note) + " " +
            targets.mapIndexed { index, target -> getString(R.string.chart_stage, (index + 1).toString()) +
                " " + String.format(Locale.ROOT, "%.1f bar", target / 10.0) }.joinToString(", ")
        if (stagePreview.contentDescription?.toString() != previewDescription)
            stagePreview.contentDescription = previewDescription
        previewUnavailable.show(getString(R.string.task_brew_no_pressure_preview))
        stageFallbackText.show(getString(R.string.task_brew_compact_preview) + "\n" +
            getString(R.string.task_brew_preview_note) + "\n" +
            (if (hasRecipeMetrics) recipeMetrics.contentDescription.toString() + "\n" else "") +
            if (targets.isEmpty()) getString(R.string.task_brew_no_pressure_preview) else
                targets.mapIndexed { index, target -> getString(R.string.chart_stage, (index + 1).toString()) +
                    " · " + String.format(Locale.ROOT, "%.1f bar", target / 10.0) }.joinToString("\n"))
        updateStagePreviewVisibility()
        brewButton.show(when {
            cupActive -> getString(R.string.task_brew_live_state)
            selected == null -> getString(R.string.home_curve_select)
            !library.canStart(selected) -> getString(R.string.home_curve_replace)
            else -> getString(R.string.home_curve_prepare)
        })
        quickLaunch.show(if (!cupActive && selected != null && library.canStart(selected))
            getString(R.string.extraction_start) else brewButton.text.toString())
        browseCurvesButton.visibility = View.VISIBLE
        val profile = displayed?.let { library.resolve(it.id, s.scaleState == DeviceState.READY) }
        temperatureSummary.show(getString(R.string.task_brew_temperature,
            owner?.currentCorrectedBrewTemperature()?.let(::number) ?: "—", profile?.temperatureC?.toString() ?: "—"))
        val doseResult = app.beanPreparationResult
        val dose = doseResult.getOrNull()?.current()
        val matches = dose?.let { value -> app.beanInventoryResult.getOrNull()?.events()?.any {
            it.eventId == value.eventId && it.batchId == value.batchId &&
                it.kind == io.openhoyi.bean.InventoryEventKind.CONSUME && it.deltaMg == -value.amountMg
        } }
        val doseState = when {
            doseResult.isFailure -> R.string.task_brew_dose_unavailable
            dose == null -> R.string.task_brew_dose_missing
            dose.shotId != null && !dose.associated -> R.string.task_brew_dose_association
            dose.shotId != null -> R.string.task_brew_dose_previous
            dose.attempted && !dose.confirmed -> R.string.task_brew_dose_pending
            dose.confirmed && matches != true -> R.string.task_brew_dose_conflict
            dose.confirmed -> R.string.task_brew_dose_confirmed
            else -> R.string.task_brew_dose_selected
        }
        beanSummary.show(dose?.beanName ?: getString(R.string.task_brew_dose_entry))
        doseStateSummary.show(getString(doseState))
        doseQuantity.visibility = if (dose == null) View.GONE else View.VISIBLE
        doseUnit.visibility = doseQuantity.visibility
        if (dose != null) doseQuantity.show(BeanQuantity.formatGrams(dose.amountMg))
        val stats = displayed?.let { app.curveUsageResult.getOrNull()?.stats(it.id) }
        usageSummary.show(when {
            app.curveUsageResult.isFailure -> getString(R.string.task_brew_usage_unavailable)
            stats == null || (stats.count == 0L && stats.lastUsedAtMs == null) -> getString(R.string.task_brew_usage_empty)
            else -> getString(R.string.task_brew_usage, stats.count.toString(), stats.lastUsedAtMs?.let {
                java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT,
                    app.languagePreferences.current.locale).format(java.util.Date(it))
            } ?: "—")
        } + "\n" + getString(R.string.task_brew_history_entry))
        val presetPrefs = getSharedPreferences("presets", MODE_PRIVATE)
        presetButtons.forEachIndexed { index, button ->
            val slot = index + 1
            val id = PresetSlots.curveId(slot, presetPrefs.getString(PresetSlots.key(slot), null))
            val item = library.find(id)
            val label = getString(R.string.home_slot_curve, slot.toString(), item?.name ?: id)
            if (button.text.toString() != label) button.text = label
            directPresetButtons[index].apply {
                contentDescription = label
                isEnabled = item?.let(library::canStart) == true
                val shortLabel = getString(R.string.home_slot, slot.toString()) + "\n" +
                    (item?.name ?: getString(R.string.home_slot_unconfigured))
                if (text.toString() != shortLabel) text = shortLabel
            }
        }
        val keys = s.candidates.map { "${it.address}:${it.advertisedName}:${it.scaleProtocolId}" }
        if (keys != candidateKeys) {
            candidateKeys = keys; candidates.removeAllViews()
            s.candidates.forEach { device ->
                if (device.scaleProtocolId == io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id)
                    HoyiUi.label(host, candidates, getString(R.string.scale_read_only_live), 14, muted = true)
                button(candidates, getString(R.string.home_candidate, device.advertisedName, getString(if (device.candidateRole == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale))) { choose(device) }
            }
        }
    }
    private fun label(state: DeviceState): String = when (state) {
        DeviceState.READY -> getString(R.string.home_connected); DeviceState.DISCONNECTED -> getString(R.string.device_state_disconnected); DeviceState.FAILED -> getString(R.string.device_state_failed)
        DeviceState.UNSUPPORTED -> getString(R.string.home_firmware_readonly); else -> getString(R.string.device_state_connecting)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun TextView.showMetric(value: String) {
        if (text.toString() == value) return
        val unitStart = value.indexOf(' ')
        text = if (unitStart < 0) value else SpannableString(value).apply {
            setSpan(RelativeSizeSpan(0.48f), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    private fun number(hundredths: Int) = String.format(Locale.ROOT, "%.2f", hundredths / 100.0)
    private fun toast(message: String) = Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun dotShape(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(when (color) { R.color.mobile_success -> productStyle.accent; R.color.mobile_muted -> productStyle.muted; else -> getColor(color) })
    }
    private fun View.showDot(colorResource: Int) {
        val color = when (colorResource) { R.color.mobile_success -> productStyle.accent; R.color.mobile_muted -> productStyle.muted; else -> getColor(colorResource) }
        val dot = background as? GradientDrawable
        // Replacing an unchanged background emits native accessibility subtree events.
        if (dot == null) background = dotShape(colorResource)
        else if (dot.color?.defaultColor != color) dot.setColor(color)
    }
    private fun updateStagePreviewVisibility() {
        fun View.visibleWhen(show: Boolean) {
            val next = if (show) View.VISIBLE else View.GONE
            if (visibility != next) visibility = next
        }
        recipeMetrics.visibleWhen(hasRecipeMetrics && !textStagePreview)
        stagePreview.visibleWhen(!textStagePreview && stagePreview.targets.isNotEmpty())
        previewUnavailable.visibleWhen(!textStagePreview && stagePreview.targets.isEmpty())
        previewNote.visibleWhen(!textStagePreview)
        stageFallback.visibleWhen(textStagePreview)
    }

    private fun statusDot(parent: LinearLayout): View = View(host).apply {
        background = dotShape(R.color.mobile_muted)
        parent.addView(this, LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginEnd = dp(8) })
    }
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView =
        TextView(host).apply {
            text = value; textSize = size.toFloat(); setTextColor(getColor(R.color.mobile_text))
            includeFontPadding = false
            productStyle.apply(this, when {
                size >= 40 -> TaskProductVisualStyle.largeValue
                size >= 24 -> TaskProductVisualStyle.title
                size >= 20 -> TaskProductVisualStyle.section
                size <= 14 -> TaskProductVisualStyle.auxiliary
                bold -> TaskProductVisualStyle.action
                else -> TaskProductVisualStyle.body
            }, secondary = size <= 14)
            setPadding(0, 0, 0, 0)
            parent.addView(this)
        }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(host, parent, title)
    private fun button(parent: LinearLayout, value: String, primary: Boolean = false, action: () -> Unit) =
        productStyle.button(HoyiUi.button(host, parent, value, primary = primary, action = action), primary)
    private fun workbenchLink(parent: LinearLayout, value: String, action: () -> Unit) =
        productStyle.button(HoyiUi.button(host, parent, value, action = action)).apply {
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
