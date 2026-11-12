package io.openhoyi.mobile

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.view.View
import android.widget.Button
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/** A fixed isolated runner, never targets the user's main/Mock packages and never grants install permission. */
class UpdateAcceptanceInstrumentation : Instrumentation() {
    private val host="io.openhoyi.mobile.updatefixture"
    private val installerTarget="io.openhoyi.updatefixture"
    private val current=AtomicReference<UpdateAcceptanceActivity?>()
    private val captures=JSONArray()
    private lateinit var output:File
    private fun main(action:()->Unit){var failure:Throwable?=null;runOnMainSync{try{action()}catch(e:Throwable){failure=e}};failure?.let{throw it}}
    private fun await(label:String,condition:()->Boolean){
        val until=SystemClock.elapsedRealtime()+15000
        while(true){var ready=false;main{ready=condition()};if(ready)return;check(SystemClock.elapsedRealtime()<until){"Update acceptance timeout: $label"};SystemClock.sleep(30)}
    }
    override fun onCreate(arguments:Bundle?){super.onCreate(arguments);start()}
    override fun onStart(){
        val result=Bundle();var activity:UpdateAcceptanceActivity?=null
        val app=targetContext.applicationContext as MobileApplication
        val callbacks=object:Application.ActivityLifecycleCallbacks{
            override fun onActivityResumed(value:Activity){if(value is UpdateAcceptanceActivity)current.set(value)}
            override fun onActivityDestroyed(value:Activity){if(current.get()===value)current.set(null)}
            override fun onActivityCreated(a:Activity,b:Bundle?)=Unit
            override fun onActivityStarted(a:Activity)=Unit
            override fun onActivityStopped(a:Activity)=Unit
            override fun onActivityPaused(a:Activity)=Unit
            override fun onActivitySaveInstanceState(a:Activity,b:Bundle)=Unit
        }
        try {
            check(BuildConfig.MOCK_MODE&&targetContext.packageName==host)
            @Suppress("DEPRECATION")
            val requested=targetContext.packageManager.getPackageInfo(host,android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
            check(requested.none{it.contains("BLUETOOTH")||it.contains("LOCATION")})
            check(host !in setOf("io.openhoyi.mobile","io.openhoyi.mobile.mock"))
            app.registerActivityLifecycleCallbacks(callbacks)
            output=File(targetContext.getExternalFilesDir(null),"update-acceptance").apply{check(isDirectory||mkdirs())}
            val profile=listOf("portrait" to false,"landscape" to true,"portrait" to true,"landscape" to false)
            for((index,pair) in profile.withIndex()){
                val (orientation,dark)=pair
                main{targetContext.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).commit()}
                startActivitySync(Intent(targetContext,UpdateAcceptanceActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("orientation",orientation))
                await("profile $orientation/$dark"){
                    val a=current.get();a!=null&&!a.isFinishing&&a.resources.configuration.orientation==
                        (if(orientation=="portrait")Configuration.ORIENTATION_PORTRAIT else Configuration.ORIENTATION_LANDSCAPE)&&
                        (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)==dark
                }
                activity=current.get()!!
                val a=activity
                main{a.showHint()};capture(a,"$orientation-${if(dark)"dark" else "light"}-fixture-B")
                main{val hint=a.javaClass.getDeclaredField("hint").apply{isAccessible=true}.get(a)!!
                    val shell=hint.javaClass.getDeclaredField("shell").apply{isAccessible=true}.get(hint) as View
                    (shell.findViewWithTag<View>("product/update/prompt/open") as Button).performClick()}
                await("real byte progress"){a.fixture.downloads.state.received>0&&a.fixture.downloads.state.phase==ReleaseDownloadController.Phase.DOWNLOADING}
                main{check(a.fixture.bytesOnDisk()==a.fixture.downloads.state.received)}
                capture(a,"$orientation-${if(dark)"dark" else "light"}-fixture-downloading")
                main{
                    val popup=a.javaClass.getDeclaredField("transfer").apply{isAccessible=true}.get(a)!!
                    val button=popup.javaClass.getDeclaredField("button").apply{isAccessible=true}.get(popup) as Button
                    button.performClick()
                }
                await("cancel"){a.fixture.downloads.state.phase==ReleaseDownloadController.Phase.INTERRUPTED&&a.fixture.bytesOnDisk()==0L}
                if(index==1)capture(a,"landscape-dark-fixture-download-cancelled")
                if(index==0){
                    main{a.fixture.discard();a.fixture.check=UpdateAcceptanceActions.Check.HOLD;a.fixture.metadata.manualCheck()}
                    await("checking"){a.fixture.metadata.state.checking};capture(a,"portrait-light-fixture-settings-checking")
                    main{
                        a.fixture.check=UpdateAcceptanceActions.Check.CURRENT;a.fixture.answer()
                        check(a.fixture.metadata.state.update?.versionCode==BuildConfig.VERSION_CODE.toLong())
                        check(a.fixture.metadata.state.update?.versionName==BuildConfig.VERSION_NAME)
                    }
                    capture(a,"portrait-light-fixture-settings-current")
                    main{
                        val toggle=a.findViewById<View>(android.R.id.content).findViewWithTag<android.widget.Switch>("product/settings/app/update/automatic")
                        check(toggle is HoyiSwitch);check(toggle.isChecked);toggle.performClick();check(!a.fixture.metadata.enabled)
                        a.fixture.check=UpdateAcceptanceActions.Check.AVAILABLE;a.fixture.metadata.manualCheck();a.fixture.answer()
                    }
                    capture(a,"portrait-light-fixture-settings-available")
                    main{
                        val toggle=a.findViewById<View>(android.R.id.content).findViewWithTag<android.widget.Switch>("product/settings/app/update/automatic")
                        toggle.performClick();check(a.fixture.metadata.enabled)
                    }
                    main{a.fixture.check=UpdateAcceptanceActions.Check.FAIL;a.fixture.metadata.manualCheck();a.fixture.answer()}
                    capture(a,"portrait-light-fixture-settings-check-failed")
                    main{a.fixture.failTransfer=true;a.fixture.download(a.fixture.target);a.showTransfer()}
                    await("retry progress"){a.fixture.downloads.state.received>0};main{a.fixture.release()}
                    await("download failure"){a.fixture.downloads.state.phase==ReleaseDownloadController.Phase.FAILED}
                    capture(a,"portrait-light-fixture-download-failed")
                    main{a.fixture.failTransfer=false;a.fixture.download(a.fixture.target);a.fixture.release()}
                    await("verified signed bytes"){a.fixture.downloads.state.phase==ReleaseDownloadController.Phase.READY}
                    runtimeChecks(a)
                    main{a.closeWindows()}
                }
                main{a.finish()};await("profile finished"){current.get()==null}
                activity=null
            }
            File(output,"manifest.json").writeText(JSONObject().put("hostPackage",host).put("installerTarget",installerTarget)
                .put("sdk",Build.VERSION.SDK_INT).put("captures",captures).put("source","actual Android product UI components")
                .put("controlledInputs","synthetic installed identity and metadata; signed local APK stream; no public-network eligibility claim")
                .put("noBle",true).put("noUserDataRead",true).toString(2))
            result.putString("stream","UPDATE_ACCEPTANCE_SCOPED_PASSED runtimeSignature=true screenshots=${captures.length()} noInstall=true noPermissionChanges=true\n")
            finish(Activity.RESULT_OK,result)
        }catch(error:Throwable){
            result.putString("stream","UPDATE_ACCEPTANCE_FAILED ${error.javaClass.simpleName}: ${error.message}\n${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED,result)
        }finally{main{activity?.closeWindows();activity?.finish()};app.unregisterActivityLifecycleCallbacks(callbacks)}
    }
    private fun capture(activity:Activity,name:String){
        val drawn=java.util.concurrent.CountDownLatch(1)
        main{activity.findViewById<View>(android.R.id.content).postOnAnimation {
            activity.findViewById<View>(android.R.id.content).postOnAnimation {drawn.countDown()}
        }}
        check(drawn.await(5,java.util.concurrent.TimeUnit.SECONDS)){"The requested UI state did not draw"}
        val rect=Rect();main{
            val content=activity.findViewById<View>(android.R.id.content)
            val at=IntArray(2);content.getLocationOnScreen(at);rect.set(at[0],at[1],at[0]+content.width,at[1]+content.height)
        }
        check(uiAutomation.rootInActiveWindow?.packageName?.toString()==host){"Refusing a foreign-window screenshot"}
        val raw=requireNotNull(uiAutomation.takeScreenshot())
        try{
            check(rect.left>=0&&rect.top>=0&&rect.right<=raw.width&&rect.bottom<=raw.height&&!rect.isEmpty)
            val cropped=Bitmap.createBitmap(raw,rect.left,rect.top,rect.width(),rect.height())
            try{File(output,"$name.png").outputStream().use{check(cropped.compress(Bitmap.CompressFormat.PNG,100,it))}}
            finally{if(cropped!==raw)cropped.recycle()}
        }finally{raw.recycle()}
        captures.put(JSONObject().put("file","$name.png").put("orientation",if(activity.resources.configuration.orientation==Configuration.ORIENTATION_PORTRAIT)"portrait"else"landscape")
            .put("theme",if(activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK==Configuration.UI_MODE_NIGHT_YES)"dark"else"light")
            .put("crop",JSONArray(listOf(rect.left,rect.top,rect.right,rect.bottom))).put("fixture",true)
            .put("metadataMode",(activity as UpdateAcceptanceActivity).fixture.check.name)
            .put("downloadPhase",activity.fixture.downloads.state.phase.name).put("bytesReceived",activity.fixture.downloads.state.received)
            .put("totalBytes",activity.fixture.target.apkSize).put("automaticCheck",activity.fixture.metadata.enabled)
            .put("controlledInputs","synthetic installed identity and metadata; actual local signed-APK byte stream, no public-network claim"))
    }
    private fun runtimeChecks(activity:UpdateAcceptanceActivity){
        val state=activity.fixture.downloads.state;val file=requireNotNull(state.file)
        activity.fixture.verifyFixture(file)
        // The real production identity gate must still reject this isolated host and APK.
        check(installedUpdateIdentity(activity)?.packageName==host)
        check(releaseUpdateStatus(installedUpdateIdentity(activity),activity.fixture.target)!=ReleaseUpdateStatus.AVAILABLE)
        var rejected=false;try{ReleaseApkVerifier(activity).verify(file,activity.fixture.target)}catch(_:Exception){rejected=true};check(rejected)
        val uri=androidx.core.content.FileProvider.getUriForFile(activity,host+".updates",file)
        check(uri.scheme=="content"&&uri.authority==host+".updates"&&uri.path!!.startsWith("/verified_updates/"))
        val before=releaseApkSha256(file)
        activity.contentResolver.openInputStream(uri)!!.use{check(java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes())
            .joinToString(""){byte->"%02x".format(byte.toInt()and 255)}==before)}
        var stagingRejected=false
        try{androidx.core.content.FileProvider.getUriForFile(activity,host+".updates",File(activity.filesDir,"update-staging/not-shareable.part"))}
        catch(_:IllegalArgumentException){stagingRejected=true};check(stagingRejected)
        val bridge=ReleaseInstallerBridge(activity)
        val permitted=bridge.permitted()
        val installIntent=bridge.intentFor(file)
        check(installIntent.data==uri&&installIntent.component!=null)
        check(installIntent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
        check(installIntent.flags and (Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)==0)
        val permissionIntent=bridge.permissionIntent()
        val component=requireNotNull(permissionIntent.component)
        @Suppress("DEPRECATION")
        val info=activity.packageManager.getActivityInfo(component,0)
        check(info.applicationInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0)
        val report=JSONObject().put("sdk",Build.VERSION.SDK_INT).put("actualCryptoVerified",true).put("actualArchivePackage",installerTarget)
            .put("productionEligibilityRejected",true).put("uri",uri.toString()).put("stagingUriRejected",true)
            .put("permissionAlreadyGranted",permitted).put("permissionComponent",component.flattenToString())
            .put("installerComponent",installIntent.component!!.flattenToString()).put("installerUriReadOnly",true)
            .put("installerConfirmationVerified",false).put("installationPerformed",false).put("permissionPolicyChanged",false)
        if(!permitted){
            var refused=false;main{try{bridge.install(activity,file)}catch(_:IllegalArgumentException){refused=true}};check(refused)
            main{activity.startActivity(permissionIntent)}
            val until=SystemClock.elapsedRealtime()+10000
            while(uiAutomation.rootInActiveWindow?.packageName?.toString()!=component.packageName&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40)
            check(uiAutomation.rootInActiveWindow?.packageName?.toString()==component.packageName)
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            await("permission return without changing policy"){current.get()===activity&&uiAutomation.rootInActiveWindow?.packageName?.toString()==host}
            check(!bridge.permitted());report.put("permissionPageRoundTrip",true).put("unauthorizedInstallerRejected",true)
        }else{
            // This branch is an explicit isolated test action; it never grants permission or clicks Install.
            main{bridge.install(activity,file)}
            val until=SystemClock.elapsedRealtime()+10000
            while(uiAutomation.rootInActiveWindow?.packageName?.toString()!=installIntent.component!!.packageName&&SystemClock.elapsedRealtime()<until)SystemClock.sleep(40)
            check(uiAutomation.rootInActiveWindow?.packageName?.toString()==installIntent.component!!.packageName)
            report.put("installerSystemWindowOpened",true).put("permissionPageRoundTrip",false)
            uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            await("installer return without installing"){current.get()===activity&&uiAutomation.rootInActiveWindow?.packageName?.toString()==host}
            report.put("scope","System installer window opened; no Install click, no installation performed")
        }
        File(output,"runtime-install-safety.json").writeText(report.toString(2))
        busyChecks(activity)
    }
    private fun busyChecks(activity:UpdateAcceptanceActivity){
        var service:MobileService?=null
        val connection=object:ServiceConnection{
            override fun onServiceConnected(name:ComponentName,binder:IBinder){service=(binder as MobileService.LocalBinder).service}
            override fun onServiceDisconnected(name:ComponentName){service=null}
        }
        main{check(activity.bindService(Intent(activity,MobileService::class.java),connection,Context.BIND_AUTO_CREATE))}
        try{
            await("isolated Mock service bound"){service!=null}
            val app=activity.application as MobileApplication
            main{
                check(!app.releaseUpdates.updateBlocked())
                val preparation=service!!.javaClass.getDeclaredField("brewPreparation").apply{isAccessible=true}.get(service) as io.openhoyi.session.BrewPreparation
                val token=preparation.begin("acceptance-fixture",93)!!
                preparation.written(token,io.openhoyi.session.OperationResult.Success(),1);preparation.observe(2,9300)
                check(preparation.state==io.openhoyi.session.BrewPreparation.State.READY)
                check(app.releaseUpdates.updateBlocked());activity.showHint();check(!activity.hintVisible())
                val owner=app.releaseUpdates.downloads
                val lease=owner.javaClass.getDeclaredField("lease").apply{isAccessible=true}.get(owner) as ReleaseInstallLease
                lease.begin(ReleaseInstallLease.Token(0,"fixture",activity,null,0))
                service!!.tareScale() // This is the isolated Mock owner, with BLE permissions absent.
                check(!lease.active);preparation.consumed();check(!app.releaseUpdates.updateBlocked())
            }
            File(output,"busy-safety.json").writeText(JSONObject().put("actualMockServiceProjection",true).put("readyBeforeStartBlocks",true)
                .put("criticalOperationRevokesLease",true).put("noBle",true).toString(2))
        }finally{main{activity.unbindService(connection)}}
    }
}
