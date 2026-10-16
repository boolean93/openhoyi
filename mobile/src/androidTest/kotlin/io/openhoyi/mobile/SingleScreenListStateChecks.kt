package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.MotionEvent
import android.view.InputDevice
import android.graphics.Rect
import io.openhoyi.bluetooth.DiscoveredDevice
import io.openhoyi.session.DeviceRole
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import java.io.ByteArrayInputStream
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** Real non-first-page viewport restoration, only in the isolated Mock package.
 * UI phase never saves a bean/shot, selects an executable curve, or invokes BLE.
 */
internal class SingleScreenListStateChecks(private val test: Instrumentation) {
    companion object {
        private const val COUNT = 40
        const val BATCH_ID = "single-screen-list-batch-01"
        val sampleFixtureIds: List<String> get() = (1..COUNT).map {
            "single-screen-list-shot-${it.toString().padStart(2, '0')}"
        }
        fun seed(app: MobileApplication) {
            check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
            val inventory = app.beanInventoryResult.getOrThrow()
            val originalInventory = inventory.events()
            val journal = app.journalResult.getOrThrow()
            val originalJournal = journal.entries()
            val originalLegacy = app.legacyHistory.list()
            val originalLegacyCurves = app.legacyCurves.load()
            // Initialize existing recent-history migration before freezing setup baselines.
            app.history.entries
            // Thirty seconds and 36.00g match each named journal observation.
            val fixtureSamples = (0..30).map { second ->
                ShotPoint(second * 1000L, if (second < 4) second * 20 else 90 - (second - 4),
                    25, second * 30, 9300, second * 120, 120, true)
            }
            for (index in 1..COUNT) {
                val suffix = index.toString().padStart(2, '0')
                val batch = BeanBatch("single-screen-list-batch-$suffix",
                    Bean("single-screen-list-bean-$suffix", "列表恢复测试豆 $suffix"), 250000)
                val existing = inventory.batches().firstOrNull { it.batch.id == batch.id }
                if (existing == null) inventory.addBatch("single-screen-list-inbound-$suffix", batch)
                else check(existing.batch == batch) { "Named inventory fixture conflicts; not overwritten" }
                val id = "single-screen-list-shot-$suffix"
                val observation = BrewJournal.Observation(id, "capture-1", 1700100000000L + index * 60000L,
                    1700100030000L + index * 60000L, 30000, "ENDED", null, 3600)
                val old = journal.find(id)
                if (old == null) journal.observe(observation)
                else check(old.observation == observation) { "Named journal fixture conflicts; not overwritten" }
                val existingSamples = app.samples.load(id)
                if (existingSamples.isEmpty()) app.samples.save(id, fixtureSamples)
                else check(existingSamples == fixtureSamples) { "Named journal sample fixture conflicts: $id; not overwritten" }
            }
            val legacy = (1..COUNT).map { index -> LegacyShot("single-screen-list-legacy-$index",
                1700100000000L + index * 60000L, 30, 6, "列表恢复旧记录 $index", null,
                listOf(LegacyPoint(0.0, 0.0, 0.0, 0.0, 0.0), LegacyPoint(1.0, 2.0, 1.0, 1.0, 1.0))) }
            app.legacyHistory.import(ByteArrayInputStream(LegacyHistoryCodec.encode(legacy).toByteArray()))
            // This store replaces on import: never import over any existing legacy library.
            if (app.legacyCurves.load() == null) {
                val items = JSONArray()
                for (index in 1..COUNT) items.put(JSONObject().put("name", "单屏旧曲线测试 $index")
                    .put("category", "test").put("factory", false).put("temp", 93).put("flow", 70)
                    .put("weight", 360).put("seg", 2).put("press1", 9).put("flow1", 30)
                    .put("press2", 6).put("flow2", 40).put("singleScreenFixture", true))
                val document = JSONObject().put("format", "openhoyi-legacy-curves-v1").put("factoryVersion", 3)
                    .put("categoryConfig", JSONObject().put("labels", JSONObject().put("test", "测试分类")))
                    .put("items", items).toString()
                app.legacyCurves.import(ByteArrayInputStream(document.toByteArray()))
            }
            if (originalLegacyCurves != null) check(app.legacyCurves.load() == originalLegacyCurves) { "Existing legacy library replaced" }
            check(inventory.events().containsAll(originalInventory)) { "Original inventory changed during setup" }
            check(originalJournal.all { journal.find(it.observation.id) == it }) { "Original journal changed during setup" }
            check(app.legacyHistory.list().containsAll(originalLegacy)) { "Original legacy history changed during setup" }
        }
    }

    private val context get() = test.targetContext
    private val opened = mutableListOf<Activity>()
    private val diagnostics = mutableListOf<String>()
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (true) {
            var ready = false
            main { ready = condition() }
            if (ready) return
            check(SystemClock.elapsedRealtime() < deadline) { "List state condition timed out" }
            Thread.sleep(25)
        }
    }
    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
    private fun children(activity: Activity) = views(activity.findViewById(android.R.id.content))
    private fun field(activity: Activity, name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun ready(activity: Activity) {
        test.waitForIdleSync()
        await { activity.hasWindowFocus() && activity.findViewById<ViewGroup>(android.R.id.content)
            .let { it.height > 0 && !it.isLayoutRequested } }
    }
    private fun open(type: Class<out Activity>): Activity = test.startActivitySync(
        Intent(context, type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        .also { opened += it; ready(it) }
    private fun recreate(activity: Activity): Activity {
        val monitor = test.addMonitor(activity.javaClass.name, null, false)
        try {
            main { activity.recreate() }
            val replacement = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("No list replacement")
            opened.remove(activity); opened += replacement
            ready(replacement)
            return replacement
        } finally { test.removeMonitor(monitor) }
    }
    private fun list(activity: Activity): ListView = children(activity).filterIsInstance<ListView>().single()
    private fun listReady(activity: Activity) {
        await { list(activity).let { it.isShown && it.count > 1 && it.childCount > 0 && !it.isLayoutRequested } }
    }
    private data class Anchor(val position: Int, val offset: Int, val item: String)
    private fun anchor(list: ListView) = Anchor(list.firstVisiblePosition, list.getChildAt(0).top,
        list.getItemAtPosition(list.firstVisiblePosition).toString())
    /** Inject a short real touch gesture strictly inside this local viewport. */
    private fun swipe(view: View) {
        val bounds = Rect()
        main { check(view.isShown && view.getGlobalVisibleRect(bounds)); check(bounds.width() > 32 && bounds.height() > 64) }
        val x = bounds.exactCenterX()
        val startY = bounds.top + bounds.height() * .82f
        val endY = bounds.top + bounds.height() * .22f
        val down = SystemClock.uptimeMillis()
        fun event(action: Int, y: Float) {
            val event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try { check(test.uiAutomation.injectInputEvent(event, true)) { "Touch gesture rejected" } }
            finally { event.recycle() }
        }
        event(MotionEvent.ACTION_DOWN, startY)
        for (step in 1..16) {
            Thread.sleep(25)
            event(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 16)
        }
        // AbsListView computes velocity inside onTouchUp BEFORE adding the UP
        // event to VelocityTracker. Sleeping alone leaves the last moving samples
        // active. Supply real stationary MOVE samples before releasing the finger.
        repeat(4) {
            Thread.sleep(50)
            event(MotionEvent.ACTION_MOVE, endY)
        }
        event(MotionEvent.ACTION_UP, endY)
        test.waitForIdleSync()
    }
    /** Capture the baseline only after the real gesture has stopped moving. */
    private fun <T : Any> settled(read: () -> T?): T {
        var candidate: T? = null
        var stableSince = 0L
        await {
            val current = read()
            val now = SystemClock.elapsedRealtime()
            if (current == null || current != candidate) {
                candidate = current
                stableSince = now
                false
            } else now - stableSince >= 250
        }
        return requireNotNull(candidate)
    }
    private fun swipeScroll(activity: Activity, scroll: ScrollView): Int {
        main { check(scroll.canScrollVertically(1)) { "${activity.javaClass.simpleName} insufficient real scroll range" } }
        swipe(scroll)
        return settled { scroll.scrollY.takeIf { it > 0 && !scroll.isLayoutRequested } }
    }
    private fun scrollList(activity: Activity): Anchor {
        listReady(activity)
        main {
            val list = list(activity)
            check(list.canScrollVertically(1)) { "${activity.javaClass.simpleName} has no actual scroll range; fixtures insufficient" }
        }
        swipe(list(activity))
        var first = 0
        main { first = list(activity).firstVisiblePosition }
        if (first == 0) swipe(list(activity))
        return settled {
            val view = list(activity)
            if (view.firstVisiblePosition > 0 && view.childCount > 0 && !view.isLayoutRequested)
                anchor(view) else null
        }
    }
    private fun viewport(activity: Activity): String {
        var result = "unavailable"
        main {
            val view = list(activity)
            val actual = if (view.childCount > 0) anchor(view).toString() else "<no child>"
            result = "actual=$actual count=${view.count} height=${view.height} childCount=${view.childCount} shown=${view.isShown} layoutRequested=${view.isLayoutRequested}"
        }
        return result
    }
    private fun restored(activity: Activity, saved: Anchor, stage: String = "restore") {
        try {
            listReady(activity)
            await { anchor(list(activity)) == saved }
            main { SingleScreenPageChecks.verify(activity); check(anchor(list(activity)) == saved) }
        } catch (failure: Throwable) {
            var actual = "diagnostic unavailable"
            try { actual = viewport(activity) } catch (secondary: Throwable) {
                if (secondary !== failure) failure.addSuppressed(secondary)
            }
            throw IllegalStateException("${activity.javaClass.simpleName} stage=$stage saved=$saved $actual", failure)
        }
    }
    private fun clickVisible(activity: Activity) {
        main {
            val list = list(activity)
            val position = list.firstVisiblePosition
            check(list.performItemClick(list.getChildAt(0), position, list.adapter.getItemId(position)))
        }
        test.waitForIdleSync()
    }
    private fun detailReturn(activity: Activity, detailType: Class<out Activity>, action: () -> Unit) {
        val monitor = test.addMonitor(detailType.name, null, false)
        try {
            action()
            val detail = test.waitForMonitorWithTimeout(monitor, 10000) ?: error("No detail page")
            opened += detail
            ready(detail)
            main { SingleScreenPageChecks.verify(detail); detail.finish() }
            await { detail.isDestroyed && activity.hasWindowFocus() }
            opened.remove(detail)
            ready(activity)
        } finally { test.removeMonitor(monitor) }
    }
    private fun curve(activity: Activity, evidence: MutableList<String>): Activity {
        val app = context.applicationContext as MobileApplication
        val category = CurveCategoryFilter.entries.filter { it != CurveCategoryFilter.ALL }
            .maxBy { filter -> CurveSearch.filter(app.curves.items, filter, "factory-v3").size }
        main {
            val search = field(activity, "search") as EditText
            search.setText("factory-v3")
            check(children(activity).filterIsInstance<TextView>().single {
                it.isClickable && it.text.toString() == category.label(activity) }.performClick())
            children(activity).filterIsInstance<Spinner>().single().setSelection(CurveSortMode.MOST_USED.ordinal)
        }
        test.waitForIdleSync()
        val saved = scrollList(activity)
        diagnostics += "CurveActivity stage=before-click saved=$saved ${viewport(activity)}"
        clickVisible(activity)
        diagnostics += "CurveActivity stage=after-click saved=$saved ${viewport(activity)}"
        val selected = field(activity, "selected") as CurveLibraryItem
        fun browser(page: Activity) {
            if (!list(page).isShown) {
                check(test.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK))
                await { list(page).isShown && page.hasWindowFocus() }
            }
        }
        browser(activity)
        diagnostics += "CurveActivity stage=after-detail-return saved=$saved ${viewport(activity)}"
        restored(activity, saved, "click-show-detail-return-before-recreate")
        evidence += "CurveActivity detail-return position=${saved.position} offset=${saved.offset} gesture=swipe selected=${selected.id}"
        val replacement = recreate(activity)
        restored(replacement, saved, "recreate")
        main {
            check((field(replacement, "search") as EditText).text.toString() == "factory-v3")
            check(field(replacement, "category") == category)
            check(field(replacement, "sortMode") == CurveSortMode.MOST_USED)
            check((field(replacement, "selected") as CurveLibraryItem).id == selected.id)
        }
        evidence += "CurveActivity recreate query=factory-v3 filter=${category.name} sort=MOST_USED selected=${selected.id} position=${saved.position} offset=${saved.offset} gesture=swipe"
        return replacement
    }
    private fun scale(initial: Activity, evidence: MutableList<String>) {
        var activity = initial
        val ownerField = ScaleOwnerActivity::class.java.getDeclaredField("owner").apply { isAccessible = true }
        await { ownerField.get(activity) != null }
        val service = ownerField.get(activity) as MobileService
        val snapshotField = MobileService::class.java.getDeclaredField("snapshot").apply { isAccessible = true }
        var original: MobileSnapshot? = null
        val state = service.shotState
        main {
            check(service.running) { "Synthetic fixture requires the existing running Mock owner" }
            check(MobileService::class.java.getDeclaredField("hub").apply { isAccessible = true }.get(service) == null)
            check(MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }.get(service) != null)
            original = service.snapshot
        }
        val before = requireNotNull(original)
        val fixtures = (1..40).map { index -> DiscoveredDevice(
            "02:00:00:00:10:${index.toString(16).padStart(2, '0')}", "UI-only synthetic scale $index", -50,
            DeviceRole.BOOKOO) }
        fun render(page: Activity) {
            page.javaClass.getDeclaredMethod("renderScale").apply { isAccessible = true }.invoke(page)
        }
        fun localScroll(page: Activity): ScrollView = (field(page, "candidateScroll") as ScrollView).also {
            check(it.getChildAt(0) === field(page, "rows")) { "Scale candidate viewport does not own the tested rows" }
        }
        fun disabledCandidates(page: Activity) {
            val buttons = views(field(page, "rows") as View).filterIsInstance<Button>()
            check(buttons.size == 40 && buttons.all { !it.isEnabled }) { "Synthetic Mock candidates must not connect" }
        }
        var primary: Throwable? = null
        var restoreFrame: MobileSnapshot? = null
        fun cleanup(action: () -> Unit) {
            try { action() } catch (secondary: Throwable) {
                val first = primary
                if (first == null) primary = secondary else if (first !== secondary) first.addSuppressed(secondary)
            }
        }
        try {
            main { snapshotField.set(service, service.snapshot.copy(candidates = fixtures)); render(activity) }
            await { (field(activity, "rows") as ViewGroup).childCount == fixtures.size }
            main { disabledCandidates(activity) }
            val saved = swipeScroll(activity, localScroll(activity))
            activity = recreate(activity)
            await { ownerField.get(activity) === service && localScroll(activity).scrollY == saved }
            main {
                SingleScreenPageChecks.verify(activity)
                disabledCandidates(activity)
                check(service.snapshot.candidates == fixtures && service.snapshot.message === before.message && service.shotState == state)
            }
            evidence += "ScaleConnectionActivity recreate scrollY=$saved gesture=swipe syntheticCandidates=true noScan=true noConnect=true sameOwner=true"
            detailReturn(activity, HomeActivity::class.java) {
                main { check(children(activity).filterIsInstance<Button>().single {
                    it.text.toString() == activity.getString(R.string.scale_connection_manager) }.performClick()) }
            }
            await { ownerField.get(activity) === service && localScroll(activity).scrollY == saved }
            main {
                SingleScreenPageChecks.verify(activity)
                disabledCandidates(activity)
                check(service.snapshot.candidates == fixtures && service.snapshot.message === before.message && service.shotState == state)
            }
            evidence += "ScaleConnectionActivity manager-navigation-return scrollY=$saved gesture=swipe syntheticCandidates=true noScan=true noConnect=true sameOwner=true"
        } catch (failure: Throwable) {
            primary = failure
        } finally {
            cleanup { main {
                // Keep current live timestamps/readings: restore ONLY the candidate field.
                val current = service.snapshot
                restoreFrame = current
                snapshotField.set(service, current.copy(candidates = before.candidates))
            } }
            cleanup { main { check(service.snapshot.candidates == before.candidates) } }
            cleanup { main {
                val frame = requireNotNull(restoreFrame) { "Candidate restoration never captured the current frame" }
                check(service.snapshot.coffeeAt?.let { it >= requireNotNull(frame.coffeeAt) } == true)
                check(service.snapshot.weightAt?.let { it >= requireNotNull(frame.weightAt) } == true)
            } }
            cleanup { main { check(service.snapshot.message === before.message) } }
            cleanup { main { check(service.shotState == state) } }
        }
        primary?.let { throw it }
    }
    fun run(directory: File): Int {
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        diagnostics.clear()
        val prefs = context.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val originalPrefs = prefs.all.toMap()
        fun preferenceNames() = File(context.applicationInfo.dataDir, "shared_prefs").listFiles().orEmpty()
            .filter { it.name.endsWith(".xml") }.map { it.name.removeSuffix(".xml") }.toSet() + "curves"
        val allPrefsBefore = preferenceNames().associateWith {
            context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap()
        }
        val evidence = mutableListOf<String>()
        val failures = mutableListOf<String>()
        fun attempt(label: String, action: () -> Unit) {
            try { action() } catch (failure: Throwable) { failures += "$label: ${failure.stackTraceToString()}" }
        }
        fun finishOwned() {
            // One broken finish must not prevent another owned window from closing.
            for (activity in opened.toList().asReversed()) {
                attempt("cleanup finish ${activity.javaClass.simpleName}") {
                    main { if (!activity.isDestroyed) activity.finish() }
                }
            }
            opened.clear()
            attempt("cleanup wait-idle") { test.waitForIdleSync() }
        }
        val pages = listOf(CurveActivity::class.java, HistoryActivity::class.java, BeanInventoryActivity::class.java,
            BeanLedgerActivity::class.java, LegacyCurveActivity::class.java, LegacyHistoryActivity::class.java, ScaleConnectionActivity::class.java)
        try {
            for (type in pages) {
                try {
                    var activity = open(type)
                    if (activity is CurveActivity) { curve(activity, evidence); continue }
                    if (activity is ScaleConnectionActivity) { scale(activity, evidence); continue }
                    if (activity is HistoryActivity) {
                        main { check(children(activity).filterIsInstance<TextView>().single {
                            it.text.toString() == activity.getString(R.string.journal_tab) }.performClick()) }
                    }
                    if (activity is BeanInventoryActivity || activity is BeanLedgerActivity) {
                        val saved = swipeScroll(activity, field(activity, "listScroll") as ScrollView)
                        activity = recreate(activity)
                        await { (field(activity, "listScroll") as ScrollView).scrollY == saved }
                        main { SingleScreenPageChecks.verify(activity) }
                        evidence += "${type.simpleName} recreate scrollY=$saved gesture=swipe"
                        if (activity is BeanInventoryActivity) {
                            var selectedId: String? = null
                            var selectedMetadata = emptyList<String>()
                            main {
                                val scroll = field(activity, "listScroll") as ScrollView
                                check(views(scroll).filterIsInstance<Button>().first {
                                    it.isShown && it.getGlobalVisibleRect(Rect())
                                }.performClick())
                                selectedId = field(activity, "selectedBatchId") as String
                                selectedMetadata = views(activity.findViewById<ViewGroup>(android.R.id.content)
                                    .findViewWithTag<View>("inventory-batch-metadata")).filterIsInstance<TextView>().map { it.text.toString() }
                                check(selectedMetadata.size >= 7)
                            }
                            activity = recreate(activity)
                            ready(activity)
                            main {
                                check(field(activity, "selectedBatchId") == selectedId)
                                check((field(activity, "collection") as TaskCollectionLayout).detailRequested)
                                check(views(activity.findViewById<ViewGroup>(android.R.id.content)
                                    .findViewWithTag<View>("inventory-batch-metadata")).filterIsInstance<TextView>().map { it.text.toString() } == selectedMetadata)
                            }
                            val shot = requireNotNull(test.uiAutomation.takeScreenshot())
                            try { File(directory, "inventory-selected-restored.png").outputStream().use {
                                check(shot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                            } } finally { shot.recycle() }
                            main {
                                views(activity.window.decorView).filterIsInstance<Button>().singleOrNull {
                                    it.isShown && it.text.toString() == activity.getString(R.string.beans_back_to_list)
                                }?.let { check(it.performClick()) }
                            }
                            await { (field(activity, "listScroll") as ScrollView).let { it.isShown && it.scrollY == saved } }
                        }
                        detailReturn(activity, BeanBatchActivity::class.java) {
                            if (activity is BeanInventoryActivity) main {
                                val scroll = field(activity, "listScroll") as ScrollView
                                val button = views(scroll).filterIsInstance<Button>().first { it.isShown && it.getGlobalVisibleRect(android.graphics.Rect()) }
                                check(button.performClick())
                                check(field(activity, "selectedBatchId") != null)
                                val batchDetails = views(activity.window.decorView).filterIsInstance<Button>().single {
                                    it.isShown && it.text.toString() == activity.getString(R.string.beans_batch_title)
                                }
                                check(batchDetails.performClick())
                            } else main { activity.startActivity(Intent(activity, BeanBatchActivity::class.java)
                                .putExtra(BeanBatchActivity.BATCH_ID, BATCH_ID)) }
                        }
                        if (activity is BeanInventoryActivity) main {
                            views(activity.window.decorView).filterIsInstance<Button>().singleOrNull {
                                it.isShown && it.text.toString() == activity.getString(R.string.beans_back_to_list)
                            }?.let { check(it.performClick()) }
                        }
                        await { (field(activity, "listScroll") as ScrollView).let { it.isShown && it.scrollY == saved } }
                        main { SingleScreenPageChecks.verify(activity) }
                        evidence += "${type.simpleName} navigation-detail-return scrollY=$saved gesture=swipe"
                    } else {
                        val saved = scrollList(activity)
                        activity = recreate(activity)
                        if (activity is HistoryActivity) main { check(field(activity, "showJournal") == true) }
                        restored(activity, saved, "recreate")
                        evidence += "${type.simpleName} recreate position=${saved.position} offset=${saved.offset} gesture=swipe journal=${activity is HistoryActivity}"
                        if (activity is LegacyCurveActivity) {
                            clickVisible(activity)
                            await { (field(activity, "collection") as TaskCollectionLayout).detailRequested }
                            var selectedText = ""
                            main {
                                val detail = field(activity, "detail") as android.widget.TextView
                                check(detail.isShown && detail.text.isNotBlank())
                                selectedText = detail.text.toString()
                            }
                            activity = recreate(activity)
                            await {
                                (field(activity, "collection") as TaskCollectionLayout).detailRequested &&
                                    (field(activity, "detail") as android.widget.TextView).text.toString() == selectedText
                            }
                            ready(activity)
                            val detailShot = requireNotNull(test.uiAutomation.takeScreenshot())
                            try {
                                File(directory, "legacy-curve-selected-restored.png").outputStream().use {
                                    check(detailShot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
                                }
                            } finally { detailShot.recycle() }
                            main {
                                // Wide mode keeps its list visible; compact mode has an explicit native return.
                                val back = views(activity.window.decorView).filterIsInstance<Button>().singleOrNull {
                                    it.isShown && it.text.toString() == activity.getString(R.string.curve_back)
                                }
                                if (back != null) check(back.performClick())
                            }
                            ready(activity)
                        } else if (activity is HistoryActivity) {
                            clickVisible(activity)
                            var selectedTag: Any? = null
                            main {
                                check((field(activity, "collection") as TaskCollectionLayout).detailRequested)
                                selectedTag = (field(activity, "detailContent") as View).tag
                                check(selectedTag.toString().startsWith("history-selection-"))
                            }
                            activity = recreate(activity)
                            main { check((field(activity, "detailContent") as View).tag == selectedTag) }
                            detailReturn(activity, HistoryDetailActivity::class.java) {
                                main {
                                    val expand = views(activity.window.decorView).filterIsInstance<Button>().single {
                                        it.isShown && it.text.toString() == activity.getString(R.string.ui_full_details)
                                    }
                                    check(expand.performClick())
                                }
                            }
                            main {
                                views(activity.window.decorView).filterIsInstance<Button>().singleOrNull {
                                    it.isShown && it.text.toString() == activity.getString(R.string.history_back_to_list)
                                }?.let { check(it.performClick()) }
                            }
                            ready(activity)
                        } else detailReturn(activity, LegacyHistoryDetailActivity::class.java) { clickVisible(activity) }
                        restored(activity, saved, "detail-return")
                        evidence += "${type.simpleName} detail-return position=${saved.position} offset=${saved.offset} gesture=swipe"
                    }
                } catch (failure: Throwable) {
                    failures += "${type.simpleName}: ${failure.stackTraceToString()}"
                } finally {
                    finishOwned()
                }
            }
        } catch (failure: Throwable) {
            failures += "suite: ${failure.stackTraceToString()}"
        } finally {
            finishOwned()
            attempt("cleanup restore curves prefs") { main { restoreMockPreferences(prefs, originalPrefs) } }
            attempt("cleanup check curves prefs") { main { check(prefs.all == originalPrefs) } }
            // Check every original/current namespace independently, even if one fails.
            val namespaces = (allPrefsBefore.keys + "curves").toMutableSet()
            attempt("cleanup enumerate prefs") { namespaces += preferenceNames() }
            for (name in namespaces) attempt("cleanup check prefs $name") { main {
                check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all ==
                    (allPrefsBefore[name] ?: emptyMap<String, Any>())) { "List navigation changed preferences: $name" }
            } }
        }
        val resultFile = File(directory, "list-state-result.txt")
        fun writeResult() = resultFile.writeText("pages=7 restoreFixtures=${evidence.size} language=zh-Hans appearance=light noBle=true gesture=swipe syntheticCandidates=true noScan=true noConnect=true\n" +
            (evidence + diagnostics + failures).joinToString("\n"))
        // Evidence output is also independent: a file failure must not suppress streams,
        // and a stream failure must not make the suite return a success count.
        attempt("output result") { writeResult() }
        for (line in diagnostics) attempt("output diagnostic stream") {
            test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_LIST_STATE_DIAGNOSTIC $line\n") })
        }
        for (line in evidence) attempt("output evidence stream") {
            test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_LIST_STATE_OK $line\n") })
        }
        for (line in failures.toList()) attempt("output failure stream") {
            test.sendStatus(0, Bundle().apply { putString("stream", "SINGLE_SCREEN_LIST_STATE_FAILED $line\n") })
        }
        attempt("output final result") { writeResult() }
        if (failures.isEmpty() && evidence.size == 14) attempt("output passed stream") {
            test.sendStatus(0, Bundle().apply {
                putString("stream", "SINGLE_SCREEN_LIST_STATE_PASSED pages=7 restoreFixtures=14 language=zh-Hans appearance=light noBle=true gesture=swipe syntheticCandidates=true noScan=true noConnect=true\n")
            })
        }
        // Persist a late output failure too; never turn it into a false pass.
        if (failures.isNotEmpty()) {
            attempt("output failure result") { writeResult() }
            attempt("output suite failed stream") {
                test.sendStatus(0, Bundle().apply { putString("stream",
                    "SINGLE_SCREEN_LIST_STATE_FAILED pages=7 restoreFixtures=${evidence.size} failures=${failures.size}\n") })
            }
        }
        return if (failures.isEmpty()) evidence.size else -evidence.size - 1
    }
}
