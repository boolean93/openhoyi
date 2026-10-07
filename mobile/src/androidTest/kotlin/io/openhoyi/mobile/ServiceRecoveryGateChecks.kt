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
    private data class Fixture(val shotPending: Boolean, val kind: MachineWriteRecoveryState.Kind?,
        val shotAddress: String? = if (shotPending) "AA:BB:CC:DD:EE:01" else null,
        val writeAddress: String? = if (kind != null) "AA:BB:CC:DD:EE:01" else null,
        val expectedShotPending: Boolean = shotPending,
        val expectedKind: MachineWriteRecoveryState.Kind? = kind)
    private fun field(subject: Any, name: String) = subject.javaClass.getDeclaredField(name).apply { isAccessible = true }
    fun run() {
        val app = test.targetContext.applicationContext as MobileApplication
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val names = listOf("shot_safety", "machine_write_safety")
        val original = names.associateWith { app.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val cases = MachineWriteRecoveryState.Kind.entries.flatMap { listOf(Fixture(false, it), Fixture(true, it)) } +
            listOf(Fixture(false, null), Fixture(true, null)) +
            listOf("AA:BB:CC:DD:EE:01", "invalid").flatMap { address -> listOf(
                Fixture(false, null, shotAddress = address, expectedShotPending = true),
                Fixture(false, null, writeAddress = address, expectedKind = MachineWriteRecoveryState.Kind.UNKNOWN)) }
        check(cases.size == 18)
        val schedule = WeeklySleepSchedule(List(7) { WeeklySleepDay(false, SleepDay(22, 0, 7, 0)) })
        for (fixture in cases) {
            val id = UUID.randomUUID().toString()
            val fixtures = names.associateWith { "service_recovery_${id}_$it" }
            val folder = File(app.cacheDir, "service-recovery-$id")
            val journal = TraceStore(folder)
            var shotPreferenceReads = 0
            val context = object : ContextWrapper(app) {
                override fun getNoBackupFilesDir():File=File(folder,"no-backup")
                override fun getApplicationContext(): Context = this
                override fun getSystemService(name: String): Any? = error("No system services: $name")
                override fun checkSelfPermission(permission: String): Int = error("No BLE permission access")
                override fun getSharedPreferences(name: String, mode: Int): android.content.SharedPreferences {
                    if (name == "shot_safety") shotPreferenceReads++
                    return app.getSharedPreferences(requireNotNull(fixtures[name]), mode)
                }
                override fun startService(intent: Intent) = error("No component dispatch")
                override fun startForegroundService(intent: Intent) = error("No component dispatch")
                override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch")
                override fun startActivity(intent: Intent) = error("No component dispatch")
                override fun startActivity(intent: Intent, options: Bundle?) = error("No component dispatch")
                override fun stopService(intent: Intent) = error("No component dispatch")
            }
            try {
                check(app.getSharedPreferences(fixtures.getValue("shot_safety"), Context.MODE_PRIVATE).edit()
                    .putBoolean("unresolved_shot", fixture.shotPending)
                    .putString("unresolved_shot_address", fixture.shotAddress).commit())
                check(app.getSharedPreferences(fixtures.getValue("machine_write_safety"), Context.MODE_PRIVATE).edit()
                    .putString("pending_kind", fixture.kind?.name)
                    .putString("pending_address", fixture.writeAddress).commit())
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
                        check(shotPreferenceReads == 0)
                        val brewWait = fixture.expectedKind == MachineWriteRecoveryState.Kind.BREW_WAIT
                        val knownKind = fixture.expectedKind != null && fixture.expectedKind != MachineWriteRecoveryState.Kind.UNKNOWN
                        check(instance.machineWriteAcknowledgementAvailable ==
                            (knownKind && (!brewWait || !fixture.expectedShotPending)))
                        check(shotPreferenceReads == if (brewWait) 1 else 0)
                        val warning = instance.machineControlSafetyMessage
                        val writeWarning = instance.machineWriteSafetyMessage
                        if (fixture.expectedKind == null) check(writeWarning == null)
                        else check(writeWarning != null)
                        if (fixture.expectedKind == MachineWriteRecoveryState.Kind.UNKNOWN)
                            check(writeWarning == instance.getString(R.string.machine_recovery_unknown))
                        if (fixture.expectedShotPending)
                            check(warning == instance.getString(R.string.machine_recovery_shot_restart))
                        else check(warning == writeWarning)
                        val results = listOf(
                            instance.changeMachineSetting(MachineSettingChange.BrewTemperature(93)),
                            instance.resetCupCount(25),
                            instance.changeSleepSchedule(schedule, schedule),
                            instance.enterSleepNow(),
                            instance.prepareBrew("fixture", false, 7),
                            instance.startShot("fixture", false, 7))
                        if (!fixture.expectedShotPending && fixture.expectedKind == null) {
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
