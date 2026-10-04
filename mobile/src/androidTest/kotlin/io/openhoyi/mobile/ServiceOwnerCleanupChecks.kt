package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.session.*
import io.openhoyi.trace.TraceStore
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Detached service and hub only; fake Connect operations, no component registration or BLE. */
internal class ServiceOwnerCleanupChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private class Driver : GattDriver {
        var closes = 0
        var executes = 0
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
        val id = UUID.randomUUID().toString()
        val fixtures = names.associateWith { "service_owner_failure_${id}_$it" }
        val folder = File(app.cacheDir, "service-owner-failure-$id")
        val journal = TraceStore(folder)
        val context = object : ContextWrapper(app) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name == NOTIFICATION_SERVICE) throw SecurityException("Intentional detached notification failure")
                error("Detached owner may not access system services: $name")
            }
            override fun checkSelfPermission(permission: String): Int = error("No BLE permissions")
            override fun getSharedPreferences(name: String, mode: Int) =
                app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
            override fun startService(intent: Intent) = error("No component dispatch")
            override fun startForegroundService(intent: Intent) = error("No component dispatch")
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch")
            override fun startActivity(intent: Intent) = error("No component dispatch")
            override fun startActivity(intent: Intent, options: Bundle?) = error("No component dispatch")
            override fun stopService(intent: Intent) = error("No component dispatch")
        }
        try {
            check(app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                .putBoolean("unresolved_shot", true).putString("unresolved_shot_address", "AA:BB:CC:DD:EE:01").commit())
            check(app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE).edit()
                .putString("pending_kind", "SETTING").putString("pending_address", "AA:BB:CC:DD:EE:01").commit())
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
                    Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(instance, app)
                    field(instance, "logs").set(instance, journal)
                    field(instance, "mock").set(instance, null)
                    field(instance, "running").setBoolean(instance, true)
                    field(instance, "hubForeground").setBoolean(instance, true)
                    val controlBlock = instance.machineControlSafetyMessage
                    check(controlBlock != null)
                    val owner = NativeDeviceHub(context, onState = { role, state ->
                        if (armed && role == DeviceRole.COFFEE && state == DeviceState.DISCONNECTED)
                            throw IllegalStateException("Intentional detached owner close failure")
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
                    // Reject off-owner calls before detaching a still-active hub.
                    val threadFailure = AtomicReference<Throwable?>()
                    val wrongOwner = Thread {
                        threadFailure.set(runCatching {
                            MobileService::class.java.getDeclaredMethod("closeDeviceOwner")
                                .apply { isAccessible = true }.invoke(instance)
                        }.exceptionOrNull())
                    }
                    wrongOwner.start()
                    wrongOwner.join(2_000)
                    check(!wrongOwner.isAlive) { "Wrong-owner probe did not finish" }
                    check((threadFailure.get() as? InvocationTargetException)?.cause is IllegalStateException)
                    check(field(instance, "hub").get(instance) === owner && field(instance, "hubForeground").getBoolean(instance))
                    check(drivers.all { it.executes == 1 && it.closes == 0 })
                    val handler = field(instance, "handler").get(instance) as Handler
                    val callbacks = listOf("watchShot", "mockTick", "leaveForeground", "stopAutomaticScale")
                        .map { field(instance, it).get(instance) as Runnable }
                    callbacks.forEach { handler.postDelayed(it, 60_000); check(handler.hasCallbacks(it)) }
                    armed = true
                    val destroyError = runCatching { instance.onDestroy() }.exceptionOrNull()
                    check(destroyError == null) { "Owner close exception escaped service destruction: ${destroyError?.javaClass?.simpleName}" }
                    check(field(instance, "hub").get(instance) == null && !field(instance, "hubForeground").getBoolean(instance))
                    check(callbacks.none(handler::hasCallbacks))
                    devices.forEach { device ->
                        check(field(device, "closed").getBoolean(device))
                        check(!(field(device, "handler").get(device) as Handler).hasCallbacks(field(device, "ticker").get(device) as Runnable))
                    }
                    check(drivers.all { it.executes == 1 && it.closes == 1 })
                    check(instance.machineControlSafetyMessage == controlBlock)
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
