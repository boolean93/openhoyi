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
    private val boilerDials = mutableListOf<BoilerTemperatureDial>()
    private lateinit var brewBoilerTemperature: TextView
    private lateinit var brewBoilerPressure: TextView
    private lateinit var steamBoilerTemperature: TextView
    private lateinit var steamBoilerPressure: TextView
    private lateinit var boilerWriteStatus: TextView
    private lateinit var brewBoilerTarget: TextView
    private lateinit var steamBoilerTarget: TextView
    private lateinit var brewBoilerToggle: Button
    private lateinit var steamBoilerToggle: Button
    private lateinit var lastCurveRow: LinearLayout
    private lateinit var lastCurveName: TextView
    private lateinit var lastCurvePreview: CurveLibrarySparklineView
    private lateinit var lastCurveDivider: View
    private var lastCurveState: HomeLastCurve = HomeLastCurve.Empty
    private var lastCurveDirty = true
    private var previousCupActive = false
    private lateinit var quickLaunch: Button
    private lateinit var quickRow: LinearLayout
    private lateinit var machineConnect: Button
    private var machineRetryIconShown=false
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
    private data class DirectPresetVisual(val button: Button, val name: TextView,
                                          val sparkline: CurveLibrarySparklineView)
    private lateinit var directPresets: List<DirectPresetVisual>
    private var presetVisualsDirty = true
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
    private val homeAccent = android.graphics.Color.rgb(0, 158, 72)
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
        feedbackUi = BrewFeedbackDialog(host, savedInstanceState)
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(250,252,250))
        }
        val phoneHome = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_PORTRAIT
        val shortHome = TaskLayoutPolicy.compactHome(resources.configuration.screenHeightDp, phoneHome)
        val shortWideHome = shortHome && resources.configuration.screenWidthDp >= 600
        val content = LinearLayout(host).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(22), dp(if (shortHome) 1 else 4), dp(22), dp(if (shortHome) 1 else 4)) }
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
        status = text(content, getString(R.string.home_not_connected_initial), 17, true).apply { productStyle.apply(this, TaskProductVisualStyle.Type(18,500,26)); tag = "product/home/service-status" }
        content.removeView(status)
        val machineRow = productStyle.row("product/home/machine-status").apply {
            gravity = android.view.Gravity.CENTER_VERTICAL; minimumHeight = dp(42)
        }
        val compactTopRow = if (shortWideHome) productStyle.row("product/home/compact-top").also {
            it.gravity = android.view.Gravity.CENTER_VERTICAL
            content.addView(it, LinearLayout.LayoutParams(-1, -2))
        } else null
        (compactTopRow ?: content).addView(machineRow, if (compactTopRow != null)
            LinearLayout.LayoutParams(0, -2, 2f) else LinearLayout.LayoutParams(-1, -2))
        machineRow.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        machineRetryIconShown=false // A rebuilt View must establish its own drawable.
        machineConnect = button(machineRow, getString(R.string.home_connect)) {
            if(service?.snapshot?.coffeeState==DeviceState.READY)showDevicePanel() else host.reconnectCoffee()
        }.apply {
            tag = "product/home/connect"
            setSingleLine(true);maxLines=1
            layoutParams = LinearLayout.LayoutParams(-2, dp(48))
            if (shortWideHome) {
                minWidth = dp(64); minimumWidth = dp(64)
                setPadding(dp(8), paddingTop, dp(8), paddingBottom)
            }
        }
        val spaciousHome = !phoneHome && resources.configuration.screenWidthDp >= 800 && resources.configuration.screenHeightDp > 420
        val boilerRow = MachineHomeView(host, spaciousHome).apply { tag = "product/home/boilers" }
        content.addView(boilerRow, LinearLayout.LayoutParams(-1, -2).apply { if(spaciousHome) topMargin=dp(8) })
        boilerWriteStatus=productStyle.text("",TaskProductVisualStyle.Type(14,400,22),"product/home/boiler-write-status").apply {
            visibility=View.GONE; setPadding(0,dp(8),0,dp(8))
        }
        content.addView(boilerWriteStatus,LinearLayout.LayoutParams(-1,-2))
        fun boiler(label: Int, tagName: String, change: () -> MachineSettingChange): List<View> {
            val card = productStyle.column("product/home/$tagName").apply {
                setPadding(dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 0 else if (shortHome) 0 else 4),
                    dp(if (spaciousHome) 16 else 8), dp(if (spaciousHome) 0 else if (shortHome) 0 else 4))
                background = productStyle.shape(productStyle.surface, true)
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
            val toggle = button(card, getString(R.string.setting_on)) { confirmBoilerChange(change()) }.apply {
                tag = "product/home/$tagName/toggle"; minimumHeight = dp(48)
            }
            if (shortHome) {
                // Short landscape windows need the readings and actions on the first screen.
                // Keep the dial presentation for portrait and taller landscape windows.
                val controls = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                controls.addView(heading, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
                (toggle.parent as? android.view.ViewGroup)?.removeView(toggle)
                controls.addView(toggle, LinearLayout.LayoutParams(-2, dp(48)))
                card.addView(controls)
                val readings = productStyle.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
                productStyle.apply(pressure, TaskProductVisualStyle.Type(14, 400, 20))
                // Units identify temperature; the live pressure field already includes its label.
                readings.addView(value, LinearLayout.LayoutParams(0, -2, 1f))
                readings.addView(pressure, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(4) })
                card.addView(readings, LinearLayout.LayoutParams(-1, -2))
                productStyle.add(card, target)
            } else {
                val dial = BoilerTemperatureDial(host, heading, value, target)
                boilerDials += dial
                card.background = null
                card.addView(dial, LinearLayout.LayoutParams(dp(if (spaciousHome) 200 else 176), dp(if (spaciousHome) 200 else 176)).apply { gravity = android.view.Gravity.CENTER_HORIZONTAL })
                card.addView(target, LinearLayout.LayoutParams(-1,-2))
                productStyle.apply(pressure, TaskProductVisualStyle.Type(if (phoneHome) 16 else 18, 400, if (phoneHome) 24 else 26))
                pressure.gravity = android.view.Gravity.CENTER
                card.addView(pressure, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(6) })
                (toggle.parent as? android.view.ViewGroup)?.removeView(toggle)
                productStyle.apply(toggle, TaskProductVisualStyle.Type(if (phoneHome) 16 else 20,400,if (phoneHome) 24 else 28))
                toggle.setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled),intArrayOf()),intArrayOf(productStyle.onDisabled,productStyle.ink)))
                toggle.background = null
                toggle.setCompoundDrawablesRelative(HomePowerDrawable(resources.displayMetrics.density, homeAccent), null, null, null)
                toggle.compoundDrawablePadding = dp(8)
                card.addView(toggle, LinearLayout.LayoutParams(-2, -2).apply {
                    gravity = android.view.Gravity.CENTER_HORIZONTAL
                    topMargin = 0
                })
            }
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
        if (spaciousHome || phoneHome) {
            val scroll=root.findViewWithTag<ScrollView>("product/home/page-scroll")
            val updateBudget=View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                val available=scroll.height
                if(available>0) {
                    boilerDials.forEach { dial ->
                        val parent=dial.parent as View
                        val diameter=minOf(dp(216),(parent.width-parent.paddingLeft-parent.paddingRight-dp(4)).coerceAtLeast(dp(112)))
                        if(dial.layoutParams.width!=diameter) dial.layoutParams=(dial.layoutParams as LinearLayout.LayoutParams).apply {width=diameter;height=diameter}
                    }
                }
            }
            scroll.addOnLayoutChangeListener(updateBudget)
            content.addOnLayoutChangeListener(updateBudget)
        }
        quickRow = productStyle.column("product/home/quick-extraction")
        content.addView(quickRow, LinearLayout.LayoutParams(-1, -2))
        val previewLegend=productStyle.text("",TaskProductVisualStyle.Type(12,400,20),"product/home/preview-legend").apply {
            val pressure="━ " + getString(R.string.curve_control_mode_pressure)
            val flow="━ " + getString(R.string.curve_control_mode_flow)
            text=android.text.SpannableString("$pressure    $flow").apply {
                setSpan(android.text.style.ForegroundColorSpan(productStyle.pressure),0,pressure.length,0)
                setSpan(android.text.style.ForegroundColorSpan(productStyle.accent),pressure.length+4,length,0)
            }
            contentDescription=getString(R.string.curve_control_mode_mixed) + " · " + getString(R.string.curve_stage_caption)
        }
        if(spaciousHome || phoneHome) {
            val quickTitle=productStyle.text(getString(R.string.home_quick_curves),TaskProductVisualStyle.Type(22,500,32),"product/home/quick-title")
            val previewHeading=object:LinearLayout(host) {
                init {gravity=android.view.Gravity.CENTER_VERTICAL;addView(quickTitle);addView(previewLegend)}
                override fun onMeasure(widthMeasureSpec:Int,heightMeasureSpec:Int) {
                    val natural=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED)
                    quickTitle.measure(natural,natural);previewLegend.measure(natural,natural)
                    val stacked=quickTitle.measuredWidth+previewLegend.measuredWidth+dp(16)>View.MeasureSpec.getSize(widthMeasureSpec)
                    orientation=if(stacked) VERTICAL else HORIZONTAL
                    val titleWidth=if(stacked) -1 else 0
                    val titleWeight=if(stacked) 0f else 1f
                    val titleParams=quickTitle.layoutParams as LayoutParams
                    if(titleParams.width!=titleWidth || titleParams.weight!=titleWeight) quickTitle.layoutParams=LayoutParams(titleWidth,-2,titleWeight)
                    val legendWidth=if(stacked) -1 else -2
                    if(previewLegend.layoutParams.width!=legendWidth) previewLegend.layoutParams=LayoutParams(legendWidth,-2)
                    previewLegend.gravity=if(stacked) android.view.Gravity.START else android.view.Gravity.END
                    super.onMeasure(widthMeasureSpec,heightMeasureSpec)
                }
            }
            quickRow.addView(previewHeading,LinearLayout.LayoutParams(-1,-2))
        } else quickRow.addView(previewLegend,LinearLayout.LayoutParams(-1,-2))
        lastCurveRow = productStyle.row("product/home/last-curve").apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            minimumHeight = dp(if(spaciousHome) 76 else 48)
            setPadding(dp(if (shortHome) 4 else 12), dp(if (spaciousHome) 8 else 0),
                dp(if (shortHome) 4 else 12), dp(if (spaciousHome) 8 else 0))
            background = productStyle.shape(productStyle.surface, true)
            elevation=resources.displayMetrics.density
        }
        if (compactTopRow != null) compactTopRow.addView(lastCurveRow,
            LinearLayout.LayoutParams(0, -2, 3f).apply { marginStart = dp(8) })
        else quickRow.addView(lastCurveRow, LinearLayout.LayoutParams(-1, -2).apply {
            if (spaciousHome) topMargin = dp(6)
        })
        lastCurvePreview = CurveLibrarySparklineView(host, productStyle).apply {
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            tag = "product/home/last-curve/preview"
        }
        lastCurveRow.addView(lastCurvePreview, LinearLayout.LayoutParams(dp(if (shortWideHome) 48 else if (shortHome) 96 else 160),
            dp(if (shortHome) 40 else 48)))
        lastCurveDivider = View(host).apply {
            setBackgroundColor(productStyle.border)
            visibility = View.GONE
        }
        lastCurveRow.addView(lastCurveDivider, LinearLayout.LayoutParams(dp(1), dp(if (shortHome) 36 else 48)).apply {
            marginStart = dp(if (shortHome) 4 else 12)
            marginEnd = dp(if (shortHome) 8 else 16)
        })
        val lastWords = productStyle.column()
        lastCurveRow.addView(lastWords, if(phoneHome) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(0, -2, 1f))
        val lastHeading = productStyle.text(getString(R.string.home_last_heading),
            TaskProductVisualStyle.Type(17, 400, 24),
            secondary = true).apply {
                if (shortWideHome) {
                    setSingleLine(true)
                    ellipsize = android.text.TextUtils.TruncateAt.END
                }
            }
        lastWords.addView(lastHeading)
        lastCurveName = productStyle.text(getString(R.string.history_no_local),
            TaskProductVisualStyle.Type(20, 500, 28),
            "product/home/quick-recipe").apply {
            maxLines = if (shortWideHome) Int.MAX_VALUE else if(phoneHome) 3 else 1
            ellipsize = if (shortWideHome) null else android.text.TextUtils.TruncateAt.END
        }
        lastWords.addView(lastCurveName)
        quickLaunch = button(lastCurveRow, getString(R.string.home_curve_select), primary = true) {
            useLastCurve()
        }.apply {
            textSize=21f
            setSingleLine(true)
            if (shortWideHome) setPadding(dp(4), paddingTop, dp(4), paddingBottom)
            tag = "product/home/quick-launch"
            minimumHeight=dp(48); minHeight=dp(48)
            layoutParams = LinearLayout.LayoutParams((dp(if (shortWideHome) 96 else 124)*resources.configuration.fontScale.coerceAtLeast(1f)).toInt(), -2).apply { marginStart = dp(8) }
        }
        if(phoneHome) {
            lastCurveRow.orientation=LinearLayout.VERTICAL
            lastCurveDivider.visibility=View.GONE
            (lastCurvePreview.layoutParams as LinearLayout.LayoutParams).apply { width=-1; height=dp(44) }
            (quickLaunch.layoutParams as LinearLayout.LayoutParams).apply { width=-1; marginStart=0; topMargin=dp(8) }
        }
        val presetRow = productStyle.column("product/home/direct-presets").apply {
            orientation=if(phoneHome) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
        }
        var phonePresetRow:LinearLayout?=null
        quickRow.addView(presetRow, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(if (shortHome) 0 else 6)
        })
        directPresets = (1..5).map { slot ->
            val tile = FrameLayout(host).apply { minimumHeight = dp(if (spaciousHome) 120 else if (shortHome) 96 else 80) }
            if(phoneHome) {
                if(slot%2==1) {
                    phonePresetRow=productStyle.row()
                    presetRow.addView(phonePresetRow,LinearLayout.LayoutParams(-1,-2).apply {if(slot>1) topMargin=dp(10)})
                }
                phonePresetRow!!.addView(tile,LinearLayout.LayoutParams(0,-2,1f).apply {if(slot%2==0) marginStart=dp(10)})
                if(slot==5) phonePresetRow!!.addView(View(host),LinearLayout.LayoutParams(0,1,1f).apply {marginStart=dp(10)})
            } else presetRow.addView(tile, LinearLayout.LayoutParams(0, if (shortHome) -2 else (dp(if (spaciousHome) 120 else 96) * resources.configuration.fontScale.coerceAtLeast(1f)).toInt(), 1f).apply {
                if (slot > 1) marginStart = dp(if (spaciousHome) 8 else 4)
            })
            val button = productStyle.button(Button(host), outline = spaciousHome).apply {
                tag = "product/home/direct-preset/$slot"
                setOnClickListener { host.showExtraction(slot, confirmStart = true) }
            }
            tile.addView(button, FrameLayout.LayoutParams(-1, -1))
            val contents = LinearLayout(host).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.TOP
                setPadding(dp(if (shortHome) 6 else 10), dp(if (shortHome) 1 else 6), dp(if (shortHome) 6 else 10), dp(if (shortHome) 1 else 6))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            tile.addView(contents, FrameLayout.LayoutParams(-1, if(phoneHome || shortHome) -2 else -1))
            val slotLabel = productStyle.text(getString(R.string.home_slot, slot.toString()),
                TaskProductVisualStyle.Type(14, 400, 20),
                secondary = true).apply { gravity = android.view.Gravity.START }
            val previewHeader = if (shortWideHome) productStyle.row().apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                contents.addView(this, LinearLayout.LayoutParams(-1, -2))
            } else contents
            previewHeader.addView(slotLabel, LinearLayout.LayoutParams(if (shortWideHome) -2 else -1, -2))
            val sparkline = CurveLibrarySparklineView(host, productStyle).apply {
                visibility = View.GONE
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                tag = "product/home/direct-preset/$slot/preview"
            }
            previewHeader.addView(sparkline, if (shortWideHome)
                LinearLayout.LayoutParams(0, dp(24), 1f).apply { marginStart = dp(4) }
            else LinearLayout.LayoutParams(-1, dp(if (shortHome) 24 else 40)))
            val name = productStyle.text("", TaskProductVisualStyle.Type(if (shortHome) 14 else 17, 500, if (shortHome) 20 else 24)).apply {
                gravity = android.view.Gravity.CENTER
                maxLines = if(phoneHome || shortHome) Int.MAX_VALUE else 2
                ellipsize = if(phoneHome || shortHome) null else android.text.TextUtils.TruncateAt.END
                if(phoneHome) minimumHeight=dp(48)
            }
            contents.addView(name, if(phoneHome || shortHome) LinearLayout.LayoutParams(-1,-2) else LinearLayout.LayoutParams(-1, 0, 1f))
            DirectPresetVisual(button, name, sparkline)
        }
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
        sleepButton = button(deviceCard, getString(R.string.home_sleep_button)) { confirmSleepNow() }.apply {setSingleLine(true);maxLines=1}
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
            getString(R.string.home_manual_shot_status) else DeviceStatusText.coffeeLabel(host,s.coffeeState,s.coffeeFailure) +
            if (LiveTelemetry.machine(s.coffee, s.coffeeState, s.coffeeAt, now) != null)
                getString(R.string.home_live_suffix) else "")
        val bothReady = s.coffeeState == DeviceState.READY && s.scaleState == DeviceState.READY
        status.visibility = View.VISIBLE
        machineConnect.show(getString(when {
            s.coffeeState==DeviceState.READY -> R.string.home_visual_change
            TaskShellDeviceStatus.connecting(s.coffeeState) -> R.string.task_shell_action_connecting
            else -> R.string.coffee_connection_retry
        }))
        val showRetry=s.coffeeState!=DeviceState.READY && !TaskShellDeviceStatus.connecting(s.coffeeState)
        if(machineRetryIconShown!=showRetry || showRetry && machineConnect.compoundDrawablesRelative[0]==null) {
            if(showRetry) {
                val chrome=TaskShellVisualStyle(host)
                val retry=HomeVisualIcon.drawable(host,"retry",machineConnect.currentTextColor,
                    currentTint={machineConnect.currentTextColor},strokeDp=chrome.iconStrokeDp,solidAccents=chrome.solidIcons)
                    .apply {setBounds(0,0,dp(20),dp(20))}
                machineConnect.compoundDrawablePadding=dp(8)
                machineConnect.setCompoundDrawablesRelative(retry,null,null,null)
            } else {
                machineConnect.setCompoundDrawablesRelative(null,null,null,null)
                machineConnect.compoundDrawablePadding=0
            }
            machineRetryIconShown=showRetry
        }
        machineConnect.contentDescription=getString(R.string.home_coffee)+" · "+DeviceStatusText.coffeeLabel(host,s.coffeeState,s.coffeeFailure)+" · "+machineConnect.text
        machineConnect.isEnabled=TaskShellDeviceStatus.canReconnect(s.coffeeState,owner?.manualShotActive==true || owner?.shotState?.let(ShotGate::active)==true)
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
        val readings = MachineHomeReadings.from(liveMachine)
        val brewCurrent = when (liveMachine) {
            is IdleTelemetry -> liveMachine.brewTemperatureHundredthsC / 100f
            is ExtractionTelemetry -> liveMachine.brewTemperatureHundredthsC / 100f
            else -> null
        }
        boilerDials.getOrNull(0)?.present(brewCurrent, s.settings?.brewTemperatureC?.toFloat())
        boilerDials.getOrNull(1)?.present(idle?.steamTemperatureHundredthsC?.div(100f), s.settings?.steamTemperatureC?.toFloat())
        brewTemperature.show(readings.brewTemperature)
        brewBoilerTemperature.showMetric(readings.brewTemperature.compactTemperature())
        brewPressure.show(readings.brewPressure)
        brewBoilerPressure.show(getString(R.string.task_chart_pressure) + " " + readings.brewPressure)
        steamTemperature.show(readings.steamTemperature)
        steamBoilerTemperature.showMetric(readings.steamTemperature.compactTemperature())
        steamPressure.show(readings.steamPressure)
        steamBoilerPressure.show(getString(R.string.task_chart_pressure) + " " + readings.steamPressure)
        brewBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.brewTemperatureC?.let { "${it} °C" } ?: "—"))
        steamBoilerTarget.show(getString(R.string.home_target_label) + " " + (s.settings?.steamTemperatureC?.let { "${it} °C" } ?: "—"))
        val boilerEditable = MachineSettingsEditGate.allowed(owner, s, now) && !BuildConfig.MOCK_MODE
        brewBoilerToggle.isEnabled = boilerEditable
        steamBoilerToggle.isEnabled = boilerEditable
        listOf(brewBoilerToggle,steamBoilerToggle).forEach { it.compoundDrawablesRelative.firstOrNull()?.alpha=if(boilerEditable) 255 else 85 }
        brewBoilerToggle.show(getString(if (s.settings?.brewHeating == true) R.string.setting_off else R.string.setting_on))
        steamBoilerToggle.show(getString(if (s.settings?.steamHeating == true) R.string.setting_off else R.string.setting_on))
        boilerWriteStatus.visibility=View.GONE
        val writeState = owner?.settingWriteState
        if (writeState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN)) {
            val message = getString(when (writeState) {
                SettingsWriteTracker.State.WRITING -> R.string.machine_settings_setting_writing
                SettingsWriteTracker.State.WAITING_READBACK -> R.string.machine_settings_setting_waiting
                else -> R.string.machine_settings_setting_unknown
            })
            boilerWriteStatus.show(message)
            boilerWriteStatus.visibility=View.VISIBLE
            boilerWriteStatus.setTextColor(if(writeState==SettingsWriteTracker.State.UNKNOWN) getColor(R.color.mobile_danger) else productStyle.muted)
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
        lastCurveRow.visibility = quickRow.visibility
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
        if (presetVisualsDirty) {
            val presetPrefs = getSharedPreferences("presets", MODE_PRIVATE)
            val presetScale = CurveStageParameters.SharedScale.from(library.items)
            presetButtons.forEachIndexed { index, button ->
                val slot = index + 1
                val id = PresetSlots.curveId(slot, presetPrefs.getString(PresetSlots.key(slot), null))
                val item = library.find(id)
                val label = getString(R.string.home_slot_curve, slot.toString(), item?.name ?: id)
                if (button.text.toString() != label) button.text = label
                val visual = directPresets[index]
                visual.button.contentDescription = label
                visual.button.background = productStyle.shape(if (item != null && item.id == selected?.id) android.graphics.Color.rgb(231,247,234) else android.graphics.Color.WHITE, true)
                visual.button.isEnabled = item?.let(library::canStart) == true
                visual.name.show(item?.name ?: getString(R.string.home_slot_unconfigured))
                visual.name.setTextColor(if (visual.button.isEnabled) 0xff087647.toInt() else productStyle.onDisabled)
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
                if (device.scaleProtocolId?.let(io.openhoyi.session.ScaleSelectionPolicy::adapter)?.readOnlyTransport == true)
                    HoyiUi.label(host, candidates, getString(R.string.scale_read_only_live), 14, muted = true)
                if (device.scaleProtocolId?.let(io.openhoyi.session.ScaleSelectionPolicy::adapter)?.documentedTransport == true)
                    HoyiUi.label(host, candidates, getString(R.string.scale_documented_live), 14, muted = true)
                button(candidates, getString(R.string.home_candidate, device.advertisedName, getString(if (device.candidateRole == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale))) { choose(device) }
            }
        }
    }
    private fun label(state: DeviceState): String = when (state) {
        DeviceState.READY -> getString(R.string.home_connected); DeviceState.DISCONNECTED -> getString(R.string.device_state_disconnected); DeviceState.FAILED -> getString(R.string.device_state_failed)
        DeviceState.UNSUPPORTED -> getString(R.string.device_state_unsupported); else -> getString(R.string.device_state_connecting)
    }
    private fun resolveLastCurve(): HomeLastCurve {
        val app = application as MobileApplication
        // Loading history also migrates positively observed recent uses into the permanent ledger.
        val history = runCatching { app.history }.getOrNull() ?: return HomeLastCurve.DataUnavailable
        val ledger = app.curveUsageResult.getOrNull() ?: return HomeLastCurve.DataUnavailable
        val recentObserved = history.entries.firstOrNull { it.curveId != "manual" && it.observedRunning }
            ?.let { CurveUsage(it.id, it.curveId, it.startedAtMs) }
        return HomeLastCurve.from(ledger.latest(), app.curves.items, recentObserved) {
            app.curves.canStart(it)
        }
    }
    private fun showLastCurve(state: HomeLastCurve) {
        lastCurveState = state
        val ready = state as? HomeLastCurve.Ready
        val label = when (state) {
            HomeLastCurve.Empty -> getString(R.string.task_brew_usage_empty)
            HomeLastCurve.DataUnavailable -> getString(R.string.task_brew_usage_unavailable)
            is HomeLastCurve.Unavailable -> getString(if (state.reason == HomeLastCurve.Reason.MUTABLE_DRAFT)
                R.string.home_last_draft_changed else R.string.home_last_unavailable)
            is HomeLastCurve.Ready -> state.item.name
        }
        lastCurveName.show(label)
        lastCurveName.setTextColor(if (ready == null) productStyle.muted else productStyle.ink)
        val preview = ready?.preview
        lastCurvePreview.visibility = if (preview == null) View.GONE else View.VISIBLE
        lastCurveDivider.visibility = if(resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_PORTRAIT) View.GONE else lastCurvePreview.visibility
        if (preview != null) {
            lastCurvePreview.preview = preview
            lastCurvePreview.scale = CurveStageParameters.SharedScale.from((application as MobileApplication).curves.items)
        }
        quickLaunch.show(getString(if (ready == null) R.string.home_curve_select else R.string.home_last_reuse))
        quickLaunch.contentDescription = getString(R.string.home_last_heading) + " · " + label + " · " + quickLaunch.text
    }
    private fun useLastCurve() {
        val latest = resolveLastCurve()
        if (latest != lastCurveState) {
            showLastCurve(latest)
            return
        }
        val ready = latest as? HomeLastCurve.Ready ?: run {
            host.select(TaskDestination.CURVES)
            return
        }
        // slot 7 resolves the persisted selection again in ExtractionFragment and MobileService.
        val saved = getSharedPreferences("curves", MODE_PRIVATE).edit()
            .putString("selected", ready.item.id).commit()
        if (!saved) { toast(getString(R.string.task_brew_usage_unavailable)); return }
        host.showExtraction(slot = 7, confirmStart = true)
    }
    private fun TextView.show(value: String) { if (text.toString() != value) text = value }
    private fun String.compactTemperature() = substringBefore(' ').toFloatOrNull()?.let { String.format(Locale.ROOT, "%.1f °C", it) } ?: this
    private fun TextView.showMetric(value: String) {
        if (text.toString() == value) return
        val unitStart = value.indexOf(' ')
        text = if (unitStart < 0) value else SpannableString(value).apply {
            setSpan(RelativeSizeSpan(0.36f), unitStart, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
    private fun number(hundredths: Int) = String.format(Locale.ROOT, "%.2f", hundredths / 100.0)
    private fun toast(message: String) = Toast.makeText(host, message, Toast.LENGTH_SHORT).show()
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun dotShape(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(when (color) { R.color.mobile_success -> homeAccent; R.color.mobile_muted -> productStyle.muted; else -> getColor(color) })
    }
    private fun View.showDot(colorResource: Int) {
        val color = when (colorResource) { R.color.mobile_success -> homeAccent; R.color.mobile_muted -> productStyle.muted; else -> getColor(colorResource) }
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
                addState(intArrayOf(), productStyle.shape(homeAccent))
            } else setTextColor(android.content.res.ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()), intArrayOf(productStyle.onDisabled, homeAccent)))
        }
    private fun workbenchLink(parent: LinearLayout, value: String, action: () -> Unit) =
        productStyle.button(HoyiUi.button(host, parent, value, action = action)).apply {
            gravity = android.view.Gravity.START or android.view.Gravity.CENTER_VERTICAL
        }
    companion object { private const val PERMISSIONS = 12; private const val ENABLE = 13; private const val EXPORT = 14 }
}
