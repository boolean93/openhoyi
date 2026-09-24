package io.openhoyi.mobile

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
import io.openhoyi.session.CoffeeAuthentication
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
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
    val sleepFirst: SleepPart? = null,
    val sleepSecond: SleepPart? = null,
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
    val manualShotActive: Boolean get() = passiveShot.active
    private val standaloneTare = StandaloneTare()
    private val settingsWrite = SettingsWriteTracker()
    private val cupReset = CupResetTracker()
    val cupResetState: CupResetTracker.State get() = if (mock?.cupReset == true)
        CupResetTracker.State.CONFIRMED else cupReset.state
    private val cupResetBusy: Boolean get() = cupReset.state in
        setOf(CupResetTracker.State.WRITING, CupResetTracker.State.WAITING_ZERO)
    private var cupSampleSerial = 0L
    private val scheduleWrite = SleepScheduleWriteTracker()
    private val sleepNow = SleepNowTracker()
    private val brewPreparation = BrewPreparation()
    private var idleSampleSerial = 0L
    val brewPreparationState: BrewPreparation.State get() = brewPreparation.state
    val brewPreparationProfileId: String? get() = brewPreparation.profileId
    val brewPreparationTargetC: Int? get() = brewPreparation.targetC
    private var sleepSampleSerial = 0L
    val sleepNowState: SleepNowTracker.State get() = if (mock?.isSleeping == true)
        SleepNowTracker.State.CONFIRMED else sleepNow.state
    private var settingsSampleSerial = 0L
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
    private var scaleSampleSerial = 0L
    val tareState: StandaloneTare.State get() = if (mock?.tareChanged == true)
        StandaloneTare.State.CONFIRMED else standaloneTare.state
    val chartPoints: List<ShotPoint> get() = series.points
    private val ownerId = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private var hub: NativeDeviceHub? = null
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
    val shotState: ExtractionState get() = mock?.shotState ?: hub?.extraction?.state ?: ExtractionState.IDLE
    val stopReason: String? get() = hub?.extraction?.stopReason?.name
    private val watchShot = object : Runnable {
        override fun run() {
            val current = shotState
            if (current != lastShotState) {
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
                }
                if (current == ExtractionState.OUTCOME_UNKNOWN) saveSeriesCheckpoint(force = true)
                event("萃取状态：${current.name}", "shot.state")
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
                            settingsWrite.disconnected()
                            cupReset.disconnected()
                            scheduleWrite.disconnected(firstSleepSerial, secondSleepSerial)
                            sleepNow.disconnected()
                            brewPreparation.disconnected()
                        }
                        if (state == DeviceState.DISCONNECTED || state == DeviceState.FAILED)
                            snapshot.copy(coffeeState = state, coffee = null, coffeeAt = null,
                                alarmBits = null, alarmAt = null,
                                settings = null, sleepFirst = null, sleepSecond = null)
                        else snapshot.copy(coffeeState = state)
                    } else if (state != DeviceState.READY) {
                        standaloneTare.disconnected()
                        snapshot.copy(scaleState = state, weight = null, weightAt = null)
                    }
                    else snapshot.copy(scaleState = state)
                    if (role == DeviceRole.COFFEE && state != DeviceState.READY &&
                        passiveShot.disconnected() == PassiveShotDetector.Event.Interrupted) {
                        passiveHistoryId?.let { id ->
                            runCatching { history?.abandon(id, "连接中断") }
                                .onFailure { event("手动萃取历史保存失败", "shot.history_error") }
                        }
                        passiveHistoryId = null
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
                            if (cupReset.observe(++cupSampleSerial, frame.cupCount))
                                event("机器已回报累计杯数归零", "cups.confirmed")
                            if (settingsWrite.observe(++settingsSampleSerial, frame))
                                event("机器回读已确认设置", "settings.confirmed")
                            snapshot.copy(settings = frame)
                        }
                        is SleepPart -> {
                            if (frame.firstDaySundayIndex == 0) {
                                firstSleepSerial++
                                snapshot = snapshot.copy(sleepFirst = frame)
                            } else {
                                secondSleepSerial++
                                snapshot = snapshot.copy(sleepSecond = frame)
                            }
                            if (scheduleWrite.observe(firstSleepSerial, secondSleepSerial,
                                    snapshot.sleepFirst, snapshot.sleepSecond)) {
                                if (scheduleWrite.state == SleepScheduleWriteTracker.State.CONFIRMED)
                                    event("机器已回读完整睡眠计划", "sleep_schedule.confirmed")
                                else event("已重新收到完整计划，请核对机器时间", "sleep_schedule.reconciled")
                            }
                            snapshot
                        }
                        is IdleTelemetry -> {
                            if (cupReset.observe(++cupSampleSerial, frame.cupCount))
                                event("机器已回报累计杯数归零", "cups.confirmed")
                            if (sleepNow.observe(++sleepSampleSerial, frame.sleepStateRaw))
                                event("机器已回报进入睡眠", "sleep.confirmed")
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
                            manualSafetyMessage = null
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
                    val before = standaloneTare.state
                    standaloneTare.sample(++scaleSampleSerial, it.weightHundredthsGram)
                    if (before != standaloneTare.state && standaloneTare.state == StandaloneTare.State.CONFIRMED)
                        event("电子秤已归零", "scale.tare_confirmed")
                    snapshot = snapshot.copy(weight = it, weightAt = SystemClock.elapsedRealtime())
                },
                diagnostic = { event("设备通信异常") },
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
    fun connectCoffee(address: String, password: String) {
        if (mock != null) { event("Mock 咖啡机已就绪；未连接蓝牙", "mock.connect"); return }
        if (manualShotActive) { event("手动萃取进行中，请先用机器拨杆结束"); return }
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
            settings = null, sleepFirst = null, sleepSecond = null)
        event("连接咖啡机")
        current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password))
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
        val token = standaloneTare.begin() ?: return "正在等待本次去皮结果"
        event("电子秤去皮命令已排队", "scale.tare_requested")
        current.tareScale done@{ result ->
            if (!standaloneTare.written(token, result, scaleSampleSerial)) return@done
            when (standaloneTare.state) {
                StandaloneTare.State.WAITING_ZERO -> {
                    event("去皮命令已写入，等待电子秤归零", "scale.tare_written")
                    handler.postDelayed({
                        val before = standaloneTare.state
                        standaloneTare.timeout(token)
                        if (before != standaloneTare.state) event("等待归零超时，去皮结果未知", "scale.tare_unknown")
                    }, 5000)
                }
                StandaloneTare.State.FAILED -> event("去皮命令未写入", "scale.tare_failed")
                StandaloneTare.State.UNKNOWN -> event("去皮结果未知", "scale.tare_unknown")
                else -> Unit
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
        val current = hub ?: return "设备服务尚未启动"
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
        if (change is MachineSettingChange.SleepScheduleEnabled && change.enabled &&
            !SleepScheduleSafety.canEnable(snapshot.sleepFirst, snapshot.sleepSecond))
            return "睡眠计划尚未完整回读，不能开启"
        if (change is MachineSettingChange.StandbyDelay &&
            change.temperatureC != observed.standbyTemperatureC) return "机器待机温度已变化，请重新选择"
        if (change is MachineSettingChange.StandbyTemperature &&
            change.minutes != observed.standbyMinutes) return "机器自动待机时间已变化，请重新选择"
        if (change.matches(observed)) return "机器回读已是该设置"
        val token = settingsWrite.begin(change) ?: return "正在等待上一次设置的结果"
        event("机器设置命令已排队：${MachineSettingsPresentation.change(change)}", "settings.requested")
        current.writeSetting(change) done@{ result ->
            if (!settingsWrite.written(token, result, settingsSampleSerial)) return@done
            when (settingsWrite.state) {
                SettingsWriteTracker.State.WAITING_READBACK -> {
                    event("命令已写入，等待机器回读", "settings.written")
                    handler.postDelayed({
                        if (settingsWrite.timeout(token)) event("机器未回读，设置结果未知", "settings.unknown")
                    }, 6000)
                }
                SettingsWriteTracker.State.FAILED -> event("机器设置命令未写入", "settings.failed")
                SettingsWriteTracker.State.UNKNOWN -> event("机器设置写入结果未知", "settings.unknown")
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
        val current = hub ?: return "设备服务尚未启动"
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
        if (expectedCount !in 1..65535 || settingsCount != expectedCount || idle.cupCount != expectedCount)
            return "机器杯数已变化，请重新核对"
        val token = cupReset.begin(expectedCount) ?: return "正在等待本次杯数重置结果"
        event("累计杯数重置命令已排队", "cups.requested")
        current.resetCupCount done@{ result ->
            if (!cupReset.written(token, result, cupSampleSerial)) return@done
            when (cupReset.state) {
                CupResetTracker.State.WAITING_ZERO -> {
                    event("重置命令已写入，等待机器回报归零", "cups.written")
                    handler.postDelayed({
                        if (cupReset.timeout(token)) event("机器未回报归零，重置结果未知", "cups.unknown")
                    }, 12_000)
                }
                CupResetTracker.State.FAILED -> event("累计杯数重置命令未写入", "cups.failed")
                CupResetTracker.State.UNKNOWN -> event("累计杯数重置结果未知", "cups.unknown")
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
        val current = hub ?: return "设备服务尚未启动"
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
        val observed = WeeklySleepSchedule.fromReadback(snapshot.sleepFirst, snapshot.sleepSecond)
            ?: return "睡眠计划尚未完整回读"
        if (observed.days != expected.days) return "机器睡眠计划已变化，请重新编辑"
        val changedDays = expected.days.indices.count { expected.days[it] != target.days[it] }
        if (changedDays == 0) return "机器回读已是该计划"
        if (changedDays != 1) return "一次只能修改一天的睡眠计划"
        val token = scheduleWrite.begin(target, firstSleepSerial, secondSleepSerial)
            ?: return "正在等待上一次睡眠计划结果"
        event("整周睡眠计划两包写入已排队", "sleep_schedule.requested")
        current.writeSleepSchedule(target) done@{ result ->
            if (!scheduleWrite.written(token, result, firstSleepSerial, secondSleepSerial,
                    snapshot.sleepFirst, snapshot.sleepSecond)) return@done
            when (scheduleWrite.state) {
                SleepScheduleWriteTracker.State.CONFIRMED ->
                    event("机器已回读完整睡眠计划", "sleep_schedule.confirmed")
                SleepScheduleWriteTracker.State.WAITING_READBACK -> {
                    event("两包计划已写入，等待机器回读整周", "sleep_schedule.written")
                    handler.postDelayed({
                        if (scheduleWrite.timeout(token, firstSleepSerial, secondSleepSerial))
                            event("睡眠计划未完整回读，结果未知", "sleep_schedule.unknown")
                    }, 8000)
                }
                SleepScheduleWriteTracker.State.FAILED -> event("睡眠计划首包未写入", "sleep_schedule.failed")
                SleepScheduleWriteTracker.State.UNKNOWN -> event("睡眠计划可能部分写入，请核对机器", "sleep_schedule.unknown")
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
        val current = hub ?: return "设备服务尚未启动"
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
        val token = sleepNow.begin() ?: return "正在等待本次入睡结果"
        event("立即睡眠命令已排队", "sleep.requested")
        current.enterSleep done@{ result ->
            if (!sleepNow.written(token, result, sleepSampleSerial)) return@done
            when (sleepNow.state) {
                SleepNowTracker.State.WAITING_ASLEEP -> {
                    event("命令已写入，等待机器回报睡眠", "sleep.written")
                    handler.postDelayed({
                        if (sleepNow.timeout(token)) event("机器未回报睡眠，结果未知", "sleep.unknown")
                    }, 12_000)
                }
                SleepNowTracker.State.FAILED -> event("立即睡眠命令未写入", "sleep.failed")
                SleepNowTracker.State.UNKNOWN -> event("立即睡眠结果未知，请查看机器", "sleep.unknown")
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
        if (snapshot.coffeeState != DeviceState.READY ||
            snapshot.coffeeAt?.let { it <= now && now - it <= 1500 } != true) return null
        return BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
            settings.brewCompensationTenthsC)
    }
    fun studioStartBlock(profile: CurveProfile): String? = StudioStartGate.block(
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
        val current = hub ?: return "设备服务尚未启动"
        if (cupResetBusy) return "正在等待累计杯数归零回报"
        if (scheduleBusy) return "正在等待睡眠计划回读"
        if (brewPreparation.active) return "已有预热请求，请先取消"
        if (sleepNow.state in setOf(SleepNowTracker.State.WRITING, SleepNowTracker.State.WAITING_ASLEEP))
            return "正在等待机器进入睡眠"
        if (settingWriteState in setOf(SettingsWriteTracker.State.WRITING, SettingsWriteTracker.State.WAITING_READBACK))
            return "正在等待机器设置回读"
        val settings = snapshot.settings ?: return "尚未收到机器设置"
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
        val token = brewPreparation.begin(profile.id, profile.temperatureC) ?: return "无法开始预热"
        event("曲线预热命令已排队：${profile.temperatureC} °C", "brew_wait.requested")
        current.setBrewWait(profile.temperatureC) done@{ result ->
            if (!brewPreparation.written(token, result, idleSampleSerial)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.WAITING_TEMP -> {
                    event("预热命令已写入，等待温度到达", "brew_wait.written")
                    handler.postDelayed({
                        if (brewPreparation.isActive(token)) {
                            event("预热超过10分钟，正在取消", "brew_wait.timeout")
                            cancelBrewPreparation()
                        }
                    }, 600_000)
                }
                BrewPreparation.State.FAILED -> event("预热命令未写入", "brew_wait.failed")
                BrewPreparation.State.UNKNOWN -> event("预热写入结果未知，请查看机器", "brew_wait.unknown")
                else -> Unit
            }
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
            refreshMock(mock, now)
            event("Mock 预热已取消；未发送蓝牙命令", "mock.brew_wait_cancelled")
            return null
        }
        if (manualShotActive) return "手动萃取期间不能发送预热取消命令"
        if (!brewPreparation.active) return "当前没有预热请求"
        val current = hub ?: return "设备服务尚未启动，预热结果未知"
        if (snapshot.coffeeState != DeviceState.READY) return "咖啡机未就绪，无法确认取消预热"
        val token = brewPreparation.beginCancel() ?: return "正在取消预热"
        event("取消预热命令已排队", "brew_wait.cancel_requested")
        current.setBrewWait(0) done@{ result ->
            if (!brewPreparation.cancelled(token, result)) return@done
            when (brewPreparation.state) {
                BrewPreparation.State.IDLE -> event("取消预热命令已写入；机器状态无独立回读", "brew_wait.cancel_written")
                BrewPreparation.State.UNKNOWN -> event("取消预热结果未知，请查看机器", "brew_wait.cancel_unknown")
                else -> Unit
            }
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
            val shotId = runCatching { history?.begin(profile.id, slot = slot) }.getOrNull()
                ?: java.util.UUID.randomUUID().toString()
            series.begin(shotId, SystemClock.elapsedRealtime())
            event("Mock 萃取已开始：${profile.name}；未发送蓝牙命令", "mock.shot_started")
            return null
        }
        if (manualShotActive) return "机器手动萃取进行中，请先用拨杆结束"
        val current = hub ?: return "设备服务尚未启动"
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
        if (!current.extraction.start(profile.parameters, profile.targetHundredthsGram, profile.compensationHundredthsGram) ||
            current.extraction.state == ExtractionState.IDLE) {
            event("启动未被会话层接受", "shot.rejected")
            return "启动未被会话层接受"
        }
        if (brewPreparation.active) brewPreparation.consumed()
        val shotId = runCatching { history?.begin(profile.id, slot = slot) }
            .onFailure { event("历史记录失败", "shot.history_error") }
            .getOrNull() ?: java.util.UUID.randomUUID().toString()
        series.begin(shotId, SystemClock.elapsedRealtime())
        if (current.extraction.state == ExtractionState.OUTCOME_UNKNOWN) {
            event("启动结果未知，请检查咖啡机", "shot.unknown")
            return "启动结果未知，请检查咖啡机"
        }
        event("已提交萃取请求：${profile.name}", "shot.requested")
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
            .setContentTitle(if (warning == null) "OpenHOYI Alpha" else "萃取状态需人工确认")
            .setContentText(warning ?: "设备连接运行中")
            .setStyle(warning?.let { Notification.BigTextStyle().bigText(it) })
            .setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "断开设备", stop).build()).build()
    }
    private fun refreshSafetyNotification() {
        if (mock != null) return
        val warning = ShotSafetyAlert.message(shotState, snapshot.coffeeState) ?: manualSafetyMessage
        if (warning == safetyMessage) return
        safetyMessage = warning
        if (!running) return
        val manager = getSystemService(NotificationManager::class.java)
        runCatching { manager.notify(1, connectionNotification(warning)) }
            .onFailure { event("前台安全提醒更新失败", "shot.safety_notify_error") }
        if (warning == null) manager.cancel(SAFETY_NOTIFICATION)
        else runCatching {
            val open = PendingIntent.getActivity(this, 2, Intent(this, ExtractionActivity::class.java),
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
        if (manualSafetyMessage == null) return
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
