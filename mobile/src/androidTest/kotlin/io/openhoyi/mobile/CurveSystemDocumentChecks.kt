package io.openhoyi.mobile

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Real DocumentsUI acceptance only on an explicitly disposable CI emulator + isolated Mock app.
 * No chooser result interception, ACTION_SEND, BLE controls, Alpha package, or unknown-file deletion. */
internal class CurveSystemDocumentChecks(private val test:Instrumentation) {
    data class Result(val paths:List<String>,val newDrafts:Int,val ownDraftId:String,
        val exportedUri:String,val systemPicker:Boolean=true,val externalSend:Boolean=false,val noBle:Boolean=true)
    private val context get()=test.targetContext
    private val opened=mutableListOf<Activity>()
    private var pickerPackage=""
    private class RootNotReady : IllegalStateException("Accessibility active window is not ready")
    private fun status(line:String)=test.sendStatus(0,Bundle().apply {putString("stream",line+"\n")})
    private fun onMain(action:()->Unit) {
        var failure:Throwable?=null
        test.runOnMainSync {try {action()} catch(error:Throwable) {failure=error}}
        failure?.let {throw it}
    }
    private fun await(label:String,condition:()->Boolean) {
        val deadline=SystemClock.elapsedRealtime()+15_000
        while(true) {
            val ready=try {condition()} catch(_:RootNotReady) {false}
            if(ready)return
            if(SystemClock.elapsedRealtime()>=deadline) {
                dumpTree("timeout-$label");error("Real system document condition timed out: $label")
            }
            SystemClock.sleep(50)
        }
    }
    private fun awaitMain(label:String,condition:()->Boolean)=await(label) {
        var ready=false;onMain {ready=condition()};ready
    }
    private fun views(view:View):List<View> = listOf(view)+
        if(view is ViewGroup)(0 until view.childCount).flatMap {views(view.getChildAt(it))} else emptyList()
    private fun button(activity:Activity,label:Int):TextView = views(activity.window.decorView)
        .filterIsInstance<TextView>().single {it.isShown && it.isClickable && it.text.toString()==activity.getString(label)}
    private fun click(activity:Activity,label:Int)=onMain {
        val action=button(activity,label);check(action.isEnabled)
        action.requestRectangleOnScreen(Rect(0,0,action.width,action.height),true)
        check(action.performClick())
    }
    private fun start(type:Class<out Activity>,clearTask:Boolean=false,extras:(Intent)->Unit={}):Activity {
        val intent=Intent(context,type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(extras)
        if(clearTask)intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return test.startActivitySync(intent).also {activity->
            opened+=activity;awaitMain("${type.simpleName}-ready") {activity.hasWindowFocus() && activity.window.decorView.isLaidOut}
        }
    }
    private fun close(activity:Activity) {
        onMain {if(!activity.isDestroyed)activity.finish()}
        awaitMain("${activity.javaClass.simpleName}-closed") {activity.isDestroyed};opened.remove(activity)
    }
    private fun owner(home:Activity):MobileService? = HomeActivity::class.java.getDeclaredField("service")
        .apply {isAccessible=true}.get(home) as? MobileService
    private fun shareUri(share:Activity):Uri?=CurveShareActivity::class.java.getDeclaredField("exportedUri")
        .apply {isAccessible=true}.get(share) as? Uri
    private fun document(share:Activity):CustomCurveDocument=CurveShareActivity::class.java.getDeclaredField("document")
        .apply {isAccessible=true}.get(share) as? CustomCurveDocument ?: error("Share document missing")
    private fun isSystemPackage(name:String):Boolean = test.context.packageManager.getApplicationInfo(name,0).flags and
        (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)!=0
    private fun emulatorGate(disposableCiEmulator:Boolean) {
        check(disposableCiEmulator) {"Caller must explicitly attest this is a disposable CI emulator"}
        check(BuildConfig.MOCK_MODE && context.packageName=="io.openhoyi.mobile.mock" &&
            test.context.packageName=="io.openhoyi.mobile.mock.test") {"Real picker checks are isolated Mock-package-only"}
        check(Build.HARDWARE in setOf("ranchu","goldfish")) {"Not a recognized Android emulator hardware: ${Build.HARDWARE}"}
        val qemu=test.uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use {descriptor->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use {it.readText().trim()}
        }
        check(qemu=="1") {"ro.kernel.qemu is not 1; user/physical devices are forbidden"}
        val intent=Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
        val resolved=requireNotNull(test.context.packageManager.resolveActivity(intent,PackageManager.MATCH_DEFAULT_ONLY)) {"No system document picker"}
        pickerPackage=resolved.activityInfo.packageName
        check(pickerPackage in setOf("com.android.documentsui","com.google.android.documentsui") && isSystemPackage(pickerPackage)) {
            "Unexpected/non-system document picker: $pickerPackage"
        }
        status("CURVE_SYSTEM_DOCUMENT_ENV sdk=${Build.VERSION.SDK_INT} hardware=${Build.HARDWARE} qemu=$qemu picker=$pickerPackage disposableCiEmulator=true systemPicker=true externalSend=false noBle=true")
    }
    @Suppress("DEPRECATION")
    private fun <T> tree(action:(List<AccessibilityNodeInfo>)->T):T {
        val root=test.uiAutomation.rootInActiveWindow ?: throw RootNotReady()
        val nodes=mutableListOf<AccessibilityNodeInfo>()
        fun visit(node:AccessibilityNodeInfo,depth:Int) {
            check(depth<=40 && nodes.size<4000) {"Unexpected accessibility tree size"}
            nodes+=node
            repeat(node.childCount) {index->node.getChild(index)?.let {visit(it,depth+1)}}
        }
        try {visit(root,0);return action(nodes)} finally {nodes.forEach {it.recycle()}}
    }
    private fun dumpTree(stage:String) {
        try {
            val lines=tree {nodes->nodes.mapIndexed {index,node->
                val bounds=Rect();node.getBoundsInScreen(bounds)
                "CURVE_SYSTEM_DOCUMENT_NODE stage=$stage index=$index package=${node.packageName} class=${node.className} id=${node.viewIdResourceName} text=${org.json.JSONObject.quote(node.text?.toString().orEmpty())} description=${org.json.JSONObject.quote(node.contentDescription?.toString().orEmpty())} bounds=${bounds.flattenToString()} shown=${node.isVisibleToUser} enabled=${node.isEnabled} clickable=${node.isClickable} editable=${node.isEditable}"
            }}
            lines.chunked(20).forEach {status(it.joinToString("\n"))}
        } catch(error:Throwable) {status("CURVE_SYSTEM_DOCUMENT_TREE_UNAVAILABLE stage=$stage error=$error")}
    }
    private fun pickerVisible():Boolean=runCatching {tree {nodes->nodes.first().packageName?.toString()==pickerPackage}}.getOrDefault(false)
    private fun clickPickerMatch(label:String,nodes:List<AccessibilityNodeInfo>,matches:List<AccessibilityNodeInfo>) {
        check(matches.size==1) {"Expected exactly one system picker node for $label, got ${matches.size}"}
        var target:AccessibilityNodeInfo?=matches.single()
        var depth=0
        while(target!=null && !target.isClickable) {
            check(++depth<=40) {"Unexpected system picker ancestor depth"}
            val parent=target.parent
            if(parent!=null && nodes.none {it===parent})(nodes as MutableList).add(parent)
            target=parent
        }
        // Never retry after dispatch: even a failed ACTION_CLICK is an uncertain UI outcome.
        check(target?.packageName?.toString()==pickerPackage && target.isVisibleToUser && target.isEnabled &&
            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {"System picker click failed: $label"}
    }
    private fun pickerAction(label:String,predicate:(AccessibilityNodeInfo)->Boolean) {
        try {
            tree {nodes->
                check(nodes.first().packageName?.toString()==pickerPackage) {"Not the verified system picker"}
                clickPickerMatch(label,nodes,nodes.filter {it.isVisibleToUser && it.isEnabled && predicate(it)})
            }
        } catch(error:Throwable) {dumpTree("action-failed-$label");throw error}
    }
    private fun awaitPickerAction(label:String,predicate:(AccessibilityNodeInfo)->Boolean) {
        try {
            await("action-$label") {tree {nodes->
                check(nodes.first().packageName?.toString()==pickerPackage) {"Not the verified system picker"}
                val matches=nodes.filter {it.isVisibleToUser && it.isEnabled && predicate(it)}
                if(matches.isEmpty())false else {
                    // Match and dispatch from this same tree. A second snapshot may lose the row
                    // during drawer transitions even after the first snapshot found it.
                    clickPickerMatch(label,nodes,matches)
                    status("CURVE_SYSTEM_DOCUMENT_ACTION label=$label matchingSnapshot=true dispatchedOnce=true")
                    true
                }
            }}
        } catch(error:Throwable) {dumpTree("action-failed-$label");throw error}
    }
    @Suppress("DEPRECATION")
    private fun hasAncestorId(node:AccessibilityNodeInfo,suffixes:Set<String>):Boolean {
        var current=node.parent
        repeat(40) {
            val parent=current ?: return false
            if(parent.viewIdResourceName?.substringAfterLast('/') in suffixes) {parent.recycle();return true}
            current=parent.parent;parent.recycle()
        }
        current?.recycle();return false
    }
    private fun downloads() {
        await("picker-visible") {pickerVisible()}
        dumpTree("picker-before-downloads")
        // Deliberately support only known English AOSP/Google DocumentsUI navigation semantics.
        // Any different tree requires recorded investigation, never coordinate guessing.
        pickerAction("show-roots") {node->
            node.contentDescription?.toString() in setOf("Show roots","Open navigation drawer") ||
                node.viewIdResourceName=="android:id/home"
        }
        // A Downloads breadcrumb is already visible before the drawer opens. Wait for
        // the actual enabled root row, not any matching title in the active window.
        val downloadsRoot:(AccessibilityNodeInfo)->Boolean={node->
            node.text?.toString()=="Downloads" && hasAncestorId(node,setOf("item_root","roots_list"))
        }
        awaitPickerAction("Downloads-root",downloadsRoot)
        await("downloads-root-open") {tree {nodes->
            nodes.first().packageName?.toString()==pickerPackage &&
                nodes.none {it.isVisibleToUser && it.viewIdResourceName=="$pickerPackage:id/roots_list"} &&
                nodes.any {it.isVisibleToUser && it.viewIdResourceName=="$pickerPackage:id/breadcrumb_text" &&
                    it.text?.toString()=="Downloads"}
        }}
        dumpTree("downloads-open")
    }
    private fun nameAndSave(filename:String) {
        try {
            tree {nodes->
                val input=nodes.filter {it.isVisibleToUser && it.isEditable &&
                    (it.viewIdResourceName=="$pickerPackage:id/edittext" || it.text?.toString()==filename)}
                check(input.size==1) {"Unknown DocumentsUI filename editor"}
                check(input.single().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,filename)
                })) {"Could not set unique document name"}
            }
            await("unique-filename") {tree {nodes->nodes.any {it.isEditable && it.text?.toString()==filename}}}
            pickerAction("Save") {it.text?.toString()?.equals("Save",ignoreCase=true)==true}
        } catch(error:Throwable) {dumpTree("name-save-failed");throw error}
    }
    private fun cancelPicker(activity:Activity) {
        check(pickerVisible()) {"Cancellation requires the verified picker in the foreground"}
        // Global back is asynchronous. Repeating it on a timer can also pop the caller
        // after DocumentsUI has finished but before window focus has been delivered.
        check(test.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {"System back rejected"}
        awaitMain("picker-cancel-return") {
            check(!activity.isFinishing && !activity.isDestroyed) {"Picker cancellation removed its caller"}
            activity.hasWindowFocus()
        }
        status("CURVE_SYSTEM_DOCUMENT_CANCEL caller=${activity.javaClass.simpleName} alive=true focused=true singleBack=true")
    }
    private fun traceSnapshot(app:MobileApplication):Map<String,List<Byte>> {
        val output=ByteArrayOutputStream();val done=CountDownLatch(1);var failure:Throwable?=null
        app.logs.export({output}) {error->failure=error;done.countDown()}
        check(done.await(10,TimeUnit.SECONDS)) {"Trace snapshot timed out"};failure?.let {throw it}
        return buildMap {
            ZipInputStream(ByteArrayInputStream(output.toByteArray())).use {zip->
                while(true) {val entry=zip.nextEntry ?: break;put(entry.name,zip.readBytes().toList())}
            }
        }
    }
    private fun verifyFile(uri:Uri,filename:String,expected:ByteArray) {
        check(uri.scheme=="content" && uri.authority in setOf("com.android.providers.downloads.documents","com.android.externalstorage.documents")) {
            "Unexpected document authority; leave unknown file untouched: $uri"
        }
        val provider=requireNotNull(test.context.packageManager.resolveContentProvider(requireNotNull(uri.authority),0))
        check(isSystemPackage(provider.packageName) && DocumentsContract.isDocumentUri(context,uri))
        context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE),null,null,null).use {cursor->
            check(cursor!=null && cursor.moveToFirst() && cursor.count==1) {"Document identity query failed"}
            check(cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))==filename) {"Document name differs; do not delete unknown file"}
            check(!cursor.isNull(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE)) &&
                cursor.getLong(cursor.getColumnIndexOrThrow(OpenableColumns.SIZE))==expected.size.toLong()) {"Document size differs"}
        }
        val bytes=requireNotNull(context.contentResolver.openInputStream(uri)).use {input->
            val result=ByteArrayOutputStream();val buffer=ByteArray(8192)
            while(true) {val count=input.read(buffer);if(count<0)break;check(result.size()+count<=CustomCurveDocument.MAX_BYTES);result.write(buffer,0,count)}
            result.toByteArray()
        }
        check(bytes.contentEquals(expected)) {"Document bytes/identity differ; preserve file for investigation"}
    }
    fun run(disposableCiEmulator:Boolean):Result {
        emulatorGate(disposableCiEmulator)
        val home=start(HomeActivity::class.java,true)
        awaitMain("single-mock-service") {owner(home)?.running==true}
        val app=context.applicationContext as MobileApplication
        app.history
        val inventory=app.beanInventoryResult.getOrThrow();val preparation=app.beanPreparationResult.getOrThrow()
        val journal=app.journalResult.getOrThrow();val custom=app.customCurvesResult.getOrThrow()
        val batches=inventory.batches();val inventoryEvents=inventory.events();val dose=preparation.current()
        val notes=journal.exportJson();val drafts=custom.list();val histories=app.history.entries
        fun bytes(name:String):List<Byte>?=File(context.filesDir,name).let {if(it.exists())it.readBytes().toList()else null}
        val privateDisk=listOf("bean_inventory_v1.bin","bean_preparation_v1.json","brew_journal_v1.json")
            .associateWith(::bytes)
        val prefs=listOf("devices","curves","presets","shot_safety","machine_write_safety","shot_history","brew_feedback","scale_tool","appearance","app_language")
            .associateWith {context.getSharedPreferences(it,Context.MODE_PRIVATE).all.toMap()}
        var service:MobileService?=null;var message:SnapshotMessage?=null
        onMain {service=owner(home);message=service!!.snapshot.message}
        val traces=traceSnapshot(app)
        val source=app.curves.items.firstOrNull {it.controlProfile!=null && runCatching {CustomCurveDocument.fromLibraryItem(it)}.isSuccess}
            ?: error("No captured shareable profile")
        val paths=mutableListOf<String>();var saved=false;var exported:Uri?=null
        var expected:CustomCurveDocument?=null;var filename="";var failure:Throwable?=null
        fun invariants()=onMain {
            check(service!!.running && service!!.snapshot.message===message) {"File UI changed/replaced service event"}
            if(home.hasWindowFocus())check(owner(home)===service) {"Home rebound a different owner"}
            check(inventory.batches()==batches && inventory.events()==inventoryEvents && preparation.current()==dose && journal.exportJson()==notes)
            check(app.history.entries==histories) {"File exchange changed shot history"}
            check(custom.list().filter {it.id!=expected?.id}==drafts) {"Unrelated drafts changed"}
            check(custom.list().size==drafts.size+(if(saved)1 else 0)) {"Wrong number of local draft changes"}
            privateDisk.forEach {(name,value)->check(bytes(name)==value) {"Private data changed: $name"}}
            prefs.forEach {(name,value)->check(context.getSharedPreferences(name,Context.MODE_PRIVATE).all==value) {"Preferences changed: $name"}}
        }
        fun record(path:String) {
            invariants();check(traceSnapshot(app)==traces) {"System document path changed diagnostic/control logs"}
            paths+=path;status("CURVE_SYSTEM_DOCUMENT_PATH $path systemPicker=true externalSend=false noBle=true")
        }
        val automationInfo=test.uiAutomation.serviceInfo
        val originalAutomationFlags=automationInfo.flags
        try {
            automationInfo.flags=originalAutomationFlags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            test.uiAutomation.serviceInfo=automationInfo
            val share=start(CurveShareActivity::class.java) {it.putExtra(CurveShareActivity.CURVE_ID,source.id)}
            onMain {expected=document(share)}
            val doc=requireNotNull(expected);UUID.fromString(doc.id.removePrefix("draft-"))
            check(custom.find(doc.id)==null)
            filename="openhoyi-${doc.id}.json"
            check(doc.id=="draft-${UUID.fromString(doc.id.removePrefix("draft-"))}")
            check(filename=="openhoyi-draft-${UUID.fromString(doc.id.removePrefix("draft-"))}.json")
            click(share,R.string.profiles_export_file);await("create-picker") {pickerVisible()};cancelPicker(share)
            onMain {check(shareUri(share)==null && !button(share,R.string.profiles_share_file).isEnabled)}
            record("system-create-cancel-no-export")
            click(share,R.string.profiles_export_file);downloads();nameAndSave(filename)
            awaitMain("real-create-result-write") {share.hasWindowFocus() && shareUri(share)!=null && button(share,R.string.profiles_share_file).isEnabled}
            onMain {exported=shareUri(share)}
            val uri=requireNotNull(exported);verifyFile(uri,filename,doc.encode().toByteArray(Charsets.UTF_8))
            status("CURVE_SYSTEM_DOCUMENT_IDENTITY uri=$uri displayName=$filename bytes=${doc.encode().toByteArray(Charsets.UTF_8).size} uuid=${doc.id} verified=true")
            record("system-downloads-export-exact-utf8-json")
            val cancelImport=start(CurveImportActivity::class.java)
            val cancelMonitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
            try {
                click(cancelImport,R.string.profiles_open_file);await("open-cancel-picker") {pickerVisible()};cancelPicker(cancelImport)
                onMain {check(cancelImport.hasWindowFocus() && cancelMonitor.hits==0)}
                record("system-open-cancel-no-preview-no-save")
            } finally {test.removeMonitor(cancelMonitor)}
            close(cancelImport)
            fun importPreview():Pair<Activity,Activity> {
                val input=start(CurveImportActivity::class.java)
                val monitor=test.addMonitor(CurveImportPreviewActivity::class.java.name,null,false)
                try {
                    // This passive monitor observes only the actual App preview lifecycle. It never
                    // intercepts ACTION_OPEN_DOCUMENT or supplies an ActivityResult/URI.
                    click(input,R.string.profiles_open_file);downloads()
                    await("uuid-file-listed") {tree {nodes->nodes.any {it.isVisibleToUser && it.text?.toString()==filename}}}
                    pickerAction("unique-exported-file") {it.text?.toString()==filename}
                    val preview=test.waitForMonitorWithTimeout(monitor,15_000)
                    if(preview==null) {dumpTree("actual-preview-missing");error("Real picker did not return the selected UUID document")}
                    opened+=preview
                    awaitMain("actual-preview-ready") {preview.hasWindowFocus() && preview.window.decorView.isLaidOut}
                    onMain {check(CustomCurveDocument.decode(requireNotNull(preview.intent.getStringExtra(CurveImportPreviewActivity.DOCUMENT)))==doc)}
                    invariants();return input to preview
                } finally {test.removeMonitor(monitor)}
            }
            val canceled=importPreview();record("system-open-exact-file-preview-no-auto-save")
            check(test.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK))
            awaitMain("preview-cancel") {canceled.second.isDestroyed && canceled.first.hasWindowFocus()}
            opened.remove(canceled.second);record("system-preview-cancel-no-draft")
            close(canceled.first)
            val confirmed=importPreview();click(confirmed.second,R.string.profiles_import_save)
            awaitMain("confirm-single-local-draft") {confirmed.second.isDestroyed}
            opened.remove(confirmed.second);saved=true
            check(custom.find(doc.id)==doc && custom.list().size==drafts.size+1)
            check(!app.curves.canStart(requireNotNull(app.curves.find(doc.id)))) {"Imported draft became machine-executable"}
            record("system-confirm-once-local-readonly-draft")
            close(confirmed.first)
        } catch(error:Throwable) {failure=error;dumpTree("failure");status("CURVE_SYSTEM_DOCUMENT_FAILURE ${error.stackTraceToString()}")}
        finally {
            fun cleanup(action:()->Unit) {try {action()} catch(error:Throwable) {if(failure==null)failure=error else failure!!.addSuppressed(error);status("CURVE_SYSTEM_DOCUMENT_CLEANUP_FAILURE $error")}}
            cleanup {
                val uri=exported
                if(uri!=null && expected!=null) {
                    // No broad Downloads listing/deletion. Reconfirm full identity immediately before deletion.
                    verifyFile(uri,filename,requireNotNull(expected).encode().toByteArray(Charsets.UTF_8))
                    check(DocumentsContract.deleteDocument(context.contentResolver,uri)) {"Known document deletion not confirmed"}
                    // DocumentsProvider may revoke this URI grant on successful deletion; a later
                    // read denial is not proof of absence. Report its synchronous delete acknowledgement.
                    status("CURVE_SYSTEM_DOCUMENT_OWN_FILE_DELETED uri=$uri verifiedIdentity=true confirmedBySystemProvider=true")
                    if(failure==null)record("system-cleanup-only-identity-verified-created-file")
                } else status("CURVE_SYSTEM_DOCUMENT_UNKNOWN_FILE_RETAINED noVerifiedReturnedUri=true")
            }
            cleanup {automationInfo.flags=originalAutomationFlags;test.uiAutomation.serviceInfo=automationInfo}
            cleanup {opened.toList().asReversed().filter {it!==home}.forEach(::close)}
            cleanup {awaitMain("same-home-owner") {home.hasWindowFocus() && owner(home)===service};invariants();check(traceSnapshot(app)==traces)}
        }
        failure?.let {throw it}
        check(saved && exported!=null)
        return Result(paths.toList(),1,requireNotNull(expected).id,requireNotNull(exported).toString())
    }
}
