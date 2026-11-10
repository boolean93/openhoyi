package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ByteFrame
import io.openhoyi.session.SleepNowTracker
import io.openhoyi.session.DeviceState
import io.openhoyi.session.SleepNowTracker.State
import java.io.File
import java.util.Locale

/** Isolated native projection fixtures plus the real Mock Activity shell. No BLE or machine commands. */
internal class ConnectionSleepPresentationChecks(private val test:Instrumentation) {
    fun run():Int {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName=="io.openhoyi.mobile.mock")
        val out=File(test.targetContext.getExternalFilesDir(null),"connection-sleep-presentation").apply {mkdirs()}
        var failure:Throwable?=null
        BoundedInstrumentationMain.run("Connection and sleep native fixtures") {try {
            for(language in listOf("zh","en")) {
                val config=Configuration(test.targetContext.resources.configuration).apply {setLocale(Locale.forLanguageTag(language))}
                val context=test.targetContext.createConfigurationContext(config)
                val body=LinearLayout(context).apply {orientation=LinearLayout.VERTICAL;setBackgroundColor(0xfffaf8f4.toInt());setPadding(24,24,24,24)}
                fun text(resource:Int)=TextView(context).apply {setText(resource);setTextColor(0xff222222.toInt());textSize=16f;body.addView(this)}
                val help=CoffeeRecoveryPresentation().apply {observe(DeviceState.CONNECTING,0)}
                text(requireNotNull(help.hint(DeviceState.SYNCHRONIZING,8000)))
                for(state in listOf(DeviceState.FAILED,DeviceState.CONNECTING)) {
                    val action=Button(context).apply {
                        setText(if(TaskShellDeviceStatus.connecting(state)) R.string.task_shell_action_connecting else R.string.coffee_connection_retry)
                        isEnabled=TaskShellDeviceStatus.canReconnect(state,false)
                    }
                    body.addView(action)
                    check(action.isEnabled==(state==DeviceState.FAILED))
                }
                val cases=listOf(Triple(State.WRITING,0,0),Triple(State.WAITING_ASLEEP,0,0),
                    Triple(State.CONFIRMED,1,1),Triple(State.UNKNOWN,0,0),
                    Triple(State.CONFIRMED,null,1),Triple(State.CONFIRMED,null,0))
                for((state,fresh,last) in cases) {
                    val id=SleepPresentation.status(state,fresh,last)
                    text(id)
                    SleepPresentation.hint(state,fresh,last)?.let {text(it)}
                    val button=Button(context).apply {
                        setText(if(id in setOf(R.string.task_shell_machine_asleep,R.string.sleep_status_last_confirmed))R.string.task_shell_machine_asleep else R.string.home_sleep_button)
                        isEnabled=false
                    }
                    body.addView(button)
                    check(!button.isEnabled)
                    if(state in setOf(State.WRITING,State.WAITING_ASLEEP,State.UNKNOWN))check(id!=R.string.task_shell_machine_awake)
                    if(last==0 && fresh==null)check(id!=R.string.sleep_status_last_confirmed)
                }
                val width=(360*context.resources.displayMetrics.density).toInt()
                body.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                body.layout(0,0,width,body.measuredHeight)
                val image=Bitmap.createBitmap(width,body.measuredHeight,Bitmap.Config.ARGB_8888)
                try {body.draw(Canvas(image));File(out,"$language.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
            }
        }catch(error:Throwable){failure=error}}
        failure?.let {throw it}
        test.sendStatus(0,android.os.Bundle().apply {putString("stream","CONNECTION_SLEEP_STAGE actual-host-launch\n")})
        val host=MainPageTestHost.launch(test,timeoutMs=5_000)
        try {
            val deadline=android.os.SystemClock.elapsedRealtime()+5_000
            var ready=false
            while(!ready && android.os.SystemClock.elapsedRealtime()<deadline) {
                val remaining=(deadline-android.os.SystemClock.elapsedRealtime()).coerceAtLeast(1)
                ready=BoundedInstrumentationMain.run("Mock service readiness",remaining) {host.activity.currentService?.running==true}
                if(!ready)android.os.SystemClock.sleep(50)
            }
            check(ready) {"Mock service did not become ready within 5000ms"}
            test.sendStatus(0,android.os.Bundle().apply {putString("stream","CONNECTION_SLEEP_STAGE actual-shell-projection\n")})
            BoundedInstrumentationMain.run("Actual Mock shell projection") {try {
                val shell=MainActivity::class.java.getDeclaredField("shellStatus").apply {isAccessible=true}.get(host.activity) as TaskShellStatusView
                val original=TaskShellStatusView::class.java.getDeclaredField("mode").apply {isAccessible=true}.get(shell) as TaskNavigationMode
                try {
                    for(mode in listOf(TaskNavigationMode.BOTTOM,TaskNavigationMode.RAIL,TaskNavigationMode.BOTTOM)) {
                        shell.renderMode(mode)
                        val hint=requireNotNull(shell.findViewWithTag<TextView>("task-shell-coffee-hint"))
                        val retry=requireNotNull(shell.findViewWithTag<Button>("task-shell-coffee-retry"))
                        check(hint.isAttachedToWindow && retry.isAttachedToWindow)
                        hint.setText(R.string.coffee_connection_recovery_hint);hint.visibility=View.VISIBLE
                        retry.visibility=View.VISIBLE
                        val needed=shell.requiredRailHeight((180*host.activity.resources.displayMetrics.density).toInt())
                        check(needed>hint.minimumHeight)
                    }
                }finally {shell.renderMode(original);shell.present(host.activity.currentService)}
                val owner=requireNotNull(host.activity.currentService)
                val snapshotField=MobileService::class.java.getDeclaredField("snapshot").apply {isAccessible=true}
                val tracker=MobileService::class.java.getDeclaredField("sleepNow").apply {isAccessible=true}.get(owner) as SleepNowTracker
                val stateField=SleepNowTracker::class.java.getDeclaredField("state").apply {isAccessible=true}
                val help=MobileService::class.java.getDeclaredField("coffeeRecoveryPresentation").apply {isAccessible=true}.get(owner) as CoffeeRecoveryPresentation
                val helpStart=CoffeeRecoveryPresentation::class.java.getDeclaredField("startedAt").apply {isAccessible=true}
                val helpFailures=CoffeeRecoveryPresentation::class.java.getDeclaredField("failures").apply {isAccessible=true}
                val savedSnapshot=owner.snapshot;val savedState=tracker.state
                val savedStart=helpStart.get(help);val savedFailures=helpFailures.get(help)
                val fragment=host.active("root:BREW")
                val render=fragment.javaClass.getDeclaredMethod("render").apply {isAccessible=true}
                fun capture(name:String){
                    render.invoke(fragment);shell.present(owner)
                    val decor=host.activity.window.decorView
                    decor.measure(View.MeasureSpec.makeMeasureSpec(decor.width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(decor.height,View.MeasureSpec.EXACTLY))
                    decor.layout(0,0,decor.measuredWidth,decor.measuredHeight)
                    val image=Bitmap.createBitmap(decor.width,decor.height,Bitmap.Config.ARGB_8888)
                    try {decor.draw(Canvas(image));File(out,"actual-$name.png").outputStream().use {image.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {image.recycle()}
                }
                try {
                    help.observe(DeviceState.FAILED,0);help.observe(DeviceState.FAILED,1)
                    stateField.set(tracker,State.IDLE)
                    snapshotField.set(owner,savedSnapshot.copy(coffeeState=DeviceState.FAILED,coffee=null,coffeeAt=null))
                    capture("failed-help")
                    check(shell.findViewWithTag<Button>("task-shell-coffee-retry").isShown)
                    check(shell.findViewWithTag<TextView>("task-shell-coffee-hint").isShown)
                    val idle=IdleTelemetry(9300,12000,0,0,1,0,0,0,ByteFrame(byteArrayOf()))
                    stateField.set(tracker,State.CONFIRMED)
                    snapshotField.set(owner,savedSnapshot.copy(coffeeState=DeviceState.READY,coffee=idle,coffeeAt=android.os.SystemClock.elapsedRealtime()-2_000))
                    capture("sleep-last-confirmed")
                    check(shell.findViewWithTag<TextView>("task-shell-coffee-status").text.toString().contains(host.activity.getString(R.string.sleep_status_last_confirmed)))
                    check(shell.findViewWithTag<TextView>("task-shell-coffee-hint").isShown)
                }finally {
                    snapshotField.set(owner,savedSnapshot);stateField.set(tracker,savedState)
                    helpStart.set(help,savedStart);helpFailures.set(help,savedFailures)
                    render.invoke(fragment);shell.present(owner)
                }
            }catch(error:Throwable){failure=error}}
            failure?.let {throw it}
        }finally {BoundedInstrumentationMain.run("Connection fixture Activity cleanup") {host.activity.finish()}}
        return 12
    }
}
