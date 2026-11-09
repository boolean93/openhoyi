package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Additive, disposable-emulator regression; the full supported-language matrix still runs afterward. */
internal class HistoryFooterChecks(private val test:Instrumentation) {
    private fun main(block:()->Unit) { var error:Throwable?=null;test.runOnMainSync {try {block()} catch(t:Throwable){error=t}};error?.let {throw it} }
    private fun nodes(view:View):List<View> = listOf(view)+if(view is ViewGroup)(0 until view.childCount).flatMap {nodes(view.getChildAt(it))}else emptyList()
    private fun await(block:()->Boolean) {val end=SystemClock.elapsedRealtime()+10000;while(true){var ready=false;main {ready=block()};if(ready)return;check(SystemClock.elapsedRealtime()<end){"History footer fixture did not settle"};Thread.sleep(25)}}
    fun run(args:Bundle) {
        val context=test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(android.os.Build.HARDWARE in setOf("ranchu","goldfish")) { "History footer fixtures require disposable emulator hardware" }
        val source=args.getString("sourceSha").orEmpty();val head=args.getString("headSha").orEmpty()
        check(source.matches(Regex("[0-9a-f]{40}")) && head.matches(Regex("[0-9a-f]{40}")))
        val app=context.applicationContext as MobileApplication
        val language=context.getSharedPreferences("app_language",Context.MODE_PRIVATE)
        val appearance=context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
        val oldLanguage=app.languagePreferences.current;val oldLanguageStorage=language.all.toMap();val oldAppearance=appearance.all.toMap()
        val journal=app.journalResult.getOrThrow();val id="single-screen-ui-shot-v2-1"
        val observation=BrewJournal.Observation(id,"capture-1",1700000000000,1700000030000,30000,"ENDED",null,3600)
        main {val prior=journal.find(id);if(prior==null)journal.observe(observation)else check(prior.observation==observation)}
        val samples=(0..30).map {ShotPoint(it*1000L,if(it<4)it*20 else 90-(it-4),30,it*30,9300,it*120,120,true)}
        val previousSamples=app.samples.load(id);if(previousSamples.isEmpty())app.samples.save(id,samples)else check(previousSamples==samples)
        val beforeJournal=journal.exportJson();val beforeHistory=app.history.entries;val beforeInventory=app.beanInventoryResult.getOrThrow().events()
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"single-screen-window-checks/history-footer-focused").apply {check(isDirectory||mkdirs())}
        val results=JSONArray();val failures=mutableListOf<String>();val frames=NativeWindowFrameEvidence(test)
        val locales=AppLanguage.entries
        try {
            for(locale in locales)for(dark in listOf(false,true)) {
                val name="${locale.tag}-${if(dark)"dark" else "light"}"
                val folder=File(directory,name).apply {check(isDirectory||mkdirs())};var activity:Activity?=null
                val record=JSONObject().put("case",name).put("sourceSha",source).put("headSha",head)
                try {
                    main {check(app.languagePreferences.select(locale)!=AppLanguagePreference.Selection.SAVE_FAILED);check(appearance.edit().putBoolean("dark",dark).commit())}
                    val page=test.startActivitySync(Intent(context,HistoryDetailActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("shotId",id));activity=page
                    val root=page.findViewById<View>(android.R.id.content)
                    main {
                        val bounds=page.windowManager.currentWindowMetrics.bounds
                        check(bounds.width()==1600 && bounds.height()==1000 && page.resources.configuration.densityDpi==200)
                    }
                    await {val chart=nodes(root).filterIsInstance<ShotChartView>().singleOrNull();page.hasWindowFocus() && chart?.points?.size==31 && root.isLaidOut && !root.isLayoutRequested && !chart.isLayoutRequested}
                    lateinit var chart:ExtractionOverlayView;lateinit var footer:View;lateinit var actions:List<Button>
                    main {chart=nodes(root).filterIsInstance<ExtractionOverlayView>().single();footer=requireNotNull(root.findViewWithTag("product/history/standalone-footer-row"));actions=nodes(footer).filterIsInstance<Button>();check(actions.size==3)}
                    fun snapshot():JSONObject {
                        val plot=requireNotNull(chart.findViewWithTag<View>("chart-plot"));val origin=IntArray(2);plot.getLocationOnScreen(origin)
                        val operation=(0 until chart.visualEvidence.length()).map {chart.visualEvidence.getJSONObject(it)}.single {it.optString("operation")=="plot"}
                        val bounds=operation.getJSONArray("boundsPx");val screen=RectF(bounds.getDouble(0).toFloat()+origin[0],bounds.getDouble(1).toFloat()+origin[1],bounds.getDouble(2).toFloat()+origin[0],bounds.getDouble(3).toFloat()+origin[1])
                        val ownerRoot=plot.rootView
                        check(ownerRoot===root.rootView && ownerRoot.isAttachedToWindow && plot.windowToken===ownerRoot.windowToken && plot.display?.displayId==ownerRoot.display?.displayId)
                        var ancestor:View?=plot
                        while(ancestor!=null) {
                            val current=ancestor
                            check(current.matrix.isIdentity && current.scaleX==1f && current.scaleY==1f && current.translationX==0f && current.translationY==0f && current.rotation==0f && current.rotationX==0f && current.rotationY==0f)
                            if(current===ownerRoot)break
                            ancestor=current.parent as? View
                        }
                        check(ancestor===ownerRoot)
                        val rootScreen=IntArray(2);ownerRoot.getLocationOnScreen(rootScreen)
                        val rootRaw=Rect();val rootOffset=android.graphics.Point();check(ownerRoot.getGlobalVisibleRect(rootRaw,rootOffset))
                        val rawVisible=Rect();val plotOffset=android.graphics.Point();check(plot.getGlobalVisibleRect(rawVisible,plotOffset))
                        val dx=rootScreen[0]-rootOffset.x-ownerRoot.scrollX;val dy=rootScreen[1]-rootOffset.y-ownerRoot.scrollY
                        check(plotOffset.x+plot.scrollX+dx==origin[0] && plotOffset.y+plot.scrollY+dy==origin[1])
                        val visible=Rect(rawVisible).apply {offset(dx,dy)}
                        val intersection=RectF(screen);val intersects=intersection.intersect(RectF(visible))
                        check(screen.left>=origin[0] && screen.top>=origin[1] && screen.right<=origin[0]+plot.width && screen.bottom<=origin[1]+plot.height)
                        val budget=footer.javaClass.getDeclaredField("productLayoutEvidence").apply {isAccessible=true}.get(footer) as JSONObject
                        return JSONObject().put("sourceSha",source).put("headSha",head)
                            .put("configuration",page.resources.configuration.toString()).put("density",page.resources.displayMetrics.density)
                            .put("fontScale",page.resources.configuration.fontScale).put("rootHeightPx",root.height).put("chartHeightPx",chart.height)
                            .put("rawGlobalVisibleRect",JSONArray(listOf(rawVisible.left,rawVisible.top,rawVisible.right,rawVisible.bottom)))
                            .put("rawRootGlobalVisibleRect",JSONArray(listOf(rootRaw.left,rootRaw.top,rootRaw.right,rootRaw.bottom)))
                            .put("rootScreenPx",JSONArray(rootScreen.toList())).put("rootGlobalOffsetPx",JSONArray(listOf(rootOffset.x,rootOffset.y)))
                            .put("rootScrollPx",JSONArray(listOf(ownerRoot.scrollX,ownerRoot.scrollY))).put("plotScrollPx",JSONArray(listOf(plot.scrollX,plot.scrollY)))
                            .put("plotGlobalOffsetPx",JSONArray(listOf(plotOffset.x,plotOffset.y))).put("globalToScreenOffsetPx",JSONArray(listOf(dx,dy)))
                            .put("plotScreenOriginPx",JSONArray(origin.toList())).put("screenMappingVerified",true)
                            .put("fixturePolicy","shared_original_single_screen_named_fixture_no_additional_rows")
                            .put("canvasPlotScreenPx",JSONArray(listOf(screen.left,screen.top,screen.right,screen.bottom)))
                            .put("plotVisibleScreenPx",JSONArray(listOf(visible.left,visible.top,visible.right,visible.bottom)))
                            .put("canvasVisibleIntersectionPx",JSONArray(listOf(intersection.left,intersection.top,intersection.right,intersection.bottom)))
                            .put("canvasIntersectsVisible",intersects).put("canvasVisibleHeightPx",if(intersects)intersection.height()else 0f)
                            .put("canvasVisibleWidthPx",if(intersects)intersection.width()else 0f)
                            .put("allAxes",operation.getBoolean("allAxes")).put("footer",JSONObject(budget.toString()))
                            .put("buttons",JSONArray(actions.map {JSONObject().put("identity",System.identityHashCode(it)).put("tag",it.tag).put("text",it.text.toString()).put("widthPx",it.width).put("heightPx",it.height)}))
                    }
                    val captured=frames.screenshot(page,root,File(folder,"window.png")){snapshot()}
                    WindowProofStreamingWriter.writeStrict(captured.target,File(folder,"geometry.json"))
                    record.put("geometry",captured.target).put("proof","$name/window-frame-sync.json")
                    main {
                        SingleScreenPageChecks.verify(page)
                        check(captured.target.getBoolean("allAxes") && captured.target.getBoolean("canvasIntersectsVisible"))
                        check(captured.target.getDouble("canvasVisibleHeightPx")>=96*page.resources.displayMetrics.density && captured.target.getDouble("canvasVisibleWidthPx")>=96*page.resources.displayMetrics.density)
                        check(captured.target.getDouble("fontScale")>1.29 && captured.target.getDouble("fontScale")<1.31)
                        check(captured.target.getJSONObject("footer").getBoolean("inline"))
                        actions.forEach {check(it.isShown && it.width>=HoyiUi.dp(page,48) && it.height>=HoyiUi.dp(page,48));LanguageUiLayoutChecks.textFits(it,name)}
                        val original=Rect(footer.left,footer.top,footer.right,footer.bottom);val identities=actions.map(System::identityHashCode);val texts=actions.map {it.text.toString()}
                        try {
                            val width=HoyiUi.dp(page,320)
                            footer.forceLayout();footer.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));footer.layout(0,0,width,footer.measuredHeight)
                            val budget=footer.javaClass.getDeclaredField("productLayoutEvidence").apply {isAccessible=true}.get(footer) as JSONObject
                            check(!budget.getBoolean("inline"));check(actions.map(System::identityHashCode)==identities && actions.map {it.text.toString()}==texts)
                            actions.forEach {check(it.width>=HoyiUi.dp(page,48) && it.height>=HoyiUi.dp(page,48));LanguageUiLayoutChecks.textFits(it,"$name narrow component")}
                            val bitmap=Bitmap.createBitmap(width,footer.height,Bitmap.Config.ARGB_8888)
                            try {footer.draw(Canvas(bitmap));File(folder,"synthetic-narrow-footer.png").outputStream().use {check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it))}}finally {bitmap.recycle()}
                            record.put("narrowComponent",JSONObject(budget.toString()).put("scope","SYNTHETIC_COMPONENT_RENDER_NOT_WHOLE_PAGE_WINDOW_ACCEPTANCE").put("widthDp",320).put("sameNodesAndText",true))
                        } finally {
                            footer.measure(View.MeasureSpec.makeMeasureSpec(original.width(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(original.height(),View.MeasureSpec.EXACTLY));footer.layout(original.left,original.top,original.right,original.bottom);root.requestLayout()
                        }
                    }
                    await {root.isLaidOut && !root.isLayoutRequested && !footer.isLayoutRequested}
                    main {SingleScreenPageChecks.verify(page);check(journal.exportJson()==beforeJournal && app.history.entries==beforeHistory && app.beanInventoryResult.getOrThrow().events()==beforeInventory)}
                    record.put("status","PASS")
                }catch(t:Throwable){failures+="$name: ${t.stackTraceToString()}";record.put("status","FAIL").put("failure",t.stackTraceToString())}
                finally {activity?.let {page->try {main {page.finish()}}catch(t:Throwable){failures+="$name cleanup: $t"}};results.put(record);File(directory,"results.json").writeText(JSONObject().put("cases",results).put("failures",JSONArray(failures)).toString(2))}
            }
        } finally {
            fun restore(label:String,block:()->Unit) {try {main(block)}catch(t:Throwable){failures+="restore $label: ${t.stackTraceToString()}"}}
            restore("language selection") {check(app.languagePreferences.select(oldLanguage)!=AppLanguagePreference.Selection.SAVE_FAILED)}
            restore("language storage") {restoreMockPreferences(language,oldLanguageStorage)}
            restore("appearance") {restoreMockPreferences(appearance,oldAppearance)}
            File(directory,"results.json").writeText(JSONObject().put("cases",results).put("failures",JSONArray(failures)).toString(2))
        }
        check(results.length()==4 && failures.isEmpty()){failures.joinToString("\n")}
        check(journal.exportJson()==beforeJournal && app.samples.load(id)==samples)
        test.sendStatus(0,Bundle().apply {putString("stream","HISTORY_FOOTER_FOCUSED_PASSED nativeCases=4 canvasMinimumDp=96 wideFraction=0.4 syntheticNarrowFooterDp=320 noBle=true\n")})
    }
}
