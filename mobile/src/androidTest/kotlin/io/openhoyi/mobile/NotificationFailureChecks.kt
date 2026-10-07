package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Handler
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.concurrent.TimeUnit

/** Detached Mock objects only: no onCreate, onStart, foreground registration or BLE owner. */
internal class NotificationFailureChecks(private val test: Instrumentation) {
    private fun field(name: String) = MobileService::class.java.getDeclaredField(name).apply { isAccessible = true }
    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val names = listOf("shot_safety", "machine_write_safety")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val fixtureNames = names.associateWith { "notification_failure_$it" }
        check(fixtureNames.values.all { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.isEmpty() })
        val folder = File(app.cacheDir, "notification-failure-${java.util.UUID.randomUUID()}")
        val journal = TraceStore(folder)
        var lookups = 0
        val context = object : ContextWrapper(app) {
            override fun getSystemService(name: String): Any? {
                check(name != BLUETOOTH_SERVICE) { "Notification failure test may not access Bluetooth" }
                if (name == NOTIFICATION_SERVICE) { lookups++; throw SecurityException("Intentional notification lookup failure") }
                return super.getSystemService(name)
            }
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                app.getSharedPreferences(requireNotNull(fixtureNames[name]), mode)
            override fun startService(intent: Intent) = error("No component dispatch")
            override fun startForegroundService(intent: Intent) = error("No component dispatch")
            override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch")
            override fun startActivity(intent: Intent) = error("No component dispatch")
            override fun startActivity(intent: Intent, options: android.os.Bundle?) = error("No component dispatch")
            override fun stopService(intent: Intent) = error("No component dispatch")
        }
        var failure: Throwable? = null
        try {
            check(app.getSharedPreferences(fixtureNames.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                .putBoolean("unresolved_shot", true).putString("unresolved_shot_address", "AA:BB:CC:DD:EE:01").commit())
            check(app.getSharedPreferences(fixtureNames.getValue("machine_write_safety"), Context.MODE_PRIVATE).edit()
                .putString("pending_kind", "SETTING").putString("pending_address", "AA:BB:CC:DD:EE:01").commit())
            test.runOnMainSync {
                var subject: MobileService? = null
                try {
                    val instance = MobileService()
                    subject = instance
                    ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                        .apply { isAccessible = true }.invoke(instance, context)
                    Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(instance, app)
                    field("logs").set(instance, journal)
                    // Disable only this detached object's projection, never a registered Mock owner.
                    field("mock").set(instance, null)
                    check(field("hub").get(instance) == null)
                    field("running").setBoolean(instance, true)
                    field("manualSafetyResource").set(instance, R.string.machine_recovery_shot_restart)
                    val before = instance.snapshot
                    val controlBlock = instance.machineControlSafetyMessage
                    check(controlBlock != null)
                    val prefsBefore = fixtureNames.values.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
                    MobileService::class.java.getDeclaredMethod("refreshSafetyNotification", Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(instance, false)
                    check(lookups == 1)
                    check(field("safetyMessage").get(instance) == null) {
                        "Failed notification lookup must not mark the warning as delivered"
                    }
                    // A later state watcher must retry the same warning, without touching a BLE owner.
                    MobileService::class.java.getDeclaredMethod("refreshSafetyNotification", Boolean::class.javaPrimitiveType)
                        .apply { isAccessible = true }.invoke(instance, false)
                    check(lookups == 2) { "Identical warning was suppressed after failed notification delivery" }
                    check(field("safetyMessage").get(instance) == null)
                    check(instance.snapshot.copy(message = before.message) == before) { "Display failure changed control state" }
                    check(instance.snapshot.messageForDisplay { id, args -> instance.getString(id, *args) } == instance.getString(R.string.notification_update_error))
                    check(field("hub").get(instance) == null && instance.running)
                    check(instance.machineControlSafetyMessage == controlBlock)
                    prefsBefore.forEach { (name, value) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == value) }
                    val beforeRenderFailure = instance.snapshot
                    val cachedWarning = field("safetyMessage").get(instance)
                    try {
                        // A synthetic invalid display resource must not escape into the state watcher.
                        field("manualSafetyResource").set(instance, 0)
                        MobileService::class.java.getDeclaredMethod("refreshSafetyNotification", Boolean::class.javaPrimitiveType)
                            .apply { isAccessible = true }.invoke(instance, false)
                        check(lookups == 2 && instance.snapshot === beforeRenderFailure)
                        check(field("safetyMessage").get(instance) == cachedWarning)
                    } finally { field("manualSafetyResource").set(instance, R.string.machine_recovery_shot_restart) }
                    // The error-reporting path itself must tolerate unavailable display text.
                    MobileService::class.java.getDeclaredMethod("notificationFailure", Boolean::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType, String::class.java, Throwable::class.java)
                        .apply { isAccessible = true }.invoke(instance, false, 0, "notification.fixture_error",
                            SecurityException("Intentional notification failure"))
                    check(instance.snapshot === beforeRenderFailure && instance.machineControlSafetyMessage == controlBlock)
                    prefsBefore.forEach { (name, value) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == value) }
                    // Exercise the actual destruction method with independent callbacks and no owner.
                    val handler = field("handler").get(instance) as Handler
                    val callbacks = listOf("watchShot", "mockTick", "leaveForeground", "stopAutomaticScale")
                        .map { field(it).get(instance) as Runnable }
                    callbacks.forEach { handler.postDelayed(it, 60_000); check(handler.hasCallbacks(it)) }
                    instance.onDestroy()
                    check(lookups == 3)
                    check(callbacks.none(handler::hasCallbacks)) { "Notification failure interrupted callback cleanup" }
                    check(field("hub").get(instance) == null)
                    check(instance.machineControlSafetyMessage == controlBlock)
                    prefsBefore.forEach { (name, value) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == value) }
                } catch (error: Throwable) { failure = error }
                finally { subject?.let { (field("handler").get(it) as Handler).removeCallbacksAndMessages(null) } }
            }
            failure?.let { throw it }
            original.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
        } finally {
            journal.close()
            check(journal.awaitTermination(5, TimeUnit.SECONDS)) { "Fixture journal did not drain" }
            folder.deleteRecursively()
            fixtureNames.values.forEach { check(app.deleteSharedPreferences(it)) }
        }
    }
}
