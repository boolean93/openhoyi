package io.openhoyi.mobile

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection
import org.json.JSONObject

/** Compiled only with -PhoyiUpdateChecks=true. No product intent or production eligibility override. */
class UpdateAcceptanceActivity : ThemedActivity() {
    internal lateinit var fixture: UpdateAcceptanceActions
    private lateinit var card: ReleaseUpdateCard
    private var hint: ReleaseUpdatePrompt? = null
    private var transfer: ReleaseDownloadProgressPrompt? = null
    private val transferObserver: (ReleaseDownloadController.State) -> Unit = { state -> transfer?.render(state, false) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        check(BuildConfig.MOCK_MODE && packageName == "io.openhoyi.mobile.updatefixture")
        requestedOrientation = if (intent.getStringExtra("orientation") == "portrait") ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN
        val style = TaskProductVisualStyle(this)
        window.statusBarColor = style.page; window.navigationBarColor = style.surface
        val root = FrameLayout(this).apply { setBackgroundColor(style.page) }
        val body = style.column().apply { setPadding(style.dp(24),style.dp(24),style.dp(24),style.dp(24)) }
        body.addView(style.text("应用更新",TaskProductVisualStyle.title))
        val scroll = ScrollView(this).apply { addView(body) }
        root.addView(scroll,FrameLayout.LayoutParams(-1,-1).apply { bottomMargin=style.dp(64) })
        root.addView(style.text("隔离 Mock 夹具 · 本地签名 APK 字节流\n不代表生产更新资格或公开网络下载",TaskProductVisualStyle.auxiliary,
            secondary=true).apply { gravity=Gravity.CENTER;setPadding(style.dp(8),0,style.dp(8),0) },
            FrameLayout.LayoutParams(-1,style.dp(64),Gravity.BOTTOM))
        fixture=UpdateAcceptanceActions(this)
        card=ReleaseUpdateCard(this,body,SettingsVisualStyle(this),fixture)
        fixture.downloads.observe(transferObserver)
        setContentView(root)
    }
    internal fun showHint() {
        if ((application as MobileApplication).releaseUpdates.updateBlocked()) return
        hint?.dismiss()
        hint=ReleaseUpdatePrompt(this,fixture.target,later={hint=null},open={
            hint=null
            if (fixture.download(fixture.target)) showTransfer()
        }).also { it.show() }
    }
    internal fun showTransfer() {
        transfer?.hide()
        transfer=ReleaseDownloadProgressPrompt(this, action={
            if(fixture.downloads.working) { fixture.cancel();transfer?.hide();transfer=null }
            else if(fixture.downloads.state.phase==ReleaseDownloadController.Phase.FAILED) fixture.download(fixture.target)
            else fixture.install()
        },dismissed={transfer=null}).also { it.render(fixture.downloads.state,false);it.show() }
    }
    internal fun hintVisible()=hint!=null
    internal fun closeWindows() {hint?.dismiss();hint=null;transfer?.hide();transfer=null}
    override fun onResume() {super.onResume();if(::card.isInitialized){fixture.metadata.setForeground(true);card.resume()}}
    override fun onPause(){if(::card.isInitialized){card.pause();fixture.metadata.setForeground(false)};closeWindows();super.onPause()}
    override fun onDestroy(){if(::fixture.isInitialized){fixture.downloads.unobserve(transferObserver);fixture.close()};super.onDestroy()}
}

/** Test-only ports: actual byte writes and APK crypto; synthetic installed identity for rendering only. */
internal class UpdateAcceptanceActions(private val activity: Activity) : ReleaseUpdateUiActions {
    enum class Check { HOLD, AVAILABLE, CURRENT, FAIL }
    var check=Check.AVAILABLE
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executors.newSingleThreadExecutor()
    private val bytes=activity.assets.open("isolated.apk").use {it.readBytes()}
    private val identityJson=JSONObject(activity.assets.open("isolated-identity.json").bufferedReader().use {it.readText()})
    val target:ReleaseUpdate=ReleaseUpdateIndex.parse(JSONObject(mapOf(
        "schemaVersion" to 1,"channel" to "develop-test","repository" to "boolean93/openhoyi",
        "packageName" to "io.openhoyi.mobile","versionCode" to 94,"versionName" to "0.2.0-fixture",
        "signerCertificateSha256" to identityJson.getString("signerSha256"),"apkSha256" to identityJson.getString("apkSha256"),
        "declaredSourceCommit" to "c".repeat(40),"sourceCommitVerified" to false,"releaseTag" to "dev-94",
        "publishedAt" to "2026-11-12T11:40:15Z","apkFile" to "update.apk","apkSize" to bytes.size,
        "releaseUrl" to "https://github.com/boolean93/openhoyi/releases/tag/dev-94",
        "downloadUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/update.apk",
        "distributionUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/distribution.json",
        "checksumsUrl" to "https://github.com/boolean93/openhoyi/releases/download/dev-94/SHA256SUMS.txt"
    )).toString())
    private var pending:((Result<ReleaseUpdate>)->Unit)?=null
    private var saved=ReleaseUpdateCoordinator.Saved()
    override val metadata=ReleaseUpdateCoordinator(object:ReleaseUpdateCoordinator.Storage{
        override fun read()=saved
        override fun write(value:ReleaseUpdateCoordinator.Saved):Boolean{saved=value;return true}
    },System::currentTimeMillis,{0},{callback->pending=callback;{pending=null}},automaticEligible=false)
    override val rateLimited=false
    @Volatile var hold=true
    @Volatile var failTransfer=false
    private var part:File?=null
    override val downloads=ReleaseDownloadController(save={true},download={state,progress,done->
        val output=File(activity.filesDir,"update-staging/${state.file!!.name}.part");part=output
        val call=ReleaseApkTransfer({url->FixtureConnection(url,bytes,{hold},{failTransfer})})
        val future=executor.submit {
            val result=runCatching {
                call.download(target,output){count->handler.post{progress(count,false)}}
                handler.post{progress(target.apkSize,true)}
                verifyFixture(output)
                val final=state.file!!;final.parentFile!!.mkdirs();check(output.renameTo(final));final
            }
            handler.post{done(result)}
        }
        val stop:()->Unit={hold=false;call.cancel();future.cancel(true)}
        stop
    },verify={state,done->
        val future=executor.submit{val result=runCatching{verifyFixture(state.file!!)};handler.post{done(result)}}
        val stop:()->Unit={future.cancel(true)}
        stop
    },newFile={File(activity.filesDir,"updates/verified/${UUID.randomUUID()}.apk")})
    override fun identity()=InstalledUpdateIdentity("io.openhoyi.mobile",if(check==Check.CURRENT)94 else 93,false,target.signerSha256)
    fun answer() {val callback=pending;pending=null;callback?.invoke(if(check==Check.FAIL)Result.failure(IOException("Fixture offline"))else Result.success(target))}
    override fun download(target:ReleaseUpdate):Boolean {hold=true;return downloads.start(target)}
    override fun install():Boolean=false // A screenshot action never launches a system installer.
    override fun cancel(){hold=false;downloads.cancel()}
    override fun discard(){hold=false;downloads.clear()}
    fun bytesOnDisk()=part?.length() ?: 0
    fun release(){hold=false}
    fun verifyFixture(file:File) {
        check(file.length()==target.apkSize && releaseApkSha256(file)==target.apkSha256)
        check(verifyReleaseApkSignature(file,Build.VERSION.SDK_INT)==target.signerSha256)
        @Suppress("DEPRECATION")
        val info=activity.packageManager.getPackageArchiveInfo(file.absolutePath,0)!!
        check(info.packageName=="io.openhoyi.updatefixture" && info.versionCode==94)
        check(info.applicationInfo!!.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE==0)
    }
    fun close(){hold=false;metadata.setForeground(false);downloads.cancel();executor.shutdownNow()}
    private class FixtureConnection(url:URL,bytes:ByteArray,hold:()->Boolean,fail:()->Boolean):HttpsURLConnection(url){
        private val input=object:ByteArrayInputStream(bytes){
            var readCount=0
            override fun read(buffer:ByteArray,offset:Int,length:Int):Int {
                while(hold()&&readCount>=512){if(Thread.currentThread().isInterrupted)throw IOException("Fixture cancelled");Thread.sleep(20)}
                if(fail()&&readCount>=1024)throw IOException("Fixture disconnect")
                Thread.sleep(160)
                val count=super.read(buffer,offset,minOf(length,256));if(count>0)readCount+=count;return count
            }
        }
        private val size=bytes.size.toLong()
        override fun getResponseCode()=200
        override fun getContentLengthLong()=size
        override fun getInputStream()=input
        override fun disconnect()=Unit
        override fun connect()=Unit
        override fun usingProxy()=false
        override fun getCipherSuite()="fixture-local-byte-stream"
        override fun getLocalCertificates():Array<Certificate>?=null
        override fun getServerCertificates():Array<Certificate> = emptyArray()
    }
}
