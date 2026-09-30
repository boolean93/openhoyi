package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.SleepScheduleWriteTracker
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.View
import android.view.WindowInsets
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import java.util.UUID
import java.util.Locale

/** Only known setting commands are exposed; applied state requires a subsequent 0x83 readback. */
class MachineSettingsActivity : ThemedActivity() {
    private data class ScheduleCard(val heading: TextView, val period: TextView)
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
    private val visibilityToken = UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var connectionState: TextView
    private lateinit var settings: TextView
    private lateinit var settingsOverview: TextView
    private lateinit var brewTemperatureValue: TextView
    private lateinit var steamTemperatureValue: TextView
    private lateinit var runModeValue: TextView
    private lateinit var supplyValue: TextView
    private lateinit var settingsToggle: Button
    private var detailsExpanded = false
    private lateinit var schedule: TextView
    private lateinit var scheduleGrid: LinearLayout
    private val scheduleCards = mutableListOf<ScheduleCard>()
    private lateinit var writeStatus: TextView
    private lateinit var scheduleWriteStatus: TextView
    private lateinit var cupResetStatus: TextView
    private lateinit var cupResetButton: Button
    private lateinit var brewInput: EditText
    private lateinit var compensationInput: EditText
    private lateinit var steamInput: EditText
    private lateinit var standbyTemperatureInput: EditText
    private lateinit var brewHeatingButton: Button
    private lateinit var steamHeatingButton: Button
    private lateinit var lightButton: Button
    private lateinit var waterSupplyButton: Button
    private lateinit var runModeButton: Button
    private lateinit var sleepScheduleButton: Button
    private var selectedSettingsSection = 0
    private val settingSections = mutableListOf<LinearLayout>()
    private val settingTabs = mutableListOf<TextView>()
    private val controlButtons = mutableListOf<Button>()
    private val scheduleButtons = mutableListOf<Button>()
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            service?.screenVisible(visibilityToken, visible)
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 500) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        detailsExpanded = savedInstanceState?.getBoolean("settingsExpanded") ?: false
        selectedSettingsSection = savedInstanceState?.getInt("settingsSection") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(getColor(R.color.mobile_background))
            setOnApplyWindowInsetsListener { view, insets ->
                val area = if (Build.VERSION.SDK_INT >= 30) {
                    val x = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    intArrayOf(x.left, x.top, x.right, x.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    intArrayOf(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                view.setPadding(area[0], area[1], area[2], area[3])
                insets
            }
        }
        val scroll = ScrollView(this)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        HoyiUi.navigation(this, root, MachineSettingsActivity::class.java)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(28))
        }
        scroll.addView(body)
        setContentView(root)
        HoyiUi.header(this, body, getString(R.string.machine_settings_title), getString(R.string.machine_settings_subtitle))
        text(body, if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_mock_disclaimer)
            else getString(R.string.machine_settings_real_disclaimer), 14)
        val overview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controlsPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        if (HoyiUi.wide(this)) {
            val columns = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            body.addView(columns)
            columns.addView(overview, LinearLayout.LayoutParams(0, -2, .8f))
            columns.addView(controlsPane, LinearLayout.LayoutParams(0, -2, 1.2f).apply { marginStart = dp(16) })
        } else {
            body.addView(overview)
            body.addView(controlsPane)
        }
        val summaryCard = card(overview, getString(R.string.machine_settings_connection_title))
        connectionState = text(summaryCard, getString(R.string.machine_settings_disconnected), 16)
        val settingsCard = card(overview, getString(R.string.machine_settings_readback_title))
        settingsOverview = text(settingsCard, getString(R.string.machine_settings_readback_missing), 14)
        fun settingRow(first: String, second: String): Pair<TextView, TextView> {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            settingsCard.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
            fun tile(label: String): TextView {
                val box = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(12), dp(14), dp(12))
                    background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(6) })
                HoyiUi.label(this, box, label, 13, muted = true)
                return HoyiUi.label(this, box, "—", 21, true).apply { setPadding(0, dp(8), 0, 0) }
            }
            return tile(first) to tile(second)
        }
        settingRow(getString(R.string.machine_settings_brew_label), getString(R.string.machine_settings_steam_label)).also {
            brewTemperatureValue = it.first; steamTemperatureValue = it.second
        }
        settingRow(getString(R.string.machine_settings_run_mode_label), getString(R.string.machine_settings_supply_label)).also {
            runModeValue = it.first; supplyValue = it.second
        }
        settings = text(settingsCard, "", 14).apply {
            visibility = if (detailsExpanded) View.VISIBLE else View.GONE
        }
        settingsToggle = action(settingsCard, if (detailsExpanded) getString(R.string.machine_settings_readback_collapse) else getString(R.string.machine_settings_readback_expand)) {
            detailsExpanded = !detailsExpanded
            settings.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            settingsToggle.text = if (detailsExpanded) getString(R.string.machine_settings_readback_collapse) else getString(R.string.machine_settings_readback_expand)
        }
        val appInfo = card(overview, getString(R.string.application_info))
        text(appInfo, getString(R.string.app_name), 16, true)
        text(appInfo, getString(R.string.application_version,
            BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE), 14)
        text(appInfo, getString(if (BuildConfig.MOCK_MODE)
            R.string.application_mode_mock else R.string.application_mode_alpha), 14)
        text(appInfo, getString(R.string.application_package, BuildConfig.APPLICATION_ID), 13)
            .setTextIsSelectable(true)
        val writeCard = card(controlsPane, getString(R.string.machine_settings_feedback_title))
        writeStatus = text(writeCard, getString(R.string.machine_settings_feedback_initial), 14)
        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        controlsPane.addView(tabsScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(14) })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabsScroll.addView(tabs)
        fun section(label: String): LinearLayout {
            val index = settingSections.size
            val tab = TextView(this).apply {
                text = label
                textSize = 15f
                gravity = Gravity.CENTER
                minHeight = dp(48)
                setPadding(dp(16), dp(8), dp(16), dp(8))
                setOnClickListener { showSettingsSection(index) }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
            settingTabs += tab
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                controlsPane.addView(this)
                settingSections += this
            }
        }
        val temperatureSection = section(getString(R.string.machine_settings_tab_temperature))
        val functionsSection = section(getString(R.string.machine_settings_tab_functions))
        val standbySection = section(getString(R.string.machine_settings_tab_standby))
        val sleepSection = section(getString(R.string.machine_settings_tab_schedule))
        val maintenanceSection = section(getString(R.string.machine_settings_tab_maintenance))
        val controls = card(temperatureSection, getString(R.string.machine_settings_temperature_title))
        brewInput = temperatureInput(controls, getString(R.string.machine_settings_brew_input))
        controlButtons += action(controls, getString(R.string.machine_settings_brew_set)) {
            val c = brewInput.text.toString().toIntOrNull()
            if (c == null || c !in 75..105) brewInput.error = getString(R.string.machine_settings_brew_error)
            else confirm(MachineSettingChange.BrewTemperature(c))
        }
        compensationInput = temperatureInput(controls, getString(R.string.machine_settings_compensation_input))
        controlButtons += action(controls, getString(R.string.machine_settings_compensation_set)) {
            val c = compensationInput.text.toString().toIntOrNull()
            if (c == null || c !in 0..5) compensationInput.error = getString(R.string.machine_settings_compensation_error)
            else confirm(MachineSettingChange.BrewCompensation(c))
        }
        steamInput = temperatureInput(controls, getString(R.string.machine_settings_steam_input))
        controlButtons += action(controls, getString(R.string.machine_settings_steam_set)) {
            val c = steamInput.text.toString().toIntOrNull()
            if (c == null || c !in 110..145) steamInput.error = getString(R.string.machine_settings_steam_error)
            else confirm(MachineSettingChange.SteamTemperature(c))
        }
        val functionsCard = card(functionsSection, getString(R.string.machine_settings_functions_title))
        brewHeatingButton = action(functionsCard, getString(R.string.machine_settings_brew_heating_toggle)) {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.BrewHeating(!it.brewHeating)) }
        }
        steamHeatingButton = action(functionsCard, getString(R.string.machine_settings_steam_heating_toggle)) {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.SteamHeating(!it.steamHeating)) }
        }
        lightButton = action(functionsCard, getString(R.string.machine_settings_light_toggle)) {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.Light(it.flags and 0x08 == 0)) }
        }
        waterSupplyButton = action(functionsCard, getString(R.string.machine_settings_supply_toggle)) {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.WaterSupply(it.flags and 0x02 == 0)) }
        }
        runModeButton = action(functionsCard, getString(R.string.machine_settings_run_mode_toggle)) {
            service?.snapshot?.settings?.let { confirm(MachineSettingChange.RunMode(it.flags and 0x04 == 0)) }
        }
        controlButtons += listOf(brewHeatingButton, steamHeatingButton, lightButton, waterSupplyButton, runModeButton)
        val standbyCard = card(standbySection, getString(R.string.machine_settings_standby_title))
        controlButtons += action(standbyCard, getString(R.string.machine_settings_standby_delay_set)) { chooseStandbyDelay() }
        standbyTemperatureInput = temperatureInput(standbyCard, getString(R.string.machine_settings_standby_input))
        controlButtons += action(standbyCard, getString(R.string.machine_settings_standby_set)) {
            val c = standbyTemperatureInput.text.toString().toIntOrNull()
            if (c == null || c !in 0..100) standbyTemperatureInput.error = getString(R.string.machine_settings_standby_error)
            else {
                val minutes = service?.snapshot?.settings?.standbyMinutes
                if (minutes == null || minutes !in listOf(0, 15, 30, 60, 120))
                    Toast.makeText(this, getString(R.string.machine_settings_standby_unknown), Toast.LENGTH_SHORT).show()
                else confirm(MachineSettingChange.StandbyTemperature(c, minutes))
            }
        }
        val sleepCard = card(sleepSection, getString(R.string.machine_settings_schedule_title))
        schedule = text(sleepCard, getString(R.string.machine_settings_schedule_missing), 16)
        scheduleWriteStatus = text(sleepCard, getString(R.string.machine_settings_schedule_initial), 14)
        sleepScheduleButton = action(sleepCard, getString(R.string.machine_settings_schedule_toggle)) {
            service?.snapshot?.settings?.let {
                confirm(MachineSettingChange.SleepScheduleEnabled(it.flags and 0x01 == 0))
            }
        }
        controlButtons += sleepScheduleButton
        scheduleGrid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sleepCard.addView(scheduleGrid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8) })
        val scheduleColumns = if (resources.configuration.screenWidthDp >= 900) 2 else 1
        (0..6).chunked(scheduleColumns).forEach { days ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            scheduleGrid.addView(row)
            days.forEach { day ->
                val cell = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), dp(10), dp(14), dp(10))
                    background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_accent_soft, 12)
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    topMargin = dp(8)
                    if (day != days.last()) marginEnd = dp(8)
                })
                val heading = HoyiUi.label(this, cell, "", 15, true)
                val period = HoyiUi.label(this, cell, "", 13, muted = true).apply {
                    setPadding(0, dp(6), 0, 0)
                }
                scheduleCards += ScheduleCard(heading, period)
            }
            if (days.size < scheduleColumns) row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        }
        val scheduleEditor = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = android.view.View.GONE }
        val scheduleToggle = action(sleepCard, getString(R.string.machine_settings_schedule_edit)) {}
        scheduleToggle.setOnClickListener {
            scheduleEditor.visibility = if (scheduleEditor.visibility == android.view.View.GONE)
                android.view.View.VISIBLE else android.view.View.GONE
            scheduleToggle.text = if (scheduleEditor.visibility == android.view.View.VISIBLE)
                getString(R.string.machine_settings_schedule_collapse) else getString(R.string.machine_settings_schedule_edit)
        }
        sleepCard.addView(scheduleEditor)
        listOf(getString(R.string.machine_settings_sunday), getString(R.string.machine_settings_monday), getString(R.string.machine_settings_tuesday), getString(R.string.machine_settings_wednesday), getString(R.string.machine_settings_thursday), getString(R.string.machine_settings_friday), getString(R.string.machine_settings_saturday)).forEachIndexed { index, name ->
            scheduleButtons += action(scheduleEditor, getString(R.string.machine_settings_edit_sleep_day, name)) { editScheduleDay(index, name) }
        }
        controlButtons += scheduleButtons
        val cupCard = card(maintenanceSection, getString(R.string.machine_settings_cups_title))
        cupResetStatus = text(cupCard, getString(R.string.machine_settings_cups_initial), 14)
        cupResetButton = action(cupCard, getString(R.string.machine_settings_cups_reset)) { confirmCupReset() }
        showSettingsSection(selectedSettingsSection)
        render()
    }

    override fun onStart() {
        super.onStart()
        visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
    }
    override fun onStop() {
        visible = false
        handler.removeCallbacks(refresh)
        service?.screenVisible(visibilityToken, false)
        release()
        super.onStop()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settingsExpanded", detailsExpanded)
        outState.putInt("settingsSection", selectedSettingsSection)
        super.onSaveInstanceState(outState)
    }
    private fun showSettingsSection(index: Int) {
        selectedSettingsSection = index.coerceIn(settingSections.indices)
        settingSections.forEachIndexed { position, section ->
            val selected = position == selectedSettingsSection
            section.visibility = if (selected) View.VISIBLE else View.GONE
            settingTabs[position].apply {
                setTextColor(getColor(if (selected) R.color.mobile_accent else R.color.mobile_muted))
                background = HoyiUi.shape(this@MachineSettingsActivity,
                    if (selected) R.color.mobile_accent_soft else R.color.mobile_surface,
                    10, R.color.mobile_border)
            }
        }
    }
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    private fun render() {
        if (!::connectionState.isInitialized) return
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val ready = snapshot.coffeeState == DeviceState.READY
        connectionState.update(if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_mock_connection) else
            "${DeviceStatusText.label(snapshot.coffeeState)}${if (ready) " · 已认证" else " · 数据不可视为当前生效配置"}")
        val reported = snapshot.settings
        settingsOverview.update(if (reported == null) getString(R.string.machine_settings_readback_missing) else
            "${if (ready && owner?.machineSettingsFresh == true) "当前回读" else "回读已过期"} · 自动待机 ${if (reported.standbyMinutes == 0) getString(R.string.machine_settings_standby_never) else "${reported.standbyMinutes} 分钟"} · 累计 ${reported.cupCount} 杯")
        brewTemperatureValue.update(reported?.let { "${it.brewTemperatureC} °C" } ?: "—")
        steamTemperatureValue.update(reported?.let { "${it.steamTemperatureC} °C" } ?: "—")
        runModeValue.update(reported?.let { if (it.flags and 0x04 != 0) getString(R.string.setting_run_studio) else getString(R.string.setting_run_cafe) } ?: "—")
        supplyValue.update(reported?.let { if (it.flags and 0x02 != 0) getString(R.string.setting_water_piped) else getString(R.string.setting_water_tank) } ?: "—")
        if (ready && reported != null) {
            prefill(brewInput, reported.brewTemperatureC)
            prefill(steamInput, reported.steamTemperatureC)
            prefill(standbyTemperatureInput, reported.standbyTemperatureC)
            reported.brewCompensationTenthsC.takeIf { it % 10 == 0 }?.let {
                prefill(compensationInput, it / 10)
            }
        }
        settings.update(MachineSettingsPresentation.settings(snapshot.settings))
        settingsToggle.visibility = if (reported == null) View.GONE else View.VISIBLE
        settings.visibility = if (reported != null && detailsExpanded) View.VISIBLE else View.GONE
        schedule.update(when {
            snapshot.sleepFirst == null && snapshot.sleepSecond == null -> getString(R.string.machine_settings_schedule_missing)
            WeeklySleepSchedule.fromReadback(snapshot.sleepFirst, snapshot.sleepSecond) == null ->
                getString(R.string.machine_settings_schedule_incomplete)
            owner?.sleepScheduleFresh != true -> getString(R.string.machine_settings_schedule_stale)
            else -> getString(R.string.machine_settings_schedule_current)
        })
        scheduleGrid.visibility = if (snapshot.sleepFirst == null && snapshot.sleepSecond == null) View.GONE else View.VISIBLE
        MachineSettingsPresentation.scheduleDaySummaries(snapshot.sleepFirst, snapshot.sleepSecond)
            .forEachIndexed { index, day ->
                scheduleCards[index].heading.update("${day.name} · ${day.state}")
                scheduleCards[index].heading.setTextColor(getColor(if (day.state == "开启")
                    R.color.mobile_accent else R.color.mobile_text))
                scheduleCards[index].period.update(day.period)
            }
        scheduleWriteStatus.update(getString(R.string.machine_settings_schedule_status_prefix) + when (owner?.scheduleWriteState) {
            SleepScheduleWriteTracker.State.WRITING -> getString(R.string.machine_settings_schedule_writing)
            SleepScheduleWriteTracker.State.WAITING_READBACK -> getString(R.string.machine_settings_schedule_waiting)
            SleepScheduleWriteTracker.State.CONFIRMED -> getString(R.string.machine_settings_schedule_confirmed)
            SleepScheduleWriteTracker.State.FAILED -> getString(R.string.machine_settings_schedule_failed)
            SleepScheduleWriteTracker.State.UNKNOWN -> getString(R.string.machine_settings_schedule_unknown)
            SleepScheduleWriteTracker.State.RECONCILED -> getString(R.string.machine_settings_schedule_reconciled)
            else -> getString(R.string.machine_settings_unchanged)
        })
        val pending = owner?.settingWriteState ?: SettingsWriteTracker.State.IDLE
        cupResetStatus.update(getString(R.string.machine_settings_cup_status_prefix) + when (owner?.cupResetState) {
            CupResetTracker.State.WRITING -> getString(R.string.machine_settings_cup_writing)
            CupResetTracker.State.WAITING_ZERO -> getString(R.string.machine_settings_cup_waiting)
            CupResetTracker.State.CONFIRMED -> getString(R.string.machine_settings_cup_confirmed)
            CupResetTracker.State.FAILED -> getString(R.string.machine_settings_cup_failed)
            CupResetTracker.State.UNKNOWN -> getString(R.string.machine_settings_cup_unknown)
            CupResetTracker.State.RECONCILED -> getString(R.string.machine_settings_cup_reconciled)
            else -> getString(R.string.machine_settings_cups_initial)
        })
        writeStatus.update(if (owner?.pendingSetting == null && pending == SettingsWriteTracker.State.IDLE)
            getString(R.string.machine_settings_feedback_initial) else "${owner?.pendingSetting?.let(MachineSettingsPresentation::change) ?: "设置"} · " +
            when (pending) {
                SettingsWriteTracker.State.IDLE -> getString(R.string.machine_settings_unchanged)
                SettingsWriteTracker.State.WRITING -> getString(R.string.machine_settings_setting_writing)
                SettingsWriteTracker.State.WAITING_READBACK -> getString(R.string.machine_settings_setting_waiting)
                SettingsWriteTracker.State.CONFIRMED -> getString(R.string.machine_settings_setting_confirmed)
                SettingsWriteTracker.State.FAILED -> getString(R.string.machine_settings_setting_failed)
                SettingsWriteTracker.State.UNKNOWN -> getString(R.string.machine_settings_setting_unknown)
                SettingsWriteTracker.State.RECONCILED -> getString(R.string.machine_settings_setting_reconciled)
            })
        val idle = snapshot.coffee as? io.openhoyi.protocol.IdleTelemetry
        val now = android.os.SystemClock.elapsedRealtime()
        val freshIdle = idle?.sleepStateRaw == 0 &&
            snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } == true
        val editable = ready && owner?.machineSettingsFresh == true && freshIdle && owner.manualShotActive != true &&
            owner?.machineControlSafetyMessage == null &&
            owner?.shotState?.let(ShotGate::active) != true &&
            owner?.brewPreparationState == BrewPreparation.State.IDLE &&
            pending !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN) &&
            owner?.scheduleWriteState !in setOf(SleepScheduleWriteTracker.State.WRITING,
                SleepScheduleWriteTracker.State.WAITING_READBACK) &&
            owner?.cupResetState !in setOf(CupResetTracker.State.WRITING,
                CupResetTracker.State.WAITING_ZERO) &&
            owner?.sleepNowState !in setOf(SleepNowTracker.State.WRITING,
                SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN)
        controlButtons.forEach { it.isEnabled = editable }
        cupResetButton.isEnabled = editable && snapshot.settings?.cupCount?.let { it > 0 && it == idle?.cupCount } == true &&
            owner?.cupResetState != CupResetTracker.State.UNKNOWN &&
            owner?.sleepNowState !in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        scheduleButtons.forEach { it.isEnabled = editable &&
            owner?.scheduleWriteState != SleepScheduleWriteTracker.State.UNKNOWN &&
            owner?.sleepScheduleFresh == true }
        sleepScheduleButton.isEnabled = editable &&
            (snapshot.settings?.flags?.and(0x01) == 1 ||
                owner?.sleepScheduleFresh == true)
        brewHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_brew_heating),
            getString(if (snapshot.settings?.brewHeating == true) R.string.setting_on else R.string.setting_off))
        steamHeatingButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_steam_heating),
            getString(if (snapshot.settings?.steamHeating == true) R.string.setting_on else R.string.setting_off))
        lightButton.text = getString(R.string.setting_toggle_status,
            getString(R.string.setting_light),
            getString(if (snapshot.settings?.flags?.and(0x08) == 0x08) R.string.setting_on else R.string.setting_off))
        waterSupplyButton.text = getString(R.string.setting_water_supply_status,
            getString(if (snapshot.settings?.flags?.and(0x02) == 0x02)
                R.string.setting_water_piped else R.string.setting_water_tank))
        runModeButton.text = getString(R.string.setting_run_mode_status,
            getString(if (snapshot.settings?.flags?.and(0x04) == 0x04)
                R.string.setting_run_studio else R.string.setting_run_cafe))
        sleepScheduleButton.setText(when (snapshot.settings?.flags?.and(0x01)) {
            1 -> R.string.setting_sleep_schedule_on
            0 -> R.string.setting_sleep_schedule_off
            else -> R.string.setting_sleep_schedule_unknown
        })
    }
    private fun confirm(change: MachineSettingChange) {
        if (BuildConfig.MOCK_MODE) {
            AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_mock_confirm_title))
                .setMessage(getString(R.string.machine_settings_mock_confirm_message, MachineSettingsPresentation.change(change)))
                .setPositiveButton(getString(R.string.machine_settings_done), null).show()
            return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_confirm_title))
            .setMessage(MachineSettingsPresentation.change(change) +
                if (change is MachineSettingChange.WaterSupply)
                    ("\n" + getString(R.string.machine_settings_water_warning))
                else if (change is MachineSettingChange.RunMode)
                    ("\n" + getString(R.string.machine_settings_run_warning))
                else if (change is MachineSettingChange.BrewCompensation)
                    ("\n" + getString(R.string.machine_settings_compensation_warning))
                else ("\n" + getString(R.string.machine_settings_readback_warning)))
            .setPositiveButton(getString(R.string.machine_settings_send)) { _, _ ->
                service?.changeMachineSetting(change)?.let {
                    Toast.makeText(this, it, Toast.LENGTH_SHORT).show()
                }
                render()
            }
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
    }
    private fun confirmCupReset() {
        if (BuildConfig.MOCK_MODE) {
            Toast.makeText(this, getString(R.string.machine_settings_mock_cup_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        val expected = service?.snapshot?.settings?.cupCount ?: return
        val input = EditText(this).apply {
            hint = getString(R.string.machine_settings_cup_input_hint, expected.toString())
            inputType = InputType.TYPE_CLASS_NUMBER
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_cup_check_title))
            .setMessage(getString(R.string.machine_settings_cup_check_message))
            .setView(input).setPositiveButton(getString(R.string.machine_settings_next), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().toIntOrNull() != expected) {
                    input.error = getString(R.string.machine_settings_cup_input_error, expected.toString())
                    return@setOnClickListener
                }
                dialog.dismiss()
                AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_cup_confirm_title))
                    .setMessage(getString(R.string.machine_settings_cup_confirm_message, expected.toString()))
                    .setPositiveButton(getString(R.string.machine_settings_cup_send)) { _, _ ->
                        service?.resetCupCount(expected)?.let {
                            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
            }
        }
        dialog.show()
    }
    private fun chooseStandbyDelay() {
        val values = listOf(15, 30, 60, 120, 0)
        val labels = arrayOf(getString(R.string.machine_settings_standby_15), getString(R.string.machine_settings_standby_30), getString(R.string.machine_settings_standby_60), getString(R.string.machine_settings_standby_120), getString(R.string.machine_settings_standby_never))
        AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_standby_delay_title))
            .setItems(labels) { _, index ->
                val temperature = service?.snapshot?.settings?.standbyTemperatureC
                if (temperature == null) Toast.makeText(this, getString(R.string.machine_settings_readback_missing), Toast.LENGTH_SHORT).show()
                else if (temperature !in 0..100) Toast.makeText(this,
                    getString(R.string.machine_settings_standby_temperature_invalid), Toast.LENGTH_LONG).show()
                else confirm(MachineSettingChange.StandbyDelay(values[index], temperature))
            }.show()
    }
    private fun editScheduleDay(index: Int, name: String) {
        val baseline = service?.snapshot?.let {
            WeeklySleepSchedule.fromReadback(it.sleepFirst, it.sleepSecond)
        } ?: return
        val previous = baseline.days[index]
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
        }
        val enabled = CheckBox(this).apply { text = getString(R.string.machine_settings_day_enabled); isChecked = previous.enabled; form.addView(this) }
        val sleepHour = numberInput(form, getString(R.string.machine_settings_sleep_hour), previous.time.sleepHour)
        val sleepMinute = numberInput(form, getString(R.string.machine_settings_sleep_minute), previous.time.sleepMinute)
        val wakeHour = numberInput(form, getString(R.string.machine_settings_wake_hour), previous.time.wakeHour)
        val wakeMinute = numberInput(form, getString(R.string.machine_settings_wake_minute), previous.time.wakeMinute)
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_schedule_day_title, name))
            .setMessage(if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_schedule_mock_message)
                else getString(R.string.machine_settings_schedule_real_message))
            .setView(form).setPositiveButton(getString(R.string.machine_settings_schedule_check), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val sh = sleepHour.text.toString().toIntOrNull()
                val sm = sleepMinute.text.toString().toIntOrNull()
                val wh = wakeHour.text.toString().toIntOrNull()
                val wm = wakeMinute.text.toString().toIntOrNull()
                if (sh == null || sh !in 0..23 || wh == null || wh !in 0..23 ||
                    sm == null || sm !in 0..59 || wm == null || wm !in 0..59) {
                    Toast.makeText(this, getString(R.string.machine_settings_invalid_time), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val target = WeeklySleepSchedule(baseline.days.toMutableList().apply {
                    this[index] = WeeklySleepDay(enabled.isChecked, SleepDay(sh, sm, wh, wm))
                })
                dialog.dismiss()
                if (BuildConfig.MOCK_MODE) {
                    AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_schedule_mock_title))
                        .setMessage(String.format(Locale.CHINA,
                            "$name ${if (enabled.isChecked) "启用" else "关闭"}：%02d:%02d 睡眠，%02d:%02d 唤醒。\n模拟值保持不变，不发送蓝牙命令。",
                            sh, sm, wh, wm))
                        .setPositiveButton(getString(R.string.machine_settings_done), null).show()
                    return@setOnClickListener
                }
                AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_schedule_confirm_title))
                    .setMessage(String.format(Locale.CHINA,
                        "$name ${if (enabled.isChecked) "启用" else "关闭"}：%02d:%02d 睡眠，%02d:%02d 唤醒。\n将发送两包计划，并等待机器回报整周内容。",
                        sh, sm, wh, wm))
                    .setPositiveButton(getString(R.string.machine_settings_send)) { _, _ ->
                        service?.changeSleepSchedule(baseline, target)?.let {
                            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.setNegativeButton(getString(R.string.machine_settings_cancel), null).show()
            }
        }
        dialog.show()
    }
    private fun numberInput(parent: LinearLayout, label: String, value: Int): EditText = EditText(this).apply {
        hint = label
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(String.format(Locale.getDefault(), "%d", value))
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun temperatureInput(parent: LinearLayout, hintText: String): EditText = EditText(this).apply {
        hint = hintText
        inputType = InputType.TYPE_CLASS_NUMBER
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(this@MachineSettingsActivity, R.color.mobile_surface, 12, R.color.mobile_border)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun prefill(input: EditText, readback: Int) {
        val current = input.text.toString()
        val suggestion = ReadbackPrefill.next(current, input.tag as? String, readback, input.hasFocus()) ?: return
        if (current != suggestion) input.setText(suggestion)
        input.tag = suggestion
    }
    private fun action(parent: LinearLayout, title: String, onClick: () -> Unit): Button =
        HoyiUi.button(this, parent, title, action = onClick)
    private fun TextView.update(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent: LinearLayout, value: String, size: Int, bold: Boolean = false): TextView = TextView(this).apply {
        text = value
        textSize = size.toFloat()
        setTextColor(getColor(R.color.mobile_text))
        setPadding(0, dp(6), 0, dp(6))
        if (bold) setTypeface(null, Typeface.BOLD)
        parent.addView(this)
    }
    private fun card(parent: LinearLayout, title: String) = HoyiUi.card(this, parent, title)
}
