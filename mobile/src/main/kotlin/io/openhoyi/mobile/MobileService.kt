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
    private var hub: NativeDeviceHub? = null
    private var visible = false
    var running = false; private set
    var snapshot = MobileSnapshot(); private set

    override fun onCreate() { super.onCreate(); logs = (application as MobileApplication).logs }
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
        val current = hub ?: return
        snapshot = snapshot.copy(coffee = null, coffeeAt = null, settings = null)
        event("连接咖啡机")
        current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password))
    }
    fun connectScale(address: String) {
        val current = hub ?: return
        snapshot = snapshot.copy(weight = null, weightAt = null)
        event("连接电子秤")
        current.connectScale(address)
    }
    fun disconnect(role: DeviceRole) {
        event("断开 ${role.name}")
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    private fun event(message: String) {
        snapshot = snapshot.copy(message = message)
        logs.record("mobile.event", mapOf("message" to message, "ownerId" to ownerId))
        Log.i(TAG, message)
    }
    fun shutdown() {
        hub?.close(); hub = null; running = false
        snapshot = snapshot.copy(coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED, scanning = false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { hub?.close(); hub = null; super.onDestroy() }
    companion object { const val STOP = "io.openhoyi.mobile.STOP"; const val TAG = "OpenHoyiMobile"; private const val CHANNEL = "connections" }
}
