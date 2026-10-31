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
import android.widget.HorizontalScrollView
import android.widget.TextView
import android.widget.Toast
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.DeviceState
import java.util.Locale

/** Only known setting commands are exposed; applied state requires a subsequent 0x83 readback. */
internal class MachineSettingsControls(private val host: ThemedActivity, suppliedVisual: SettingsVisualStyle? = null) : android.content.ContextWrapper(host) {
    private val visual by lazy {suppliedVisual ?: SettingsVisualStyle(this)}
    val productLayoutEvidence get()=visual.evidence
    private lateinit var brewTemperatureUnit:TextView
    private lateinit var steamTemperatureUnit:TextView
    private val settingsPresentation by lazy { MachineSettingsPresentation(this) }
    private data class ScheduleCard(val heading: TextView, val period: TextView)
    var statusChanged: (String) -> Unit = {}
    private val dialogs = mutableListOf<AlertDialog>()
    private var service: MobileService? = null
    private var bound = false
    private var visible = false
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
            render()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; render() }
        override fun onBindingDied(name: ComponentName) { release(); render() }
    }
    private val refresh = object : Runnable {
        override fun run() { render(); if (visible) handler.postDelayed(this, 500) }
    }

    fun create(body: LinearLayout, savedInstanceState: Bundle?, embedded: Boolean = false) {
        detailsExpanded = savedInstanceState?.getBoolean("settingsExpanded") ?: false
        selectedSettingsSection = savedInstanceState?.getInt("settingsSection") ?: 0
        val style=visual.palette
        text(body, if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_mock_disclaimer)
            else getString(R.string.machine_settings_real_disclaimer), 14).apply {
            val warning=ExtractionHistoryVisualStyle(this@MachineSettingsControls)
            setTextColor(warning.warningInk);setPadding(dp(16),dp(16),dp(16),dp(16))
            background=style.shape(warning.warningSurface,true)
            (layoutParams as LinearLayout.LayoutParams).topMargin=dp(16)
        }
        val overview = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val controlsPane = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val columnsEvidence=org.json.JSONObject();productLayoutEvidence.put("columns",columnsEvidence)
        if (embedded) body.addView(controlsPane, LinearLayout.LayoutParams(-1,-2))
        else body.addView(SettingsColumns(this,style,overview,controlsPane,columnsEvidence),LinearLayout.LayoutParams(-1,-2))
        val summaryCard = card(overview, getString(R.string.machine_settings_connection_title))
        connectionState = text(summaryCard, getString(R.string.machine_settings_disconnected), 16).apply {tag="product/settings/machine/connection"}
        val settingsCard = card(overview, getString(R.string.machine_settings_readback_title))
        fun settingRow(label: Int): TextView {
            settingsDivider(host, settingsCard, visual)
            val key=when(label) {R.string.machine_settings_brew_label->"brew";R.string.machine_settings_steam_label->"steam";R.string.machine_settings_run_mode_label->"run-mode";else->"supply"}
            val name=style.text(getString(label),TaskProductVisualStyle.auxiliary,"product/settings/machine/readback/$key/label",true)
            val value=style.text("—",TaskProductVisualStyle.body,"product/settings/machine/readback/$key/number")
            val quantity=style.row().apply {isBaselineAligned=true;gravity=Gravity.TOP or Gravity.END}
            quantity.addView(value,LinearLayout.LayoutParams(-2,-2))
            if(label in listOf(R.string.machine_settings_brew_label,R.string.machine_settings_steam_label)) {
                val unit=style.text("°C",TaskProductVisualStyle.auxiliary,"product/settings/machine/readback/$key/unit",true)
                quantity.addView(unit,LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(8)})
                unit.visibility=View.GONE
                if(label==R.string.machine_settings_brew_label)brewTemperatureUnit=unit else steamTemperatureUnit=unit
            }
            style.add(settingsCard,visual.pair(name,quantity,"product/settings/machine/readback/$key/row"))
            return value
        }
        brewTemperatureValue = settingRow(R.string.machine_settings_brew_label)
        steamTemperatureValue = settingRow(R.string.machine_settings_steam_label)
        runModeValue = settingRow(R.string.machine_settings_run_mode_label)
        supplyValue = settingRow(R.string.machine_settings_supply_label)
        settingsDivider(host, settingsCard, visual)
        settingsOverview = text(settingsCard, getString(R.string.machine_settings_readback_missing), TaskVisualTokens.SUPPORTING_SP)
        settings = text(settingsCard, "", 14).apply {
            visibility = if (detailsExpanded) View.VISIBLE else View.GONE
        }
        settingsToggle = action(settingsCard, if (detailsExpanded) getString(R.string.machine_settings_readback_collapse) else getString(R.string.machine_settings_readback_expand)) {
            detailsExpanded = !detailsExpanded
            settings.visibility = if (detailsExpanded) View.VISIBLE else View.GONE
            settingsToggle.text = if (detailsExpanded) getString(R.string.machine_settings_readback_collapse) else getString(R.string.machine_settings_readback_expand)
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visual.action(this,icon="chevron",atEnd=true)
        }
        action(settingsCard, getString(R.string.app_settings_title)) {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }.apply { asTaskTextAction(); gravity = Gravity.START or Gravity.CENTER_VERTICAL
            visual.action(this,icon="chevron",atEnd=true)
        }
        val writeCard = card(controlsPane, getString(R.string.machine_settings_feedback_title))
        writeStatus = text(writeCard, getString(R.string.machine_settings_feedback_initial), 16).apply {tag="product/settings/machine/write-status"}
        val tabsScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        controlsPane.addView(tabsScroll, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
        val tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabsScroll.addView(tabs)
        fun section(label: String): LinearLayout {
            val index = settingSections.size
            val tab = TextView(this).apply {
                text = label
                style.apply(this,TaskProductVisualStyle.action)
                gravity = Gravity.CENTER
                minHeight = dp(48)
                isFocusable = true
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
        compactTemperatureRows(controls, listOf(brewInput, compensationInput, steamInput),listOf("brew","compensation","steam"),
            listOf(R.string.m8_brew_label,R.string.m8_compensation_label,R.string.m8_steam_label),
            listOf(R.string.m8_brew_range,R.string.m8_compensation_range,R.string.m8_steam_range))
        visual.palette.add(controls,visual.palette.text(getString(R.string.machine_settings_readback_warning),TaskProductVisualStyle.auxiliary,secondary=true),16)
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
        compactTemperatureRows(standbyCard,listOf(standbyTemperatureInput),listOf("standby"),
            listOf(R.string.m8_standby_label),listOf(R.string.m8_standby_range))
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
                    setPadding(dp(16), dp(8), dp(16), dp(8))
                    background = style.shape(style.selected)
                }
                row.addView(cell, LinearLayout.LayoutParams(0, -2, 1f).apply {
                    topMargin = dp(8)
                    if (day != days.last()) marginEnd = dp(8)
                })
                val heading = style.text("",TaskProductVisualStyle.action).also {style.add(cell,it)}
                val period = style.text("",TaskProductVisualStyle.auxiliary,secondary=true).also {style.add(cell,it,8)}
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
        listOf(brewInput, compensationInput, steamInput, standbyTemperatureInput).forEachIndexed { index, input ->
            savedInstanceState?.getString("machineDraft$index")?.let { input.setText(it) }
            input.tag = savedInstanceState?.getString("machineReadback$index")
        }
        if (embedded) {
            overview.visibility = View.GONE
            tabsScroll.visibility = View.GONE
            settingSections.forEachIndexed { index, section -> section.visibility = if (index < 2) View.VISIBLE else View.GONE }
            listOf(R.string.machine_settings_tab_standby, R.string.machine_settings_tab_schedule, R.string.machine_settings_tab_maintenance).forEachIndexed { index, label ->
                action(body, getString(label)) {
                    host.startActivity(Intent(host, MachineSettingsActivity::class.java).putExtra("section", index + 2))
                }
            }
        } else showSettingsSection(savedInstanceState?.getInt("settingsSection") ?: host.intent.getIntExtra("section", 0))
        render()
    }

    fun start() {
        visible = true
        if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0)
        handler.post(refresh)
    }
    fun stop() {
        dialogs.forEach { it.dismiss() }; dialogs.clear()
        visible = false
        handler.removeCallbacks(refresh)
        release()
    }
    fun save(outState: Bundle) {
        outState.putBoolean("settingsExpanded", detailsExpanded)
        outState.putInt("settingsSection", selectedSettingsSection)
        listOf(brewInput, compensationInput, steamInput, standbyTemperatureInput).forEachIndexed { index, input ->
            outState.putString("machineDraft$index", input.text.toString())
            outState.putString("machineReadback$index", input.tag as? String)
        }
    }
    private fun showSettingsSection(index: Int) {
        selectedSettingsSection = index.coerceIn(settingSections.indices)
        settingSections.forEachIndexed { position, section ->
            val selected = position == selectedSettingsSection
            section.visibility = if (selected) View.VISIBLE else View.GONE
            settingTabs[position].apply {
                visual.palette.apply(this,if(selected)TaskProductVisualStyle.action else TaskProductVisualStyle.body,!selected)
                minimumHeight = dp(48); minHeight = dp(48)
                setTextColor(if(selected)visual.palette.accent else visual.palette.muted)
                isSelected = selected
                typeface = TaskTypography.face(context, if (selected) 500 else 400)
                val baseline = GradientDrawable().apply { setColor(visual.palette.border) }
                val indicator = GradientDrawable().apply {
                    setColor(if(selected)visual.palette.accent else visual.palette.border)
                    cornerRadius = dp(2).toFloat()
                }
                background = android.graphics.drawable.LayerDrawable(arrayOf(baseline, indicator)).apply {
                    setLayerHeight(0, dp(1))
                    setLayerGravity(0, Gravity.BOTTOM)
                    setLayerHeight(1, dp(if (selected) 3 else 1))
                    setLayerGravity(1, Gravity.BOTTOM)
                    setLayerInset(1, dp(8), 0, dp(8), 0)
                }
            }
        }
    }
    private fun release() {
        if (bound) { unbindService(connection); bound = false }
        service = null
    }
    private fun render() {
        try { renderContent() } finally {
            if (::connectionState.isInitialized && ::writeStatus.isInitialized) statusChanged(listOf(connectionState.text, settingsOverview.text, writeStatus.text).joinToString("\n"))
        }
    }
    private fun renderContent() {
        if (!::connectionState.isInitialized) return
        val owner = service
        val snapshot = owner?.snapshot ?: MobileSnapshot()
        val ready = snapshot.coffeeState == DeviceState.READY
        connectionState.update(if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_mock_connection) else
            getString(R.string.machine_settings_connection_summary,
                DeviceStatusText.label(this, snapshot.coffeeState), getString(if (ready)
                    R.string.machine_settings_authenticated else R.string.machine_settings_not_current)))
        val reported = snapshot.settings
        settingsOverview.update(if (reported == null) getString(R.string.machine_settings_readback_missing) else
            getString(R.string.machine_settings_readback_summary,
                getString(if (ready && owner?.machineSettingsFresh == true)
                    R.string.machine_settings_readback_current else R.string.machine_settings_readback_stale),
                if (reported.standbyMinutes == 0) getString(R.string.machine_settings_standby_never)
                    else getString(R.string.machine_settings_minutes, reported.standbyMinutes.toString()),
                reported.cupCount.toString()))
        brewTemperatureValue.update(reported?.let { it.brewTemperatureC.toString() } ?: "—")
        steamTemperatureValue.update(reported?.let { it.steamTemperatureC.toString() } ?: "—")
        brewTemperatureUnit.visibility=if(reported==null)View.GONE else View.VISIBLE
        steamTemperatureUnit.visibility=if(reported==null)View.GONE else View.VISIBLE
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
        settings.update(settingsPresentation.settings(snapshot.settings))
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
        settingsPresentation.scheduleDaySummaries(snapshot.sleepFirst, snapshot.sleepSecond)
            .forEachIndexed { index, day ->
                scheduleCards[index].heading.update("${day.name} · ${day.state}")
                scheduleCards[index].heading.setTextColor(if(day.enabled==true)visual.palette.accent else visual.palette.ink)
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
            getString(R.string.machine_settings_feedback_initial) else "${owner?.pendingSetting?.let(settingsPresentation::change) ?: getString(R.string.machine_settings_generic_setting)} · " +
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
        val editable = MachineSettingsEditGate.allowed(owner, snapshot, now)
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
                .setMessage(getString(R.string.machine_settings_mock_confirm_message, settingsPresentation.change(change)))
                .setPositiveButton(getString(R.string.machine_settings_done), null).show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
            return
        }
        AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_confirm_title))
            .setMessage(settingsPresentation.change(change) +
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
            .setNegativeButton(getString(R.string.machine_settings_cancel), null).show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
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
        visual.input(input)
        val viewport=SettingsDialogViewport(this).apply {addView(input)}
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_cup_check_title))
            .setMessage(getString(R.string.machine_settings_cup_check_message))
            .setView(viewport).setPositiveButton(getString(R.string.machine_settings_next), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
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
                    }.setNegativeButton(getString(R.string.machine_settings_cancel), null).show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
            }
        }
        dialog.show()
        visual.dialog(dialog,viewport)
        AppDialog.styleShown(dialog); dialogs += dialog
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
            }.show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
    }
    private fun editScheduleDay(index: Int, name: String) {
        val baseline = service?.snapshot?.let {
            WeeklySleepSchedule.fromReadback(it.sleepFirst, it.sleepSecond)
        } ?: return
        val previous = baseline.days[index]
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val enabled = CheckBox(this).apply {
            text = getString(R.string.machine_settings_day_enabled); isChecked = previous.enabled
            visual.palette.apply(this,TaskProductVisualStyle.body);minimumHeight=dp(48);minHeight=dp(48)
            form.addView(this)
        }
        val sleepHour = numberInput(form, getString(R.string.machine_settings_sleep_hour), previous.time.sleepHour)
        val sleepMinute = numberInput(form, getString(R.string.machine_settings_sleep_minute), previous.time.sleepMinute)
        val wakeHour = numberInput(form, getString(R.string.machine_settings_wake_hour), previous.time.wakeHour)
        val wakeMinute = numberInput(form, getString(R.string.machine_settings_wake_minute), previous.time.wakeMinute)
        val viewport=SettingsDialogViewport(this).apply {tag="product/settings/machine/schedule/dialog-scroll";addView(form)}
        val dialog = AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_schedule_day_title, name))
            .setMessage(if (BuildConfig.MOCK_MODE) getString(R.string.machine_settings_schedule_mock_message)
                else getString(R.string.machine_settings_schedule_real_message))
            .setView(viewport).setPositiveButton(getString(R.string.machine_settings_schedule_check), null).setNegativeButton(getString(R.string.machine_settings_cancel), null).create()
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
                            getString(R.string.machine_settings_schedule_preview_mock),
                            name, getString(if (enabled.isChecked) R.string.machine_settings_preview_enabled
                                else R.string.setting_off), sh, sm, wh, wm))
                        .setPositiveButton(getString(R.string.machine_settings_done), null).show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
                    return@setOnClickListener
                }
                AlertDialog.Builder(this).setTitle(getString(R.string.machine_settings_schedule_confirm_title))
                    .setMessage(String.format(Locale.CHINA,
                        getString(R.string.machine_settings_schedule_preview_real),
                        name, getString(if (enabled.isChecked) R.string.machine_settings_preview_enabled
                            else R.string.setting_off), sh, sm, wh, wm))
                    .setPositiveButton(getString(R.string.machine_settings_send)) { _, _ ->
                        service?.changeSleepSchedule(baseline, target)?.let {
                            Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                        }
                        render()
                    }.setNegativeButton(getString(R.string.machine_settings_cancel), null).show().also { visual.dialog(it); AppDialog.styleShown(it); dialogs += it }
            }
        }
        dialog.show()
        visual.dialog(dialog,viewport)
        AppDialog.styleShown(dialog); dialogs += dialog
    }
    private fun numberInput(parent: LinearLayout, label: String, value: Int): EditText = EditText(this).apply {
        id=View.generateViewId()
        visual.palette.text(label,TaskProductVisualStyle.body).also {it.labelFor=id;visual.palette.add(parent,it,16)}
        contentDescription=label
        hint = label
        inputType = InputType.TYPE_CLASS_NUMBER
        setText(String.format(Locale.getDefault(), "%d", value))
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(host, R.color.mobile_surface, 12, R.color.mobile_border)
        visual.input(this)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply {topMargin=dp(8)})
    }
    private fun temperatureInput(parent: LinearLayout, hintText: String): EditText = EditText(this).apply {
        id = View.generateViewId()
        val fieldId = id
        HoyiUi.label(host, parent, hintText, TaskVisualTokens.SUPPORTING_SP, muted = true).apply {
            labelFor = fieldId
            setPadding(0, dp(8), 0, dp(8))
        }
        hint = hintText;contentDescription=hintText
        textSize = TaskVisualTokens.BODY_SP.toFloat()
        typeface = TaskTypography.face(this@MachineSettingsControls, 400)
        inputType = InputType.TYPE_CLASS_NUMBER
        minHeight = dp(48)
        setPadding(dp(14), dp(8), dp(14), dp(8))
        background = HoyiUi.shape(host, R.color.mobile_surface, 12, R.color.mobile_border)
        visual.input(this)
        parent.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun compactTemperatureRows(parent:LinearLayout,inputs:List<EditText>,keys:List<String>,labels:List<Int>,ranges:List<Int>) {
        inputs.forEachIndexed {index,input->
            val inputIndex=parent.indexOfChild(input)
            val button=parent.getChildAt(inputIndex+1) as Button
            val label=parent.getChildAt(inputIndex-1) as TextView
            check(label.labelFor==input.id)
            parent.removeView(label);parent.removeView(input);parent.removeView(button)
            label.text=getString(labels[index]);label.setPadding(0,0,0,0)
            visual.palette.apply(label,TaskProductVisualStyle.body)
            val words=visual.palette.column()
            visual.palette.add(words,label)
            visual.palette.add(words,visual.palette.text(getString(ranges[index]),TaskProductVisualStyle.auxiliary,secondary=true),8)
            val quantity=visual.palette.row("product/settings/machine/${keys[index]}/quantity").apply {isBaselineAligned=true;gravity=Gravity.TOP}
            quantity.addView(input,LinearLayout.LayoutParams(dp(96),-2))
            quantity.addView(visual.palette.text("°C",TaskProductVisualStyle.auxiliary,"product/settings/machine/${keys[index]}/unit",true),
                LinearLayout.LayoutParams(-2,-2).apply {marginStart=dp(8)})
            button.tag="product/settings/machine/${keys[index]}/action"
            val rowEvidence=org.json.JSONObject();productLayoutEvidence.put(keys[index],rowEvidence)
            val row=SettingsTemperatureRow(this,visual.palette,words,quantity,input,button,rowEvidence)
                .apply {tag="product/settings/machine/${keys[index]}/row"}
            parent.addView(row,inputIndex-1,LinearLayout.LayoutParams(-1,-2).apply {topMargin=dp(8)})
        }
    }

    private fun prefill(input: EditText, readback: Int) {
        val current = input.text.toString()
        val suggestion = ReadbackPrefill.next(current, input.tag as? String, readback, input.hasFocus()) ?: return
        if (current != suggestion) input.setText(suggestion)
        input.tag = suggestion
    }
    private fun action(parent: LinearLayout, title: String, onClick: () -> Unit): Button =
        HoyiUi.button(host, parent, title, action = onClick).also {visual.action(it,outline=true)}
    private fun TextView.update(value: String) { if (text.toString() != value) text = value }
    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()
    private fun text(parent:LinearLayout,value:String,size:Int,bold:Boolean=false):TextView =
        visual.palette.text(value,if(bold)TaskProductVisualStyle.section else if(size<=14)TaskProductVisualStyle.auxiliary else TaskProductVisualStyle.body,
            secondary=size<=14).also {visual.palette.add(parent,it,8)}
    private fun card(parent:LinearLayout,title:String)=visual.card(parent,title)
}
