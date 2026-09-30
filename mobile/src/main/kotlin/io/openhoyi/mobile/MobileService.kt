package io.openhoyi.mobile

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
    val message: String = "尚未连接设备",
)

/** Product-app BLE owner. Screens observe snapshots; explicit controls remain gated in this service. */
class MobileService : Service() {
    inner class LocalBinder : Binder() { val service: MobileService get() = this@MobileService }
    private val binder = LocalBinder()
    private lateinit var logs: TraceStore
    private var history: ShotHistory? = null
    private val series = ShotSeries()
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
    private val restartShotWarning = "上次萃取未确认结束。请检查咖啡机，重新连接并等待待机回报后再清除提示。"
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
    val machineWriteSafetyMessage: String? get() = when (machineWriteRecovery.kind) {
        MachineWriteRecoveryState.Kind.CUP_RESET ->
            "上次杯数重置未确认。请连接原咖啡机，核对设置与待机杯数后再清除提示。"
        MachineWriteRecoveryState.Kind.SETTING ->
            "上次机器设置未确认。请连接原咖啡机，核对当前设置后再清除提示。"
        MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE ->
            "上次睡眠计划可能只写入一部分。请连接原咖啡机，核对整周计划后再清除提示。"
        MachineWriteRecoveryState.Kind.SLEEP_NOW ->
            "上次立即睡眠未确认。请检查机器，并在原咖啡机回报明确睡眠状态后清除提示。"
        MachineWriteRecoveryState.Kind.BREW_WAIT -> if (brewPreparation.state in setOf(
                BrewPreparation.State.WRITING, BrewPreparation.State.WAITING_TEMP,
                BrewPreparation.State.READY)) null else
            "预热或取消结果未确认。请检查原咖啡机是否仍在预热；可发送取消命令，待机器回报已唤醒待机后人工确认。"
        MachineWriteRecoveryState.Kind.UNKNOWN ->
            "机器写入安全记录无法识别，已阻止新的控制命令。"
        null -> null
    }
    val machineControlSafetyMessage: String? get() = MachineControlGate.block(
        shotRecovery.pending, machineWriteSafetyMessage, restartShotWarning)
    val shotRecoveryClearBlock: String? get() = ShotRecoveryClearGate.block(shotRecovery,
        hub?.coffeeAddress, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
        SystemClock.elapsedRealtime(), shotState, manualShotActive)
    val machineWriteAcknowledgementAvailable: Boolean get() = when (machineWriteRecovery.kind) {
        MachineWriteRecoveryState.Kind.CUP_RESET -> !cupResetBusy
        MachineWriteRecoveryState.Kind.SETTING -> settingsWrite.state !in setOf(
            SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK)
        MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE -> !scheduleBusy
        MachineWriteRecoveryState.Kind.SLEEP_NOW -> sleepNow.state !in setOf(
            SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP)
        MachineWriteRecoveryState.Kind.BREW_WAIT -> !shotRecovery.pending &&
            !ShotGate.active(shotState) && brewPreparation.state in setOf(
            BrewPreparation.State.IDLE, BrewPreparation.State.FAILED,
            BrewPreparation.State.UNKNOWN, BrewPreparation.State.CANCEL_WRITTEN)
        else -> false
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
    private val cupResetBusy: Boolean get() = cupReset.state in
        setOf(CupResetTracker.State.WRITING, CupResetTracker.State.WAITING_ZERO)
    private var cupSettingsSerial = 0L
    private var cupIdleSerial = 0L
    private fun observeCupCount(settingsFrame: Boolean, count: Int) {
        val previous = cupReset.state
        val confirmed = if (settingsFrame) cupReset.observeSettings(++cupSettingsSerial, count)
            else cupReset.observeIdle(++cupIdleSerial, count)
        if (cupReset.state == CupResetTracker.State.CONFIRMED &&
            machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.CUP_RESET &&
            !machineWriteRecovery.clear())
            event("无法清除杯数重置安全记录；请核对机器", "cups.recovery_clear_failed")
        refreshSafetyNotification()
        if (confirmed) event("机器设置与待机数据均回报累计杯数归零", "cups.confirmed")
        else if (previous == CupResetTracker.State.UNKNOWN && cupReset.state == CupResetTracker.State.RECONCILED)
            event("机器杯数已重新回读；上次重置未获确认", "cups.reconciled")
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
    val brewWaitCancelBlock: String? get() = if (mock != null) null else
        BrewWaitCancelGate.block(snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            SystemClock.elapsedRealtime(), shotState, shotRecovery.pending)
    private var sleepSampleSerial = 0L
    private val sleepNowUnresolved: Boolean get() = sleepNow.state == SleepNowTracker.State.UNKNOWN
    private val sleepNowUnresolvedMessage = "上次立即睡眠结果未知，请等待新的机器待机状态或重新连接"
    val sleepNowState: SleepNowTracker.State get() = if (mock?.isSleeping == true)
        SleepNowTracker.State.CONFIRMED else sleepNow.state
    private var settingsSampleSerial = 0L
    private val settingWriteUnresolved: Boolean get() = settingsWrite.state == SettingsWriteTracker.State.UNKNOWN
    private val settingWriteUnresolvedMessage = "上次机器设置结果未知，请等待新的设置回读或重新连接"
    val settingWriteState: SettingsWriteTracker.State get() = if (mock?.lastSetting != null)
        SettingsWriteTracker.State.CONFIRMED else settingsWrite.state
    val pendingSetting: MachineSettingChange? get() = mock?.lastSetting ?: settingsWrite.change
    val scheduleWriteState: SleepScheduleWriteTracker.State get() = if (mock?.scheduleChanged == true)
        SleepScheduleWriteTracker.State.CONFIRMED else scheduleWrite.state
    val pendingSchedule: WeeklySleepSchedule? get() = scheduleWrite.target
    private val scheduleBusy: Boolean get() = scheduleWrite.state in
        setOf(SleepScheduleWriteTracker.State.WRITING, SleepScheduleWriteTracker.State.WAITING_READBACK)
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
    private val mock = if (BuildConfig.MOCK_MODE) MockDeviceRuntime() else null
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
                    event("Mock 目标温度已达到；未发送蓝牙命令", "mock.brew_wait_ready")
            }
            (snapshot.coffee as? io.openhoyi.protocol.ExtractionTelemetry)?.let { frame ->
                series.machine(frame, now, snapshot.weight?.weightHundredthsGram,
                    snapshot.weightAt, snapshot.weight?.deviceFlowHundredths)
                saveSeriesCheckpoint(now)
            }
            handler.postDelayed(this, 250)
        }
    }
    private val visibleScreens = VisibleScreens()
    private var hubForeground = false
    private var safetyMessage: String? = null
    var manualSafetyMessage: String? = null
        private set
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
            if (visibleScreens.visible || !hubForeground) return
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
        StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO -> "正在等待电子秤去皮确认，暂不能开始萃取"
        StandaloneTare.State.UNKNOWN -> "去皮结果未知，请重新去皮并确认归零后再开始"
        else -> null
    }
    private var lastTareState = StandaloneTare.State.IDLE
    private fun observeTare() {
        val current = tareState
        if(current == lastTareState) return
        lastTareState = current
        if(current == StandaloneTare.State.CONFIRMED) event("电子秤已归零", "scale.tare_confirmed")
        if(current == StandaloneTare.State.UNKNOWN) event("去皮结果未知，请重新去皮并等待归零", "scale.tare_unknown")
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
                        snapshot.weightAt?.let { received -> received <= now && now - received <= 1500 } == true
                }
                runCatching { history?.transition(current, stopReason, weight) }
                    .onFailure { event("历史记录失败", "shot.history_error") }
                if (current == ExtractionState.ENDED_OBSERVED || current == ExtractionState.IDLE) {
                    finishSeries(current == ExtractionState.ENDED_OBSERVED)
                    val shotRecordCleared = shotRecovery.clear()
                    if (!shotRecordCleared)
                        manualSafetyMessage = "无法清除萃取安全记录；请检查机器并重试。"
                    if (current == ExtractionState.ENDED_OBSERVED && shotRecordCleared && brewWaitShotStarted &&
                        machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT &&
                        machineWriteRecovery.matchesDevice(hub?.coffeeAddress)) {
                        if (!machineWriteRecovery.clear())
                            event("无法清除预热安全记录；请核对机器", "brew_wait.recovery_clear_failed")
                        brewWaitShotStarted = false
                    }
                }
                if (current == ExtractionState.OUTCOME_UNKNOWN) saveSeriesCheckpoint(force = true)
                event("萃取状态：${current.name}", "shot.state")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.TARE_UNCONFIRMED.name)
                    event("电子秤去皮未确认，咖啡机未启动", "shot.preflight_failed")
                if (previous == ExtractionState.STARTING && current == ExtractionState.IDLE &&
                    stopReason == io.openhoyi.session.StopReason.START_CONDITIONS_CHANGED.name)
                    event("启动条件已变化，咖啡机未启动；请重新核对曲线和机器设置", "shot.conditions_changed")
                refreshSafetyNotification()
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val app = application as MobileApplication
        logs = app.logs
        history = runCatching { app.history }.getOrNull()
        if (mock == null && shotRecovery.pending) manualSafetyMessage = restartShotWarning
        handler.post(watchShot)
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
            event("Mock 数据已启动；所有设备命令均为模拟", "mock.started")
            return START_NOT_STICKY
        }
        automaticScaleOnly = intent?.action == AUTO_SCALE
        safetyMessage = null
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "设备连接", NotificationManager.IMPORTANCE_LOW))
            manager.createNotificationChannel(NotificationChannel(SAFETY_CHANNEL, "萃取安全提醒", NotificationManager.IMPORTANCE_HIGH))
            val note = connectionNotification(null)
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, note)
            val prefs = getSharedPreferences("devices", MODE_PRIVATE)
            hub = NativeDeviceHub(applicationContext, prefs.getString("scale", null),
                onScaleRemembered = { prefs.edit().putString("scale", it).apply() },
                onState = { role, state ->
                    snapshot = if (role == DeviceRole.COFFEE) {
                        if (state != DeviceState.READY) {
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
                                event("咖啡机已连接，但本机未能保存密码", "coffee.credential_save_failed")
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
                                .onFailure { event("手动萃取历史保存失败", "shot.history_error") }
                        }
                        passiveHistoryId = null
                        passiveMayClearRecovery = false
                        saveSeriesCheckpoint(force = true)
                        finishSeries(false)
                        manualSafetyMessage = "手动萃取时连接中断，结果未知；请检查机器并用拨杆确认停止。"
                        event("手动萃取连接中断，结果未知", "shot.passive_unknown")
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
                                event("机器回读已确认设置", "settings.confirmed")
                            else if (previousSettingState == SettingsWriteTracker.State.UNKNOWN &&
                                settingsWrite.state == SettingsWriteTracker.State.RECONCILED)
                                event("机器设置已重新回读；上次写入未获确认", "settings.reconciled")
                            if (settingsWrite.state == SettingsWriteTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SETTING) {
                                if (!machineWriteRecovery.clear())
                                    event("无法清除机器设置安全记录", "settings.recovery_clear_failed")
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
                                    event("机器已回读完整睡眠计划", "sleep_schedule.confirmed")
                                else event("已重新收到完整计划，请核对机器时间", "sleep_schedule.reconciled")
                            }
                            if (scheduleWrite.state == SleepScheduleWriteTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
                                if (!machineWriteRecovery.clear())
                                    event("无法清除睡眠计划安全记录", "sleep_schedule.recovery_clear_failed")
                                refreshSafetyNotification()
                            }
                            snapshot
                        }
                        is IdleTelemetry -> {
                            observeCupCount(false, frame.cupCount)
                            val previousSleepState = sleepNow.state
                            if (sleepNow.observe(++sleepSampleSerial, frame.sleepStateRaw))
                                event("机器已回报进入睡眠", "sleep.confirmed")
                            else if (previousSleepState == SleepNowTracker.State.UNKNOWN &&
                                sleepNow.state == SleepNowTracker.State.RECONCILED)
                                event("机器已重新回报清醒待机；上次入睡未获确认", "sleep.reconciled")
                            if (sleepNow.state == SleepNowTracker.State.CONFIRMED &&
                                machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_NOW) {
                                if (!machineWriteRecovery.clear())
                                    event("无法清除立即睡眠安全记录", "sleep.recovery_clear_failed")
                                refreshSafetyNotification()
                            }
                            val currentSettings = snapshot.settings
                            if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                                    BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
                                        currentSettings.brewCompensationTenthsC)))
                                event("冲泡温度已达到曲线目标", "brew_wait.ready")
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
                            if (!previousRecovery) manualSafetyMessage = null
                            val currentAddress = hub?.coffeeAddress
                            val armed = shotRecovery.arm(currentAddress)
                            passiveMayClearRecovery = armed &&
                                shotRecovery.mayClearAfterPassiveShot(previousRecovery, currentAddress)
                            if (!armed)
                                manualSafetyMessage = "无法保存萃取安全记录；请守在机器旁并用拨杆停止。"
                            refreshSafetyNotification()
                            passiveHistoryId = runCatching { history?.begin("manual", slot = 6) }
                                .onFailure { event("手动萃取历史暂不可写", "shot.history_error") }.getOrNull()
                            val id = passiveHistoryId ?: java.util.UUID.randomUUID().toString()
                            series.begin(id, passiveEvent.first.atMs)
                            recordMachinePoint(passiveEvent.first.frame, passiveEvent.first.atMs)
                            recordMachinePoint(passiveEvent.second.frame, passiveEvent.second.atMs)
                            passiveHistoryId?.let {
                                runCatching { history?.transition(ExtractionState.RUNNING, "机器手动萃取", null) }
                                    .onFailure { event("手动萃取历史保存失败", "shot.history_error") }
                            }
                            event("检测到机器手动萃取；仅记录，不发送控制命令", "shot.passive_started")
                        }
                        is PassiveShotDetector.Event.Point -> recordMachinePoint(passiveEvent.value.frame,
                            passiveEvent.value.atMs)
                        PassiveShotDetector.Event.Ended -> {
                            if (!passiveMayClearRecovery || !shotRecovery.matchesDevice(hub?.coffeeAddress) ||
                                !shotRecovery.clear())
                                manualSafetyMessage = "无法清除萃取安全记录；请检查机器并重试。"
                            passiveMayClearRecovery = false
                            val weight = snapshot.weight?.weightHundredthsGram?.takeIf {
                                snapshot.scaleState == DeviceState.READY &&
                                    snapshot.weightAt?.let { time -> observedAt >= time && observedAt - time <= 1500 } == true
                            }
                            passiveHistoryId?.let {
                                runCatching { history?.transition(ExtractionState.ENDED_OBSERVED,
                                    "机器待机回报", weight) }
                                    .onFailure { event("手动萃取历史保存失败", "shot.history_error") }
                            }
                            passiveHistoryId = null
                            finishSeries(true)
                            event("机器已回报手动萃取结束", "shot.passive_ended")
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
                    else event("设备通信异常", "ble.diagnostic")
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
            event("服务已启动")
            refreshSafetyNotification()
            scheduleAutomaticScaleStop()
            if (visibleScreens.visible) {
                hub?.foreground()
                hubForeground = true
            }
        } catch (error: RuntimeException) {
            event("服务启动失败：${error.javaClass.simpleName}")
            shutdown()
        }
        return START_NOT_STICKY
    }
    fun screenVisible(owner: String, value: Boolean) {
        visibleScreens.set(owner, value)
        if (visibleScreens.visible) {
            handler.removeCallbacks(leaveForeground)
            if (!hubForeground && hub != null) {
                hub?.foreground()
                hubForeground = true
                scheduleAutomaticScaleStop()
            }
        } else if (hubForeground) {
            // Activity transitions may briefly have no resumed screen.
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
            event("Mock 候选设备已就绪", "mock.scan")
            return
        }
        val current = hub ?: return
        manualDeviceUse()
        if (snapshot.scanning) return
        snapshot = snapshot.copy(scanning = true, candidates = emptyList(), message = "正在扫描…")
        Log.i(TAG, "scan start")
        current.scanner.start(onDevice = { candidate ->
            snapshot = snapshot.copy(candidates = (snapshot.candidates.filterNot { it.address == candidate.address } + candidate)
                .sortedWith(compareBy({ it.candidateRole.name }, { it.advertisedName })).take(64))
        }, onFinished = { error ->
            snapshot = snapshot.copy(scanning = false)
            event(error ?: "扫描完成：${snapshot.candidates.size} 台候选设备")
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
        if (mock != null) { event("Mock 咖啡机已就绪；未连接蓝牙", "mock.connect"); return }
        if (manualShotActive) { event("手动萃取进行中，请先用机器拨杆结束"); return }
        if (!shotRecovery.matchesDevice(address)) {
            event("上一杯未确认结束，只能重新连接原咖啡机", "shot.device_mismatch"); return
        }
        if (!machineWriteRecovery.matchesDevice(address)) {
            event("上次机器写入未确认，只能重新连接原咖啡机", "machine_write.device_mismatch"); return
        }
        require(password.matches(Regex("[0-9]{6}")))
        manualDeviceUse()
        if (cupResetBusy) { event("等待累计杯数归零回报，暂不切换咖啡机"); return }
        if (scheduleBusy) { event("睡眠计划尚未确认，暂不切换咖啡机"); return }
        if (!ShotGate.mayReconnectCoffee(shotState)) {
            event("萃取尚未结束，不能重连咖啡机"); return
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event("正在取消预热，请确认结果后再切换咖啡机", "brew_wait.connect_deferred")
            return
        }
        val current = hub ?: return
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null,
            settings = null, settingsAt = null, sleepFirst = null, sleepSecond = null, sleepFirstAt = null, sleepSecondAt = null)
        event(if (remembered) "使用本机保存的密码连接咖啡机" else "连接咖啡机")
        pendingCoffeeCredential = PendingCoffeeCredential(address, password, remembered)
        try { current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password)) }
        catch (error: RuntimeException) {
            pendingCoffeeCredential = null
            event("连接咖啡机失败：${error.javaClass.simpleName}", "coffee.connect_failed")
        }
    }
    fun connectScale(address: String) {
        if (mock != null) { event("Mock 电子秤已就绪；未连接蓝牙", "mock.connect"); return }
        if (manualShotActive) { event("手动萃取进行中，暂不切换电子秤"); return }
        manualDeviceUse()
        if (ShotGate.active(shotState)) { event("萃取尚未结束，不能切换电子秤"); return }
        val current = hub ?: return
        if (!current.connectScale(address)) { event("电子秤已连接或正在连接"); return }
        snapshot = snapshot.copy(weight = null, weightAt = null)
        event("连接电子秤")
    }
    fun disconnect(role: DeviceRole) {
        if (mock != null) { event("Mock 设备保持就绪；未连接蓝牙", "mock.disconnect"); return }
        if (manualShotActive) { event("手动萃取进行中，请先用机器拨杆结束"); return }
        if (ShotGate.active(shotState)) { event("萃取尚未结束，先停止萃取"); return }
        if (role == DeviceRole.COFFEE && cupResetBusy) {
            event("等待累计杯数归零回报，暂不断开咖啡机"); return
        }
        if (role == DeviceRole.COFFEE && scheduleBusy) {
            event("睡眠计划尚未确认，暂不断开咖啡机"); return
        }
        if (role == DeviceRole.COFFEE && brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event("正在取消预热，请确认结果后再断开", "brew_wait.disconnect_deferred")
            return
        }
        event("断开 ${role.name}")
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    fun tareScale(): String? {
        if (mock != null) {
            val result = mock.tare()
            if (result == null) {
                refreshMock(mock)
                event("Mock 电子秤已归零；未发送蓝牙命令", "mock.tare")
            }
            return result
        }
        if (manualShotActive) return "手动萃取期间不能手动去皮"
        val current = hub ?: return "设备服务尚未启动"
        if (ShotGate.active(shotState)) return "萃取期间不能手动去皮"
        if (snapshot.scaleState != DeviceState.READY) return "电子秤尚未就绪"
        if (tareState in setOf(StandaloneTare.State.WRITING, StandaloneTare.State.WAITING_ZERO))
            return "正在等待本次去皮结果"
        event("请求电子秤去皮", "scale.tare_requested")
        current.tareScale { result ->
            lastTareState = tareState
            when (tareState) {
                StandaloneTare.State.WAITING_ZERO -> event("去皮命令已写入，等待电子秤归零", "scale.tare_written")
                StandaloneTare.State.UNKNOWN -> event("去皮结果未知，请重新去皮并等待归零", "scale.tare_unknown")
                else -> if (result !is OperationResult.Success) event("去皮命令未写入", "scale.tare_failed")
            }
        }
        return null
    }
    fun changeMachineSetting(change: MachineSettingChange): String? {
        if (mock != null) {
            val result = mock.changeSetting(change)
            if (result == null) {
                refreshMock(mock)
                event("Mock 设置已更新：${MachineSettingsPresentation.change(change)}；未发送蓝牙命令", "mock.setting")
            }
            return result
        }
        if (manualShotActive) return "手动萃取期间不能修改机器设置"
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return "正在等待累计杯数归零回报"
        if (scheduleBusy) return "正在等待睡眠计划回读"
        if (ShotGate.active(shotState)) return "萃取期间不能修改机器设置"
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "正在等待机器进入睡眠"
        if (brewPreparation.active) return "请先取消曲线预热"
        if (snapshot.coffeeState != DeviceState.READY) return "咖啡机尚未就绪"
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return "等待咖啡机待机数据"
        if (snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } != true)
            return "咖啡机待机数据已过期"
        if (idle.sleepStateRaw != 0) return "机器未明确处于唤醒待机状态，暂不修改设置"
        val observed = snapshot.settings ?: return "尚未收到机器设置"
        if (!machineSettingsFresh) return "机器设置回报已过期，请等待新回报后再修改"
        if (change is MachineSettingChange.SleepScheduleEnabled && change.enabled &&
            !sleepScheduleFresh)
            return "睡眠计划尚未完整、新鲜回读，不能开启"
        if (change is MachineSettingChange.StandbyDelay &&
            change.temperatureC != observed.standbyTemperatureC) return "机器待机温度已变化，请重新选择"
        if (change is MachineSettingChange.StandbyTemperature &&
            change.minutes != observed.standbyMinutes) return "机器自动待机时间已变化，请重新选择"
        if (change.matches(observed)) return "机器回读已是该设置"
        val coffeeAddress = current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止设置写入"
        val token = settingsWrite.begin(change) ?: return "正在等待上一次设置的结果"
        if (!machineWriteRecovery.arm(MachineWriteRecoveryState.Kind.SETTING, coffeeAddress)) {
            settingsWrite.written(token, OperationResult.Failed("safety record unavailable"), settingsSampleSerial)
            return "无法可靠保存设置安全状态，已阻止发送"
        }
        recoveryAfterSettingsSerial = settingsSampleSerial
        refreshSafetyNotification()
        event("机器设置命令已排队：${MachineSettingsPresentation.change(change)}", "settings.requested")
        current.writeSetting(change) done@{ result ->
            if (!settingsWrite.written(token, result, settingsSampleSerial)) return@done
            when (settingsWrite.state) {
                SettingsWriteTracker.State.WAITING_READBACK -> {
                    event("命令已写入，等待机器回读", "settings.written")
                    handler.postDelayed({
                        if (settingsWrite.timeout(token, settingsSampleSerial)) {
                            recoveryAfterSettingsSerial = settingsSampleSerial
                            event("机器未回读，设置结果未知", "settings.unknown")
                        }
                    }, 6000)
                }
                SettingsWriteTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event("无法清除机器设置安全记录", "settings.recovery_clear_failed")
                    refreshSafetyNotification()
                    event("机器设置命令未写入", "settings.failed")
                }
                SettingsWriteTracker.State.UNKNOWN -> {
                    recoveryAfterSettingsSerial = settingsSampleSerial
                    event("机器设置写入结果未知", "settings.unknown")
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
                event("Mock 杯数已归零；未发送蓝牙命令", "mock.cups")
            }
            return result
        }
        if (manualShotActive) return "手动萃取期间不能重置杯数"
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupReset.state == CupResetTracker.State.UNKNOWN)
            return "上次杯数重置结果未知，请等待机器设置与待机杯数重新回读或重新连接"
        if (cupResetBusy) return "正在等待本次杯数重置结果"
        if (scheduleBusy || settingWriteState in
            setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK) ||
            sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "请等待当前机器操作完成"
        if (brewPreparation.active || ShotGate.active(shotState)) return "请先结束预热或萃取"
        if (snapshot.coffeeState != DeviceState.READY) return "咖啡机尚未就绪"
        val idle = snapshot.coffee as? IdleTelemetry ?: return "等待咖啡机待机数据"
        val now = SystemClock.elapsedRealtime()
        if (snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } != true || idle.sleepStateRaw != 0)
            return "需要新鲜、已唤醒的待机状态"
        val settingsCount = snapshot.settings?.cupCount ?: return "尚未收到机器杯数"
        if (!machineSettingsFresh) return "机器设置回报已过期，请等待新杯数回报"
        if (expectedCount !in 1..65535 || settingsCount != expectedCount || idle.cupCount != expectedCount)
            return "机器杯数已变化，请重新核对"
        val coffeeAddress = current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止杯数重置"
        val token = cupReset.begin(expectedCount) ?: return "正在等待本次杯数重置结果"
        if (!machineWriteRecovery.arm(MachineWriteRecoveryState.Kind.CUP_RESET, coffeeAddress)) {
            cupReset.written(token, OperationResult.Failed("safety record unavailable"),
                cupSettingsSerial, cupIdleSerial)
            return "无法可靠保存杯数重置安全状态，已阻止发送"
        }
        recoveryAfterSettingsSerial = cupSettingsSerial
        recoveryAfterIdleSerial = cupIdleSerial
        refreshSafetyNotification()
        event("累计杯数重置命令已排队", "cups.requested")
        current.resetCupCount(expectedCount) done@{ result ->
            if (!cupReset.written(token, result, cupSettingsSerial, cupIdleSerial)) return@done
            when (cupReset.state) {
                CupResetTracker.State.WAITING_ZERO -> {
                    event("重置命令已写入，等待机器回报归零", "cups.written")
                    handler.postDelayed({
                        if (cupReset.timeout(token, cupSettingsSerial, cupIdleSerial)) {
                            recoveryAfterSettingsSerial = cupSettingsSerial
                            recoveryAfterIdleSerial = cupIdleSerial
                            event("机器未完整回报归零，重置结果未知", "cups.unknown")
                        }
                    }, 12_000)
                }
                CupResetTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event("无法清除杯数重置安全记录", "cups.recovery_clear_failed")
                    refreshSafetyNotification()
                    event("累计杯数重置命令未写入", "cups.failed")
                }
                CupResetTracker.State.UNKNOWN -> {
                    recoveryAfterSettingsSerial = cupSettingsSerial
                    recoveryAfterIdleSerial = cupIdleSerial
                    event("累计杯数重置结果未知", "cups.unknown")
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
                event("Mock 睡眠计划已更新；未发送蓝牙命令", "mock.sleep_schedule")
            }
            return result
        }
        if (manualShotActive) return "手动萃取期间不能修改睡眠计划"
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return "正在等待累计杯数归零回报"
        if (ShotGate.active(shotState)) return "萃取期间不能修改睡眠计划"
        if (scheduleWriteState == SleepScheduleWriteTracker.State.UNKNOWN)
            return "上次计划结果未知，请重新连接并等待两段完整回报"
        if (scheduleBusy || settingWriteState in
            setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return "正在等待上一次机器设置回读"
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "正在等待机器进入睡眠"
        if (brewPreparation.active) return "请先取消曲线预热"
        if (snapshot.coffeeState != DeviceState.READY) return "咖啡机尚未就绪"
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return "等待咖啡机待机数据"
        if (snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } != true || idle.sleepStateRaw != 0)
            return "需要新鲜、已唤醒的待机状态"
        if (!sleepScheduleFresh) return "睡眠计划回报已过期或两段尚未配齐，请等待新回报"
        val observed = WeeklySleepSchedule.fromReadback(snapshot.sleepFirst, snapshot.sleepSecond)
            ?: return "睡眠计划尚未完整回读"
        if (observed.days != expected.days) return "机器睡眠计划已变化，请重新编辑"
        val changedDays = expected.days.indices.count { expected.days[it] != target.days[it] }
        if (changedDays == 0) return "机器回读已是该计划"
        if (changedDays != 1) return "一次只能修改一天的睡眠计划"
        val coffeeAddress = current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止计划写入"
        val token = scheduleWrite.begin(target, firstSleepSerial, secondSleepSerial)
            ?: return "正在等待上一次睡眠计划结果"
        if (!machineWriteRecovery.arm(MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE, coffeeAddress)) {
            scheduleWrite.written(token, OperationResult.Failed("safety record unavailable"),
                firstSleepSerial, secondSleepSerial, snapshot.sleepFirst, snapshot.sleepSecond)
            return "无法可靠保存计划安全状态，已阻止发送"
        }
        recoveryAfterFirstSleepSerial = firstSleepSerial
        recoveryAfterSecondSleepSerial = secondSleepSerial
        refreshSafetyNotification()
        event("整周睡眠计划两包写入已排队", "sleep_schedule.requested")
        current.writeSleepSchedule(target,expected) done@{ result ->
            if (!scheduleWrite.written(token, result, firstSleepSerial, secondSleepSerial,
                    snapshot.sleepFirst, snapshot.sleepSecond)) return@done
            when (scheduleWrite.state) {
                SleepScheduleWriteTracker.State.CONFIRMED -> {
                    if (!machineWriteRecovery.clear())
                        event("无法清除睡眠计划安全记录", "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event("机器已回读完整睡眠计划", "sleep_schedule.confirmed")
                }
                SleepScheduleWriteTracker.State.WAITING_READBACK -> {
                    event("两包计划已写入，等待机器回读整周", "sleep_schedule.written")
                    handler.postDelayed({
                        if (scheduleWrite.timeout(token, firstSleepSerial, secondSleepSerial)) {
                            recoveryAfterFirstSleepSerial = firstSleepSerial
                            recoveryAfterSecondSleepSerial = secondSleepSerial
                            event("睡眠计划未完整回读，结果未知", "sleep_schedule.unknown")
                        }
                    }, 8000)
                }
                SleepScheduleWriteTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event("无法清除睡眠计划安全记录", "sleep_schedule.recovery_clear_failed")
                    refreshSafetyNotification()
                    event("睡眠计划首包未写入", "sleep_schedule.failed")
                }
                SleepScheduleWriteTracker.State.UNKNOWN -> {
                    recoveryAfterFirstSleepSerial = firstSleepSerial
                    recoveryAfterSecondSleepSerial = secondSleepSerial
                    event("睡眠计划可能部分写入，请核对机器", "sleep_schedule.unknown")
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
                event("Mock 咖啡机已入睡；未发送蓝牙命令", "mock.sleep")
            }
            return result
        }
        if (manualShotActive) return "手动萃取期间不能让机器睡眠"
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return "正在等待累计杯数归零回报"
        if (scheduleBusy) return "正在等待睡眠计划回读"
        if (ShotGate.active(shotState)) return "萃取期间不能让机器睡眠"
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return "正在等待机器设置回读"
        if (brewPreparation.active) return "请先取消曲线预热"
        if (snapshot.coffeeState != DeviceState.READY) return "咖啡机尚未就绪"
        val now = SystemClock.elapsedRealtime()
        val idle = snapshot.coffee as? IdleTelemetry ?: return "等待咖啡机待机数据"
        val observedAt = snapshot.coffeeAt ?: return "等待咖啡机待机数据"
        if (observedAt > now || now - observedAt > 1500) return "咖啡机待机数据已过期"
        if (idle.sleepStateRaw == 1) return "机器已经入睡；请用拨杆唤醒"
        if (idle.sleepStateRaw != 0) return "机器睡眠状态未知，暂不发送"
        val coffeeAddress = current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止入睡命令"
        val token = sleepNow.begin() ?: return "正在等待本次入睡结果"
        if (!machineWriteRecovery.arm(MachineWriteRecoveryState.Kind.SLEEP_NOW, coffeeAddress)) {
            sleepNow.written(token, OperationResult.Failed("safety record unavailable"), sleepSampleSerial)
            return "无法可靠保存入睡安全状态，已阻止发送"
        }
        recoveryAfterSleepSerial = sleepSampleSerial
        refreshSafetyNotification()
        event("立即睡眠命令已排队", "sleep.requested")
        current.enterSleep done@{ result ->
            if (!sleepNow.written(token, result, sleepSampleSerial)) return@done
            when (sleepNow.state) {
                SleepNowTracker.State.WAITING_ASLEEP -> {
                    event("命令已写入，等待机器回报睡眠", "sleep.written")
                    handler.postDelayed({
                        if (sleepNow.timeout(token, sleepSampleSerial)) {
                            recoveryAfterSleepSerial = sleepSampleSerial
                            event("机器未回报睡眠，结果未知", "sleep.unknown")
                        }
                    }, 12_000)
                }
                SleepNowTracker.State.FAILED -> {
                    if (!machineWriteRecovery.clear())
                        event("无法清除立即睡眠安全记录", "sleep.recovery_clear_failed")
                    refreshSafetyNotification()
                    event("立即睡眠命令未写入", "sleep.failed")
                }
                SleepNowTracker.State.UNKNOWN -> {
                    recoveryAfterSleepSerial = sleepSampleSerial
                    event("立即睡眠结果未知，请查看机器", "sleep.unknown")
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
            snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } != true) return null
        return BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
            settings.brewCompensationTenthsC)
    }
    fun studioStartBlock(profile: CurveProfile): String? = if (!machineSettingsFresh)
        "机器设置回报已过期，请等待新回报" else StudioStartGate.block(
        snapshot.settings, currentCorrectedBrewTemperature(), profile, brewPreparation)
    fun prepareBrew(profileId: String, expectedScaleMode: Boolean?, slot: Int): String? {
        if (mock != null) {
            if (brewPreparation.active) return "已有 Mock 预热请求，请先取消"
            val profile = selectedCurve(profileId, slot) ?: return "请先选择曲线"
            if (profile.scaleMode != expectedScaleMode) return "Mock 电子秤状态已变化，请重新确认"
            val library = (application as MobileApplication).curves
            ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
                snapshot.scaleState, snapshot.weightAt, SystemClock.elapsedRealtime(), shotState,
                validated = library.validated(profile))?.let { return it }
            val now = SystemClock.elapsedRealtime()
            val result = mock.beginPreheat(profile.temperatureC, now)
            if (result != null) return result
            val token = brewPreparation.begin(profile.id, profile.temperatureC)
                ?: run { mock?.cancelPreheat(now); return "无法开始 Mock 预热" }
            brewPreparation.written(token, OperationResult.Success(), idleSampleSerial)
            refreshMock(mock, now)
            event("Mock 正在模拟预热到 ${profile.temperatureC} °C；未发送蓝牙命令", "mock.brew_wait")
            return null
        }
        if (manualShotActive) return "手动萃取期间不能预热曲线"
        machineControlSafetyMessage?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return "正在等待累计杯数归零回报"
        if (scheduleBusy) return "正在等待睡眠计划回读"
        if (brewPreparation.active) return "已有预热请求，请先取消"
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "正在等待机器进入睡眠"
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return "正在等待机器设置回读"
        val settings = snapshot.settings ?: return "尚未收到机器设置"
        if (!machineSettingsFresh) return "机器设置回报已过期，请等待新回报后再预热"
        if (settings.flags and 0x04 == 0) return "当前是咖啡馆模式，无需曲线预热"
        val profile = selectedCurve(profileId, slot)
        if (profile != null && profile.scaleMode != expectedScaleMode) return "电子秤状态已变化，请重新确认"
        val library = (application as MobileApplication).curves
        val blocked = ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt,
            snapshot.scaleState, snapshot.weightAt, SystemClock.elapsedRealtime(), current.extraction.state,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) return blocked
        requireNotNull(profile)
        val actual = currentCorrectedBrewTemperature() ?: return "等待新鲜冲泡温度"
        if (BrewPreparation.isAtTarget(actual, profile.temperatureC)) return "温度已达到目标，可直接确认萃取"
        val coffeeAddress = current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止预热"
        val token = brewPreparation.begin(profile.id, profile.temperatureC) ?: return "无法开始预热"
        if (!machineWriteRecovery.arm(MachineWriteRecoveryState.Kind.BREW_WAIT, coffeeAddress)) {
            brewPreparation.consumed()
            return "无法可靠保存预热安全状态，已阻止预热"
        }
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        refreshSafetyNotification()
        event("曲线预热命令已排队：${profile.temperatureC} °C", "brew_wait.requested")
        current.setBrewWait(profile.temperatureC) done@{ result ->
            if (!brewPreparation.written(token, result, idleSampleSerial)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.WAITING_TEMP -> {
                    event("预热命令已写入，等待温度到达", "brew_wait.written")
                    handler.postDelayed({
                        if (brewPreparation.isActive(token)) {
                            val blocked = cancelBrewPreparation()
                            if (blocked == null && brewPreparation.state != BrewPreparation.State.UNKNOWN)
                                event("预热超过10分钟，已请求取消", "brew_wait.timeout_cancel_requested")
                            else if (brewPreparation.timedOut(token)) {
                                event("预热超时，取消命令未发送：$blocked。请检查机器", "brew_wait.timeout_cancel_blocked")
                                refreshSafetyNotification()
                            }
                        }
                    }, 600_000)
                }
                BrewPreparation.State.FAILED -> event("预热命令未写入", "brew_wait.failed")
                BrewPreparation.State.UNKNOWN -> event("预热写入结果未知，请查看机器", "brew_wait.unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    fun cancelBrewPreparation(): String? {
        if (mock != null) {
            if (!brewPreparation.active) return "当前没有 Mock 预热请求"
            val now = SystemClock.elapsedRealtime()
            val result = mock.cancelPreheat(now)
            if (result != null) return result
            val token = brewPreparation.beginCancel() ?: return "正在取消 Mock 预热"
            brewPreparation.cancelled(token, OperationResult.Success())
            brewPreparation.consumed()
            refreshMock(mock, now)
            event("Mock 预热已取消；未发送蓝牙命令", "mock.brew_wait_cancelled")
            return null
        }
        if (manualShotActive) return "手动萃取期间不能发送预热取消命令"
        val recovering = machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT
        if (!brewPreparation.active && !recovering) return "当前没有预热请求"
        val current = hub ?: return "设备服务尚未启动，预热结果未知"
        if (!machineWriteRecovery.matchesDevice(current.coffeeAddress)) return "请先连接预热使用的原咖啡机"
        brewWaitCancelBlock?.let { return it }
        if (!brewPreparation.active && recovering) {
            if (!brewPreparation.restoreUnknown()) return "无法进入预热恢复状态"
        }
        val token = brewPreparation.beginCancel() ?: return "取消命令已写入或正在取消；请检查机器"
        recoveryAfterBrewWaitIdleSerial = idleSampleSerial
        event("取消预热命令已排队", "brew_wait.cancel_requested")
        current.setBrewWait(0) done@{ result ->
            if (!brewPreparation.cancelled(token, result)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.CANCEL_WRITTEN -> event("取消预热命令已写入；请检查机器，回报待机后人工确认", "brew_wait.cancel_written")
                BrewPreparation.State.UNKNOWN -> event("取消预热结果未知，请查看机器", "brew_wait.cancel_unknown")
                else -> Unit
            }
            refreshSafetyNotification()
        }
        return null
    }
    fun startShot(profileId: String, expectedScaleMode: Boolean? = null, slot: Int = 7): String? {
        if (mock != null) {
            if (mock.isSleeping) return "Mock 咖啡机已入睡；重启模拟服务可复位"
            val profile = selectedCurve(profileId, slot) ?: return "请先选择曲线"
            if (profile.scaleMode != expectedScaleMode) return "Mock 电子秤状态已变化，请重新确认"
            if (ShotGate.active(shotState)) return "Mock 萃取正在进行"
            studioStartBlock(profile)?.let { return it }
            val now = SystemClock.elapsedRealtime()
            if (brewPreparation.active) {
                mock.cancelPreheat(now)
                brewPreparation.consumed()
            }
            mock.start(now)
            activeShotTargetHundredthsGram = profile.targetHundredthsGram
            val shotId = runCatching { history?.begin(profile.id, slot = slot) }.getOrNull()
                ?: java.util.UUID.randomUUID().toString()
            series.begin(shotId, SystemClock.elapsedRealtime())
            event("Mock 萃取已开始：${profile.name}；未发送蓝牙命令", "mock.shot_started")
            return null
        }
        if (manualShotActive) return "机器手动萃取进行中，请先用拨杆结束"
        if (machineWriteRecovery.kind != MachineWriteRecoveryState.Kind.BREW_WAIT ||
            !machineWriteRecovery.matchesDevice(hub?.coffeeAddress) ||
            !brewPreparation.matches(profileId, selectedCurve(profileId, slot)?.temperatureC ?: -1))
            machineWriteSafetyMessage?.let { return it }
        if (shotRecovery.pending) return restartShotWarning
        tareStartBlock?.let { return it }
        val current = hub ?: return "设备服务尚未启动"
        if (settingWriteUnresolved) return settingWriteUnresolvedMessage
        if (sleepNowUnresolved) return sleepNowUnresolvedMessage
        if (cupResetBusy) return "正在等待累计杯数归零回报，不能启动萃取"
        if (scheduleBusy) return "正在等待睡眠计划回读，不能启动萃取"
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "正在等待机器进入睡眠，不能启动萃取"
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return "正在等待机器设置回读，不能启动萃取"
        val library = (application as MobileApplication).curves
        val profile = selectedCurve(profileId, slot)
        if (profile != null && profile.scaleMode != expectedScaleMode) {
            event("电子秤连接状态已变化，请重新确认", "shot.rejected")
            return "电子秤连接状态已变化，请重新确认"
        }
        val blocked = ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
            snapshot.weightAt, SystemClock.elapsedRealtime(), current.extraction.state,
            validated = profile?.let(library::validated) == true)
        if (blocked != null) { event("启动被阻止：$blocked", "shot.rejected"); return blocked }
        requireNotNull(profile)
        studioStartBlock(profile)?.let { return it }
        if (current.extraction.state == ExtractionState.ENDED_OBSERVED) {
            runCatching { history?.transition(ExtractionState.ENDED_OBSERVED, stopReason, null) }
                .onFailure { event("历史记录失败", "shot.history_error") }
            finishSeries(true)
        }
        logs.record("shot.start.attempt", mapOf("ownerId" to ownerId, "curveId" to profile.id,
            "targetHundredthsGram" to profile.targetHundredthsGram.toString(),
            "scaleMode" to (profile.scaleMode?.toString() ?: "captured"), "slot" to slot.toString()))
        val coffeeAddress=current.coffeeAddress ?: return "无法确认咖啡机身份，已阻止启动"
        if (!shotRecovery.arm(coffeeAddress)) return "无法可靠保存萃取安全状态，已阻止启动"
        if (!current.extraction.start(profile.parameters, profile.targetHundredthsGram, profile.compensationHundredthsGram) ||
            current.extraction.state == ExtractionState.IDLE) {
            if (!shotRecovery.clear()) manualSafetyMessage = restartShotWarning
            event("启动未被会话层接受", "shot.rejected")
            return "启动未被会话层接受"
        }
        activeShotTargetHundredthsGram = profile.targetHundredthsGram
        if (brewPreparation.active) brewPreparation.consumed()
        if (machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT)
            brewWaitShotStarted = true
        val shotId = runCatching { history?.begin(profile.id, slot = slot) }
            .onFailure { event("历史记录失败", "shot.history_error") }
            .getOrNull() ?: java.util.UUID.randomUUID().toString()
        series.begin(shotId, SystemClock.elapsedRealtime())
        if (current.extraction.state == ExtractionState.OUTCOME_UNKNOWN) {
            event("启动结果未知，请检查咖啡机", "shot.unknown")
            return "启动结果未知，请检查咖啡机"
        }
        event(if (current.extraction.preparingScale) "正在确认电子秤归零；确认后启动：${profile.name}"
            else "已提交萃取请求：${profile.name}", "shot.requested")
        return null
    }
    fun stopShot() {
        if (mock != null) {
            if (ShotGate.active(shotState)) {
                mock.stop()
                event("Mock 萃取已停止；未发送蓝牙命令", "mock.shot_stopped")
            }
            return
        }
        if (manualShotActive) { event("手动萃取请使用机器拨杆停止；App 未发送命令"); return }
        if (!ShotGate.active(shotState)) return
        if (shotState == ExtractionState.OUTCOME_UNKNOWN && snapshot.coffeeState != DeviceState.READY) {
            event("咖啡机未连接，无法发送停止命令；请先重连并检查机器", "shot.stop_unavailable")
            return
        }
        event("用户请求停止萃取", "shot.manual_stop")
        hub?.extraction?.manualStop()
    }
    private fun finishSeries(observedEnd: Boolean) {
        val finished = series.finish() ?: return
        val app = application as MobileApplication
        if (observedEnd && finished.second.isNotEmpty() &&
            history?.entries?.any { it.id == finished.first } == true) {
            app.samples.save(finished.first, finished.second)
        }
        history?.entries?.map(ShotHistory.Entry::id)?.toSet()?.let(app.samples::prune)
    }
    private fun recordMachinePoint(frame: io.openhoyi.protocol.ExtractionTelemetry, atMs: Long) {
        series.machine(frame, atMs,
            snapshot.weight?.weightHundredthsGram?.takeIf { snapshot.scaleState == DeviceState.READY },
            snapshot.weightAt,
            snapshot.weight?.deviceFlowHundredths?.takeIf { snapshot.scaleState == DeviceState.READY })
        saveSeriesCheckpoint(atMs)
    }
    private fun saveSeriesCheckpoint(atElapsedMs: Long = SystemClock.elapsedRealtime(), force: Boolean = false) {
        series.checkpoint(atElapsedMs, force)?.let { (id, points) ->
            runCatching { (application as MobileApplication).samples.save(id, points) }
                .onFailure { event("曲线采样暂存失败", "shot.samples_error") }
        }
    }
    private fun event(message: String, kind: String = "mobile.event") {
        snapshot = snapshot.copy(message = message)
        logs.record(kind, mapOf("message" to message, "ownerId" to ownerId))
        Log.i(TAG, message)
    }
    private fun connectionNotification(warning: String?): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, HomeActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MobileService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(if (warning == null) "OpenHOYI Alpha" else "设备状态需人工确认")
            .setContentText(warning ?: "设备连接运行中")
            .setStyle(warning?.let { Notification.BigTextStyle().bigText(it) })
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "断开设备", stop).build()).build()
    }
    private fun refreshSafetyNotification() {
        if (mock != null) return
        val warning = ShotSafetyAlert.message(shotState, snapshot.coffeeState) ?:
            manualSafetyMessage ?: machineWriteSafetyMessage
        if (warning == safetyMessage) return
        safetyMessage = warning
        if (!running) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(1, connectionNotification(warning)) }
            .onFailure { event("前台安全提醒更新失败", "shot.safety_notify_error") }
        if (warning == null) manager.cancel(SAFETY_NOTIFICATION)
        else runCatching {
            val destination = if (machineWriteSafetyMessage != null && manualSafetyMessage == null &&
                ShotSafetyAlert.message(shotState, snapshot.coffeeState) == null)
                HomeActivity::class.java else ExtractionActivity::class.java
            val open = PendingIntent.getActivity(this, 2, Intent(this, destination),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(SAFETY_NOTIFICATION, Notification.Builder(this, SAFETY_CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("请立即检查咖啡机")
                .setContentText(warning)
                .setStyle(Notification.BigTextStyle().bigText(warning))
                .setContentIntent(open).setCategory(Notification.CATEGORY_ALARM)
                .setOngoing(true).build())
        }.onFailure { event("安全提醒通知不可用", "shot.safety_notify_error") }
    }
    fun acknowledgeManualSafety() {
        if (manualSafetyMessage == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.BREW_WAIT) {
            if (shotRecovery.pending || ShotGate.active(shotState)) {
                event("请先确认萃取结束，再核对预热状态", "brew_wait.recovery_shot_active")
                return
            }
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val state = brewPreparation.state
            val evidence = MachineWriteRecoveryState.BrewWaitEvidence(hub?.coffeeAddress,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                idleSampleSerial, snapshot.coffeeAt, now,
                state !in setOf(BrewPreparation.State.IDLE, BrewPreparation.State.FAILED,
                    BrewPreparation.State.UNKNOWN, BrewPreparation.State.CANCEL_WRITTEN))
            if (snapshot.coffeeState != DeviceState.READY ||
                !machineWriteRecovery.canClearBrewWait(evidence, recoveryAfterBrewWaitIdleSerial)) {
                event("请连接原咖啡机，检查预热状态并等待新的已唤醒待机回报", "brew_wait.recovery_waiting")
                return
            }
            if (!machineWriteRecovery.clear()) {
                event("无法保存预热安全记录的清除状态", "brew_wait.recovery_clear_failed")
                return
            }
            brewPreparation.consumed()
            event("用户已核对机器预热状态，清除上次预热提醒", "brew_wait.recovery_acknowledged")
            refreshSafetyNotification()
            return
        }
        if (manualSafetyMessage == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.CUP_RESET) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.CupResetEvidence(hub?.coffeeAddress,
                snapshot.settings?.cupCount, cupSettingsSerial, idle?.cupCount, cupIdleSerial,
                snapshot.coffeeAt, now, cupResetBusy)
            if (snapshot.coffeeState != DeviceState.READY ||
                !machineWriteRecovery.canClearCupReset(evidence,
                    recoveryAfterSettingsSerial, recoveryAfterIdleSerial)) {
                event("请连接原咖啡机，等待新的设置与待机杯数一致后再清除提示", "cups.recovery_waiting")
                return
            }
            if (!machineWriteRecovery.clear()) {
                event("无法保存杯数重置安全记录的清除状态", "cups.recovery_clear_failed")
                return
            }
            event("用户已核对机器杯数，清除上次重置提醒", "cups.recovery_acknowledged")
            refreshSafetyNotification()
            return
        }
        if (manualSafetyMessage == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SETTING) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.SettingEvidence(hub?.coffeeAddress,
                snapshot.settings != null, settingsSampleSerial,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                snapshot.coffeeAt, now,
                settingsWrite.state in setOf(SettingsWriteTracker.State.WRITING,
                    SettingsWriteTracker.State.WAITING_READBACK))
            if (snapshot.coffeeState != DeviceState.READY ||
                !machineWriteRecovery.canClearSetting(evidence, recoveryAfterSettingsSerial)) {
                event("请连接原咖啡机，等待新的设置与已唤醒待机状态后再清除提示", "settings.recovery_waiting")
                return
            }
            if (!machineWriteRecovery.clear()) {
                event("无法保存机器设置安全记录的清除状态", "settings.recovery_clear_failed")
                return
            }
            event("用户已核对机器当前设置，清除上次写入提醒", "settings.recovery_acknowledged")
            refreshSafetyNotification()
            return
        }
        if (manualSafetyMessage == null &&
            machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_SCHEDULE) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.ScheduleEvidence(hub?.coffeeAddress,
                sleepScheduleFresh,
                firstSleepSerial, secondSleepSerial,
                idle?.let { it.sleepStateRaw == 0 && it.alarmBits and 0xBFFF == 0 } == true,
                snapshot.coffeeAt, now, scheduleBusy)
            if (snapshot.coffeeState != DeviceState.READY ||
                !machineWriteRecovery.canClearSchedule(evidence,
                    recoveryAfterFirstSleepSerial, recoveryAfterSecondSleepSerial)) {
                event("请连接原咖啡机，等待整周计划两段新回报及唤醒待机后再清除提示",
                    "sleep_schedule.recovery_waiting")
                return
            }
            if (!machineWriteRecovery.clear()) {
                event("无法保存睡眠计划安全记录的清除状态", "sleep_schedule.recovery_clear_failed")
                return
            }
            event("用户已核对整周睡眠计划，清除上次写入提醒", "sleep_schedule.recovery_acknowledged")
            refreshSafetyNotification()
            return
        }
        if (manualSafetyMessage == null && machineWriteRecovery.kind == MachineWriteRecoveryState.Kind.SLEEP_NOW) {
            val now = SystemClock.elapsedRealtime()
            val idle = snapshot.coffee as? IdleTelemetry
            val evidence = MachineWriteRecoveryState.SleepEvidence(hub?.coffeeAddress,
                idle?.sleepStateRaw, sleepSampleSerial, snapshot.coffeeAt, now,
                sleepNow.state in setOf(SleepNowTracker.State.WRITING,
                    SleepNowTracker.State.WAITING_ASLEEP))
            if (snapshot.coffeeState != DeviceState.READY ||
                !machineWriteRecovery.canClearSleep(evidence, recoveryAfterSleepSerial)) {
                event("请连接原咖啡机，等待新的明确睡眠状态后再清除提示", "sleep.recovery_waiting")
                return
            }
            if (!machineWriteRecovery.clear()) {
                event("无法保存立即睡眠安全记录的清除状态", "sleep.recovery_clear_failed")
                return
            }
            event("用户已核对机器睡眠状态，清除上次入睡提醒", "sleep.recovery_acknowledged")
            refreshSafetyNotification()
            return
        }
        if (manualSafetyMessage == null) return
        shotRecoveryClearBlock?.let {
            event(it, "shot.recovery_waiting")
            return
        }
        if (shotRecovery.pending) {
            if (!shotRecovery.clear()) {
                event("萃取安全记录未能保存清除，请重试", "shot.recovery_clear_failed")
                return
            }
        }
        manualSafetyMessage = null
        event("用户已检查手动萃取状态", "shot.passive_acknowledged")
        refreshSafetyNotification()
    }
    fun shutdown() {
        if (mock != null) {
            if (ShotGate.active(shotState)) mock.stop()
            running = false
            handler.removeCallbacks(mockTick)
            snapshot = MobileSnapshot(message = "Mock 数据已停止")
            stopSelf()
            return
        }
        if (manualShotActive) { event("手动萃取进行中，请先用机器拨杆结束", "service.stop_deferred"); return }
        if (cupResetBusy) {
            event("累计杯数重置尚未确认，设备服务保持运行", "service.stop_deferred")
            return
        }
        if (scheduleBusy) {
            event("睡眠计划尚未确认，设备服务保持运行", "service.stop_deferred")
            return
        }
        if (ShotGate.active(shotState)) {
            stopShot()
            event("萃取结果未确认，设备服务保持运行", "service.stop_deferred")
            return
        }
        if (shotRecovery.pending) {
            event("上次萃取未确认结束，设备服务保持运行", "service.stop_deferred")
            return
        }
        if (machineWriteRecovery.pending) {
            event("上次机器写入未确认，设备服务保持运行", "service.stop_deferred")
            return
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event("正在取消曲线预热，设备服务保持运行", "service.stop_deferred")
            return
        }
        hub?.close(); hub = null; running = false
        safetyMessage = null
        getSystemService(NotificationManager::class.java).cancel(SAFETY_NOTIFICATION)
        handler.removeCallbacks(leaveForeground)
        handler.removeCallbacks(stopAutomaticScale)
        hubForeground = false
        snapshot = snapshot.copy(coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED,
            coffee = null, coffeeAt = null, alarmBits = null, alarmAt = null, scanning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        if (passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
            passiveHistoryId?.let { id -> runCatching { history?.abandon(id, "设备服务停止") } }
            saveSeriesCheckpoint(force = true)
            finishSeries(false)
        }
        handler.removeCallbacks(mockTick)
        getSystemService(NotificationManager::class.java).cancel(SAFETY_NOTIFICATION)
        handler.removeCallbacks(watchShot)
        handler.removeCallbacks(leaveForeground)
        handler.removeCallbacks(stopAutomaticScale)
        hub?.close(); hub = null; hubForeground = false
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
