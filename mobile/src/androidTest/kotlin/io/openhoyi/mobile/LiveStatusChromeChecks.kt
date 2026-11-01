package io.openhoyi.mobile

import android.app.AlertDialog
import android.app.Instrumentation
import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import android.widget.Button
import android.widget.TextView
import java.io.File
import org.json.JSONObject

/** Mock-only presentation fixtures; no service snapshot, preparation, history or machine writes. */
internal class LiveStatusChromeChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable?=null
        test.runOnMainSync { try { action() } catch(e:Throwable) { error=e } }
        error?.let { throw it }
    }
    fun run() {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName=="io.openhoyi.mobile.mock")
        val host=MainPageTestHost.launch(test,extraction=true)
        val fragment=host.active("extraction") as ExtractionFragment
        fun field(name:String):Any?=fragment.javaClass.getDeclaredField(name).apply { isAccessible=true }.get(fragment)
        fun invoke(name:String)=fragment.javaClass.getDeclaredMethod(name).apply { isAccessible=true }.invoke(fragment)
        fun present(notices:List<ExtractionFragment.StatusNotice>)=fragment.javaClass.getDeclaredMethod("presentStatusNotices",List::class.java).apply { isAccessible=true }.invoke(fragment,notices)
        val directory=File(test.targetContext.getExternalFilesDir(null),"live-status-chrome-checks").apply { mkdirs() }
        fun capture(name:String) {
            test.waitForIdleSync()
            val bitmap=checkNotNull(test.uiAutomation.takeScreenshot())
            try { File(directory,"$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } }
            finally { bitmap.recycle() }
        }
        lateinit var handler:Handler; lateinit var refresh:Runnable
        var paused=false
        var failure:Throwable?=null
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+5000
            var normal=false
            while(android.os.SystemClock.elapsedRealtime()<deadline && !normal) {
                main { invoke("render"); normal=(field("service") as? MobileService)?.running==true && (field("statusNotices") as List<*>).isEmpty() }
                if(!normal) android.os.SystemClock.sleep(50)
            }
            check(normal) { "Actual status did not become normal; keep and report the real problems" }
            main {
                handler=field("handler") as Handler; refresh=field("refresh") as Runnable; handler.removeCallbacks(refresh); paused=true
                invoke("render")
                listOf("readiness","preparationStatus","notificationStatus","alarmStatus").forEach { check((field(it) as View).visibility==View.GONE) }
            }
            capture("actual-main-normal")
            val notices=listOf(
                ExtractionFragment.StatusNotice(ExtractionStatusPresentation.Signal(ExtractionStatusPresentation.Kind.GUIDANCE,ExtractionStatusPresentation.Severity.CRITICAL,"fixture-unknown"),test.targetContext.getString(R.string.extraction_unknown_guidance)),
                ExtractionFragment.StatusNotice(ExtractionStatusPresentation.Signal(ExtractionStatusPresentation.Kind.ALARM,ExtractionStatusPresentation.Severity.CRITICAL,"fixture-alarm"),MachineAlarms(test.targetContext).describe(1,1000,1000)),
                ExtractionFragment.StatusNotice(ExtractionStatusPresentation.Signal(ExtractionStatusPresentation.Kind.NOTIFICATION,ExtractionStatusPresentation.Severity.ADVICE),test.targetContext.getString(R.string.extraction_notification_warning)),
            )
            var originalToolWidth=0; var originalToolHeight=0
            main {
                val tools=field("chartToolsButton") as Button; originalToolWidth=tools.width; originalToolHeight=tools.height
                present(notices)
                check(tools.contentDescription.contains("3"))
                check(tools.width==originalToolWidth && tools.height==originalToolHeight && tools.height>=HoyiUi.dp(host.activity,48))
            }
            capture("actual-main-synthetic-critical-badge")
            repeat(2) { reopen ->
                main {
                    invoke("showTools") // Original menu still computes its real enabled actions.
                    present(notices) // Only presentation list is synthetic; service and gates stay untouched.
                    val menu=field("toolsDialog") as AlertDialog
                    val entry=checkNotNull(menu.window!!.decorView.findViewWithTag<Button>("product/extraction/tool/status"))
                    check(entry.performClick())
                }
                test.waitForIdleSync()
                main {
                    val dialog=field("statusDetailsDialog") as AlertDialog
                    val text=dialog.window!!.decorView.findViewWithTag<TextView>("product/extraction/status-details")
                    check(notices.all { text.text.contains(it.detail) })
                    check(dialog.window!!.decorView.findViewWithTag<View>("app-dialog-body") is android.widget.ScrollView)
                }
                capture("synthetic-critical-details-$reopen")
                main { (field("statusDetailsDialog") as AlertDialog).dismiss() }
            }
            main { present(emptyList()); check((field("chartToolsButton") as Button).contentDescription.toString()==test.targetContext.getString(R.string.task_tools)) }
            File(directory,"scope.json").writeText(JSONObject().put("actualMainWindow",true).put("criticalStatesSyntheticPresentation",true).put("serviceAndBusinessStoresModified",false).put("reopenCount",2).put("toolWidthPx",originalToolWidth).put("toolHeightPx",originalToolHeight).put("narrowPortraitNotYetMeasured",true).toString())
        } catch(error:Throwable) { failure=error; throw error } finally {
            var cleanupFailure:Throwable?=null
            fun cleanup(action:()->Unit) {
                try { action() } catch(error:Throwable) {
                    val primary=failure ?: cleanupFailure
                    if(primary==null) cleanupFailure=error else if(primary!==error) primary.addSuppressed(error)
                }
            }
            cleanup { main { (field("statusDetailsDialog") as? AlertDialog)?.dismiss() } }
            cleanup { main { (field("toolsDialog") as? AlertDialog)?.dismiss() } }
            cleanup { main { if(fragment.isAdded) invoke("render") } }
            cleanup { main { if(paused) { handler.removeCallbacks(refresh); handler.post(refresh) } } }
            cleanup { main { host.activity.finish() } }
            if(failure==null) cleanupFailure?.let { throw it }
        }
    }
}
