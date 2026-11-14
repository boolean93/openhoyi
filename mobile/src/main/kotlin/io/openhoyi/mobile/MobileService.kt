package io.openhoyi.mobile

import io.openhoyi.session.ShotDispatchPermit
import io.openhoyi.session.ShotRecoveryState
import io.openhoyi.session.MachineRecoveryActivity
import io.openhoyi.session.MachineWriteWatchdog
import io.openhoyi.session.MachineWriteDisconnection
import io.openhoyi.session.MachineWriteDispatchPermit
import io.openhoyi.session.MachineWriteResult
import io.openhoyi.session.MachineWriteRegistration
import io.openhoyi.session.MachineWriteAcknowledgement
import io.openhoyi.session.MachineWriteRecoveryState

import io.openhoyi.session.BrewWriteDispatchPermit
import io.openhoyi.session.BrewPreparationWatchdog
import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.PassiveShotDetector
import io.openhoyi.session.SettingsWriteTracker
import io.openhoyi.session.CupResetTracker
import io.openhoyi.session.OrdinaryWriteReadback
import io.openhoyi.session.CupResetReadback
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.SleepScheduleWriteTracker
import io.openhoyi.session.StandaloneTare
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import android.util.Log
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.ScaleObservation
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.protocol.SleepPart
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.session.CoffeeFirmware
import io.openhoyi.session.CustomCurveFirmwarePolicy
import io.openhoyi.session.CoffeeSessionFailure
import io.openhoyi.session.CoffeeAuthentication
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
import io.openhoyi.session.TelemetryFreshness
import io.openhoyi.session.SettingsFreshness
import io.openhoyi.session.BrewSettingsEvidence
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
    val scaleObservation: ScaleObservation? = null,
    val scaleCapabilities: ScaleCapabilities? = null,
    val candidates: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val message: SnapshotMessage? = null,
    /** Display-only reason for the last coffee FAILED state; never a control permit. */
    val coffeeFailure: CoffeeSessionFailure? = null,
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
    private var dispatchingCurveId: String? = null
    private val brewFeedback = BrewFeedbackTracker()
    private val feedbackDelivery = BrewFeedbackDelivery()
    private var feedbackShotId: String? = null
    private var feedbackAppShot = true
    internal var brewFeedbackResult: BrewFeedbackResult? = null
        private set
    private val passiveShot = PassiveShotDetector()
    private var passiveHistoryId: String? = null
    /** A started passive observation only owns history/series/navigation after it is committed. */
    private var passiveCommitted = false
    private val manualPresentation=ManualShotPresentationGate()
    val manualShotPresentationId: String? get()=manualPresentation.activeId
    private var recentReadyCoffeeAddress: String? = null
    private var initialKnownCoffeeChecked=false
    private var passiveMayClearRecovery = false
    private var passiveRecoveryOwner:ShotRecoveryState.Ownership?=null
    /** True when this passive round wrote the durable shot-recovery guard (not a pre-existing one). */
    private var passiveArmedRecovery = false
    /** Wall-clock start backfilled from the first passive frame, used when the cup is committed. */
    private var passiveStartedWallMs = 0L
    /** True when this passive round failed to arm its guard and therefore set its own warning. */
    private var passiveArmFailed = false
    /** Warning shown before this round armed, restored only when the uncommitted round is dropped. */
    private var passiveSafetyBeforeArm: Int? = null
    private data class AppRecoveryOwner(val hub:NativeDeviceHub,val address:String,val shot:ShotRecoveryState.Ownership,
        val machine:MachineWriteRecoveryState.Ownership?)
    private var appRecoveryOwner:AppRecoveryOwner?=null
    val manualShotActive: Boolean get() = passiveShot.active
    /** Display-only projection for the task shell; gates remain with the connection owners. */
    val automaticScaleWaiting: Boolean get() = hub?.automaticScaleWaiting == true
    private val shotRecovery by lazy { ShotRecoveryState(RecoveryPreferenceStorage.shot(this)) }
    private val restartShotWarning: String get() = getString(R.string.machine_recovery_shot_restart)
    private val machineWriteRecovery by lazy { MachineWriteRecoveryState(RecoveryPreferenceStorage.machine(this)) }
    private val machineWriteSafetyResource: Int? get() = MachineRecoveryText.resource(
        machineWriteRecovery.kind, brewPreparation.state)
    val machineWriteSafetyMessage: String? get() = machineWriteSafetyResource?.let { getString(it) }
    /** Display-only update gate. The service remains the sole machine-control authority. */
    internal val updateInterruptionBlocked: Boolean get() = releaseUpdateInterruptionBlocked(
        shotState, manualShotActive, scalePreflight, brewPreparation.active, tareState,
        shotRecovery.pending, machineWriteRecovery.pending, settingsWrite.state,
        cupResetBusy, scheduleBusy, sleepNow.state, leverInFlight != null, leverStore.blocked) ||
        (hub == null && io.openhoyi.bluetooth.SharedPreferenceTareStorage(this).read())
    val machineControlSafetyMessage: String? get() = leverWriteBlock ?: MachineControlGate.block(
        shotRecovery.pending, machineWriteSafetyMessage, restartShotWarning)
    val shotRecoveryClearBlock: String? get() = shotRecoveryClearResource?.let { getString(it) }
    private val shotRecoveryClearResource: Int? get() = shotRecoveryClearGate.resource(shotRecovery,
        hub?.coffeeAddress, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
        MobileMonotonicClock.elapsedRealtime(), shotState, manualShotActive)
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
    private var settingRecoveryOwner:MachineWriteRecoveryState.Ownership?=null
    private var cupRecoveryOwner:MachineWriteRecoveryState.Ownership?=null
    private var scheduleRecoveryOwner:MachineWriteRecoveryState.Ownership?=null
    private var sleepRecoveryOwner:MachineWriteRecoveryState.Ownership?=null
    private val settingsWrite = SettingsWriteTracker()
    private val cupReset = CupResetTracker()
    val cupResetState: CupResetTracker.State get() = if (mock?.cupReset == true)
        CupResetTracker.State.CONFIRMED else cupReset.state
    private val cupResetBusy: Boolean get() = MachineRecoveryActivity.isBusy(cupReset.state)
    private var cupSettingsSerial = 0L
    private var cupIdleSerial = 0L
    private fun observeCupCount(settingsFrame: Boolean, count: Int) {
        val sample = if (settingsFrame) CupResetReadback.Sample.Settings(++cupSettingsSerial,count)
            else CupResetReadback.Sample.Idle(++cupIdleSerial,count)
        val feedback = CupResetReadback.observe(cupReset,machineWriteRecovery,sample) { machineWriteRecovery.clear(cupRecoveryOwner) }
        refreshSafetyNotification()
        feedback.forEach { when(it) {
            CupResetReadback.Event.CONFIRMED -> event(ResourceMessage(R.string.service_event_cups_confirmed), "cups.confirmed")
            CupResetReadback.Event.RECONCILED -> event(ResourceMessage(R.string.service_event_cups_reconciled), "cups.reconciled")
            CupResetReadback.Event.CLEAR_FAILED -> event(ResourceMessage(R.string.service_event_cups_clear_failed), "cups.recovery_clear_failed")
        } }
    }
    private val scheduleWrite = SleepScheduleWriteTracker()
    private val sleepNow = SleepNowTracker()
    private val brewPreparation = BrewPreparation()
    private var autoPreheatIntent: io.openhoyi.session.AutoPreheatIntent? = null
    private var autoPreheatResource: ResourceMessage? = null
    val autoPreheatMessage: String? get() = autoPreheatResource?.render { id, args -> getString(id, *args) }
    var autoPreheatInitialTemperature: Int? = null; private set
    private var idleSampleSerial = 0L
    private var recoveryAfterBrewWaitIdleSerial = 0L
    private var brewWaitShotStarted = false
    val brewPreparationState: BrewPreparation.State get() = brewPreparation.state
    val brewPreparationProfileId: String? get() = brewPreparation.profileId
    val brewPreparationTargetC: Int? get() = brewPreparation.targetC
    val brewWaitCancelBlock: String? get() = brewWaitCancelBlockMessage?.render { id, args -> getString(id, *args) }
    private val brewWaitCancelBlockMessage: ResourceMessage? get() = if (mock != null) null else
        brewWaitCancelGate.blockMessage(snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            MobileMonotonicClock.elapsedRealtime(), shotState, shotRecovery.pending)
    private var sleepSampleSerial = 0L
    private val sleepNowUnresolved: Boolean get() = sleepNow.state == SleepNowTracker.State.UNKNOWN
    private val sleepNowUnresolvedMessage: String get() = getString(R.string.machine_recovery_sleep_unresolved)
    val sleepNowState: SleepNowTracker.State get() = if (mock?.isSleeping == true)
        SleepNowTracker.State.CONFIRMED else sleepNow.state
    private class BoilerFallback(val hub:NativeDeviceHub,val token:Long,val address:String,val generation:Long,
        val owner:MachineWriteRecoveryState.Ownership,val startedAt:Long) { var used=false }
    private var boilerFallback:BoilerFallback?=null
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
    internal var activeShotRecipe: ShotRecipeSnapshot? = null; private set
    private val extractionScaleReadout = ExtractionScaleReadout()
    private fun scaleReadoutOwner(current:NativeDeviceHub? = hub):ExtractionScaleReadout.Owner? = current?.let {
        val address = it.scaleAddress ?: return@let null
        ExtractionScaleReadout.Owner(it,it.extraction,it.extraction.displayShotSerial,scaleConnectionEpoch,address)
    }
    private fun observeScaleReadoutProof() {
        val controller = hub?.extraction
        val proof = controller?.displayTareBaseline?.let {
            ExtractionScaleReadout.Proof(it.shotSerial,it.baselineHundredthsGram,it.confirmedAtMs)
        }
        extractionScaleReadout.observeProof(scaleReadoutOwner(),proof,controller?.state ?: shotState)
    }
    internal fun extractionScaleValue(now: Long): ExtractionScaleReadout.Value {
        observeScaleReadoutProof()
        return extractionScaleReadout.value(snapshot.scaleObservation, snapshot.scaleState, now,
            shotState == ExtractionState.OUTCOME_UNKNOWN)
    }
    private fun observeExtractionScaleEndpoint(frame: HoyiMessage, at: Long) {
        observeScaleReadoutProof()
        if (frame is io.openhoyi.protocol.ExtractionTelemetry && frame.valveOpen && !frame.brewWait)
            extractionScaleReadout.brewing()
        if (frame is IdleTelemetry)
            extractionScaleReadout.observedIdle(snapshot.scaleObservation, snapshot.scaleState, at)
    }
    val chartTraceIdentity: String? get() = series.traceIdentity
    val chartPoints: List<ShotPoint> get() = series.points
    val chartCurveId: String? get() = series.curveId
    private val ownerId = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private val preheatTimeoutScheduler:(Long,()->Unit)->Unit={ delay,action->
        handler.postDelayed({ action() },delay)
    }
    private val writeWatchdog = MachineWriteWatchdog(
        { delay, callback -> handler.postDelayed({ callback() }, delay) },
        { MachineWriteWatchdog.Serials(settingsSampleSerial, cupSettingsSerial, cupIdleSerial,
            firstSleepSerial, secondSleepSerial, sleepSampleSerial) })
    private val writeDisconnection = MachineWriteDisconnection(settingsWrite,cupReset,scheduleWrite,sleepNow,brewPreparation)
    private var hub: NativeDeviceHub? = null
    private val coffeeCredentials by lazy { CoffeeCredentialStore(this) }
    private val coffeeCredentialRetries by lazy {
        CoffeeCredentialRetryGate(store = SharedPreferencesCoffeeFailureStore(this))
    }
    private var pendingCoffeeCredential: PendingCoffeeCredential? = null
    private val mock = if (BuildConfig.MOCK_MODE) MockDeviceRuntime { getString(it) } else null
    private val mockExtractionScenario = MockExtractionScenario(BuildConfig.MOCK_MODE,
        { Looper.myLooper() === Looper.getMainLooper() }, MobileMonotonicClock::ownsMockClock,
        { hub == null }, MobileMonotonicClock::elapsedRealtime, ::onCoffeeFrame,
        { onDeviceState(DeviceRole.COFFEE, it) })
    private var mockExtractionOwner: Any? = null
    internal fun installMockExtractionScenario(owner: Any) {
        check(mock != null && running && snapshot.coffeeState == DeviceState.READY &&
            shotState == ExtractionState.IDLE && chartTraceIdentity == null)
        mockExtractionScenario.install(owner)
        mockExtractionOwner = owner
    }
    internal fun clearMockExtractionScenario(owner: Any) {
        check(mockExtractionOwner === owner)
        mockExtractionScenario.clear(owner)
        mockExtractionOwner = null
    }
    internal val mockExtractionOfferedFrameSerial get() = mockExtractionScenario.offeredFrameSerial
    internal val mockExtractionPendingStartId get() = mockExtractionScenario.pendingStartId
    internal val mockExtractionPendingStopId get() = mockExtractionScenario.pendingStopId
    internal fun completeMockExtractionStart(owner: Any, id: Long, result: OperationResult) =
        mockExtractionScenario.completeStart(owner, id, result)
    internal fun completeMockExtractionStop(owner: Any, id: Long, result: OperationResult) =
        mockExtractionScenario.completeStop(owner, id, result)
    internal fun offerMockExtractionCoffeeState(owner: Any, state: DeviceState) =
        mockExtractionScenario.coffeeState(owner, state)
    internal fun offerMockExtractionFrame(owner: Any, frame: HoyiMessage) =
        mockExtractionScenario.offerCoffeeFrame(owner, frame)
    internal fun tickMockExtraction(owner: Any) = mockExtractionScenario.tick(owner)
    private val mockScaleVisualInput = MockScaleVisualInput(BuildConfig.MOCK_MODE,
        { Looper.myLooper() === Looper.getMainLooper() }, MobileMonotonicClock::ownsMockClock,
        { hub == null }, MobileMonotonicClock::elapsedRealtime,
        { onDeviceState(DeviceRole.BOOKOO, it) }, ::onScaleObservation,
        { scaleTrend.clear(); beanDoseCapture.reset() })
    private fun requireScaleVisualAccess(owner: Any) {
        check(BuildConfig.MOCK_MODE && mock != null) { "Scale visual input is Mock-only" }
        check(Looper.myLooper() === Looper.getMainLooper()) { "Scale visual input requires main thread" }
        check(MobileMonotonicClock.ownsMockClock(owner)) { "Scale visual input clock owner mismatch" }
        check(hub == null) { "Scale visual input cannot coexist with a device hub" }
    }
    internal fun installScaleVisualInput(owner: Any) {
        requireScaleVisualAccess(owner)
        mockScaleVisualInput.install(owner)
        scaleTrend.clear()
        beanDoseCapture.reset()
        applyScaleVisualInput()
    }
    internal fun offerScaleVisualInput(owner: Any, state: DeviceState, observation: ScaleObservation?) {
        requireScaleVisualAccess(owner)
        mockScaleVisualInput.offer(owner, state, observation)
        applyScaleVisualInput()
    }
    internal fun clearScaleVisualInput(owner: Any) {
        requireScaleVisualAccess(owner)
        mockScaleVisualInput.clear(owner)
        snapshot = snapshot.copy(scaleState = DeviceState.DISCONNECTED, scaleObservation = null,
            scaleCapabilities = null)
    }
    private fun applyScaleVisualInput() {
        mockScaleVisualInput.current()?.let { input ->
            snapshot = snapshot.copy(scaleState = input.state, scaleObservation = input.observation,
                scaleCapabilities = input.observation?.capabilities)
        }
    }
    private fun refreshMock(runtime: MockDeviceRuntime, now: Long = MobileMonotonicClock.elapsedRealtime()) {
        val previous = snapshot
        snapshot = runtime.sample(now).copy(candidates = previous.candidates,
            scanning = previous.scanning, message = previous.message)
        if (mockScaleVisualInput.isInstalled) {
            applyScaleVisualInput() // Injected visual input ticks never create observations.
        } else {
            snapshot.scaleObservation?.let(::onScaleObservation)
                ?: scaleTrend.observe(null, snapshot.scaleState, now)
        }
        snapshot.coffee?.let { observeExtractionScaleEndpoint(it, now) }
    }
    private val mockTick = object : Runnable {
        override fun run() {
            val runtime = mock ?: return
            if (!running) return
            mockExtractionOwner?.let { owner ->
                // No offered telemetry means no new sample, even while the UI and
                // original controller watchdog continue to receive clock ticks.
                mockExtractionScenario.tick(owner)
                handler.postDelayed(this, 250)
                return
            }
            val now = MobileMonotonicClock.elapsedRealtime()
            refreshMock(runtime, now)
            (snapshot.coffee as? IdleTelemetry)?.let { idle ->
                val currentSettings = snapshot.settings
                if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                        BrewPreparation.correctedTemperature(idle.brewTemperatureHundredthsC,
                            currentSettings.brewCompensationTenthsC)))
                    event(ResourceMessage(R.string.service_event_mock_temperature_ready), "mock.brew_wait_ready")
            }
            (snapshot.coffee as? io.openhoyi.protocol.ExtractionTelemetry)?.let { frame ->
                // Mock samples use the same observation/history projection as real telemetry.
                // These remain synthetic and never authorize a Bluetooth write.
                recordMachinePoint(frame, now)
            }
            handler.postDelayed(this, 250)
        }
    }
    private val appVisibility: AppVisibility get() = (application as MobileApplication).visibility
    private var hubForeground = false
    private var safetyMessage: String? = null
    private var safetyNotificationDirty = false
    private var manualSafetyResource: Int? = null
    val manualSafetyMessage: String? get() = manualSafetyResource?.let { getString(it) }
    private val autoConnect get()=(application as MobileApplication).autoConnect
    private var selectingDevice=false
    fun beginDeviceSelection(){selectingDevice=true;hub?.refreshAutomaticIntent(autoConnect.paused(DeviceRole.BOOKOO),autoConnect.paused(DeviceRole.COFFEE))}
    fun endDeviceSelection(){
        selectingDevice=false
        hub?.scanner?.cancel(io.openhoyi.bluetooth.ScanCoordinator.Owner.MANUAL)
        snapshot=snapshot.copy(scanning=false)
    }
    private fun automaticAllowed(role:DeviceRole):Boolean = io.openhoyi.session.AutomaticConnectionGate.allows(
        role,autoConnect.enabled(role),autoConnect.paused(role),appVisibility.visible,
        selectingDevice || snapshot.scanning,manualShotActive,shotState,shotRecovery.pending,
        machineWriteRecovery.kind!=null,cupResetBusy || scheduleBusy || brewPreparation.active)
    private fun refreshAutomaticIntent(){
        hub?.refreshAutomaticIntent(autoConnect.paused(DeviceRole.BOOKOO),autoConnect.paused(DeviceRole.COFFEE))
        initialKnownCoffeeChecked=false
        ensureKnownDevices()
    }
    private var automaticScaleOnly = false
    private val stopAutomaticScale = object : Runnable {
        override fun run() {
            if (!automaticScaleOnly || !running) return
            if(appVisibility.visible){handler.postDelayed(this,30_000);return}
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
            selectingDevice=false
            hub?.background(keepCoffeeRecovery=ShotGate.active(shotState)||manualShotActive)
            hubForeground = false
            snapshot = snapshot.copy(scanning = false)
            if (automaticScaleOnly && snapshot.coffeeState != DeviceState.READY &&
                snapshot.scaleState in setOf(DeviceState.DISCONNECTED, DeviceState.FAILED)) shutdown()
        }
    }
    private var lastShotState = ExtractionState.IDLE
    var running = false; private set
    private val shortcutConfiguration = ShortcutContentRevision<List<Any?>>(
        listOf(null, DeviceState.DISCONNECTED, DeviceState.DISCONNECTED, null))
    internal val shortcutConfigurationRevision get() = shortcutConfiguration.revision
    var snapshot = MobileSnapshot()
        private set(value) {
            shortcutConfiguration.observe(listOf(value.settings, value.coffeeState, value.scaleState, value.scaleCapabilities))
            if (value.coffeeState != DeviceState.READY || value.coffee is io.openhoyi.protocol.ExtractionTelemetry)
                autoPreheatIntent?.invalidate()
            field = value
        }
    val coffeeFirmware: CoffeeFirmware? get() = if(mock != null)
        snapshot.settings?.let { CoffeeFirmware(it.firmwareMajor,it.firmwareMinor,it.firmwarePatch) }
        else hub?.coffeeFirmware
    val machineSettingsFresh: Boolean get() = !staleSettingsRefreshBusy && snapshot.settings != null &&
        SettingsFreshness.isFresh(snapshot.settingsAt, MobileMonotonicClock.elapsedRealtime())
    val machineBrewSettingsAvailable: Boolean get() = BrewSettingsEvidence.isAvailable(
        snapshot.coffeeState == DeviceState.READY, snapshot.settings != null,
        snapshot.settingsAt, MobileMonotonicClock.elapsedRealtime())
    val sleepScheduleFresh: Boolean get() = SleepScheduleFreshness.isFresh(
        snapshot.sleepFirst, snapshot.sleepFirstAt, snapshot.sleepSecond, snapshot.sleepSecondAt,
        MobileMonotonicClock.elapsedRealtime())
    val shotState: ExtractionState get() = if (mockExtractionOwner != null) mockExtractionScenario.state
        else mock?.shotState ?: hub?.extraction?.state ?: ExtractionState.IDLE
    var activeShotTargetHundredthsGram: Int? = null; private set
    val stopReason: String? get() = if (mockExtractionOwner != null) mockExtractionScenario.stopReason
        else hub?.extraction?.stopReason?.name
    val scalePreflight: Boolean get() = if (mockExtractionOwner != null) mockExtractionScenario.preparingScale
        else hub?.extraction?.preparingScale == true
    val tareStartBlock: String? get() = when(tareState) {
        StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO -> getString(R.string.service_tare_start_wait)
        StandaloneTare.State.UNKNOWN -> getString(R.string.service_tare_start_unknown)
        else -> null
    }
    private var lastTareState = StandaloneTare.State.IDLE
    private fun observeTare() {
        val current = tareState
        if(current == lastTareState) return
        logTarePhase(current)
        lastTareState = current
        if(current == StandaloneTare.State.CONFIRMED) event(ResourceMessage(R.string.service_tare_confirmed), "scale.tare_confirmed")
        if(current == StandaloneTare.State.UNKNOWN) event(ResourceMessage(R.string.service_tare_unknown), "scale.tare_unknown")
    }
    private fun logTarePhase(current: StandaloneTare.State) {
        if (current == lastTareState) return
        logs.record("scale.tare_phase", mapOf("from" to lastTareState.name, "to" to current.name,
            "shotState" to shotState.name, "preparingScale" to scalePreflight.toString()))
    }
    private val watchShot = object : Runnable {
        override fun run() {
            observeTare()
            refreshStaleMachineSettings()
            val current = shotState
            if (current != lastShotState) {
                val previous = lastShotState
                lastShotState = current
                val now = MobileMonotonicClock.elapsedRealtime()
                val weight = ScaleBusinessReading.terminalWeight(snapshot.scaleObservation, snapshot.scaleState, now)
                runCatching { history?.transition(current, stopReason, weight) }
                    .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
                if (current == ExtractionState.ENDED_OBSERVED || current == ExtractionState.IDLE) {
                    finishSeries(current == ExtractionState.ENDED_OBSERVED)
                    val originalOwner=appRecoveryOwner
                    val shotRecordCleared=if(originalOwner==null) !shotRecovery.pending else
                        hub === originalOwner.hub && snapshot.coffeeState==DeviceState.READY && !manualShotActive &&
                            hub?.coffeeAddress?.equals(originalOwner.address,ignoreCase=true)==true &&
                            shotRecovery.clear(originalOwner.shot)
                    if(shotRecordCleared)appRecoveryOwner=null
                    if (!shotRecordCleared)
                        manualSafetyResource = R.string.service_event_shot_clear_failed
                    if (current == ExtractionState.ENDED_OBSERVED && shotRecordCleared && brewWaitShotStarted &&
                        machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT &&
                        machineWriteRecovery.matchesDevice(hub?.coffeeAddress)) {
                        if (!machineWriteRecovery.clear(originalOwner?.machine))
                            event(ResourceMessage(R.string.service_event_preheat_clear_failed), "brew_wait.recovery_clear_failed")
                        brewWaitShotStarted = false
                    }
                }
                if (current == ExtractionState.OUTCOME_UNKNOWN) {
                    saveSeriesCheckpoint(force = true)
                    finishBrewFeedback(false)
                }
                if (!appVisibility.visible && !ShotGate.active(current) && !manualShotActive) hub?.background()
                event(ResourceMessage(R.string.service_event_shot_state, current.name), "shot.state")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.TARE_UNCONFIRMED.name)
                    event(ResourceMessage(if (tareState in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO))
                        R.string.service_event_tare_cancelled_waiting else R.string.service_event_tare_unconfirmed), "shot.preflight_failed")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.START_CONDITIONS_CHANGED.name)
                    event(ResourceMessage(R.string.service_event_conditions_changed), "shot.conditions_changed")
                refreshSafetyNotification()
            }
            handler.postDelayed(this, 100)
        }
    }

    /** Invalidates UI stability even if a disconnect/reconnect occurs between page refreshes. */
    private val scaleTrend = ScaleTrendWindow()
    private var mockScaleVisualObservationSerial = 0L
    internal fun scaleTrendAt(now: Long): List<ScaleTrendWindow.Point> = scaleTrend.points(now)
    private var scaleConnectionEpoch = 0L
    private val beanDoseCapture = BeanDoseCapturePolicy()
    internal fun resetBeanDoseCapture() = beanDoseCapture.reset()
    internal fun beanDoseCaptureAt(now: Long): Int? {
        if (BuildConfig.MOCK_MODE || manualShotActive ||
            !io.openhoyi.session.DeviceConnectionGate.mayChangeScale(shotState) ||
            tareState in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO)) {
            beanDoseCapture.reset(); return null
        }
        return beanDoseCapture.update(scaleConnectionEpoch,
            LiveTelemetry.scale(snapshot.scaleObservation, snapshot.scaleState, now), now)
    }
    private fun onScaleObservation(observation: ScaleObservation) {
        if (BuildConfig.MOCK_MODE && mockScaleVisualInput.isInstalled) mockScaleVisualObservationSerial++
        scaleTrend.observe(observation, snapshot.scaleState, MobileMonotonicClock.elapsedRealtime())
        snapshot = snapshot.copy(scaleObservation = observation, scaleCapabilities = observation.capabilities)
        // Consume every notification, not just the last frame at the UI refresh.
        beanDoseCaptureAt(MobileMonotonicClock.elapsedRealtime())
    }
    fun requestCoffeeRecoverySoon(){if(appVisibility.visible)hub?.requestCoffeeRecoverySoon()}
    private fun onDeviceState(role: DeviceRole, state: DeviceState) {
        if (role == DeviceRole.COFFEE && state != DeviceState.READY) settleLeverWriteUnknown()

        if(state==DeviceState.READY)selectingDevice=false
        if (role == DeviceRole.BOOKOO && state != DeviceState.READY) {
            scaleConnectionEpoch++; beanDoseCapture.reset()
            scaleTrend.observe(null, state, MobileMonotonicClock.elapsedRealtime())
        }
        snapshot = if (role == DeviceRole.COFFEE) {
            if (state != DeviceState.READY) {
                finishBrewFeedback(false)
                saveSeriesCheckpoint(force = true)
                writeDisconnection.apply(MachineWriteDisconnection.Samples(settingsSampleSerial,
                    cupSettingsSerial,cupIdleSerial,firstSleepSerial,secondSleepSerial,sleepSampleSerial))
            }
            if (state == DeviceState.DISCONNECTED || state == DeviceState.FAILED)
                snapshot.copy(coffeeState = state, coffee = null, coffeeAt = null,
                    alarmBits = null, alarmAt = null,
                    settings = null, settingsAt = null, sleepFirst = null, sleepSecond = null, sleepFirstAt = null, sleepSecondAt = null,
                    coffeeFailure = if (state == DeviceState.FAILED) hub?.coffeeFailure else null)
            else snapshot.copy(coffeeState = state, coffeeFailure = null)
        } else if (state != DeviceState.READY) {
            snapshot.copy(scaleState = state, scaleObservation = null, scaleCapabilities = null)
        }
        else snapshot.copy(scaleState = state)
        if (role == DeviceRole.COFFEE) when (state) {
            DeviceState.READY -> {
                hub?.coffeeAddress?.let { address ->
                    recentReadyCoffeeAddress=address
                    if(!getSharedPreferences("devices",MODE_PRIVATE).edit().putString("coffee",address).commit())
                        event(ResourceMessage(R.string.service_event_credential_save_failed),"coffee.address_save_failed")
                }
                pendingCoffeeCredential?.let { credential ->
                    credential.passwordToSave(hub?.coffeeAddress, state)?.let { password ->
                        if (!coffeeCredentials.save(credential.address, password))
                            event(ResourceMessage(R.string.service_event_credential_save_failed), "coffee.credential_save_failed")
                    }
                    coffeeCredentialRetries.connectionState(credential.address, state, credential.remembered)
                    pendingCoffeeCredential = null
                }
            }
            DeviceState.FAILED, DeviceState.UNSUPPORTED -> pendingCoffeeCredential?.let { credential ->
                coffeeCredentialRetries.connectionState(credential.address, state, credential.remembered)
                pendingCoffeeCredential = null
            }
            else -> Unit
        }
        if (role == DeviceRole.COFFEE && state != DeviceState.READY &&
            passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
            val committed = passiveCommitted
            passiveCommitted = false
            if (committed) {
                manualPresentation.disconnected()
                passiveHistoryId?.let { id ->
                    runCatching { history?.abandon(id, "连接中断") }
                        .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                }
                passiveHistoryId = null
                passiveArmedRecovery = false
                passiveMayClearRecovery = false
                passiveRecoveryOwner = null
                saveSeriesCheckpoint(force = true)
                finishSeries(false)
                manualSafetyResource = R.string.service_event_manual_disconnect_warning
                event(ResourceMessage(R.string.service_event_manual_unknown), "shot.passive_unknown")
            } else {
                // A short passive water release was never a cup: drop its provisional chart and
                // this round's guard without abandoning history or raising an unknown warning.
                discardPassiveSeries(requireDeviceMatch = false)
            }
        }
        event("${getString(if (role == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale)}: ${state.name}")
        if (role == DeviceRole.COFFEE) refreshSafetyNotification()
    }

    private fun onCoffeeFrame(frame: HoyiMessage) {
        if (frame is io.openhoyi.protocol.ExtractionTelemetry && (frame.valveOpen || frame.brewWait))
            interruptUpdateInstallation()
        snapshot = when (frame) {
            is Settings -> {
                observeCupCount(true, frame.cupCount)
                if (OrdinaryWriteReadback.settings(settingsWrite,machineWriteRecovery,++settingsSampleSerial,frame,{ machineWriteRecovery.clear(settingRecoveryOwner) },
                    machineWriteRecovery.owns(settingRecoveryOwner) && hub?.boilerReconciliationReady==true &&
                        hub?.coffeeAddress?.equals(machineWriteRecovery.address,ignoreCase=true)==true) { feedback ->
                    when(feedback) {
                        OrdinaryWriteReadback.Event.CONFIRMED -> event(ResourceMessage(R.string.service_event_setting_confirmed), "settings.confirmed")
                        OrdinaryWriteReadback.Event.RECONCILED -> event(ResourceMessage(R.string.service_event_setting_reconciled), "settings.reconciled")
                        OrdinaryWriteReadback.Event.CLEAR_FAILED -> event(ResourceMessage(R.string.service_write_setting_clear_failed), "settings.recovery_clear_failed")
                    }
                }) refreshSafetyNotification()
                if(settingsWrite.state in setOf(SettingsWriteTracker.State.CONFIRMED,SettingsWriteTracker.State.RECONCILED) &&
                    !machineWriteRecovery.pending) {
                    boilerFallback=null
                    hub?.completeBoilerReadbackRecovery()
                }
                // Authentication may adopt an earlier received frame after GATT completion.
                // Preserve its receipt time; an absent native observation is not fresh evidence.
                snapshot.copy(settings = frame, settingsAt = if(mock!=null) MobileMonotonicClock.elapsedRealtime()
                    else hub?.coffeeSettingsObservedAtMs)
            }
            is SleepPart -> {
                if (frame.firstDaySundayIndex == 0) {
                    firstSleepSerial++
                    snapshot = snapshot.copy(sleepFirst = frame, sleepFirstAt = MobileMonotonicClock.elapsedRealtime(), sleepSecondAt = null)
                } else {
                    secondSleepSerial++
                    snapshot = snapshot.copy(sleepSecond = frame, sleepSecondAt = MobileMonotonicClock.elapsedRealtime())
                }
                if (OrdinaryWriteReadback.schedule(scheduleWrite,machineWriteRecovery,sleepScheduleFresh,
                        firstSleepSerial,secondSleepSerial,snapshot.sleepFirst,snapshot.sleepSecond,{ machineWriteRecovery.clear(scheduleRecoveryOwner) }) { feedback ->
                    when(feedback) {
                        OrdinaryWriteReadback.Event.CONFIRMED -> event(ResourceMessage(R.string.service_write_schedule_confirmed), "sleep_schedule.confirmed")
                        OrdinaryWriteReadback.Event.RECONCILED -> event(ResourceMessage(R.string.service_event_schedule_reconciled), "sleep_schedule.reconciled")
                        OrdinaryWriteReadback.Event.CLEAR_FAILED -> event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                    }
                }) refreshSafetyNotification()
                snapshot
            }
            is IdleTelemetry -> {
                observeCupCount(false, frame.cupCount)
                if (OrdinaryWriteReadback.sleep(sleepNow,machineWriteRecovery,++sleepSampleSerial,frame.sleepStateRaw,{ machineWriteRecovery.clear(sleepRecoveryOwner) }) { feedback ->
                    when(feedback) {
                        OrdinaryWriteReadback.Event.CONFIRMED -> event(ResourceMessage(R.string.service_event_sleep_confirmed), "sleep.confirmed")
                        OrdinaryWriteReadback.Event.RECONCILED -> event(ResourceMessage(R.string.service_event_sleep_reconciled), "sleep.reconciled")
                        OrdinaryWriteReadback.Event.CLEAR_FAILED -> event(ResourceMessage(R.string.service_write_sleep_clear_failed), "sleep.recovery_clear_failed")
                    }
                }) refreshSafetyNotification()
                val currentSettings = snapshot.settings
                if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                        BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
                            currentSettings.brewCompensationTenthsC)))
                    event(ResourceMessage(R.string.service_event_preheat_ready), "brew_wait.ready")
                val observedAt = MobileMonotonicClock.elapsedRealtime()
                snapshot.copy(coffee = frame, coffeeAt = observedAt,
                    alarmBits = frame.alarmBits, alarmAt = observedAt)
            }
            is io.openhoyi.protocol.ExtractionTelemetry ->
                snapshot.copy(coffee = frame, coffeeAt = MobileMonotonicClock.elapsedRealtime())
            else -> snapshot
        }
        val observedAt = snapshot.coffeeAt ?: MobileMonotonicClock.elapsedRealtime()
        observeExtractionScaleEndpoint(frame, observedAt)
        val appShotInProgress = shotState !in setOf(ExtractionState.IDLE, ExtractionState.ENDED_OBSERVED) ||
            lastShotState !in setOf(ExtractionState.IDLE, ExtractionState.ENDED_OBSERVED)
        val passiveEvents = if (snapshot.coffeeState == DeviceState.READY)
            passiveShot.observe(frame, observedAt, appShotInProgress) else emptyList()
        // Water is flowing now, so the realtime chart and navigation start at Started under a
        // provisional identity. Only Committed keeps a manual identity and a history entry.
        passiveEvents.forEach { passiveEvent ->
            when (passiveEvent) {
                is PassiveShotDetector.Event.Started -> {
                    val previousRecovery = shotRecovery.pending
                    if (!previousRecovery) manualSafetyResource = null
                    val safetyBeforeArm = manualSafetyResource
                    val currentAddress = hub?.coffeeAddress
                    val armed = shotRecovery.arm(currentAddress)
                    passiveArmedRecovery = armed && !previousRecovery
                    passiveMayClearRecovery = armed &&
                        shotRecovery.mayClearAfterPassiveShot(previousRecovery, currentAddress)
                    passiveRecoveryOwner=if(passiveMayClearRecovery)shotRecovery.captureOwnership() else null
                    passiveArmFailed = !armed
                    passiveSafetyBeforeArm = if (armed) null else safetyBeforeArm
                    if (!armed)
                        manualSafetyResource = R.string.service_event_shot_record_failed
                    passiveCommitted = false
                    passiveHistoryId = null
                    // The cup began at the first observed frame; keep durable history aligned.
                    passiveStartedWallMs = System.currentTimeMillis() -
                        (observedAt - passiveEvent.first.atMs)
                    val provisionalId = java.util.UUID.randomUUID().toString()
                    series.begin(provisionalId, passiveEvent.first.atMs, "manual")
                    extractionScaleReadout.begin(provisionalId, null)
                    extractionScaleReadout.brewing()
                    manualPresentation.started(provisionalId)
                    val feedbackSlot = passiveEvent.first.frame.slotOrPhase
                    beginBrewFeedback(provisionalId, feedbackSlot, if (feedbackSlot == 6) 0 else null,
                        appShot = false)
                    recordMachinePoint(passiveEvent.first.frame, passiveEvent.first.atMs)
                    recordMachinePoint(passiveEvent.second.frame, passiveEvent.second.atMs)
                    refreshSafetyNotification()
                }
                PassiveShotDetector.Event.Committed -> {
                    passiveCommitted = true
                    val historyId = runCatching {
                        history?.begin("manual", atMs = passiveStartedWallMs, slot = 6)
                    }.onFailure {
                        event(ResourceMessage(R.string.service_event_manual_history_unwritable),
                            "shot.history_error")
                    }.getOrNull()
                    if (historyId != null) {
                        passiveHistoryId = historyId
                        series.rebind(historyId)
                        runCatching { history?.transition(ExtractionState.RUNNING, "机器手动萃取", null) }
                            .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                        event(ResourceMessage(R.string.service_event_manual_started), "shot.passive_started")
                    }
                }
                is PassiveShotDetector.Event.Point -> recordMachinePoint(passiveEvent.value.frame,
                    passiveEvent.value.atMs)
                PassiveShotDetector.Event.Ended -> {
                    val committed = passiveCommitted
                    if (committed) {
                        passiveCommitted = false
                        manualPresentation.ended()
                        clearPassiveRecovery(requireDeviceMatch = true)
                        val weight = ScaleBusinessReading.terminalWeight(snapshot.scaleObservation, snapshot.scaleState, observedAt)
                        passiveHistoryId?.let {
                            runCatching { history?.transition(ExtractionState.ENDED_OBSERVED,
                                "机器待机回报", weight) }
                                .onFailure { event(ResourceMessage(R.string.service_event_manual_history_failed), "shot.history_error") }
                        }
                        passiveHistoryId = null
                        finishSeries(true)
                        if(!appVisibility.visible && !ShotGate.active(shotState))hub?.background()
                        event(ResourceMessage(R.string.service_event_manual_ended), "shot.passive_ended")
                    } else {
                        discardPassiveSeries(requireDeviceMatch = true)
                    }
                }
                PassiveShotDetector.Event.Interrupted -> Unit
            }
        }
        if (passiveEvents.isEmpty() && frame is io.openhoyi.protocol.ExtractionTelemetry &&
            !passiveShot.active) recordMachinePoint(frame, observedAt)
        if(frame is io.openhoyi.protocol.IdleTelemetry && !passiveShot.active)
            manualPresentation.observedIdle()
        if(frame is IdleTelemetry)attemptBoilerFallback()
    }

    /** Releases the guard a committed passive cup armed under the existing evidence rules. */
    private fun clearPassiveRecovery(requireDeviceMatch: Boolean) {
        val matched = !requireDeviceMatch || shotRecovery.matchesDevice(hub?.coffeeAddress)
        if (!passiveMayClearRecovery || !matched || !shotRecovery.clear(passiveRecoveryOwner))
            manualSafetyResource = R.string.service_event_shot_clear_failed
        passiveArmedRecovery = false
        passiveMayClearRecovery = false
        passiveRecoveryOwner = null
        passiveArmFailed = false
        passiveSafetyBeforeArm = null
    }

    /**
     * Ends a provisional passive series that never became a cup: no history entry is written and
     * only this round's recovery guard is cleared. finishSeries(false) clears only the active id,
     * so the page keeps showing the just-observed realtime curve and its manual identity until the
     * next series.begin (an App shot or another passive release) replaces them.
     */
    private fun discardPassiveSeries(requireDeviceMatch: Boolean) {
        passiveCommitted = false
        manualPresentation.ended()
        finishBrewFeedback(false)
        finishSeries(false)
        passiveHistoryId = null
        passiveStartedWallMs = 0L
        discardPassiveRecovery(requireDeviceMatch)
    }

    /**
     * Drops only the record this round created. A record that was already pending before the
     * observation belongs to an earlier shot and stays untouched.
     */
    private fun discardPassiveRecovery(requireDeviceMatch: Boolean) {
        if (passiveArmedRecovery) {
            val matched = !requireDeviceMatch || shotRecovery.matchesDevice(hub?.coffeeAddress)
            if (!matched || !shotRecovery.clear(passiveRecoveryOwner))
                manualSafetyResource = R.string.service_event_shot_clear_failed
        }
        passiveArmedRecovery = false
        passiveMayClearRecovery = false
        passiveRecoveryOwner = null
        // A dropped short release must not leave the arm-failure warning this round raised.
        if (passiveArmFailed && manualSafetyResource == R.string.service_event_shot_record_failed) {
            manualSafetyResource = passiveSafetyBeforeArm
            refreshSafetyNotification()
        }
        passiveArmFailed = false
        passiveSafetyBeforeArm = null
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MobileApplication
        app.releaseUpdates.serviceCreated(this)
        app.curveDeletionGuards[ownerId] = { id ->
            CurveDeletionPolicy.inUse(id, dispatchingCurveId, series.curveId,
                ShotGate.active(shotState) || scalePreflight,
                brewPreparation.profileId, brewPreparation.active)
        }
        app.feedbackPreferences.observe(ownerId) { enabled -> if (!enabled) feedbackDelivery.clear() }
        logs = app.logs
        history = runCatching { app.history }.getOrNull()
        if (mock == null && shotRecovery.pending) manualSafetyResource = R.string.machine_recovery_shot_restart
        handler.post(watchShot)
        app.autoConnect.onChanged=::refreshAutomaticIntent
        app.visibility.observe(ownerId, ::onAppVisibilityChanged)
    }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { shutdown(); return START_NOT_STICKY }
        if (running) {
            if (intent?.action !in setOf(AUTO_SCALE,AUTO_KNOWN_DEVICES)) manualDeviceUse()
            refreshAutomaticIntent()
            return START_NOT_STICKY
        }
        if (mock != null) {
            running = true
            snapshot = mock.sample(MobileMonotonicClock.elapsedRealtime())
            handler.post(mockTick)
            event(ResourceMessage(R.string.service_event_mock_started), "mock.started")
            (application as MobileApplication).serviceAvailability.started(ownerId)
            return START_NOT_STICKY
        }
        automaticScaleOnly = intent?.action == AUTO_SCALE || intent?.action == AUTO_KNOWN_DEVICES
        safetyMessage = null
        try {
            val manager = getSystemService(NotificationManager::class.java)
            createNotificationChannels(manager)
            val note = connectionNotification(null)
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, note)
            val prefs = getSharedPreferences("devices", MODE_PRIVATE)
            recentReadyCoffeeAddress=prefs.getString("coffee",null)
            hub = NativeDeviceHub(applicationContext, prefs.getString("scale", null),
                    tareStorage = io.openhoyi.bluetooth.SharedPreferenceTareStorage(applicationContext),
                rememberedScaleProtocolId = prefs.getString("scaleProtocol", null),
                onScaleSelectionRemembered = { address, protocolId ->
                    if (!prefs.edit().putString("scale", address).putString("scaleProtocol", protocolId).commit())
                        event(ResourceMessage(R.string.scale_memory_failed), "scale.memory_failed")
                },
                onState = ::onDeviceState,
                onCoffee = ::onCoffeeFrame,
                onScaleObservation = ::onScaleObservation,
                diagnostic = { detail ->
                    if(detail.startsWith("coffee.auto_reconnect.trigger=") || detail.startsWith("coffee.auto_reconnect.blocked="))
                        logs.record("coffee.auto_reconnect",mapOf("signal" to detail.removePrefix("coffee.auto_reconnect.")))
                    else if (detail.startsWith("scale.auto_reconnect.")) event(detail, "scale.auto_reconnect")
                    else if(detail.startsWith("coffee.auto_reconnect."))event(detail,"coffee.auto_reconnect")
                    else if(ConnectionDiagnosticDisplay.isInformational(detail))
                        logs.record("ble.diagnostic.info",mapOf("detail" to detail,"ownerId" to ownerId))
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
                customPressureStartPermit = { parameters ->
                    (application as MobileApplication).curves.permitsCustomPressureStart(
                        getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null), parameters)
                },
                customCurveStartPermit = { parameters ->
                    CustomCurveFirmwarePolicy.permits(parameters, coffeeFirmware) && (application as MobileApplication).curves.permitsCustomStart(
                        getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null), parameters)
                },
                onCoffeeReconnect = { address ->
                    if(recentReadyCoffeeAddress?.equals(address,ignoreCase=true)!=true)false
                    else connectRememberedCoffee(address,automatic=true)==RememberedCoffeeConnection.STARTED
                },
                rememberedScaleSuppressed=autoConnect.paused(DeviceRole.BOOKOO),
                automaticAllowed=::automaticAllowed,
            )
            running = true
            event(ResourceMessage(R.string.service_event_started))
            refreshSafetyNotification()
            refreshAutomaticIntent()
            scheduleAutomaticScaleStop()
            if (appVisibility.visible) {
                hub?.foreground()
                hubForeground = true
            }
            (application as MobileApplication).serviceAvailability.started(ownerId)
        } catch (error: RuntimeException) {
            event(ResourceMessage(R.string.service_event_startup_failed, error.javaClass.simpleName))
            shutdown()
        }
        return START_NOT_STICKY
    }
    private fun onAppVisibilityChanged(visible: Boolean) {
        if (visible) {
            refreshAutomaticIntent()
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
                DiscoveredDevice("02:00:00:00:00:02", "BOOKOO Mock", -45, DeviceRole.BOOKOO,
                    io.openhoyi.session.BookooScaleProtocolAdapter.id)),
                scanning = false)
            event(ResourceMessage(R.string.service_scan_mock), "mock.scan")
            return
        }
        val current = hub ?: return
        manualDeviceUse()
        beginDeviceSelection()
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
    /** Explicit recovery after physical lever wake; never sends a wake/start/heating command. */
    val canReconnectAfterSleep: Boolean get() {
        val idle = LiveTelemetry.machine(snapshot.coffee, snapshot.coffeeState, snapshot.coffeeAt,
            MobileMonotonicClock.elapsedRealtime()) as? IdleTelemetry
        return SleepReconnectPolicy.allowed(running, snapshot.coffeeState, sleepNowState,
            idle?.sleepStateRaw, (snapshot.coffee as? IdleTelemetry)?.sleepStateRaw,
            manualShotActive || ShotGate.active(shotState), settingWriteState,
            brewPreparationState != BrewPreparation.State.IDLE,
            cupResetBusy || scheduleBusy || leverInFlight != null || leverStore.blocked || machineWriteRecovery.pending)
    }
    fun reconnectAfterSleep(address: String): RememberedCoffeeConnection {
        if (!canReconnectAfterSleep) return RememberedCoffeeConnection.BLOCKED
        // Mock can exercise the action without pretending it wakes a sleeping machine.
        mock?.let { refreshMock(it); return RememberedCoffeeConnection.STARTED }
        return connectRememberedCoffee(address, automatic = false)
    }
    fun connectRememberedCoffee(address: String): RememberedCoffeeConnection = connectRememberedCoffee(address,automatic=false)
    fun ensureKnownDevices() {
        if(mock!=null || !running || initialKnownCoffeeChecked)return
        initialKnownCoffeeChecked=true
        val prefs=getSharedPreferences("devices",MODE_PRIVATE)
        if(!autoConnect.enabled(DeviceRole.COFFEE)||autoConnect.paused(DeviceRole.COFFEE))return
        val address=recentReadyCoffeeAddress ?: return
        if(!shotRecovery.matchesDevice(address) || !machineWriteRecovery.matchesDevice(address))return
        // Credential availability is checked by the reconnect callback, so a temporary
        // Keystore failure at startup cannot consume this one-time restore opportunity.
        hub?.ensureKnownCoffee(address)
    }
    private fun connectRememberedCoffee(address: String,automatic:Boolean): RememberedCoffeeConnection {
        if(coffeeConnectionInProgress(address))return RememberedCoffeeConnection.STARTED
        val allowed = (!automatic || automaticAllowed(DeviceRole.COFFEE)) && coffeeConnectionAllowed(address, automatic)
        val result = RememberedCoffeeConnection.attempt(allowed, {
            coffeeCredentials.readState(address).also {
                if (it == CoffeeCredentialRead.Unavailable)
                    event(ResourceMessage(R.string.service_coffee_credential_unavailable), "coffee.credential_unavailable")
            }
        }) { password -> startCoffeeConnection(address, password, remembered = true, automatic = automatic) }
        if (automatic && result == RememberedCoffeeConnection.PASSWORD_REQUIRED) hub?.disableCoffeeRecovery()
        return result
    }
    fun connectCoffee(address: String, password: String) {
        if (!coffeeConnectionAllowed(address, automatic = false)) return
        require(password.matches(Regex("[0-9]{6}")))
        startCoffeeConnection(address, password, remembered = false, automatic = false)
    }
    private fun coffeeConnectionAllowed(address: String, automatic: Boolean): Boolean {
        if (mock != null) { event(ResourceMessage(R.string.service_coffee_mock), "mock.connect"); return false }
        if (manualShotActive) { event(ResourceMessage(R.string.service_connection_manual_block)); return false }
        leverStore.blockingAttempt?.let {
            if (leverInFlight != null || !it.snapshot.address.equals(address, ignoreCase = true)) {
                event(ResourceMessage(R.string.lever_reconnect_original), "lever_recipe.device_mismatch"); return false
            }
        }
        if (!shotRecovery.matchesDevice(address)) {
            event(ResourceMessage(R.string.service_connection_shot_device_mismatch), "shot.device_mismatch"); return false
        }
        if (!machineWriteRecovery.matchesDevice(address)) {
            event(ResourceMessage(R.string.service_connection_write_device_mismatch), "machine_write.device_mismatch"); return false
        }
        if(!automatic)manualDeviceUse()
        if (cupResetBusy) { event(ResourceMessage(R.string.service_connection_cups_busy)); return false }
        if (scheduleBusy) { event(ResourceMessage(R.string.service_connection_schedule_busy)); return false }
        if (!ShotGate.mayReconnectCoffee(shotState)) {
            event(ResourceMessage(R.string.service_connection_shot_busy)); return false
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event(ResourceMessage(R.string.service_connection_cancel_preheat), "brew_wait.connect_deferred")
            return false
        }
        return hub != null
    }
    private fun coffeeConnectionInProgress(address:String)=TaskShellDeviceStatus.connecting(snapshot.coffeeState) &&
        pendingCoffeeCredential?.address?.equals(address,ignoreCase=true)==true
    private fun startCoffeeConnection(address: String, password: String, remembered: Boolean, automatic: Boolean): Boolean {
        if(!automatic)boilerFallback=null
        if(coffeeConnectionInProgress(address))return true
        val current = hub ?: return false
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null,
            settings = null, settingsAt = null, sleepFirst = null, sleepSecond = null, sleepFirstAt = null, sleepSecondAt = null)
        event(ResourceMessage(if (remembered) R.string.service_coffee_connect_remembered else R.string.home_connect_coffee_title))
        pendingCoffeeCredential = PendingCoffeeCredential(address, password, remembered)
        try { current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password),explicit=!automatic,
            remembered=recentReadyCoffeeAddress?.equals(address,ignoreCase=true)==true) }
        catch (error: RuntimeException) {
            pendingCoffeeCredential = null
            event(ResourceMessage(R.string.service_coffee_connect_failed, error.javaClass.simpleName), "coffee.connect_failed")
            return false
        }
        if(!automatic)autoConnect.pause(DeviceRole.COFFEE,false)
        return true
    }
    fun connectScale(address: String, protocolId: String) {
        if (io.openhoyi.session.ScaleSelectionPolicy.adapter(protocolId) == null) {
            event(ResourceMessage(R.string.scale_protocol_unsupported)); return
        }
        if (mock != null) { event(ResourceMessage(R.string.service_scale_mock), "mock.connect"); return }
        if (manualShotActive) { event(ResourceMessage(R.string.service_scale_manual_block)); return }
        manualDeviceUse()
        if (ShotGate.active(shotState)) { event(ResourceMessage(R.string.service_scale_shot_block)); return }
        val current = hub ?: return
        if (current.scaleConnectionBlocked) {
            event(ResourceMessage(R.string.scale_connection_close_uncertain), "scale.close_uncertain"); return
        }
        try {
            if (!current.connectScale(address, protocolId)) { event(ResourceMessage(R.string.service_scale_already_connected)); return }
        } catch (_: RuntimeException) {
            event(ResourceMessage(if (current.scaleConnectionBlocked) R.string.scale_connection_close_uncertain
                else R.string.service_event_communication_error), "scale.connect_failed"); return
        }
        autoConnect.pause(DeviceRole.BOOKOO,false)
        snapshot = snapshot.copy(scaleObservation = null, scaleCapabilities = null)
        scaleTrend.clear() // A selected scale starts a new display history, not a mixed-device line.
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
        event(ResourceMessage(R.string.service_disconnect,
            getString(if (role == DeviceRole.COFFEE) R.string.home_coffee else R.string.home_scale)))
        if (role == DeviceRole.COFFEE) {
            hub?.disconnectCoffee()
            autoConnect.pause(DeviceRole.COFFEE,true)
        } else {
            hub?.disconnectScale()
            autoConnect.pause(DeviceRole.BOOKOO,true)
        }
    }
    /** UI intent revocation only; it cannot authorize, cancel or replay a machine operation. */
    private fun interruptUpdateInstallation() {
        (application as? MobileApplication)?.let { app -> runCatching { app.releaseUpdates.interruptInstallLease() } }
    }
    fun tareScale(): String? {
        interruptUpdateInstallation()
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
        val scaleAddress=current.scaleAddress
        scaleConnectionEpoch++ // A tare attempt invalidates any bean stability window.
        beanDoseCapture.reset()
        event(ResourceMessage(R.string.service_tare_requested), "scale.tare_requested")
        current.tareScale({
            io.openhoyi.session.StandaloneTareDispatchPermit.allows(scaleAddress,current.scaleAddress,
                hub === current,snapshot.scaleState==DeviceState.READY,manualShotActive,shotState)
        }) done@{ result ->
            if(hub !== current)return@done
            logTarePhase(tareState)
            lastTareState = tareState
            when (tareState) {
                StandaloneTare.State.WAITING_ZERO -> event(ResourceMessage(R.string.service_tare_written), "scale.tare_written")
                StandaloneTare.State.UNKNOWN -> event(ResourceMessage(R.string.service_tare_unknown), "scale.tare_unknown")
                else -> if (result !is OperationResult.Success) event(ResourceMessage(R.string.service_tare_failed), "scale.tare_failed")
            }
        }
        return null
    }
    private fun permitsOrdinaryWrite(current:NativeDeviceHub,address:String,token:Long,
        request:MachineWriteDispatchPermit.Request):Boolean = hub === current && snapshot.coffeeState == DeviceState.READY &&
        MachineWriteDispatchPermit.allows(token,request,MachineWriteDispatchPermit.Context(machineWriteRecovery,
            address,current.coffeeAddress,manualShotActive,shotRecovery.pending,ShotGate.active(shotState),brewPreparation.active))

    private fun boilerRecoveryProductSafe():Boolean = !manualShotActive && !shotRecovery.pending &&
        !io.openhoyi.session.DeviceConnectionGate.unsettled(shotState) && !brewPreparation.active && !cupResetBusy && !scheduleBusy &&
        sleepNow.state !in setOf(SleepNowTracker.State.WRITING,SleepNowTracker.State.WAITING_ASLEEP,SleepNowTracker.State.UNKNOWN)
    private fun attemptBoilerFallback() {
        val pending=boilerFallback ?: return
        val current=hub ?: return
        val now=MobileMonotonicClock.elapsedRealtime()
        if(pending.used || now<pending.startedAt+3000 || now>=pending.startedAt+6000 ||
            current!==pending.hub || current.coffeeGeneration!=pending.generation ||
            !machineWriteRecovery.owns(pending.owner) || !boilerRecoveryProductSafe() ||
            !permitsOrdinaryWrite(current,pending.address,pending.token,
                MachineWriteDispatchPermit.Request.BoilerSettingsRefresh(settingsWrite)) || !current.canReconnectBoilerSettings)return
        pending.used=true // connect synchronously publishes DISCONNECTED; consume before any observer.
        val started=current.reconnectBoilerSettings({hub===current && machineWriteRecovery.owns(pending.owner) &&
            boilerRecoveryProductSafe() && current.coffeeGeneration==pending.generation &&
            permitsOrdinaryWrite(current,pending.address,pending.token,
                MachineWriteDispatchPermit.Request.BoilerSettingsRefresh(settingsWrite))},
            {hub===current && machineWriteRecovery.owns(pending.owner) && boilerRecoveryProductSafe()})
        logs.record("settings.reconnect",mapOf("ownerId" to ownerId,"outcome" to if(started)"started" else "rejected"))
    }
    val boilerReadbackRecoveryActive:Boolean get()=HomeBoilerSwitchPresentation.localProgress(
        machineWriteRecovery.owns(settingRecoveryOwner),settingsWrite.change,settingsWrite.state,
        boilerReadbackReconnecting,boilerSettingsRecoveryBusy)
    val boilerReadbackReconnecting:Boolean get()=hub?.boilerReadbackReconnecting==true
    private fun recordBoilerRefresh(manual:Boolean,attempt:Int,result:OperationResult) {
        logs.record("settings.refresh",mapOf("ownerId" to ownerId,"manual" to manual.toString(),
            "attempt" to attempt.toString(),"outcome" to when(result) {
                is OperationResult.Success->"transport_success"
                is OperationResult.Failed->"failed"
                is OperationResult.Cancelled->"cancelled"
                is OperationResult.Unknown->"unknown"
            }))
    }
    val boilerSettingsRecoveryBusy:Boolean get()=hub?.boilerSettingsRecoveryBusy==true
    val canRefreshUnknownBoilerSettings:Boolean get() {
        val token=settingsWrite.boilerRecoveryToken() ?: return false
        val current=hub ?: return false
        val address=machineWriteRecovery.address ?: return false
        val owner=settingRecoveryOwner ?: return false
        return machineWriteRecovery.owns(owner) && boilerRecoveryProductSafe() && current.canRefreshUnknownBoilerSettings &&
            permitsOrdinaryWrite(current,address,token,MachineWriteDispatchPermit.Request.BoilerSettingsRecovery(settingsWrite))
    }
    fun refreshUnknownBoilerSettings():String? {
        if(!canRefreshUnknownBoilerSettings)return getString(R.string.service_setting_unresolved)
        val current=requireNotNull(hub)
        val token=requireNotNull(settingsWrite.boilerRecoveryToken())
        val address=requireNotNull(machineWriteRecovery.address)
        val owner=requireNotNull(settingRecoveryOwner)
        current.refreshUnknownBoilerSettings({machineWriteRecovery.owns(owner) && boilerRecoveryProductSafe() &&
            permitsOrdinaryWrite(current,address,token,MachineWriteDispatchPermit.Request.BoilerSettingsRecovery(settingsWrite))}) {attempt,result->
            recordBoilerRefresh(true,attempt,result)
        }
        return null
    }

    fun changeMachineSetting(change: MachineSettingChange): String? {
        interruptUpdateInstallation()
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
        val now = MobileMonotonicClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        if (!TelemetryFreshness.isFresh(snapshot.coffeeAt, now))
            return getString(R.string.service_write_idle_stale)
        if (idle.sleepStateRaw != 0) return getString(R.string.service_write_setting_awake_block)
        val observed = snapshot.settings ?: return getString(R.string.settings_missing)
        if (!machineSettingsFresh) return getString(R.string.service_write_settings_stale)
        if (change is MachineSettingChange.LeverMode &&
            !io.openhoyi.session.MachineModeFirmwarePolicy.permits(change, coffeeFirmware))
            return getString(R.string.slow_global_firmware_required)
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
        val recoveryOwner=requireNotNull(machineWriteRecovery.captureOwnership())
        settingRecoveryOwner=recoveryOwner
        recoveryAfterSettingsSerial = settingsSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_setting_queued, settingsPresentation.changeMessage(change)), "settings.requested")
        current.writeSetting(change,{ machineWriteRecovery.owns(recoveryOwner) && permitsOrdinaryWrite(current,coffeeAddress,token,MachineWriteDispatchPermit.Request.Setting(settingsWrite)) }) done@{ result ->
            val outcome = MachineWriteResult.apply(token, result, MachineWriteResult.Request.Setting(settingsWrite, settingsSampleSerial))
            if (outcome == MachineWriteResult.Outcome.IGNORED) return@done
            when (outcome) {
                MachineWriteResult.Outcome.WAITING -> {
                    event(ResourceMessage(R.string.service_write_setting_written), "settings.written")
                    writeWatchdog.await(token, MachineWriteWatchdog.Request.Setting(settingsWrite)) { serials ->
                        recoveryAfterSettingsSerial = serials.settings
                        event(ResourceMessage(R.string.service_write_setting_no_readback), "settings.unknown")
                    }
                    if(change is MachineSettingChange.BrewHeating || change is MachineSettingChange.SteamHeating) {
                        // First arm WAITING/watchdog, then request one fresh 0x83 via authenticated
                        // time sync. Its GATT success is not evidence that the setting was applied.
                        val fallback=BoilerFallback(current,token,coffeeAddress,current.coffeeGeneration,recoveryOwner,
                            MobileMonotonicClock.elapsedRealtime())
                        boilerFallback=fallback
                        handler.postDelayed({if(boilerFallback===fallback)attemptBoilerFallback()},3000)
                        current.refreshBoilerSettings({machineWriteRecovery.owns(recoveryOwner) && boilerRecoveryProductSafe() &&
                            permitsOrdinaryWrite(current,coffeeAddress,token,
                                MachineWriteDispatchPermit.Request.BoilerSettingsRefresh(settingsWrite))}) { attempt,refreshResult ->
                            recordBoilerRefresh(false,attempt,refreshResult)
                        }
                    }
                }
                MachineWriteResult.Outcome.FAILED -> {
                    if (!machineWriteRecovery.clear(recoveryOwner))
                        event(ResourceMessage(R.string.service_write_setting_clear_failed), "settings.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_setting_not_written), "settings.failed")
                }
                MachineWriteResult.Outcome.UNKNOWN -> {
                    recoveryAfterSettingsSerial = settingsSampleSerial
                    event(ResourceMessage(R.string.service_write_setting_unknown), "settings.unknown")
                }
                else -> Unit
            }
        }
        return null
    }
    private val settingsRefreshBudget = SettingsRefreshBudget()
    private var staleSettingsRefreshBusy = false
    private fun settingsRefreshSafe(): Boolean {
        val idle = snapshot.coffee as? IdleTelemetry ?: return false
        return SettingsRefreshEligibility.allowed(
            foreground = running && mock == null && appVisibility.visible,
            readyIdle = snapshot.coffeeState == DeviceState.READY &&
                TelemetryFreshness.isFresh(snapshot.coffeeAt, MobileMonotonicClock.elapsedRealtime()) &&
                idle.sleepStateRaw == 0 && io.openhoyi.session.CoffeeAlarmPolicy.permitsNewControl(idle.alarmBits),
            controlRecoveryBlocked = MachineControlGate.block(shotRecovery.pending, machineWriteSafetyMessage, restartShotWarning) != null,
            leverActive = leverInFlight != null || leverStore.active,
            operationActive = manualShotActive || ShotGate.active(shotState) || brewPreparation.active || cupResetBusy || scheduleBusy ||
                settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                    SettingsWriteTracker.State.UNKNOWN) || sleepNow.state in setOf(SleepNowTracker.State.WRITING,
                    SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN))
    }
    /** A bounded settings synchronization, not a retry of the user's machine command. */
    private fun refreshStaleMachineSettings(explicit: Boolean = false) {
        if (staleSettingsRefreshBusy || machineSettingsFresh || !settingsRefreshSafe()) return
        val current = hub ?: return
        val generation = current.coffeeGeneration
        if (!current.canRefreshStaleSettings) return
        if (!settingsRefreshBudget.claim(generation, snapshot.settingsAt, MobileMonotonicClock.elapsedRealtime(), explicit)) return
        staleSettingsRefreshBusy = true
        logMachineStateGate("settings.refresh_started")
        current.refreshStaleSettings({ hub === current && current.coffeeGeneration == generation && settingsRefreshSafe() }) { result ->
            staleSettingsRefreshBusy = false
            logs.record("settings.refresh_finished", mapOf("result" to result.javaClass.simpleName,
                "version" to BuildConfig.VERSION_NAME))
        }
    }
    private fun logMachineStateGate(event: String) {
        val now = MobileMonotonicClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry
        logs.record(event, mapOf("version" to BuildConfig.VERSION_NAME,
            "firmware" to coffeeFirmware.toString(), "state" to snapshot.coffeeState.name,
            "idleAgeMs" to (snapshot.coffeeAt?.let { now-it }?.toString() ?: "missing"),
            "settingsAgeMs" to (snapshot.settingsAt?.let { now-it }?.toString() ?: "missing"),
            "sleep" to idle?.sleepStateRaw.toString(), "alarms" to idle?.alarmBits.toString()))
    }

    // The framework-attached Application owns the journal even when the base context is isolated.
    // Detached upgrade checks have only a base context. Never substitute an empty journal.
    private val leverStore get() = ((application as? MobileApplication)
        ?: (applicationContext as MobileApplication)).leverRecipes
    private var leverInFlight: String? = null
    internal val leverDeviceAddress: String? get() = if (mock != null) "00:00:00:00:00:01" else hub?.coffeeAddress
    private val leverWriteBlock: String? get() = if (leverStore.blocked)
        getString(if (leverStore.active) R.string.lever_sending else R.string.lever_unresolved) else null

    /** No complete recipe readback exists. This API reports transport only, never durable save. */
    internal fun leverRecipeWriteBlock(): String? {
        leverWriteBlock?.let { return it }
        val blocked = leverRecipeConditions()
        if (blocked != null) {
            logMachineStateGate("lever_recipe.blocked")
            refreshStaleMachineSettings(explicit = true)
        }
        return blocked
    }
    private fun leverRecipeConditions(): String? {
        MachineControlGate.block(shotRecovery.pending, machineWriteSafetyMessage, restartShotWarning)?.let { return it }
        if (manualShotActive || ShotGate.active(shotState) || brewPreparation.active || cupResetBusy || scheduleBusy ||
            settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN) || sleepNow.state in setOf(SleepNowTracker.State.WRITING,
                SleepNowTracker.State.WAITING_ASLEEP, SleepNowTracker.State.UNKNOWN))
            return getString(R.string.service_write_operation_pending)
        if (mock != null) return null
        if (snapshot.coffeeState != DeviceState.READY) return getString(R.string.start_block_coffee_not_ready)
        val idle = snapshot.coffee as? IdleTelemetry ?: return getString(R.string.service_write_idle_waiting)
        if (!TelemetryFreshness.isFresh(snapshot.coffeeAt, MobileMonotonicClock.elapsedRealtime()))
            return getString(R.string.lever_gate_sync)
        if (idle.sleepStateRaw != 0) return getString(R.string.lever_gate_asleep)
        if (!io.openhoyi.session.CoffeeAlarmPolicy.permitsNewControl(idle.alarmBits)) return getString(R.string.lever_gate_alarm)
        if (!machineSettingsFresh) return getString(R.string.lever_gate_sync)
        if (coffeeFirmware !in listOf(CoffeeFirmware(1, 1, 3), CoffeeFirmware(1, 1, 4), CoffeeFirmware(1, 1, 5)))
            return getString(R.string.lever_firmware_unsupported)
        return null
    }
    internal fun sendLeverRecipe(profile: CurveProfile, expectedAddress: String, expectedFirmware: String,
        outcome: (String) -> Unit): String? {
        interruptUpdateInstallation()
        leverRecipeWriteBlock()?.let { return it }
        if (!CustomCurveFirmwarePolicy.permitsStoredRecipe(profile.parameters, coffeeFirmware))
            return getString(R.string.lever_firmware_unsupported)
        val app = application as MobileApplication
        if (leverDeviceAddress != expectedAddress || coffeeFirmware.toString() != expectedFirmware ||
            app.curves.resolve(profile.id, false, 7) != profile || !app.curves.validated(profile))
            return getString(R.string.lever_changed)
        val frame = runCatching { io.openhoyi.protocol.StoredLeverRecipeFormat.candidate(profile.parameters).hex() }
            .getOrNull() ?: return getString(R.string.lever_changed)
        val current = hub
        val generation = current?.coffeeGeneration
        val token = leverStore.begin(MachineLeverRecipeStore.Snapshot(expectedAddress, profile.id, profile.name,
            frame, System.currentTimeMillis(), expectedFirmware)) ?: return getString(R.string.lever_record_failed)
        leverInFlight = token
        event(ResourceMessage(R.string.lever_sending), "lever_recipe.requested")
        fun complete(result: OperationResult) {
            if (leverInFlight != token) return
            leverInFlight = null
            val sameOwner = mock != null || (hub === current && current?.coffeeGeneration == generation &&
                leverDeviceAddress == expectedAddress)
            val state = when {
                !sameOwner -> MachineLeverRecipeStore.State.UNKNOWN
                result is OperationResult.Success -> MachineLeverRecipeStore.State.SENT
                result is OperationResult.Failed || result is OperationResult.Cancelled -> MachineLeverRecipeStore.State.FAILED
                else -> MachineLeverRecipeStore.State.UNKNOWN
            }
            val persisted = leverStore.complete(token, state)
            val resource = when {
                !persisted -> R.string.lever_record_failed
                state == MachineLeverRecipeStore.State.SENT -> R.string.lever_sent
                state == MachineLeverRecipeStore.State.FAILED -> R.string.lever_not_sent
                else -> R.string.lever_result_unknown
            }
            event(ResourceMessage(resource), "lever_recipe.result")
            if (persisted && state == MachineLeverRecipeStore.State.SENT && mock == null && current != null) {
                // Read fresh mode/settings once. 0x83 is NOT stored-recipe verification.
                current.refreshStoredRecipeSettings({
                    hub === current && current.coffeeGeneration == generation && leverDeviceAddress == expectedAddress &&
                        snapshot.coffeeState == DeviceState.READY && !leverStore.blocked &&
                        !manualShotActive && !ShotGate.active(shotState) && !brewPreparation.active &&
                        !machineWriteRecovery.pending && !cupResetBusy && !scheduleBusy &&
                        settingWriteState !in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
                }) { refresh ->
                    if (refresh !is OperationResult.Success)
                        logs.record("lever_recipe.settings_refresh_unconfirmed", mapOf("result" to refresh.javaClass.simpleName))
                }
            }
            outcome(getString(resource))
        }
        if (mock != null) { complete(OperationResult.Success()); return null }
        if (current == null) { complete(OperationResult.Failed("no owner")); return null }
        try { current.writeStoredLeverRecipe(profile.parameters, {
            leverInFlight == token && leverStore.pending(token) && hub === current &&
                current.coffeeGeneration == generation && leverDeviceAddress == expectedAddress &&
                coffeeFirmware.toString() == expectedFirmware && leverRecipeConditions() == null &&
                app.curves.resolve(profile.id, false, 7) == profile && app.curves.validated(profile)
        }, { snapshot = snapshot.copy(settingsAt = null) }, ::complete)
        } catch (_: Exception) { complete(OperationResult.Unknown("recipe submission interrupted")) }
        return null
    }
    private fun settleLeverWriteUnknown() {
        leverInFlight?.let { token ->
            leverInFlight = null
            leverStore.complete(token, MachineLeverRecipeStore.State.UNKNOWN)
        }
    }
    internal fun acknowledgeLeverRecipeUnknown(): String? {
        if (leverInFlight != null) return getString(R.string.lever_sending)
        val record = leverStore.blockingAttempt ?: return getString(R.string.lever_record_failed)
        if (!record.snapshot.address.equals(leverDeviceAddress, ignoreCase = true) || leverRecipeConditions() != null) {
            refreshStaleMachineSettings(explicit = true)
            return getString(R.string.lever_reconnect_original)
        }
        if (!leverStore.acknowledge(record.token)) return getString(R.string.lever_record_failed)
        event(ResourceMessage(R.string.lever_result_unknown), "lever_recipe.acknowledged")
        return null
    }

    fun resetCupCount(expectedCount: Int): String? {
        interruptUpdateInstallation()
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
        val now = MobileMonotonicClock.elapsedRealtime()
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
        val recoveryOwner=requireNotNull(machineWriteRecovery.captureOwnership())
        cupRecoveryOwner=recoveryOwner
        recoveryAfterSettingsSerial = cupSettingsSerial
        recoveryAfterIdleSerial = cupIdleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_cups_queued), "cups.requested")
        current.resetCupCount(expectedCount,{ machineWriteRecovery.owns(recoveryOwner) && permitsOrdinaryWrite(current,coffeeAddress,token,MachineWriteDispatchPermit.Request.CupReset(cupReset)) }) done@{ result ->
            val outcome = MachineWriteResult.apply(token, result, MachineWriteResult.Request.CupReset(cupReset, cupSettingsSerial, cupIdleSerial))
            if (outcome == MachineWriteResult.Outcome.IGNORED) return@done
            when (outcome) {
                MachineWriteResult.Outcome.WAITING -> {
                    event(ResourceMessage(R.string.service_write_cups_written), "cups.written")
                    writeWatchdog.await(token, MachineWriteWatchdog.Request.CupReset(cupReset)) { serials ->
                        recoveryAfterSettingsSerial = serials.cupSettings
                        recoveryAfterIdleSerial = serials.cupIdle
                        event(ResourceMessage(R.string.service_write_cups_no_readback), "cups.unknown")
                    }
                }
                MachineWriteResult.Outcome.FAILED -> {
                    if (!machineWriteRecovery.clear(recoveryOwner))
                        event(ResourceMessage(R.string.service_write_cups_clear_failed), "cups.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_cups_not_written), "cups.failed")
                }
                MachineWriteResult.Outcome.UNKNOWN -> {
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
        interruptUpdateInstallation()
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
        val now = MobileMonotonicClock.elapsedRealtime()
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
        val recoveryOwner=requireNotNull(machineWriteRecovery.captureOwnership())
        scheduleRecoveryOwner=recoveryOwner
        recoveryAfterFirstSleepSerial = firstSleepSerial
        recoveryAfterSecondSleepSerial = secondSleepSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_schedule_queued), "sleep_schedule.requested")
        current.writeSleepSchedule(target,expected,{ machineWriteRecovery.owns(recoveryOwner) && permitsOrdinaryWrite(current,coffeeAddress,token,MachineWriteDispatchPermit.Request.Schedule(scheduleWrite)) }) done@{ result ->
            val outcome = MachineWriteResult.apply(token, result, MachineWriteResult.Request.Schedule(scheduleWrite, firstSleepSerial, secondSleepSerial, snapshot.sleepFirst, snapshot.sleepSecond))
            if (outcome == MachineWriteResult.Outcome.IGNORED) return@done
            when (outcome) {
                MachineWriteResult.Outcome.CONFIRMED -> {
                    if (!machineWriteRecovery.clear(recoveryOwner))
                        event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_schedule_confirmed), "sleep_schedule.confirmed")
                }
                MachineWriteResult.Outcome.WAITING -> {
                    event(ResourceMessage(R.string.service_write_schedule_written), "sleep_schedule.written")
                    writeWatchdog.await(token, MachineWriteWatchdog.Request.Schedule(scheduleWrite)) { serials ->
                        recoveryAfterFirstSleepSerial = serials.sleepFirst
                        recoveryAfterSecondSleepSerial = serials.sleepSecond
                        event(ResourceMessage(R.string.service_write_schedule_no_readback), "sleep_schedule.unknown")
                    }
                }
                MachineWriteResult.Outcome.FAILED -> {
                    if (!machineWriteRecovery.clear(recoveryOwner))
                        event(ResourceMessage(R.string.service_write_schedule_clear_failed), "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_schedule_first_not_written), "sleep_schedule.failed")
                }
                MachineWriteResult.Outcome.UNKNOWN -> {
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
        interruptUpdateInstallation()
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
        val now = MobileMonotonicClock.elapsedRealtime()
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
        val recoveryOwner=requireNotNull(machineWriteRecovery.captureOwnership())
        sleepRecoveryOwner=recoveryOwner
        recoveryAfterSleepSerial = sleepSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_write_sleep_queued), "sleep.requested")
        current.enterSleep({ machineWriteRecovery.owns(recoveryOwner) && permitsOrdinaryWrite(current,coffeeAddress,token,MachineWriteDispatchPermit.Request.Sleep(sleepNow)) }) done@{ result ->
            val outcome = MachineWriteResult.apply(token, result, MachineWriteResult.Request.Sleep(sleepNow, sleepSampleSerial))
            if (outcome == MachineWriteResult.Outcome.IGNORED) return@done
            when (outcome) {
                MachineWriteResult.Outcome.WAITING -> {
                    event(ResourceMessage(R.string.service_write_sleep_written), "sleep.written")
                    writeWatchdog.await(token, MachineWriteWatchdog.Request.Sleep(sleepNow)) { serials ->
                        recoveryAfterSleepSerial = serials.sleep
                        event(ResourceMessage(R.string.service_write_sleep_no_readback), "sleep.unknown")
                    }
                }
                MachineWriteResult.Outcome.FAILED -> {
                    if (!machineWriteRecovery.clear(recoveryOwner))
                        event(ResourceMessage(R.string.service_write_sleep_clear_failed), "sleep.recovery_clear_failed")
                    refreshSafetyNotification()
                    event(ResourceMessage(R.string.service_write_sleep_not_written), "sleep.failed")
                }
                MachineWriteResult.Outcome.UNKNOWN -> {
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
                .getString(PresetSlots.key(slot), null), (application as MobileApplication).curves)
        else null
        return profileId.takeIf { it == selectedId }?.let {
            (application as MobileApplication).curves.resolve(it, snapshot.scaleState == DeviceState.READY, slot)
        }
    }
    fun currentCorrectedBrewTemperature(): Int? {
        val frame = snapshot.coffee as? IdleTelemetry ?: return null
        val settings = snapshot.settings ?: return null
        val now = MobileMonotonicClock.elapsedRealtime()
        if (snapshot.coffeeState != DeviceState.READY || !machineBrewSettingsAvailable ||
            !TelemetryFreshness.isFresh(snapshot.coffeeAt, now)) return null
        return BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
            settings.brewCompensationTenthsC)
    }
    /** One read-only reason for the grey start action in both extraction screens. No writes. */
    private fun customCurveFirmwareBlock(profile: CurveProfile?): String? {
        val configured = profile?.let { (application as MobileApplication).curves.find(it.id)?.customDocument?.configuration } != null
        return if (configured && !CustomCurveFirmwarePolicy.permits(requireNotNull(profile).parameters, coffeeFirmware))
            getString(if (CustomCurveFirmwarePolicy.isPressureFollow(profile.parameters))
                R.string.follow_firmware_required else R.string.flow_editor_firmware_required) else null
    }
    fun extractionStartBlock(profile: CurveProfile?, invalidCurve: Boolean = false): String? {
        if (!running) return getString(R.string.service_unavailable)
        machineControlSafetyMessage?.let { return it }
        if (manualShotActive) return getString(R.string.extraction_manual_block)
        if (invalidCurve) return getString(R.string.extraction_invalid_curve_block)
        customCurveFirmwareBlock(profile)?.let { return it }
        tareStartBlock?.let { return it }
        shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            snapshot.scaleState, snapshot.scaleObservation, MobileMonotonicClock.elapsedRealtime(), shotState,
            validated = profile?.let((application as MobileApplication).curves::validated) == true)?.let { return it }
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK,
                SettingsWriteTracker.State.UNKNOWN)) return getString(R.string.service_shot_settings_start_block)
        if (sleepNowState in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP,
                SleepNowTracker.State.UNKNOWN)) return getString(R.string.service_shot_sleep_start_block)
        // Temperature preparation is start-only: callers must still be able to preheat.
        return null
    }
    fun studioStartReason(profile:CurveProfile):io.openhoyi.session.PreheatGate.StartBlock? =
        if(!machineBrewSettingsAvailable)io.openhoyi.session.PreheatGate.StartBlock.SETTINGS_MISSING
        else io.openhoyi.session.PreheatGate.startBlock(snapshot.settings,currentCorrectedBrewTemperature(),
            profile.id,profile.temperatureC,brewPreparation)
    fun studioStartBlock(profile: CurveProfile): String? = if (!machineBrewSettingsAvailable)
        getString(R.string.studio_settings_stale) else studioStartGate.block(
        snapshot.settings, currentCorrectedBrewTemperature(), profile, brewPreparation)
    /** Explicit use only. No retry on service binding, render, settings refresh or reconnect. */
    fun useCurveForPreheat(profileId: String, expectedScaleMode: Boolean?, slot: Int): String? {
        val old = autoPreheatIntent
        if (old != null && old.profileId == profileId && old.targetC == selectedCurve(profileId, slot)?.temperatureC &&
            old.slot == slot && old.requestId == brewPreparation.requestId &&
            old.permitsStart(shortcutDeviceIdentity, shortcutConfigurationRevision) && brewPreparation.active) return null
        leaveAutoPreheat()
        autoPreheatResource = null
        autoPreheatInitialTemperature = null
        val settings = snapshot.settings ?: return autoPreheatRejected(ResourceMessage(R.string.settings_missing))
        if (settings.flags and 0x04 == 0) return null
        val profile = selectedCurve(profileId, slot)
            ?: return autoPreheatRejected(ResourceMessage(R.string.start_block_curve_missing))
        if (!machineBrewSettingsAvailable) return autoPreheatRejected(ResourceMessage(R.string.studio_settings_stale))
        val actual = currentCorrectedBrewTemperature()
            ?: return autoPreheatRejected(ResourceMessage(R.string.service_auto_temperature_waiting))
        if (BrewPreparation.isAtTarget(actual, profile.temperatureC)) return null
        // 1.1.4 clips positive 0x11 targets to 96 C. Do not wait forever for an unreachable request.
        if (mock == null && settings.firmwareInteger == 0x114 && profile.temperatureC > 96)
            return autoPreheatRejected(ResourceMessage(R.string.service_auto_target_unsupported))
        if (!settings.brewHeating) return autoPreheatRejected(ResourceMessage(R.string.service_auto_boiler_off))
        if (brewPreparation.active) return autoPreheatRejected(ResourceMessage(R.string.service_shot_preheat_pending))
        val identity = shortcutDeviceIdentity
            ?: return autoPreheatRejected(ResourceMessage(R.string.service_unavailable))
        val intent = io.openhoyi.session.AutoPreheatIntent(profileId, profile.temperatureC, slot,
            identity, shortcutConfigurationRevision)
        autoPreheatIntent = intent
        autoPreheatInitialTemperature = actual
        val blocked = prepareBrewRequest(profileId, expectedScaleMode, slot) {
            autoPreheatIntent === intent && intent.permitsStart(shortcutDeviceIdentity, shortcutConfigurationRevision)
        }
        if (blocked != null) {
            intent.invalidate()
            autoPreheatResource = ResourceMessage(R.string.service_auto_request_blocked, blocked)
            return blocked
        }
        intent.requestId = brewPreparation.requestId
        return null
    }

    private fun autoPreheatRejected(message: ResourceMessage): String? {
        autoPreheatResource = message
        return autoPreheatMessage
    }

    /** One best-effort cancellation on leaving. An abnormal connection never queues recovery work. */
    fun leaveAutoPreheat(): String? {
        val intent = autoPreheatIntent ?: return null
        val now = MobileMonotonicClock.elapsedRealtime()
        if (!intent.leave(now)) return null
        if (intent.requestId == null || !brewPreparation.active) return null
        if (intent.requestId != brewPreparation.requestId) {
            if (brewPreparation.state == BrewPreparation.State.UNKNOWN) {
                autoPreheatResource = ResourceMessage(R.string.service_auto_cancel_not_sent)
                return autoPreheatMessage
            }
            return null
        }
        fun permit(): Boolean = autoPreheatIntent === intent && intent.permitsCancel(shortcutDeviceIdentity, shortcutConfigurationRevision,
            MobileMonotonicClock.elapsedRealtime()) && snapshot.coffeeState == DeviceState.READY &&
            !manualShotActive && !ShotGate.active(shotState)
        autoPreheatResource = ResourceMessage(R.string.service_auto_cancel_requested)
        val blocked = if (!permit()) ResourceMessage(R.string.service_auto_cancel_not_sent)
            else cancelBrewPreparationMessage(::permit)
        if (blocked != null) {
            intent.invalidate()
            // Invalidate the positive write and its watchdog. Never retry it after reconnect.
            if (brewPreparation.active) brewPreparation.disconnected()
            autoPreheatResource = ResourceMessage(R.string.service_auto_cancel_blocked, blocked)
            event(requireNotNull(autoPreheatResource), "brew_wait.auto_cancel_skipped")
            refreshSafetyNotification()
        } else if (mock != null) {
            autoPreheatResource = ResourceMessage(R.string.service_shot_cancel_mock_done)
        }
        return autoPreheatMessage
    }

    fun prepareBrew(profileId: String, expectedScaleMode: Boolean?, slot: Int): String? {
        val blocked = prepareBrewRequest(profileId, expectedScaleMode, slot, null)
        if (blocked == null) {
            autoPreheatResource = null
            autoPreheatInitialTemperature = null
        }
        return blocked
    }

    private fun prepareBrewRequest(profileId: String, expectedScaleMode: Boolean?, slot: Int,
        automaticPermit: (() -> Boolean)?): String? {
        interruptUpdateInstallation()
        if (mock != null) {
            if (brewPreparation.active) return getString(R.string.service_shot_preheat_mock_pending)
            val profile = selectedCurve(profileId, slot) ?: return getString(R.string.start_block_curve_missing)
            if (profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_mock_scale_changed)
            val library = (application as MobileApplication).curves
            shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
                snapshot.scaleState, snapshot.scaleObservation, MobileMonotonicClock.elapsedRealtime(), shotState,
                validated = library.validated(profile))?.let { return it }
            val now = MobileMonotonicClock.elapsedRealtime()
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
        if (!machineBrewSettingsAvailable) return getString(R.string.service_shot_preheat_settings_stale)
        if (settings.flags and 0x04 == 0) return getString(R.string.service_shot_preheat_cafe_mode)
        val profile = selectedCurve(profileId, slot)
        if (profile != null && profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_scale_changed)
        val library = (application as MobileApplication).curves
        val blocked = shotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            snapshot.scaleState, snapshot.scaleObservation, MobileMonotonicClock.elapsedRealtime(), current.extraction.state,
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
        val recoveryOwner=requireNotNull(machineWriteRecovery.captureOwnership())
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        refreshSafetyNotification()
        event(ResourceMessage(R.string.service_shot_preheat_queued, profile.temperatureC.toString()), "brew_wait.requested")
        current.setBrewWait(profile.temperatureC, {
            (automaticPermit?.invoke() != false) && BrewWriteDispatchPermit.allows(token,brewPreparation,profile.temperatureC,
                BrewWriteDispatchPermit.Context(machineWriteRecovery,recoveryOwner,coffeeAddress,current.coffeeAddress,
                    hub === current,snapshot.coffeeState==DeviceState.READY,manualShotActive,shotRecovery.pending,
                    ShotGate.active(current.extraction.state),selectedCurve(profileId,slot)==profile && library.validated(profile)))
        }) done@{ result ->
            val outcome = MachineWriteResult.apply(token, result, MachineWriteResult.Request.BrewWait(brewPreparation, idleSampleSerial))
            if (outcome == MachineWriteResult.Outcome.IGNORED) return@done
            when (outcome) {
                MachineWriteResult.Outcome.WAITING -> {
                    event(ResourceMessage(R.string.service_shot_preheat_written), "brew_wait.written")
                    preheatTimeoutScheduler(BrewPreparationWatchdog.TIMEOUT_MS) timeout@{
                        if (automaticPermit != null) {
                            if (brewPreparation.isActive(token)) {
                                leaveAutoPreheat()
                                if (brewPreparation.timedOut(token)) {
                                    autoPreheatResource = ResourceMessage(R.string.service_auto_cancel_not_sent)
                                    refreshSafetyNotification()
                                }
                            }
                            return@timeout
                        }
                        val expiry = BrewPreparationWatchdog.expire(brewPreparation,token,{
                            (automaticPermit?.invoke() != false) && hub === current && machineWriteRecovery.owns(recoveryOwner) &&
                                current.coffeeAddress?.equals(coffeeAddress,ignoreCase=true)==true
                        },ResourceMessage(R.string.service_shot_cancel_original_device),::cancelBrewPreparationMessage)
                        when(expiry?.outcome) {
                            BrewPreparationWatchdog.Outcome.CANCEL_REQUESTED ->
                                event(ResourceMessage(R.string.service_shot_preheat_timeout_cancel), "brew_wait.timeout_cancel_requested")
                            BrewPreparationWatchdog.Outcome.CANCEL_BLOCKED -> {
                                event(ResourceMessage(R.string.service_shot_preheat_timeout_blocked, expiry.blocked ?: "null"), "brew_wait.timeout_cancel_blocked")
                                refreshSafetyNotification()
                            }
                            null -> Unit
                        }
                    }
                }
                MachineWriteResult.Outcome.FAILED -> event(ResourceMessage(R.string.service_shot_preheat_not_written), "brew_wait.failed")
                MachineWriteResult.Outcome.UNKNOWN -> event(ResourceMessage(R.string.service_shot_preheat_unknown), "brew_wait.unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    fun cancelBrewPreparation(): String? {
        autoPreheatResource = null
        return cancelBrewPreparationMessage()?.render { id, args -> getString(id, *args) }
    }

    private fun cancelBrewPreparationMessage(): ResourceMessage? = cancelBrewPreparationMessage(null)

    private fun cancelBrewPreparationMessage(automaticPermit: (() -> Boolean)?): ResourceMessage? {
        if (mock != null) {
            if (!brewPreparation.active) return ResourceMessage(R.string.service_shot_cancel_mock_none)
            val now = MobileMonotonicClock.elapsedRealtime()
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
        val coffeeAddress=current.coffeeAddress ?: return ResourceMessage(R.string.service_shot_cancel_original_device)
        val recoveryOwner=machineWriteRecovery.captureOwnership() ?: return ResourceMessage(R.string.service_shot_cancel_none)
        val token = brewPreparation.beginCancel() ?: return ResourceMessage(R.string.service_shot_cancel_pending)
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        event(ResourceMessage(R.string.service_shot_cancel_queued), "brew_wait.cancel_requested")
        current.setBrewWait(0, {
            (automaticPermit?.invoke() != false) && BrewWriteDispatchPermit.allows(token,brewPreparation,0,
                BrewWriteDispatchPermit.Context(machineWriteRecovery,recoveryOwner,coffeeAddress,current.coffeeAddress,
                    hub === current,snapshot.coffeeState==DeviceState.READY,manualShotActive,shotRecovery.pending,
                    ShotGate.active(current.extraction.state),true)) && brewWaitCancelBlockMessage == null
        }) done@{ result ->
            if (!brewPreparation.cancelled(token, result)) return@done
            if (automaticPermit != null) autoPreheatResource = ResourceMessage(
                if (brewPreparation.state == BrewPreparation.State.CANCEL_WRITTEN)
                    R.string.service_shot_cancel_written else R.string.service_shot_cancel_unknown)
            when (brewPreparation.state) {
                BrewPreparation.State.CANCEL_WRITTEN -> event(ResourceMessage(R.string.service_shot_cancel_written), "brew_wait.cancel_written")
                BrewPreparation.State.UNKNOWN -> event(ResourceMessage(R.string.service_shot_cancel_unknown), "brew_wait.cancel_unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    /** Connection identity is read-only; a shortcut cannot migrate to a replacement session. */
    internal val shortcutDeviceIdentity: Any?
        get() {
            if (mock != null) return mockExtractionOwner ?: mock
            val current = hub ?: return null
            val address = current.coffeeAddress ?: return null
            return Triple(current, address, current.coffeeGeneration)
        }

    fun startShot(profileId: String, expectedScaleMode: Boolean? = null, slot: Int = 7,
        startPermit: (() -> Boolean)? = null): String? {
        interruptUpdateInstallation()
        dispatchingCurveId = profileId
        return try { startShotChecked(profileId, expectedScaleMode, slot, startPermit) }
        finally { dispatchingCurveId = null }
    }
    private fun startShotChecked(profileId: String, expectedScaleMode: Boolean?, slot: Int,
                                 startPermit: (() -> Boolean)?): String? {
        fun callerPermits() = startPermit == null || runCatching { startPermit() }.getOrDefault(false)
        if (!callerPermits()) return getString(R.string.shortcut_start_cancelled)
        val app = application as MobileApplication
        val preparation = app.beanPreparationResult.getOrElse { return getString(R.string.dose_start_unavailable) }
        if (preparation.current()?.let { it.shotId != null && !it.associated } == true)
            return getString(R.string.dose_start_unavailable)
        preparation.current()?.takeIf { it.shotId == null }?.let { dose ->
            if (!dose.confirmed) return getString(R.string.dose_start_unconfirmed)
            if (runCatching { preparation.verifyConfirmed(app.beanInventoryResult.getOrThrow()) }.isFailure)
                return getString(R.string.dose_start_unavailable)
        }
        if (mock != null) {
            if (mock.isSleeping) return getString(R.string.service_shot_mock_sleeping)
            val profile = selectedCurve(profileId, slot) ?: return getString(R.string.start_block_curve_missing)
            if (profile.scaleMode != expectedScaleMode) return getString(R.string.service_shot_mock_scale_changed)
            if (ShotGate.active(shotState)) return getString(R.string.service_shot_mock_running)
            customCurveFirmwareBlock(profile)?.let { return it }
            studioStartBlock(profile)?.let { return it }
            val now = MobileMonotonicClock.elapsedRealtime()
            if (brewPreparation.active) {
                mock.cancelPreheat(now)
                brewPreparation.consumed()
            }
            if (!callerPermits()) return getString(R.string.shortcut_start_cancelled)
            val shotId = beginPreparedShot(profile.id, slot) ?: return getString(R.string.dose_start_unavailable)
            val scenarioOwner = mockExtractionOwner
            if (scenarioOwner == null) mock.start(now, slot)
            else if (!mockExtractionScenario.start(scenarioOwner, profile.parameters,
                    profile.targetHundredthsGram, profile.compensationHundredthsGram) {
                    callerPermits() && selectedCurve(profileId, slot) == profile && snapshot.coffeeState == DeviceState.READY &&
                        customCurveFirmwareBlock(profile) == null &&
                        mockExtractionOwner === scenarioOwner
                }) {
                runCatching { history?.transition(ExtractionState.IDLE, null, null) }
                return getString(R.string.service_shot_session_rejected)
            }
            activeShotTargetHundredthsGram = profile.targetHundredthsGram
            series.begin(shotId, MobileMonotonicClock.elapsedRealtime(), profile.id)
            extractionScaleReadout.begin(shotId, activeShotTargetHundredthsGram)
            activeShotRecipe = ShotRecipeSnapshot.capture(shotId, profile,
                (application as MobileApplication).curves.find(profile.id)?.customDocument)
            beginBrewFeedback(shotId, if (slot == 7) 8 else slot, profile.parameters.preinfusionSeconds)
            if (scenarioOwner == null) event(ResourceMessage(R.string.service_shot_mock_started, profile.name), "mock.shot_started")
            else event(ResourceMessage(R.string.service_shot_requested, profile.name), "mock.synthetic_shot_requested")
            return null
        }
        leverWriteBlock?.let { return it }
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
            snapshot.scaleObservation, MobileMonotonicClock.elapsedRealtime(), current.extraction.state,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) {
            event(ResourceMessage(R.string.service_shot_start_blocked, blocked), "shot.rejected")
            return blocked.render { id, args -> getString(id, *args) }
        }
        requireNotNull(profile)
        customCurveFirmwareBlock(profile)?.let { return it }
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
        val shotId = beginPreparedShot(profile.id, slot) ?: return getString(R.string.dose_start_unavailable)
        val originalMachineIntent=MachineWriteRecoveryState.Record(machineWriteRecovery.kind,machineWriteRecovery.address)
        if (!shotRecovery.arm(coffeeAddress)) {
            runCatching { history?.transition(ExtractionState.IDLE, null, null) }
            return getString(R.string.service_shot_record_failed)
        }
        val shotOwner=requireNotNull(shotRecovery.captureOwnership())
        val machineOwner=machineWriteRecovery.captureOwnership()
        appRecoveryOwner=AppRecoveryOwner(current,coffeeAddress,shotOwner,machineOwner)
        if (!current.extraction.start(profile.parameters, profile.targetHundredthsGram, profile.compensationHundredthsGram) {
                val selected=selectedCurve(profileId,slot)
                val busy=settingWriteUnresolved || sleepNowUnresolved || cupResetBusy || scheduleBusy ||
                    sleepNow.state in setOf(SleepNowTracker.State.WRITING,SleepNowTracker.State.WAITING_ASLEEP) ||
                    settingsWrite.state in setOf(SettingsWriteTracker.State.WRITING,SettingsWriteTracker.State.WAITING_READBACK)
                callerPermits() && shotRecovery.owns(shotOwner) && (machineOwner==null || machineWriteRecovery.owns(machineOwner)) &&
                ShotDispatchPermit.allows(ShotDispatchPermit.Context(coffeeAddress,current.coffeeAddress,
                    hub === current,snapshot.coffeeState==DeviceState.READY,
                    selected==profile && library.validated(profile),manualShotActive,busy,
                    shotRecovery.pending,shotRecovery.address,originalMachineIntent,
                    MachineWriteRecoveryState.Record(machineWriteRecovery.kind,machineWriteRecovery.address))) &&
                    studioStartBlock(profile)==null && customCurveFirmwareBlock(profile)==null
            } ||
            current.extraction.state == ExtractionState.IDLE) {
            if (!shotRecovery.clear(shotOwner)) manualSafetyResource = R.string.machine_recovery_shot_restart
            else appRecoveryOwner=null
            runCatching { history?.transition(ExtractionState.IDLE, null, null) }
            event(ResourceMessage(R.string.service_shot_session_rejected), "shot.rejected")
            return getString(R.string.service_shot_session_rejected)
        }
        activeShotTargetHundredthsGram = profile.targetHundredthsGram
        if (brewPreparation.active) brewPreparation.consumed()
        if (machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT)
            brewWaitShotStarted = true
        series.begin(shotId, MobileMonotonicClock.elapsedRealtime(), profile.id)
        extractionScaleReadout.begin(shotId, activeShotTargetHundredthsGram, scaleReadoutOwner(current))
        activeShotRecipe = ShotRecipeSnapshot.capture(shotId, profile,
            (application as MobileApplication).curves.find(profile.id)?.customDocument)
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
    /** Persist shot identity and consumed-dose ownership before any start dispatch. */
    private fun beginPreparedShot(curveId: String, slot: Int): String? {
        val app = application as MobileApplication
        val id = runCatching { requireNotNull(history).begin(curveId, slot = slot) }
            .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
            .getOrNull() ?: return null
        return runCatching {
            app.beanPreparationResult.getOrThrow().claim(id)
            // Journal association is recoverable; durable ownership is not optional.
            app.reconcileJournal()
            id
        }.onFailure {
            runCatching { history?.transition(ExtractionState.IDLE, null, null) }
            event(ResourceMessage(R.string.dose_start_unavailable), "bean.shot_claim_failed")
        }.getOrNull()
    }
    fun stopShot() {
        interruptUpdateInstallation()
        if (mock != null) {
            if (ShotGate.active(shotState)) {
                val scenarioOwner = mockExtractionOwner
                if (scenarioOwner == null) {
                    mock.stop()
                    // Ordinary Mock has no BLE idle callback. Publish its explicit stopped sample
                    // before watchShot can finalize the readout; synthetic inputs remain notification-only.
                    refreshMock(mock, MobileMonotonicClock.elapsedRealtime())
                } else mockExtractionScenario.stop(scenarioOwner)
                if (scenarioOwner == null) event(ResourceMessage(R.string.service_shot_mock_stopped), "mock.shot_stopped")
                else event(ResourceMessage(R.string.service_shot_stop_requested), "mock.synthetic_stop_requested")
            }
            return
        }
        if (manualShotActive) { event(ResourceMessage(R.string.service_shot_manual_stop_block)); return }
        if (!ShotGate.active(shotState)) return
        if (shotState == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != DeviceState.READY) {
            event(ResourceMessage(R.string.service_shot_stop_unavailable), "shot.stop_unavailable")
            return
        }
        StopRequestDelivery.request(
            { hub?.extraction?.manualStop() },
            { event(ResourceMessage(R.string.service_shot_stop_requested), "shot.manual_stop") })
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
        extractionScaleReadout.finish(observedEnd)
        val finished = series.finish() ?: return
        runCatching { history?.attachTimingEvidence(finished.first, series.observedMachineElapsedSeconds, series.observedSampleCoverageMs) }
            .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "shot.history_error") }
        val app = application as MobileApplication
        if (observedEnd && finished.second.isNotEmpty() &&
            history?.entries?.any { it.id == finished.first } == true) {
            app.samples.save(finished.first, finished.second)
        }
        history?.entries?.map(ShotHistory.Entry::id)?.toSet()?.let(app.samples::prune)
    }
    private fun recordMachinePoint(frame: io.openhoyi.protocol.ExtractionTelemetry, atMs: Long) {
        if (CurveUseEvidence.matches(frame)) runCatching {
            val observedId = history?.observeRunning()
            val recipe = activeShotRecipe?.takeIf { it.traceId == observedId }
            val fingerprint = recipe?.draftFingerprint
            if (recipe != null && fingerprint != null)
                (application as MobileApplication).curveUsageResult.getOrThrow()
                    .attachFingerprint(recipe.traceId, fingerprint)
        }
            .onFailure { event(ResourceMessage(R.string.service_shot_history_failed), "curve.usage_observation_failed") }
        observeBrewFeedback(frame, atMs)
        // Reuse confirmed per-shot proof when present; ShotSeries separately labels a fresh
        // fallback baseline as estimated. Neither path changes tare/start commands.
        val beverage = extractionScaleValue(atMs).confirmedBeverageSample()
        ScaleBusinessReading.recordMachine(series, frame, atMs, snapshot.scaleObservation, snapshot.scaleState, beverage, scaleConnectionEpoch)
        saveSeriesCheckpoint(atMs)
    }
    private fun saveSeriesCheckpoint(atElapsedMs: Long = MobileMonotonicClock.elapsedRealtime(), force: Boolean = false) {
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
        (application as? MobileApplication)?.let { app -> runCatching { app.releaseUpdates.interruptionStateChanged() } }
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
        if (!displayOnly && !safetyNotificationDirty && warning == safetyMessage) return
        if (!running) {
            safetyMessage = warning
            safetyNotificationDirty = false
            return
        }
        // A partial update may have changed either platform notification, even if the warning
        // later returns to the previous value. Do not deduplicate until both operations succeed.
        safetyNotificationDirty = true
        val manager = try { getSystemService(NotificationManager::class.java) }
        catch (error: RuntimeException) {
            notificationFailure(displayOnly, R.string.notification_update_error, "shot.safety_notify_error", error)
            return
        }
        val connectionDelivered = runCatching { manager.notify(1, connectionNotification(warning)) }
            .onFailure { notificationFailure(displayOnly, R.string.notification_update_error, "shot.safety_notify_error", it) }
            .isSuccess
        val safetyDelivered = if (warning == null) runCatching { manager.cancel(SAFETY_NOTIFICATION) }
            .onFailure { notificationFailure(displayOnly, R.string.notification_unavailable, "shot.safety_notify_error", it) }
        else runCatching {
            manager.notify(SAFETY_NOTIFICATION,
                notificationDisplay.safety(warning, MainActivity::class.java, SAFETY_CHANNEL, displayOnly,
                    openExtraction=presentation.destination==SafetyNotificationPresentation.Destination.EXTRACTION))
        }.onFailure { notificationFailure(displayOnly, R.string.notification_unavailable, "shot.safety_notify_error", it) }
        // Keep the last fully delivered warning so an ordinary later refresh can retry failures.
        if (connectionDelivered && safetyDelivered.isSuccess) {
            safetyMessage = warning
            safetyNotificationDirty = false
        }
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
            val now = MobileMonotonicClock.elapsedRealtime()
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
                onAcknowledged = {
                    brewPreparation.consumed()
                    autoPreheatResource = null
                    autoPreheatInitialTemperature = null
                    autoPreheatIntent = null
                })
            return
        }
        if (manualSafetyResource == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.CUP_RESET) {
            val now = MobileMonotonicClock.elapsedRealtime()
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
            val now = MobileMonotonicClock.elapsedRealtime()
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
            val now = MobileMonotonicClock.elapsedRealtime()
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
            val now = MobileMonotonicClock.elapsedRealtime()
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
        finally { hub = null; hubForeground = false; scaleTrend.clear() }
    }
    fun shutdown() {
        if (mock != null) {
            finishBrewFeedback(false)
            if (ShotGate.active(shotState)) mock.stop()
            running = false
            handler.removeCallbacks(mockTick)
            snapshot = MobileSnapshot(message = resolvedMessage(ResourceMessage(R.string.service_event_mock_stopped)))
            (application as MobileApplication).serviceAvailability.stopped(ownerId)
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
            coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null, scanning = false,
            scaleObservation = null, scaleCapabilities = null)
        (application as MobileApplication).serviceAvailability.stopped(ownerId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        settleLeverWriteUnknown()
        (application as MobileApplication).releaseUpdates.serviceDestroyed(this)

        (application as MobileApplication).curveDeletionGuards.remove(ownerId)
        running = false
        (application as MobileApplication).serviceAvailability.stopped(ownerId)
        (application as MobileApplication).feedbackPreferences.unobserve(ownerId)
        appVisibility.unobserve(ownerId)
        finishBrewFeedback(false)
        if (passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
            val committed = passiveCommitted
            passiveCommitted = false
            if (committed) {
                manualPresentation.disconnected()
                passiveHistoryId?.let { id -> runCatching { history?.abandon(id, "设备服务停止") } }
                saveSeriesCheckpoint(force = true)
                finishSeries(false)
            } else discardPassiveSeries(requireDeviceMatch = false)
            passiveHistoryId = null
            passiveStartedWallMs = 0L
            passiveArmedRecovery = false
            passiveMayClearRecovery = false
            passiveRecoveryOwner = null
        }
        handler.removeCallbacks(mockTick)
        cancelSafetyNotification()
        handler.removeCallbacks(watchShot)
        handler.removeCallbacks(leaveForeground)
        handler.removeCallbacks(stopAutomaticScale)
        closeDeviceOwner()
        autoConnect.onChanged=null
        super.onDestroy()
    }
    companion object {
        const val STOP = "io.openhoyi.mobile.STOP"
        const val AUTO_SCALE = "io.openhoyi.mobile.AUTO_SCALE"
        const val AUTO_KNOWN_DEVICES = "io.openhoyi.mobile.AUTO_KNOWN_DEVICES"
        const val TAG = "OpenHoyiMobile"
        private const val CHANNEL = "connections"
        private const val SAFETY_CHANNEL = "shot_safety"
        private const val SAFETY_NOTIFICATION = 2
        fun hasRememberedCoffee(context:Context):Boolean =
            context.getSharedPreferences("devices",Context.MODE_PRIVATE).let { prefs ->
                prefs.getBoolean("coffeeAutoEnabled",true) && !prefs.getBoolean("coffeeAutoSuppressed",false) && !prefs.getString("coffee",null).isNullOrBlank()
            }
    }
}
