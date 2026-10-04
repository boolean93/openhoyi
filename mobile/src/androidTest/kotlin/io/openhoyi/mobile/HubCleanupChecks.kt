package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub
import io.openhoyi.session.*
import java.time.LocalDateTime

/** Detached hub, fake transports, same-main-task close: no scanning, BLE, service or preference writes. */
internal class HubCleanupChecks(private val test: Instrumentation) {
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
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    private fun assertStopped(subject: Any) {
        check(field(subject, "closed").getBoolean(subject)) { "Device owner was not closed" }
        val handler = field(subject, "handler").get(subject) as Handler
        val ticker = field(subject, "ticker").get(subject) as Runnable
        check(!handler.hasCallbacks(ticker)) { "Device owner retained a ticker" }
    }
    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        val context = object : ContextWrapper(test.targetContext.applicationContext) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? = error("Detached hub may not access system services: $name")
            override fun checkSelfPermission(permission: String): Int = error("Detached hub may not access Bluetooth permissions")
            override fun getSharedPreferences(name: String, mode: Int) = error("Detached hub may not write preferences")
        }
        for (failures in listOf(emptySet(), setOf(DeviceRole.COFFEE), setOf(DeviceRole.BOOKOO), setOf(DeviceRole.COFFEE, DeviceRole.BOOKOO))) {
            var mainFailure: Throwable? = null
            test.runOnMainSync {
                try {
                    var armed = false
                    val coffeeError = IllegalStateException("Intentional coffee observer failure")
                    val scaleError = IllegalStateException("Intentional scale observer failure")
                    val hub = NativeDeviceHub(context, onState = { role, state ->
                        if (armed && state == DeviceState.DISCONNECTED && role in failures)
                            throw if (role == DeviceRole.COFFEE) coffeeError else scaleError
                    })
                    try {
                        val coffee = field(hub, "coffee").get(hub) as AndroidDevice
                        val scale = field(hub, "scale").get(hub) as AndroidDevice
                        val drivers = listOf(Driver(), Driver())
                        for ((device, driver) in listOf(coffee, scale).zip(drivers)) {
                            val queue = field(device.session, "queue").get(device.session) as GattQueue
                            // Replace only this detached session's transport before any connection.
                            check(!queue.active)
                            field(queue, "driver").set(queue, driver)
                        }
                        coffee.session.connect("fixture-coffee", CoffeeAuthentication(LocalDateTime.of(2026, 10, 4, 0, 0), "123456"))
                        scale.session.connect("fixture-scale")
                        check(drivers.all { it.executes == 1 && it.closes == 0 })
                        armed = true
                        val failure = runCatching { hub.close() }.exceptionOrNull()
                        assertStopped(hub)
                        assertStopped(coffee)
                        assertStopped(scale)
                        check(drivers.all { it.closes == 1 && it.executes == 1 }) { "Both fake GATT owners must close without new operations" }
                        check(coffee.session.state == DeviceState.DISCONNECTED && scale.session.state == DeviceState.DISCONNECTED)
                        when {
                            failures.isEmpty() -> check(failure == null)
                            DeviceRole.COFFEE in failures -> {
                                check(failure === coffeeError)
                                check(failure.suppressed.toList() == if (DeviceRole.BOOKOO in failures) listOf(scaleError) else emptyList<Throwable>())
                            }
                            else -> check(failure === scaleError)
                        }
                    } finally {
                        armed = false
                        // RED must also release every detached task before leaving this main-looper turn.
                        runCatching { (field(hub, "coffee").get(hub) as AndroidDevice).close() }
                        runCatching { (field(hub, "scale").get(hub) as AndroidDevice).close() }
                        runCatching { hub.scanner.close() }
                        val handler = field(hub, "handler").get(hub) as Handler
                        handler.removeCallbacks(field(hub, "ticker").get(hub) as Runnable)
                    }
                } catch (error: Throwable) { mainFailure = error }
            }
            mainFailure?.let { throw it }
        }
    }
}
