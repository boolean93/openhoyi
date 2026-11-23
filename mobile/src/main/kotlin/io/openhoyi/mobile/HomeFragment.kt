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
    private val boilerExceptions = mutableMapOf<HoyiSwitch, Button>()
    private var renderingBoilerSwitch = false
    private val boilerDisplay = HomeBoilerDialHold()
    private val boilerDials = mutableListOf<BoilerTemperatureDial>()
    private lateinit var brewBoilerTemperature: TextView
    private lateinit var brewBoilerPressure: TextView
    private lateinit var steamBoilerTemperature: TextView
    private lateinit var steamBoilerPressure: TextView
    private lateinit var brewBoilerTarget: TextView
    private lateinit var steamBoilerTarget: TextView
    private lateinit var brewBoilerToggle: HoyiSwitch
    private lateinit var steamBoilerToggle: HoyiSwitch
    private var lastCurveReference: HomeLastCurve.Reference? = null
    private var lastCurveState: HomeLastCurve = HomeLastCurve.Empty
    private var lastCurveDirty = true
    private var previousCupActive = false
    private lateinit var quickRow: LinearLayout
    private var quickBottomSpacer: View? = null
    private lateinit var brewPressure: TextView
    private lateinit var steamTemperature: TextView
    private lateinit var steamPressure: TextView
    private lateinit var leverStatus: TextView
    private lateinit var leverButton: Button
    private lateinit var machineModes: MachineModeControls
    private var dialogMachineModes: MachineModeControls? = null
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
    private lateinit var directPresets: HomePresetGrid
    private var presetVisualsDirty = true
    private var customCurveRevision = -1L
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
    private val homeAccent get() = productStyle.homeAccent
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
        boilerDials.clear()
        boilerExceptions.clear()
        feedbackUi = BrewFeedbackDialog(host, savedInstanceState)
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(productStyle.homePage)
            host.installPageBackdrop(this, productStyle.homePage)
        }
        val phoneHome = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val compactType = HomePortraitBudget.compactTypography(
            resources.configuration.screenWidthDp, resources.configuration.screenHeightDp)
        val shortHome = TaskLayoutPolicy.compactHome(resources.configuration.screenHeightDp, phoneHome)
        val shortWideHome = shortHome && resources.configuration.screenWidthDp >= 600
        val anchorPhoneShortcuts = phoneHome && resources.configuration.screenWidthDp in 1 until 600
        // Redistribute the roomy landscape lead-in; keep the compact row budget
        // and portrait header unchanged. Portrait can scroll into its trailing inset.
        // Very short landscapes keep the existing one-dp inset so the entire
        // 48dp shortcut target remains reachable without compulsory scrolling.
        val shortcutBottomBreathingRoom = if (!shortHome) 8 else 0
        val content = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            val inset = if (shortHome) 1 else 4
            setPadding(dp(22), dp(inset), dp(22), dp(inset + shortcutBottomBreathingRoom))
            clipToPadding = false
            // A light card's native elevation can extend outside the final row,
            // but the ScrollView still clips to the actual app viewport.
            if (!phoneHome || !HoyiUi.dark(host)) clipChildren = false
        }
        val shortcutOutset = dp(if (phoneHome) 3 else if (shortHome) 0 else 6)
        root.addView(ScrollView(host).apply {
            tag = "product/home/page-scroll"
            isFillViewport = true
            addView(content, android.widget.FrameLayout.LayoutParams(-1, -2))
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        emergencyStop = Button(host).apply {
            text = getString(R.string.home_stop)
            isAllCaps = false
            textSize = 18f
            minHeight = dp(56)
            if (shortHome) {
                minimumHeight = dp(56)
                includeFontPadding = false
                setPadding(dp(16), dp(8), dp(16), dp(8))
            }
            setTextColor(getColor(android.R.color.white))
            background = HoyiUi.shape(host, R.color.mobile_stop_button, 12)
            visibility = View.GONE
            setOnClickListener { service?.stopShot(); render() }
        }
        root.addView(emergencyStop, LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dp(24), dp(4), dp(24), dp(if (shortHome) 4 else 12))
        })
        safetyWarning = text(content, "", 17, true).apply {
            setTextColor(getColor(R.color.mobile_danger)); visibility = View.GONE
        }
        acknowledgeManual = button(content, getString(R.string.home_recovery_button)) {
            val active=service
            if(active!=null && active.manualSafetyMessage==null && active.splitRecoveryChoices.isNotEmpty()) {
                showSplitRecovery(active)
                return@button
            }
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
                .apply {
                    if (recoveryKind == MachineWriteRecoveryState.Kind.SETTING &&
                        service?.canRefreshUnknownBoilerSettings == true)
                        setNeutralButton(getString(R.string.home_boiler_refresh)) { _, _ ->
                            service?.refreshUnknownBoilerSettings()?.let(::toast)
                            render()
                        }
                }
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
        status = text(content, getString(R.string.home_not_connected_initial), 17, true).apply { productStyle.apply(this, TaskProductVisualStyle.Type(if (compactType) 14 else 18,500,if (compactType) 20 else 26)); tag = "product/home/service-status" }
        content.removeView(status)
        val machineHeading = productStyle.row().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        machineModes = MachineModeControls(host, ::changeMachineMode).apply { preserveHomeBackdrop() }
        // Landscape mode controls live immediately above the shortcut cards.
        val machineRow = if (!phoneHome) machineHeading.apply {
            tag = "product/home/machine-status"
        } else MachineModeHeader(host, machineHeading, machineModes)
        val compactTopRow = if (shortWideHome) productStyle.row("product/home/compact-top").also {
            it.gravity = android.view.Gravity.CENTER_VERTICAL
            content.addView(it, LinearLayout.LayoutParams(-1, -2))
        } else null
        (compactTopRow ?: content).addView(machineRow, if (compactTopRow != null)
            LinearLayout.LayoutParams(0, -2, 2f) else LinearLayout.LayoutParams(-1, -2))
        machineHeading.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        val spaciousHome = !phoneHome && resources.configuration.screenWidthDp >= 800 && !shortHome
        val boilerRow = MachineHomeView(host, spaciousHome, onOpenAi = {
            // Navigation only: never selects a recipe or invokes a machine command.
            startActivity(Intent(host, AiChatActivity::class.java))
        }).apply { tag = "product/home/boilers" }
        content.addView(boilerRow, LinearLayout.LayoutParams(-1, -2).apply { if(spaciousHome) topMargin=dp(8) })
        fun boiler(label: Int, tagName: String, change: (Boolean) -> MachineSettingChange): List<View> {
            val card = productStyle.column("product/home/$tagName").apply {
                setPadding(dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 0 else if (shortHome) 0 else 4),
                    dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 0 else if (shortHome) 0 else 4))
                background = productStyle.shape(productStyle.surface, true)
                if (shortHome) {
                    keepOwnBackdropTreatment()
                    tintPanelForWallpaper((host.application as MobileApplication).pageBackgrounds,
                        productStyle.shape(wallpaperTint(productStyle.surface), true))
                }
            }
            boilerRow.addBoiler(card)
            val heading = productStyle.text(getString(label), TaskProductVisualStyle.Type(14, 500, 20))
            heading.text = getString(if (tagName == "brew-boiler") R.string.home_brew_boiler else R.string.home_steam_boiler)
            val value = productStyle.text("—", TaskProductVisualStyle.Type(if (spaciousHome) 48 else if (phoneHome) 36 else 19, 700,
                if (spaciousHome) 58 else if (phoneHome) 44 else 25), "product/home/$tagName/temperature")
            value.setSingleLine(true)
            value.setHorizontallyScrolling(false)
            value.setAutoSizeTextTypeUniformWithConfiguration(16, if (spaciousHome) 48 else if (phoneHome) 36 else 19, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            val pressure = productStyle.text("—", TaskProductVisualStyle.Type(if (spaciousHome) 48 else if (phoneHome) 36 else 19, 700,
                if (spaciousHome) 58 else if (phoneHome) 44 else 25), "product/home/$tagName/pressure")
            val target = productStyle.text("—", TaskProductVisualStyle.Type(if (shortHome) 14 else 16, 400, if (shortHome) 20 else 24), "product/home/$tagName/target", true)
            target.tag = "product/home/$tagName/target"
            val toggle = HoyiSwitch(host).apply {
                tag = "product/home/$tagName/toggle"
                contentDescription = heading.text
                setOnCheckedChangeListener { _, checked ->
                    if (!renderingBoilerSwitch) changeBoilerSetting(change(checked))
                }
            }
            if (shortHome) {
                // Readings and controls run side-by-side so the new 48dp switch
                // does not consume a full extra row in short landscape windows.
                val compact = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                val readings = productStyle.column()
                productStyle.apply(pressure, TaskProductVisualStyle.Type(14, 400, 20))
                productStyle.add(readings, heading)
                productStyle.add(readings, value)
                productStyle.add(readings, pressure)
                compact.addView(readings, LinearLayout.LayoutParams(0, -2, 1f))
                val controls = productStyle.column().apply { gravity = android.view.Gravity.CENTER_HORIZONTAL }
                controls.addView(toggle, LinearLayout.LayoutParams(dp(48), dp(48)))
                target.gravity = android.view.Gravity.CENTER
                productStyle.add(controls, target)
                compact.addView(controls, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(6) })
                productStyle.add(card, compact)
            } else {
                val dial = BoilerTemperatureDial(host, heading, value, target, pressure, toggle,
                    steam = tagName == "steam-boiler").apply { keepOwnBackdropTreatment() }
                boilerDials += dial
                card.background = null
                card.addView(dial, LinearLayout.LayoutParams(dp(if (spaciousHome) 200 else 176), dp(if (spaciousHome) 200 else 176)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
            }
            boilerExceptions[toggle] = button(card, "") { changeBoilerSetting(change(toggle.isChecked)) }.apply {
                tag = "product/home/$tagName/exception"
                visibility = View.GONE
                layoutParams = LinearLayout.LayoutParams(-1, -2)
            }
            return listOf(value, pressure, target, toggle)
        }
        boiler(R.string.home_brew_temperature, "brew-boiler") {
            MachineSettingChange.BrewHeating(it)
        }.let { (value, pressure, target, toggle) -> brewBoilerTemperature=value as TextView; brewBoilerPressure=pressure as TextView;
            brewBoilerTarget=target as TextView; brewBoilerToggle=toggle as HoyiSwitch }
        boiler(R.string.home_steam_temperature, "steam-boiler") {
            MachineSettingChange.SteamHeating(it)
        }.let { (value, pressure, target, toggle) -> steamBoilerTemperature=value as TextView; steamBoilerPressure=pressure as TextView;
            steamBoilerTarget=target as TextView; steamBoilerToggle=toggle as HoyiSwitch }
        HomeViewportBudget.install(root, content, boilerRow, boilerDials, phoneHome, compactType, spaciousHome)
        // FillViewport supplies an exact height only when the natural stack fits.
        // Weighted empty space then absorbs the spare room, without moving the header.
        // For an overflowing window the spacer measures zero; all controls scroll normally.
        quickBottomSpacer = if (anchorPhoneShortcuts) android.widget.Space(host).also { spacer ->
            spacer.tag = "product/home/quick-bottom-spacer"
            content.addView(spacer, LinearLayout.LayoutParams(-1, 0, 1f))
        } else null
        quickRow = productStyle.column("product/home/quick-extraction").apply { clipChildren = false; clipToPadding = false }
        content.addView(quickRow, LinearLayout.LayoutParams(-1, -2).apply {
            marginStart = -shortcutOutset; marginEnd = -shortcutOutset
        })
        if (!phoneHome) {
            val modeGroup = productStyle.row("product/home/compact-mode-group").apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(productStyle.text(getString(R.string.machine_mode_title), TaskProductVisualStyle.auxiliary).apply {
                    protectRoundedBackdropContent(productStyle.surface, dp(8), horizontalOutset = dp(4), omitForPhoto = true)
                }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
                addView(machineModes, LinearLayout.LayoutParams(-2, -2))
            }
            content.addView(modeGroup, content.indexOfChild(quickRow), LinearLayout.LayoutParams(-2, -2).apply {
                gravity = android.view.Gravity.END
                marginStart = -shortcutOutset; marginEnd = -shortcutOutset
            })
        }
        directPresets = HomePresetGrid(host, productStyle, phoneHome, shortHome, onSlotClick = { slot ->
            if (slot == 0) MachineLeverRecipeUi.details(host) else host.openShortcut(slot)
        }, onNameLongClick = { fullName ->
            AlertDialog.Builder(host).setTitle(R.string.home_preset_full_name)
                .setMessage(fullName).setPositiveButton(android.R.string.ok, null).let(::showTransient)
        }, onLastClick = ::useLastCurve)
        quickRow.addView(directPresets, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(if (shortHome) 0 else if (phoneHome) 2 else 10 - shortcutBottomBreathingRoom)
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
        leverButton = button(deviceCard, getString(R.string.machine_mode_title)) { chooseLeverMode() }
        sleepButton = button(deviceCard, getString(R.string.home_sleep_button)) { sleepNow() }.apply {setSingleLine(true);maxLines=1}
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
        button(presetPanel, getString(R.string.lever_title)) {
            presetDialog?.dismiss(); MachineLeverRecipeUi.details(host)
        }
        presetButtons = PresetSlots.userFacing.map { slot ->
            button(presetPanel, getString(R.string.home_slot, PresetSlots.label(slot))) {
                presetDialog?.dismiss()
                host.openShortcut(slot)
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
        host.openSettingsSection("devices")
    }
    override fun onResume() {
        super.onResume()
        feedbackUi.start()
        recentCurves.refresh()
        // Assignment happens on the library page; returning here refreshes tiles once.
        // Telemetry renders every 250 ms and must not rescan the entire curve catalog.
        presetVisualsDirty = true
        lastCurveDirty = true
        visible = true
        host.observeService(serviceObserver)
        handler.post(refresh)
    }
    override fun onPause() {
        feedbackUi.leave(host.isChangingConfigurations)
        visible = false; handler.removeCallbacks(refresh)
        boilerDials.forEach(BoilerTemperatureDial::suspendMotion)
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
            if (missingBle().isEmpty()) {
                if(BuildConfig.MANUAL_REAL_BLE_EXISTING_ID)toast(getString(R.string.manual_real_ble_scan_again))
                else enableAndScan()
            } else toast(getString(R.string.home_permission_required))
        }
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == ENABLE && resultCode == RESULT_OK) {
            if(BuildConfig.MANUAL_REAL_BLE_EXISTING_ID)toast(getString(R.string.manual_real_ble_scan_again))
            else enableAndScan()
        }
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
        if (service?.connectRememberedCoffee(device.address) != RememberedCoffeeConnection.PASSWORD_REQUIRED) return
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
    private fun showSplitRecovery(owner:MobileService) {
        val choices=owner.splitRecoveryChoices
        if(choices.isEmpty())return
        fun title(choice:CanonicalMachineOperationBroker.RecoveryChoice):String =
            when(val target=choice.entry.target) {
                is io.openhoyi.session.SplitMachineWriteJournal.Target.Weekly -> getString(R.string.home_recovery_schedule_title)
                is io.openhoyi.session.SplitMachineWriteJournal.Target.Boiler ->
                    getString(if(target.boiler==io.openhoyi.session.SplitMachineWriteJournal.Target.BoilerId.BREW)
                        R.string.home_brew_boiler else R.string.home_steam_boiler)
                null -> getString(R.string.home_recovery_button)
            }
        AlertDialog.Builder(host).setTitle(R.string.home_recovery_button)
            .setItems(choices.map(::title).toTypedArray()) { _,index ->
                val choice=choices[index]
                val observed=owner.takeIf {service===it}?.splitObservation(choice)
                if(observed==null) {
                    toast(getString(R.string.service_v2_split_recovery_wait))
                } else {
                    val evidence=if(choice.entry.target==observed.observed) R.string.service_v2_split_observed_match
                        else R.string.service_v2_split_observed_different
                    AlertDialog.Builder(host).setTitle(title(choice)).setMessage(
                        splitObservationText(observed.observed) + "\n\n" + getString(evidence))
                        .setPositiveButton(R.string.home_recovery_clear) { _,_ ->
                            if(service!==owner || !owner.resolveSplitObservation(observed))
                                toast(getString(R.string.service_v2_split_recovery_wait))
                            render()
                        }.setNegativeButton(R.string.machine_settings_cancel,null).let(::showTransient)
                }
            }.setNegativeButton(R.string.machine_settings_cancel,null).let(::showTransient)
    }

    private fun splitObservationText(observed:io.openhoyi.session.SplitMachineWriteJournal.Target):String =
        when(observed) {
            is io.openhoyi.session.SplitMachineWriteJournal.Target.Boiler ->
                getString(if(observed.enabled) R.string.setting_on else R.string.setting_off)
            is io.openhoyi.session.SplitMachineWriteJournal.Target.Weekly -> {
                val names=listOf(R.string.machine_settings_sunday,R.string.machine_settings_monday,
                    R.string.machine_settings_tuesday,R.string.machine_settings_wednesday,R.string.machine_settings_thursday,
                    R.string.machine_settings_friday,R.string.machine_settings_saturday)
                observed.days.mapIndexed { index,day ->
                    val at=day.time
                    "${getString(names[index])} · ${getString(if(day.enabled) R.string.setting_on else R.string.setting_off)} · " +
                        "%02d:%02d–%02d:%02d".format(at.sleepHour,at.sleepMinute,at.wakeHour,at.wakeMinute)
                }.joinToString("\n")
            }
        }

    private fun changeMachineMode(change: MachineSettingChange.LeverMode) {
        if (!MachineSettingsEditGate.allowed(service)) { render(); return }
        service?.changeMachineSetting(change)?.let(::toast)
        render()
    }
    private fun chooseLeverMode() {
        val controls = MachineModeControls(host, ::changeMachineMode).apply { render(service); setPadding(dp(20), dp(4), dp(20), dp(4)) }
        dialogMachineModes = controls
        val dialog = AlertDialog.Builder(host).setTitle(R.string.machine_mode_title).setView(controls)
            .setPositiveButton(R.string.machine_settings_done, null).create()
        dialog.setOnDismissListener { dialogMachineModes = null }
        AppDialog.show(dialog); transientDialogs += dialog
    }
    private fun sleepNow() {
        if (BuildConfig.MOCK_MODE) { toast(getString(R.string.home_sleep_mock)); return }
        service?.enterSleepNow()?.let(::toast)
        render()
    }
    private fun changeBoilerSetting(change: MachineSettingChange) {
        try {
            if (BuildConfig.MOCK_MODE) return
            val owner = service ?: return
            val recovery = owner.settingWriteState == SettingsWriteTracker.State.UNKNOWN &&
                (change is MachineSettingChange.BrewHeating && owner.pendingSetting is MachineSettingChange.BrewHeating ||
                    change is MachineSettingChange.SteamHeating && owner.pendingSetting is MachineSettingChange.SteamHeating)
            if (recovery) {
                owner.refreshUnknownBoilerSettings()?.let(::toast)
            } else {
                val admission = BoilerHeatingAdmission.evaluate(owner)
                if (!admission.canSubmit) {
                    admission.reason?.let { toast(getString(it)) }
                    return
                }
                owner.changeMachineSetting(change)?.let(::toast)
            }
        } finally {
            render()
        }
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
        val boilerRecoveryActive=owner?.boilerReadbackRecoveryActive==true
        val localWriteProgress=boilerRecoveryActive || owner?.machineModeWritePending==true
        val warning = ShotSafetyAlert.resource(owner?.shotState ?: ExtractionState.IDLE, s.coffeeState)?.let { getString(it) }
            ?: owner?.manualSafetyMessage ?: owner?.machineWriteSafetyMessage.takeUnless {localWriteProgress}
        safetyWarning.visibility = if (warning == null) View.GONE else View.VISIBLE
        safetyWarning.show(warning.orEmpty())
        val splitRecovery=owner?.splitRecoveryChoices?.isNotEmpty()==true
        acknowledgeManual.visibility = if (!splitRecovery && owner?.manualSafetyMessage == null &&
            (owner?.machineWriteAcknowledgementAvailable != true || localWriteProgress)) View.GONE else View.VISIBLE
        acknowledgeManual.isEnabled = if (owner?.manualSafetyMessage != null)
            owner.shotRecoveryClearBlock == null else splitRecovery || owner?.machineWriteAcknowledgementAvailable == true
        val alarmBanner = machineAlarms.banner(s.alarmBits, s.alarmAt, now)
        prominentAlarm.visibility = if (alarmBanner == null) View.GONE else View.VISIBLE
        prominentAlarm.show(alarmBanner?.message.orEmpty())
        prominentAlarm.setTextColor(getColor(if (alarmBanner?.blocking == true)
            R.color.mobile_danger else R.color.mobile_alarm_advisory))
        // Connection/readiness already belongs to the shell. Only exceptional shot state
        // needs a second label here; keep mode controls in their existing header.
        val manualShot = owner?.manualShotActive == true
        status.show(if (manualShot) getString(R.string.home_manual_shot_status) else "")
        (status.parent as? View)?.visibility = if (manualShot) View.VISIBLE else View.GONE
        val bothReady = s.coffeeState == DeviceState.READY && s.scaleState == DeviceState.READY
        status.visibility = View.VISIBLE
        scanButton.visibility = if (bothReady && !s.scanning) View.GONE else View.VISIBLE
        scanButton.isEnabled = !s.scanning
        val shotActive = owner?.shotState?.let(ShotGate::active) == true
        val stopAction = StopActionPresentation.describe(owner?.shotState ?: ExtractionState.IDLE,
            s.coffeeState, running)
        emergencyStop.visibility = if (stopAction.visible) View.VISIBLE else View.GONE
        emergencyStop.isEnabled = stopAction.enabled
        emergencyStop.show(if (owner?.scalePreflight == true) getString(R.string.home_start_cancel) else getString(stopAction.labelResource))
        val settingBusy = owner?.blockingSettingWriteState in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
            SettingsWriteTracker.State.UNKNOWN)
        val preparationIdle = owner?.brewPreparationState == BrewPreparation.State.IDLE
        val freshAwakeIdle = s.coffee is IdleTelemetry && s.coffee.sleepStateRaw == 0 &&
            s.coffeeAt?.let { now >= it && now - it <= 1500 } == true
        leverButton.isEnabled = running && !shotActive && owner?.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null && !settingBusy &&
            owner?.sleepNowState != SleepNowTracker.State.UNKNOWN && preparationIdle &&
            s.coffeeState == DeviceState.READY && owner?.machineSettingsFresh == true && freshAwakeIdle
        machineModes.render(owner)
        dialogMachineModes?.render(owner)
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
        firmwareStatus.show(firmwarePresentation.describe(owner?.coffeeFirmware,s.coffeeState,BuildConfig.MOCK_MODE,s.coffeeFailure))
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
        val sleepLabel=SleepPresentation.status(owner?.sleepNowState,idle?.sleepStateRaw,(s.coffee as? IdleTelemetry)?.sleepStateRaw)
        val sleepHint=SleepPresentation.hint(owner?.sleepNowState,idle?.sleepStateRaw,(s.coffee as? IdleTelemetry)?.sleepStateRaw)
        sleepButton.show(getString(if((idle?.sleepStateRaw==1 || sleepLabel==R.string.sleep_status_last_confirmed) && !sleepBusy) R.string.task_shell_machine_asleep else R.string.home_sleep_button))
        sleepStatus.show(getString(sleepLabel)+(sleepHint?.let { " · "+getString(it) } ?: ""))
        alarmStatus.show(machineAlarms.describe(s.alarmBits, s.alarmAt, now))
        val compactStatus = resources.configuration.screenWidthDp < 400
        coffee.show(if (compactStatus) getString(R.string.home_coffee_compact, if (coffeeFresh) getString(R.string.home_live) else DeviceStatusText.coffeeLabel(host,s.coffeeState,s.coffeeFailure))
            else getString(R.string.home_coffee_status, DeviceStatusText.coffeeLabel(host,s.coffeeState,s.coffeeFailure), if (coffeeFresh) getString(R.string.home_live_suffix) else ""))
        coffeeDot.showDot(when (s.coffeeState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        val pending = owner?.pendingSetting
        val writeState = owner?.settingWriteState
        val ownsPending = owner?.ownsBoilerDisplayWrite == true
        fun inFlight(brew: Boolean) = HomeBoilerSwitchPresentation.pendingAppearance(
            brew, pending, writeState, ownsPending, recoveryRunning = boilerRecoveryActive)
        val dial = boilerDisplay.resolve(owner, owner?.boilerDisplayAddress,
            liveMachine, s.coffeeAt, s.settings?.brewTemperatureC, s.settings?.steamTemperatureC, now,
            hold = !shotActive && owner?.manualShotActive != true && (inFlight(true) || inFlight(false)) &&
                (s.coffeeState == DeviceState.READY || owner?.boilerReadbackReconnecting == true))
        val readings = MachineHomeReadings.from(dial.frame)
        val brewCurrent = when (val frame = dial.frame) {
            is IdleTelemetry -> frame.brewTemperatureHundredthsC / 100f
            is ExtractionTelemetry -> frame.brewTemperatureHundredthsC / 100f
            else -> null
        }
        val warmingSession = owner?.boilerPresentationSession?.takeIf {
            !shotActive && !boilerRecoveryActive && !inFlight(true) && !inFlight(false) &&
                idle != null && dial.frame === idle && idle.sleepStateRaw == 0 &&
                io.openhoyi.session.CoffeeAlarmPolicy.permitsNewControl(idle.alarmBits)
        }
        boilerDials.getOrNull(0)?.present(brewCurrent, dial.brewTarget?.toFloat(), warmingSession)
        boilerDials.getOrNull(1)?.present((dial.frame as? IdleTelemetry)?.steamTemperatureHundredthsC?.div(100f),
            dial.steamTarget?.toFloat(), warmingSession)
        // Only the round cards may hold the previous display; connection status, feed, and
        // control gates still use independently freshness-checked live telemetry.
        val liveReadings = if (dial.frame === liveMachine) readings else MachineHomeReadings.from(liveMachine)
        brewTemperature.show(liveReadings.brewTemperature)
        if (boilerDials.isEmpty()) brewBoilerTemperature.showMetric(readings.brewTemperature.compactTemperature())
        brewPressure.show(liveReadings.brewPressure)
        brewBoilerPressure.showBoilerPressure(readings.brewPressure)
        steamTemperature.show(liveReadings.steamTemperature)
        if (boilerDials.isEmpty()) steamBoilerTemperature.showMetric(readings.steamTemperature.compactTemperature())
        steamPressure.show(liveReadings.steamPressure)
        steamBoilerPressure.showBoilerPressure(readings.steamPressure)
        brewBoilerTarget.show(getString(R.string.home_target_label) + " " + (dial.brewTarget?.let { "${it} °C" } ?: "—"))
        steamBoilerTarget.show(getString(R.string.home_target_label) + " " + (dial.steamTarget?.let { "${it} °C" } ?: "—"))
        val heatingAdmission = BoilerHeatingAdmission.evaluate(owner, s, now)
        val boilerEditable = heatingAdmission.canSubmit && !BuildConfig.MOCK_MODE
        val brewRecovery=owner?.settingWriteState==SettingsWriteTracker.State.UNKNOWN && owner.pendingSetting is MachineSettingChange.BrewHeating
        val steamRecovery=owner?.settingWriteState==SettingsWriteTracker.State.UNKNOWN && owner.pendingSetting is MachineSettingChange.SteamHeating
        brewBoilerToggle.isEnabled = boilerEditable && !brewRecovery
        steamBoilerToggle.isEnabled = boilerEditable && !steamRecovery

        val preheatOccupied=PreheatStatusPresentation.boilerOccupied(
            owner?.brewPreparationState ?: BrewPreparation.State.IDLE,s.coffeeState==DeviceState.READY,
            freshAwakeIdle && idle?.let {io.openhoyi.session.CoffeeAlarmPolicy.permitsNewControl(it.alarmBits)}==true,
            owner?.machineSettingsFresh!=true || shotActive || owner?.manualShotActive==true ||
                owner?.machineControlSafetyMessage!=null || settingBusy || sleepBusy ||
                owner?.scheduleWriteState in setOf(io.openhoyi.session.SleepScheduleWriteTracker.State.WRITING,
                    io.openhoyi.session.SleepScheduleWriteTracker.State.WAITING_READBACK) ||
                owner?.cupResetState in setOf(io.openhoyi.session.CupResetTracker.State.WRITING,
                    io.openhoyi.session.CupResetTracker.State.WAITING_ZERO))
        renderingBoilerSwitch = true
        try {
            fun project(toggle: HoyiSwitch, brew: Boolean, checked: Boolean?) {
                val label = HomeBoilerSwitchPresentation.label(brew, checked,
                    owner?.pendingSetting, owner?.settingWriteState, owner?.boilerSettingsRecoveryBusy==true,
                    owner?.boilerReadbackReconnecting==true, preheatOccupied,
                    heatingAdmission.reason.takeUnless { BuildConfig.MOCK_MODE })
                val state = getString(label)
                boilerExceptions[toggle]?.let { status ->
                    val exceptional = HomeBoilerSwitchPresentation.showInline(label)
                    val refresh = label == R.string.home_boiler_refresh
                    val actionable = refresh && owner?.canRefreshUnknownBoilerSettings == true
                    val visible = if (exceptional) View.VISIBLE else View.GONE
                    if (status.visibility != visible) status.visibility = visible
                    status.isEnabled = actionable
                    status.isClickable = actionable
                    status.show(state)
                }
                val heading = getString(if (brew) R.string.home_brew_boiler else R.string.home_steam_boiler)
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    if (toggle.stateDescription != state) toggle.stateDescription = state
                } else {
                    val description = "$heading, $state"
                    if (toggle.contentDescription != description) toggle.contentDescription = description
                }
                toggle.keepPendingAppearance = inFlight(brew)
                val displayed = HomeBoilerSwitchPresentation.checked(
                    brew, checked, pending, writeState, ownsPending, recoveryRunning = boilerRecoveryActive)
                if (toggle.isChecked != displayed) toggle.isChecked = displayed
            }
            project(brewBoilerToggle, true, s.settings?.brewHeating)
            project(steamBoilerToggle, false, s.settings?.steamHeating)
        } finally {
            renderingBoilerSwitch = false
        }
        val liveScale = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.hundredthsGram
        scale.show(if (compactStatus) getString(R.string.home_scale_compact, if (liveScale != null) getString(R.string.home_live) else label(s.scaleState))
            else getString(R.string.home_scale_status, label(s.scaleState), if (liveScale != null) getString(R.string.home_live_suffix) else ""))
        scaleDot.showDot(when (s.scaleState) {
            DeviceState.READY -> R.color.mobile_success
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> R.color.mobile_danger
            else -> R.color.mobile_muted
        })
        val liveDeviceFlow = LiveTelemetry.scale(s.scaleObservation, s.scaleState, now)?.let {
            it.deviceFlowHundredths.takeIf { _ -> it.capabilities.deviceFlow }
        }
        scaleWeight.show("${liveScale?.let(::number) ?: "—"} g" +
            "  ·  ${liveDeviceFlow?.let(::number) ?: "—"} g/s")
        val app = application as MobileApplication
        val library = app.curves
        val revision = app.customCurvesResult.getOrNull()?.revision ?: -1L
        if (customCurveRevision != revision) {
            customCurveRevision = revision
            presetVisualsDirty = true
            lastCurveDirty = true
            recentCurves.refresh()
        }
        val selected = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)?.let(library::find)
        val cupActive = shotActive || owner?.manualShotActive == true
        quickRow.visibility = if (cupActive) View.GONE else View.VISIBLE
        quickBottomSpacer?.let { spacer ->
            val visibility = if (cupActive) View.GONE else View.INVISIBLE
            if (spacer.visibility != visibility) spacer.visibility = visibility
        }
        if (!cupActive && (lastCurveDirty || previousCupActive)) {
            showLastCurve(resolveLastCurve())
            lastCurveDirty = false
        }
        previousCupActive = cupActive
        recentCurves.updateAvailability()
        val displayed = if (cupActive) owner?.chartCurveId?.let(library::find) else selected
        val title = displayed?.name ?: getString(if (cupActive && owner?.chartCurveId == "manual") R.string.extraction_manual_label
            else if (cupActive) R.string.task_brew_curve_unknown else R.string.home_curve_missing)
        selection.show(title)
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
        val leverStore = app.leverRecipes
        val leverAddress = service?.leverDeviceAddress
        val leverAttempt = leverAddress?.let(leverStore::attemptFor)
        val leverRecord = leverAddress?.let(leverStore::recordFor)
        directPresets.showSlots(leverRecord != null)
        val leverUnknown = leverAttempt?.state == MachineLeverRecipeStore.State.UNKNOWN
        val leverName = when {
            leverUnknown -> getString(R.string.lever_unknown_short)
            leverRecord != null -> leverRecord.snapshot.name
            else -> getString(R.string.lever_empty)
        }
        directPresets.visuals.first().apply {
            name.show(leverName)
            val description = "${getString(R.string.lever_title)} · ${getString(R.string.lever_recent)} · $leverName"
            if (button.contentDescription?.toString() != description) button.contentDescription = description
            val preview = if (leverUnknown) null else leverRecord?.snapshot?.frameHex?.let(MachineLeverRecipePreview::from)
            val visibility = if (preview == null) View.GONE else View.VISIBLE
            if (sparkline.visibility != visibility) sparkline.visibility = visibility
            // Clear old-device/uncertain previews as well; no cached machine identity is trusted.
            val desiredPreview = preview ?: CurveStageParameters.Preview(emptyList())
            if (sparkline.preview != desiredPreview) sparkline.preview = desiredPreview
        }
        if (presetVisualsDirty) {
            val presetPrefs = getSharedPreferences("presets", MODE_PRIVATE)
            val presetScale = CurveStageParameters.SharedScale.from(library.items)
            presetButtons.forEachIndexed { index, button ->
                val slot = index + 1
                val id = PresetSlots.curveId(slot, presetPrefs.getString(PresetSlots.key(slot), null), library)
                val item = library.find(id)
                val empty = id == PresetSlots.EMPTY
                val unavailable = id.startsWith("draft-") && (item == null || !library.canStart(item))
                val label = getString(R.string.home_slot_curve, PresetSlots.label(slot),
                    if (empty) getString(R.string.curve_slot_choose) else if (unavailable) getString(R.string.curve_slot_invalid) else item?.name ?: id)
                if (button.text.toString() != label) button.text = label
                val visual = directPresets.visuals[index + 1]
                val homeName = if (empty) getString(R.string.curve_slot_choose) else if (unavailable) getString(R.string.curve_slot_invalid) else HomePresetName.resolve(item?.customDocument?.name, item?.name, getString(R.string.home_slot_unconfigured))
                visual.button.contentDescription = "${visual.label.text} · $homeName"
                visual.button.isSelected = item != null && item.id == selected?.id
                visual.button.isEnabled = empty || unavailable || item?.let(library::canStart) == true
                visual.name.show(homeName)
                visual.name.setTextColor(if (!unavailable && visual.button.isEnabled) productStyle.ink else productStyle.muted)
                val thumbnail = HomePresetPreview.from(item, presetScale)
                visual.sparkline.visibility = if (thumbnail == null) View.GONE else View.VISIBLE
                if (thumbnail != null) {
                    if (visual.sparkline.preview != thumbnail.preview) visual.sparkline.preview = thumbnail.preview
                    if (visual.sparkline.scale != thumbnail.scale) visual.sparkline.scale = thumbnail.scale
                }
            }
            presetVisualsDirty = false
        }
        val keys = s.candidates.map { "${it.address}:${it.advertisedName}:${it.scaleProtocolId}" }
        if (keys != candidateKeys) {
            candidateKeys = keys; candidates.removeAllViews()
            s.candidates.forEach { device ->
                ScaleCandidateStatus.resource(device.scaleProtocolId)?.let { status ->
                    HoyiUi.label(host, candidates, getString(status), 14, muted = true)
                }
                button(candidates, getString(R.string.home_candidate, device.advertisedName, getString(if (device.candidateRole == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale))) { choose(device) }
            }
        }
    }
    private fun label(state: DeviceState): String = when (state) {
        DeviceState.READY -> getString(R.string.home_connected); DeviceState.DISCONNECTED -> getString(R.string.device_state_disconnected); DeviceState.FAILED -> getString(R.string.device_state_failed)
        DeviceState.UNSUPPORTED -> getString(R.string.device_state_unsupported); else -> getString(R.string.device_state_connecting)
    }
    private fun resolveLastCurve(): HomeLastCurve {
        return host.resolveLastCurve()
    }

    private fun showLastCurve(state: HomeLastCurve) {
        lastCurveState = state
        lastCurveReference = host.lastCurveReference()
        val ready = state as? HomeLastCurve.Ready
        val nameText = when (state) {
            HomeLastCurve.Empty -> getString(R.string.task_brew_usage_empty)
            HomeLastCurve.DataUnavailable -> getString(R.string.task_brew_usage_unavailable)
            is HomeLastCurve.Unavailable -> getString(if (state.reason == HomeLastCurve.Reason.MUTABLE_DRAFT)
                R.string.home_last_draft_changed else R.string.home_last_unavailable)
            is HomeLastCurve.Ready -> state.item.name
        }
        directPresets.last.apply {
            this.label.show(getString(if (ready == null) R.string.home_shortcut_last else R.string.home_shortcut_previous))
            name.show(nameText)
            name.setTextColor(if (ready == null) productStyle.muted else productStyle.ink)
            val preview = ready?.preview
            sparkline.visibility = if (preview == null) View.GONE else View.VISIBLE
            if (preview != null) {
                sparkline.preview = preview
                sparkline.scale = CurveStageParameters.SharedScale.from((application as MobileApplication).curves.items)
            }
            val action = getString(if (ready == null) R.string.home_curve_select else R.string.home_last_reuse)
            button.contentDescription = "${this.label.text} · $nameText · $action"
        }
    }
    private fun useLastCurve() {
        val reference = host.lastCurveReference()
        val latest = host.resolveLastCurve(reference)
        if (latest != lastCurveState || reference != lastCurveReference) {
            showLastCurve(latest)
            return
        }
        val ready = latest as? HomeLastCurve.Ready ?: run {
            host.select(TaskDestination.CURVES)
            return
        }
        if (reference == null) return
        host.openLastCurve(ready, reference)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun TextView.showBoilerPressure(reading: String) {
        val label = getString(R.string.task_chart_pressure)
        val rendered = "$label $reading"
        if (text.toString() == rendered) return
        text = if (parent is BoilerTemperatureDial) SpannableString(rendered).apply {
            setSpan(android.text.style.StyleSpan(android.graphics.Typeface.BOLD), label.length + 1, length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        } else rendered
    }
    private fun String.compactTemperature() = substringBefore(' ').toFloatOrNull()?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: this
    private fun TextView.showMetric(value: String) {
        if (text.toString() == value) return
        val unitStart = value.indexOf(' ')
        text = when {
            unitStart < 0 -> value
            else -> SpannableString(value).apply {
                setSpan(RelativeSizeSpan(0.36f), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }
    private fun number(hundredths: Int) = String.format(Locale.ROOT, "%.2f", hundredths / 100.0)
    private fun toast(message: String) = Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun dotShape(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(when (color) { R.color.mobile_success -> getColor(R.color.home_device_ready); R.color.mobile_muted -> productStyle.muted; else -> getColor(color) })
    }
    private fun View.showDot(colorResource: Int) {
        val color = when (colorResource) { R.color.mobile_success -> getColor(R.color.home_device_ready); R.color.mobile_muted -> productStyle.muted; else -> getColor(colorResource) }
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
        productStyle.button(HoyiUi.button(host, parent, value, primary = primary, action = action), primary).apply {
            if (primary) background = android.graphics.drawable.StateListDrawable().apply {
                addState(intArrayOf(-android.R.attr.state_enabled), productStyle.shape(productStyle.disabled))
                addState(intArrayOf(), productStyle.shape(productStyle.homePrimary))
            } else setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(productStyle.onDisabled, homeAccent)))
        }
    private fun workbenchLink(parent: LinearLayout, value: String, action: () -> Unit) =
        productStyle.button(HoyiUi.button(host, parent, value, action = action)).apply {
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
