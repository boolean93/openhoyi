package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.view.View
import android.widget.TextView
import java.io.File

/** Mock rendering probe: temporary selection/in-memory series are restored; no hardware controls. */
class PendingRecipeLabelChecks : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            var failure: Throwable? = null
            runOnMainSync {
                try {
                    val folder = File(targetContext.getExternalFilesDir(null), "pending-recipe-label").apply { mkdirs() }
                    for (size in AppFontSize.entries) for (dark in listOf(false, true))
                        for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                            val context = AppLanguageContext.wrap(targetContext, language, dark, size)
                            val style = TaskProductVisualStyle(context)
                            val pending = PendingRecipeLabel(style)
                            val title = android.widget.LinearLayout(context).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                addView(pending,android.widget.LinearLayout.LayoutParams(-1,-2))
                                addView(android.widget.Button(context).apply {
                                    text=context.getString(R.string.extraction_start)
                                    style.button(this)
                                    minimumHeight=(48*context.resources.displayMetrics.density).toInt()
                                },android.widget.LinearLayout.LayoutParams(-1,-2))
                            }
                            for (width in listOf(112, 190, 320)) {
                                val px=(width*context.resources.displayMetrics.density).toInt()
                                fun layout() {
                                    title.measure(View.MeasureSpec.makeMeasureSpec(px,View.MeasureSpec.EXACTLY),
                                        View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED))
                                    title.layout(0,0,title.measuredWidth,title.measuredHeight)
                                }
                                pending.bind(null); layout()
                                val ordinaryHeight=title.height
                                check(pending.visibility==View.GONE)
                                pending.bind("Long user recipe · 很长的自定义曲线名称".repeat(4)); layout()
                                check(pending.lineCount==1)
                                check(pending.visibility==View.VISIBLE && pending.height>0 && pending.bottom<=title.height)
                                check(pending.layout.getEllipsisCount(0)>0)
                                val button=title.getChildAt(1)
                                check(button.top>=pending.bottom && button.bottom<=title.height)
                                check(button.height>=48*context.resources.displayMetrics.density)
                                val bitmap=Bitmap.createBitmap(title.width,title.height,Bitmap.Config.ARGB_8888)
                                Canvas(bitmap).also { it.drawColor(style.surface); title.draw(it) }
                                File(folder,"${language}-${size}-${dark}-${width}.png").outputStream().use {
                                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                                }; bitmap.recycle()
                                pending.bind(null); layout()
                                check(title.height==ordinaryHeight)
                            }
                        }
                } catch(error:Throwable) { failure=error }
            }
            failure?.let { throw it }
            actualPage()
            result.putString("stream","PENDING_RECIPE_LABEL_PASSED languages=2 themes=2 sizes=3 widths=3 noHardware=true temporarySelectionAndSeriesRestored=true\n")
            finish(Activity.RESULT_OK,result)
        } catch(error:Throwable) {
            result.putString("stream",error.stackTraceToString()); finish(Activity.RESULT_CANCELED,result)
        }
    }
    /** Real Fragment rendering with an isolated in-memory record; no start/control calls. */
    private fun actualPage() {
        val prefs=targetContext.getSharedPreferences("curves",0)
        val hadSelection=prefs.contains("selected")
        val savedSelection=prefs.getString("selected",null)
        val host=MainPageTestHost.launch(this,extraction=true,timeoutMs=10000)
        val fragment=host.active("extraction") as ExtractionFragment
        fun field(owner:Any,name:String)=owner.javaClass.getDeclaredField(name).apply { isAccessible=true }
        fun main(action:()->Unit)=BoundedInstrumentationMain.run("pending recipe actual page",5000,action)
        val deadline=android.os.SystemClock.elapsedRealtime()+5000
        var service:MobileService?=null
        while(service==null && android.os.SystemClock.elapsedRealtime()<deadline) {
            main { service=field(fragment,"service").get(fragment) as? MobileService }
            if(service==null)android.os.SystemClock.sleep(30)
        }
        val owner=checkNotNull(service)
        val seriesField=field(owner,"series")
        val previousSeries=seriesField.get(owner)
        val handler=field(fragment,"handler").get(fragment) as android.os.Handler
        val refresh=field(fragment,"refresh").get(fragment) as Runnable
        val render=fragment.javaClass.getDeclaredMethod("render").apply { isAccessible=true }
        val folder=File(targetContext.getExternalFilesDir(null),"pending-recipe-label").apply { mkdirs() }
        fun label()=field(fragment,"pendingRecipeLabel").get(fragment) as TextView
        fun record(id:String) {
            val fixture=ShotSeries().apply { begin("pending-label-fixture",0,id) }
            @Suppress("UNCHECKED_CAST")
            val points=field(fixture,"recorded").get(fixture) as MutableList<ShotPoint>
            points.add(ShotPoint(0,10,10,0,9200,null))
            points.add(ShotPoint(1000,30,20,20,9200,null))
            seriesField.set(owner,fixture)
            render.invoke(fragment)
        }
        fun assertNoStopModeFootnote() {
            fun texts(view:View):List<String> = if(view is android.view.ViewGroup)
                (0 until view.childCount).flatMap {texts(view.getChildAt(it))}
                else if(view is TextView && view.visibility==View.VISIBLE) listOf(view.text.toString()) else emptyList()
            val content=texts(host.activity.window.decorView)
            check(content.none {it.contains(host.activity.getString(R.string.extraction_weight_control_advice)) ||
                it.contains(host.activity.getString(R.string.extraction_no_weight_advice))})
        }
        fun capture(name:String) {
            val frames=NativeWindowFrameEvidence(this)
            frames.committedFrame(host.activity.window.decorView)
            frames.committedFrame(host.activity.window.decorView)
            val bitmap=checkNotNull(uiAutomation.takeScreenshot())
            try {File(folder,"actual-$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}
            finally {bitmap.recycle()}
        }
        try {
            main {
                handler.removeCallbacks(refresh)
                check(prefs.edit().putString("selected","factory-v3-001").commit())
                record("manual")
                check(label().visibility==View.VISIBLE && label().text.isNotBlank())
                assertNoStopModeFootnote()
                check((field(fragment,"compactIdentity").get(fragment) as TextView).text.toString()==
                    host.activity.getString(R.string.extraction_manual_label))
            }
            capture("manual")
            main { record("factory-v3-002");check(label().visibility==View.VISIBLE) }
            capture("different-recipe")
            main { record("factory-v3-001");check(label().visibility==View.GONE) }
            capture("same-recipe")
            main {
                check(prefs.edit().putString("selected","draft-missing-fixture").commit())
                record("manual");check(label().visibility==View.GONE)
                check(prefs.edit().remove("selected").commit())
                render.invoke(fragment);check(label().visibility==View.GONE)
            }
        } finally {
            main {
                seriesField.set(owner,previousSeries)
                val editor=prefs.edit()
                if(hadSelection)editor.putString("selected",savedSelection) else editor.remove("selected")
                check(editor.commit())
                render.invoke(fragment)
                handler.removeCallbacks(refresh);handler.post(refresh)
                host.activity.finish()
            }
        }
    }

}
