package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

/** Controlled provider + platform ActivityResult contract, not a real DocumentsUI/hardware test. */
internal class CurveDocumentContractChecks(private val test: Instrumentation) {
    data class Result(val paths: List<String>, val pickerContracts: Int, val shareContracts: Int, val fileFixtures: Int, val newDrafts: Int)
    private val context get() = test.targetContext
    private val opened = mutableListOf<Activity>()
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            onMain { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "Curve document condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun views(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun root(activity: Activity): View = activity.findViewById(android.R.id.content)
    private fun button(activity: Activity, label: Int): TextView = views(root(activity)).filterIsInstance<TextView>()
        .single { it.isShown && it.isClickable && it.text.toString() == activity.getString(label) }
    private fun click(activity: Activity, label: Int) {
        onMain {
            val action = button(activity, label)
            check(action.isEnabled)
            action.requestRectangleOnScreen(android.graphics.Rect(0, 0, action.width, action.height), true)
            check(action.performClick())
        }
        test.waitForIdleSync()
    }
    private fun start(type: Class<out Activity>, extras: (Intent) -> Unit = {}): Activity {
        val activity = test.startActivitySync(Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).also(extras)).also { opened += it }
        test.waitForIdleSync()
        awaitMain { activity.hasWindowFocus() && root(activity).isLaidOut && !root(activity).isLayoutRequested }
        return activity
    }
    private fun close(activity: Activity) {
        onMain { if (!activity.isDestroyed) activity.finish() }
        awaitMain { activity.isDestroyed }; opened.remove(activity)
    }
    private fun owner(home: Activity): MobileService? = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService

    fun run(): Result {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(test.context.packageName == "io.openhoyi.mobile.mock.test")
        check(context.checkSelfPermission(CurveFixtureProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED) {
            "Mock signature fixture permission not granted; install/signature/order must be verified, not bypassed"
        }
        check(context.packageManager.checkSignatures(context.packageName, test.context.packageName) == PackageManager.SIGNATURE_MATCH)
        check(context.packageManager.getPermissionInfo(CurveFixtureProvider.PERMISSION, 0).protectionLevel and
            android.content.pm.PermissionInfo.PROTECTION_MASK_BASE == android.content.pm.PermissionInfo.PROTECTION_SIGNATURE)
        check(CustomCurveDocument.MAX_BYTES == CurveFixtureProvider.MAX_FIXTURE_BYTES)
        val home = start(HomeActivity::class.java)
        awaitMain { owner(home)?.running == true }
        val app = context.applicationContext as MobileApplication
        app.history
        val inventory = app.beanInventoryResult.getOrThrow()
        val preparation = app.beanPreparationResult.getOrThrow()
        val journal = app.journalResult.getOrThrow()
        val custom = app.customCurvesResult.getOrThrow()
        val balances = inventory.batches()
        val events = inventory.events()
        val dose = preparation.current()
        val notes = journal.exportJson()
        val drafts = custom.list()
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history", "brew_feedback", "scale_tool", "appearance", "app_language")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        fun bytes(name: String): List<Byte>? = File(context.filesDir, name).let { if (it.exists()) it.readBytes().toList() else null }
        val privateDisk = listOf("bean_inventory_v1.bin", "bean_preparation_v1.json", "brew_journal_v1.json").associateWith(::bytes)
        var service: MobileService? = null
        var message: SnapshotMessage? = null
        onMain { service = owner(home); message = service!!.snapshot.message }
        val source = app.curves.items.firstOrNull { it.controlProfile != null && runCatching { CustomCurveDocument.fromLibraryItem(it) }.isSuccess }
            ?: error("No shareable captured parameter fixture")
        val session = UUID.randomUUID().toString()
        var registered = false
        val paths = mutableListOf<String>()
        var pickerContracts = 0
        var shareContracts = 0
        var ownDraftId: String? = null
        var failure: Throwable? = null
        val baseUri = Uri.parse("content://${CurveFixtureProvider.AUTHORITY}")
        fun call(method: String, data: Bundle? = null): Bundle = requireNotNull(context.contentResolver.call(baseUri, method, session, data))
        fun stats(): Bundle = call("stats")
        fun invariants() = onMain {
            check(service!!.running && service!!.snapshot.message === message) { "File exchange replaced machine service/event" }
            if (home.hasWindowFocus()) check(owner(home) === service) { "Home rebound another service" }
            check(inventory.batches() == balances && inventory.events() == events && preparation.current() == dose && journal.exportJson() == notes)
            check(custom.list().filter { it.id != ownDraftId } == drafts) { "File exchange changed unrelated drafts" }
            privateDisk.forEach { (name, value) -> check(bytes(name) == value) { "File exchange changed $name" } }
            preserved.forEach { (name, value) -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == value) { "File exchange changed $name preferences" } }
        }
        fun record(path: String) {
            invariants()
            paths += path
            test.sendStatus(0, Bundle().apply { putString("stream", "CURVE_DOCUMENT_CONTRACT_PATH $path\n") })
        }
        fun picker(activity: Activity, action: String, resultCode: Int, uri: Uri?, title: String? = null) {
            var observed: Intent? = null
            var intercepted = 0
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action != action) return null
                    observed = Intent(intent); intercepted++
                    return Instrumentation.ActivityResult(resultCode, uri?.let {
                        Intent().setData(it).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    })
                }
            }
            test.addMonitor(monitor)
            try {
                click(activity, if (action == Intent.ACTION_CREATE_DOCUMENT) R.string.profiles_export_file else R.string.profiles_open_file)
                onMain {
                    val intent = requireNotNull(observed) { "Document action was not dispatched" }
                    check(intercepted == 1 && intent.action == action && intent.categories == setOf(Intent.CATEGORY_OPENABLE))
                    check(intent.component == null && intent.`package` == null && intent.data == null)
                    if (action == Intent.ACTION_CREATE_DOCUMENT) {
                        check(intent.type == "application/json" && intent.getStringExtra(Intent.EXTRA_TITLE) == title)
                        check(intent.extras?.keySet() == setOf(Intent.EXTRA_TITLE))
                    } else {
                        check(intent.type == "*/*" && intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.toList() == listOf("application/json", "text/plain"))
                        check(intent.extras?.keySet() == setOf(Intent.EXTRA_MIME_TYPES))
                    }
                }
                pickerContracts++
            } finally { test.removeMonitor(monitor) }
        }
        // Intercept the chooser before any external app or person receives this fixture.
        fun shareContract(activity: Activity, label: Int, expectedJson: String, uri: Uri? = null) {
            var observed: Intent? = null
            var intercepted = 0
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    // Fail closed even if the product regresses to direct ACTION_SEND or
                    // another external Activity: inspect only after cancelling the launch.
                    observed = Intent(intent); intercepted++
                    return Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                }
            }
            test.addMonitor(monitor)
            try {
                click(activity, label)
                onMain {
                    val chooser = requireNotNull(observed) { "Share chooser was not dispatched" }
                    check(intercepted == 1 && chooser.action == Intent.ACTION_CHOOSER)
                    @Suppress("DEPRECATION")
                    val intent = requireNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
                    check(intent.action == Intent.ACTION_SEND && intent.component == null && intent.`package` == null && intent.data == null)
                    if (uri == null) {
                        check(intent.type == "text/plain") { "Text sharing must match plain-text receivers, got ${intent.type}" }
                        check(intent.getStringExtra(Intent.EXTRA_TEXT) == expectedJson)
                        check(intent.extras?.keySet() == setOf(Intent.EXTRA_TEXT))
                        check(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0)
                        intent.clipData?.let { clip ->
                            check(clip.itemCount == 1 && clip.getItemAt(0).uri == null && clip.getItemAt(0).text?.toString() == expectedJson)
                        }
                    } else {
                        check(intent.type == "application/json")
                        @Suppress("DEPRECATION")
                        check(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM) == uri)
                        check(intent.extras?.keySet() == setOf(Intent.EXTRA_STREAM))
                        check(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                        check(intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
                        check(intent.clipData?.itemCount == 1 && intent.clipData?.getItemAt(0)?.uri == uri)
                        check(chooser.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0 &&
                            chooser.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)
                        check(chooser.clipData?.itemCount == 1 && chooser.clipData?.getItemAt(0)?.uri == uri)
                        check(CustomCurveDocument.decode(expectedJson).encode() == expectedJson)
                    }
                }
                shareContracts++
            } finally { test.removeMonitor(monitor) }
        }
        try {
            val share = start(CurveShareActivity::class.java) { it.putExtra(CurveShareActivity.CURVE_ID, source.id) }
            var document: CustomCurveDocument? = null
            onMain {
                document = CurveShareActivity::class.java.getDeclaredField("document").apply { isAccessible = true }.get(share) as? CustomCurveDocument
                check(!button(share, R.string.profiles_share_file).isEnabled) { "File sharing enabled before export" }
            }
            val expected = requireNotNull(document)
            check(custom.find(expected.id) == null)
            val expectedJson = expected.encode()
            shareContract(share, R.string.profiles_share_text, expectedJson)
            record("share-text-exact-parameters-plain-text-no-send")
            call("create", Bundle().apply { putString("document", expectedJson) }); registered = true
            val output = CurveFixtureProvider.uri(session, "output.json")
            picker(share, Intent.ACTION_CREATE_DOCUMENT, Activity.RESULT_CANCELED, null, "openhoyi-${expected.id}.json")
            onMain { check(!button(share, R.string.profiles_share_file).isEnabled) }
            check(stats().getInt("write:output.json") == 0 && custom.list() == drafts)
            record("create-cancel-no-write")
            picker(share, Intent.ACTION_CREATE_DOCUMENT, Activity.RESULT_OK, output, "openhoyi-${expected.id}.json")
            awaitMain { button(share, R.string.profiles_share_file).isEnabled }
            val exported = requireNotNull(context.contentResolver.openInputStream(output)).use { it.readBytes() }
            check(exported.contentEquals(expectedJson.toByteArray(Charsets.UTF_8))) { "Export bytes differ from exact UTF-8 parameters" }
            val utf8 = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(exported)).toString()
            check(CustomCurveDocument.decode(utf8) == expected)
            val rootJson = org.json.JSONObject(utf8)
            check(rootJson.keys().asSequence().toSet() == setOf("schema", "kind", "id", "name", "temperatureC", "targetHundredthsGram", "controlMode", "stages"))
            for (index in 0 until rootJson.getJSONArray("stages").length()) {
                check(rootJson.getJSONArray("stages").getJSONObject(index).keys().asSequence().toSet() == setOf("target", "waterTenthsMl"))
            }
            check(stats().getInt("write:output.json") == 1 && custom.list() == drafts)
            record("create-export-exact-utf8-parameters-only-share-enabled")
            shareContract(share, R.string.profiles_share_file, expectedJson, output)
            record("share-file-exact-uri-read-only-grant-no-send"); close(share)
            var input = start(CurveImportActivity::class.java)
            val beforeCancel = bytes("custom_curves_v1.json")
            picker(input, Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_CANCELED, null)
            onMain { check(input.hasWindowFocus()) }
            check(custom.list() == drafts && bytes("custom_curves_v1.json") == beforeCancel && stats().getInt("read:valid.json") == 0)
            record("open-cancel-no-read-or-save"); close(input)
            fun importFile(): Pair<Activity, Activity> {
                val parent = start(CurveImportActivity::class.java)
                val beforePreview = custom.list()
                val beforeDisk = bytes("custom_curves_v1.json")
                val monitor = test.addMonitor(CurveImportPreviewActivity::class.java.name, null, false)
                val preview: Activity
                try {
                    picker(parent, Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_OK, CurveFixtureProvider.uri(session, "valid.json"))
                    preview = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("File import preview missing")
                    opened += preview
                    test.waitForIdleSync(); awaitMain { preview.hasWindowFocus() && root(preview).isLaidOut }
                } finally { test.removeMonitor(monitor) }
                check(custom.list() == beforePreview && bytes("custom_curves_v1.json") == beforeDisk) { "File import saved without confirmation" }
                onMain { check(CustomCurveDocument.decode(requireNotNull(preview.intent.getStringExtra(CurveImportPreviewActivity.DOCUMENT))) == expected) }
                return parent to preview
            }
            var importPages = importFile()
            record("open-valid-real-resolver-preview-no-auto-save")
            click(importPages.second, R.string.profiles_import_save)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            ownDraftId = expected.id
            check(custom.find(expected.id) == expected && custom.list().size == drafts.size + 1)
            check(!app.curves.canStart(requireNotNull(app.curves.find(expected.id))))
            record("file-confirm-local-readonly-save"); close(importPages.first)
            val afterSave = custom.list()
            val afterSaveDisk = bytes("custom_curves_v1.json")
            importPages = importFile()
            click(importPages.second, R.string.profiles_import_save)
            awaitMain { importPages.second.isDestroyed }; opened.remove(importPages.second)
            check(custom.list() == afterSave && bytes("custom_curves_v1.json") == afterSaveDisk)
            record("file-confirm-repeat-idempotent"); close(importPages.first)
            for (name in listOf("malformed.json", "oversize.json")) {
                input = start(CurveImportActivity::class.java)
                val beforeInvalid = custom.list()
                val beforeInvalidDisk = bytes("custom_curves_v1.json")
                val previewMonitor = test.addMonitor(CurveImportPreviewActivity::class.java.name, null, false)
                try {
                    picker(input, Intent.ACTION_OPEN_DOCUMENT, Activity.RESULT_OK, CurveFixtureProvider.uri(session, name))
                    awaitMain { views(root(input)).filterIsInstance<TextView>().any { it.text.toString() == input.getString(R.string.profiles_import_invalid) } }
                    onMain { check(input.hasWindowFocus() && previewMonitor.hits == 0) { "Invalid file navigated to preview" } }
                } finally { test.removeMonitor(previewMonitor) }
                check(custom.list() == beforeInvalid && bytes("custom_curves_v1.json") == beforeInvalidDisk)
                check(stats().getInt("read:$name") == 1)
                record(if (name == "malformed.json") "file-invalid-utf8-rejected" else "file-over-64k-rejected")
                close(input)
            }
            // Known registered path/UUID only; never grant arbitrary file access to make I/O pass.
            for (bad in listOf(CurveFixtureProvider.uri(session, "../private.json"), CurveFixtureProvider.uri(UUID.randomUUID().toString(), "valid.json"))) {
                check(runCatching { context.contentResolver.openInputStream(bad)?.use { it.readBytes() } }.isFailure) { "Provider accepted an unknown fixture URI" }
            }
            record("provider-unknown-path-session-rejected")
            val finalStats = stats()
            check(finalStats.getInt("write:output.json") == 1 && finalStats.getInt("read:output.json") == 1 &&
                finalStats.getInt("read:valid.json") == 2 && finalStats.getInt("read:malformed.json") == 1 && finalStats.getInt("read:oversize.json") == 1)
            check(paths.size == 11 && pickerContracts == 7 && shareContracts == 2 && ownDraftId != null)
            invariants()
        } catch (error: Throwable) { failure = error }
        finally {
            fun cleanup(action: () -> Unit) { try { action() } catch (error: Throwable) { if (failure == null) failure = error else failure!!.addSuppressed(error) } }
            cleanup { opened.toList().asReversed().filter { it !== home }.forEach(::close) }
            cleanup { if (registered) call("cleanup") }
            cleanup { awaitMain { home.hasWindowFocus() && owner(home) === service }; invariants() }
        }
        failure?.let { throw it }
        return Result(paths.toList(), pickerContracts, shareContracts, 4, if (ownDraftId == null) 0 else 1)
    }
}
