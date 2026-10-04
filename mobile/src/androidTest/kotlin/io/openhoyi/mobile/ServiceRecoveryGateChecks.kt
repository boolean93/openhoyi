package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Service
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import io.openhoyi.protocol.SleepDay
import io.openhoyi.protocol.WeeklySleepDay
import io.openhoyi.protocol.WeeklySleepSchedule
import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.trace.TraceStore
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Real Service entry gates with detached UUID preferences; no owner, BLE or component dispatch. */
internal class ServiceRecoveryGateChecks(private val test: Instrumentation) {
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val names = listOf("shot_safety", "machine_write_safety")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val cases = MachineWriteRecoveryState.Kind.entries.flatMap { listOf(false to it, true to it) } + (false to null)
        val schedule = WeeklySleepSchedule(List(7) { WeeklySleepDay(false, SleepDay(22, 0, 7, 0)) })
        for ((shotPending, kind) in cases) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_recovery_${id}_$it" }
            val folder = File(app.cacheDir, "service-recovery-$id")
            val journal = TraceStore(folder)
            val context = object : ContextWrapper(app) {
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? = error("No system services: $name")
                override fun checkSelfPermission(permission: String): Int = error("No BLE permission access")
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
                    .putBoolean("unresolved_shot", shotPending)
                    .putString("unresolved_shot_address", if (shotPending) "AA:BB:CC:DD:EE:01" else null).commit())
                check(app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE).edit()
                    .putString("pending_kind", kind?.name)
                    .putString("pending_address", if (kind != null) "AA:BB:CC:DD:EE:01" else null).commit())
                val before = fixtures.values.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
                var failure: Throwable? = null
                test.runOnMainSync {
                    var service: MobileService? = null
                    try {
                        val instance = MobileService()
                        service = instance
                        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                            .apply { isAccessible = true }.invoke(instance, context)
                        Service::class.java.getDeclaredField("mApplication").apply { isAccessible = true }.set(instance, app)
                        field(instance, "logs").set(instance, journal)
                        field(instance, "mock").set(instance, null)
                        check(field(instance, "hub").get(instance) == null)
                        val warning = instance.machineControlSafetyMessage
                        val writeWarning = instance.machineWriteSafetyMessage
                        if (kind == null) check(warning == null) else check(warning != null && writeWarning != null)
                        val results = listOf(
                            instance.changeMachineSetting(MachineSettingChange.BrewTemperature(93)),
                            instance.resetCupCount(25),
                            instance.changeSleepSchedule(schedule, schedule),
                            instance.enterSleepNow(),
                            instance.prepareBrew("fixture", false, 7),
                            instance.startShot("fixture", false, 7))
                        if (kind == null) {
                            check(results.all { it == instance.getString(R.string.service_unavailable) })
                        } else {
                            // startShot may prioritize a machine-write warning over a concurrent shot warning.
                            check(results.take(5).all { it == warning })
                            check(results.last() != null && results.last() in setOf(warning, writeWarning))
                        }
                        instance.acknowledgeManualSafety()
                        check(instance.machineControlSafetyMessage == warning)
                        check(field(instance, "hub").get(instance) == null && field(instance, "mock").get(instance) == null)
                        before.forEach { (name, values) -> check(app.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
                    } catch (error: Throwable) { failure = error }
                    finally { service?.let { (field(it, "handler").get(it) as Handler).removeCallbacksAndMessages(null) } }
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
