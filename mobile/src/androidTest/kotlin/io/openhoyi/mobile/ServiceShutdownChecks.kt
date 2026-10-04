package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Bundle
import android.os.Handler
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Actual shutdown method, detached objects and a local ActivityManager proxy; no platform dispatch. */
internal class ServiceShutdownChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun frameworkField(name: String) = Service::class.java.getDeclaredField(name).apply { isAccessible = true }
    private class Driver : GattDriver {
        var executes = 0
        var closes = 0
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            check(operation is GattOperation.Connect)
            executes++
            return true
        }
        override fun close(generation: Long) { closes++ }
    }
    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val names = listOf("shot_safety", "machine_write_safety")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        for ((shotPending, writePending) in listOf(false to false, true to false, false to true, true to true)) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_shutdown_${id}_$it" }
            val folder = File(app.cacheDir, "service-shutdown-$id")
            val journal = TraceStore(folder)
            val context = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? {
                    if (name == NOTIFICATION_SERVICE) throw SecurityException("Intentional detached notification failure")
                    error("No system services: $name")
                }
                override fun checkSelfPermission(permission: String): Int = error("No BLE permissions")
                override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
                override fun startService(intent: Intent) = error("No component dispatch")
                override fun startForegroundService(intent: Intent) = error("No component dispatch")
                override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch")
                override fun startActivity(intent: Intent) = error("No component dispatch")
                override fun startActivity(intent: Intent, options: Bundle?) = error("No component dispatch")
                override fun stopService(intent: Intent) = error("No component dispatch")
            }
            try {
                check(app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                    .putBoolean("unresolved_shot", shotPending).putString("unresolved_shot_address", if (shotPending) "AA:BB:CC:DD:EE:01" else null).commit())
                check(app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE).edit()
                    .putString("pending_kind", if (writePending) "SETTING" else null).putString("pending_address", if (writePending) "AA:BB:CC:DD:EE:01" else null).commit())
                val before = fixtures.values.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
                var failure: Throwable? = null
                test.runOnMainSync {
                    var service: MobileService? = null
                    var hub: NativeDeviceHub? = null
                    var armed = false
                    try {
                        val instance = MobileService()
                        service = instance
                        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                            .apply { isAccessible = true }.invoke(instance, context)
                        frameworkField("mApplication").set(instance, app)
                        field(instance, "logs").set(instance, journal)
                        field(instance, "mock").set(instance, null)
                        field(instance, "running").setBoolean(instance, true)
                        field(instance, "hubForeground").setBoolean(instance, true)
                        field(instance, "snapshot").set(instance, MobileSnapshot(coffeeState = DeviceState.READY, scaleState = DeviceState.READY))
                        val token = Binder()
                        val expectedComponent = ComponentName(app.packageName, MobileService::class.java.name)
                        val calls = mutableListOf<String>()
                        val managerField = frameworkField("mActivityManager")
                        val manager = Proxy.newProxyInstance(managerField.type.classLoader, arrayOf(managerField.type)) { _, method, args ->
                            val values = requireNotNull(args)
                            check(values[0] == expectedComponent && values[1] === token)
                            check(field(instance, "hub").get(instance) == null && !instance.running)
                            when (method.name) {
                                "setServiceForeground" -> {
                                    check(values.size == 6 && values[2] == 0 && values[3] == null && values[4] == Service.STOP_FOREGROUND_REMOVE && values[5] == 0)
                                    calls += method.name
                                    null
                                }
                                "stopServiceToken" -> {
                                    check(values.size == 3 && values[2] == -1)
                                    calls += method.name
                                    true
                                }
                                else -> error("No platform ActivityManager calls: ${method.name}")
                            }
                        }
                        managerField.set(instance, manager)
                        check(managerField.get(instance) === manager)
                        frameworkField("mClassName").set(instance, MobileService::class.java.name)
                        frameworkField("mToken").set(instance, token)
                        val owner = NativeDeviceHub(context, onState = { role, state ->
                            if (armed && role == DeviceRole.COFFEE && state == DeviceState.DISCONNECTED)
                                throw IllegalStateException("Intentional detached shutdown observer failure")
                        })
                        hub = owner
                        val devices = listOf("coffee", "scale").map { field(owner, it).get(owner) as AndroidDevice }
                        val drivers = listOf(Driver(), Driver())
                        devices.zip(drivers).forEach { (device, driver) ->
                            val queue = field(device.session, "queue").get(device.session) as GattQueue
                            check(!queue.active)
                            field(queue, "driver").set(queue, driver)
                        }
                        devices[0].session.connect("fixture-coffee", CoffeeAuthentication(LocalDateTime.of(2026, 10, 4, 0, 0), "123456"))
                        devices[1].session.connect("fixture-scale")
                        field(instance, "hub").set(instance, owner)
                        val handler = field(instance, "handler").get(instance) as Handler
                        val callbacks = listOf("leaveForeground", "stopAutomaticScale").map { field(instance, it).get(instance) as Runnable }
                        callbacks.forEach { handler.postDelayed(it, 60_000); check(handler.hasCallbacks(it)) }
                        armed = true
                        instance.shutdown()
                        if (shotPending || writePending) {
                            check(calls.isEmpty() && instance.running && field(instance, "hub").get(instance) === owner)
                            check(field(instance, "hubForeground").getBoolean(instance))
                            check(callbacks.all(handler::hasCallbacks))
                            check(drivers.all { it.closes == 0 && it.executes == 1 })
                        } else {
                            check(calls == listOf("setServiceForeground", "stopServiceToken"))
                            check(!instance.running && field(instance, "hub").get(instance) == null && !field(instance, "hubForeground").getBoolean(instance))
                            check(callbacks.none(handler::hasCallbacks))
                            check(drivers.all { it.closes == 1 && it.executes == 1 })
                            check(instance.snapshot.coffeeState == DeviceState.DISCONNECTED && instance.snapshot.scaleState == DeviceState.DISCONNECTED)
                        }
                        before.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
                    } catch (error: Throwable) { failure = error }
                    finally {
                        armed = false
                        hub?.let { owner ->
                            listOf("coffee", "scale").forEach { name -> runCatching { (field(owner, name).get(owner) as AndroidDevice).close() } }
                            runCatching { owner.scanner.close() }
                            (field(owner, "handler").get(owner) as Handler).removeCallbacks(field(owner, "ticker").get(owner) as Runnable)
                        }
                        service?.let { (field(it, "handler").get(it) as Handler).removeCallbacksAndMessages(null) }
                    }
                }
                failure?.let { throw it }
                original.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
            } finally {
                journal.close()
                check(journal.awaitTermination(5, TimeUnit.SECONDS))
                folder.deleteRecursively()
                fixtures.values.forEach { check(app.deleteSharedPreferences(it)) }
            }
        }
    }
}
