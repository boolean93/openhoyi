package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.WindowInsets
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.os.SystemClock
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/** Measures actual library views; preview only, never selects a recipe for brewing. */
internal class CurveLibraryViewportChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun imeSearchRegression(activity: MainActivity, fragment: CurveFragment, app: MobileApplication) {
        if (activity.resources.configuration.screenWidthDp < 840) return
        fun field(name: String) = CurveFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)
        val show = CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java).apply { isAccessible = true }
        val search = field("search") as android.widget.EditText
        val collection = field("collection") as TaskCollectionLayout
        val list = field("curveList") as android.widget.ListView
        val sort = activity.window.decorView.findViewWithTag<View>("product/curve-library/sort")
        val filter = activity.window.decorView.findViewWithTag<View>("product/curve-library/filter")
        val count = activity.window.decorView.findViewWithTag<View>("product/curve-library/count")
        val original = field("selected") as CurveLibraryItem?
        val originalQuery = search.text.toString()
        val selected = app.curves.items.first { it.factoryCurve != null && it.name.length >= 5 }
        val input = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        fun ime(visible: Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 5000
            while (SystemClock.elapsedRealtime() < deadline) {
                var state = false
                main { state = activity.window.decorView.rootWindowInsets?.isVisible(WindowInsets.Type.ime()) == true }
                if (state == visible) return
                test.waitForIdleSync(); SystemClock.sleep(20)
            }
            error("Curve library IME visibility did not become $visible")
        }
        try {
            main {
                show.invoke(fragment, selected)
                search.setText(selected.name.take(2))
                search.setSelection(search.length())
                check(search.requestFocus())
                input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT)
            }
            ime(true)
            test.waitForIdleSync()
            main {
                val fullResult = (0 until list.childCount).mapNotNull(list::getChildAt)
                    .any { it.top >= 0 && it.bottom <= list.height && it.isShown }
                check(sort.visibility == View.GONE && filter.visibility == View.GONE && count.visibility == View.GONE &&
                    list.height >= HoyiUi.dp(activity, 64) && fullResult) {
                    "IME search did not leave a complete curve result visible" }
            }
            for (query in listOf(selected.name.take(3), selected.name.take(5))) {
                main {
                    search.setText(query); search.setSelection(query.length - 1)
                    check(search.hasFocus() && search.selectionStart == query.length - 1 &&
                        collection.browserBody.isShown && collection.detailBody.isShown &&
                        (field("selected") as CurveLibraryItem).id == selected.id &&
                        list.count == CurveSearch.filter(app.curves.items, CurveCategoryFilter.ALL, query).size) {
                        "IME search changed curve two-pane state, focus, caret, selection, or live result"
                    }
                }
            }
            main { input.hideSoftInputFromWindow(search.windowToken, 0) }
            ime(false)
            test.waitForIdleSync()
            main { check(sort.visibility == View.VISIBLE && filter.visibility == View.VISIBLE && count.visibility == View.VISIBLE) }
            main { input.showSoftInput(search, InputMethodManager.SHOW_IMPLICIT) }
            ime(true)
            main {
                check(search.text.toString() == selected.name.take(5) && search.selectionStart == selected.name.take(5).length - 1 &&
                    search.hasFocus() && collection.browserBody.isShown && collection.detailBody.isShown &&
                    (field("selected") as CurveLibraryItem).id == selected.id)
            }
        } finally {
            main {
                input.hideSoftInputFromWindow(search.windowToken, 0)
                search.setText(originalQuery); search.clearFocus()
                original?.let { show.invoke(fragment, it) }
            }
            test.waitForIdleSync()
        }
    }
    private fun returnIntentRegression(activity: MainActivity, fragment: CurveFragment, app: MobileApplication, directory: File) {
        fun field(name: String) = CurveFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)
        val show = CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java).apply { isAccessible = true }
        val refreshPage = CurveFragment::class.java.getDeclaredMethod("onResume").apply { isAccessible = true }
        lateinit var collection: TaskCollectionLayout
        lateinit var list: android.widget.ListView
        lateinit var detail: ScrollView
        lateinit var fallback: ScrollView
        lateinit var original: CurveLibraryItem
        var originalRequested = false
        var originalDetailY = 0
        var originalFallbackY = 0
        main {
            collection = field("collection") as TaskCollectionLayout
            list = field("curveList") as android.widget.ListView
            detail = field("detailScroll") as ScrollView
            fallback = field("previewFallbackScroll") as ScrollView
            original = field("selected") as CurveLibraryItem
            originalRequested = collection.detailRequested
            originalDetailY = detail.scrollY
            originalFallbackY = fallback.scrollY
        }
        val pressure = app.curves.items.filter { CurveStageParameters.pressureTargets(it).isNotEmpty() }
            .maxByOrNull { CurveDetailsText(activity).render(it, app.curves.canStart(it)).length }
            ?: error("No pressure curve for return-intent regression")
        val mixed = app.curves.items.first { it.factoryCurve != null && CurveStageParameters.preview(it).mixed }
        fun viewport() = list.firstVisiblePosition to (list.getChildAt(0)?.top ?: 0)
        fun selectedId() = (field("selected") as CurveLibraryItem?)?.id
        fun refresh() { main { refreshPage.invoke(fragment) }; test.waitForIdleSync() }
        var detailRange = 0
        var fallbackRange = 0
        var detailBefore = 0
        var fallbackBefore = 0
        var failure: Throwable? = null
        try {
            main {
                show.invoke(fragment, pressure)
                check(collection.browserBody.visibility == View.VISIBLE && collection.detailBody.visibility == View.VISIBLE)
                collection.detailRequested = false
            }
            test.waitForIdleSync()
            var before = 0 to 0
            main { before = viewport() }
            refresh()
            main {
                check(!collection.detailRequested && selectedId() == pressure.id && viewport() == before) {
                    "Curve return turned visible two-pane detail into requested detail" }
            }
            main { show.invoke(fragment, pressure) }
            test.waitForIdleSync()
            main {
                check(collection.detailRequested)
                detailRange = (detail.getChildAt(0).height - detail.height).coerceAtLeast(0)
                if (detailRange > 0) { detail.scrollTo(0, detailRange); check(detail.scrollY > 0) }
                detailBefore = detail.scrollY
                before = viewport()
            }
            refresh()
            main {
                check(collection.detailRequested && selectedId() == pressure.id && viewport() == before)
                if (detailRange > 0) check(detail.scrollY == detailBefore) {
                    "Curve return reset nonzero detail preview scroll" }
                if (detailRange > 0) {
                    show.invoke(fragment, pressure)
                    check(collection.detailRequested && detail.scrollY == 0) {
                        "Explicit Curve selection no longer resets detail preview scroll" }
                }
            }
            main { show.invoke(fragment, mixed) }
            test.waitForIdleSync()
            main {
                fallbackRange = (detail.getChildAt(0).height - detail.height).coerceAtLeast(0)
                if (fallbackRange > 0) { detail.scrollTo(0, fallbackRange); check(detail.scrollY > 0) }
                fallbackBefore = detail.scrollY
                before = viewport()
            }
            refresh()
            main {
                check(collection.detailRequested && selectedId() == mixed.id && viewport() == before)
                if (fallbackRange > 0) check(detail.scrollY == fallbackBefore) {
                    "Curve return reset nonzero mixed-stage card scroll" }
            }
            File(directory, "return-intent-regression.txt").writeText(
                "twoPaneFalseAndTruePreserved=true explicitShowRequested=true detailNonzeroCovered=${detailRange > 0} " +
                    "mixedDetailNonzeroCovered=${fallbackRange > 0} detailRangePx=$detailRange mixedDetailRangePx=$fallbackRange\n")
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            var cleanupFailure: Throwable? = null
            fun recordCleanup(error: Throwable) {
                if (cleanupFailure == null) cleanupFailure = error else cleanupFailure!!.addSuppressed(error)
            }
            try {
                main {
                    fun restore(action: () -> Unit) {
                        try { action() } catch (error: Throwable) { recordCleanup(error) }
                    }
                    restore { show.invoke(fragment, original) }
                    restore { collection.detailRequested = originalRequested }
                    restore { detail.scrollTo(0, originalDetailY) }
                    restore { fallback.scrollTo(0, originalFallbackY) }
                }
            } catch (error: Throwable) { recordCleanup(error) }
            try { test.waitForIdleSync() } catch (error: Throwable) { recordCleanup(error) }
            cleanupFailure?.let { if (failure == null) throw it else failure!!.addSuppressed(it) }
        }
    }
    private fun useCurveOpensPreparation(page: MainPageTestHost, fragment: CurveFragment, app: MobileApplication) {
        val activity = page.activity
        val preferences = activity.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val hadSelection = preferences.contains("selected")
        val previousSelection = preferences.getString("selected", null)
        fun field(owner: Any, name: String) = owner.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }.get(owner)
        val chosen = app.curves.items.first { app.curves.canStart(it) }
        val owner = activity.currentService
        val shotBefore = owner?.shotState
        val traceBefore = owner?.chartTraceIdentity
        try {
            main {
                CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                    .apply { isAccessible = true }.invoke(fragment, chosen)
                val use = activity.window.decorView.findViewWithTag<Button>("product/curve-library/use")
                check(use.isEnabled && use.performClick())
            }
            test.waitForIdleSync()
            main {
                val extraction = page.active("extraction") as ExtractionFragment
                check(preferences.getString("selected", null) == chosen.id)
                val selected = ExtractionFragment::class.java.getDeclaredMethod("selected")
                    .apply { isAccessible = true }.invoke(extraction) as CurveLibraryItem
                check(selected.id == chosen.id && field(extraction, "presetSlot") == 7)
                check(field(activity, "pendingStartConfirmation") == false &&
                    field(extraction, "startConfirmationRequested") == false)
                check(activity.currentService === owner && owner?.shotState == shotBefore &&
                    owner?.chartTraceIdentity == traceBefore) { "Using a curve started or replaced a shot" }
                check((field(extraction, "transientDialogs") as Collection<*>).isEmpty()) {
                    "Using a curve opened a start confirmation instead of preparation" }
            }
        } finally {
            main {
                preferences.edit().apply {
                    if (hadSelection) putString("selected", previousSelection) else remove("selected")
                }.commit().also { check(it) }
                activity.select(TaskDestination.CURVES)
            }
            test.waitForIdleSync()
        }
    }

    fun run(expectedFontScale: Float) {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val preferences = context.getSharedPreferences("curves", 0)
        val before = preferences.all.toMap()
        val usage = app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) }
        val page = MainPageTestHost.launch(test, TaskDestination.CURVES)
        val activity = page.activity
        val fragment = page.active("root:CURVES") as CurveFragment
        try {
            test.waitForIdleSync()
            main {
                check(kotlin.math.abs(activity.resources.configuration.fontScale - expectedFontScale) < .01f)
                fun field(name: String) = CurveFragment::class.java.getDeclaredField(name).apply { isAccessible = true }.get(fragment)
                val root = activity.window.decorView
                val newCurve = requireNotNull(root.findViewWithTag<Button>("product/curve-library/new"))
                val manage = requireNotNull(root.findViewWithTag<Button>("curve-library-management"))
                val search = field("search") as android.widget.EditText
                fun iconAction(button: Button, label: CharSequence) =
                    button.text.isEmpty() && button.contentDescription == label && button.tooltipText == label &&
                        button.layoutParams.width == HoyiUi.dp(activity, 48) &&
                        button.layoutParams.height == HoyiUi.dp(activity, 48) &&
                        button.compoundDrawablesRelative[0]?.intrinsicWidth == HoyiUi.dp(activity, 24)
                check(search.hint.toString() == activity.getString(R.string.curve_search_hint) &&
                    search.contentDescription.toString().contains(activity.getString(R.string.profiles_name)))
                check(root.findViewWithTag<View>("product/curve-library/header") == null &&
                    newCurve.isShown && newCurve.isClickable && newCurve.height >= HoyiUi.dp(activity, 48) &&
                    iconAction(newCurve, activity.getString(R.string.profiles_new)) &&
                    newCurve.isEnabled == (app.customCurvesResult.isSuccess) &&
                    manage.isShown && manage.height >= HoyiUi.dp(activity, 48) &&
                    iconAction(manage, activity.getString(R.string.task_curve_library_manage)) &&
                    newCurve.parent === manage.parent && (newCurve.parent as View).parent === search.parent) {
                    "Curve library lost the direct accessible create action beside search" }
                var chosen = app.curves.items.first { CurveStageParameters.pressureTargets(it).isNotEmpty() }
                val originalChosen = chosen
                CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                    .apply { isAccessible = true }.invoke(fragment, chosen)
                val directEdit = field("directEdit") as Button
                val directShare = field("directShare") as Button
                val detailActions = field("detailActions") as Button
                check(iconAction(directEdit, (field("editDraft") as Button).text) &&
                    iconAction(directShare, activity.getString(R.string.profiles_share)) &&
                    iconAction(detailActions, activity.getString(R.string.task_curve_library_actions))) {
                    "Curve detail icon actions lost labels or 48dp target" }
                val collection = activity.window.decorView.findViewWithTag<TaskCollectionLayout>("task-collection")
                val usage = requireNotNull(activity.window.decorView.findViewWithTag<TextView>("product/curve-library/usage-count"))
                val identity = requireNotNull(activity.window.decorView.findViewWithTag<View>("product/curve-library/identity"))
                check((usage.parent as View).parent === identity &&
                    usage.text.toString() == activity.getString(R.string.curve_usage_count,
                        app.curveUsageResult.getOrThrow().stats(chosen.id).count).removePrefix(" · ") &&
                    !usage.text.startsWith(" · ")) { "Curve usage lost its real count or compact identity position" }
                val plan = field("recipePlan") as CurveRecipePlanView
                val fallback = field("previewFallbackScroll") as ScrollView
                val text = field("previewFallback") as TextView
                val metrics = field("previewMetrics") as CurvePreviewMetrics
                check(metrics.paddingTop == HoyiUi.dp(activity, 8) &&
                    metrics.paddingBottom == HoyiUi.dp(activity, 8))
                val ability = field("availability") as TextView
                val select = field("select") as Button
                val parameter = field("parameterShortcut") as Button
                val directory = File(context.getExternalFilesDir(null), "curve-library-checks").apply { mkdirs() }
                val oldWidth = collection.width
                val oldHeight = collection.height
                fun measure(name: String, width: Int, height: Int) {
                    collection.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                    collection.layout(0, 0, width, height)
                    check(collection.detailBody.visibility == View.VISIBLE)
                    if (width == HoyiUi.dp(activity, 360)) check(collection.browserBody.visibility == View.GONE)
                    for (action in listOf(select, parameter)) {
                        check(action.isShown && action.height >= HoyiUi.dp(activity, 48))
                        LanguageUiLayoutChecks.textFits(action, "$name fixed curve action")
                        val bounds = android.graphics.Rect(0, 0, action.width, action.height)
                        collection.offsetDescendantRectToMyCoords(action, bounds)
                        check(bounds.left >= 0 && bounds.right <= width && bounds.top >= 0 && bounds.bottom <= height) {
                            "$name fixed curve action clipped: ${action.text} $bounds"
                        }
                        val row = action.parent as android.widget.LinearLayout
                        check(action.top >= 0 && action.bottom <= row.height) {
                            "$name action extends outside its own row: ${action.top}..${action.bottom}/${row.height}"
                        }
                    }
                    // Doubled integer centers allow <=1 only for odd/even height rounding
                    // (<=0.5px center quantization), never clipping; bounds above remain strict.
                    check(kotlin.math.abs((parameter.top + parameter.bottom) - (select.top + select.bottom)) <= 1) {
                        "$name footer bounds are not vertically centered"
                    }
                    File(directory, "$name-footer.txt").writeText(listOf(parameter, select).joinToString("\n") {
                        "text=${it.text} size=${it.width}x${it.height} local=${it.left},${it.top}..${it.right},${it.bottom} " +
                            "parent=${(it.parent as View).width}x${(it.parent as View).height}"
                    } + "\nfontScale=$expectedFontScale collection=${width}x$height syntheticMeasure=true\n")
                    for (index in 0 until collection.detailBody.childCount) {
                        val child = collection.detailBody.getChildAt(index)
                        if (child.visibility != View.VISIBLE) continue
                        check(child.height > 0 && child.top >= 0 && child.bottom <= collection.detailBody.height) {
                            "$name detail control/fallback clipped: ${child.javaClass.simpleName} ${child.top}..${child.bottom}/${collection.detailBody.height}"
                        }
                    }
                    if (plan.isShown) {
                        val preview = CurveStageParameters.preview(chosen)
                        check(preview.stages.isNotEmpty() && (1..preview.stages.size).all {
                            plan.findViewWithTag<View>("product/curve-library/stage/$it")?.isShown == true
                        }) {
                            "$name actual stage cards do not match per-stage control targets"
                        }
                        check(metrics.visibility == View.VISIBLE && metrics.contentDescription.isNotBlank())
                        check(metrics.childCount == 3) { "$name recipe metrics lost a column" }
                        for (columnIndex in 0 until metrics.childCount) {
                            val column=metrics.getChildAt(columnIndex) as android.view.ViewGroup
                            check(column.childCount == 2) { "$name recipe metric column lost label/value" }
                            if (column.getChildAt(1) !is TextView) check(column.getChildAt(0).tag == "product/curve-library/summary/$columnIndex/label")
                            for (labelIndex in 0 until column.childCount) {
                                val node=column.getChildAt(labelIndex)
                                if (node is TextView) {
                                    LanguageUiLayoutChecks.textFits(node,"$name recipe metric")
                                } else {
                                    // Only the explicit M3 number/unit row may replace the legacy value TextView.
                                    val prefix="product/curve-library/summary/$columnIndex"
                                    check(node is android.widget.LinearLayout && node.tag == prefix) {
                                        "$name unexpected recipe metric container ${node.javaClass.name}/${node.tag}"
                                    }
                                    check(node.childCount == if (columnIndex < 2) 2 else 1)
                                    val number=node.getChildAt(0) as TextView
                                    check(number.tag == "$prefix/number" && number.isShown)
                                    LanguageUiLayoutChecks.textFits(number,"$name recipe metric number")
                                    if (columnIndex < 2) {
                                        val unit=node.getChildAt(1) as TextView
                                        check(unit.tag == "$prefix/unit")
                                        if (unit.visibility == View.VISIBLE) {
                                            check(unit.isShown)
                                            LanguageUiLayoutChecks.textFits(unit,"$name recipe metric unit")
                                            val a=IntArray(2); val b=IntArray(2)
                                            number.getLocationOnScreen(a); unit.getLocationOnScreen(b)
                                            check(number.baseline >= 0 && unit.baseline >= 0 &&
                                                a[1]+number.baseline == b[1]+unit.baseline) {
                                                "$name recipe metric number/unit baseline differs"
                                            }
                                        } else check(unit.visibility == View.GONE) { "$name unit state must be visible or explicitly absent" }
                                    }
                                }
                            }
                        }
                        check(plan.height > 0 && ability.visibility == View.VISIBLE && ability.parent != null)
                    } else {
                        check(fallback.visibility == View.VISIBLE && fallback.height > 0)
                        val preview = CurveStageParameters.preview(chosen)
                        if (!preview.renderable) {
                            val reason = if (preview.stages.any { it.unit == CurveStageParameters.Unit.RAW_UNKNOWN })
                                R.string.curve_control_mode_raw else R.string.task_curve_library_no_pressure
                            check(text.text.contains(activity.getString(reason)))
                            preview.stages.forEach { check(text.text.contains(it.target.toString())) }
                        } else {
                            check(text.text.contains(chosen.name) && text.text.contains(ability.text))
                            check(text.text.contains(metrics.contentDescription))
                            check(text.text.contains(activity.getString(when {
                                preview.mixed -> R.string.curve_control_caption_mixed
                                preview.hasFlow -> R.string.curve_control_caption_flow
                                else -> R.string.profiles_stage_caption
                            })))
                            preview.stages.forEachIndexed { index, stage ->
                                val unit = if (stage.unit == CurveStageParameters.Unit.FLOW_ML_PER_SECOND) "mL/s" else "bar"
                                check(text.text.contains(activity.getString(R.string.profiles_stage, index + 1)) &&
                                    text.text.contains(String.format(Locale.ROOT, "%.1f %s", stage.target / 10.0, unit)))
                            }
                        }
                        LanguageUiLayoutChecks.textFits(text, name)
                        val maximum = (fallback.getChildAt(0).height - fallback.height).coerceAtLeast(0)
                        fallback.scrollTo(0, maximum); check(fallback.scrollY == maximum); fallback.scrollTo(0, 0)
                    }
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    try {
                        bitmap.eraseColor(activity.getColor(R.color.mobile_background)); collection.draw(Canvas(bitmap))
                        File(directory, "$name.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                    } finally { bitmap.recycle() }
                }
                try {
                    val compactWidth = HoyiUi.dp(activity, 360)
                    measure("normal", compactWidth, HoyiUi.dp(activity, 640)); check(plan.isShown)
                    measure("short-180dp", compactWidth, HoyiUi.dp(activity, 180))
                    check(text.text.contains(usage.text)) { "Short curve summary lost usage status" }
                    measure("recovered", compactWidth, HoyiUi.dp(activity, 640)); check(plan.isShown)
                    for ((kind, item) in listOf(
                        "noPressure" to app.curves.items.first { it.factoryCurve != null && CurveStageParameters.pressureTargets(it).isEmpty() },
                        "fourStage" to CurveFooterFixtures.fourStage(app.curves))) {
                        chosen = item
                        CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                            .apply { isAccessible = true }.invoke(fragment, chosen)
                        check(iconAction(directEdit, (field("editDraft") as Button).text))
                        // Use this CI profile's actual collection space, including the user tablet profiles.
                        measure("footer-$kind-actual-space", oldWidth, oldHeight)
                        check(select.isEnabled == app.curves.canStart(item))
                    }
                    CurveFragment::class.java.getDeclaredMethod("show", CurveLibraryItem::class.java)
                        .apply { isAccessible = true }.invoke(fragment, originalChosen)
                    check(preferences.all == before)
                    check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usage)
                } finally {
                    collection.measure(View.MeasureSpec.makeMeasureSpec(oldWidth, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(oldHeight, View.MeasureSpec.EXACTLY))
                    collection.layout(0, 0, oldWidth, oldHeight); collection.requestLayout()
                }
            }
            test.waitForIdleSync()
            returnIntentRegression(activity, fragment, app, File(context.getExternalFilesDir(null), "curve-library-checks"))
            imeSearchRegression(activity, fragment, app)
            val frames = java.util.concurrent.CountDownLatch(1)
            main {
                activity.window.decorView.postOnAnimation {
                    activity.window.decorView.postOnAnimation { frames.countDown() }
                }
            }
            check(frames.await(5, java.util.concurrent.TimeUnit.SECONDS)) { "Selected curve window did not draw" }
            val directory = File(context.getExternalFilesDir(null), "curve-library-checks")
            main {
                val collection = activity.window.decorView.findViewWithTag<TaskCollectionLayout>("task-collection")
                check(collection.detailBody.visibility == View.VISIBLE)
                check(preferences.all == before)
                check(app.curves.items.associate { it.id to app.curveUsageResult.getOrThrow().stats(it.id) } == usage)
                TaskTypographyEvidence.capture(activity, directory)
            }
            val window = requireNotNull(test.uiAutomation.takeScreenshot())
            try {
                File(directory, "selected-window.png").outputStream().use {
                    check(window.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { window.recycle() }
            File(directory, "selected-window.txt").writeText(
                "actualWindow=true restoredAfterSyntheticViewport=true fontScale=$expectedFontScale preferencesUnchanged=true usageUnchanged=true noBle=true\n")
            useCurveOpensPreparation(page, fragment, app)
        } finally { main { activity.finish() }; test.waitForIdleSync() }
    }
}
