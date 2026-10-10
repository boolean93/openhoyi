package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import io.openhoyi.session.ExtractionState

/** Actual product controls and service, isolated Mock only. Never constructs a Bluetooth hub. */
internal class CustomCurveExecutionChecks(private val test:Instrumentation) {
    private val context get()=test.targetContext
    private val opened=mutableListOf<Activity>()
    private fun main(action:()->Unit) {
        var error:Throwable?=null
        test.runOnMainSync {try {action()} catch(failure:Throwable) {error=failure}}
        error?.let {throw it}
    }
    private fun await(condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+15000
        while(true) {
            var ready=false;main {ready=condition()}
            if(ready)return
            check(SystemClock.elapsedRealtime()<deadline) {"Custom execution UI timed out"}
            Thread.sleep(25)
        }
    }
    private fun views(view:View):List<View> = listOf(view)+if(view is ViewGroup)
        (0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList()
    private fun root(activity:Activity):View=activity.findViewById(android.R.id.content)
    private fun start(type:Class<out Activity>):Activity = test.startActivitySync(
        Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)).also {
        opened+=it;test.waitForIdleSync();await {it.hasWindowFocus() && root(it).isLaidOut}
    }
    private fun click(activity:Activity,label:Int) {
        main {
            val button=views(root(activity)).filterIsInstance<TextView>().single {
                it.isShown && it.isClickable && it.text.toString()==activity.getString(label)
            }
            check(button.isEnabled)
            button.requestRectangleOnScreen(android.graphics.Rect(0,0,button.width,button.height),true)
            check(button.performClick())
        }
        test.waitForIdleSync()
    }
    fun run(disposable:Boolean) {
        check(disposable && BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) {"Physical devices are forbidden"}
        val qemu=test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use {descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText().trim()}
        }
        check(qemu=="1") {"Disposable Android emulator required"}
        val app=context.applicationContext as MobileApplication
        check(app.beanPreparationResult.getOrThrow().current()==null)
        check(app.history.entries.none {it.status in setOf(ShotHistory.Status.STARTING,ShotHistory.Status.RUNNING,
            ShotHistory.Status.STOP_REQUESTED,ShotHistory.Status.UNKNOWN)}) {"Existing unresolved history must not be modified"}
        val originalHistory=app.history.entries
        val originalDrafts=app.customCurvesResult.getOrThrow().list()
        val doc=CustomCurveDocument(CustomCurveDocument.newId(),"Execution UI pressure",93,1800,
            CustomCurveDocument.ControlMode.PRESSURE,listOf(CustomCurveDocument.Stage(30,150),CustomCurveDocument.Stage(90,400)))
        val prefs=context.getSharedPreferences("curves",android.content.Context.MODE_PRIVATE)
        val originalSelection=prefs.getString("selected",null)
        try {
            val home=start(HomeActivity::class.java)
            fun owner():MobileService? = HomeActivity::class.java.getDeclaredField("service").apply {isAccessible=true}.get(home) as? MobileService
            await {owner()?.running==true}
            val service=requireNotNull(owner())
            fun noBle() {check(MobileService::class.java.getDeclaredField("hub").apply {isAccessible=true}.get(service)==null)}
            val import=start(CurveImportActivity::class.java)
            main {views(root(import)).filterIsInstance<EditText>().single().setText(doc.encode())}
            val monitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
            val preview=try {
                click(import,R.string.profiles_import_preview)
                requireNotNull(test.waitForMonitorWithTimeout(monitor,10000)).also {opened+=it}
            } finally {test.removeMonitor(monitor)}
            await {preview.hasWindowFocus()}
            check(app.customCurvesResult.getOrThrow().find(doc.id)==null)
            click(preview,R.string.profiles_import_save)
            await {preview.isDestroyed}
            check(app.customCurvesResult.getOrThrow().find(doc.id)==doc)
            check(prefs.getString("selected",null)==originalSelection && app.history.entries==originalHistory)
            check(service.shotState==ExtractionState.IDLE);noBle()
            val library=start(CurveActivity::class.java)
            main {views(root(library)).filterIsInstance<EditText>().single().setText(doc.name)}
            main {
                val list=views(root(library)).filterIsInstance<ListView>().single()
                check(list.adapter.count==1 && list.adapter.getItem(0)==doc.id)
                check(list.performItemClick(list.getChildAt(0),0,list.adapter.getItemId(0)))
            }
            click(library,R.string.curve_use)
            await {library.isDestroyed}
            check(prefs.getString("selected",null)==doc.id && app.history.entries==originalHistory)
            val extraction=start(ExtractionActivity::class.java)
            await {views(root(extraction)).filterIsInstance<TextView>().any {
                it.text.toString()==extraction.getString(R.string.extraction_start) && it.isEnabled
            }}
            click(extraction,R.string.extraction_start)
            // Confirm the actual dialog's positive action; never invoke service.startShot directly.
            val deadline=SystemClock.elapsedRealtime()+10000
            var confirmed=false
            while(!confirmed) {
                val window=test.uiAutomation.rootInActiveWindow
                val matches=window?.findAccessibilityNodeInfosByText(extraction.getString(R.string.extraction_mock_start)).orEmpty()
                    .filter {it.isEnabled && it.isClickable && it.text?.toString()==extraction.getString(R.string.extraction_mock_start)}
                check(matches.size<=1) {"Ambiguous start confirmation"}
                if(matches.size==1) {check(matches.single().performAction(AccessibilityNodeInfo.ACTION_CLICK));confirmed=true}
                else {check(SystemClock.elapsedRealtime()<deadline);Thread.sleep(25)}
            }
            await {service.shotState==ExtractionState.RUNNING && app.history.entries.any {it.curveId==doc.id && it.observedRunning}}
            noBle()
            check(app.curveUsageResult.getOrThrow().stats(doc.id).count==1L)
            click(extraction,R.string.extraction_stop)
            await {service.shotState==ExtractionState.ENDED_OBSERVED && app.history.entries.any {it.curveId==doc.id && it.status==ShotHistory.Status.ENDED}}
            val shot=app.history.entries.single {it.curveId==doc.id}
            check(shot.slot==7 && shot.observedRunning && shot.endedAtMs!=null)
            check(app.history.entries.filter {it.curveId!=doc.id}==originalHistory)
            check(app.customCurvesResult.getOrThrow().list().filter {it.id!=doc.id}==originalDrafts)
            check(app.journalResult.getOrThrow().entries().any {it.observation.id==shot.id && it.observation.curveId==doc.id})
            noBle()
        } finally {
            // CI-owned completed records remain as evidence; never clear safety or unknown records.
            main {opened.asReversed().filter {!it.isDestroyed}.forEach {it.finish()}}
        }
    }
}
