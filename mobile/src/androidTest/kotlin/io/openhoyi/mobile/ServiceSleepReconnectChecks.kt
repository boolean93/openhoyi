package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Detached service and native session; every transport is replaced before a connection is opened. */
internal class ServiceSleepReconnectChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }

    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val names = listOf("shot_safety", "machine_write_safety", "curves", "devices", "coffee_credentials", "coffee_credential_failures")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val id = UUID.randomUUID().toString()
        val fixtures = names.associateWith { "service_sleep_reconnect_${id}_$it" }
        val folder = File(app.cacheDir, "service-sleep-reconnect-$id")
        val journal = TraceStore(folder)
        var forbiddenCalls = 0
        fun forbidden(): Nothing { forbiddenCalls++; error("No real BLE, system service or component access permitted") }
        val context = object : ContextWrapper(app) {
            override fun getNoBackupFilesDir() = File(folder, "no-backup")
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
            override fun getSystemService(name: String): Any? = forbidden()
            override fun checkSelfPermission(permission: String): Int = forbidden()
            override fun startService(intent: Intent): android.content.ComponentName? = forbidden()
            override fun startForegroundService(intent: Intent): android.content.ComponentName? = forbidden()
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int): Boolean = forbidden()
            override fun startActivity(intent: Intent) = forbidden()
            override fun startActivity(intent: Intent, options: Bundle?) = forbidden()
            override fun stopService(intent: Intent): Boolean = forbidden()
        }
        try {
            val address = "AA:BB:CC:DD:EE:01"
            check(CoffeeCredentialStore(context).save(address, "123456"))
            var failure: Throwable? = null
            test.runOnMainSync {
                val service = MobileService()
                var hub: NativeDeviceHub? = null
                val auto = app.autoConnect
                val autoPrefs = field(auto, "prefs")
                val autoSession = field(auto, "session")
                val oldPrefs = autoPrefs.get(auto)
                val oldSession = autoSession.get(auto)
                try {
                    autoPrefs.set(auto, context.getSharedPreferences("devices", Context.MODE_PRIVATE))
                    autoSession.set(auto, DeviceUseSession(DeviceUseSession.Saved(1,
                        DeviceUseSession.Stamp(0, 0, 0), coffeePaused = true, scalePaused = false)))
                    ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                        .apply { isAccessible = true }.invoke(service, context)
                    Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(service, app)
                    field(service, "logs").set(service, journal)
                    field(service, "mock").set(service, null)
                    field(service, "running").setBoolean(service, true)
                    val callback = MobileService::class.java.getDeclaredMethod("onDeviceState", DeviceRole::class.java, DeviceState::class.java)
                        .apply { isAccessible = true }
                    val owner = NativeDeviceHub(context, legacyVerifiedStartFrames = app.curves.legacyVerifiedStartFrames,
                        onState = { role, state -> callback.invoke(service, role, state) })
                    hub = owner
                    field(service, "hub").set(service, owner)
                    val coffee = hubDevice(owner, "coffee").session
                    val calls = mutableListOf<GattOperation>()
                    listOf("coffee", "scale").forEach { name ->
                        val queue = field(hubDevice(owner, name).session, "queue").get(hubDevice(owner, name).session) as GattQueue
                        check(!queue.active)
                        val driver = object : GattDriver {
                            override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
                                check(name == "coffee") { "No scale dispatch permitted" }
                                calls += operation
                                return true // Deliberately leave connect pending to exercise a second click.
                            }
                            override fun close(generation: Long) = Unit
                        }
                        field(queue, "driver").set(queue, GuardedGattDriver(if (name == "coffee") DeviceRole.COFFEE else DeviceRole.BOOKOO, driver))
                    }
                    val queue = field(coffee, "queue").get(coffee) as GattQueue
                    queue.open()
                    field(coffee, "state").set(coffee, DeviceState.READY)
                    field(coffee, "activeAddress").set(coffee, address)
                    val stamp = MobileMonotonicClock.elapsedRealtime()
                    val asleep = IdleTelemetry(8000, 12000, 0, 0, 1, 0, 0, 0, ByteFrame(byteArrayOf()))
                    field(coffee, "lastIdle").set(coffee, asleep)
                    field(coffee, "lastIdleAtMs").set(coffee, stamp)
                    field(service, "snapshot").set(service, MobileSnapshot(coffeeState = DeviceState.READY, coffee = asleep, coffeeAt = stamp))
                    check(service.canReconnectAfterSleep)
                    check(service.reconnectAfterSleep(address) == RememberedCoffeeConnection.STARTED)
                    check(service.snapshot.coffeeState == DeviceState.CONNECTING && service.snapshot.coffee == null)
                    check(!service.canReconnectAfterSleep)
                    check(service.reconnectAfterSleep(address) == RememberedCoffeeConnection.BLOCKED)
                    check(calls == listOf(GattOperation.Connect(address))) { "Repeated click dispatched extra transport operations" }
                    check(calls.none { it is GattOperation.Write }) { "Recovery must not dispatch wake/start writes" }
                    check(coffee.state == DeviceState.CONNECTING && queue.inFlight)
                    check(forbiddenCalls == 0)
                } catch (error: Throwable) { failure = error }
                finally {
                    try {
                        hub?.let { owner ->
                            listOf("coffee", "scale").forEach { name -> runCatching { hubDevice(owner, name).close() } }
                            runCatching { owner.scanner.close() }
                            (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable)
                        }
                        (field(service, "handler").get(service) as Handler).removeCallbacksAndMessages(null)
                    } finally {
                        autoPrefs.set(auto, oldPrefs)
                        autoSession.set(auto, oldSession)
                        check(autoPrefs.get(auto) === oldPrefs && autoSession.get(auto) === oldSession)
                    }
                }
            }
            check(forbiddenCalls == 0)
            failure?.let { throw it }
        } finally {
            journal.close()
            check(journal.awaitTermination(5, TimeUnit.SECONDS))
            check(folder.deleteRecursively())
            fixtures.values.forEach { app.deleteSharedPreferences(it) }
            original.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
        }
    }
}
