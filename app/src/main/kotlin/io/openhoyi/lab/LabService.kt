package io.openhoyi.lab

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.*
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.*
import io.openhoyi.session.*
import java.time.LocalDateTime

/** One owner for both links. Binding/unbinding a screen never owns a GATT connection. */
class LabService : Service() {
    inner class LocalBinder : Binder() { val service: LabService get() = this@LabService }
    private val binder = LocalBinder()
    private lateinit var logs: TraceStore
    private val ownerId = java.util.UUID.randomUUID().toString()
    private var hub: NativeDeviceHub? = null
    private var visible = false
    var running = false; private set
    var snapshot = LabSnapshot(); private set
    val logStatus: String get() = logs.status
    override fun onCreate() { super.onCreate(); logs = (application as LabApplication).logs }
    override fun onBind(intent: Intent): IBinder = binder
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { shutdown(); return START_NOT_STICKY }
        if (!running) {
            try {
                val manager = getSystemService(NotificationManager::class.java)
                manager.createNotificationChannel(NotificationChannel(CHANNEL, "蓝牙设备连接", NotificationManager.IMPORTANCE_LOW))
                val open = PendingIntent.getActivity(this, 0, Intent(this, LabActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val stop = PendingIntent.getService(this, 1, Intent(this, LabService::class.java).setAction(STOP), PendingIntent.FLAG_IMMUTABLE)
                val note = Notification.Builder(this, CHANNEL).setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                    .setContentTitle("OpenHOYI Lab").setContentText("蓝牙诊断服务运行中")
                    .setContentIntent(open).setOngoing(true)
                    .addAction(Notification.Action.Builder(null, "停止连接", stop).build()).build()
                if (Build.VERSION.SDK_INT >= 29) startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
                else startForeground(1, note)
                val prefs = getSharedPreferences("devices", MODE_PRIVATE)
                hub = NativeDeviceHub(applicationContext, prefs.getString("scale", null),
                    onScaleRemembered = { prefs.edit().putString("scale", it).apply() },
                    onState = { role, state ->
                        snapshot = if (role == DeviceRole.COFFEE) snapshot.copy(coffeeState = state) else snapshot.copy(scaleState = state)
                        event("state", "${role.name}: ${state.label()}")
                    },
                    onCoffee = { frame ->
                        snapshot = when (frame) {
                            is Settings -> snapshot.copy(settings = frame)
                            is IdleTelemetry, is ExtractionTelemetry -> snapshot.copy(coffee = frame, coffeeAt = SystemClock.elapsedRealtime())
                            else -> snapshot
                        }
                    },
                    onWeight = { snapshot = snapshot.copy(weight = it, weightAt = SystemClock.elapsedRealtime()) },
                    diagnostic = { event("diagnostic", it) },
                    trace = { role, trace ->
                        logs.record("wire.${trace.kind}", buildMap {
                            put("ownerId", ownerId); put("role", role.name); put("generation", trace.generation.toString())
                            trace.token?.let { put("token", it.toString()) }
                            trace.endpoint?.let { put("endpoint", it) }
                            trace.hex?.let { put("hex", it) }
                            trace.size?.let { put("size", it.toString()) }
                            trace.detail?.let { put("detail", it) }
                        })
                    })
                running = true
                event("service", "服务已启动；等待手动连接咖啡机")
                if (visible) hub?.foreground()
            } catch (e: RuntimeException) {
                event("error", "服务启动失败：${e.javaClass.simpleName}")
                shutdown()
            }
        }
        return START_NOT_STICKY
    }
    fun screenVisible(value: Boolean) {
        if (visible == value) return
        visible = value
        if (value) hub?.foreground() else { hub?.background(); snapshot = snapshot.copy(scanning = false) }
    }
    fun scan() {
        if (snapshot.scanning) return
        val current = hub ?: return
        event("ui.scan", "扫描 HOYI / BOOKOO，持续 5 秒")
        snapshot = snapshot.copy(scanning = true, devices = emptyList())
        current.scanner.start(onDevice = { device ->
            val others = snapshot.devices.filterNot { it.address == device.address }
            snapshot = snapshot.copy(devices = (others + device).sortedBy { it.address }.take(64))
        }, onFinished = { error ->
            snapshot = snapshot.copy(scanning = false)
            event("scan.finished", error ?: "扫描结束：${snapshot.devices.size} 个候选设备")
        })
    }
    fun connect(role: DeviceRole, address: String, password: String? = null) {
        val current = hub ?: return
        event("ui.connect", "连接 ${role.name}")
        if (role == DeviceRole.COFFEE) {
            require(password != null && password.matches(Regex("[0-9]{6}")))
            snapshot = snapshot.copy(coffee = null, coffeeAt = null, settings = null)
            current.connectCoffee(address, CoffeeAuthentication(LocalDateTime.now(), password))
        } else {
            snapshot = snapshot.copy(weight = null, weightAt = null)
            current.connectScale(address)
        }
    }
    fun disconnect(role: DeviceRole) {
        event("ui.disconnect", "手动断开 ${role.name}")
        if (role == DeviceRole.COFFEE) hub?.disconnectCoffee() else hub?.disconnectScale()
    }
    private fun event(kind: String, text: String) {
        logs.record(kind, mapOf("message" to text, "ownerId" to ownerId))
        snapshot = snapshot.copy(events = (snapshot.events + text).takeLast(30))
    }
    fun shutdown() {
        event("service", "服务停止；断开所有连接")
        hub?.close(); hub = null; running = false
        snapshot = snapshot.copy(scanning = false, coffeeState = DeviceState.DISCONNECTED, scaleState = DeviceState.DISCONNECTED)
        stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
    }
    override fun onDestroy() {
        hub?.close(); hub = null; super.onDestroy()
    }
    companion object { const val STOP = "io.openhoyi.lab.STOP"; private const val CHANNEL = "ble" }
}
