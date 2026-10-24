package io.openhoyi.mobile

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Instrumentation
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Rect
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.ScrollView
import android.widget.EditText
import android.widget.ListView
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.session.ExtractionState
import io.openhoyi.trace.TraceStore
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** M3 real library/import/preview evidence. Only isolated Mock domain stores; no machine commands. */
internal class CurveVisualMigrationChecks(private val test: Instrumentation) {
    private val context get() = test.targetContext
    private val app get() = context.applicationContext as MobileApplication
    private val frames = NativeWindowFrameEvidence(test)
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false; main { ready = condition() }
            if (ready && SystemClock.elapsedRealtime() <= deadline) return
            check(SystemClock.elapsedRealtime() < deadline) { "M3 timeout: $description" }
            test.waitForIdleSync()
            // Bounded condition polling, never an acceptance delay or retry of a failed assertion.
            SystemClock.sleep(20)
        }
    }
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun field(owner: Any, name: String): Any? = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(owner)
    private fun write(owner: Any, name: String, value: Any?) = owner.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(owner, value)
    private fun sha(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun shaText(value:String):String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
    private fun businessDisk(allowPresetFiles:Boolean=false): Map<String, String> = listOf("files", "shared_prefs", "databases").flatMap { name ->
        File(context.applicationInfo.dataDir, name).walkTopDown().filter { it.isFile &&
            !(name == "shared_prefs" && (it.name in setOf("appearance.xml", "appearance.xml.bak", "curves.xml", "curves.xml.bak") ||
                (allowPresetFiles && it.name in setOf("presets.xml", "presets.xml.bak")))) }
            .map { it.relativeTo(File(context.applicationInfo.dataDir)).path to sha(it) }.toList()
    }.toMap()
    private fun services(): List<String> = (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        .getRunningServices(100).filter { it.service.packageName == context.packageName }.map { it.service.className }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun rect(value: JSONArray) = Rect(value.getInt(0), value.getInt(1), value.getInt(2), value.getInt(3))
    /** Measure the hierarchy clip and its origin in the same screen coordinate space. */
    private fun visibility(view: View): JSONObject {
        val ownerRoot=view.rootView
        val origin=IntArray(2); view.getLocationOnScreen(origin)
        val rootScreen=IntArray(2); ownerRoot.getLocationOnScreen(rootScreen)
        val rootWindow=IntArray(2); ownerRoot.getLocationInWindow(rootWindow)
        val raw=Rect(); val targetGlobalOffset=Point()
        val shown=view.getGlobalVisibleRect(raw,targetGlobalOffset)
        val rootRaw=Rect(); val rootGlobalOffset=Point()
        val rootShown=ownerRoot.getGlobalVisibleRect(rootRaw,rootGlobalOffset)
        val dx=rootScreen[0]-rootGlobalOffset.x-ownerRoot.scrollX
        val dy=rootScreen[1]-rootGlobalOffset.y-ownerRoot.scrollY
        val derived=Rect(raw).apply { if(shown && rootShown) offset(dx,dy) }
        val full=Rect(origin[0],origin[1],origin[0]+view.width,origin[1]+view.height)
        val mappedOrigin=intArrayOf(targetGlobalOffset.x+view.scrollX+dx,targetGlobalOffset.y+view.scrollY+dy)
        var identity=true; var current:View?=view
        while(current!=null) {
            val target=current
            identity=identity && target.matrix.isIdentity && target.scaleX==1f && target.scaleY==1f &&
                target.rotation==0f && target.rotationX==0f && target.rotationY==0f &&
                target.translationX==0f && target.translationY==0f && target.translationZ==0f
            if(target===ownerRoot) break
            current=target.parent as? View
        }
        val sameRoot=current===ownerRoot && view.isAttachedToWindow && ownerRoot.isAttachedToWindow &&
            view.windowToken!=null && view.windowToken===ownerRoot.windowToken && view.display!=null &&
            view.display?.displayId==ownerRoot.display?.displayId
        val mapping=shown && rootShown && sameRoot && identity && mappedOrigin.contentEquals(origin)
        return JSONObject().put("rectPx",rect(full)).put("visibleRectPx",rect(derived)).put("rectCoordinateSpace","screen")
            .put("rawGlobalVisibleRectRootPx",rect(raw)).put("rawRootGlobalVisibleRect",rect(rootRaw))
            .put("rawGlobalVisibleRectDefined",shown).put("rootGlobalVisibleRectDefined",rootShown)
            .put("targetGlobalOffsetPointPx",JSONArray(listOf(targetGlobalOffset.x,targetGlobalOffset.y)))
            .put("rootGlobalOffsetPointPx",JSONArray(listOf(rootGlobalOffset.x,rootGlobalOffset.y)))
            .put("rootOriginOnScreenPx",JSONArray(rootScreen.toList())).put("rootOriginInWindowPx",JSONArray(rootWindow.toList()))
            .put("rootScrollPx",JSONArray(listOf(ownerRoot.scrollX,ownerRoot.scrollY)))
            .put("targetScrollPx",JSONArray(listOf(view.scrollX,view.scrollY)))
            .put("derivedScreenOffsetPx",JSONArray(listOf(dx,dy))).put("mappedScreenOriginPx",JSONArray(mappedOrigin.toList()))
            .put("rootIdentity",System.identityHashCode(ownerRoot)).put("attachedToSameRootWindow",sameRoot)
            .put("rootAttached",ownerRoot.isAttachedToWindow).put("targetAttached",view.isAttachedToWindow)
            .put("rootDisplayId",ownerRoot.display?.displayId ?: JSONObject.NULL).put("targetDisplayId",view.display?.displayId ?: JSONObject.NULL)
            .put("identityTransformChain",identity).put("screenMappingVerified",mapping)
            .put("visibleRectConversion","raw_global_rect_plus_measured_root_screen_minus_root_global_Point_origin; own_scroll_explicitly_removed")
            .put("visibilitySemantics","exact_hierarchy_clip_only; not_system_window_or_IME_occlusion_proof")
            .put("globalVisible",shown).put("complete",mapping && view.width>0 && view.height>0 && derived==full)
    }
    private fun observation(view: View): JSONObject {
        val visible=visibility(view)
        val texts = nodes(view).filterIsInstance<TextView>().map { text ->
            val textVisibility=visibility(text)
            val layout = text.layout
            JSONObject().put("semanticId", text.tag).put("text", text.text).put("fullViewVisible", textVisibility.getBoolean("complete"))
                .put("visibility",textVisibility)
                .put("layoutPresent", layout != null).put("lineCount", layout?.lineCount ?: 0)
                .put("ellipsisCount", layout?.let { (0 until it.lineCount).sumOf(it::getEllipsisCount) } ?: JSONObject.NULL)
                .put("layoutHeightPx", layout?.height ?: JSONObject.NULL)
                .put("contentHeightPx", text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
                .put("contentWidthPx", text.width - text.compoundPaddingLeft - text.compoundPaddingRight)
                .put("horizontalLayoutLines",layout?.let { actual -> JSONArray((0 until actual.lineCount).map { index ->
                    JSONObject().put("line",index).put("leftPx",actual.getLineLeft(index)).put("rightPx",actual.getLineRight(index))
                }) } ?: JSONObject.NULL)
        }
        return visible.put("semanticId",view.tag).put("textLineBoxes",JSONArray(texts))
    }
    private fun whole(value: JSONObject): Boolean = value.getBoolean("complete") && value.getJSONArray("textLineBoxes").let { lines ->
        (0 until lines.length()).all { index -> val line = lines.getJSONObject(index)
            line.getBoolean("fullViewVisible") && line.getBoolean("layoutPresent") && line.getInt("lineCount") > 0 &&
                line.getInt("ellipsisCount") == 0 && line.getInt("layoutHeightPx") <= line.getInt("contentHeightPx") &&
                line.getJSONArray("horizontalLayoutLines").let { horizontal ->
                    (0 until horizontal.length()).all { row -> val bounds=horizontal.getJSONObject(row)
                        bounds.getDouble("leftPx")>=0 && bounds.getDouble("rightPx")<=line.getInt("contentWidthPx") &&
                            bounds.getDouble("rightPx")-bounds.getDouble("leftPx")<=line.getInt("contentWidthPx") }
                } }
    }
    private inner class Scope(private val directory: File, private val page: String, private val specialLibrary: Boolean=false) {
        private val delegateNames = listOf("feedbackPreferences", "logs", "curves", "samples", "history", "customCurvesResult",
            "curveUsageResult", "beanInventoryResult", "beanPreparationResult", "journalStoreResult")
        private val legacyNames = listOf("legacyHistory", "legacyCurves")
        private val saved = linkedMapOf<String, Lazy<*>>()
        private val originalInitialized = linkedMapOf<String, Boolean>()
        private val legacy = linkedMapOf<String, Pair<Lazy<*>, Boolean>>()
        private var ownedLegacy: LegacyCurveStore? = null
        private var ownedLegacySha:String?=null
        private val legacyReaders = linkedSetOf<Thread>()
        private val legacyReaderNames = setOf("legacy-curve-list", "legacy-curve-import")
        private var oldLanguage: Any? = null
        private var oldFailure: Any? = null
        private val savedPrefs = linkedMapOf<String, Map<String, *>>()
        private var diskBefore = emptyMap<String, String>()
        private var whitePrefFileBefore = emptyMap<String, String>()
        private fun whitePrefFiles(): Map<String,String> = listOf("appearance.xml","appearance.xml.bak","curves.xml","curves.xml.bak",
            "presets.xml","presets.xml.bak").mapNotNull { name ->
            File(File(context.applicationInfo.dataDir,"shared_prefs"),name).takeIf { it.isFile }?.let { name to sha(it) }
        }.toMap()
        private var installed = false
        private val ownedDirectory = File(context.cacheDir, "curve-visual-${UUID.randomUUID()}")
        private val tracked = linkedSetOf<Activity>()
        private val destroyed = linkedSetOf<Activity>()
        var service: MobileService? = null
        val inventory = BeanInventory(object : InventoryStorage { var value: ByteArray? = null
            override fun read() = value?.clone(); override fun write(bytes: ByteArray) { value = bytes.clone() } })
        val preparation = BeanPreparation(object : BeanPreparation.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        var rejectCustomWrites = false
        var customWriteAttempts = 0
            private set
        val custom = CustomCurveStore(object : CustomCurveStore.Storage { var value: String? = null
            override fun read() = value
            override fun write(value: String) {
                customWriteAttempts++
                check(!rejectCustomWrites) { "M3 scoped memory custom write failure before publish" }
                this.value = value
            }
        })
        val usage = CurveUsageLedger(object : CurveUsageLedger.Storage { var value = ""
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val journal = BrewJournal(object : BrewJournal.Storage { var value: String? = null
            override fun read() = value; override fun write(value: String) { this.value = value } })
        val feedback = BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            override fun read() = false; override fun write(enabled: Boolean) = !enabled })
        private lateinit var logs: TraceStore
        private lateinit var samples: ShotSamplesRepository
        lateinit var curves: CurveLibrary
            private set
        lateinit var history: ShotHistory
            private set
        lateinit var document: CustomCurveDocument
            private set
        lateinit var baseLibraryIds: List<String>
            private set
        lateinit var incoming: CustomCurveDocument
            private set
        var readonly: CustomCurveDocument?=null
            private set
        lateinit var fixture: JSONObject
            private set
        private val lifecycle = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) { tracked.add(activity) }
            override fun onActivityDestroyed(activity: Activity) { destroyed.add(activity) }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        }
        private var clipboardCaptured = false
        private var clipboardRestored = false
        private var originalClip: ClipData? = null
        private var originalClipFingerprint: String? = null
        private var originalClipPresent = false
        private var providerSession: String? = null
        private val importThreads = linkedSetOf<Thread>()
        private fun liveImportThreads() = Thread.getAllStackTraces().keys.filter { it.isAlive && it.name == "curve-document-import" }
        private fun clipFingerprint(clip: ClipData?): String? = clip?.let { value ->
            // Inert payload only: never resolve/coerce unknown URI/Intent or persist clipboard content.
            JSONObject().put("label",value.description.label?.toString())
                .put("mimeTypes",JSONArray((0 until value.description.mimeTypeCount).map(value.description::getMimeType)))
                .put("items",JSONArray((0 until value.itemCount).map { index -> val item=value.getItemAt(index)
                    JSONObject().put("text",item.text?.toString()).put("htmlText",item.htmlText)
                        .put("uri",item.uri?.toString()).put("intent",item.intent?.toUri(0)) })).toString()
        }
        fun clipboardSnapshot():String {
            val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            return JSONObject().put("present",manager.hasPrimaryClip())
                .put("fingerprint",clipFingerprint(manager.primaryClip) ?: JSONObject.NULL).toString()
        }
        fun pasteFixture(owner: Activity, value: String) {
            main {
                check(owner is CurveImportActivity && owner.hasWindowFocus())
                val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if(!clipboardCaptured) {
                    originalClipPresent=manager.hasPrimaryClip(); originalClip=manager.primaryClip
                    check(originalClipPresent==(originalClip!=null)) { "Foreground clipboard presence/read inconsistent" }
                    originalClipFingerprint=clipFingerprint(originalClip); clipboardCaptured=true
                }
                manager.setPrimaryClip(ClipData.newPlainText("M3 isolated explicit paste fixture",value))
            }
        }
        fun observeImportThreads() { importThreads.addAll(liveImportThreads()) }
        fun drainImportThreads() {
            observeImportThreads()
            val deadline=SystemClock.elapsedRealtime()+10000
            importThreads.filter { it.isAlive }.forEach { thread ->
                val remaining=deadline-SystemClock.elapsedRealtime()
                check(remaining>0) { "M3 import thread drain budget exhausted" }
                thread.join(remaining)
                check(!thread.isAlive && SystemClock.elapsedRealtime()<=deadline) { "M3 owned import thread did not exit" }
            }
            // Thread may have finished before it was observable; main callback outcome and this barrier prove no pending callback remains.
            main { }
            check(liveImportThreads().isEmpty()) { "M3 new import thread still alive" }
        }
        private fun liveLegacyReaders()=Thread.getAllStackTraces().keys.filter { it.isAlive && it.name in legacyReaderNames }
        fun installOwnedLegacy(): LegacyCurveStore {
            check(page=="library" && ownedLegacy==null && liveLegacyReaders().isEmpty())
            val archive=JSONObject().put("format","openhoyi-legacy-curves-v1").put("factoryVersion",3)
                .put("items",JSONArray().put(JSONObject().put("name","M3 owned legacy route").put("category","espresso")
                    .put("factory",false).put("temp",93).put("flow",60).put("weight",360).put("seg",4)))
            val store=LegacyCurveStore(File(ownedDirectory,"library-route-legacy.json"))
            store.import(archive.toString().byteInputStream())
            check(store.load()?.curves?.single()?.name=="M3 owned legacy route")
            ownedLegacySha=sha(File(ownedDirectory,"library-route-legacy.json"))
            main {
                val old=requireNotNull(legacy["legacyCurves"])
                check(field(app,"legacyCurves\$delegate")===old.first && !old.second && !old.first.isInitialized())
                write(app,"legacyCurves\$delegate",lazyOf(store))
                check(app.legacyCurves===store)
            }
            ownedLegacy=store
            return store
        }
        fun drainOwnedLegacyReaders() {
            legacyReaders.addAll(liveLegacyReaders())
            val deadline=SystemClock.elapsedRealtime()+10000
            legacyReaders.filter { it.isAlive }.forEach { reader ->
                val remaining=deadline-SystemClock.elapsedRealtime()
                check(remaining>0); reader.join(remaining)
                check(!reader.isAlive && SystemClock.elapsedRealtime()<=deadline)
            }
            main { }
            check(liveLegacyReaders().isEmpty()) { "M3 owned legacy reader still alive" }
        }
        fun restoreClipboard(owner: Activity?) {
            if(!clipboardCaptured || clipboardRestored) return
            drainImportThreads()
            main {
                val manager=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if(originalClipPresent) manager.setPrimaryClip(requireNotNull(originalClip)) else manager.clearPrimaryClip()
                val foreground=owner is CurveImportActivity && owner.hasWindowFocus() && !owner.isDestroyed
                if(foreground) {
                    check(manager.hasPrimaryClip()==originalClipPresent && clipFingerprint(manager.primaryClip)==originalClipFingerprint)
                    clipboardRestored=true
                }
                File(directory,"clipboard-isolation.json").writeText(JSONObject()
                    .put("explicitPasteFixtureOnly",true).put("originalPayloadNotPersisted",true)
                    .put("restoreCallCompleted",true).put("foregroundLogicalReadback",if(foreground) "PASS" else "UNKNOWN_background_clipboard_policy")
                    .put("timestampOrParcelByteIdentity","not_claimed").toString(2))
                check(foreground) { "M3 clipboard restore readback UNKNOWN without foreground Import; isolation not passed" }
            }
        }
        fun createProvider(document: CustomCurveDocument): String {
            check(providerSession==null)
            check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION)==android.content.pm.PackageManager.PERMISSION_GRANTED)
            check(context.packageManager.checkSignatures(context.packageName,test.context.packageName)==android.content.pm.PackageManager.SIGNATURE_MATCH)
            val session=UUID.randomUUID().toString(); providerSession=session
            provider("create",Bundle().apply { putString("document",document.encode()) })
            return session
        }
        fun provider(method: String, data: Bundle?=null): Bundle = requireNotNull(context.contentResolver.call(
            Uri.parse("content://${CurveFixtureProvider.AUTHORITY}"),method,requireNotNull(providerSession),data))
        fun prepare() {
            // Scope is assigned to the outer finally BEFORE allocating either owned IO resource.
            check(!::logs.isInitialized && !::samples.isInitialized)
            check(liveImportThreads().isEmpty()) { "Original import thread exists before scoped installation" }
            logs=TraceStore(File(ownedDirectory,"traces"))
            samples=ShotSamplesRepository(File(ownedDirectory,"samples")) { logs.record("fixture.samples_error",mapOf("type" to it.javaClass.simpleName)) }
            document=CustomCurveDocument("draft-00000000-0000-0000-0000-000000000301", "经典浓缩", 93, 3600,
                CustomCurveDocument.ControlMode.PRESSURE, listOf(20,90,80,60).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            custom.save(document)
            if(specialLibrary) {
                check(page=="library")
                readonly=document.copy(id=CustomCurveDocument.newId(),name="FlowRaw只读参数",
                    controlMode=CustomCurveDocument.ControlMode.FLOW_RAW,
                    stages=listOf(30,90,70,50).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
                custom.save(requireNotNull(readonly))
            }
            incoming=document.copy(name="经典浓缩（新版）",stages=listOf(30,90,70,50).zip(listOf(150,250,150,50)).map { CustomCurveDocument.Stage(it.first,it.second) })
            val factory=FactoryCurveCatalog.load(context.assets.open("factory_curves_v3.tsv"))
            val proof=FactoryWireProof.load(context.assets.open("factory_wire_v1.tsv"),factory,context.assets.open("factory_slot_wire_v1.tsv"))
            baseLibraryIds=CurveLibrary(factory,proof).items.map { it.id }
            check(baseLibraryIds.size==103) { "M3 must preserve the original complete 103-item fixture" }
            curves=CurveLibrary(factory,proof,{ custom.items() },true)
            check(curves.items.map { it.id }.containsAll(baseLibraryIds) &&
                curves.items.size==baseLibraryIds.size+(if(specialLibrary) 2 else 1))
            var nextShot=0
            val fixedNow=1792026750000L
            history=ShotHistory(object : ShotHistory.Storage { var value=""
                override fun read()=value; override fun write(value: String) { this.value=value }
            },now={fixedNow},newId={ "m3-scoped-observed-${++nextShot}" },onObservedUse={ entry ->
                usage.record(entry.id,entry.curveId,entry.startedAtMs)
            },onEntryChanged={ entry ->
                journal.observe(entry.journalObservation())
                if(preparation.current()?.shotId==entry.id) preparation.associatePending(journal,inventory)
            })
            journal.reconcileRecent(history.entries.map { it.journalObservation() })
            preparation.associatePending(journal,inventory)
            samples.prune(history.entries.map(ShotHistory.Entry::id).toSet())
            (1..3).forEach { index ->
                val at=fixedNow-(4-index)*30000L
                history.begin(document.id,at)
                history.transition(ExtractionState.RUNNING,null,null,at+1000)
                history.observeRunning()
                history.transition(ExtractionState.ENDED_OBSERVED,null,3600,at+27400)
            }
            check(usage.stats(document.id).count==3L && history.entries.size==3 && journal.entries().size==3)
            fixture=JSONObject().put("page",page).put("document",document.toJson())
                .put("incomingPreviewDocumentNotSaved",incoming.toJson()).put("baseLibraryIds",JSONArray(baseLibraryIds))
                .put("specialLibraryScope",specialLibrary).put("readonlyOwnedDocument",readonly?.toJson() ?: JSONObject.NULL)
                .put("ownedCustomWriteAttemptsAtPrepare",customWriteAttempts)
                .put("actualLibraryCount",curves.items.size).put("scopedHistory",JSONArray(history.entries.map { entry ->
                    JSONObject().put("id",entry.id).put("curveId",entry.curveId).put("status",entry.status).put("observedRunning",entry.observedRunning)
                })).put("usageCount",usage.stats(document.id).count)
                .put("usageSource","three_synthetic_legal_ShotHistory_domain_observations_not_hardware_telemetry")
                .put("source","legal_memory_Storage_and_scoped_owned_cache_writers")
                .put("servicePolicy","no_service_launch_bind_or_machine_commands")
                .put("clipboardAndPresetPolicy","clipboard_only_explicit_import_business_with_foreground_restore; presets_not_touched")
        }
        fun install(dark: Boolean) {
            main {
                check(services().isEmpty() && android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) {
                    "Fresh isolated process required: service or attached window already exists"
                }
                delegateNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>
                    saved[name]=lazy; originalInitialized[name]=lazy.isInitialized() }
                legacyNames.forEach { name -> val lazy = field(app, name + "\$delegate") as Lazy<*>; legacy[name]=lazy to lazy.isInitialized() }
                // A fresh process must not have acquired any original store before this entire group is installed.
                check(saved.values.none { it.isInitialized() }) { "Original delegate initialized before fixture isolation" }
                oldLanguage = field(app,"languagePreferences"); oldFailure=field(app,"journalObservationFailure")
                listOf("appearance","curves","presets").forEach { savedPrefs[it]=context.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap() }
                diskBefore=businessDisk(specialLibrary); whitePrefFileBefore=whitePrefFiles()
                app.registerActivityLifecycleCallbacks(lifecycle)
                val values = mapOf("feedbackPreferences" to feedback,"logs" to logs,"curves" to curves,"samples" to samples,"history" to history,
                    "customCurvesResult" to Result.success(custom),"curveUsageResult" to Result.success(usage),"beanInventoryResult" to Result.success(inventory),
                    "beanPreparationResult" to Result.success(preparation),"journalStoreResult" to Result.success(journal))
                // Set installed before mutation, so a partial install still runs the same strict finally cleanup.
                installed=true
                values.forEach { (name,value) -> write(app,name+"\$delegate",lazyOf(value)) }
                write(app,"journalObservationFailure",null)
                write(app,"languagePreferences",AppLanguagePreference(object : AppLanguagePreference.Storage {
                    override fun read() = "zh-Hans"; override fun write(tag: String) = tag == "zh-Hans" }))
                check(app.feedbackPreferences === feedback && app.logs === logs && app.curves === curves && app.samples === samples && app.history === history)
                check(app.customCurvesResult.getOrThrow() === custom && app.curveUsageResult.getOrThrow() === usage && app.beanInventoryResult.getOrThrow() === inventory &&
                    app.beanPreparationResult.getOrThrow() === preparation && app.journalResult.getOrThrow() === journal)
                check(context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).commit())
                check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).edit().putString("selected",document.id).putString("sort",CurveSortMode.RECENT.name).commit())
            }
        }
        fun activeActivity(): Activity? = tracked.lastOrNull { !it.isDestroyed }
        fun cleanup() {
            val proof = JSONObject().put("scopedOwnedDirectory",ownedDirectory.absolutePath)
            try {
                if (installed) {
                    drainImportThreads()
                    if(ownedLegacy!=null) drainOwnedLegacyReaders()
                    restoreClipboard(activeActivity())
                    main { tracked.filter { !it.isDestroyed }.forEach { it.finish() } }
                    await("all fixture Activities destroyed") { tracked.all { it.isDestroyed && it in destroyed } }
                    main { context.stopService(Intent(context,MobileService::class.java)) }
                    await("Mock service removed and onDestroy postconditions") {
                        services().isEmpty() && (service?.let { owner ->
                            val id = field(owner,"ownerId")
                            val handler = field(owner,"handler") as Handler
                            val noCallbacks = listOf("mockTick","watchShot","leaveForeground","stopAutomaticScale").all { !handler.hasCallbacks(field(owner,it) as Runnable) }
                            val observers = field(feedback,"observers") as Map<*,*>
                            val visibility = field(app.visibility,"observers") as Map<*,*>
                            noCallbacks && id !in observers && id !in visibility && field(owner,"hub") == null
                        } ?: true)
                    }
                    drainImportThreads()
                    if(ownedLegacy!=null) drainOwnedLegacyReaders()
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().isEmpty()) { "M3 fixture dialog/window remains after Activity destruction" }
                    proof.put("ownedImportThreadsObserved",importThreads.size).put("allImportThreadsExitedAndMainCallbackBarrier",true)
                    if(ownedLegacy!=null) {
                        val current=sha(File(ownedDirectory,"library-route-legacy.json"))
                        check(current==ownedLegacySha) { "M3 owned legacy read-only route changed archive bytes" }
                        proof.put("ownedLegacyArchiveSha256",current)
                        .put("ownedLegacyReadersObserved",legacyReaders.size)
                        .put("allOwnedLegacyReadersExitedAndMainCallbackBarrier",true)
                    }
                    proof.put("activityDestroyedCount",destroyed.size).put("allActivitiesDestroyed",true)
                        .put("serviceRemovedAndOnDestroyPostconditions",true).put("mainQueueBarrierAfterDestroy",true)
                }
                proof.put("ownedSampleRepositoryCreated",::samples.isInitialized).put("ownedTraceCreated",::logs.isInitialized)
                if (::samples.isInitialized) {
                    val writer=field(samples,"writer") as ExecutorService
                    writer.shutdown(); check(writer.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped sample writer did not terminate" }
                }
                if (::logs.isInitialized) { logs.close(); check(logs.awaitTermination(10,TimeUnit.SECONDS)) { "Scoped trace writer did not terminate" } }
                proof.put("scopedWritersTerminated",true)
                providerSession?.let { provider("cleanup"); providerSession=null; proof.put("ownedProviderSessionRemoved",true) }
                proof.put("clipboardIsolation",if(!clipboardCaptured) "NOT_TOUCHED" else if(clipboardRestored) "PASS_FOREGROUND_LOGICAL_RESTORE" else "UNKNOWN")
                if (installed) main {
                    if(ownedLegacy!=null) {
                        val original=requireNotNull(legacy["legacyCurves"])
                        write(app,"legacyCurves\$delegate",original.first)
                        check(field(app,"legacyCurves\$delegate")===original.first && !original.first.isInitialized())
                    }
                    saved.forEach { (name,lazy) -> write(app,name+"\$delegate",lazy) }
                    write(app,"languagePreferences",oldLanguage); write(app,"journalObservationFailure",oldFailure)
                    savedPrefs.forEach { (name,values) ->
                        val preferences = context.getSharedPreferences(name,Context.MODE_PRIVATE)
                        if(name=="presets" && preferences.all==values) return@forEach
                        val keys = when(name) {
                            "appearance" -> listOf("dark")
                            "curves" -> listOf("selected","sort")
                            else -> (1..5).map(PresetSlots::key)
                        }
                        val edit = preferences.edit()
                        keys.forEach { key ->
                            if (key !in values) edit.remove(key)
                            else when (val value = values[key]) {
                                is Boolean -> edit.putBoolean(key,value)
                                is String -> edit.putString(key,value)
                                is Int -> edit.putInt(key,value)
                                is Long -> edit.putLong(key,value)
                                is Float -> edit.putFloat(key,value)
                                is Set<*> -> {
                                    check(value.all { it is String }) { "Invalid string-set preference type" }
                                    @Suppress("UNCHECKED_CAST")
                                    edit.putStringSet(key,value as Set<String>)
                                }
                                else -> error("Unsupported original whitelist preference type: $name/$key")
                            }
                        }
                        check(edit.commit()) { "Cannot restore whitelist preference keys: $name/$keys" }
                    }
                    app.unregisterActivityLifecycleCallbacks(lifecycle)
                    saved.forEach { (name,lazy) -> check(field(app,name+"\$delegate") === lazy && lazy.isInitialized()==originalInitialized.getValue(name)) }
                    legacy.forEach { (name,pair) -> check(field(app,name+"\$delegate") === pair.first && pair.first.isInitialized()==pair.second) }
                    check(field(app,"languagePreferences") === oldLanguage && field(app,"journalObservationFailure") === oldFailure)
                    savedPrefs.forEach { (name,values) -> check(context.getSharedPreferences(name,Context.MODE_PRIVATE).all==values) }
                    val after = businessDisk(specialLibrary)
                    proof.put("businessDiskBefore",JSONObject(diskBefore)).put("businessDiskAfter",JSONObject(after))
                    check(after==diskBefore) { "Original business disk changed; no rollback attempted" }
                    proof.put("originalLazyIdentityAndInitializedRestored",true).put("legacyUnchanged",true)
                        .put("typedPreferencesRestored",true).put("businessDiskByteUnchanged",true)
                        .put("whitePrefFilesBefore",JSONObject(whitePrefFileBefore)).put("whitePrefFilesAfter",JSONObject(whitePrefFiles()))
                        .put("whitePrefPhysicalBytesMayDiffer",if(specialLibrary)
                            "appearance.dark/curves.selected/curves.sort/presets.slot_1..slot_5 typed restoration; no file deletion or byte rollback"
                            else "only appearance.dark/curves.selected/curves.sort typed restoration; presets bytes part of strict businessDisk")
                }
                proof.put("status","PASS_ISOLATION_ONLY")
            } catch (failure: Throwable) {
                proof.put("status","FAIL").put("failure",failure.stackTraceToString())
                // Do not restore original delegates while a scoped Activity/service/writer may still hold references.
                throw failure
            } finally { File(directory,"fixture-isolation.json").writeText(proof.toString(2)) }
        }
    }
    private fun geometry(activity: Activity): JSONObject {
        val content=root(activity); val origin=IntArray(2); content.getLocationOnScreen(origin)
        val insets=requireNotNull(content.rootWindowInsets)
        val bars=insets.getInsets(WindowInsets.Type.systemBars())
        val config=activity.resources.configuration
        val budget=when(activity) {
            is CurveActivity -> JSONObject(activity.productLayoutEvidence.toString())
            is CurveImportPreviewActivity -> JSONObject(activity.productLayoutEvidence.toString())
            else -> JSONObject().put("status","unknown").put("reason","Activity_has_no_existing_natural_budget_interface; actual_View_geometry_recorded")
        }
        val restoreEvidence=when(activity) {
            is CurveImportActivity -> JSONObject(activity.productRestoreEvidence.toString())
            else -> JSONObject().put("status","unknown").put("reason","Activity_has_no_productRestoreEvidence_interface")
        }
        val actualRestoreState=JSONObject().put("imeVisible",insets.isVisible(WindowInsets.Type.ime()))
            .put("hasWindowFocus",activity.hasWindowFocus()).put("focusedViewTag",activity.currentFocus?.tag ?: JSONObject.NULL)
            .put("focusedViewClass",activity.currentFocus?.javaClass?.name ?: JSONObject.NULL)
            .put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("scrolls",JSONArray(nodes(content).filter { it is ScrollView || it is EditText }.map { view ->
                JSONObject().put("semanticId",view.tag ?: JSONObject.NULL).put("x",view.scrollX).put("y",view.scrollY)
            }))
        return JSONObject().put("naturalBudgetActualPx",budget)
            .put("productRestoreEvidence",restoreEvidence)
            .put("productRestoreEvidenceSemantics","deep_copy_of_page_self_report; not_OS_confirmation; actualRestoreState_is_separate_current_View_and_WindowInsets_observation")
            .put("actualRestoreState",actualRestoreState)
            .put("resourcesDisplayMetricsPx",JSONArray(listOf(activity.resources.displayMetrics.widthPixels,activity.resources.displayMetrics.heightPixels)))
            .put("windowMetricsBoundsPx",rect(activity.windowManager.currentWindowMetrics.bounds))
            .put("windowMetricsSemantics","Android_window_metrics_not_physical_panel; original_PNG_header_recorded_separately")
            .put("appOriginPx",JSONArray(origin.toList())).put("appSizePx",JSONArray(listOf(content.width,content.height)))
            .put("systemInsetsPx",JSONArray(listOf(bars.left,bars.top,bars.right,bars.bottom)))
            .put("imeVisible",insets.isVisible(WindowInsets.Type.ime())).put("densityDpi",config.densityDpi)
            .put("fontScale",config.fontScale).put("locale",config.locales.toLanguageTags())
            .put("uiModeNightMeaning",when(config.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) {
                android.content.res.Configuration.UI_MODE_NIGHT_YES -> "dark"; android.content.res.Configuration.UI_MODE_NIGHT_NO -> "light"; else -> "undefined"
            }).put("nodes",JSONArray(nodes(content).filter { it.tag is String }.map { observation(it) }))
    }
    private fun baselines(activity: Activity): JSONArray = JSONArray((0..1).map { index ->
        val prefix=if(activity is CurveActivity) "product/curve-library/summary/$index" else "product/import-preview/metric-$index"
        val number=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/number"))
        val unit=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/unit"))
        val a=IntArray(2); val b=IntArray(2); number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
        val delta=a[1]+number.baseline-b[1]-unit.baseline
        JSONObject().put("numberId",number.tag).put("unitId",unit.tag).put("deltaPx",delta)
            .put("numberBaselineScreenPx",a[1]+number.baseline).put("unitBaselineScreenPx",b[1]+unit.baseline)
            .put("status",if(number.isShown && unit.isShown && number.baseline>=0 && unit.baseline>=0 && delta==0)
                "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
    })
    private fun librarySpecialBaselines(activity: Activity, fixture: JSONObject, fallbackText: String): JSONArray =
        JSONArray((0..1).map { index ->
            val prefix="product/curve-library/summary/$index"
            val number=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/number"))
            val unit=requireNotNull(root(activity).findViewWithTag<TextView>("$prefix/unit"))
            val modelNull=fixture.getJSONArray("metricUnits").isNull(index)
            val a=IntArray(2); val b=IntArray(2); number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
            val delta=a[1]+number.baseline-b[1]-unit.baseline
            val status=when {
                fallbackText.isNotBlank() && !number.isShown && !unit.isShown -> "NOT_APPLICABLE_SHORT_FALLBACK"
                modelNull && unit.visibility==View.GONE && number.isShown -> "NOT_APPLICABLE_MODEL_NULL_UNIT"
                number.isShown && unit.isShown && number.baseline>=0 && unit.baseline>=0 && delta==0 ->
                    "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE"
                else -> "FAIL"
            }
            JSONObject().put("index",index).put("numberId",number.tag).put("unitId",unit.tag)
                .put("modelUnit",fixture.getJSONArray("metricUnits").opt(index))
                .put("numberVisibility",number.visibility).put("unitVisibility",unit.visibility)
                .put("numberShown",number.isShown).put("unitShown",unit.isShown)
                .put("deltaPx",delta).put("numberBaselineScreenPx",a[1]+number.baseline)
                .put("unitBaselineScreenPx",b[1]+unit.baseline).put("status",status)
        })
    private fun list(activity: Activity)=field(activity,"curveList") as ListView
    private fun listState(activity: Activity): JSONObject {
        val view=list(activity)
        check(view.childCount>0 && view.count>0)
        return JSONObject().put("position",view.firstVisiblePosition).put("offsetPx",view.getChildAt(0).top)
            .put("firstItemId",view.getItemAtPosition(view.firstVisiblePosition).toString()).put("count",view.count)
            .put("query",(field(activity,"search") as EditText).text.toString()).put("category",field(activity,"category").toString())
            .put("sort",field(activity,"sortMode").toString()).put("selectedId",(field(activity,"selected") as? CurveLibraryItem)?.id ?: JSONObject.NULL)
    }
    private fun stableList(activity: Activity, directory: File, phase: String): JSONObject {
        val samples=JSONArray(); val deadline=SystemClock.elapsedRealtime()+5000
        var previous: String?=null; var consecutive=0
        try {
            while(SystemClock.elapsedRealtime()<deadline) {
                frames.committedFrame(root(activity))
                lateinit var value: JSONObject; var pending=true
                main { value=listState(activity); pending=root(activity).isLayoutRequested || list(activity).isLayoutRequested }
                val signature=value.toString()
                consecutive=if(!pending && signature==previous) consecutive+1 else if(!pending) 1 else 0
                val within=SystemClock.elapsedRealtime()<=deadline
                samples.put(JSONObject().put("actualFrameIndex",samples.length()+1).put("state",value)
                    .put("pendingLayout",pending).put("consecutiveUnchanged",consecutive).put("withinBudget",within))
                previous=signature
                if(consecutive>=3 && within) return value
            }
            error("M3 $phase list did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-list-stability.json").writeText(JSONObject().put("samples",samples)
            .put("policy","actual_committed_frames; stability_not_waiting_for_expected_anchor_match").toString(2)) }
    }
    private fun gesture(view: View) {
        val visible=Rect()
        main { check(view.isShown && view.getGlobalVisibleRect(visible) && visible.width()>32 && visible.height()>64) }
        val x=visible.exactCenterX(); val start=visible.top+visible.height()*.82f; val end=visible.top+visible.height()*.22f
        val down=SystemClock.uptimeMillis()
        fun event(action: Int,y: Float) {
            val value=android.view.MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x,y,0).apply { source=android.view.InputDevice.SOURCE_TOUCHSCREEN }
            try { check(test.uiAutomation.injectInputEvent(value,true)) { "M3 real local list gesture rejected" } } finally { value.recycle() }
        }
        event(android.view.MotionEvent.ACTION_DOWN,start)
        for(step in 1..16) { SystemClock.sleep(25); event(android.view.MotionEvent.ACTION_MOVE,start+(end-start)*step/16) }
        // Real stationary MOVE events remove fling velocity before UP; delays are gesture timing, not acceptance waits.
        repeat(4) { SystemClock.sleep(50); event(android.view.MotionEvent.ACTION_MOVE,end) }
        event(android.view.MotionEvent.ACTION_UP,end)
    }
    private fun invariant(scope: Scope): String = JSONObject().put("inventoryEvents",JSONArray(scope.inventory.events().map { it.toString() }))
        .put("preparation",scope.preparation.current()?.toString() ?: JSONObject.NULL).put("journal",JSONObject(scope.journal.exportJson()))
        .put("history",JSONArray(scope.history.entries.map { it.toString() }))
        .put("drafts",JSONArray(scope.custom.list().map { it.toJson() }))
        .put("usage",JSONArray(scope.curves.items.map { JSONObject().put("id",it.id).put("stats",scope.usage.stats(it.id).toString()) })).toString()
    private fun captureState(activity: Activity, directory: File, phase: String) {
        val folder=File(directory,"states/$phase").apply { check(isDirectory || mkdirs()) }
        frames.screenshot(activity,root(activity),File(folder,"window.png")) { geometry(activity) }
        main { File(folder,"geometry.json").writeText(geometry(activity).toString(2)); TaskTypographyEvidence.capture(activity,folder) }
    }
    private fun libraryCapture(activity: Activity, folder: File, short: Boolean=false, observe: () -> JSONObject): JSONObject {
        check(folder.isDirectory || folder.mkdirs())
        frames.committedFrame(root(activity))
        val frame=if(short) frames.screenshotMeasuredShortActivity(activity,root(activity),File(folder,"window.png"),observe)
            else frames.screenshot(activity,root(activity),File(folder,"window.png"),observe)
        File(folder,"geometry.json").writeText(frame.target.toString(2))
        check(frame.proof.getInt("protocolVersion")==2 && frame.proof.getBoolean("accepted") && frame.proof.getInt("timeoutMs")==5000)
        return frame.target
    }
    private fun libraryCaseState(activity: Activity, scope: Scope): JSONObject {
        val window=activity.window
        return JSONObject().put("list",listState(activity))
            .put("detailRequested",(field(activity,"collection") as TaskCollectionLayout).detailRequested)
            .put("detailScrollY",(field(activity,"detailScroll") as ScrollView).scrollY)
            .put("fallbackScrollY",(field(activity,"previewFallbackScroll") as ScrollView).scrollY)
            .put("activityIdentity",System.identityHashCode(activity))
            .put("windowIdentity",System.identityHashCode(window))
            .put("decorIdentity",System.identityHashCode(window.decorView))
            .put("windowTokenIdentity",System.identityHashCode(requireNotNull(window.decorView.windowToken)))
            .put("windowLayout",JSONArray(listOf(window.attributes.width,window.attributes.height,
                window.attributes.gravity,window.attributes.x,window.attributes.y)))
            .put("curvesPreferences",prefMapJson(context.getSharedPreferences("curves",Context.MODE_PRIVATE).all))
            .put("ownedStoreSha256",shaText(invariant(scope)))
            .put("services",JSONArray(services()))
            .put("hasWindowFocus",activity.hasWindowFocus())
    }
    private fun libraryCaseGeometry(activity: Activity, scope: Scope): JSONObject =
        geometry(activity).put("libraryCaseState",libraryCaseState(activity,scope))
    private fun captureReference(scopeRoot:File, folder:File):JSONObject {
        fun relative(name:String)=File(folder,name).relativeTo(scopeRoot).invariantSeparatorsPath
        return JSONObject().put("image",relative("window.png")).put("imageSha256",sha(File(folder,"window.png")))
            .put("geometry",relative("geometry.json")).put("geometrySha256",sha(File(folder,"geometry.json")))
            .put("proof",relative("window-frame-sync.json")).put("proofSha256",sha(File(folder,"window-frame-sync.json")))
    }
    private data class SlotPopup(val root:View,val list:ListView,val token:IBinder,val displayId:Int,
        val choices:List<String>)
    private fun slotPopup(activity:Activity, list:ListView, expected:List<String>):SlotPopup {
        val inspectors=android.view.inspector.WindowInspector.getGlobalWindowViews()
        val ancestors=generateSequence(list as View) { it.parent as? View }.toList()
        val roots=inspectors.filter { candidate->ancestors.any { it===candidate } }
        check(roots.size==1) { "M3 original slot menu has no unique Inspector root" }
        val popup=roots.single();val token=requireNotNull(popup.windowToken)
        check(popup!==activity.window.decorView && popup.isAttachedToWindow &&
            popup.javaClass.name=="android.widget.PopupWindow\$PopupDecorView" &&
            list.rootView===popup && list.isAttachedToWindow && list.windowToken===token &&
            list.display?.displayId==popup.display?.displayId &&
            (0 until list.adapter.count).map {list.adapter.getItem(it).toString()}==expected) {
            "M3 original slot menu root/token/items changed" }
        return SlotPopup(popup,list,token,requireNotNull(popup.display).displayId,expected)
    }
    private fun slotRootState(view:View):JSONObject=JSONObject()
        .put("class",view.javaClass.name).put("identity",System.identityHashCode(view))
        .put("tokenIdentity",view.windowToken?.let {System.identityHashCode(it)} ?: JSONObject.NULL)
        .put("attached",view.isAttachedToWindow).put("laidOut",view.isLaidOut)
        .put("layoutRequested",view.isLayoutRequested).put("focused",view.hasWindowFocus())
        .put("displayId",view.display?.displayId ?: JSONObject.NULL)
    private fun slotPopupState(popup:SlotPopup):JSONObject=slotRootState(popup.root)
        .put("listIdentity",System.identityHashCode(popup.list))
        .put("listTokenIdentity",System.identityHashCode(popup.token))
        .put("choices",JSONArray(popup.choices))
    private fun libraryWindowDialog(activity: Activity, popup:SlotPopup?=null,
        observe:((JSONObject)->Unit)?=null): AlertDialog? {
        val roots=android.view.inspector.WindowInspector.getGlobalWindowViews().filter {
            it!==activity.window.decorView && it.isAttachedToWindow }
        val state=JSONObject().put("foreignRoots",JSONArray(roots.map(::slotRootState)))
            .put("originalPopup",popup?.let(::slotPopupState) ?: JSONObject.NULL)
        fun decision(value:String,reason:String,dialog:AlertDialog?=null):AlertDialog? {
            state.put("decision",value).put("reason",reason)
            dialog?.let { owner->val window=requireNotNull(owner.window);val decor=window.decorView
                val list=owner.listView
                state.put("dialog",slotRootState(decor).put("windowIdentity",System.identityHashCode(window))
                    .put("ownerActivityIdentity",System.identityHashCode(activity))
                    .put("listIdentity",list?.let {System.identityHashCode(it)} ?: JSONObject.NULL)
                    .put("listAttached",list?.isAttachedToWindow ?: false)
                    .put("listLaidOut",list?.isLaidOut ?: false)
                    .put("listRootIdentity",list?.rootView?.let {System.identityHashCode(it)} ?: JSONObject.NULL)
                    .put("listTokenIdentity",list?.windowToken?.let {System.identityHashCode(it)} ?: JSONObject.NULL)
                    .put("listDisplayId",list?.display?.displayId ?: JSONObject.NULL)) }
            observe?.invoke(state);return if(value=="READY") dialog else null
        }
        try {
            if(popup!=null && popup.root.windowToken!=null && popup.root.windowToken!==popup.token)
                error("M3 original slot Popup token changed")
            if(popup!=null && popup.root.display!=null && popup.root.display?.displayId!=popup.displayId)
                error("M3 original slot Popup display changed")
            if(popup!=null && popup.root.isAttachedToWindow && roots.none {it===popup.root})
                error("M3 original slot Popup missing from Inspector")
            val dialogs=ArrayList<AlertDialog>()
            var popupPresent=false
            for(decor in roots) {
                if(popup!=null && decor===popup.root) {
                    check(decor.javaClass.name=="android.widget.PopupWindow\$PopupDecorView" &&
                        decor.windowToken===popup.token && decor.display?.displayId==popup.displayId &&
                        popup.list.rootView===decor && popup.list.windowToken===popup.token) {
                        "M3 original slot Popup identity/token changed" }
                    popupPresent=true;continue
                }
                check(decor.javaClass.name=="com.android.internal.policy.DecorView") {
                    "M3 unknown foreign library Window ${decor.javaClass.name}" }
                val window=decor.javaClass.getDeclaredField("mWindow").apply { isAccessible=true }
                    .get(decor) as android.view.Window
                val dialog=window.callback as? AlertDialog ?: error("M3 foreign Window is not AlertDialog")
                var owner:Context=dialog.context
                while(owner is ContextWrapper && owner!==activity) owner=owner.baseContext
                check(window.decorView===decor && dialog.window===window && dialog.isShowing &&
                    (dialog.ownerActivity===activity || owner===activity) &&
                    decor.windowToken!=null && decor.display?.displayId==activity.window.decorView.display?.displayId) {
                    "M3 details Window not owned by Library" }
                dialogs.add(dialog)
            }
            check(dialogs.size<=1) { "M3 ambiguous real library Dialog Windows" }
            val dialog=dialogs.singleOrNull() ?: return decision("PENDING","no_dialog" + if(popupPresent) "_original_popup" else "")
            val decor=requireNotNull(dialog.window).decorView
            if(!decor.isLaidOut || !decor.hasWindowFocus() || (popup!=null && decor.isLayoutRequested))
                return decision("PENDING","dialog_not_ready",dialog)
            if(popup==null) return decision("READY","single_real_dialog",dialog)
            val list=dialog.listView
            if(list==null || !list.isAttachedToWindow || !list.isLaidOut)
                return decision("PENDING","dialog_list_not_ready",dialog)
            check(list.rootView===decor && list.windowToken===decor.windowToken &&
                list.display?.displayId==decor.display?.displayId) { "M3 Dialog list root/token/display mismatch" }
            if(popupPresent && popup.root.hasWindowFocus())
                return decision("PENDING","original_popup_still_focused",dialog)
            return decision("READY",if(popupPresent) "original_popup_defocused" else "original_popup_absent",dialog)
        }catch(error:Throwable) {
            state.put("decision","FAIL").put("reason",error.javaClass.name+":"+error.message)
            try {observe?.invoke(state)}catch(recordFailure:Throwable){error.addSuppressed(recordFailure)}
            throw error
        }
    }
    private fun librarySelect(activity: Activity, scope: Scope, item: CurveLibraryItem): JSONObject {
        check(scope.curves.find(item.id)==item && item.id in scope.curves.items.map { it.id })
        main {
            if(field(activity,"category")!=CurveCategoryFilter.ALL) {
                val chip=nodes(root(activity)).filterIsInstance<TextView>().single {
                    it.isClickable && it.text.toString()==CurveCategoryFilter.ALL.label(activity)
                }
                check(chip.performClick())
            }
            (field(activity,"search") as EditText).setText(item.id)
        }
        await("real Library result for ${item.id}") { list(activity).count==1 && list(activity).childCount==1 }
        main {
            val view=list(activity); val row=view.getChildAt(0)
            check(row.isShown && row.isAttachedToWindow && view.adapter.getItem(0)==item.id)
            check(view.performItemClick(row,0,view.adapter.getItemId(0)))
            check((field(activity,"selected") as CurveLibraryItem)==item)
        }
        return JSONObject().put("searchQuery",item.id).put("adapterItemId",list(activity).adapter.getItem(0))
            .put("actualVisibleChildIndex",0).put("selectedId",item.id)
            .put("list",listState(activity))
    }
    private fun libraryFixture(item: CurveLibraryItem, scope: Scope,
        displayContext: Context = AppLanguageContext.wrap(context,app.languagePreferences.current)): JSONObject {
        val copyable=runCatching { CustomCurveDocument.fromLibraryItem(item) }.getOrNull()
        val weight=item.customDocument?.targetHundredthsGram ?: item.factoryCurve?.weightTenthsGram?.times(10)
            ?: item.controlProfile?.targetHundredthsGram
        val temperature=item.customDocument?.temperatureC ?: item.factoryCurve?.temperatureC ?: item.controlProfile?.temperatureC
        val stages=item.customDocument?.stages?.size ?: item.factoryCurve?.segmentCount ?: item.controlProfile?.parameters?.segmentCount
        val values=listOf(temperature?.toString() ?: "—",weight?.let { if(it==0) displayContext.getString(R.string.curve_details_no_weight)
            else String.format(java.util.Locale.getDefault(),"%.2f",it/100.0) } ?: "—",stages?.toString() ?: "—")
        return JSONObject().put("id",item.id).put("name",item.name).put("category",item.category)
            .put("source",when { item.factoryCurve!=null -> "factory"; item.controlProfile!=null -> "captured";
                item.customDocument!=null -> "owned-draft"; else -> "unknown" })
            .put("modelDetails",item.details).put("factoryModel",item.factoryCurve?.toString() ?: JSONObject.NULL)
            .put("capturedModel",item.controlProfile?.toString() ?: JSONObject.NULL)
            .put("customDocument",item.customDocument?.toJson() ?: JSONObject.NULL)
            .put("targets",JSONArray(CurveStageParameters.pressureTargets(item)))
            .put("metricUnits",JSONArray(listOf("°C",if(weight==null || weight==0) JSONObject.NULL else "g",JSONObject.NULL)))
            .put("metricValues",JSONArray(values))
            .put("canStart",scope.curves.canStart(item)).put("copyable",copyable!=null)
            .put("slotEligible",item.factoryCurve!=null && scope.curves.canStart(item))
    }
    private fun libraryDisclosureState(activity: Activity, item: CurveLibraryItem, scope: Scope,
        diagnosticFile: File? = null): JSONObject {
        val page=geometry(activity)
        val required=JSONObject()
        val tags=listOf("product/curve-library/full-parameters","product/curve-library/use")+
            (0..2).flatMap { index -> listOf("product/curve-library/summary/$index/label",
                "product/curve-library/summary/$index/number") }+
            (0..1).map { "product/curve-library/summary/$it/unit" }
        tags.forEach { tag ->
            val view=requireNotNull(root(activity).findViewWithTag<View>(tag)) { "M3 missing common target $tag" }
            required.put(tag,observation(view))
        }
        val title=field(activity,"detailTitle") as TextView
        val source=field(activity,"detailCategory") as TextView
        val availability=field(activity,"availability") as TextView
        val details=field(activity,"details") as TextView
        val chart=field(activity,"stageChart") as CurveStageView
        val fallback=field(activity,"previewFallback") as TextView
        val fallbackScroll=field(activity,"previewFallbackScroll") as ScrollView
        val fallbackLines=JSONArray()
        if(fallback.isShown) {
            val layout=requireNotNull(fallback.layout); val mapped=visibility(fallbackScroll)
            check(mapped.getBoolean("screenMappingVerified")); val viewport=rect(mapped.getJSONArray("visibleRectPx"))
            val location=IntArray(2); fallback.getLocationOnScreen(location)
            val originX=location[0]+fallback.compoundPaddingLeft-fallback.scrollX
            val originY=location[1]+fallback.baseline-layout.getLineBaseline(0)-fallback.scrollY
            for(index in 0 until layout.lineCount) {
                val line=Rect(kotlin.math.floor(originX+layout.getLineLeft(index)).toInt(),originY+layout.getLineTop(index),
                    kotlin.math.ceil(originX+layout.getLineRight(index)).toInt(),originY+layout.getLineBottom(index))
                val text=fallback.text.toString().substring(layout.getLineStart(index),layout.getLineEnd(index))
                val horizontal=if(text.isBlank()) line.left>=viewport.left && line.left<=viewport.right
                    else line.width()>0 && line.left>=viewport.left && line.right<=viewport.right
                fallbackLines.put(JSONObject().put("index",index).put("start",layout.getLineStart(index))
                    .put("end",layout.getLineEnd(index)).put("text",text)
                    .put("rectScreenPx",rect(line)).put("ellipsisCount",layout.getEllipsisCount(index))
                    .put("fullyVisible",horizontal && line.height()>0 && line.top>=viewport.top &&
                        line.bottom<=viewport.bottom && layout.getEllipsisCount(index)==0))
            }
        }
        val targets=CurveStageParameters.pressureTargets(item)
        val fixture=libraryFixture(item,scope,activity)
        val expectedLabels=listOf(R.string.curve_preview_temperature,R.string.curve_preview_weight,R.string.curve_preview_stages)
        val summaryRows=(0..2).map { index ->
            val label=requireNotNull(root(activity).findViewWithTag<TextView>("product/curve-library/summary/$index/label"))
            val number=requireNotNull(root(activity).findViewWithTag<TextView>("product/curve-library/summary/$index/number"))
            JSONObject().put("index",index).put("actualLabel",label.text.toString())
                .put("expectedLabel",activity.getString(expectedLabels[index]))
                .put("actualNumber",number.text.toString())
                .put("expectedNumber",fixture.getJSONArray("metricValues").getString(index))
        }
        diagnosticFile?.writeText(JSONObject().put("itemId",item.id)
            .put("selectedId",(field(activity,"selected") as? CurveLibraryItem)?.id ?: JSONObject.NULL)
            .put("activityLocale",activity.resources.configuration.locales[0].toLanguageTag())
            .put("targetContextLocale",context.resources.configuration.locales[0].toLanguageTag())
            .put("rows",JSONArray(summaryRows)).toString(2))
        for(index in 0..2) {
            val row=summaryRows[index]
            check(row.getString("actualLabel")==row.getString("expectedLabel") &&
                row.getString("actualNumber")==row.getString("expectedNumber"))
            if(index<2) {
                val unit=requireNotNull(root(activity).findViewWithTag<TextView>("product/curve-library/summary/$index/unit"))
                check(if(fixture.getJSONArray("metricUnits").isNull(index)) unit.visibility==View.GONE
                    else unit.visibility==View.VISIBLE && unit.text.toString()==fixture.getJSONArray("metricUnits").getString(index))
            }
        }
        val expectedSource=if(item.customDocument!=null) activity.getString(R.string.profiles_category)
            else CurveCategoryFilter.display(activity,item.category)
        val expectedAvailability=if(item.customDocument!=null) activity.getString(if(scope.curves.canStart(item))
            R.string.profiles_pressure_compatible else R.string.profiles_read_only)
            else activity.getString(if(scope.curves.canStart(item)) R.string.curve_verified else R.string.curve_unavailable)
        val expectedDetails=if(item.customDocument!=null) (activity as ThemedActivity).profileSummary(item.customDocument)
            else CurveDetailsText(activity).render(item,scope.curves.canStart(item))
        check(title.text.toString()==item.name && chart.targets==targets &&
            source.text.toString()==expectedSource && availability.text.toString()==expectedAvailability &&
            details.text.toString()==expectedDetails && (field(activity,"selected") as CurveLibraryItem)==item)
        check((field(activity,"select") as Button).isEnabled==scope.curves.canStart(item))
        check((field(activity,"assignPreset") as Button).isEnabled==(item.factoryCurve!=null && scope.curves.canStart(item)))
        check(!chart.isShown || targets.isNotEmpty()) { "M3 fake pressure path" }
        return page.put("libraryDisclosure",JSONObject().put("itemId",item.id).put("title",observation(title))
            .put("source",observation(source)).put("availability",observation(availability))
            .put("details",details.text.toString()).put("common",required)
            .put("metricValues",fixture.getJSONArray("metricValues")).put("metricUnits",fixture.getJSONArray("metricUnits"))
            .put("actualChartTargets",JSONArray(chart.targets)).put("chartShown",chart.isShown)
            .put("actualDrawOperations",chart.productDrawEvidence)
            .put("fallback",observation(fallback)).put("fallbackText",fallback.text.toString())
            .put("fallbackLines",fallbackLines)
            .put("selectEnabled",(field(activity,"select") as Button).isEnabled)
            .put("editEnabled",(field(activity,"editDraft") as Button).isEnabled)
            .put("shareEnabled",(field(activity,"shareParameters") as Button).isEnabled)
            .put("assignVisible",(field(activity,"assignPreset") as Button).visibility==View.VISIBLE)
            .put("assignEnabled",(field(activity,"assignPreset") as Button).isEnabled)
            .put("detailRequested",(field(activity,"collection") as TaskCollectionLayout).detailRequested))
    }
    private fun libraryDetails(activity: Activity, item: CurveLibraryItem, scope: Scope, folder: File): JSONObject {
        val selectedBefore=field(activity,"selected") as CurveLibraryItem
        val listBefore=listState(activity).toString()
        val details=field(activity,"details") as TextView
        val availability=field(activity,"availability") as TextView
        val expected=item.name+"\n\n"+details.text.toString()+"\n\n"+availability.text.toString()
        val formatter=if(item.customDocument!=null) (activity as ThemedActivity).profileSummary(item.customDocument)
            else CurveDetailsText(activity).render(item,scope.curves.canStart(item))
        check(details.text.toString()==formatter && selectedBefore==item)
        main { val button=field(activity,"parameterShortcut") as Button
            check(button.isShown && button.isEnabled && button.performClick()) }
        var dialog:AlertDialog?=null
        await("original Library parameter AlertDialog") { dialog=libraryWindowDialog(activity); dialog!=null }
        val owner=requireNotNull(dialog); val window=requireNotNull(owner.window); val decor=window.decorView
        val message=requireNotNull(owner.findViewById<TextView>(android.R.id.message))
        val positive=requireNotNull(owner.getButton(AlertDialog.BUTTON_POSITIVE))
        val scroll=nodes(decor).filterIsInstance<ScrollView>().single { nodes(it).any { node -> node===message } }
        val originalX=scroll.scrollX; val originalY=scroll.scrollY
        val report=JSONObject().put("itemId",item.id).put("expectedMessage",expected)
            .put("modelFormatter",formatter).put("originalScroll",JSONArray(listOf(originalX,originalY)))
            .put("windowIdentity",System.identityHashCode(window)).put("decorIdentity",System.identityHashCode(decor))
            .put("windowTokenIdentity",System.identityHashCode(requireNotNull(decor.windowToken)))
            .put("ownerActivityIdentity",System.identityHashCode(activity))
            .put("visualAcceptance","ASTRA_PENDING")
        val steps=JSONArray(); val covered=linkedSetOf<Int>(); var primary:Throwable?=null
        fun state():JSONObject {
            check(libraryWindowDialog(activity)===owner && message.text.toString()==expected &&
                positive.text.toString()==activity.getString(android.R.string.ok))
            val layout=requireNotNull(message.layout); check(layout.lineCount>0)
            val mapped=visibility(scroll)
            check(mapped.getBoolean("screenMappingVerified")); val visible=rect(mapped.getJSONArray("visibleRectPx"))
            val location=IntArray(2); message.getLocationOnScreen(location)
            val originX=location[0]+message.compoundPaddingLeft-message.scrollX
            val originY=location[1]+message.baseline-layout.getLineBaseline(0)-message.scrollY
            val lines=JSONArray()
            for(index in 0 until layout.lineCount) {
                val line=Rect(kotlin.math.floor(originX+layout.getLineLeft(index)).toInt(),originY+layout.getLineTop(index),
                    kotlin.math.ceil(originX+layout.getLineRight(index)).toInt(),originY+layout.getLineBottom(index))
                val text=expected.substring(layout.getLineStart(index),layout.getLineEnd(index))
                val horizontal=if(text.isBlank()) line.left>=visible.left && line.left<=visible.right
                    else line.width()>0 && line.left>=visible.left && line.right<=visible.right
                val readable=horizontal && line.height()>0 && line.top>=visible.top && line.bottom<=visible.bottom &&
                    layout.getEllipsisCount(index)==0
                lines.put(JSONObject().put("index",index).put("start",layout.getLineStart(index)).put("end",layout.getLineEnd(index))
                    .put("text",text)
                    .put("rectScreenPx",rect(line)).put("fullyVisible",readable).put("ellipsisCount",layout.getEllipsisCount(index)))
            }
            val safe=Rect(); decor.getWindowVisibleDisplayFrame(safe)
            val title=requireNotNull(nodes(decor).filterIsInstance<TextView>().singleOrNull { it.text.toString()==item.name && it!==message })
            check(whole(observation(title)) && whole(observation(positive)) && positive.width>=HoyiUi.dp(activity,48) &&
                positive.height>=HoyiUi.dp(activity,48)) { "M3 details title/confirm not fully readable" }
            return JSONObject().put("windowIdentity",System.identityHashCode(window)).put("decorIdentity",System.identityHashCode(decor))
                .put("windowTokenIdentity",System.identityHashCode(requireNotNull(decor.windowToken)))
                .put("messageText",message.text.toString()).put("title",observation(title)).put("positive",observation(positive))
                .put("scroll",JSONArray(listOf(scroll.scrollX,scroll.scrollY))).put("scrollVisibleRectScreenPx",rect(visible))
                .put("safeRectScreenPx",rect(safe)).put("layoutOriginScreenPx",JSONArray(listOf(originX,originY)))
                .put("layoutWidthPx",layout.width).put("contentWidthPx",message.width-message.compoundPaddingLeft-message.compoundPaddingRight)
                .put("messageSelfScroll",JSONArray(listOf(message.scrollX,message.scrollY)))
                .put("lines",lines).put("lineCount",layout.lineCount)
        }
        fun capture(name:String):JSONObject {
            val step=File(folder,"details/$name").apply { check(isDirectory || mkdirs()) }
            frames.committedFrame(decor)
            val frame=frames.screenshotMeasuredWindowBufferTranslation(window,decor,File(step,"window.png")) { state() }
            File(step,"geometry.json").writeText(frame.target.toString(2))
            val geometry=frame.target; val lines=geometry.getJSONArray("lines")
            val indices=(0 until lines.length()).filter { lines.getJSONObject(it).getBoolean("fullyVisible") }
            covered.addAll(indices)
            steps.put(JSONObject().put("name",name).put("directory","details/$name")
                .put("windowSha256",sha(File(step,"window.png"))).put("geometrySha256",sha(File(step,"geometry.json")))
                .put("proofSha256",sha(File(step,"window-frame-sync.json"))).put("coveredLines",JSONArray(indices)))
            return geometry
        }
        try {
            val first=capture("initial"); val firstLines=first.getJSONArray("lines"); val count=first.getInt("lineCount")
            for(index in 0 until count) {
                if(index in covered) continue
                main {
                    val layout=requireNotNull(message.layout)
                    check(layout.lineCount==count)
                    val y=message.baseline-layout.getLineBaseline(0)
                    message.requestRectangleOnScreen(Rect(0,y+layout.getLineTop(index),message.width,
                        y+layout.getLineBottom(index)),true)
                }
                val current=capture("segment-%03d".format(index))
                check(current.getInt("lineCount")==count && index in covered)
                val lines=current.getJSONArray("lines")
                check((0 until count).all { row ->
                    val before=firstLines.getJSONObject(row); val after=lines.getJSONObject(row)
                    before.getInt("start")==after.getInt("start") && before.getInt("end")==after.getInt("end") &&
                        after.getInt("ellipsisCount")==0
                })
            }
            check(covered==(0 until count).toSet()) { "M3 full Library parameter text not reachable" }
            report.put("lineCount",count).put("coveredLines",JSONArray(covered.sorted())).put("status","PENDING_RESTORE")
        } catch(error:Throwable) { primary=error; report.put("status","FAIL").put("failure",error.stackTraceToString()); throw error }
        finally {
            var restore:Throwable?=null
            try {
                main { scroll.scrollTo(originalX,originalY) }
                val restored=capture("restored")
                check(restored.getJSONArray("scroll").toString()==JSONArray(listOf(originalX,originalY)).toString() &&
                    field(activity,"selected")==selectedBefore && listState(activity).toString()==listBefore)
                report.put("restoredScroll",JSONArray(listOf(originalX,originalY)))
                if(primary==null) report.put("status","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
            } catch(error:Throwable) {
                report.put("status","FAIL").put("restoreFailure",error.stackTraceToString())
                if(primary!=null) primary?.addSuppressed(error) else restore=error
            } finally {
                report.put("steps",steps)
                try { File(folder,"details-report.json").writeText(report.toString(2)) }
                catch(error:Throwable) { if(primary!=null) primary?.addSuppressed(error)
                    else if(restore!=null) restore?.addSuppressed(error) else restore=error }
                try { main { check(positive.performClick()) } }
                catch(error:Throwable) { if(primary!=null) primary?.addSuppressed(error)
                    else if(restore!=null) restore?.addSuppressed(error) else restore=error }
            }
            restore?.let { throw it }
        }
        await("Library parameter Window closed") { activity.hasWindowFocus() }
        return report
    }
    private fun librarySpecialVisualCases(scope: Scope, directory: File): List<CurveLibraryItem> {
        val folder=File(directory,"special-states/library").apply { check(isDirectory || mkdirs()) }
        val original=scope.curves.items
        val factory=original.filter { it.factoryCurve!=null }
        val long=factory.maxWithOrNull(compareBy<CurveLibraryItem> {
            CurveDetailsText(context).render(it,scope.curves.canStart(it)).length
        }.thenBy { it.id }) ?: error("M3 no real factory")
        val captured=original.filter { it.controlProfile?.let(CurveCatalog::validated)==true }.minByOrNull { it.id }
            ?: error("M3 no validated captured curve")
        val noPressure=factory.filter { CurveStageParameters.pressureTargets(it).isEmpty() }.minByOrNull { it.id }
            ?: error("M3 factory has no real no-pressure source; fixture capability FAIL")
        val readonly=requireNotNull(scope.curves.find(requireNotNull(scope.readonly).id))
        val four=requireNotNull(scope.curves.find(scope.document.id))
        val cases=listOf("long-factory" to long,"captured" to captured,"no-pressure" to noPressure,
            "readonly-flow" to readonly,"four-stage-pressure" to four,"short-pressure" to four)
        check(cases.size==6 && original.size==105 && scope.baseLibraryIds.size==103)
        val frozen=invariant(scope); val prefs=context.getSharedPreferences("curves",Context.MODE_PRIVATE).all.toMap()
        cases.forEach { (name,item) ->
            val case=File(folder,name).apply { check(isDirectory || mkdirs()) }
            val fixture=libraryFixture(item,scope)
            if(name=="long-factory") {
                val full=CurveDetailsText(context).render(item,scope.curves.canStart(item))
                fixture.put("formatterFullTextSha256",shaText(full)).put("formatterFullTextLength",full.length)
                    .put("longSelectionRule","max_CurveDetailsText_render_length_then_id")
            }
            File(case,"fixture.json").writeText(fixture.toString(2))
            var activity:Activity?=null; var short=false; var error:Throwable?=null
            val report=JSONObject().put("case",name).put("itemId",item.id).put("visualAcceptance","ASTRA_PENDING")
            try {
                val page=start(CurveActivity::class.java); activity=page
                unfocus(page)
                val startupFolder=File(case,"startup")
                val startup=libraryCapture(page,startupFolder) { libraryCaseGeometry(page,scope) }
                val startupState=startup.getJSONObject("libraryCaseState")
                check(!startupState.getBoolean("detailRequested") && startupState.getJSONObject("list").getString("selectedId")!="")
                report.put("startupCapture",captureReference(directory,startupFolder)).put("startupState",startupState)
                val originalItem=requireNotNull(scope.curves.find(startupState.getJSONObject("list").getString("selectedId")))
                val originalSelection=librarySelect(page,scope,originalItem)
                stableList(page,case,"prepared-baseline")
                val preparedFolder=File(case,"prepared")
                val prepared=libraryCapture(page,preparedFolder) { libraryCaseGeometry(page,scope) }
                val preparedState=prepared.getJSONObject("libraryCaseState")
                check(preparedState.getBoolean("detailRequested") &&
                    preparedState.getJSONObject("list").getString("selectedId")==originalItem.id &&
                    preparedState.getJSONObject("list").getString("query")==originalItem.id)
                report.put("originalSelection",originalSelection).put("preparedCapture",captureReference(directory,preparedFolder))
                    .put("preparedState",preparedState).put("baselineSemantics","real_list_click_after_startup; startup_false_preserved")
                val before=listState(page)
                val beforeDetail=(field(page,"collection") as TaskCollectionLayout).detailRequested
                val beforeScroll=(field(page,"detailScroll") as ScrollView).scrollY
                val beforeFallback=(field(page,"previewFallbackScroll") as ScrollView).scrollY
                check(before.toString()==preparedState.getJSONObject("list").toString() && beforeDetail &&
                    beforeScroll==preparedState.getInt("detailScrollY") && beforeFallback==preparedState.getInt("fallbackScrollY"))
                val choice=librarySelect(page,scope,item)
                if(name=="short-pressure") {
                    val old=page.window.attributes
                    report.put("originalWindowLayout",JSONArray(listOf(old.width,old.height)))
                    main { page.window.setLayout(-1,HoyiUi.dp(page,280)) }
                    short=true
                    await("real short Library Window measured") { root(page).isLaidOut && !root(page).isLayoutRequested }
                }
                val initial=libraryCapture(page,case,short) {
                    libraryDisclosureState(page,item,scope,File(case,"summary-assertion.json")) }
                report.put("initialCapture",captureReference(directory,case))
                val disclosure=initial.getJSONObject("libraryDisclosure")
                val targets=CurveStageParameters.pressureTargets(item)
                check(disclosure.getJSONArray("actualChartTargets").length()==targets.size)
                if(targets.isEmpty()) check(!disclosure.getBoolean("chartShown") &&
                    disclosure.getString("fallbackText").contains(page.getString(R.string.task_curve_library_no_pressure)))
                else check(disclosure.getBoolean("chartShown") || disclosure.getString("fallbackText").isNotBlank())
                if(name=="readonly-flow") check(!disclosure.getBoolean("selectEnabled") && !disclosure.getBoolean("editEnabled") &&
                    !disclosure.getBoolean("assignEnabled") && !disclosure.getBoolean("chartShown") &&
                    disclosure.getJSONObject("availability").getJSONArray("textLineBoxes").toString().contains(page.getString(R.string.profiles_read_only)))
                File(case,"canvas.json").writeText(JSONObject().put("actualTargets",JSONArray(targets))
                    .put("chartShown",disclosure.getBoolean("chartShown"))
                    .put("drawOperations",disclosure.getJSONArray("actualDrawOperations"))
                    .put("noPressureMeansNoFakePath",targets.isNotEmpty() || !disclosure.getBoolean("chartShown")).toString(2))
                main { File(case,"baseline-alignment.json").writeText(
                    librarySpecialBaselines(page,libraryFixture(item,scope),disclosure.getString("fallbackText")).toString(2)) }
                main { TaskTypographyEvidence.capture(page,case) }
                val reachable=JSONObject(); val common=disclosure.getJSONObject("common")
                val keys=common.keys().asSequence().toList()+listOf("title","source","availability")
                keys.forEach { key ->
                    val proof=if(common.has(key)) common.getJSONObject(key) else disclosure.getJSONObject(key)
                    if(whole(proof)) reachable.put(key,JSONObject().put("mode","initial").put("geometry","geometry.json"))
                    else {
                        val view=if(key=="title") field(page,"detailTitle") as View
                            else if(key=="source") field(page,"detailCategory") as View
                            else if(key=="availability") field(page,"availability") as View
                            else requireNotNull(root(page).findViewWithTag<View>(key))
                        if(view.isShown) {
                            main { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                            val segment="reachable/"+key.replace('/','-')
                            val sampled=libraryCapture(page,File(case,segment),short) { libraryDisclosureState(page,item,scope) }
                            val observed=sampled.getJSONObject("libraryDisclosure")
                            val target=if(observed.getJSONObject("common").has(key)) observed.getJSONObject("common").getJSONObject(key)
                                else observed.getJSONObject(key)
                            check(whole(target)) { "M3 Library $name target $key not reachable" }
                            reachable.put(key,JSONObject().put("mode","reachable")
                                .put("capture",captureReference(directory,File(case,segment))))
                        } else {
                            if(key=="product/curve-library/summary/1/unit" &&
                                libraryFixture(item,scope).getJSONArray("metricUnits").isNull(1)) {
                                check(view.visibility==View.GONE)
                                reachable.put(key,JSONObject().put("mode","model-null-unit").put("expected",JSONObject.NULL))
                                return@forEach
                            }
                            check(name=="short-pressure" && disclosure.getString("fallbackText").isNotBlank()) {
                                "M3 Library $name target $key hidden without real short fallback"
                            }
                            reachable.put(key,JSONObject().put("mode","short-fallback").put("directory",".")
                                .put("actualFallbackText",disclosure.getString("fallbackText")))
                        }
                    }
                }
                if(!disclosure.getBoolean("chartShown")) {
                    val fallback=field(page,"previewFallback") as TextView
                    val first=disclosure.getJSONArray("fallbackLines")
                    check(first.length()>0 && disclosure.getString("fallbackText").isNotBlank())
                    val lineCaptures=JSONArray(); val covered=linkedSetOf<Int>()
                    (0 until first.length()).forEach { if(first.getJSONObject(it).getBoolean("fullyVisible")) covered.add(it) }
                    for(index in 0 until first.length()) {
                        if(index in covered) continue
                        main {
                            val layout=requireNotNull(fallback.layout); check(layout.lineCount==first.length())
                            val y=fallback.baseline-layout.getLineBaseline(0)
                            fallback.requestRectangleOnScreen(Rect(0,y+layout.getLineTop(index),fallback.width,
                                y+layout.getLineBottom(index)),true)
                        }
                        val step=File(case,"reachable/fallback-line-$index")
                        val observed=libraryCapture(page,step,short) { libraryDisclosureState(page,item,scope) }
                            .getJSONObject("libraryDisclosure").getJSONArray("fallbackLines")
                        check(observed.length()==first.length() && observed.getJSONObject(index).getBoolean("fullyVisible"))
                        for(row in 0 until first.length()) {
                            val prior=first.getJSONObject(row); val now=observed.getJSONObject(row)
                            check(now.getInt("start")==prior.getInt("start") && now.getInt("end")==prior.getInt("end") &&
                                now.getInt("ellipsisCount")==0)
                            if(now.getBoolean("fullyVisible")) covered.add(row)
                        }
                        lineCaptures.put(JSONObject().put("line",index).put("capture",captureReference(directory,step)))
                    }
                    check(covered==(0 until first.length()).toSet())
                    reachable.put("fallback-line-coverage",JSONObject().put("lineCount",first.length())
                        .put("initialCovered",JSONArray((0 until first.length()).filter { first.getJSONObject(it).getBoolean("fullyVisible") }))
                        .put("segments",lineCaptures).put("allCovered",JSONArray(covered.sorted())))
                }
                File(case,"reachability.json").writeText(reachable.toString(2))
                report.put("sourceSelection",choice).put("initialList",before).put("initialDetailRequested",beforeDetail)
                    .put("initialScroll",JSONArray(listOf(beforeScroll,beforeFallback)))
                    .put("modelTargets",JSONArray(targets)).put("details",libraryDetails(page,item,scope,case))
                if(short) {
                    val compact=field(page,"compact") as Boolean
                    report.put("actualCompact",compact).put("preRestoreActivityIdentity",System.identityHashCode(page))
                    if(compact) {
                        test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                        await("compact short Library returned to browser") { !page.isDestroyed &&
                            !(field(page,"collection") as TaskCollectionLayout).detailRequested }
                    }
                    val old=report.getJSONArray("originalWindowLayout")
                    main { page.window.setLayout(old.getInt(0),old.getInt(1)) }
                    short=false
                }
                librarySelect(page,scope,originalItem)
                main {
                    (field(page,"search") as EditText).setText(before.getString("query"))
                    list(page).setSelectionFromTop(before.getInt("position"),before.getInt("offsetPx"))
                    (field(page,"detailScroll") as ScrollView).scrollTo(0,beforeScroll)
                    (field(page,"previewFallbackScroll") as ScrollView).scrollTo(0,beforeFallback)
                }
                val restoredFolder=File(case,"restored")
                val restored=libraryCapture(page,restoredFolder) { libraryCaseGeometry(page,scope) }
                val restoredState=restored.getJSONObject("libraryCaseState")
                val fields=listOf("list","detailRequested","detailScrollY","fallbackScrollY","activityIdentity",
                    "windowIdentity","decorIdentity","windowTokenIdentity","windowLayout","curvesPreferences",
                    "ownedStoreSha256","services","hasWindowFocus")
                val mismatches=fields.filter { key -> restoredState.get(key).toString()!=preparedState.get(key).toString() }
                report.put("restoredState",restoredState).put("restorationMismatches",JSONArray(mismatches))
                    .put("restoredCapture",captureReference(directory,restoredFolder))
                check(mismatches.isEmpty()) { "M3 Library $name prepared state mismatch: $mismatches" }
                check(listState(page).toString()==before.toString() &&
                    (field(page,"collection") as TaskCollectionLayout).detailRequested==beforeDetail &&
                    (field(page,"detailScroll") as ScrollView).scrollY==beforeScroll &&
                    (field(page,"previewFallbackScroll") as ScrollView).scrollY==beforeFallback &&
                    restored.getJSONObject("actualRestoreState").getBoolean("hasWindowFocus") && invariant(scope)==frozen &&
                    context.getSharedPreferences("curves",Context.MODE_PRIVATE).all==prefs && services().isEmpty()) {
                    "M3 Library $name original state not restored" }
                report.put("restoredList",listState(page))
                    .put("status","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
                if(name=="short-pressure") report.put("postRestoreActivityIdentity",System.identityHashCode(page))
                    .put("windowLayoutAfterRestore",JSONArray(listOf(page.window.attributes.width,page.window.attributes.height)))
                if(name=="short-pressure" && !(field(page,"compact") as Boolean)) {
                    val decor=page.window.decorView
                    test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                    await("wide short Library original Back finished Activity") { page.isDestroyed }
                    check(android.view.inspector.WindowInspector.getGlobalWindowViews().none { it===decor || it.windowToken===decor.windowToken })
                    report.put("originalBackAfterRestore","finished_original_activity")
                        .put("destroyedActivityIdentity",System.identityHashCode(page))
                }
            } catch(failure:Throwable) { error=failure; report.put("status","FAIL").put("failure",failure.stackTraceToString()); throw failure }
            finally {
                var cleanupFailure:Throwable?=null
                try { activity?.let { page -> if(!page.isDestroyed) close(page) } }
                catch(closeFailure:Throwable) { report.put("status","FAIL").put("cleanupFailure",closeFailure.stackTraceToString())
                    if(error!=null) error?.addSuppressed(closeFailure) else cleanupFailure=closeFailure }
                try { File(case,"case.json").writeText(report.toString(2)) }
                catch(writeFailure:Throwable) { if(error!=null) error?.addSuppressed(writeFailure)
                    else if(cleanupFailure!=null) cleanupFailure?.addSuppressed(writeFailure) else cleanupFailure=writeFailure }
                cleanupFailure?.let { throw it }
            }
        }
        return listOf(long,captured,readonly)
    }
    private fun popupList(activity:Activity):ListView? = android.view.inspector.WindowInspector.getGlobalWindowViews()
        .filter { it!==activity.window.decorView && it.isAttachedToWindow }
        .flatMap(::nodes).filterIsInstance<ListView>().singleOrNull { it.isShown && (it.adapter?.count ?: 0)>0 }
    private fun popupChoices(activity:Activity):List<String> {
        check(Looper.myLooper()==Looper.getMainLooper()) { "M3 Popup choices must be read on main" }
        val list=requireNotNull(popupList(activity)) { "M3 original PopupMenu not visible" }
        val adapter=requireNotNull(list.adapter) { "M3 original PopupMenu adapter missing" }
        return (0 until adapter.count).map { index ->
            requireNotNull(adapter.getItem(index)) { "M3 original PopupMenu label $index missing" }.toString()
        }
    }
    private fun popupEvidence(activity:Activity,list:ListView,expected:List<String>,diagnostic:JSONObject):JSONObject {
        check(Looper.myLooper()==Looper.getMainLooper()) { "M3 Popup evidence must be read on main" }
        val scope=diagnostic.getString("case")
        fun phase(value:String) { diagnostic.put("phase",value) }
        fun reason(value:String)="M3 $scope phase=${diagnostic.optString("phase")} $value"
        try {
            phase("identity-before")
            val popup=slotPopup(activity,list,expected)
            val adapter=requireNotNull(list.adapter) { reason("adapter missing before snapshot") }
            val count=adapter.count;val first=list.firstVisiblePosition;val childCount=list.childCount
            diagnostic.put("before",slotPopupState(popup).put("listAttached",list.isAttachedToWindow)
                .put("listLaidOut",list.isLaidOut).put("listLayoutRequested",list.isLayoutRequested)
                .put("adapterIdentity",System.identityHashCode(adapter)).put("adapterCount",count)
                .put("firstVisiblePosition",first).put("childCount",childCount))
            check(list.isAttachedToWindow && list.isLaidOut && popup.root.isAttachedToWindow && popup.root.isLaidOut &&
                count>0 && first>=0 && childCount>0 && first+childCount<=count) {
                reason("invalid original Popup snapshot bounds/layout") }
            phase("adapter-labels")
            val labels=(0 until count).map { index ->
                requireNotNull(adapter.getItem(index)) { reason("adapter label $index missing") }.toString()
            }
            check(labels==expected) { reason("original Popup labels changed") }
            val children=ArrayList<View>(childCount)
            phase("child-refs")
            for(index in 0 until childCount) {
                val child=requireNotNull(list.getChildAt(index)) { reason("child $index missing") }
                check(child.parent===list) { reason("child $index parent changed") }
                children.add(child)
            }
            diagnostic.put("childIdentities",JSONArray(children.map { System.identityHashCode(it) }))
            val visible=JSONArray()
            children.forEachIndexed { index,child ->
                phase("child-$index-text-and-node")
                val texts=nodes(child).filterIsInstance<TextView>().mapIndexed { textIndex,text ->
                    requireNotNull(text.text) { reason("child $index text $textIndex null") }.toString()
                }
                visible.put(JSONObject().put("adapterPosition",first+index)
                    .put("adapterText",labels[first+index])
                    .put("actualNodeTexts",JSONArray(texts)).put("actualNode",observation(child)))
                diagnostic.put("capturedChildCount",visible.length())
            }
            phase("identity-after")
            val after=slotPopup(activity,list,expected)
            diagnostic.put("after",slotPopupState(after).put("listAttached",list.isAttachedToWindow)
                .put("listLaidOut",list.isLaidOut).put("listLayoutRequested",list.isLayoutRequested)
                .put("adapterIdentity",list.adapter?.let { System.identityHashCode(it) } ?: JSONObject.NULL)
                .put("adapterCount",list.adapter?.count ?: JSONObject.NULL)
                .put("firstVisiblePosition",list.firstVisiblePosition).put("childCount",list.childCount)
                .put("childIdentities",JSONArray((0 until list.childCount).map { index ->
                    list.getChildAt(index)?.let { System.identityHashCode(it) } ?: JSONObject.NULL })))
            check(after.root===popup.root && after.token===popup.token && after.displayId==popup.displayId &&
                list.adapter===adapter && list.adapter?.count==count && list.firstVisiblePosition==first &&
                list.childCount==childCount && children.indices.all { index -> list.getChildAt(index)===children[index] &&
                    children[index].parent===list } && list.rootView===popup.root &&
                list.windowToken===popup.token && list.display?.displayId==popup.displayId &&
                list.isAttachedToWindow && list.isLaidOut && popup.root.isAttachedToWindow && popup.root.isLaidOut) {
                reason("original Popup changed during snapshot") }
            phase("complete")
            return JSONObject().put("localizedAdapterChoices",JSONArray(labels)).put("visibleNodes",visible)
                .put("noIndependentWindowProtocolClaim",true)
        } catch(error:Throwable) {
            diagnostic.put("failure",error.stackTraceToString())
            throw error
        }
    }
    private fun openLibraryMenu(activity:Activity, management:Boolean=false):List<String> {
        main {
            val anchor=if(management) requireNotNull(root(activity).findViewWithTag<View>("curve-library-management"))
                else field(activity,"detailActions") as View
            check(anchor.isShown && anchor.isAttachedToWindow && anchor.performClick())
        }
        await("original Library PopupMenu") { popupList(activity)!=null }
        lateinit var choices:List<String>
        main { choices=popupChoices(activity) }
        return choices
    }
    private fun choosePopup(activity:Activity, label:String) {
        val list=requireNotNull(popupList(activity))
        val matches=(0 until list.adapter.count).filter { list.adapter.getItem(it).toString()==label }
        check(matches.size==1) { "M3 PopupMenu action $label absent/ambiguous" }
        val position=matches.single()
        main { list.setSelection(position) }
        await("visible original PopupMenu child $label") {
            position>=list.firstVisiblePosition && position<list.firstVisiblePosition+list.childCount }
        main {
            val child=list.getChildAt(position-list.firstVisiblePosition)
            check(child!=null && child.isShown && list.performItemClick(child,position,list.adapter.getItemId(position)))
        }
    }
    private fun prefMapJson(values:Map<String,*>):JSONObject = JSONObject(values.mapValues { (_,value) ->
        JSONObject().put("type",when(value) { is Boolean -> "Boolean"; is String -> "String"; is Int -> "Int";
            is Long -> "Long"; is Float -> "Float"; is Set<*> -> "StringSet"; else -> error("Unknown preference type") })
            .put("value",if(value is Set<*>) JSONArray(value.sortedBy { it.toString() }) else value)
    })
    private fun routePreferenceSnapshot(name:String):Map<String,*> =
        context.getSharedPreferences(name,Context.MODE_PRIVATE).all.mapValues { (_,value) ->
            if(value is Set<*>) value.toSet() else value
        }.toMap()
    private fun routeViewState(view:View):JSONObject = JSONObject()
        .put("identity",System.identityHashCode(view)).put("class",view.javaClass.name)
        .put("visibility",view.visibility).put("isLaidOut",view.isLaidOut)
        .put("isLayoutRequested",view.isLayoutRequested)
        .put("width",view.width).put("height",view.height)
        .put("scrollX",view.scrollX).put("scrollY",view.scrollY)
    private fun routeActivityState(activity:Activity, into:JSONObject) {
        val window=activity.window; val decor=window.decorView; val token=decor.windowToken
        into.put("activityIdentity",System.identityHashCode(activity)).put("activityClass",activity.javaClass.name)
            .put("windowIdentity",System.identityHashCode(window)).put("windowClass",window.javaClass.name)
            .put("decorIdentity",System.identityHashCode(decor)).put("decorClass",decor.javaClass.name)
            .put("windowTokenIdentity",token?.let { System.identityHashCode(it) } ?: JSONObject.NULL)
            .put("windowTokenClass",token?.javaClass?.name ?: JSONObject.NULL)
            .put("compact",field(activity,"compact") as Boolean)
        into.put("root",routeViewState(root(activity)))
        into.put("list",routeViewState(list(activity)))
        into.put("detailPanel",routeViewState(field(activity,"detailPanel") as View))
        into.put("detailScroll",routeViewState(field(activity,"detailScroll") as ScrollView))
        into.put("fallbackScroll",routeViewState(field(activity,"previewFallbackScroll") as ScrollView))
    }
    private fun restorePresetKeys(before:Map<String,*>) {
        val preferences=context.getSharedPreferences("presets",Context.MODE_PRIVATE)
        val edit=preferences.edit()
        (1..5).map(PresetSlots::key).forEach { key ->
            if(!before.containsKey(key)) edit.remove(key)
            else when(val value=before[key]) {
                is Boolean -> edit.putBoolean(key,value)
                is String -> edit.putString(key,value)
                is Int -> edit.putInt(key,value)
                is Long -> edit.putLong(key,value)
                is Float -> edit.putFloat(key,value)
                is Set<*> -> { check(value.all { it is String }); @Suppress("UNCHECKED_CAST")
                    edit.putStringSet(key,value as Set<String>) }
                else -> error("Unknown original preset key type $key")
            }
        }
        check(edit.commit()); main { }
        check(preferences.all==before) { "M3 preset whitelist restoration changed original map" }
    }
    private fun presetDisk():JSONObject = JSONObject().apply {
        listOf("presets.xml","presets.xml.bak").forEach { name ->
            val file=File(context.applicationInfo.dataDir,"shared_prefs/$name")
            put(name,if(file.isFile) sha(file) else JSONObject.NULL)
        }
    }
    private fun diskPresetString(key:String):String? {
        val file=File(context.applicationInfo.dataDir,"shared_prefs/presets.xml")
        if(!file.isFile) return null
        val parser=android.util.Xml.newPullParser()
        file.inputStream().use { parser.setInput(it,"UTF-8")
            while(parser.eventType!=org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if(parser.eventType==org.xmlpull.v1.XmlPullParser.START_TAG && parser.name=="string" &&
                    parser.getAttributeValue(null,"name")==key) return parser.nextText()
                parser.next()
            }
        }
        return null
    }
    private fun librarySlots(scope: Scope, directory: File, qualified:CurveLibraryItem, captured:CurveLibraryItem,
        readonly:CurveLibraryItem) {
        val folder=File(directory,"preset-eligibility-and-slots").apply { check(isDirectory || mkdirs()) }
        check(qualified.factoryCurve!=null && scope.curves.canStart(qualified) &&
            captured.factoryCurve==null && readonly.factoryCurve==null)
        val prefs=context.getSharedPreferences("presets",Context.MODE_PRIVATE)
        val before=prefs.all.toMap(); val diskBefore=presetDisk(); val frozen=invariant(scope)
        val report=JSONObject().put("qualifiedId",qualified.id).put("before",prefMapJson(before))
            .put("diskBeforeSha256",diskBefore).put("steps",JSONArray()).put("negative",JSONArray())
            .put("menuDiagnostics",JSONArray())
            .put("allowedKeys",JSONArray((1..5).map(PresetSlots::key))).put("visualAcceptance","ASTRA_PENDING")
        var activity:Activity?=null; var primary:Throwable?=null
        try {
            val page=start(CurveActivity::class.java); activity=page; unfocus(page)
            librarySelect(page,scope,qualified)
            for(slot in 1..5) {
                val menu=openLibraryMenu(page)
                check(menu.count { it==page.getString(R.string.curve_assign_slot) }==1)
                val menuDiagnostic=JSONObject().put("case","slot-$slot").put("phase","before-main")
                report.getJSONArray("menuDiagnostics").put(menuDiagnostic)
                lateinit var menuList:ListView
                lateinit var menuNodes:JSONObject
                lateinit var popup:SlotPopup
                main {
                    try {
                        menuDiagnostic.put("phase","original-list")
                        menuList=requireNotNull(popupList(page)) { "M3 slot-$slot phase=original-list Popup List missing" }
                        menuNodes=popupEvidence(page,menuList,menu,menuDiagnostic)
                        menuDiagnostic.put("phase","post-evidence-slot-identity")
                        popup=slotPopup(page,menuList,menu)
                        menuDiagnostic.put("phase","complete")
                    } catch(error:Throwable) {
                        if(!menuDiagnostic.has("failure"))menuDiagnostic.put("failure",error.stackTraceToString())
                        throw error
                    }
                }
                val transitionFile=File(folder,"slot-$slot-transition.json")
                val observations=JSONArray()
                val transition=JSONObject().put("slot",slot).put("menu",JSONArray(menu))
                    .put("activityIdentity",System.identityHashCode(page))
                    .put("originalPopupBeforeClick",slotPopupState(popup)).put("observations",observations)
                var lastObservation:String?=null
                fun recordTransition(observed:JSONObject) {
                    val value=observed.toString()
                    if(value!=lastObservation) {
                        observations.put(observed);lastObservation=value
                        transitionFile.writeText(transition.toString(2))
                    }
                }
                transitionFile.writeText(transition.toString(2))
                var dialog:AlertDialog?=null
                try {
                    choosePopup(page,page.getString(R.string.curve_assign_slot))
                    await("original slot chooser") { dialog=libraryWindowDialog(page,popup,::recordTransition); dialog!=null }
                }catch(error:Throwable) {
                    transition.put("failure",error.stackTraceToString())
                    try {transitionFile.writeText(transition.toString(2))}
                    catch(recordFailure:Throwable) {error.addSuppressed(recordFailure)}
                    throw error
                }
                val chooser=requireNotNull(dialog); val window=requireNotNull(chooser.window); val decor=window.decorView
                var selected:JSONObject?=null
                main { check(libraryWindowDialog(page,popup) {selected=it}===chooser) }
                transition.put("selected",requireNotNull(selected))
                    .put("chooserWindowIdentity",System.identityHashCode(window))
                    .put("chooserDecorIdentity",System.identityHashCode(decor))
                    .put("chooserTokenIdentity",System.identityHashCode(requireNotNull(decor.windowToken)))
                transitionFile.writeText(transition.toString(2))
                val labels=(1..5).map { page.getString(R.string.home_slot,it.toString()) }
                val list=requireNotNull(chooser.listView)
                main { check(list.adapter.count==5 && (0 until 5).map { list.adapter.getItem(it).toString() }==labels) }
                if(slot==1) {
                    val chooserFolder=File(folder,"chooser")
                    val frame=frames.screenshotMeasuredWindowBufferTranslation(window,decor,File(chooserFolder.apply { check(isDirectory || mkdirs()) },"window.png")) {
                        var transitionState:JSONObject?=null
                        check(libraryWindowDialog(page,popup) {transitionState=it}===chooser)
                        val titleText=page.getString(R.string.curve_choose_slot)
                        val title=requireNotNull(nodes(decor).filterIsInstance<TextView>().singleOrNull { it.text.toString()==titleText })
                        check(list.firstVisiblePosition==0 && list.childCount==5)
                        val rows=JSONArray((0 until 5).map { index ->
                            val child=requireNotNull(list.getChildAt(index))
                            val text=requireNotNull(nodes(child).filterIsInstance<TextView>()
                                .singleOrNull { it.text.toString()==labels[index] })
                            check(child.isShown && text.isShown)
                            JSONObject().put("adapterPosition",index).put("adapterText",list.adapter.getItem(index).toString())
                                .put("child",observation(child)).put("label",observation(text))
                        })
                        check(whole(observation(title)) && (0 until rows.length()).all { index ->
                            val row=rows.getJSONObject(index); whole(row.getJSONObject("child")) && whole(row.getJSONObject("label")) })
                        JSONObject().put("title",titleText).put("titleView",observation(title)).put("choices",JSONArray(labels))
                            .put("visibleRows",rows)
                            .put("actualAdapterChoices",JSONArray((0 until list.adapter.count).map { list.adapter.getItem(it).toString() }))
                            .put("windowIdentity",System.identityHashCode(window))
                            .put("slotTransition",requireNotNull(transitionState))
                            .put("originalPopupBeforeClick",transition.getJSONObject("originalPopupBeforeClick"))
                    }
                    File(chooserFolder,"geometry.json").writeText(frame.target.toString(2))
                    transition.put("acceptedChooserState",frame.target.getJSONObject("slotTransition"))
                    transitionFile.writeText(transition.toString(2))
                    report.put("chooserCapture",captureReference(directory,chooserFolder))
                }
                main { check(libraryWindowDialog(page,popup,::recordTransition)===chooser) }
                transitionFile.writeText(transition.toString(2))
                val justBefore=prefs.all.toMap()
                main { list.setSelection(slot-1) }
                await("visible original slot child $slot") { slot-1>=list.firstVisiblePosition &&
                    slot-1<list.firstVisiblePosition+list.childCount }
                main {
                    val child=list.getChildAt(slot-1-list.firstVisiblePosition)
                    check(child!=null && child.isShown && list.performItemClick(child,slot-1,list.adapter.getItemId(slot-1)))
                }
                await("slot chooser dismissed") { page.hasWindowFocus() }
                main { }
                val after=prefs.all.toMap(); val key=PresetSlots.key(slot)
                check(after.keys==justBefore.keys+key && after[key]==qualified.id &&
                    after.filterKeys { it!=key }==justBefore.filterKeys { it!=key }) { "M3 slot $slot wrote outside whitelist" }
                await("slot $slot original apply persisted") { diskPresetString(key)==qualified.id }
                val step=File(folder,"slot-$slot")
                libraryCapture(page,step) { libraryDisclosureState(page,qualified,scope) }
                report.getJSONArray("steps").put(JSONObject().put("slot",slot).put("menu",JSONArray(menu))
                    .put("menuNodes",menuNodes)
                    .put("slotTransitionFile",transitionFile.relativeTo(directory).invariantSeparatorsPath)
                    .put("slotTransitionSha256",sha(transitionFile))
                    .put("before",prefMapJson(justBefore)).put("after",prefMapJson(after))
                    .put("diskSha256",presetDisk()).put("actualStoredId",after[key])
                    .put("capture",captureReference(directory,step)))
                check(invariant(scope)==frozen && services().isEmpty())
            }
            for((label,item) in listOf("captured" to captured,"readonly-flow" to readonly)) {
                librarySelect(page,scope,item)
                val unchanged=prefs.all.toMap()
                val menu=openLibraryMenu(page)
                val menuDiagnostic=JSONObject().put("case","negative-$label").put("phase","before-main")
                report.getJSONArray("menuDiagnostics").put(menuDiagnostic)
                lateinit var menuNodes:JSONObject
                main {
                    try {
                        menuDiagnostic.put("phase","original-list")
                        val menuList=requireNotNull(popupList(page)) { "M3 negative-$label phase=original-list Popup List missing" }
                        menuNodes=popupEvidence(page,menuList,menu,menuDiagnostic)
                    } catch(error:Throwable) {
                        if(!menuDiagnostic.has("failure"))menuDiagnostic.put("failure",error.stackTraceToString())
                        throw error
                    }
                }
                check(page.getString(R.string.curve_assign_slot) !in menu &&
                    !(field(page,"assignPreset") as Button).isEnabled &&
                    (field(page,"assignPreset") as Button).visibility==View.GONE && prefs.all==unchanged)
                report.getJSONArray("negative").put(JSONObject().put("case",label).put("itemId",item.id)
                    .put("actualMenu",JSONArray(menu)).put("actualMenuNodes",menuNodes)
                    .put("localizedAssignText",page.getString(R.string.curve_assign_slot))
                    .put("assignEnabled",false).put("assignAbsent",true)
                    .put("slotMap",prefMapJson(prefs.all)))
                test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                await("negative qualification menu dismissed") { popupList(page)==null }
            }
            restorePresetKeys(before)
            val restoredFolder=File(folder,"restored")
            libraryCapture(page,restoredFolder) { libraryDisclosureState(page,readonly,scope) }
            report.put("afterRestore",prefMapJson(prefs.all)).put("diskAfterSha256",presetDisk())
                .put("restoredCapture",captureReference(directory,restoredFolder))
                .put("typedPresenceValueRestored",prefs.all==before).put("businessModelUnchanged",invariant(scope)==frozen)
                .put("status","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error:Throwable) { primary=error; report.put("status","FAIL").put("failure",error.stackTraceToString()); throw error }
        finally {
            try { if(prefs.all!=before) restorePresetKeys(before) }
            catch(error:Throwable) { report.put("status","FAIL").put("restoreFailure",error.stackTraceToString())
                if(primary!=null) primary?.addSuppressed(error) else primary=error }
            try { activity?.let { if(!it.isDestroyed) close(it) } }
            catch(error:Throwable) { report.put("status","FAIL").put("closeFailure",error.stackTraceToString())
                if(primary!=null) primary?.addSuppressed(error) else primary=error }
            try { File(folder,"business.json").writeText(report.toString(2)) }
            catch(error:Throwable) { if(primary!=null) primary?.addSuppressed(error) else primary=error }
            if(primary!=null && report.optString("failure").isEmpty()) throw requireNotNull(primary)
        }
    }
    private fun librarySourceRoutes(scope:Scope, directory:File, qualified:CurveLibraryItem) {
        val rootFolder=File(directory,"library-source-routes").apply { check(isDirectory || mkdirs()) }
        val source=listOf("edit-draft" to requireNotNull(scope.curves.find(scope.document.id)),
            "edit-copy-factory" to qualified,"share-page" to requireNotNull(scope.curves.find(scope.document.id)),
            "import-page" to null,"legacy-page" to null)
        check(source.size==5 && qualified.factoryCurve!=null && scope.curves.canStart(qualified) &&
            CustomCurveDocument.fromLibraryItem(qualified).controlMode==CustomCurveDocument.ControlMode.PRESSURE)
        val frozen=invariant(scope); val preset=routePreferenceSnapshot("presets")
        val curvePrefs=routePreferenceSnapshot("curves")
        val clipboard=scope.clipboardSnapshot()
        val report=JSONObject().put("routes",JSONArray(source.map { it.first })).put("visualAcceptance","ASTRA_PENDING")
        source.forEach { (route,item) ->
            val folder=File(rootFolder,"routes/$route").apply { check(isDirectory || mkdirs()) }
            var library:Activity?=null; var target:Activity?=null; var primary:Throwable?=null
            val routeReport=JSONObject().put("route",route).put("sourceId",item?.id ?: JSONObject.NULL)
                .put("visualAcceptance","ASTRA_PENDING")
            try {
                if(route=="legacy-page") scope.installOwnedLegacy()
                val page=start(CurveActivity::class.java); library=page; unfocus(page)
                if(item!=null) routeReport.put("realSelection",librarySelect(page,scope,item))
                val departure=JSONObject().put("stage","before_route_departure_main_snapshot")
                routeReport.put("departureSnapshot",departure)
                main { try {
                    departure.put("sampleElapsedRealtimeMs",SystemClock.elapsedRealtime())
                    departure.put("listState",listState(page))
                    departure.put("detailRequested",(field(page,"collection") as TaskCollectionLayout).detailRequested)
                    departure.put("detailScrollY",(field(page,"detailScroll") as ScrollView).scrollY)
                    departure.put("fallbackScrollY",(field(page,"previewFallbackScroll") as ScrollView).scrollY)
                    routeActivityState(page,departure)
                } catch(error:Throwable) { departure.put("snapshotFailure",error.stackTraceToString());throw error } }
                val before=departure.getJSONObject("listState"); val requested=departure.getBoolean("detailRequested")
                val scroll=departure.getInt("detailScrollY"); val fallback=departure.getInt("fallbackScrollY")
                val expected=when(route) {
                    "edit-draft","edit-copy-factory" -> CurveEditorActivity::class.java
                    "share-page" -> CurveShareActivity::class.java
                    "import-page" -> CurveImportActivity::class.java
                    else -> LegacyCurveActivity::class.java
                }
                val monitor=test.addMonitor(expected.name,null,false)
                try {
                    when(route) {
                        "edit-draft","edit-copy-factory" -> {
                            var direct=false
                            main { val view=field(page,"directEdit") as Button
                                direct=view.isShown
                                if(direct) check(view.isEnabled && view.performClick()) }
                            if(!direct) {
                                val menu=openLibraryMenu(page)
                                val label=page.getString(if(route=="edit-draft") R.string.profiles_edit else R.string.profiles_edit_copy)
                                check(label in menu); choosePopup(page,label)
                            }
                        }
                        "share-page" -> {
                            var direct=false
                            main { val view=field(page,"directShare") as Button
                                direct=view.isShown
                                if(direct) check(view.isEnabled && view.performClick()) }
                            if(!direct) { val menu=openLibraryMenu(page)
                                check(page.getString(R.string.profiles_share) in menu)
                                choosePopup(page,page.getString(R.string.profiles_share)) }
                        }
                        else -> { val menu=openLibraryMenu(page,true)
                            val label=page.getString(if(route=="import-page") R.string.profiles_import else R.string.curve_legacy)
                            check(label in menu); choosePopup(page,label) }
                    }
                    target=test.waitForMonitorWithTimeout(monitor,10000)
                } finally { test.removeMonitor(monitor) }
                val destination=requireNotNull(target) { "M3 $route target Activity not launched" }
                await("M3 $route target focused") { destination.hasWindowFocus() && root(destination).isLaidOut &&
                    !root(destination).isLayoutRequested }
                check(destination.javaClass==expected && !page.isDestroyed)
                val extra=when(route) { "edit-draft","edit-copy-factory" -> destination.intent.getStringExtra(CurveEditorActivity.CURVE_ID)
                    "share-page" -> destination.intent.getStringExtra(CurveShareActivity.CURVE_ID)
                    else -> null }
                check(extra==item?.id && destination.intent.extras?.keySet()?.all { key ->
                    key==CurveEditorActivity.CURVE_ID || key==CurveShareActivity.CURVE_ID
                }!=false) { "M3 $route wrong source Intent or unrequested extra" }
                when(route) {
                    "edit-draft" -> {
                        val actual=(field(destination,"fields") as Map<*,*>)["name"] as EditText
                        check(field(destination,"draftId")==scope.document.id && actual.text.toString()==scope.document.name)
                        routeReport.put("targetContent",JSONObject().put("draftId",field(destination,"draftId"))
                            .put("name",actual.text.toString()).put("sourceDocument",scope.document.toJson()))
                    }
                    "edit-copy-factory" -> {
                        val newId=field(destination,"draftId") as String
                        val warning=nodes(root(destination)).filterIsInstance<TextView>()
                            .any { it.text.toString()==destination.getString(R.string.profiles_copy_warning) }
                        check(newId.startsWith("draft-") && newId!=item?.id && warning)
                        routeReport.put("targetContent",JSONObject().put("newDraftId",newId)
                            .put("copyWarningVisible",warning).put("factorySourceId",item?.id))
                    }
                    "share-page" -> {
                        val actual=field(destination,"document") as CustomCurveDocument
                        check(actual==scope.document && nodes(root(destination)).filterIsInstance<TextView>()
                            .any { it.text.toString()==scope.document.name })
                        routeReport.put("targetContent",JSONObject().put("encodedDocument",actual.toJson())
                            .put("externalShareCopyExportInvoked",false))
                    }
                    "import-page" -> {
                        val empty=nodes(root(destination)).filterIsInstance<EditText>().single().text.isEmpty()
                        check(empty)
                        routeReport.put("targetContent",JSONObject().put("rawInputEmpty",empty)
                            .put("clipboardOrSafReadInvoked",false))
                    }
                    "legacy-page" -> {
                        scope.drainOwnedLegacyReaders()
                        main { check(nodes(root(destination)).filterIsInstance<TextView>().any {
                            it.text.toString()==destination.getString(R.string.legacy_curve_count,"1") }) }
                        routeReport.put("targetContent",JSONObject().put("ownedArchiveCount",1)
                            .put("importOrOriginalFileReadInvoked",false))
                    }
                }
                val targetFolder=File(folder,"target")
                libraryCapture(destination,targetFolder) { geometry(destination) }
                routeReport.put("before",before).put("detailRequestedBefore",requested)
                    .put("scrollBefore",JSONArray(listOf(scroll,fallback))).put("intentExtraId",extra ?: JSONObject.NULL)
                    .put("targetClass",destination.javaClass.name).put("targetFocused",true)
                    .put("targetCapture",captureReference(directory,targetFolder))
                when(route) {
                    "edit-draft","edit-copy-factory" -> main {
                        val cancel=requireNotNull(root(destination).findViewWithTag<Button>("product/form/curve-editor/cancel"))
                        check(cancel.isShown && cancel.performClick()) }
                    "import-page" -> main { val cancel=requireNotNull(root(destination).findViewWithTag<Button>("product/curve-import/cancel"))
                        check(cancel.text.toString()==destination.getString(R.string.beans_cancel))
                        check(cancel.isShown && cancel.performClick()) }
                    else -> test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
                }
                await("M3 $route returned to original Library") { destination.isDestroyed && page.hasWindowFocus() }
                if(route=="legacy-page") scope.drainOwnedLegacyReaders()
                val restoredFolder=File(folder,"restored")
                val returned=libraryCapture(page,restoredFolder) { geometry(page) }
                val diagnostics=JSONObject().put("stage","after_restored_capture_pre_assertion_main_snapshot")
                    .put("sameCompositorFrameProven",false)
                    .put("returnedFrameFocusSource","restored_capture_target_actualRestoreState")
                routeReport.put("returnAssertionDiagnostics",diagnostics)
                    .put("restoredCapture",captureReference(directory,restoredFolder))
                main { try {
                    diagnostics.put("sampleElapsedRealtimeMs",SystemClock.elapsedRealtime())
                    val after=JSONObject();diagnostics.put("after",after)
                    val afterList=listState(page);after.put("listState",afterList);routeReport.put("returned",afterList)
                    val afterRequested=(field(page,"collection") as TaskCollectionLayout).detailRequested
                    after.put("detailRequested",afterRequested)
                    val afterScroll=(field(page,"detailScroll") as ScrollView).scrollY
                    after.put("detailScrollY",afterScroll)
                    val afterFallback=(field(page,"previewFallbackScroll") as ScrollView).scrollY
                    after.put("fallbackScrollY",afterFallback)
                    routeActivityState(page,after)
                    val afterInvariant=invariant(scope);after.put("invariant",afterInvariant)
                    val afterPreset=routePreferenceSnapshot("presets");after.put("presets",prefMapJson(afterPreset))
                    val afterCurvePrefs=routePreferenceSnapshot("curves");after.put("curvePrefs",prefMapJson(afterCurvePrefs))
                    val afterClipboard=scope.clipboardSnapshot();after.put("clipboard",afterClipboard)
                    val afterServices=services().toList();after.put("services",JSONArray(afterServices))
                    val capturedFocus=returned.getJSONObject("actualRestoreState").getBoolean("hasWindowFocus")
                    after.put("returnedFrameHasWindowFocus",capturedFocus)
                        .put("currentHasWindowFocus",page.hasWindowFocus())
                        .put("currentFocusedViewTag",page.currentFocus?.tag ?: JSONObject.NULL)
                        .put("currentFocusedViewClass",page.currentFocus?.javaClass?.name ?: JSONObject.NULL)
                        .put("targetDestroyed",destination.isDestroyed).put("targetHasWindowFocus",destination.hasWindowFocus())
                    val comparisons=JSONObject();val falseItems=JSONArray()
                    diagnostics.put("comparisons",comparisons).put("falseItems",falseItems)
                    fun compare(name:String, original:Any?, actual:Any?, equal:Boolean) {
                        comparisons.put(name,JSONObject().put("before",original ?: JSONObject.NULL)
                            .put("after",actual ?: JSONObject.NULL).put("equal",equal))
                        if(!equal)falseItems.put(name)
                    }
                    compare("listState",before,afterList,before.toString()==afterList.toString())
                    val listFields=JSONObject();diagnostics.put("listFields",listFields)
                    listOf("position","offsetPx","firstItemId","count","query","category","sort","selectedId")
                        .forEach { key -> val original=before.get(key);val actual=afterList.get(key)
                            listFields.put(key,JSONObject().put("before",original).put("after",actual)
                                .put("equal",original==actual)) }
                    compare("detailRequested",requested,afterRequested,requested==afterRequested)
                    compare("detailScrollY",scroll,afterScroll,scroll==afterScroll)
                    compare("fallbackScrollY",fallback,afterFallback,fallback==afterFallback)
                    compare("returnedFrameHasWindowFocus",true,capturedFocus,capturedFocus)
                    compare("invariant",frozen,afterInvariant,frozen==afterInvariant)
                    compare("presetMap",prefMapJson(preset),prefMapJson(afterPreset),preset==afterPreset)
                    compare("curvePrefsMap",prefMapJson(curvePrefs),prefMapJson(afterCurvePrefs),curvePrefs==afterCurvePrefs)
                    compare("clipboard",clipboard,afterClipboard,clipboard==afterClipboard)
                    compare("servicesEmpty",JSONArray(),JSONArray(afterServices),afterServices.isEmpty())
                    diagnostics.put("allTenEqual",falseItems.length()==0)
                        .put("sampleFinishedElapsedRealtimeMs",SystemClock.elapsedRealtime())
                } catch(error:Throwable) { diagnostics.put("snapshotFailure",error.stackTraceToString());throw error } }
                check(diagnostics.getBoolean("allTenEqual")) { "M3 $route return/invariant failed" }
                routeReport.put("status","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
            } catch(error:Throwable) { primary=error; routeReport.put("status","FAIL").put("failure",error.stackTraceToString()); throw error }
            finally {
                try { target?.let { if(!it.isDestroyed) close(it) }; library?.let { if(!it.isDestroyed) close(it) }
                    if(route=="legacy-page") scope.drainOwnedLegacyReaders() }
                catch(error:Throwable) { routeReport.put("status","FAIL").put("closeFailure",error.stackTraceToString())
                    if(primary!=null) primary?.addSuppressed(error) else primary=error }
                try { File(folder,"business.json").writeText(routeReport.toString(2)) }
                catch(error:Throwable) { if(primary!=null) primary?.addSuppressed(error) else primary=error }
                if(primary!=null && routeReport.optString("failure").isEmpty()) throw requireNotNull(primary)
            }
        }
        report.put("completed",true).put("status","PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE")
        File(rootFolder,"business.json").writeText(report.toString(2))
    }
    private fun libraryBusiness(initial: Activity, scope: Scope, directory: File) {
        var activity=initial
        val frozen=invariant(scope)
        val preferences=context.getSharedPreferences("curves",Context.MODE_PRIVATE)
        val selectedPreference=preferences.getString("selected",null)
        val category=CurveCategoryFilter.entries.filter { it!=CurveCategoryFilter.ALL }
            .maxBy { CurveSearch.filter(scope.curves.items,it,"factory-v3").size }
        main {
            (field(activity,"search") as EditText).setText("factory-v3")
            nodes(root(activity)).filterIsInstance<TextView>().single { it.isClickable && it.text.toString()==category.label(activity) }.let { check(it.performClick()) }
            nodes(root(activity)).filterIsInstance<Spinner>().single().setSelection(CurveSortMode.MOST_USED.ordinal)
        }
        stableList(activity,directory,"filtered")
        main { check(list(activity).canScrollVertically(1)) { "M3 fixture has no genuine continuous-list range" } }
        gesture(list(activity))
        val before=stableList(activity,directory,"before-selection")
        check(before.getInt("position")>0) { "M3 real gesture did not establish non-first-page anchor" }
        main {
            val list=list(activity)
            val child=(0 until list.childCount).map { list.getChildAt(it) }.first { child ->
                val visible=Rect(); child.getGlobalVisibleRect(visible) && visible.width()==child.width && visible.height()==child.height
            }
            val position=list.firstVisiblePosition+list.indexOfChild(child)
            check(list.performItemClick(child,position,list.adapter.getItemId(position)))
        }
        val after=stableList(activity,directory,"after-selection")
        listOf("position","offsetPx","firstItemId","count").forEach { check(before.get(it)==after.get(it)) { "M3 preview moved list $it" } }
        check(preferences.getString("selected",null)==selectedPreference && invariant(scope)==frozen)
        captureState(activity,directory,"non-first-selection")
        main { check((field(activity,"parameterShortcut") as Button).performClick()) }
        await("real parameter dialog") { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it!==activity.window.decorView && it.hasWindowFocus() } }
        main {
            val dialog=android.view.inspector.WindowInspector.getGlobalWindowViews().single { it!==activity.window.decorView && it.hasWindowFocus() }
            check(dialog.findViewById<View>(android.R.id.button1).performClick())
        }
        await("parameter dialog closed") { activity.hasWindowFocus() }
        val returned=stableList(activity,directory,"after-detail-return")
        check(returned.toString()==after.toString()) { "M3 details return changed list/selection" }
        val monitor=test.addMonitor(CurveActivity::class.java.name,null,false)
        try {
            main { activity.recreate() }
            activity=test.waitForMonitorWithTimeout(monitor,10000) ?: error("M3 Curve Activity recreation missing")
        } finally { test.removeMonitor(monitor) }
        await("recreated real Curve ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        val recreated=stableList(activity,directory,"after-recreate")
        File(directory,"continuous-list-business.json").writeText(JSONObject().put("beforeSelection",before).put("afterSelection",after)
            .put("afterDetailsReturn",returned).put("afterRecreate",recreated).put("originalBaseCount",scope.baseLibraryIds.size)
            .put("actualLibraryCountIncludingRealDraft",scope.curves.items.size).put("completed",false).toString(2))
        check(recreated.toString()==after.toString()) { "M3 recreate changed query/category/sort/selected or exact non-first anchor" }
        check(invariant(scope)==frozen && preferences.getString("selected",null)==selectedPreference)
        captureState(activity,directory,"list-restored")
        lateinit var chosen: CurveLibraryItem
        val beforeUse=preferences.all.toMap()
        main {
            chosen=field(activity,"selected") as CurveLibraryItem
            val use=field(activity,"select") as Button
            check(use.isEnabled==scope.curves.canStart(chosen) && use.isEnabled)
            check(whole(observation(use)) && use.height>=HoyiUi.dp(activity,48))
            check(use.performClick())
        }
        await("real use-for-cup callback finished") { activity.isDestroyed }
        check(preferences.all==beforeUse.toMutableMap().apply { put("selected",chosen.id) }) { "M3 use-for-cup changed unrelated preferences" }
        check(invariant(scope)==frozen && services().isEmpty()) { "M3 library action changed domain data or started service" }
        File(directory,"continuous-list-business.json").writeText(JSONObject().put("beforeSelection",before).put("afterSelection",after)
            .put("afterDetailsReturn",returned).put("afterRecreate",recreated).put("originalBaseCount",scope.baseLibraryIds.size)
            .put("actualLibraryCountIncludingRealDraft",scope.curves.items.size).put("onlySelectedPreferenceChanged",true)
            .put("usedCurveId",chosen.id).put("noMachineOrServiceCommands",true).put("modelsAndUsageUnchanged",true)
            .put("presetSlotsAndClipboardNotTouched",true).put("completed",true).toString(2))
    }
    private fun diagnostic(activity: Activity, folder: File) {
        check(folder.isDirectory || folder.mkdirs())
        lateinit var before: JSONObject; main { before=geometry(activity); TaskTypographyEvidence.capture(activity,folder) }
        val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
        try { File(folder,"window.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } }
        finally { bitmap.recycle() }
        lateinit var after: JSONObject; main { after=geometry(activity) }
        File(folder,"geometry.json").writeText(JSONObject().put("before",before).put("after",after)
            .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false)
            .put("protocol","unsynchronized_IME_diagnostic_not_main_protocol2").toString(2))
    }
    private fun stableWindow(activity: Activity, directory: File, phase: String,
        transitionProbe: ImeViewportTransitionProbe?=null) {
        val deadline=SystemClock.elapsedRealtime()+5000; val samples=JSONArray()
        var prior: String?=null; var consecutive=0
        try {
            while(SystemClock.elapsedRealtime()<deadline) {
                frames.committedFrame(root(activity))
                lateinit var value: JSONObject; var pending=true
                main { value=geometry(activity);transitionProbe?.sample("stable-frame-$phase",samples.length()+1,force=false)
                    pending=nodes(root(activity)).any { it.isShown && it.isLayoutRequested } }
                val signature=value.toString(); val within=SystemClock.elapsedRealtime()<=deadline
                consecutive=if(!pending && signature==prior) consecutive+1 else if(!pending) 1 else 0
                samples.put(JSONObject().put("geometry",value).put("pendingLayout",pending)
                    .put("consecutiveUnchanged",consecutive).put("withinBudget",within))
                prior=signature
                if(consecutive>=3 && within) return
            }
            error("M3 $phase geometry did not stabilize on three actual frames within 5000ms")
        } finally { File(directory,"$phase-window-stability.json").writeText(JSONObject().put("samples",samples)
            .put("acceptancePolicy","geometry_stability_only; pending_only_isShown_tree; hidden_nodes_do_not_block; not_waiting_for_visibility_or_business_assertion_success").toString(2)) }
    }
    private fun start(type: Class<out Activity>, document: CustomCurveDocument?=null): Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        document?.let { intent.putExtra(CurveImportPreviewActivity.DOCUMENT,it.encode()) }
        return test.startActivitySync(intent).also { activity -> await("${type.simpleName} ready") {
            activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested } }
    }
    private fun close(activity: Activity) {
        main { activity.finish() }
        await("${activity.javaClass.simpleName} destroyed") { activity.isDestroyed }
    }
    private fun action(activity: Activity, id: Int): TextView = nodes(root(activity)).filterIsInstance<TextView>()
        .single { it.isClickable && it.text.toString()==activity.getString(id) }
    private fun click(activity: Activity, id: Int) { main {
        val view=action(activity,id); check(view.isEnabled && view.isShown)
        check(view.performClick())
    }; test.waitForIdleSync() }
    private fun unfocus(activity: Activity, causalTrace:ImeRecreationTrace?=null) {
        main {
            causalTrace?.observe("fixture_unfocus_before",activity,root(activity),"CurveVisualMigrationChecks.importBusiness.unfocus","content")
            root(activity).isFocusableInTouchMode=true; check(root(activity).requestFocus())
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .hideSoftInputFromWindow(root(activity).windowToken,0)
            causalTrace?.observe("fixture_unfocus_hide_returned",activity,root(activity),"CurveVisualMigrationChecks.importBusiness.unfocus","content")
        }
        await("unfocused real window without IME") { !requireNotNull(root(activity).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
    }
    private fun input(activity: Activity)=field(activity,"text") as EditText
    private fun rawState(activity: Activity): JSONObject {
        val view=input(activity)
        return JSONObject().put("text",view.text.toString()).put("selectionStart",view.selectionStart).put("selectionEnd",view.selectionEnd)
            .put("focused",view.hasFocus()).put("scrollX",view.scrollX).put("scrollY",view.scrollY)
    }
    private fun nonCustomInvariant(scope: Scope)=JSONObject(invariant(scope)).apply { remove("drafts") }.toString()
    private fun dismissDialog(owner: Activity) {
        await("real local dialog") { android.view.inspector.WindowInspector.getGlobalWindowViews().any { it!==owner.window.decorView && it.hasWindowFocus() } }
        main {
            val window=android.view.inspector.WindowInspector.getGlobalWindowViews().single { it!==owner.window.decorView && it.hasWindowFocus() }
            check(requireNotNull(window.findViewById<View>(android.R.id.button1)).performClick())
        }
        await("local dialog dismissed") { owner.hasWindowFocus() }
    }
    private fun importBusiness(initial: Activity, scope: Scope, directory: File) {
        var activity=initial; val frozen=invariant(scope); val cases=JSONArray()
        val nativeTrace=ImeRecreationTrace(test,directory)
        val transitionProbe=ImeViewportTransitionProbe("Import.raw.recreate",{ owner,attempt ->
            attempt.value("rawCount",null);attempt.value("rawCountStatus","not_evaluated")
            attempt.value("middleCount",null);attempt.value("middleCountStatus","not_evaluated")
            attempt.value("hostCount",null);attempt.value("hostCountStatus","not_evaluated")
            attempt.stage("input-read");val raw=input(owner);attempt.viewReference("input",raw)
            attempt.stage("page-root-read");val pageRoot=root(owner);attempt.viewReference("pageRoot",pageRoot)
            attempt.stage("form-scroll-read");val middle=field(owner,"formScroll") as ScrollView
            attempt.viewReference("formScroll",middle)
            attempt.stage("raw-count");val rawCount=nodes(pageRoot).count { it===raw }
            attempt.value("rawCount",rawCount);attempt.value("rawCountStatus","evaluated")
            attempt.stage("middle-count");val middleCount=if(rawCount==1) nodes(pageRoot).count { it===middle } else null
            if(middleCount!=null) {attempt.value("middleCount",middleCount);attempt.value("middleCountStatus","evaluated")}
            attempt.stage("first-check");check(rawCount==1 && middleCount==1)
            attempt.stage("parent-host-read");val host=middle.parent as? View;attempt.viewReference("parentHost",host)
            attempt.stage("host-count");val hostCount=if(host!=null) nodes(pageRoot).count { it===host } else null
            if(hostCount!=null) {attempt.value("hostCount",hostCount);attempt.value("hostCountStatus","evaluated")}
            attempt.stage("second-check");check(host!=null && hostCount==1)
            attempt.stage("targets");val scrolls=nodes(pageRoot).filterIsInstance<ScrollView>()
            ImeViewportTransitionProbe.Targets(pageRoot,host,raw,
                scrolls)
        },{ owner -> (owner as? CurveImportActivity)?.productRestoreEvidence })
        fun record(name: String) {
            cases.put(JSONObject().put("case",name).put("modelsUnchanged",invariant(scope)==frozen))
            File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        fun errorText()=(field(activity,"error") as TextView).text.toString()
        main { check(input(activity).text.isEmpty() && !action(activity,R.string.profiles_import_preview).isEnabled) }
        // Raw draft is intentionally invalid and multiline: test the original restoration contract without decoding it.
        val raw="  {未完成的原文\n  保留空格与换行}\n"+(1..90).joinToString("\n") { "  未完成行 $it  " }
        main { input(activity).setText(raw); check(input(activity).requestFocus())
            input(activity).setSelection(raw.length-15,raw.length-7)
            nativeTrace.observe("fixture_setup_show_before",activity,input(activity),"CurveVisualMigrationChecks.importBusiness.setupShow")
            (activity.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).showSoftInput(input(activity),0)
            nativeTrace.observe("fixture_setup_show_returned",activity,input(activity),"CurveVisualMigrationChecks.importBusiness.setupShow") }
        await("Import actual IME") { requireNotNull(root(activity).rootWindowInsets).isVisible(WindowInsets.Type.ime()) }
        main { val view=input(activity); val layout=requireNotNull(view.layout)
            view.scrollTo(0,(layout.height-view.height+view.compoundPaddingTop+view.compoundPaddingBottom).coerceAtLeast(0)) }
        stableWindow(activity,directory,"raw-before-recreate")
        lateinit var before: JSONObject
        try {
            main {
                before=rawState(activity)
                transitionProbe.install(activity,"old-before-recreate")
                transitionProbe.sample("before-state-assertion")
                // Preserve the actual pre-assertion state in this SAME main turn, even if its establishment failed.
                File(directory,"raw-ime-geometry.json").writeText(JSONObject().put("geometry",geometry(activity))
                    .put("state",before).put("phase","before_focus_nonzero_scroll_and_footer_assertions")
                    .put("sameCompositorFrameProven",false).toString(2))
                check(before.getBoolean("focused") && before.getInt("scrollY")>0) {
                    "M3 raw draft state not established: focused=${before.getBoolean("focused")} scrollY=${before.getInt("scrollY")} " +
                        "inputHeightPx=${input(activity).height}; original focus/nonzero-scroll gate is unchanged"
                }
                listOf(R.string.beans_cancel,R.string.profiles_import_preview).forEach { id ->
                    val button=action(activity,id); check(whole(observation(button)) && button.height>=HoyiUi.dp(activity,48)) }
            }
            diagnostic(activity,File(directory,"raw-ime"))
        } catch(error:Throwable) {
            try { main { transitionProbe.closeAndWrite(File(directory,"ime-viewport-transitions.json")) } }
            catch(secondary:Throwable) { error.addSuppressed(secondary) }
            throw error
        }
        var restorationFailure:Throwable?=null
        var oldSnapshot:ImeRecreationTrace.Snapshot?=null
        var newSnapshot:ImeRecreationTrace.Snapshot?=null
        val observedRoots=mutableListOf<Pair<View,View.OnAttachStateChangeListener>>()
        fun observeRoot(owner:Activity) {
            val view=owner.window.decorView
            if(observedRoots.any { (observed,_) -> observed===view }) return
            val listener=object:View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(attached:View) { nativeTrace.observe("decor_attached",owner,attached,viewRole="decor") }
                override fun onViewDetachedFromWindow(detached:View) { nativeTrace.observe("decor_detached",owner,detached,viewRole="decor") }
            }
            view.addOnAttachStateChangeListener(listener)
            observedRoots.add(view to listener)
        }
        val lifecycle=object:Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(owner:Activity,state:Bundle?) { if(owner is CurveImportActivity) {
                nativeTrace.observe("activity_created",owner) } }
            override fun onActivityPostCreated(owner:Activity,state:Bundle?) { if(owner is CurveImportActivity) {
                nativeTrace.observe("activity_post_created",owner)
                transitionProbe.install(owner,"replacement-post-created"); observeRoot(owner) } }
            override fun onActivityStarted(owner:Activity) { if(owner is CurveImportActivity) nativeTrace.observe("activity_started",owner) }
            override fun onActivityResumed(owner:Activity) { if(owner is CurveImportActivity) nativeTrace.observe("activity_resumed",owner) }
            override fun onActivityPaused(owner:Activity) { if(owner is CurveImportActivity) nativeTrace.observe("activity_paused",owner) }
            override fun onActivityStopped(owner:Activity) { if(owner is CurveImportActivity) nativeTrace.observe("activity_stopped",owner) }
            override fun onActivitySaveInstanceState(owner:Activity,state:Bundle) { if(owner is CurveImportActivity) nativeTrace.observe("activity_save_state",owner) }
            override fun onActivityDestroyed(owner:Activity) { if(owner is CurveImportActivity) nativeTrace.observe("activity_destroyed",owner) }
        }
        try {
            nativeTrace.start()
            main { oldSnapshot=nativeTrace.capture("old_before_recreate",activity,input(activity),activity.window.decorView)
                observeRoot(activity); app.registerActivityLifecycleCallbacks(lifecycle) }
            val monitor=test.addMonitor(CurveImportActivity::class.java.name,null,false)
            try {
                nativeTrace.mark("recreate_call_before","CurveVisualMigrationChecks.importBusiness.activity.recreate")
                main { activity.recreate() }; activity=test.waitForMonitorWithTimeout(monitor,10000) ?: error("M3 Import recreate missing")
                nativeTrace.mark("replacement_returned","CurveVisualMigrationChecks.importBusiness.waitForMonitorWithTimeout")
                main { newSnapshot=nativeTrace.capture("new_after_replacement_returned",activity,input(activity),activity.window.decorView)
                    observeRoot(activity); nativeTrace.compare(requireNotNull(oldSnapshot),requireNotNull(newSnapshot)) }
            }
            finally { test.removeMonitor(monitor) }
            await("Import restored ready") { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
            stableWindow(activity,directory,"raw-after-recreate",transitionProbe)
            nativeTrace.mark("after_comparison_before","CurveVisualMigrationChecks.importBusiness.rawStateComparison")
            main { val after=rawState(activity)
                transitionProbe.sample("after-state-assertion")
                nativeTrace.observe("after_comparison",activity,input(activity),"CurveVisualMigrationChecks.importBusiness.rawStateComparison")
                File(directory,"raw-restoration.json").writeText(JSONObject().put("before",before).put("after",after)
                    .put("afterGeometryBeforeAssertion",geometry(activity)).toString(2))
                check(after.toString()==before.toString()) { "M3 raw text/focus/selection/scroll changed before screenshot side effects" } }
            check(invariant(scope)==frozen); record("raw-exact-text-selection-focus-nonzero-scroll-recreate")
        } catch(error:Throwable) {
            restorationFailure=error
            // Preserve the original failure window before stopping tracing or changing focus/IME.
            try {diagnostic(activity,File(directory,"raw-recreate-failure"))}catch(secondary:Throwable) {error.addSuppressed(secondary)}
            throw error
        } finally {
            var finalizationFailure:Throwable?=null
            fun preserveFinalization(error:Throwable, reportName:String) {
                val primary=restorationFailure ?: finalizationFailure
                if(primary!=null) primary.addSuppressed(error)
                else try { File(directory,reportName).writeText(error.stackTraceToString()) }
                catch(writeError:Throwable) { finalizationFailure=error;error.addSuppressed(writeError) }
            }
            try {main { transitionProbe.closeAndWrite(File(directory,"ime-viewport-transitions.json")) }}
            catch(secondary:Throwable) { val primary=restorationFailure
                if(primary!=null)primary.addSuppressed(secondary) else finalizationFailure=secondary }
            var restoredEvidence:JSONObject?=null
            try {main { observedRoots.forEach { (view,listener) -> view.removeOnAttachStateChangeListener(listener) }
                app.unregisterActivityLifecycleCallbacks(lifecycle)
                if(activity is CurveImportActivity) restoredEvidence=JSONObject((activity as CurveImportActivity).productRestoreEvidence.toString())
                nativeTrace.observe("observers_removed",activity) }
                restoredEvidence?.let(nativeTrace::productEvidence)
            } catch(secondary:Throwable) { preserveFinalization(secondary,"ime-causal-links-finalization-error.txt") }
            try {nativeTrace.finish()}catch(secondary:Throwable) {
                preserveFinalization(secondary,"ime-trace-finalization-error.txt")
            }
            if(restorationFailure==null) finalizationFailure?.let { throw it }
        }
        unfocus(activity,nativeTrace)
        nativeTrace.observe("fixture_unfocus_returned",callerSite="CurveVisualMigrationChecks.importBusiness.unfocus")
        captureState(activity,directory,"raw-restored")
        for((index,invalid) in listOf("{bad",scope.document.encode().replace("\"schema\":1","\"schema\":99"),
            scope.document.copy(temperatureC=200).toJson().toString()).withIndex()) {
            check(runCatching { CustomCurveDocument.decode(invalid) }.isFailure)
            main { input(activity).setText(invalid); (field(activity,"error") as TextView).text=""
                check(action(activity,R.string.profiles_import_preview).isEnabled) }
            click(activity,R.string.profiles_import_preview)
            main { check(activity.hasWindowFocus() && !activity.isFinishing && input(activity).text.toString()==invalid &&
                errorText()==activity.getString(R.string.profiles_import_invalid)) }
            check(invariant(scope)==frozen); captureState(activity,directory,"invalid-$index")
            main { check(whole(observation(field(activity,"error") as TextView))) { "M3 inline invalid-input error clipped" } }
            record("invalid-$index-no-write")
        }
        val pasted="  \n${scope.document.encode()}\n  "
        scope.pasteFixture(activity,pasted); click(activity,R.string.profiles_paste)
        main { check(input(activity).text.toString()==pasted && errorText().isEmpty()) }
        check(invariant(scope)==frozen)
        val previewMonitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
        val preview: Activity
        try { click(activity,R.string.profiles_import_preview)
            preview=test.waitForMonitorWithTimeout(previewMonitor,10000) ?: error("M3 pasted valid document did not preview") }
        finally { test.removeMonitor(previewMonitor) }
        await("pasted real preview ready") { preview.hasWindowFocus() && root(preview).isLaidOut }
        check(invariant(scope)==frozen); close(preview)
        await("raw return after preview") { activity.hasWindowFocus() }
        main { check(input(activity).text.toString()==pasted) }; record("explicit-paste-preview-return-original-whitespace-no-write")
        val session=scope.createProvider(scope.document)
        fun picker(code: Int, uri: Uri?) {
            var observed: Intent?=null; var count=0
            val intercept=object: Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if(intent.action!=Intent.ACTION_OPEN_DOCUMENT) {
                        check(intent.component?.className==CurveImportPreviewActivity::class.java.name) { "M3 unexpected external launch" }
                        return null
                    }
                    observed=Intent(intent); count++
                    return Instrumentation.ActivityResult(code,uri?.let { Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                }
            }
            test.addMonitor(intercept)
            try {
                click(activity,R.string.profiles_open_file); scope.observeImportThreads()
                main { val intent=requireNotNull(observed)
                    check(count==1 && intent.action==Intent.ACTION_OPEN_DOCUMENT && intent.type=="*/*" &&
                        intent.categories==setOf(Intent.CATEGORY_OPENABLE) && intent.component==null && intent.`package`==null)
                    check(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList()==listOf("application/json","text/plain")) }
            } finally { test.removeMonitor(intercept) }
        }
        main { input(activity).setText(pasted) }
        val priorStats=scope.provider("stats")
        picker(Activity.RESULT_CANCELED,null); scope.drainImportThreads()
        main { check(input(activity).text.toString()==pasted) }
        check(scope.provider("stats").keySet().all { scope.provider("stats").getInt(it)==priorStats.getInt(it) } && invariant(scope)==frozen)
        record("SAF-cancel-preserves-draft-no-read")
        for(name in listOf("output.json","oversize.json","malformed.json")) {
            main { (field(activity,"error") as TextView).text="" }
            picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,name))
            await("SAF $name real failure callback") { errorText()==activity.getString(R.string.profiles_import_invalid) }
            scope.drainImportThreads()
            main { check(input(activity).text.toString()==pasted && activity.hasWindowFocus()) }
            check(invariant(scope)==frozen); captureState(activity,directory,"SAF-failed-$name")
            main { check(whole(observation(field(activity,"error") as TextView))) { "M3 SAF error clipped" } }
            record("SAF-$name-preserves-draft-no-write")
        }
        val successMonitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
        val fromFile: Activity
        try { picker(Activity.RESULT_OK,CurveFixtureProvider.uri(session,"valid.json"))
            fromFile=test.waitForMonitorWithTimeout(successMonitor,10000) ?: error("M3 valid UTF8 SAF did not directly enter preview") }
        finally { test.removeMonitor(successMonitor) }
        scope.drainImportThreads(); await("file preview ready") { fromFile.hasWindowFocus() && root(fromFile).isLaidOut }
        check(invariant(scope)==frozen); close(fromFile)
        await("Import foreground after file preview") { activity.hasWindowFocus() }
        main { check(input(activity).text.toString()==scope.document.encode()) }
        val stats=scope.provider("stats")
        check(stats.getInt("read:valid.json")==1 && stats.getInt("read:malformed.json")==1 && stats.getInt("read:oversize.json")==1)
        check(stats.keySet().filter { it.startsWith("write:") }.all { stats.getInt(it)==0 })
        record("SAF-valid-strict-UTF8-direct-preview-no-write")
        scope.restoreClipboard(activity)
        click(activity,R.string.beans_cancel); await("Import cancel destroyed") { activity.isDestroyed }
        check(invariant(scope)==frozen && services().isEmpty()); record("cancel-no-store-or-machine-write")
        File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases)
            .put("providerStats",JSONObject(stats.keySet().associateWith { stats.getInt(it) }))
            .put("completed",true).put("noMachineOrServiceCommands",true).put("modelsAndUsageUnchanged",true).toString(2))
    }
    private fun previewReachability(activity: Activity, directory: File) {
        val rows=mutableListOf<View>(); val reports=JSONArray(); val scrolls=nodes(root(activity)).filterIsInstance<ScrollView>()
        val prior=scrolls.map { Triple(it,it.scrollX,it.scrollY) }; val dock=requireNotNull(root(activity).findViewWithTag<View>("product/import-preview/dock"))
        lateinit var fixed: JSONObject
        main {
            val table=requireNotNull(root(activity).findViewWithTag<ViewGroup>("import-stage-table")); check(table.childCount==5)
            (0 until 5).forEach { rows.add(table.getChildAt(it)) }
            fixed=observation(dock)
        }
        val chart=nodes(root(activity)).filterIsInstance<CurveStageView>().single()
        val targets=rows+listOf(chart)+listOf("stage-caption","scope","compatibility","ability","full-details").map { requireNotNull(root(activity).findViewWithTag<View>("product/import-preview/$it")) }
        val standard=activity.resources.configuration.let { it.screenWidthDp>=1000 && it.screenHeightDp>=600 && it.fontScale<=1.1f }
        val initial=mutableListOf<JSONObject>()
        main { targets.forEach { initial.add(observation(it)) } }
        var failed=false
        try {
            targets.forEachIndexed { index,view ->
                val before=initial[index]
                main { view.requestRectangleOnScreen(Rect(0,0,view.width,view.height),true) }
                frames.committedFrame(root(activity))
                lateinit var after: JSONObject; var fixedDock=false
                main { after=observation(view); fixedDock=observation(dock).getJSONArray("rectPx").toString()==fixed.getJSONArray("rectPx").toString() }
                val initialRequired=standard && index<6 // Original five-row table plus chart, sampled together before any reveal.
                val pass=whole(after) && fixedDock && (!initialRequired || whole(before))
                reports.put(JSONObject().put("targetIndex",index).put("before",before).put("after",after)
                    .put("initialRequired",initialRequired).put("fixedDock",fixedDock).put("status",if(pass) "PASS_LAYOUT_ONLY" else "FAIL"))
                captureState(activity,directory,"reachable-$index")
                failed=failed || !pass
            }
        } finally {
            main { prior.forEach { (view,x,y) -> view.scrollTo(x,y) } }
            frames.committedFrame(root(activity))
            val restored=prior.all { (view,x,y) -> view.scrollX==x && view.scrollY==y }
            File(directory,"scroll-reachability.json").writeText(JSONObject().put("targets",reports).put("scrollRestored",restored).toString(2))
            check(restored) { "M3 Preview scroll restoration failed" }
        }
        check(!failed) { "M3 Preview actual text/table reachability or standard initial full-table gate failed; original PNGs retained" }
    }
    private fun previewBusiness(initial: Activity, scope: Scope, directory: File) {
        val fixedDomain=nonCustomInvariant(scope); val cases=JSONArray(); val original=scope.custom.list()
        fun expectDomain() { check(nonCustomInvariant(scope)==fixedDomain && services().isEmpty()) }
        fun record(name: String) {
            expectDomain(); cases.put(JSONObject().put("case",name).put("documents",JSONArray(scope.custom.list().map { it.toJson() })))
            File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",false).toString(2))
        }
        click(initial,R.string.profiles_existing); dismissDialog(initial)
        check(scope.custom.list()==original); record("conflict-view-local-no-write")
        click(initial,R.string.profiles_keep_existing); await("keep existing closes Preview") { initial.isDestroyed }
        check(scope.custom.list()==original); record("conflict-keep-no-write")
        var page=start(CurveImportPreviewActivity::class.java,scope.incoming)
        scope.rejectCustomWrites=true
        try { click(page,R.string.profiles_save_copy)
            main { check(action(page,R.string.profiles_save_copy).isEnabled && !page.isFinishing &&
                nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_save_failed) }) }
            check(scope.custom.list()==original); captureState(page,directory,"copy-write-failed"); record("copy-write-failure-reenabled-no-publish")
        } finally { scope.rejectCustomWrites=false }
        val attempts=scope.customWriteAttempts
        click(page,R.string.profiles_save_copy); await("explicit copy retry closes Preview") { page.isDestroyed }
        val copy=scope.custom.list().single { it.id!=scope.document.id }
        check(copy.id!=scope.document.id && copy.copy(id=scope.incoming.id)==scope.incoming && scope.custom.find(scope.document.id)==scope.document &&
            scope.customWriteAttempts==attempts+1); record("copy-explicit-retry-new-id-original-unchanged")
        val fresh=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000302",name="无冲突配方")
        page=start(CurveImportPreviewActivity::class.java,fresh)
        val beforeSave=scope.custom.list()
        scope.rejectCustomWrites=true
        try {
            click(page,R.string.profiles_import_save)
            main { check(!page.isFinishing && action(page,R.string.profiles_import_save).isEnabled &&
                nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_save_failed) }) }
            check(scope.custom.list()==beforeSave); captureState(page,directory,"save-write-failed"); record("save-write-failure-reenabled-no-publish")
        } finally { scope.rejectCustomWrites=false }
        val attemptsBefore=scope.customWriteAttempts
        click(page,R.string.profiles_import_save); await("new import saved") { page.isDestroyed }
        check(scope.custom.list()==beforeSave+fresh && scope.customWriteAttempts==attemptsBefore+1); record("no-conflict-explicit-retry-save-once")
        page=start(CurveImportPreviewActivity::class.java,fresh)
        val repeatBefore=scope.custom.list(); val repeatAttempts=scope.customWriteAttempts
        click(page,R.string.profiles_import_save); await("identical repeated import finished") { page.isDestroyed }
        check(scope.custom.list()==repeatBefore && scope.customWriteAttempts==repeatAttempts); record("identical-repeat-idempotent-no-conflict-no-write")
        val runtime=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000303",name="运行时冲突输入")
        page=start(CurveImportPreviewActivity::class.java,runtime)
        val existing=runtime.copy(name="运行时已存本机")
        scope.custom.save(existing); val runtimeBefore=scope.custom.list()
        click(page,R.string.profiles_import_save)
        main { check(!page.isFinishing && root(page).findViewWithTag<View>("import-conflict-notice")!=null &&
            action(page,R.string.profiles_save_copy).isEnabled) }
        check(scope.custom.list()==runtimeBefore); captureState(page,directory,"runtime-conflict")
        click(page,R.string.profiles_keep_existing); await("runtime keep closes Preview") { page.isDestroyed }
        check(scope.custom.list()==runtimeBefore); record("runtime-conflict-rerender-no-overwrite")
        val flow=scope.document.copy(id="draft-00000000-0000-0000-0000-000000000304",name="FlowRaw只读参数",controlMode=CustomCurveDocument.ControlMode.FLOW_RAW)
        page=start(CurveImportPreviewActivity::class.java,flow)
        main { check(nodes(root(page)).filterIsInstance<CurveStageView>().isEmpty())
            check(CustomPressureCurveAdapter.profile(flow,false)==null && nodes(root(page)).filterIsInstance<TextView>().any { it.text.toString()==page.getString(R.string.profiles_read_only) }) }
        val flowBefore=scope.custom.list(); captureState(page,directory,"flow-raw-read-only"); close(page)
        check(scope.custom.list()==flowBefore); record("FlowRaw-no-fake-pressure-no-write")
        val invalid=test.startActivitySync(Intent(context,CurveImportPreviewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(CurveImportPreviewActivity.DOCUMENT,"{bad"))
        await("invalid Preview inline error") { invalid.hasWindowFocus() && root(invalid).isLaidOut }
        main { check(nodes(root(invalid)).filterIsInstance<TextView>().any { it.text.toString()==invalid.getString(R.string.profiles_import_invalid) }) }
        check(scope.custom.list()==flowBefore); captureState(invalid,directory,"invalid-document"); close(invalid); record("invalid-document-no-write")
        File(directory,"curve-business.json").writeText(JSONObject().put("cases",cases).put("completed",true)
            .put("noMachineOrServiceCommands",true).put("nonCustomModelsAndUsageUnchanged",true)
            .put("scopedCustomOnlyExpectedWrites",true).toString(2))
    }
    /** One real page/profile per fresh process. The host must require all three pages × four profiles. */
    fun run(arguments: Bundle): Int {
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock")
        check(arguments.getString("disposableCiEmulator")=="true" && arguments.getString("freshProcessVerified")=="true")
        check(Build.HARDWARE in setOf("ranchu","goldfish") && Build.VERSION.SDK_INT>=34)
        val page=requireNotNull(arguments.getString("curvePage")); check(page in setOf("library","import","preview"))
        val source=requireNotNull(arguments.getString("sourceSha")); check(source.matches(Regex("[0-9a-f]{40}")))
        val dark=arguments.getString("sampleTheme")=="dark"; val scale=requireNotNull(arguments.getString("expectedFontScale")).toFloat()
        val profile="tablet-user-landscape-${if(dark) "dark" else "light"}-font-$scale"
        val directory=File(requireNotNull(context.getExternalFilesDir(null)),"curve-visual-migration/$source/$profile/$page").apply { check(isDirectory || mkdirs()) }
        val manifest=JSONObject().put("sourceSha",source).put("sourceDirty",arguments.getString("sourceDirty"))
            .put("headSha",arguments.getString("headSha")).put("apkSha256",arguments.getString("apkSha"))
            .put("ciRun",arguments.getString("ciRun")).put("ciJob",arguments.getString("ciJob"))
            .put("profile",profile).put("page",page).put("realProductActivity",true).put("specimenOnly",false).put("visualAcceptance","ASTRA_PENDING")
            .put("pid",android.os.Process.myPid()).put("deliveryScope","one_of_M3_three_real_Activities_by_four_profiles; host_requires_complete_12")
        var scope: Scope?=null; var failure: Throwable?=null; var isolatedScopes=0
        try {
            val active=Scope(directory,page); scope=active; active.prepare(); active.install(dark)
            File(directory,"fixture.json").writeText(active.fixture.toString(2))
            val selectionPrefsBefore=context.getSharedPreferences("curves",Context.MODE_PRIVATE).all.toMap()
            val activity=when(page) {
                "library" -> start(CurveActivity::class.java)
                "import" -> start(CurveImportActivity::class.java)
                else -> start(CurveImportPreviewActivity::class.java,active.incoming)
            }
            unfocus(activity)
            frames.committedFrame(root(activity))
            check(services().isEmpty())
            val captured=frames.screenshot(activity,root(activity),File(directory,"window.png")) { geometry(activity) }
            val image=android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds=true }
            android.graphics.BitmapFactory.decodeFile(File(directory,"window.png").absolutePath,image)
            manifest.put("wholeWindowPngSizePx",JSONArray(listOf(image.outWidth,image.outHeight)))
                .put("wholeWindowPngSizeSource","original_unmodified_window.png_header_not_resource_metrics_or_physical_panel_claim")
                .put("initialFrameSync",captured.proof)
            check(image.outWidth==2560 && image.outHeight==1600 && captured.proof.getInt("protocolVersion")==2 && captured.proof.getBoolean("accepted"))
            val font=context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
                val digest=MessageDigest.getInstance("SHA-256"); val bytes=ByteArray(8192)
                while(true) { val count=input.read(bytes); if(count<0) break; digest.update(bytes,0,count) }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
            manifest.put("fontSha256",font); check(font==TaskProductVisualStyle.FONT_SHA)
            main {
                val actual=geometry(activity); manifest.put("window",actual)
                File(directory,"geometry.json").writeText(actual.toString(2))
                check(actual.getDouble("fontScale").toFloat()==scale && actual.getString("uiModeNightMeaning")==if(dark) "dark" else "light")
                check(actual.getInt("densityDpi")==360 && actual.getString("locale").startsWith("zh") && !actual.getBoolean("imeVisible"))
                TaskTypographyEvidence.capture(activity,directory)
                if(page=="import") {
                    check(input(activity).text.isEmpty() && !action(activity,R.string.profiles_import_preview).isEnabled)
                    check(nodes(root(activity)).filterIsInstance<EditText>().size==1)
                    listOf(R.string.profiles_paste,R.string.profiles_open_file,R.string.beans_cancel,R.string.profiles_import_preview).forEach { id ->
                        val view=action(activity,id); check(whole(observation(view)) && view.height>=HoyiUi.dp(activity,48))
                    }
                } else {
                    val pairs=baselines(activity); File(directory,"baseline-alignment.json").writeText(pairs.toString(2))
                    val chart=nodes(root(activity)).filterIsInstance<CurveStageView>().single { it.productDrawEvidence.length()>0 }
                    File(directory,"canvas.json").writeText(JSONObject().put("targets",JSONArray(chart.targets))
                        .put("actualDrawOperations",chart.productDrawEvidence).toString(2))
                    check(chart.targets==if(page=="library") active.document.stages.map { it.target } else active.incoming.stages.map { it.target })
                    check(chart.isShown && chart.height>=HoyiUi.dp(activity,160)) { "M3 main pressure fixture chart missing or under 160dp" }
                    (0 until pairs.length()).forEach { check(pairs.getJSONObject(it).getString("status")!="FAIL") }
                    if(page=="library") {
                        check(list(activity).count==active.curves.items.size && list(activity).count==104)
                        check((field(activity,"selected") as CurveLibraryItem).id==active.document.id)
                        val actions=listOf(field(activity,"parameterShortcut") as Button,field(activity,"select") as Button)
                        actions.forEach { check(whole(observation(it)) && it.height>=HoyiUi.dp(activity,48)) }
                        check(kotlin.math.abs((actions[0].top+actions[0].bottom)-(actions[1].top+actions[1].bottom))<=1) {
                            "M3 original doubled-integer footer center constraint failed" }
                    } else {
                        check(root(activity).findViewWithTag<View>("import-conflict-notice")!=null && active.custom.find(active.document.id)==active.document)
                        listOf(R.string.profiles_existing,R.string.profiles_keep_existing,R.string.profiles_save_copy).forEach { id ->
                            val view=action(activity,id); check(whole(observation(view)) && view.height>=HoyiUi.dp(activity,48)) }
                    }
                }
            }
            when(page) {
                "library" -> {
                    libraryBusiness(activity,active,directory)
                    File(directory,"curve-business.json").writeText(JSONObject(File(directory,"continuous-list-business.json").readText()).toString(2))
                    scope=null; active.cleanup(); isolatedScopes++
                    val specialDirectory=File(directory,"library-special-scope").apply { check(isDirectory || mkdirs()) }
                    val special=Scope(specialDirectory,page,true); scope=special
                    special.prepare(); special.install(dark)
                    File(specialDirectory,"fixture.json").writeText(special.fixture.toString(2))
                    val candidates=librarySpecialVisualCases(special,specialDirectory)
                    val qualified=candidates.first().takeIf { it.factoryCurve!=null && special.curves.canStart(it) &&
                        runCatching { CustomCurveDocument.fromLibraryItem(it).controlMode==CustomCurveDocument.ControlMode.PRESSURE }.getOrDefault(false) }
                        ?: special.curves.items.filter { it.factoryCurve!=null && special.curves.canStart(it) &&
                            runCatching { CustomCurveDocument.fromLibraryItem(it).controlMode==CustomCurveDocument.ControlMode.PRESSURE }.getOrDefault(false) }
                            .minByOrNull { it.id } ?: error("M3 no real slot-eligible copyable factory")
                    librarySlots(special,specialDirectory,qualified,candidates[1],candidates[2])
                    librarySourceRoutes(special,specialDirectory,qualified)
                    check(special.customWriteAttempts==2 && special.custom.list().map { it.id }==
                        listOf(special.document.id,requireNotNull(special.readonly).id) && services().isEmpty())
                    File(specialDirectory,"special-scope-business.json").writeText(JSONObject()
                        .put("expectedOwnedCustomWrites",2).put("actualOwnedCustomWrites",special.customWriteAttempts)
                        .put("ownedDocumentIds",JSONArray(special.custom.list().map { it.id }))
                        .put("specialLibraryCount",special.curves.items.size)
                        .put("originalMainScopeAlreadyCleaned",isolatedScopes==1)
                        .put("noService",services().isEmpty()).toString(2))
                    scope=null; special.cleanup(); isolatedScopes++
                    manifest.put("libraryVisualCaseCount",6).put("libraryBusinessCaseCount",2)
                        .put("librarySpecialActualCount",special.curves.items.size)
                        .put("mainLibraryOriginalCount",104)
                }
                "import" -> importBusiness(activity,active,directory)
                else -> { previewReachability(activity,directory); previewBusiness(activity,active,directory) }
            }
            if(page!="library") check(context.getSharedPreferences("curves",Context.MODE_PRIVATE).all==selectionPrefsBefore) {
                "M3 Import/Preview changed curve selection/sort or unrelated preferences" }
            manifest.put("layoutStatus","PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
        } catch(error: Throwable) {
            failure=error; manifest.put("failure",error.stackTraceToString()).put("layoutStatus","FAIL")
            scope?.activeActivity()?.let { owner -> try {
                lateinit var before: JSONObject; main { before=geometry(owner) }
                val bitmap=requireNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory,"failure-context.png").outputStream().use { check(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
                lateinit var after: JSONObject; main { after=geometry(owner) }
                File(directory,"failure-context-geometry.json").writeText(JSONObject().put("before",before).put("after",after)
                    .put("geometryUnchanged",before.toString()==after.toString()).put("sameCompositorFrameProven",false).toString(2))
            } catch(captureFailure: Throwable) { error.addSuppressed(captureFailure) } }
        } finally {
            try {
                scope?.let { it.cleanup(); isolatedScopes++ }
                manifest.put("isolationStatus", if(isolatedScopes==(if(page=="library") 2 else 1))
                    "PASS_ISOLATION_ONLY" else "FAIL")
                manifest.put("isolatedOwnedScopeCount",isolatedScopes)
            } catch(error: Throwable) {
                manifest.put("isolationStatus","FAIL").put("cleanupFailure",error.stackTraceToString())
                if(failure==null) failure=error else failure!!.addSuppressed(error)
            }
            manifest.put("overallStatus",if(failure==null) "PASS_MECHANICAL_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            failure?.let { manifest.put("failure",it.stackTraceToString()) }
            manifest.put("fileSha256",JSONObject(directory.walkTopDown().filter { it.isFile && it!=File(directory,"manifest.json") }.associate { it.relativeTo(directory).path to sha(it) }))
            File(directory,"manifest.json").writeText(manifest.toString(2))
        }
        failure?.let { throw it }; return 1
    }
}
