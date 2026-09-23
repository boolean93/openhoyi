package io.openhoyi.lab

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.TextView
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** May scan candidates, but never selects/connects a device or sends a hardware command. */
class LabSmokeInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    private fun launch(): Activity {
        val monitor = addMonitor(LabActivity::class.java.name, null, false)
        try {
            // Launch from the shell like adb; some OEMs block the test process's background launch.
            uiAutomation.executeShellCommand("am start -W -n io.openhoyi.lab/.LabActivity").use { fd ->
                FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
            return monitor.waitForActivityWithTimeout(8000) ?: error("Activity launch timed out")
        } finally { removeMonitor(monitor) }
    }
    override fun onStart() {
        var activity: Activity? = null
        var service: LabService? = null
        var bound = false
        val report = mutableListOf<String>()
        val latch = CountDownLatch(1)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                service = (binder as LabService.LocalBinder).service; latch.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName) { service = null }
        }
        var failure: Throwable? = null
        try {
            activity = launch()
            runOnMainSync {
                val root = activity!!.window.decorView
                check(root.findViewWithTag<TextView>("coffee_data").text.contains("尚无数据"))
                check(root.findViewWithTag<TextView>("scale_data").text.contains("尚无数据"))
                check(!root.findViewWithTag<android.view.View>("disconnect_coffee").isEnabled)
            }
            report += "PASS native UI startup / unknown readings"
            val required = if (Build.VERSION.SDK_INT >= 31) listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                else listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            val mayScan = required.all { targetContext.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
            val enabled = mayScan && targetContext.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true
            if (enabled) {
                runOnMainSync {
                    activity!!.window.decorView.findViewWithTag<android.view.View>("scan").performClick()
                    bound = targetContext.bindService(Intent(targetContext, LabService::class.java), connection, Context.BIND_AUTO_CREATE)
                }
                check(bound && latch.await(8, TimeUnit.SECONDS)) { "Service binding timed out" }
                val original = service ?: error("Missing service")
                runOnMainSync { check(original.running) }
                val monitor = addMonitor(LabActivity::class.java.name, null, false)
                try {
                    runOnMainSync { activity!!.recreate() }
                    activity = monitor.waitForActivityWithTimeout(8000) ?: error("Activity recreation timed out")
                } finally { removeMonitor(monitor) }
                runOnMainSync { check(service === original && original.running) }
                report += "PASS Activity recreation preserves Service instance"
                uiAutomation.executeShellCommand("input keyevent KEYCODE_HOME").close()
                SystemClock.sleep(800)
                runOnMainSync { check(original.running); activity!!.finish() }
                activity = launch()
                runOnMainSync { check(service === original && original.running); original.shutdown() }
                report += "PASS background/return preserves Service; explicit shutdown stops it"
            } else report += "SKIP Service lifecycle: BLE permissions or adapter unavailable"
            val app = targetContext.applicationContext as LabApplication
            val output = ByteArrayOutputStream()
            val exported = CountDownLatch(1)
            var exportFailure: Throwable? = null
            app.logs.record("test.marker", mapOf("scenario" to "export-after-service-stop"))
            app.logs.export({ output }) { exportFailure = it; exported.countDown() }
            check(exported.await(8, TimeUnit.SECONDS)) { "Export timed out" }
            check(exportFailure == null) { "Export failed" }
            val files = mutableMapOf<String,String>()
            ZipInputStream(output.toByteArray().inputStream()).use { zip ->
                while (true) { val entry = zip.nextEntry ?: break; files[entry.name] = zip.readBytes().toString(Charsets.UTF_8) }
            }
            check(files.containsKey("metadata.json"))
            check(files.filterKeys { it.endsWith(".jsonl") }.values.any { it.contains("export-after-service-stop") })
            report += "PASS Android ZIP export after Service stop (in-process stream, not SAF picker)"
        } catch (error: Throwable) { failure = error }
        finally {
            runOnMainSync {
                service?.takeIf { it.running }?.shutdown()
                if (bound) targetContext.unbindService(connection)
                activity?.finish()
            }
        }
        failure?.let { report += "FAIL ${it.javaClass.simpleName}: ${it.message}" }
        finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", report.joinToString("\n", postfix="\n")) })
    }
}
