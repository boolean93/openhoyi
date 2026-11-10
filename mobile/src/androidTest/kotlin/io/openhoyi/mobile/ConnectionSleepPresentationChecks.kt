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
import io.openhoyi.session.CoffeeSessionFailure
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
                text(DeviceStatusText.coffeeResource(DeviceState.SYNCHRONIZING,null))
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
            BoundedInstrumentationMain.run("Actual Mock shell projection",10_000) {try {
                val shell=MainActivity::class.java.getDeclaredField("shellStatus").apply {isAccessible=true}.get(host.activity) as TaskShellStatusView
                val original=TaskShellStatusView::class.java.getDeclaredField("mode").apply {isAccessible=true}.get(shell) as TaskNavigationMode
                try {
                    for(mode in listOf(TaskNavigationMode.BOTTOM,TaskNavigationMode.RAIL,TaskNavigationMode.BOTTOM)) {
                        shell.renderMode(mode)
                        val hint=requireNotNull(shell.findViewWithTag<TextView>("task-shell-coffee-hint"))
                        val retry=requireNotNull(shell.findViewWithTag<Button>("task-shell-coffee-retry"))
                        check(hint.isAttachedToWindow && retry.isAttachedToWindow)
                        hint.setText(R.string.task_shell_sleep_wake);hint.visibility=View.VISIBLE
                        retry.visibility=View.VISIBLE
                        val needed=shell.requiredRailHeight(HoyiUi.dp(host.activity,104))
                        check(needed>hint.minimumHeight)
                    }
                }finally {shell.renderMode(original);shell.present(host.activity.currentService)}
                val owner=requireNotNull(host.activity.currentService)
                val snapshotField=MobileService::class.java.getDeclaredField("snapshot").apply {isAccessible=true}
                val tracker=MobileService::class.java.getDeclaredField("sleepNow").apply {isAccessible=true}.get(owner) as SleepNowTracker
                val stateField=SleepNowTracker::class.java.getDeclaredField("state").apply {isAccessible=true}
                val savedSnapshot=owner.snapshot;val savedState=tracker.state
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
                    val connectionChecks=org.json.JSONArray()
                    val devices=host.activity.getSharedPreferences("devices",android.content.Context.MODE_PRIVATE)
                    for((name,state,failure) in listOf(Triple("failed",DeviceState.FAILED,null),
                        Triple("sync-timeout",DeviceState.FAILED,CoffeeSessionFailure.PROTOCOL_TIMEOUT),
                        Triple("auth-interrupted",DeviceState.FAILED,CoffeeSessionFailure.AUTH),
                        Triple("synchronizing",DeviceState.SYNCHRONIZING,null),
                        Triple("disconnected",DeviceState.DISCONNECTED,null),
                        Triple("unsupported",DeviceState.UNSUPPORTED,null))) {
                        stateField.set(tracker,State.IDLE)
                        snapshotField.set(owner,savedSnapshot.copy(coffeeState=state,coffeeFailure=failure,coffee=null,coffeeAt=null,
                            scaleState=when(name){"failed"->DeviceState.FAILED;"synchronizing"->DeviceState.CONNECTING;else->savedSnapshot.scaleState}))
                        capture("connection-$name")
                        val label=host.activity.getString(DeviceStatusText.coffeeResource(state,failure))
                        val header=host.viewField<TextView>(fragment,"status").text.toString()
                        val status=shell.findViewWithTag<TextView>("task-shell-coffee-status").text.toString().removePrefix("● ")
                        val hint=shell.findViewWithTag<TextView>("task-shell-coffee-hint")
                        val retry=shell.findViewWithTag<Button>("task-shell-coffee-retry")
                        val headerAction=host.viewField<Button>(fragment,"machineConnect").text.toString()
                        check(header==host.activity.getString(R.string.home_machine_title)+" · "+label) {"Header contains extra process text: $header"}
                        check(status==label && !hint.isShown) {"Connection hint remained on the actual rail"}
                        check(retry.isShown && retry.layout.lineCount==1)
                        check(!retry.contentDescription.toString().contains('\uFFFC'))
                        val homeRetry=host.viewField<Button>(fragment,"machineConnect")
                        fun iconSpans(button:Button)=(button.text as? android.text.Spanned)
                            ?.getSpans(0,button.text.length,android.text.style.ReplacementSpan::class.java)?.toList().orEmpty()
                        if(name=="failed") {
                            val scaleRetry=shell.findViewWithTag<Button>("task-shell-scale-reconnect")
                            val scaleRemembered=io.openhoyi.session.ScaleSelectionPolicy.remembered(
                                devices.getString("scale",null),devices.getString("scaleProtocol",null))
                                ?.takeIf {android.bluetooth.BluetoothAdapter.checkBluetoothAddress(it.address)}!=null
                            val scaleCanRetry=scaleRemembered && host.activity.missingBle().isEmpty()
                            val scaleAction=host.activity.getString(if(scaleCanRetry)R.string.task_shell_action_retry else R.string.task_shell_action_connect)
                            check(scaleRetry.text.toString().removePrefix("\uFFFC")==scaleAction) {"Scale action must follow its actual permissions and memory"}
                            if(scaleCanRetry)check(retry.text.toString()==scaleRetry.text.toString())
                            check(iconSpans(retry).size==1 && iconSpans(scaleRetry).size==1)
                            val first=iconSpans(retry).single()
                            repeat(3){shell.present(owner)}
                            check(iconSpans(retry).size==1 && iconSpans(retry).single()===first) {"Retry refresh duplicated its icon"}
                            check(retry.text.count {it=='\uFFFC'}==1)
                            check(TaskShellStatusView::class.java.getDeclaredField("coffeeRetryIcon").apply {isAccessible=true}.get(shell)=="retry")
                            check(TaskShellStatusView::class.java.getDeclaredField("reconnectIcon").apply {isAccessible=true}.get(shell)==if(scaleCanRetry)"retry" else "new")
                            check(homeRetry.compoundDrawablesRelative[0]!=null && homeRetry.layout.lineCount==1)
                            var coffeeClicks=0;var scaleClicks=0
                            val probe=TaskShellStatusView(host.activity,{}, {}, {scaleClicks++}, {}, {coffeeClicks++})
                            probe.present(owner)
                            probe.findViewWithTag<Button>("task-shell-coffee-retry").performClick()
                            check(coffeeClicks==1 && scaleClicks==0)
                            probe.findViewWithTag<Button>("task-shell-scale-reconnect").performClick()
                            check(coffeeClicks==1 && scaleClicks==1)
                        }
                        if(name=="synchronizing") {
                            val scaleRetry=shell.findViewWithTag<Button>("task-shell-scale-reconnect")
                            check(iconSpans(retry).isEmpty() && !retry.text.contains('\uFFFC') && !retry.isEnabled)
                            check(iconSpans(scaleRetry).isEmpty() && !scaleRetry.text.contains('\uFFFC') && !scaleRetry.isEnabled)
                            check(homeRetry.compoundDrawablesRelative.all {it==null} && !homeRetry.isEnabled)
                        }
                        if(host.activity.resources.configuration.locales[0].language=="zh") {
                            check(label.length<=4 && retry.text.length<=4 && headerAction.length<=4)
                        }
                        connectionChecks.put(org.json.JSONObject().put("case",name).put("header",header).put("status",status)
                            .put("button",retry.text.toString()).put("headerButton",headerAction).put("hintVisible",hint.isShown)
                            .put("scaleAction",shell.findViewWithTag<Button>("task-shell-scale-reconnect").text.toString().removePrefix("\uFFFC"))
                            .put("mockBlePermissionMissing",host.activity.missingBle().isNotEmpty()))
                    }
                    File(out,"compact-connection-evidence.json").writeText(org.json.JSONObject()
                        .put("railWidthDp",104).put("cases",connectionChecks).toString(2))
                    val checks=org.json.JSONArray()
                    val cases=listOf(Triple("sending",State.WRITING,0),Triple("waiting",State.WAITING_ASLEEP,0),
                        Triple("unknown",State.UNKNOWN,0),Triple("asleep",State.CONFIRMED,1),
                        Triple("last-confirmed",State.CONFIRMED,1),Triple("standby",State.IDLE,0))
                    val mock=MobileService::class.java.getDeclaredField("mock").apply {isAccessible=true}.get(owner)
                    val mockSleep=mock.javaClass.getDeclaredField("sleeping").apply {isAccessible=true}
                    val savedMockSleep=mockSleep.get(mock)
                    try {
                        mockSleep.set(mock,false)
                        for((name,state,raw) in cases){
                            val idle=IdleTelemetry(9300,12000,0,0,raw,0,0,0,ByteFrame(byteArrayOf()))
                            stateField.set(tracker,state)
                            snapshotField.set(owner,savedSnapshot.copy(coffeeState=DeviceState.READY,coffee=idle,
                                coffeeAt=android.os.SystemClock.elapsedRealtime()-if(name=="last-confirmed")2_000 else 0))
                            capture("sleep-$name")
                            check(shell.findViewWithTag<Button>("task-shell-coffee-retry").visibility==View.GONE)
                            check(!shell.findViewWithTag<Button>("task-shell-coffee-retry").text.contains('\uFFFC'))
                            val readyAction=host.viewField<Button>(fragment,"machineConnect")
                            check(readyAction.text.toString()==host.activity.getString(R.string.home_visual_change))
                            check(readyAction.compoundDrawablesRelative.all {it==null})
                            val button=shell.findViewWithTag<Button>("task-shell-sleep")
                            val label=button.text.toString()
                            val status=shell.findViewWithTag<TextView>("task-shell-coffee-status").text.toString().removePrefix("● ")
                            val hint=shell.findViewWithTag<TextView>("task-shell-coffee-hint")
                            val expectedWidth=HoyiUi.dp(host.activity,104)
                            check(kotlin.math.abs(shell.width-expectedWidth)<=1) {"Expected actual 104dp rail, got ${shell.width}px"}
                            check(button.isAttachedToWindow && button.isShown)
                            check(!label.contains('\uFFFC')) {"Sleep button retained an icon span"}
                            check(button.layout.lineCount==1 && button.layout.getEllipsisCount(0)==0) {"Sleep button wrapped or truncated: $label"}
                            check(button.layout.getLineWidth(0)<=button.width-button.compoundPaddingLeft-button.compoundPaddingRight+1) {"Sleep label does not fit its actual button"}
                            if(host.activity.resources.configuration.locales[0].language=="zh") {
                                check(label.length<=4 && status.length<=4) {"Sleep rail text exceeded four characters: $label / $status"}
                                if(hint.isShown)check(hint.text.length<=4) {"Sleep hint exceeded four characters"}
                            }
                            if(name=="last-confirmed")check(status==host.activity.getString(R.string.task_shell_sleep_history))
                            checks.put(org.json.JSONObject().put("case",name).put("label",label).put("status",status)
                                .put("hint",if(hint.isShown)hint.text.toString() else "").put("lines",button.layout.lineCount)
                                .put("buttonWidthPx",button.width).put("railWidthPx",shell.width))
                        }
                    }finally {mockSleep.set(mock,savedMockSleep)}
                    File(out,"compact-sleep-evidence.json").writeText(org.json.JSONObject()
                        .put("fontScale",host.activity.resources.configuration.fontScale)
                        .put("railWidthDp",104).put("cases",checks).toString(2))
                }finally {
                    snapshotField.set(owner,savedSnapshot);stateField.set(tracker,savedState)
                    render.invoke(fragment);shell.present(owner)
                }
            }catch(error:Throwable){failure=error}}
            failure?.let {throw it}
        }finally {BoundedInstrumentationMain.run("Connection fixture Activity cleanup") {host.activity.finish()}}
        return 12
    }
}
