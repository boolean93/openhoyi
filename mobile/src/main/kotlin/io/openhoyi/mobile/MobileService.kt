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
import io.openhoyi.session.CoffeeAuthentication
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import io.openhoyi.trace.TraceStore
import java.time.LocalDateTime

data class MobileSnapshot(
    val coffeeState: DeviceState = DeviceState.DISCONNECTED,
    val scaleState: DeviceState = DeviceState.DISCONNECTED,
    val coffee: HoyiMessage? = null,
    val coffeeAt: Long? = null,
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
    private val standaloneTare = StandaloneTare()
    private val settingsWrite = SettingsWriteTracker()
    private val sleepNow = SleepNowTracker()
    private val brewPreparation = BrewPreparation()
    private var idleSampleSerial = 0L
    val brewPreparationState: BrewPreparation.State get() = brewPreparation.state
    val brewPreparationProfileId: String? get() = brewPreparation.profileId
    val brewPreparationTargetC: Int? get() = brewPreparation.targetC
    private var sleepSampleSerial = 0L
    val sleepNowState: SleepNowTracker.State get() = sleepNow.state
    private var settingsSampleSerial = 0L
    val settingWriteState: SettingsWriteTracker.State get() = settingsWrite.state
    val pendingSetting: MachineSettingChange? get() = settingsWrite.change
    private var scaleSampleSerial = 0L
    val tareState: StandaloneTare.State get() = standaloneTare.state
    val chartPoints: List<ShotPoint> get() = series.points
    private val ownerId = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private var hub: NativeDeviceHub? = null
    private val visibleScreens = VisibleScreens()
    private var hubForeground = false
    private val leaveForeground = object : Runnable {
        override fun run() {
            if (visibleScreens.visible || !hubForeground) return
            hub?.background()
            hubForeground = false
            snapshot = snapshot.copy(scanning = false)
        }
    }
    private var lastShotState = ExtractionState.IDLE
    var running = false; private set
    var snapshot = MobileSnapshot(); private set
    val shotState: ExtractionState get() = hub?.extraction?.state ?: ExtractionState.IDLE
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
                event("萃取状态：${current.name}", "shot.state")
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
        if (running) return START_NOT_STICKY
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "设备连接", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 0, Intent(this, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val stop = PendingIntent.getService(this, 1, Intent(this, MobileService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
            val note = Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("OpenHOYI Alpha").setContentText("设备连接运行中")
                .setContentIntent(open).setOngoing(true)
                .addAction(Notification.Action.Builder(null, "断开设备", stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(1, note)
            val prefs = getSharedPreferences("devices", MODE_PRIVATE)
            hub = NativeDeviceHub(applicationContext, prefs.getString("scale", null),
                onScaleRemembered = { prefs.edit().putString("scale", it).apply() },
                onState = { role, state ->
                    snapshot = if (role == DeviceRole.COFFEE) {
                        if (state != DeviceState.READY) {
                            settingsWrite.disconnected()
                            sleepNow.disconnected()
                            brewPreparation.disconnected()
                        }
                        if (state == DeviceState.DISCONNECTED || state == DeviceState.FAILED)
                            snapshot.copy(coffeeState = state, coffee = null, coffeeAt = null,
                                settings = null, sleepFirst = null, sleepSecond = null)
                        else snapshot.copy(coffeeState = state)
                    } else if (state != DeviceState.READY) {
                        standaloneTare.disconnected()
                        snapshot.copy(scaleState = state, weight = null, weightAt = null)
                    }
                    else snapshot.copy(scaleState = state)
                    event("${role.name}: ${state.name}")
                },
                onCoffee = { frame ->
                    snapshot = when (frame) {
                        is Settings -> {
                            if (settingsWrite.observe(++settingsSampleSerial, frame))
                                event("机器回读已确认设置", "settings.confirmed")
                            snapshot.copy(settings = frame)
                        }
                        is SleepPart -> if (frame.firstDaySundayIndex == 0) snapshot.copy(sleepFirst = frame)
                            else snapshot.copy(sleepSecond = frame)
                        is IdleTelemetry -> {
                            if (sleepNow.observe(++sleepSampleSerial, frame.sleepStateRaw))
                                event("机器已回报进入睡眠", "sleep.confirmed")
                            val currentSettings = snapshot.settings
                            if (currentSettings != null && brewPreparation.observe(++idleSampleSerial,
                                    BrewPreparation.correctedTemperature(frame.brewTemperatureHundredthsC,
                                        currentSettings.brewCompensationTenthsC)))
                                event("冲泡温度已达到曲线目标", "brew_wait.ready")
                            snapshot.copy(coffee = frame, coffeeAt = SystemClock.elapsedRealtime())
                        }
                        is io.openhoyi.protocol.ExtractionTelemetry ->
                            snapshot.copy(coffee = frame, coffeeAt = SystemClock.elapsedRealtime())
                        else -> snapshot
                    }
                    if (frame is io.openhoyi.protocol.ExtractionTelemetry) {
                        series.machine(frame, SystemClock.elapsedRealtime(),
                            snapshot.weight?.weightHundredthsGram?.takeIf { snapshot.scaleState == DeviceState.READY },
                            snapshot.weightAt)
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
            }
        } else if (hubForeground) {
            // Activity transitions may briefly have no resumed screen.
            handler.removeCallbacks(leaveForeground)
            handler.postDelayed(leaveForeground, 500)
        }
    }
    fun scan() {
        val current = hub ?: return
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
        require(password.matches(Regex("[0-9]{6}")))
        if (!ShotGate.mayReconnectCoffee(shotState)) {
            event("萃取尚未结束，不能重连咖啡机"); return
        }
        if (brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event("正在取消预热，请确认结果后再切换咖啡机", "brew_wait.connect_deferred")
            return
        }
        val current = hub ?: return
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, settings = null, sleepFirst = null, sleepSecond = null)
        event("连接咖啡机")
        current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password))
    }
    fun connectScale(address: String) {
        if (ShotGate.active(shotState)) { event("萃取尚未结束，不能切换电子秤"); return }
        val current = hub ?: return
        snapshot = snapshot.copy(weight = null, weightAt = null)
        event("连接电子秤")
        current.connectScale(address)
    }
    fun disconnect(role: DeviceRole) {
        if (ShotGate.active(shotState)) { event("萃取尚未结束，先停止萃取"); return }
        if (role == DeviceRole.COFFEE && brewPreparation.active && snapshot.coffeeState == DeviceState.READY) {
            cancelBrewPreparation()
            event("正在取消预热，请确认结果后再断开", "brew_wait.disconnect_deferred")
            return
        }
        event("断开 ${role.name}")
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    fun tareScale(): String? {
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
        val current = hub ?: return "设备服务尚未启动"
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
    fun enterSleepNow(): String? {
        val current = hub ?: return "设备服务尚未启动"
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
        val current = hub ?: return "设备服务尚未启动"
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
        val current = hub ?: return "设备服务尚未启动"
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
    private fun event(message: String, kind: String = "mobile.event") {
        snapshot = snapshot.copy(message = message)
        logs.record(kind, mapOf("message" to message, "ownerId" to ownerId))
        Log.i(TAG, message)
    }
    fun shutdown() {
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
        handler.removeCallbacks(leaveForeground)
        hubForeground = false
        snapshot = snapshot.copy(coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED, scanning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacks(watchShot)
        handler.removeCallbacks(leaveForeground)
        hub?.close(); hub = null; hubForeground = false
        super.onDestroy()
    }
    companion object { const val STOP = "io.openhoyi.mobile.STOP"; const val TAG = "OpenHoyiMobile"; private const val CHANNEL = "connections" }
}
