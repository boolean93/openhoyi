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
import io.openhoyi.protocol.Settings
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
    val weight: BookooSample? = null,
    val weightAt: Long? = null,
    val candidates: List<DiscoveredDevice> = emptyList(),
    val scanning: Boolean = false,
    val message: String = "尚未连接设备",
)

/** Product-app BLE owner. Screens only observe snapshots; no extraction command is exposed yet. */
class MobileService : Service() {
    inner class LocalBinder : Binder() { val service: MobileService get() = this@MobileService }
    private val binder = LocalBinder()
    private lateinit var logs: TraceStore
    private val ownerId = java.util.UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private var hub: NativeDeviceHub? = null
    private var visible = false
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
                event("萃取状态：${current.name}", "shot.state")
            }
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate() { super.onCreate(); logs = (application as MobileApplication).logs; handler.post(watchShot) }
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
                    snapshot = if (role == DeviceRole.COFFEE) snapshot.copy(coffeeState = state)
                        else snapshot.copy(scaleState = state)
                    event("${role.name}: ${state.name}")
                },
                onCoffee = { frame ->
                    snapshot = when (frame) {
                        is Settings -> snapshot.copy(settings = frame)
                        is io.openhoyi.protocol.IdleTelemetry, is io.openhoyi.protocol.ExtractionTelemetry ->
                            snapshot.copy(coffee = frame, coffeeAt = SystemClock.elapsedRealtime())
                        else -> snapshot
                    }
                },
                onWeight = { snapshot = snapshot.copy(weight = it, weightAt = SystemClock.elapsedRealtime()) },
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
            )
            running = true
            event("服务已启动")
            if (visible) hub?.foreground()
        } catch (error: RuntimeException) {
            event("服务启动失败：${error.javaClass.simpleName}")
            shutdown()
        }
        return START_NOT_STICKY
    }
    fun screenVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) hub?.foreground() else {
            hub?.background()
            snapshot = snapshot.copy(scanning = false)
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
        val current = hub ?: return
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, settings = null)
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
        event("断开 ${role.name}")
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    fun startShot(profileId: String): String? {
        val current = hub ?: return "设备服务尚未启动"
        val selectedId = getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null)
        val profile = CurveCatalog.find(profileId)?.takeIf { it.id == selectedId }
        val blocked = ShotGate.startBlock(profile, snapshot.coffeeState, snapshot.coffee, snapshot.coffeeAt, snapshot.scaleState,
            snapshot.weightAt, SystemClock.elapsedRealtime(), current.extraction.state)
        if (blocked != null) { event("启动被阻止：$blocked", "shot.rejected"); return blocked }
        requireNotNull(profile)
        logs.record("shot.start.attempt", mapOf("ownerId" to ownerId, "curveId" to profile.id,
            "targetHundredthsGram" to profile.targetHundredthsGram.toString()))
        if (!current.extraction.start(profile.parameters, profile.targetHundredthsGram, 0) ||
            current.extraction.state == ExtractionState.IDLE) {
            event("启动未被会话层接受", "shot.rejected")
            return "启动未被会话层接受"
        }
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
        hub?.close(); hub = null; running = false
        snapshot = snapshot.copy(coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED, scanning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { handler.removeCallbacks(watchShot); hub?.close(); hub = null; super.onDestroy() }
    companion object { const val STOP = "io.openhoyi.mobile.STOP"; const val TAG = "OpenHoyiMobile"; private const val CHANNEL = "connections" }
}
