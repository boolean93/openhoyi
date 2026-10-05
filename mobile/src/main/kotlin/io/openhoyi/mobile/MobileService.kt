package io.openhoyi.mobile

import io.openhoyi.session.ShotRecoveryState
import io.openhoyi.session.MachineRecoveryActivity
import io.openhoyi.session.MachineWriteRegistration
import io.openhoyi.session.MachineWriteAcknowledgement
import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.PassiveShotDetector
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.SleepScheduleWriteTracker
import io.openhoyi.session.StandaloneTare
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.BookooSample
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.CoffeeAuthentication
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
import io.openhoyi.session.TelemetryFreshness
import io.openhoyi.session.SettingsFreshness
import io.openhoyi.session.ScaleReadingPolicy
import io.openhoyi.session.SleepScheduleFreshness
import io.openhoyi.trace.TraceStore
import java.time.LocalDateTime

data class MobileSnapshot(
    val coffeeState: DeviceState = DeviceState.DISCONNECTED,
    val scaleState: DeviceState = DeviceState.DISCONNECTED,
    val coffee: HoyiMessage? = null,
    val coffeeAt: Long? = null,
    val alarmBits: Int? = null,
    val alarmAt: Long? = null,
    val settings: Settings? = null,
    val settingsAt: Long? = null,
    val sleepFirst: SleepPart? = null,
    val sleepSecond: SleepPart? = null,
    val sleepFirstAt: Long? = null,
    val sleepSecondAt: Long? = null,
    val weight: BookooSample? = null,
    val weightAt: Long? = null,
    val candidates: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val message: SnapshotMessage? = null,
) {
    /** Null means no event yet; an explicit empty event must not be replaced. */
    fun messageForDisplay(text: (Int, Array<out Any?>) -> String): String =
        message?.render(text) ?: text(R.string.device_initial_message, emptyArray())
}

/** Product-app BLE owner. Screens observe snapshots; explicit controls remain gated in this service. */
class MobileService : Service() {
    // Resource reads follow the process language authority without restarting this service or BLE.
    override fun getResources(): android.content.res.Resources =
        (application as? MobileApplication)?.resources ?: super.getResources()

    private val notificationDisplay by lazy { MobileNotificationDisplay(this) }
    private val studioStartGate by lazy { StudioStartGate(this) }
    private val brewWaitCancelGate by lazy { BrewWaitCancelGate(this) }
    private val shotRecoveryClearGate by lazy { ShotRecoveryClearGate(this) }
    private val shotGate by lazy { ShotGate(this) }
    private val settingsPresentation by lazy { MachineSettingsPresentation(this) }
    inner class LocalBinder : Binder() { val service: MobileService get() = this@MobileService }
    private val binder = LocalBinder()
    private lateinit var logs: TraceStore
    private var history: ShotHistory? = null
    private val series = ShotSeries()
    private val brewFeedback = BrewFeedbackTracker()
    private val feedbackDelivery = BrewFeedbackDelivery()
    private var feedbackShotId: String? = null
    private var feedbackAppShot = true
    internal var brewFeedbackResult: BrewFeedbackResult? = null
        private set
    private val passiveShot = PassiveShotDetector()
    private var passiveHistoryId: String? = null
    private var passiveMayClearRecovery = false
    val manualShotActive: Boolean get() = passiveShot.active
    private val shotRecovery by lazy {
        val prefs = getSharedPreferences("shot_safety", MODE_PRIVATE)
        ShotRecoveryState(object : ShotRecoveryState.Storage {
            override fun read(): ShotRecoveryState.Record = ShotRecoveryState.Record(
                prefs.getBoolean("unresolved_shot", false), prefs.getString("unresolved_shot_address", null))
            override fun write(record: ShotRecoveryState.Record): Boolean =
                prefs.edit().putBoolean("unresolved_shot", record.pending)
                    .putString("unresolved_shot_address", record.address).commit()
        })
    }
    private val restartShotWarning: String get() = getString(R.string.machine_recovery_shot_restart)
    private val machineWriteRecovery by lazy {
        val prefs = getSharedPreferences("machine_write_safety", MODE_PRIVATE)
        MachineWriteRecoveryState(object : MachineWriteRecoveryState.Storage {
            override fun read(): MachineWriteRecoveryState.Record {
                val kind = prefs.getString("pending_kind", null)?.let { raw ->
                    runCatching { MachineWriteRecoveryState.Kind.valueOf(raw) }
                        .getOrDefault(MachineWriteRecoveryState.Kind.UNKNOWN)
                }
                return MachineWriteRecoveryState.Record(kind, prefs.getString("pending_address", null))
            }
            override fun write(record: MachineWriteRecoveryState.Record): Boolean =
                prefs.edit().putString("pending_kind", record.kind?.name)
                    .putString("pending_address", record.address).commit()
        })
    }
    private val machineWriteSafetyResource: Int? get() = MachineRecoveryText.resource(
        machineWriteRecovery.kind, brewPreparation.state)
    val machineWriteSafetyMessage: String? get() = machineWriteSafetyResource?.let { getString(it) }
    val machineControlSafetyMessage: String? get() = MachineControlGate.block(
        shotRecovery.pending, machineWriteSafetyMessage, restartShotWarning)
    val shotRecoveryClearBlock: String? get() = shotRecoveryClearResource?.let { getString(it) }
    private val shotRecoveryClearResource: Int? get() = shotRecoveryClearGate.resource(shotRecovery,
        hub?.coffeeAddress, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
        SystemClock.elapsedRealtime(), shotState, manualShotActive)
    val machineWriteAcknowledgementAvailable: Boolean get() {
        val kind = machineWriteRecovery.kind
        val brewWait = kind == MachineWriteRecoveryState.Kind.BREW_WAIT
        val pendingShot = brewWait && shotRecovery.pending
        return MachineRecoveryActivity.available(kind, cupReset.state, settingsWrite.state,
            scheduleWrite.state, sleepNow.state, brewPreparation.state, pendingShot,
            brewWait && !pendingShot && ShotGate.active(shotState))
    }
    val machineWriteRecoveryKind: MachineWriteRecoveryState.Kind? get() = machineWriteRecovery.kind
    private var recoveryAfterSettingsSerial = 0L
    private var recoveryAfterIdleSerial = 0L
    private var recoveryAfterFirstSleepSerial = 0L
    private var recoveryAfterSecondSleepSerial = 0L
    private var recoveryAfterSleepSerial = 0L
    private val settingsWrite = SettingsWriteTracker()
    private val cupReset = CupResetTracker()
    val cupResetState: CupResetTracker.State get() = if (mock?.cupReset == true)
        CupResetTracker.State.CONFIRMED else cupReset.state
    private val cupResetBusy: Boolean get() = MachineRecoveryActivity.isBusy(cupReset.state)
    private var cupSettingsSerial = 0L
    private var cupIdleSerial = 0L
    private fun observeCupCount(settingsFrame: Boolean, count: Int) {
        val previous = cupReset.state
        val confirmed = if (settingsFrame) cupReset.observeSettings(++cupSettingsSerial, count)
            else cupReset.observeIdle(++cupIdleSerial, count)
        if (cupReset.state == CupResetTracker.State.CONFIRMED &&
            machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.CUP_RESET &&
            !machineWriteRecovery.clear())
            event(ResourceMessage(R.string.service_event_cups_clear_failed), "cups.recovery_clear_failed")
        refreshSafetyNotification()
        if (confirmed) event(ResourceMessage(R.string.service_event_cups_confirmed), "cups.confirmed")
        else if (previous == CupResetTracker.State.UNKNOWN && cupReset.state == CupResetTracker.State.RECONCILED)
            event(ResourceMessage(R.string.service_event_cups_reconciled), "cups.reconciled")
    }
    private val scheduleWrite = SleepScheduleWriteTracker()
    private val sleepNow = SleepNowTracker()
    private val brewPreparation = BrewPreparation()
    private var idleSampleSerial = 0L
    private var recoveryAfterBrewWaitIdleSerial = 0L
    private var brewWaitShotStarted = false
    val brewPreparationState: BrewPreparation.State get() = brewPreparation.state
    val brewPreparationProfileId: String? get() = brewPreparation.profileId
    val brewPreparationTargetC: Int? get() = brewPreparation.targetC
    val brewWaitCancelBlock: String? get() = brewWaitCancelBlockMessage?.render { id, args -> getString(id, *args) }
    private val brewWaitCancelBlockMessage: ResourceMessage? get() = if (mock != null) null else
        brewWaitCancelGate.blockMessage(snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            SystemClock.elapsedRealtime(), shotState, shotRecovery.pending)
    private var sleepSampleSerial = 0L
    private val sleepNowUnresolved: Boolean get() = sleepNow.state == SleepNowTracker.State.UNKNOWN
    private val sleepNowUnresolvedMessage: String get() = getString(R.string.machine_recovery_sleep_unresolved)
    val sleepNowState: SleepNowTracker.State get() = if (mock?.isSleeping == true)
        SleepNowTracker.State.CONFIRMED else sleepNow.state
    private var settingsSampleSerial = 0L
    private val settingWriteUnresolved: Boolean get() = settingsWrite.state == SettingsWriteTracker.State.UNKNOWN
    private val settingWriteUnresolvedMessage: String get() = getString(R.string.service_setting_unresolved)
    val settingWriteState: SettingsWriteTracker.State get() = if (mock?.lastSetting != null)
        SettingsWriteTracker.State.CONFIRMED else settingsWrite.state
    val pendingSetting: MachineSettingChange? get() = mock?.lastSetting ?: settingsWrite.change
    val scheduleWriteState: SleepScheduleWriteTracker.State get() = if (mock?.scheduleChanged == true)
        SleepScheduleWriteTracker.State.CONFIRMED else scheduleWrite.state
    val pendingSchedule: WeeklySleepSchedule? get() = scheduleWrite.target
    private val scheduleBusy: Boolean get() = MachineRecoveryActivity.isBusy(scheduleWrite.state)
    private var firstSleepSerial = 0L
    private var secondSleepSerial = 0L
    val tareState: StandaloneTare.State get() = if (mock?.tareChanged == true)
        StandaloneTare.State.CONFIRMED else hub?.scaleTareState ?: StandaloneTare.State.IDLE
    val chartPoints: List<ShotPoint> get() = series.points
    private val ownerId = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private var hub: NativeDeviceHub? = null
    private val coffeeCredentials by lazy { CoffeeCredentialStore(this) }
    private val coffeeCredentialRetries by lazy {
        CoffeeCredentialRetryGate(store = SharedPreferencesCoffeeFailureStore(this))
    }
    private data class PendingCoffeeCredential(val address: String, val password: String, val remembered: Boolean)
    private var pendingCoffeeCredential: PendingCoffeeCredential? = null
    private val mock = if (BuildConfig.MOCK_MODE) MockDeviceRuntime { getString(it) } else null
    private fun refreshMock(runtime: MockDeviceRuntime, now: Long = SystemClock.elapsedRealtime()) {
        val previous = snapshot
        snapshot = runtime.sample(now).copy(candidates = previous.candidates,
            scanning = previous.scanning, message = previous.message)
    }
    private val mockTick = object : Runnable {
        override fun run() {
            val runtime = mock ?: return
            if (!running) return
            val now = SystemClock.elapsedRealtime()
            refreshMock(runtime, now)
            (snapshot.coffee as? IdleTelemetry)?.let { idle ->
                val currentSettings = snapshot.settings
                if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                        BrewPreparation.correctedTemperature(idle.brewTemperatureHundredthsC,
                            currentSettings.brewCompensationTenthsC)))
                    event(ResourceMessage(R.string.service_event_mock_temperature_ready), "mock.brew_wait_ready")
            }
            (snapshot.coffee as? io.openhoyi.protocol.ExtractionTelemetry)?.let { frame ->
                observeBrewFeedback(frame, now)
                series.machine(frame, now, snapshot.weight?.weightHundredthsGram,
                    snapshot.weightAt, snapshot.weight?.deviceFlowHundredths)
                saveSeriesCheckpoint(now)
            }
            handler.postDelayed(this, 250)
        }
    }
    private val appVisibility: AppVisibility get() = (application as MobileApplication).visibility
    private var hubForeground = false
    private var safetyMessage: String? = null
    private var manualSafetyResource: Int? = null
    val manualSafetyMessage: String? get() = manualSafetyResource?.let { getString(it) }
    private var automaticScaleOnly = false
    private val stopAutomaticScale = object : Runnable {
        override fun run() {
            if (!automaticScaleOnly || !running) return
            if (snapshot.coffeeState == DeviceState.READY || snapshot.scaleState == DeviceState.READY ||
                snapshot.scanning || snapshot.scaleState !in setOf(DeviceState.DISCONNECTED, DeviceState.FAILED)) {
                handler.postDelayed(this, 30_000)
            } else {
                shutdown()
                if (running) handler.postDelayed(this, 30_000)
            }
        }
    }
    private fun manualDeviceUse() {
        automaticScaleOnly = false
        handler.removeCallbacks(stopAutomaticScale)
    }
    private fun scheduleAutomaticScaleStop() {
        if (!automaticScaleOnly) return
        handler.removeCallbacks(stopAutomaticScale)
        handler.postDelayed(stopAutomaticScale, 600_000)
    }
    private val leaveForeground = object : Runnable {
        override fun run() {
            if (appVisibility.visible || !hubForeground) return
            hub?.background()
            hubForeground = false
            snapshot = snapshot.copy(scanning = false)
            if (automaticScaleOnly && snapshot.coffeeState != DeviceState.READY &&
                snapshot.scaleState in setOf(DeviceState.DISCONNECTED, DeviceState.FAILED)) shutdown()
        }
    }
    private var lastShotState = ExtractionState.IDLE
    var running = false; private set
    var snapshot = MobileSnapshot(); private set
    val coffeeFirmware: CoffeeFirmware? get() = if(mock != null)
        snapshot.settings?.let { CoffeeFirmware(it.firmwareMajor,it.firmwareMinor,it.firmwarePatch) }
        else hub?.coffeeFirmware
    val machineSettingsFresh: Boolean get() = snapshot.settings != null &&
        SettingsFreshness.isFresh(snapshot.settingsAt, SystemClock.elapsedRealtime())
    val sleepScheduleFresh: Boolean get() = SleepScheduleFreshness.isFresh(
        snapshot.sleepFirst, snapshot.sleepFirstAt, snapshot.sleepSecond, snapshot.sleepSecondAt,
        SystemClock.elapsedRealtime())
    val shotState: ExtractionState get() = mock?.shotState ?: hub?.extraction?.state ?: ExtractionState.IDLE
    var activeShotTargetHundredthsGram: Int? = null; private set
    val stopReason: String? get() = hub?.extraction?.stopReason?.name
    val scalePreflight: Boolean get() = hub?.extraction?.preparingScale == true
    val tareStartBlock: String? get() = when(tareState) {
        StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO -> getString(R.string.service_tare_start_wait)
        StandaloneTare.State.UNKNOWN -> getString(R.string.service_tare_start_unknown)
        else -> null
    }
    private var lastTareState = StandaloneTare.State.IDLE
    private fun observeTare() {
        val current = tareState
        if(current == lastTareState) return
        lastTareState = current
        if(current == StandaloneTare.State.CONFIRMED) event(ResourceMessage(R.string.service_tare_confirmed), "scale.tare_confirmed")
        if(current == StandaloneTare.State.UNKNOWN) event(ResourceMessage(R.string.service_tare_unknown), "scale.tare_unknown")
    }
    private val watchShot = object : Runnable {
        override fun run() {
            observeTare()
            val current = shotState
            if (current != lastShotState) {
                val previous = lastShotState
                lastShotState = current
                val now = SystemClock.elapsedRealtime()
                val weight = snapshot.weight?.weightHundredthsGram?.takeIf {
                    snapshot.scaleState == DeviceState.READY &&
                        TelemetryFreshness.isFresh(snapshot.weightAt, now)
                }
                runCatching { history?.transition(current, stopReason, weight) }
                    .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
                if (current == ExtractionState.ENDED_OBSERVED || current == ExtractionState.IDLE) {
                    finishSeries(current == ExtractionState.ENDED_OBSERVED)
                    val shotRecordCleared = shotRecovery.clear()
                    if (!shotRecordCleared)
                        manualSafetyResource = R.string.service_event_shot_clear_failed
                    if (current == ExtractionState.ENDED_OBSERVED && shotRecordCleared && brewWaitShotStarted &&
                        machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT &&
                        machineWriteRecovery.matchesDevice(hub?.coffeeAddress)) {
                        if (!machineWriteRecovery.clear())
                            event(ResourceMessage(R.string.service_event_preheat_clear_failed), "brew_wait.recovery_clear_failed")
                        brewWaitShotStarted = false
                    }
                }
                if (current == ExtractionState.OUTCOME_UNKNOWN) {
                    saveSeriesCheckpoint(force = true)
                    finishBrewFeedback(false)
                }
                event(ResourceMessage(R.string.service_event_shot_state, current.name), "shot.state")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.TARE_UNCONFIRMED.name)
                    event(ResourceMessage(R.string.service_event_tare_unconfirmed), "shot.preflight_failed")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.START_CONDITIONS_CHANGED.name)
                    event(ResourceMessage(R.string.service_event_conditions_changed), "shot.conditions_changed")
                refreshSafetyNotification()
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MobileApplication
        app.feedbackPreferences.observe(ownerId) { enabled -> if (!enabled) feedbackDelivery.clear() }
        logs = app.logs
        history = runCatching { app.history }.getOrNull()
        if (mock == null && shotRecovery.pending) manualSafetyResource = R.string.machine_recovery_shot_restart
        handler.post(watchShot)
        app.visibility.observe(ownerId, ::onAppVisibilityChanged)
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { shutdown(); return START_NOT_STICKY }
        if (running) {
            if (intent?.action != AUTO_SCALE) manualDeviceUse()
            return START_NOT_STICKY
        }
        if (mock != null) {
            running = true
            snapshot = mock.sample(SystemClock.elapsedRealtime())
            handler.post(mockTick)
            event(ResourceMessage(R.string.service_event_mock_started), "mock.started")
            return START_NOT_STICKY
        }
        automaticScaleOnly = intent?.action == AUTO_SCALE
        safetyMessage = null
        try {
            val manager = getSystemService(NotificationManager::class.java)
            createNotificationChannels(manager)
            val note = connectionNotification(null)
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, note)
            val prefs = getSharedPreferences("devices", MODE_PRIVATE)
            hub = NativeDeviceHub(applicationContext, prefs.getString("scale", null),
                onScaleRemembered = { prefs.edit().putString("scale", it).apply() },
                onState = { role, state ->
                    snapshot = if (role == DeviceRole.COFFEE) {
                        if (state != DeviceState.READY) {
                            finishBrewFeedback(false)
                            saveSeriesCheckpoint(force = true)
                            settingsWrite.disconnected(settingsSampleSerial)
                            cupReset.disconnected(cupSettingsSerial, cupIdleSerial)
                            scheduleWrite.disconnected(firstSleepSerial, secondSleepSerial)
                            sleepNow.disconnected(sleepSampleSerial)
                            brewPreparation.disconnected()
                        }
                        if (state == DeviceState.DISCONNECTED || state == DeviceState.FAILED)
                            snapshot.copy(coffeeState = state, coffee = null, coffeeAt = null,
                                alarmBits = null, alarmAt = null,
                                settings = null, settingsAt = null, sleepFirst = null, sleepSecond = null, sleepFirstAt = null, sleepSecondAt = null)
                        else snapshot.copy(coffeeState = state)
                    } else if (state != DeviceState.READY) {
                        snapshot.copy(scaleState = state, weight = null, weightAt = null)
                    }
                    else snapshot.copy(scaleState = state)
                    if (role == DeviceRole.COFFEE) when (state) {
                        DeviceState.READY -> pendingCoffeeCredential?.let { credential ->
                            if (!credential.remembered && !coffeeCredentials.save(credential.address, credential.password))
                                event(ResourceMessage(R.string.service_event_credential_save_failed), "coffee.credential_save_failed")
                            coffeeCredentialRetries.connectionState(credential.address, state, credential.remembered)
                            pendingCoffeeCredential = null
                        }
                        DeviceState.FAILED, DeviceState.UNSUPPORTED -> pendingCoffeeCredential?.let { credential ->
                            coffeeCredentialRetries.connectionState(credential.address, state, credential.remembered)
                            pendingCoffeeCredential = null
                        }
                        else -> Unit
                    }
                    if (role == DeviceRole.COFFEE && state != DeviceState.READY &&
                        passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
                        passiveHistoryId?.let { id ->
                            runCatching { history?.abandon(id, "连接中断") }
                                .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                        }
                        passiveHistoryId = null
                        passiveMayClearRecovery = false
                        saveSeriesCheckpoint(force = true)
                        finishSeries(false)
                        manualSafetyResource = R.string.service_event_manual_disconnect_warning
                        event(ResourceMessage(R.string.service_event_manual_unknown), "shot.passive_unknown")
                    }
                    event("${role.name}: ${state.name}")
                    if (role == DeviceRole.COFFEE) refreshSafetyNotification()
                },
                onCoffee = { frame ->
                    snapshot = when (frame) {
                        is Settings -> {
                            observeCupCount(true, frame.cupCount)
                            val previousSettingState = settingsWrite.state
                            if (settingsWrite.observe(++settingsSampleSerial, frame))
                                event(ResourceMessage(R.string.service_event_setting_confirmed), "settings.confirmed")
                            else if (previousSettingState == SettingsWriteTracker.State.UNKNOWN &&
                                settingsWrite.state == SettingsWriteTracker.State.RECONCILED)
                                event(ResourceMessage(R.string.service_event_setting_reconciled), "settings.reconciled")
                            if (settingsWrite.state == SettingsWriteTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SETTING) {
                                if (!machineWriteRecovery.clear())
                                    event(ResourceMessage(R.string.service_write_setting_clear_failed), "settings.recovery_clear_failed")
                                refreshSafetyNotification()
                            }
                            snapshot.copy(settings = frame, settingsAt = SystemClock.elapsedRealtime())
                        }
                        is SleepPart -> {
                            if (frame.firstDaySundayIndex == 0) {
                                firstSleepSerial++
                                snapshot = snapshot.copy(sleepFirst = frame, sleepFirstAt = SystemClock.elapsedRealtime(), sleepSecondAt = null)
                            } else {
                                secondSleepSerial++
                                snapshot = snapshot.copy(sleepSecond = frame, sleepSecondAt = SystemClock.elapsedRealtime())
                            }
                            if (sleepScheduleFresh && scheduleWrite.observe(firstSleepSerial, secondSleepSerial,
                                    snapshot.sleepFirst, snapshot.sleepSecond)) {
                                if (scheduleWrite.state == SleepScheduleWriteTracker.State.CONFIRMED)
                                    event(ResourceMessage(R.string.service_write_schedule_confirmed), "sleep_schedule.confirmed")
                                else event(ResourceMessage(R.string.service_event_schedule_reconciled), "sleep_schedule.reconciled")
                            }
                            if (scheduleWrite.state == SleepScheduleWriteTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
                                if (!machineWriteRecovery.clear())
                                    event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                                refreshSafetyNotification()
                            }
                            snapshot
                        }
                        is IdleTelemetry -> {
                            observeCupCount(false, frame.cupCount)
                            val previousSleepState = sleepNow.state
                            if (sleepNow.observe(++sleepSampleSerial, frame.sleepStateRaw))
                                event(ResourceMessage(R.string.service_event_sleep_confirmed), "sleep.confirmed")
                            else if (previousSleepState == SleepNowTracker.State.UNKNOWN &&
                                sleepNow.state == SleepNowTracker.State.RECONCILED)
                                event(ResourceMessage(R.string.service_event_sleep_reconciled), "sleep.reconciled")
                            if (sleepNow.state == SleepNowTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_NOW) {
                                if (!machineWriteRecovery.clear())
                                    event(ResourceMessage(R.string.service_write_sleep_clear_failed), "sleep.recovery_clear_failed")
                                refreshSafetyNotification()
                            }
                            val currentSettings = snapshot.settings
                            if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                                    BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
                                        currentSettings.brewCompensationTenthsC)))
                                event(ResourceMessage(R.string.service_event_preheat_ready), "brew_wait.ready")
                            val observedAt = SystemClock.elapsedRealtime()
                            snapshot.copy(coffee = frame, coffeeAt = observedAt,
                                alarmBits = frame.alarmBits, alarmAt = observedAt)
                        }
                        is io.openhoyi.protocol.ExtractionTelemetry ->
                            snapshot.copy(coffee = frame, coffeeAt = SystemClock.elapsedRealtime())
                        else -> snapshot
                    }
                    val observedAt = snapshot.coffeeAt ?: SystemClock.elapsedRealtime()
                    val appShotInProgress = shotState !in setOf(ExtractionState.IDLE, ExtractionState.ENDED_OBSERVED) ||
                        lastShotState !in setOf(ExtractionState.IDLE, ExtractionState.ENDED_OBSERVED)
                    val passiveEvent = if (snapshot.coffeeState == DeviceState.READY)
                        passiveShot.observe(frame, observedAt, appShotInProgress) else null
                    when (passiveEvent) {
                        is PassiveShotDetector.Event.Started -> {
                            val previousRecovery = shotRecovery.pending
                            if (!previousRecovery) manualSafetyResource = null
                            val currentAddress = hub?.coffeeAddress
                            val armed = shotRecovery.arm(currentAddress)
                            passiveMayClearRecovery = armed &&
                                shotRecovery.mayClearAfterPassiveShot(previousRecovery, currentAddress)
                            if (!armed)
                                manualSafetyResource = R.string.service_event_shot_record_failed
                            refreshSafetyNotification()
                            passiveHistoryId = runCatching { history?.begin("manual", slot = 6) }
                                .onFailure { event(ResourceMessage(R.string.service_event_manual_history_unwritable), "shot.history_error") }.getOrNull()
                            val id = passiveHistoryId ?: java.util.UUID.randomUUID().toString()
                            series.begin(id, passiveEvent.first.atMs)
                            val feedbackSlot = passiveEvent.first.frame.slotOrPhase
                            beginBrewFeedback(id, feedbackSlot, if (feedbackSlot == 6) 0 else null, appShot = false)
                            recordMachinePoint(passiveEvent.first.frame, passiveEvent.first.atMs)
                            recordMachinePoint(passiveEvent.second.frame, passiveEvent.second.atMs)
                            passiveHistoryId?.let {
                                runCatching { history?.transition(ExtractionState.RUNNING, "机器手动萃取", null) }
                                    .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                            }
                            event(ResourceMessage(R.string.service_event_manual_started), "shot.passive_started")
                        }
                        is PassiveShotDetector.Event.Point -> recordMachinePoint(passiveEvent.value.frame,
                            passiveEvent.value.atMs)
                        PassiveShotDetector.Event.Ended -> {
                            if (!passiveMayClearRecovery || !shotRecovery.matchesDevice(hub?.coffeeAddress) ||
                                !shotRecovery.clear())
                                manualSafetyResource = R.string.service_event_shot_clear_failed
                            passiveMayClearRecovery = false
                            val weight = snapshot.weight?.weightHundredthsGram?.takeIf {
                                snapshot.scaleState == DeviceState.READY &&
                                    TelemetryFreshness.isFresh(snapshot.weightAt, observedAt)
                            }
                            passiveHistoryId?.let {
                                runCatching { history?.transition(ExtractionState.ENDED_OBSERVED,
                                    "机器待机回报", weight) }
                                    .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                            }
                            passiveHistoryId = null
                            finishSeries(true)
                            event(ResourceMessage(R.string.service_event_manual_ended), "shot.passive_ended")
                        }
                        else -> if (frame is io.openhoyi.protocol.ExtractionTelemetry && !passiveShot.active)
                            recordMachinePoint(frame, observedAt)
                    }
                },
                onWeight = {
                    observeTare()
                    val receivedAt = SystemClock.elapsedRealtime()
                    if (ScaleReadingPolicy.isFresh(it.weightHundredthsGram, receivedAt, receivedAt))
                        snapshot = snapshot.copy(weight = it, weightAt = receivedAt)
                },
                diagnostic = { detail ->
                    if (detail.startsWith("scale.auto_reconnect.")) event(detail, "scale.auto_reconnect")
                    else event(ResourceMessage(R.string.service_event_communication_error), "ble.diagnostic")
                },
                trace = { role, trace ->
                    logs.record("wire.${trace.kind}", buildMap {
                        put("ownerId", ownerId); put("role", role.name); put("generation", trace.generation.toString())
                        trace.token?.let { put("token", it.toString()) }
                        trace.endpoint?.let { put("endpoint", it) }
                        trace.hex?.let { put("hex", it) }
                        trace.size?.let { put("size", it.toString()) }
                        trace.detail?.let { put("detail", it) }
                    })
                },
                legacyVerifiedStartFrames = (application as MobileApplication).curves.legacyVerifiedStartFrames,
            )
            running = true
            event(ResourceMessage(R.string.service_event_started))
            refreshSafetyNotification()
            scheduleAutomaticScaleStop()
            if (appVisibility.visible) {
                hub?.foreground()
                hubForeground = true
            }
        } catch (error: RuntimeException) {
            event(ResourceMessage(R.string.service_event_startup_failed, error.javaClass.simpleName))
            shutdown()
        }
        return START_NOT_STICKY
    }
    private fun onAppVisibilityChanged(visible: Boolean) {
        if (visible) {
            handler.removeCallbacks(leaveForeground)
            if (!hubForeground && hub != null) {
                hub?.foreground()
                hubForeground = true
                scheduleAutomaticScaleStop()
            }
        } else if (hubForeground) {
            // Normal navigation may briefly have no started screen; configuration replacement stays visible.
            handler.removeCallbacks(leaveForeground)
            handler.postDelayed(leaveForeground, 500)
        }
    }
    fun scan() {
        if (mock != null) {
            snapshot = snapshot.copy(candidates = listOf(
                DiscoveredDevice("02:00:00:00:00:01", "HOYI Mock", -42, DeviceRole.COFFEE),
                DiscoveredDevice("02:00:00:00:00:02", "BOOKOO Mock", -45, DeviceRole.BOOKOO)),
                scanning = false)
            event(ResourceMessage(R.string.service_scan_mock), "mock.scan")
            return
        }
        val current = hub ?: return
        manualDeviceUse()
        if (snapshot.scanning) return
        snapshot = snapshot.copy(scanning = true, candidates = emptyList(), message = resolvedMessage(ResourceMessage(R.string.service_scanning)))
        Log.i(TAG, "scan start")
        current.scanner.start(onDevice = { candidate ->
            snapshot = snapshot.copy(candidates = (snapshot.candidates.filterNot { it.address == candidate.address } + candidate)
                .sortedWith(compareBy({ it.candidateRole.name }, { it.advertisedName })).take(64))
        }, onFinished = { error ->
            snapshot = snapshot.copy(scanning = false)
            if (error == null) event(ResourceMessage(R.string.service_scan_finished, snapshot.candidates.size.toString()))
            else event(error)
        })
    }
    fun connectRememberedCoffee(address: String): Boolean {
        if (mock != null || !shotRecovery.matchesDevice(address) ||
            !machineWriteRecovery.matchesDevice(address) || !coffeeCredentialRetries.mayUse(address)) return false
        val password = coffeeCredentials.read(address) ?: return false
        connectCoffee(address, password, remembered = true)
        return true
    }
    fun connectCoffee(address: String, password: String) = connectCoffee(address, password, remembered = false)
    private fun connectCoffee(address: String, password: String, remembered: Boolean) {
        if (mock != null) { event(ResourceMessage(R.string.service_coffee_mock), "mock.connect"); return }
        if (manualShotActive) { event(ResourceMessage(R.string.service_connection_manual_block)); return }
        if (!shotRecovery.matchesDevice(address)) {
            event(ResourceMessage(R.string.service_connection_shot_device_mismatch), "shot.device_mismatch"); return
        }
        if (!machineWriteRecovery.matchesDevice(address)) {
            event(ResourceMessage(R.string.service_connection_write_device_mismatch), "machine_write.device_mismatch"); return
        }
        require(password.matches(Regex("[0-9]{6}")))
        manualDeviceUse()
        if (cupResetBusy) { event(ResourceMessage(R.string.service_connection_cups_busy)); return }
        if (scheduleBusy) { event(ResourceMessage(R.string.service_connection_schedule_busy)); return }
        if (!ShotGate.mayReconnectCoffee(shotState)) {
            event(ResourceMessage(R.string.service_connection_shot_busy)); return
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event(ResourceMessage(R.string.service_connection_cancel_preheat), "brew_wait.connect_deferred")
            return
        }
        val current = hub ?: return
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null,
            settings = null, settingsAt = null, sleepFirst = null, sleepSecond = null, sleepFirstAt = null, sleepSecondAt = null)
        event(ResourceMessage(if (remembered) R.string.service_coffee_connect_remembered else R.string.home_connect_coffee_title))
        pendingCoffeeCredential = PendingCoffeeCredential(address, password, remembered)
        try { current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password)) }
        catch (error: RuntimeException) {
            pendingCoffeeCredential = null
            event(ResourceMessage(R.string.service_coffee_connect_failed, error.javaClass.simpleName), "coffee.connect_failed")
        }
    }
    fun connectScale(address: String) {
        if (mock != null) { event(ResourceMessage(R.string.service_scale_mock), "mock.connect"); return }
        if (manualShotActive) { event(ResourceMessage(R.string.service_scale_manual_block)); return }
        manualDeviceUse()
        if (ShotGate.active(shotState)) { event(ResourceMessage(R.string.service_scale_shot_block)); return }
        val current = hub ?: return
        if (!current.connectScale(address)) { event(ResourceMessage(R.string.service_scale_already_connected)); return }
        snapshot = snapshot.copy(weight = null, weightAt = null)
        event(ResourceMessage(R.string.service_scale_connect))
    }
    fun disconnect(role: DeviceRole) {
        if (mock != null) { event(ResourceMessage(R.string.service_disconnect_mock), "mock.disconnect"); return }
        if (manualShotActive) { event(ResourceMessage(R.string.service_connection_manual_block)); return }
        if (ShotGate.active(shotState)) { event(ResourceMessage(R.string.service_disconnect_shot_block)); return }
        if (role == DeviceRole.COFFEE && cupResetBusy) {
            event(ResourceMessage(R.string.service_disconnect_cups_busy)); return
        }
        if (role == DeviceRole.COFFEE && scheduleBusy) {
            event(ResourceMessage(R.string.service_disconnect_schedule_busy)); return
        }
        if (role == DeviceRole.COFFEE && brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event(ResourceMessage(R.string.service_disconnect_cancel_preheat), "brew_wait.disconnect_deferred")
            return
        }
        event(ResourceMessage(R.string.service_disconnect, role.name))
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    fun tareScale(): String? {
        if (mock != null) {
            val result = mock.tare()
            if (result == null) {
                refreshMock(mock)
                event(ResourceMessage(R.string.service_tare_mock), "mock.tare")
            }
            return result
        }
        if (manualShotActive) return getString(R.string.service_tare_manual_block)
        val current = hub ?: return getString(R.string.service_unavailable)
        if (ShotGate.active(shotState)) return getString(R.string.service_tare_shot_block)
        if (snapshot.scaleState != DeviceState.READY) return getString(R.string.service_tare_not_ready)
        if (tareState in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO))
            return getString(R.string.service_tare_waiting)
        event(ResourceMessage(R.string.service_tare_requested), "scale.tare_requested")
        current.tareScale { result ->
            lastTareState = tareState
            when (tareState) {
                StandaloneTare.State.WAITING_ZERO -> event(ResourceMessage(R.string.service_tare_written), "scale.tare_written")
                StandaloneTare.State.UNKNOWN -> event(ResourceMessage(R.string.service_tare_unknown), "scale.tare_unknown")
                else -> if (result !is OperationResult.Success) event(ResourceMessage(R.string.service_tare_failed), "scale.tare_failed")
            }
        }
        return null
    }
    fun changeMachineSetting(change: MachineSettingChange): String? {
        if (mock != null) {
            val result = mock.changeSetting(change)
            if (result == null) {
                refreshMock(mock)
                event(ResourceMessage(R.string.service_write_mock_setting, settingsPresentation.changeMessage(change)), "mock.setting")
            }
            return result
        }
        if (manualShotActive) return getString(R.string.service_write_setting_manual_block)
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return getString(R.string.service_write_cups_waiting)
        if (scheduleBusy) return getString(R.string.service_write_schedule_waiting)
        if (ShotGate.active(shotState)) return getString(R.string.service_write_setting_shot_block)
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return getString(R.string.service_write_sleep_waiting)
        if (brewPreparation.active) return getString(R.string.service_write_preheat_block)
        if (snapshot.coffeeState != DeviceState.READY) return getString(R.string.start_block_coffee_not_ready)
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        if (!TelemetryFreshness.isFresh(snapshot.coffeeAt, now))
            return getString(R.string.service_write_idle_stale)
        if (idle.sleepStateRaw != 0) return getString(R.string.service_write_setting_awake_block)
        val observed = snapshot.settings ?: return getString(R.string.settings_missing)
        if (!machineSettingsFresh) return getString(R.string.service_write_settings_stale)
        if (change is MachineSettingChange.SleepScheduleEnabled && change.enabled &&
            !sleepScheduleFresh)
            return getString(R.string.service_write_schedule_enable_block)
        if (change is MachineSettingChange.StandbyDelay &&
            change.temperatureC != observed.standbyTemperatureC) return getString(R.string.service_write_standby_temperature_changed)
        if (change is MachineSettingChange.StandbyTemperature &&
            change.minutes != observed.standbyMinutes) return getString(R.string.service_write_standby_delay_changed)
        if (change.matches(observed)) return getString(R.string.service_write_setting_already_observed)
        val coffeeAddress = current.coffeeAddress ?: return getString(R.string.service_write_setting_identity_missing)
        val token = when (val registration = MachineWriteRegistration.begin(machineWriteRecovery,
            coffeeAddress, MachineWriteRegistration.Request.Setting(settingsWrite, change, settingsSampleSerial))) {
            MachineWriteRegistration.Result.Busy -> return getString(R.string.service_write_setting_pending)
            MachineWriteRegistration.Result.RecordFailed -> return getString(R.string.service_write_setting_record_failed)
            is MachineWriteRegistration.Result.Registered -> registration.token
        }
        recoveryAfterSettingsSerial = settingsSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_setting_queued, settingsPresentation.changeMessage(change)), "settings.requested")
        current.writeSetting(change) done@{ result ->
            if (!settingsWrite.written(token, result, settingsSampleSerial)) return@done
            when (settingsWrite.state) {
                SettingsWriteTracker.State.WAITING_READBACK -> {
                    event(ResourceMessage(R.string.service_write_setting_written), "settings.written")
                    handler.postDelayed({
                        if (settingsWrite.timeout(token, settingsSampleSerial)) {
                            recoveryAfterSettingsSerial = settingsSampleSerial
                            event(ResourceMessage(R.string.service_write_setting_no_readback), "settings.unknown")
                        }
                    }, 6000)
                }
                SettingsWriteTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event(ResourceMessage(R.string.service_write_setting_clear_failed), "settings.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_setting_not_written), "settings.failed")
                }
                SettingsWriteTracker.State.UNKNOWN -> {
                    recoveryAfterSettingsSerial = settingsSampleSerial
                    event(ResourceMessage(R.string.service_write_setting_unknown), "settings.unknown")
                }
                else -> Unit
            }
        }
        return null
    }
    fun resetCupCount(expectedCount: Int): String? {
        if (mock != null) {
            val result = mock.resetCupCount(expectedCount)
            if (result == null) {
                refreshMock(mock)
                event(ResourceMessage(R.string.service_write_cups_mock), "mock.cups")
            }
            return result
        }
        if (manualShotActive) return getString(R.string.service_write_cups_manual_block)
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupReset.state == CupResetTracker.State.UNKNOWN)
            return getString(R.string.service_write_cups_previous_unknown)
        if (cupResetBusy) return getString(R.string.service_write_cups_pending)
        if (scheduleBusy || settingWriteState in
            setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK) ||
            sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return getString(R.string.service_write_operation_pending)
        if (brewPreparation.active || ShotGate.active(shotState)) return getString(R.string.service_write_preheat_or_shot_block)
        if (snapshot.coffeeState != DeviceState.READY) return getString(R.string.start_block_coffee_not_ready)
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        val now = SystemClock.elapsedRealtime()
        if (!TelemetryFreshness.isFresh(snapshot.coffeeAt, now) || idle.sleepStateRaw != 0)
            return getString(R.string.service_write_awake_idle_needed)
        val settingsCount = snapshot.settings?.cupCount ?: return getString(R.string.service_write_cups_missing)
        if (!machineSettingsFresh) return getString(R.string.service_write_cups_settings_stale)
        if (expectedCount !in 1..65535 || settingsCount != expectedCount || idle.cupCount != expectedCount)
            return getString(R.string.service_write_cups_changed)
        val coffeeAddress = current.coffeeAddress ?: return getString(R.string.service_write_cups_identity_missing)
        val token = when (val registration = MachineWriteRegistration.begin(machineWriteRecovery,
            coffeeAddress, MachineWriteRegistration.Request.CupReset(cupReset, expectedCount, cupSettingsSerial, cupIdleSerial))) {
            MachineWriteRegistration.Result.Busy -> return getString(R.string.service_write_cups_pending)
            MachineWriteRegistration.Result.RecordFailed -> return getString(R.string.service_write_cups_record_failed)
            is MachineWriteRegistration.Result.Registered -> registration.token
        }
        recoveryAfterSettingsSerial = cupSettingsSerial
        recoveryAfterIdleSerial = cupIdleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_cups_queued), "cups.requested")
        current.resetCupCount(expectedCount) done@{ result ->
            if (!cupReset.written(token, result, cupSettingsSerial, cupIdleSerial)) return@done
            when (cupReset.state) {
                CupResetTracker.State.WAITING_ZERO -> {
                    event(ResourceMessage(R.string.service_write_cups_written), "cups.written")
                    handler.postDelayed({
                        if (cupReset.timeout(token, cupSettingsSerial, cupIdleSerial)) {
                            recoveryAfterSettingsSerial = cupSettingsSerial
                            recoveryAfterIdleSerial = cupIdleSerial
                            event(ResourceMessage(R.string.service_write_cups_no_readback), "cups.unknown")
                        }
                    }, 12_000)
                }
                CupResetTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event(ResourceMessage(R.string.service_write_cups_clear_failed), "cups.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_cups_not_written), "cups.failed")
                }
                CupResetTracker.State.UNKNOWN -> {
                    recoveryAfterSettingsSerial = cupSettingsSerial
                    recoveryAfterIdleSerial = cupIdleSerial
                    event(ResourceMessage(R.string.service_write_cups_unknown), "cups.unknown")
                }
                else -> Unit
            }
        }
        return null
    }
    fun changeSleepSchedule(expected: WeeklySleepSchedule, target: WeeklySleepSchedule): String? {
        if (mock != null) {
            val result = mock.changeSchedule(expected, target)
            if (result == null) {
                refreshMock(mock)
                event(ResourceMessage(R.string.service_write_schedule_mock), "mock.sleep_schedule")
            }
            return result
        }
        if (manualShotActive) return getString(R.string.service_write_schedule_manual_block)
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return getString(R.string.service_write_cups_waiting)
        if (ShotGate.active(shotState)) return getString(R.string.service_write_schedule_shot_block)
        if (scheduleWriteState == SleepScheduleWriteTracker.State.UNKNOWN)
            return getString(R.string.service_write_schedule_previous_unknown)
        if (scheduleBusy || settingWriteState in
            setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return getString(R.string.service_write_setting_previous_pending)
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return getString(R.string.service_write_sleep_waiting)
        if (brewPreparation.active) return getString(R.string.service_write_preheat_block)
        if (snapshot.coffeeState != DeviceState.READY) return getString(R.string.start_block_coffee_not_ready)
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        if (!TelemetryFreshness.isFresh(snapshot.coffeeAt, now) || idle.sleepStateRaw != 0)
            return getString(R.string.service_write_awake_idle_needed)
        if (!sleepScheduleFresh) return getString(R.string.service_write_schedule_stale)
        val observed = WeeklySleepSchedule.fromReadback(snapshot.sleepFirst, snapshot.sleepSecond)
            ?: return getString(R.string.service_write_schedule_missing)
        if (observed.days != expected.days) return getString(R.string.service_write_schedule_changed)
        val changedDays = expected.days.indices.count { expected.days[it] != target.days[it] }
        if (changedDays == 0) return getString(R.string.service_write_schedule_already_observed)
        if (changedDays != 1) return getString(R.string.service_write_schedule_one_day_only)
        val coffeeAddress = current.coffeeAddress ?: return getString(R.string.service_write_schedule_identity_missing)
        val token = when (val registration = MachineWriteRegistration.begin(machineWriteRecovery,
            coffeeAddress, MachineWriteRegistration.Request.Schedule(scheduleWrite, target, firstSleepSerial, secondSleepSerial, snapshot.sleepFirst, snapshot.sleepSecond))) {
            MachineWriteRegistration.Result.Busy -> return getString(R.string.service_write_schedule_pending)
            MachineWriteRegistration.Result.RecordFailed -> return getString(R.string.service_write_schedule_record_failed)
            is MachineWriteRegistration.Result.Registered -> registration.token
        }
        recoveryAfterFirstSleepSerial = firstSleepSerial
        recoveryAfterSecondSleepSerial = secondSleepSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_schedule_queued), "sleep_schedule.requested")
        current.writeSleepSchedule(target,expected) done@{ result ->
            if (!scheduleWrite.written(token, result, firstSleepSerial, secondSleepSerial,
                    snapshot.sleepFirst, snapshot.sleepSecond)) return@done
            when (scheduleWrite.state) {
                SleepScheduleWriteTracker.State.CONFIRMED -> {
                    if (!machineWriteRecovery.clear())
                        event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_schedule_confirmed), "sleep_schedule.confirmed")
                }
                SleepScheduleWriteTracker.State.WAITING_READBACK -> {
                    event(ResourceMessage(R.string.service_write_schedule_written), "sleep_schedule.written")
                    handler.postDelayed({
                        if (scheduleWrite.timeout(token, firstSleepSerial, secondSleepSerial)) {
                            recoveryAfterFirstSleepSerial = firstSleepSerial
                            recoveryAfterSecondSleepSerial = secondSleepSerial
                            event(ResourceMessage(R.string.service_write_schedule_no_readback), "sleep_schedule.unknown")
                        }
                    }, 8000)
                }
                SleepScheduleWriteTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_schedule_first_not_written), "sleep_schedule.failed")
                }
                SleepScheduleWriteTracker.State.UNKNOWN -> {
                    recoveryAfterFirstSleepSerial = firstSleepSerial
                    recoveryAfterSecondSleepSerial = secondSleepSerial
                    event(ResourceMessage(R.string.service_write_schedule_partial_unknown), "sleep_schedule.unknown")
                }
                else -> Unit
            }
        }
        return null
    }
    fun enterSleepNow(): String? {
        if (mock != null) {
            val result = mock.sleepNow()
            if (result == null) {
                refreshMock(mock)
                event(ResourceMessage(R.string.service_write_sleep_mock), "mock.sleep")
            }
            return result
        }
        if (manualShotActive) return getString(R.string.service_write_sleep_manual_block)
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return getString(R.string.service_write_cups_waiting)
        if (scheduleBusy) return getString(R.string.service_write_schedule_waiting)
        if (ShotGate.active(shotState)) return getString(R.string.service_write_sleep_shot_block)
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return getString(R.string.service_write_setting_waiting)
        if (brewPreparation.active) return getString(R.string.service_write_preheat_block)
        if (snapshot.coffeeState != DeviceState.READY) return getString(R.string.start_block_coffee_not_ready)
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        val observedAt = snapshot.coffeeAt ?: return getString(R.string.service_write_idle_waiting)
        if (!TelemetryFreshness.isFresh(observedAt, now)) return getString(R.string.service_write_idle_stale)
        if (idle.sleepStateRaw == 1) return getString(R.string.service_write_sleep_already_asleep)
        if (idle.sleepStateRaw != 0) return getString(R.string.service_write_sleep_state_unknown)
        val coffeeAddress = current.coffeeAddress ?: return getString(R.string.service_write_sleep_identity_missing)
        val token = when (val registration = MachineWriteRegistration.begin(machineWriteRecovery,
            coffeeAddress, MachineWriteRegistration.Request.Sleep(sleepNow, sleepSampleSerial))) {
            MachineWriteRegistration.Result.Busy -> return getString(R.string.service_write_sleep_pending)
            MachineWriteRegistration.Result.RecordFailed -> return getString(R.string.service_write_sleep_record_failed)
            is MachineWriteRegistration.Result.Registered -> registration.token
        }
        recoveryAfterSleepSerial = sleepSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_sleep_queued), "sleep.requested")
        current.enterSleep done@{ result ->
            if (!sleepNow.written(token, result, sleepSampleSerial)) return@done
            when (sleepNow.state) {
                SleepNowTracker.State.WAITING_ASLEEP -> {
                    event(ResourceMessage(R.string.service_write_sleep_written), "sleep.written")
                    handler.postDelayed({
                        if (sleepNow.timeout(token, sleepSampleSerial)) {
                            recoveryAfterSleepSerial = sleepSampleSerial
                            event(ResourceMessage(R.string.service_write_sleep_no_readback), "sleep.unknown")
                        }
                    }, 12_000)
                }
                SleepNowTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event(ResourceMessage(R.string.service_write_sleep_clear_failed), "sleep.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_sleep_not_written), "sleep.failed")
                }
                SleepNowTracker.State.UNKNOWN -> {
                    recoveryAfterSleepSerial = sleepSampleSerial
                    event(ResourceMessage(R.string.service_write_sleep_unknown), "sleep.unknown")
                }
                else -> Unit
            }
        }
        return null
    }
    private fun selectedCurve(profileId: String, slot: Int): CurveProfile? {
        val selectedId = if (slot == 7)
            getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        else if (slot in 1..5)
            PresetSlots.curveId(slot, getSharedPreferences("presets", MODE_PRIVATE)
                .getString(PresetSlots.key(slot), null))
        else null
        return profileId.takeIf { it == selectedId }?.let {
            (application as MobileApplication).curves.resolve(it, snapshot.scaleState == DeviceState.READY, slot)
        }
    }
    fun currentCorrectedBrewTemperature(): Int? {
        val frame = snapshot.coffee as? IdleTelemetry ?: return null
        val settings = snapshot.settings ?: return null
        val now = SystemClock.elapsedRealtime()
        if (snapshot.coffeeState != DeviceState.READY || !machineSettingsFresh ||
            !TelemetryFreshness.isFresh(snapshot.coffeeAt, now)) return null
        return BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
            settings.brewCompensationTenthsC)
    }
    fun studioStartBlock(profile: CurveProfile): String? = if (!machineSettingsFresh)
        getString(R.string.studio_settings_stale) else studioStartGate.block(
        snapshot.settings, currentCorrectedBrewTemperature(), profile, brewPreparation)
    fun prepareBrew(profileId: String, expectedScaleMode: Boolean?, slot: Int): String? {
        if (mock != null) {
            if (brewPreparation.active) return getString(R.string.service_shot_preheat_mock_pending)
            val profile = selectedCurve(profileId, slot) ?: return getString(R.string.start_block_curve_missing)
            if (profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_mock_scale_changed)
            val library = (application as MobileApplication).curves
            shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
                snapshot.scaleState, snapshot.weightAt, SystemClock.elapsedRealtime(), shotState,
                validated = library.validated(profile))?.let { return it }
            val now = SystemClock.elapsedRealtime()
            val result = mock.beginPreheat(profile.temperatureC, now)
            if (result != null) return result
            val token = brewPreparation.begin(profile.id, profile.temperatureC)
                ?: run { mock?.cancelPreheat(now); return getString(R.string.service_shot_preheat_mock_failed) }
            brewPreparation.written(token, OperationResult.Success(), idleSampleSerial)
            refreshMock(mock, now)
            event(ResourceMessage(R.string.service_shot_preheat_mock_target, profile.temperatureC.toString()), "mock.brew_wait")
            return null
        }
        if (manualShotActive) return getString(R.string.service_shot_preheat_manual_block)
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return getString(R.string.service_write_cups_waiting)
        if (scheduleBusy) return getString(R.string.service_write_schedule_waiting)
        if (brewPreparation.active) return getString(R.string.service_shot_preheat_pending)
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return getString(R.string.service_write_sleep_waiting)
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return getString(R.string.service_write_setting_waiting)
        val settings = snapshot.settings ?: return getString(R.string.settings_missing)
        if (!machineSettingsFresh) return getString(R.string.service_shot_preheat_settings_stale)
        if (settings.flags and 0x04 == 0) return getString(R.string.service_shot_preheat_cafe_mode)
        val profile = selectedCurve(profileId, slot)
        if (profile != null && profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_scale_changed)
        val library = (application as MobileApplication).curves
        val blocked = shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            snapshot.scaleState, snapshot.weightAt, SystemClock.elapsedRealtime(), current.extraction.state,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) return blocked
        requireNotNull(profile)
        val actual = currentCorrectedBrewTemperature() ?: return getString(R.string.service_shot_temperature_waiting)
        if (BrewPreparation.isAtTarget(actual, profile.temperatureC)) return getString(R.string.service_shot_preheat_already_ready)
        val coffeeAddress = current.coffeeAddress ?: return getString(R.string.service_shot_preheat_identity_missing)
        val token = when (val registration = MachineWriteRegistration.begin(machineWriteRecovery,
            coffeeAddress, MachineWriteRegistration.Request.BrewWait(brewPreparation, profile.id, profile.temperatureC))) {
            MachineWriteRegistration.Result.Busy -> return getString(R.string.service_shot_preheat_failed)
            MachineWriteRegistration.Result.RecordFailed -> return getString(R.string.service_shot_preheat_record_failed)
            is MachineWriteRegistration.Result.Registered -> registration.token
        }
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_shot_preheat_queued, profile.temperatureC.toString()), "brew_wait.requested")
        current.setBrewWait(profile.temperatureC, {
            brewPreparation.permitsWrite(token, profile.temperatureC) && !manualShotActive &&
                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT &&
                machineWriteRecovery.matchesDevice(current.coffeeAddress)
        }) done@{ result ->
            if (!brewPreparation.written(token, result, idleSampleSerial)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.WAITING_TEMP -> {
                    event(ResourceMessage(R.string.service_shot_preheat_written), "brew_wait.written")
                    handler.postDelayed({
                        if (brewPreparation.isActive(token)) {
                            val blocked = cancelBrewPreparationMessage()
                            if (blocked == null && brewPreparation.state != BrewPreparation.State.UNKNOWN)
                                event(ResourceMessage(R.string.service_shot_preheat_timeout_cancel), "brew_wait.timeout_cancel_requested")
                            else if (brewPreparation.timedOut(token)) {
                                event(ResourceMessage(R.string.service_shot_preheat_timeout_blocked, blocked ?: "null"), "brew_wait.timeout_cancel_blocked")
                                refreshSafetyNotification()
                            }
                        }
                    }, 600_000)
                }
                BrewPreparation.State.FAILED -> event(ResourceMessage(R.string.service_shot_preheat_not_written), "brew_wait.failed")
                BrewPreparation.State.UNKNOWN -> event(ResourceMessage(R.string.service_shot_preheat_unknown), "brew_wait.unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    fun cancelBrewPreparation(): String? = cancelBrewPreparationMessage()?.render { id, args -> getString(id, *args) }

    private fun cancelBrewPreparationMessage(): ResourceMessage? {
        if (mock != null) {
            if (!brewPreparation.active) return ResourceMessage(R.string.service_shot_cancel_mock_none)
            val now = SystemClock.elapsedRealtime()
            val result = mock.cancelPreheatMessage(now)
            if (result != null) return result
            val token = brewPreparation.beginCancel() ?: return ResourceMessage(R.string.service_shot_cancel_mock_pending)
            brewPreparation.cancelled(token, OperationResult.Success())
            brewPreparation.consumed()
            refreshMock(mock, now)
            event(ResourceMessage(R.string.service_shot_cancel_mock_done), "mock.brew_wait_cancelled")
            return null
        }
        if (manualShotActive) return ResourceMessage(R.string.service_shot_cancel_manual_block)
        val recovering = machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT
        if (!brewPreparation.active && !recovering) return ResourceMessage(R.string.service_shot_cancel_none)
        val current = hub ?: return ResourceMessage(R.string.service_shot_cancel_unavailable)
        if (!machineWriteRecovery.matchesDevice(current.coffeeAddress)) return ResourceMessage(R.string.service_shot_cancel_original_device)
        brewWaitCancelBlockMessage?.let { return it }
        if (!brewPreparation.active && recovering) {
            if (!brewPreparation.restoreUnknown()) return ResourceMessage(R.string.service_shot_cancel_restore_failed)
        }
        val token = brewPreparation.beginCancel() ?: return ResourceMessage(R.string.service_shot_cancel_pending)
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        event(ResourceMessage(R.string.service_shot_cancel_queued), "brew_wait.cancel_requested")
        current.setBrewWait(0, {
            brewPreparation.permitsWrite(token, 0) && brewWaitCancelBlockMessage == null &&
                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT &&
                machineWriteRecovery.matchesDevice(current.coffeeAddress)
        }) done@{ result ->
            if (!brewPreparation.cancelled(token, result)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.CANCEL_WRITTEN -> event(ResourceMessage(R.string.service_shot_cancel_written), "brew_wait.cancel_written")
                BrewPreparation.State.UNKNOWN -> event(ResourceMessage(R.string.service_shot_cancel_unknown), "brew_wait.cancel_unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    fun startShot(profileId: String, expectedScaleMode: Boolean? = null, slot: Int = 7): String? {
        if (mock != null) {
            if (mock.isSleeping) return getString(R.string.service_shot_mock_sleeping)
            val profile = selectedCurve(profileId, slot) ?: return getString(R.string.start_block_curve_missing)
            if (profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_mock_scale_changed)
            if (ShotGate.active(shotState)) return getString(R.string.service_shot_mock_running)
            studioStartBlock(profile)?.let { return it }
            val now = SystemClock.elapsedRealtime()
            if (brewPreparation.active) {
                mock.cancelPreheat(now)
                brewPreparation.consumed()
            }
            mock.start(now, slot)
            activeShotTargetHundredthsGram = profile.targetHundredthsGram
            val shotId = runCatching { history?.begin(profile.id, slot = slot) }.getOrNull()
                ?: java.util.UUID.randomUUID().toString()
            series.begin(shotId, SystemClock.elapsedRealtime())
            beginBrewFeedback(shotId, if (slot == 7) 8 else slot, profile.parameters.preinfusionSeconds)
            event(ResourceMessage(R.string.service_shot_mock_started, profile.name), "mock.shot_started")
            return null
        }
        if (manualShotActive) return getString(R.string.service_shot_manual_block)
        if (machineWriteRecovery.kind != MachineWriteRecoveryState.Kind.BREW_WAIT ||
            !machineWriteRecovery.matchesDevice(hub?.coffeeAddress) ||
            !brewPreparation.matches(profileId, selectedCurve(profileId, slot)?.temperatureC ?: -1))
            machineWriteSafetyMessage?.let { return it }
        if (shotRecovery.pending) return restartShotWarning
        tareStartBlock?.let { return it }
        val current = hub ?: return getString(R.string.service_unavailable)
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return getString(R.string.service_shot_cups_start_block)
        if (scheduleBusy) return getString(R.string.service_shot_schedule_start_block)
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return getString(R.string.service_shot_sleep_start_block)
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return getString(R.string.service_shot_settings_start_block)
        val library = (application as MobileApplication).curves
        val profile = selectedCurve(profileId, slot)
        if (profile != null && profile.scaleMode != expectedScaleMode) {
            event(ResourceMessage(R.string.service_shot_scale_connection_changed), "shot.rejected")
            return getString(R.string.service_shot_scale_connection_changed)
        }
        val blocked = shotGate.startBlockMessage(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
            snapshot.weightAt, SystemClock.elapsedRealtime(), current.extraction.state,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) {
            event(ResourceMessage(R.string.service_shot_start_blocked, blocked), "shot.rejected")
            return blocked.render { id, args -> getString(id, *args) }
        }
        requireNotNull(profile)
        studioStartBlock(profile)?.let { return it }
        if (current.extraction.state == ExtractionState.ENDED_OBSERVED) {
            runCatching { history?.transition(ExtractionState.ENDED_OBSERVED, stopReason, null) }
                .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
            finishSeries(true)
        }
        logs.record("shot.start.attempt", mapOf("ownerId" to ownerId, "curveId" to profile.id,
            "targetHundredthsGram" to profile.targetHundredthsGram.toString(),
            "scaleMode" to (profile.scaleMode?.toString() ?: "captured"), "slot" to slot.toString()))
        val coffeeAddress=current.coffeeAddress ?: return getString(R.string.service_shot_identity_missing)
        if (!shotRecovery.arm(coffeeAddress)) return getString(R.string.service_shot_record_failed)
        if (!current.extraction.start(profile.parameters, profile.targetHundredthsGram, profile.compensationHundredthsGram) ||
            current.extraction.state == ExtractionState.IDLE) {
            if (!shotRecovery.clear()) manualSafetyResource = R.string.machine_recovery_shot_restart
            event(ResourceMessage(R.string.service_shot_session_rejected), "shot.rejected")
            return getString(R.string.service_shot_session_rejected)
        }
        activeShotTargetHundredthsGram = profile.targetHundredthsGram
        if (brewPreparation.active) brewPreparation.consumed()
        if (machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT)
            brewWaitShotStarted = true
        val shotId = runCatching { history?.begin(profile.id, slot = slot) }
            .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
            .getOrNull() ?: java.util.UUID.randomUUID().toString()
        series.begin(shotId, SystemClock.elapsedRealtime())
        beginBrewFeedback(shotId, if (slot == 7) 8 else slot, profile.parameters.preinfusionSeconds)
        if (current.extraction.state == ExtractionState.OUTCOME_UNKNOWN) {
            finishBrewFeedback(false)
            event(ResourceMessage(R.string.service_shot_start_unknown), "shot.unknown")
            return getString(R.string.service_shot_start_unknown)
        }
        event(ResourceMessage(if (current.extraction.preparingScale) R.string.service_shot_waiting_tare
            else R.string.service_shot_requested, profile.name), "shot.requested")
        return null
    }
    fun stopShot() {
        if (mock != null) {
            if (ShotGate.active(shotState)) {
                mock.stop()
                event(ResourceMessage(R.string.service_shot_mock_stopped), "mock.shot_stopped")
            }
            return
        }
        if (manualShotActive) { event(ResourceMessage(R.string.service_shot_manual_stop_block)); return }
        if (!ShotGate.active(shotState)) return
        if (shotState == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != DeviceState.READY) {
            event(ResourceMessage(R.string.service_shot_stop_unavailable), "shot.stop_unavailable")
            return
        }
        event(ResourceMessage(R.string.service_shot_stop_requested), "shot.manual_stop")
        hub?.extraction?.manualStop()
    }
    // Local projection only. Failures cannot escape into history, recovery or device callbacks.
    private fun beginBrewFeedback(shotId: String, slot: Int, preinfusionSeconds: Int?, appShot: Boolean = true) {
        finishBrewFeedback(false)
        feedbackAppShot = appShot
        if (preinfusionSeconds != null &&
            runCatching { brewFeedback.begin(shotId, slot, preinfusionSeconds) }.isSuccess)
            feedbackShotId = shotId
    }
    private fun observeBrewFeedback(frame: io.openhoyi.protocol.ExtractionTelemetry, atMs: Long) {
        val id = feedbackShotId ?: return
        if (runCatching { brewFeedback.observe(id, frame, atMs) }.isFailure) finishBrewFeedback(false)
    }
    private fun finishBrewFeedback(observedEnd: Boolean) {
        val id = feedbackShotId
        feedbackShotId = null
        if (!observedEnd) {
            brewFeedbackResult = null
            feedbackDelivery.clear()
        }
        if (id != null) {
            val eligible = BrewFeedbackCompletion.allowed(observedEnd, snapshot.coffeeState,
                snapshot.alarmBits, if (feedbackAppShot) hub?.extraction?.stopReason else null)
            brewFeedbackResult = runCatching { brewFeedback.finish(id, eligible) }.getOrNull()
            runCatching { feedbackDelivery.publish(brewFeedbackResult,
                (application as MobileApplication).feedbackPreferences.enabled) }
                .onFailure { feedbackDelivery.clear() }
        }
    }
    internal fun claimBrewFeedback(): BrewFeedbackResult? = feedbackDelivery.claim(
        (application as MobileApplication).feedbackPreferences.enabled)
    internal fun discardBrewFeedback() = feedbackDelivery.clear()
    private fun finishSeries(observedEnd: Boolean) {
        finishBrewFeedback(observedEnd)
        val finished = series.finish() ?: return
        val app = application as MobileApplication
        if (observedEnd && finished.second.isNotEmpty() &&
            history?.entries?.any { it.id == finished.first } == true) {
            app.samples.save(finished.first, finished.second)
        }
        history?.entries?.map(ShotHistory.Entry::id)?.toSet()?.let(app.samples::prune)
    }
    private fun recordMachinePoint(frame: io.openhoyi.protocol.ExtractionTelemetry, atMs: Long) {
        observeBrewFeedback(frame, atMs)
        series.machine(frame, atMs,
            snapshot.weight?.weightHundredthsGram?.takeIf { snapshot.scaleState == DeviceState.READY },
            snapshot.weightAt,
            snapshot.weight?.deviceFlowHundredths?.takeIf { snapshot.scaleState == DeviceState.READY })
        saveSeriesCheckpoint(atMs)
    }
    private fun saveSeriesCheckpoint(atElapsedMs: Long = SystemClock.elapsedRealtime(), force: Boolean = false) {
        series.checkpoint(atElapsedMs, force)?.let { (id, points) ->
            runCatching { (application as MobileApplication).samples.save(id, points) }
                .onFailure { event(ResourceMessage(R.string.service_event_sample_save_failed), "shot.samples_error") }
        }
    }
    private fun resolvedMessage(resource: ResourceMessage): SnapshotMessage =
        SnapshotMessage.resource(resource) { id, args -> getString(id, *args) }
    private fun event(resource: ResourceMessage, kind: String = "mobile.event") =
        recordMessage(resolvedMessage(resource), kind)
    private fun event(message: String, kind: String = "mobile.event") =
        recordMessage(SnapshotMessage.raw(message), kind)
    private fun recordMessage(message: SnapshotMessage, kind: String) {
        snapshot = snapshot.copy(message = message)
        logs.record(kind, mapOf("message" to message.initialText, "ownerId" to ownerId))
        Log.i(TAG, message.initialText)
    }
    private fun connectionNotification(warning: String?): Notification = notificationDisplay.connection(warning, CHANNEL)
    private fun createNotificationChannels(manager: NotificationManager) =
        notificationDisplay.createChannels(manager, CHANNEL, SAFETY_CHANNEL)
    /** Called on the main thread after the Service's language context has been updated.
     * Only notification display is refreshed; no event, command or recovery action is replayed. */
    fun refreshNotificationDisplay() {
        if (!running || mock != null) return
        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            runCatching { createNotificationChannels(manager) }
                .onFailure { Log.w(TAG, "notification channel display failed: ${it.javaClass.simpleName}") }
            refreshSafetyNotification(displayOnly = true)
        }.onFailure { Log.w(TAG, "notification display failed: ${it.javaClass.simpleName}") }
    }
    private fun notificationFailure(displayOnly: Boolean, resource: Int, kind: String, error: Throwable) {
        if (displayOnly) Log.w(TAG, "notification display failed: ${error.javaClass.simpleName}")
        else try { event(ResourceMessage(resource), kind) }
        catch (displayError: RuntimeException) {
            Log.w(TAG, "notification error display failed: ${error.javaClass.simpleName}/${displayError.javaClass.simpleName}")
        }
    }
    private fun refreshSafetyNotification(displayOnly: Boolean = false) {
        try { updateSafetyNotification(displayOnly) }
        catch (error: RuntimeException) {
            // Notification presentation must never interrupt the caller's state-watch scheduling.
            Log.w(TAG, "notification display failed: ${error.javaClass.simpleName}")
        }
    }
    private fun updateSafetyNotification(displayOnly: Boolean) {
        if (mock != null) return
        val presentation = SafetyNotificationPresentation.from(
            ShotSafetyAlert.resource(shotState, snapshot.coffeeState), manualSafetyResource,
            machineWriteSafetyResource)
        val warning = presentation.warning { id, args -> getString(id, *args) }
        if (!displayOnly && warning == safetyMessage) return
        safetyMessage = warning
        if (!running) return
        val manager = try { getSystemService(NotificationManager::class.java) }
        catch (error: RuntimeException) {
            notificationFailure(displayOnly, R.string.notification_update_error, "shot.safety_notify_error", error)
            return
        }
        runCatching { manager.notify(1, connectionNotification(warning)) }
            .onFailure { notificationFailure(displayOnly, R.string.notification_update_error, "shot.safety_notify_error", it) }
        if (warning == null) runCatching { manager.cancel(SAFETY_NOTIFICATION) }
            .onFailure { notificationFailure(displayOnly, R.string.notification_unavailable, "shot.safety_notify_error", it) }
        else runCatching {
            val destination = if (presentation.destination == SafetyNotificationPresentation.Destination.HOME)
                HomeActivity::class.java else ExtractionActivity::class.java
            manager.notify(SAFETY_NOTIFICATION,
                notificationDisplay.safety(warning, destination, SAFETY_CHANNEL, displayOnly))
        }.onFailure { notificationFailure(displayOnly, R.string.notification_unavailable, "shot.safety_notify_error", it) }
    }
    /** A display failure must not interrupt Handler and BLE owner cleanup. */
    private fun cancelSafetyNotification() {
        try { getSystemService(NotificationManager::class.java).cancel(SAFETY_NOTIFICATION) }
        catch (error: RuntimeException) {
            Log.w(TAG, "notification cleanup failed: ${error.javaClass.simpleName}")
        }
    }
    fun acknowledgeManualSafety() {
        if (manualSafetyResource == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT) {
            if (shotRecovery.pending || ShotGate.active(shotState)) {
                event(ResourceMessage(R.string.recovery_event_brew_wait_recovery_shot_active), "brew_wait.recovery_shot_active")
                return
            }
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val state = brewPreparation.state
            val evidence = MachineWriteRecoveryState.BrewWaitEvidence(hub?.coffeeAddress,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                idleSampleSerial, snapshot.coffeeAt, now,
                MachineRecoveryActivity.isBusy(state))
            acknowledgeMachineWrite(MachineWriteAcknowledgement.Request.BrewWait(evidence, recoveryAfterBrewWaitIdleSerial),
                R.string.recovery_event_brew_wait_recovery_waiting,
                R.string.recovery_event_brew_wait_recovery_clear_failed,
                R.string.recovery_event_brew_wait_recovery_acknowledged, "brew_wait",
                onAcknowledged = { brewPreparation.consumed() })
            return
        }
        if (manualSafetyResource == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.CUP_RESET) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.CupResetEvidence(hub?.coffeeAddress,
                snapshot.settings?.cupCount, cupSettingsSerial, idle?.cupCount, cupIdleSerial,
                snapshot.coffeeAt, now, cupResetBusy)
            acknowledgeMachineWrite(MachineWriteAcknowledgement.Request.CupReset(evidence, recoveryAfterSettingsSerial, recoveryAfterIdleSerial),
                R.string.recovery_event_cups_recovery_waiting,
                R.string.recovery_event_cups_recovery_clear_failed,
                R.string.recovery_event_cups_recovery_acknowledged, "cups")
            return
        }
        if (manualSafetyResource == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SETTING) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.SettingEvidence(hub?.coffeeAddress,
                snapshot.settings != null, settingsSampleSerial,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                snapshot.coffeeAt, now,
                MachineRecoveryActivity.isBusy(settingsWrite.state))
            acknowledgeMachineWrite(MachineWriteAcknowledgement.Request.Setting(evidence, recoveryAfterSettingsSerial),
                R.string.recovery_event_settings_recovery_waiting,
                R.string.recovery_event_settings_recovery_clear_failed,
                R.string.recovery_event_settings_recovery_acknowledged, "settings")
            return
        }
        if (manualSafetyResource == null &&
            machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.ScheduleEvidence(hub?.coffeeAddress,
                sleepScheduleFresh,
                firstSleepSerial, secondSleepSerial,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                snapshot.coffeeAt, now, scheduleBusy)
            acknowledgeMachineWrite(MachineWriteAcknowledgement.Request.Schedule(evidence, recoveryAfterFirstSleepSerial, recoveryAfterSecondSleepSerial),
                R.string.recovery_event_sleep_schedule_recovery_waiting,
                R.string.recovery_event_sleep_schedule_recovery_clear_failed,
                R.string.recovery_event_sleep_schedule_recovery_acknowledged, "sleep_schedule")
            return
        }
        if (manualSafetyResource == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_NOW) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.SleepEvidence(hub?.coffeeAddress,
                idle?.sleepStateRaw, sleepSampleSerial, snapshot.coffeeAt, now,
                MachineRecoveryActivity.isBusy(sleepNow.state))
            acknowledgeMachineWrite(MachineWriteAcknowledgement.Request.Sleep(evidence, recoveryAfterSleepSerial),
                R.string.recovery_event_sleep_recovery_waiting,
                R.string.recovery_event_sleep_recovery_clear_failed,
                R.string.recovery_event_sleep_recovery_acknowledged, "sleep")
            return
        }
        if (manualSafetyResource == null) return
        shotRecoveryClearResource?.let {
            event(ResourceMessage(it), "shot.recovery_waiting")
            return
        }
        if (shotRecovery.pending) {
            if (!shotRecovery.clear()) {
                event(ResourceMessage(R.string.recovery_event_shot_recovery_clear_failed), "shot.recovery_clear_failed")
                return
            }
        }
        manualSafetyResource = null
        event(ResourceMessage(R.string.recovery_event_shot_passive_acknowledged), "shot.passive_acknowledged")
        refreshSafetyNotification()
    }
    private fun acknowledgeMachineWrite(
        request: MachineWriteAcknowledgement.Request,
        waitingResource: Int,
        failedResource: Int,
        acknowledgedResource: Int,
        eventPrefix: String,
        onAcknowledged: () -> Unit = {}
    ) {
        val result = MachineWriteAcknowledgement.acknowledge(machineWriteRecovery,
            snapshot.coffeeState == DeviceState.READY, request)
        if (result == MachineWriteAcknowledgement.Result.ACKNOWLEDGED) onAcknowledged()
        val (resource, suffix) = when (result) {
            MachineWriteAcknowledgement.Result.WAITING -> waitingResource to "waiting"
            MachineWriteAcknowledgement.Result.CLEAR_FAILED -> failedResource to "clear_failed"
            MachineWriteAcknowledgement.Result.ACKNOWLEDGED -> acknowledgedResource to "acknowledged"
        }
        event(ResourceMessage(resource), "${eventPrefix}.recovery_$suffix")
        if (result == MachineWriteAcknowledgement.Result.ACKNOWLEDGED) refreshSafetyNotification()
    }
    private fun closeDeviceOwner() {
        check(Looper.myLooper() == Looper.getMainLooper()) { "Device owner close must use main looper" }
        try { hub?.close() }
        catch (error: Exception) {
            Log.w(TAG, "device owner close failed: ${error.javaClass.simpleName}; suppressed=${error.suppressed.size}")
        }
        finally { hub = null; hubForeground = false }
    }
    fun shutdown() {
        if (mock != null) {
            finishBrewFeedback(false)
            if (ShotGate.active(shotState)) mock.stop()
            running = false
            handler.removeCallbacks(mockTick)
            snapshot = MobileSnapshot(message = resolvedMessage(ResourceMessage(R.string.service_event_mock_stopped)))
            stopSelf()
            return
        }
        if (manualShotActive) { event(ResourceMessage(R.string.service_connection_manual_block), "service.stop_deferred"); return }
        if (cupResetBusy) {
            event(ResourceMessage(R.string.service_event_cups_shutdown_block), "service.stop_deferred")
            return
        }
        if (scheduleBusy) {
            event(ResourceMessage(R.string.service_event_schedule_shutdown_block), "service.stop_deferred")
            return
        }
        if (ShotGate.active(shotState)) {
            stopShot()
            event(ResourceMessage(R.string.service_event_shot_shutdown_block), "service.stop_deferred")
            return
        }
        if (shotRecovery.pending) {
            event(ResourceMessage(R.string.service_event_shot_recovery_shutdown_block), "service.stop_deferred")
            return
        }
        if (machineWriteRecovery.pending) {
            event(ResourceMessage(R.string.service_event_write_shutdown_block), "service.stop_deferred")
            return
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event(ResourceMessage(R.string.service_event_preheat_shutdown_block), "service.stop_deferred")
            return
        }
        closeDeviceOwner(); running = false
        safetyMessage = null
        cancelSafetyNotification()
        handler.removeCallbacks(leaveForeground)
        handler.removeCallbacks(stopAutomaticScale)
        hubForeground = false
        snapshot = snapshot.copy(coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED,
            coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null, scanning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        (application as MobileApplication).feedbackPreferences.unobserve(ownerId)
        appVisibility.unobserve(ownerId)
        finishBrewFeedback(false)
        if (passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
            passiveHistoryId?.let { id -> runCatching { history?.abandon(id, "设备服务停止") } }
            saveSeriesCheckpoint(force = true)
            finishSeries(false)
        }
        handler.removeCallbacks(mockTick)
        cancelSafetyNotification()
        handler.removeCallbacks(watchShot)
        handler.removeCallbacks(leaveForeground)
        handler.removeCallbacks(stopAutomaticScale)
        closeDeviceOwner()
        super.onDestroy()
    }
    companion object {
        const val STOP = "io.openhoyi.mobile.STOP"
        const val AUTO_SCALE = "io.openhoyi.mobile.AUTO_SCALE"
        const val TAG = "OpenHoyiMobile"
        private const val CHANNEL = "connections"
        private const val SAFETY_CHANNEL = "shot_safety"
        private const val SAFETY_NOTIFICATION = 2
    }
}
