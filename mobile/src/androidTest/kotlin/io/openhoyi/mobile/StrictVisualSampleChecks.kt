package io.openhoyi.mobile

import android.app.ActivityManager
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.widget.TextView
import android.widget.ScrollView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Whole native windows only, on the existing disposable CI emulator. No product Activity/services. */
internal class StrictVisualSampleChecks(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    private fun sha(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    private fun storage(context: Context): Map<String, String> = listOf("files", "shared_prefs", "databases").flatMap { name ->
        File(context.applicationInfo.dataDir, name).walkTopDown().filter { it.isFile }.map { it.path to sha(it) }.toList()
    }.toMap()
    private fun services(context: Context): List<String> =
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getRunningServices(100)
            .filter { it.service.packageName == context.packageName }.map { it.service.className }.sorted()
    private fun settled(activity: StrictVisualSampleActivity) {
        test.waitForIdleSync()
        val frames = CountDownLatch(1)
        main { activity.sampleRoot.postOnAnimation { activity.sampleRoot.postOnAnimation { frames.countDown() } } }
        check(frames.await(5, TimeUnit.SECONDS)) { "Specimen stable-frame wait timed out" }
        test.waitForIdleSync()
    }
    fun run(arguments: Bundle): Int {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        check(Build.VERSION.SDK_INT >= 30)
        check(arguments.getString("disposableCiEmulator") == "true" && Build.HARDWARE in setOf("ranchu", "goldfish"))
        StrictVisualSampleFixtureChecks.run()
        val source = requireNotNull(arguments.getString("sourceSha")).also { require(it.matches(Regex("[0-9a-f]{40}"))) }
        val expectedScale = requireNotNull(arguments.getString("expectedFontScale")).toFloat()
        check(expectedScale in setOf(1f, 1.3f))
        val expectedTheme = requireNotNull(arguments.getString("sampleTheme")).also { require(it in setOf("light", "dark")) }
        val profile = "tablet-user-landscape-${expectedTheme}-font-${if (expectedScale == 1f) "1.0" else "1.3"}"
        val base = File(context.getExternalFilesDir(null), "strict-native-samples/$source/$profile").apply { mkdirs() }
        val baselineStorage = storage(context)
        val baselineServices = services(context)
        check(baselineServices.isEmpty()) { "Unexpected pre-existing Mock service; no automatic cleanup" }
        val actualApkSha = sha(File(context.applicationInfo.sourceDir))
        check(actualApkSha == arguments.getString("apkSha")) { "Installed Mock APK differs from host artifact" }
        val fontSha = context.resources.openRawResource(R.font.noto_sans_sc_variable).use { input ->
            val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(8192)
            while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
            digest.digest().joinToString("") { "%02x".format(it) }
        }
        check(fontSha == StrictVisualSampleTokens.FONT_SHA)
        var commonGeometry: String? = null
        for (fixtureId in StrictVisualSampleFixtures.ids) {
            val directory = File(base, fixtureId).apply { mkdirs() }
            val activity = test.startActivitySync(Intent(context, StrictVisualSampleActivity::class.java)
                .putExtra("fixtureId", fixtureId).putExtra("candidateVersion", StrictVisualSampleFixtures.VERSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as StrictVisualSampleActivity
            try {
                settled(activity)
                // Retain a real whole-window image even if a subsequent measurement assertion fails.
                val shot = requireNotNull(test.uiAutomation.takeScreenshot())
                try { File(directory, "window.png").outputStream().use { check(shot.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
                finally { shot.recycle() }
                val anchors = JSONArray()
                val canvas = JSONArray()
                val layoutIssues = JSONArray()
                lateinit var window: JSONObject
                lateinit var tokens: JSONObject
                lateinit var roles: JSONArray
                main {
                    val config = activity.resources.configuration
                    check(config.locales[0].language == "zh" && config.fontScale == expectedScale)
                    check(activity.resources.displayMetrics.densityDpi == 360)
                    check(activity.palette.dark == (expectedTheme == "dark"))
                    val all = nodes(activity.sampleRoot)
                    check(all.none { it.hasOnClickListeners() }) { "Specimen contains callback-bearing actions" }
                    for (action in activity.actionViews) check(action.height >= StrictVisualSampleTokens.dp(activity, 48f)) {
                        "Action specimen below 48dp: ${action.tag}"
                    }
                    for (view in all) {
                        val tag = view.tag?.toString() ?: continue
                        val location = IntArray(2); view.getLocationOnScreen(location)
                        val rootLocation = IntArray(2); activity.sampleRoot.getLocationOnScreen(rootLocation)
                        val bounds = Rect(); val visible = view.getGlobalVisibleRect(bounds)
                        anchors.put(JSONObject().put("semanticId", tag).put("visible", visible)
                            .put("rectPx", JSONArray(listOf(location[0], location[1], location[0] + view.width, location[1] + view.height)))
                            .put("appRectDp", JSONArray(listOf((location[0] - rootLocation[0]) / activity.resources.displayMetrics.density,
                                (location[1] - rootLocation[1]) / activity.resources.displayMetrics.density,
                                (location[0] + view.width - rootLocation[0]) / activity.resources.displayMetrics.density,
                                (location[1] + view.height - rootLocation[1]) / activity.resources.displayMetrics.density)))
                            .put("visibleRectPx", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom))))
                        if (view is TextView && view.layout != null) {
                            check(view.layout.ellipsizedWidth == view.width - view.compoundPaddingLeft - view.compoundPaddingRight)
                            check((0 until view.layout.lineCount).none { view.layout.getEllipsisCount(it) > 0 }) { "Ellipsized specimen text: $tag" }
                            check(view.layout.height <= view.height - view.compoundPaddingTop - view.compoundPaddingBottom) { "Clipped text line box: $tag" }
                        }
                        if (tag.endsWith("primary") || tag.contains("/actions/") || tag.startsWith("sample/nav/item") || tag == "sample/nav/selected")
                            check(view.height >= StrictVisualSampleTokens.dp(activity, 48f)) { "Touch specimen below 48dp: $tag" }
                    }
                    activity.charts.forEach { chart ->
                        check(chart.evidence.length() > 0)
                        for (index in 0 until chart.evidence.length()) {
                            val item = chart.evidence.getJSONObject(index)
                            if (item.getString("operation") == "text") {
                                val glyph = item.getJSONArray("glyphBoundsPx")
                                check(glyph.getDouble(0) >= 0 && glyph.getDouble(1) >= 0 &&
                                    glyph.getDouble(2) <= chart.width && glyph.getDouble(3) <= chart.height) {
                                    "Sample Canvas glyph clipped: ${item.getString("text")} $glyph"
                                }
                            }
                            canvas.put(item)
                            if (item.getString("operation") == "plotGeometry") {
                                val location = IntArray(2); chart.getLocationOnScreen(location)
                                val origin = IntArray(2); activity.sampleRoot.getLocationOnScreen(origin)
                                val rect = item.getJSONArray("plotBoundsPx")
                                val absolute = listOf(location[0] + rect.getDouble(0), location[1] + rect.getDouble(1),
                                    location[0] + rect.getDouble(2), location[1] + rect.getDouble(3))
                                val density = activity.resources.displayMetrics.density
                                anchors.put(JSONObject().put("semanticId", item.getString("semanticId"))
                                    .put("visible", chart.isShown).put("rectPx", JSONArray(absolute))
                                    .put("appRectDp", JSONArray(absolute.mapIndexed { i, value -> (value - origin[i % 2]) / density }))
                                    .put("measurement", "actual_Canvas_plot_geometry_not_View_bounds"))
                            }
                        }
                    }
                    activity.icons.forEach { icon -> check(icon.evidence.length() > 0); canvas.put(icon.evidence) }
                    if (fixtureId == StrictVisualSampleFixtures.ids[1]) {
                        check((1..4).all { index -> all.single { it.tag == "sample/share/table/row$index" }.height > 0 })
                        check(!all.single { it.tag == "sample/share/fileShareDisabled" }.isEnabled)
                    }
                    if (fixtureId == StrictVisualSampleFixtures.ids[2]) {
                        check((1..2).all { index -> all.single { it.tag == "sample/inventory/ledgerRow$index" }.height > 0 })
                    }
                    if (fixtureId == StrictVisualSampleFixtures.ids[0]) {
                        val recent = all.single { it.tag == "sample/preparation/recent" } as ScrollView
                        val rows = (1..3).map { index -> all.single { it.tag == "sample/preparation/recent/row$index" } }
                        val visible = Rect(); recent.getGlobalVisibleRect(visible)
                        val viewportHeight = visible.height()
                        val requiredRows = if (expectedScale == 1f) 2 else 1
                        val requiredHeight = rows.take(requiredRows).sumOf { it.height }
                        val firstRowDeficit = (rows.first().height - viewportHeight).coerceAtLeast(0)
                        val requiredDeficit = (requiredHeight - viewportHeight).coerceAtLeast(0)
                        val completeRows = rows.count { row ->
                            val bounds = Rect(); row.getGlobalVisibleRect(bounds) && bounds.height() == row.height && bounds.width() == row.width
                        }
                        val originalY = recent.scrollY
                        val maximum = (recent.getChildAt(0).height - recent.height).coerceAtLeast(0)
                        val reachability = JSONArray()
                        val reachable = try {
                            rows.map { row ->
                                row.requestRectangleOnScreen(Rect(0, 0, row.width, row.height), true)
                                val bounds = Rect()
                                val complete = row.getGlobalVisibleRect(bounds) && bounds.width() == row.width && bounds.height() == row.height
                                reachability.put(JSONObject().put("semanticId", row.tag).put("complete", complete)
                                    .put("visibleRectPx", JSONArray(listOf(bounds.left, bounds.top, bounds.right, bounds.bottom)))
                                    .put("sizePx", JSONArray(listOf(row.width, row.height))).put("scrollY", recent.scrollY))
                                complete
                            }.all { it }
                        } finally { recent.scrollTo(0, originalY) }
                        val density = activity.resources.displayMetrics.density
                        val appHeightPx = activity.sampleRoot.height
                        val appHeightDp = appHeightPx / density
                        val minimumHeightPx = (618f * density).roundToInt()
                        val maximumHeightPx = (623f * density).roundToInt()
                        val plannedHeight = appHeightPx in minimumHeightPx..maximumHeightPx
                        val fail = requiredDeficit > 0 || !reachable || completeRows < requiredRows || !plannedHeight
                        layoutIssues.put(JSONObject().put("semanticId", "sample/preparation/recent")
                            .put("status", if (fail) "FAIL" else "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
                            .put("reason", if (fail) "insufficient_complete_rows_or_unreachable_content_or_unplanned_window_height" else "measured_row_budget_and_local_scroll")
                            .put("viewportHeightPx", viewportHeight).put("naturalRowHeightsPx", JSONArray(rows.map { it.height }))
                            .put("requiredCompleteRows", requiredRows).put("actualCompleteRows", completeRows)
                            .put("minimumOneRowDeficitPx", firstRowDeficit).put("requiredRowsDeficitPx", requiredDeficit)
                            .put("allThreeRowsReachable", reachable).put("maxScrollPx", maximum).put("appHeightDp", appHeightDp)
                            .put("rowReachability", reachability).put("appHeightPx", appHeightPx)
                            .put("plannedHeightRangePx", JSONArray(listOf(minimumHeightPx, maximumHeightPx)))
                            .put("heightBoundaryConversion", "roundToInt(dp*density); compare_actual_integer_px_no_tolerance")
                            .put("plannedWindowHeight", plannedHeight))
                    }
                    val root = activity.sampleRoot
                    val origin = IntArray(2); root.getLocationOnScreen(origin)
                    val metrics = activity.windowManager.currentWindowMetrics.bounds
                    val insets = requireNotNull(root.rootWindowInsets)
                    val bars = insets.getInsets(WindowInsets.Type.systemBars())
                    val ime = insets.getInsets(WindowInsets.Type.ime())
                    window = JSONObject().put("displayPx", JSONArray(listOf(metrics.width(), metrics.height())))
                        .put("appOriginPx", JSONArray(origin.toList())).put("appSizePx", JSONArray(listOf(root.width, root.height)))
                        .put("appSizeDp", JSONArray(listOf(root.width / root.resources.displayMetrics.density, root.height / root.resources.displayMetrics.density)))
                        .put("systemInsetsPx", JSONArray(listOf(bars.left, bars.top, bars.right, bars.bottom)))
                        .put("imeVisible", insets.isVisible(WindowInsets.Type.ime())).put("imeInsetsPx", JSONArray(listOf(ime.left, ime.top, ime.right, ime.bottom)))
                        .put("densityDpi", config.densityDpi).put("fontScale", config.fontScale).put("locale", config.locales.toLanguageTags())
                    check(metrics.width() == 2560 && metrics.height() == 1600 && !insets.isVisible(WindowInsets.Type.ime()))
                    val signature = JSONObject().put("origin", JSONArray(origin.toList())).put("app", JSONArray(listOf(root.width, root.height)))
                        .put("insets", window.getJSONArray("systemInsetsPx")).toString()
                    if (commonGeometry == null) commonGeometry = signature else check(commonGeometry == signature) { "Window geometry changed between same-profile specimens" }
                    tokens = StrictVisualSampleTokens.json(activity.palette)
                    roles = activity.requestedRoles()
                    TaskTypographyEvidence.capture(activity, directory)
                }
                val measured = JSONObject(File(directory, "StrictVisualSampleActivity-typography.json").readText())
                val layoutFailed = (0 until layoutIssues.length()).any { layoutIssues.getJSONObject(it).getString("status") == "FAIL" }
                measured.put("candidateRoles", roles).put("canvas", canvas)
                    .put("layoutIssues", layoutIssues)
                    .put("canvasPaintCoverage", "sample_owned_chart_and_icon_operations_only_not_product_canvas")
                    .put("gates", JSONObject().put("G1", if (layoutFailed) "FAIL" else "BASELINE_PENDING").put("G2", "BASELINE_PENDING").put("G3", "BASELINE_PENDING").put("G4", "BASELINE_PENDING"))
                File(directory, "visual.json").writeText(measured.toString(2))
                File(directory, "tokens.json").writeText(tokens.toString(2))
                File(directory, "anchors.json").writeText(JSONObject().put("actual", anchors).put("original", JSONObject.NULL)
                    .put("originalStatus", "host_finalizer_must_attach_Astra_annotations").toString(2))
                File(directory, "manifest.json").writeText(JSONObject().put("schemaVersion", 1)
                    .put("specimenOnly", true).put("productAcceptance", false).put("baselineStatus", "proposed")
                    .put("sourceSha", source).put("sourceDirty", arguments.getString("sourceDirty") == "true")
                    .put("apkSha256", actualApkSha).put("candidateVersion", StrictVisualSampleFixtures.VERSION)
                    .put("fontSha256", fontSha).put("tokensSha256", sha(File(directory, "tokens.json")))
                    .put("fixture", activity.fixture.json()).put("profile", profile).put("theme", expectedTheme)
                    .put("window", window).put("api", Build.VERSION.SDK_INT).put("fingerprint", Build.FINGERPRINT)
                    .put("hardware", Build.HARDWARE).put("ciRun", arguments.getString("ciRun"))
                    .put("ciJob", arguments.getString("ciJob")).put("stableFrame", "idle_and_two_postOnAnimation_callbacks")
                    .put("screenCompositeColorCoverage", "PNG_requires_Astra_review_unknown_not_pass")
                    .put("storageBeforeSha256", sha(JSONObject(baselineStorage).toString().toByteArray()))
                    .put("businessCallbacks", false).put("productActivitiesStarted", false).put("servicesStarted", false)
                    .put("layoutStatus", if (layoutFailed) "FAIL" else "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE")
                    .put("windowPngSha256", sha(File(directory, "window.png"))).toString(2))
                File(directory, "comparison.md").writeText("# ${activity.fixture.title} — candidate specimen only\n\n" +
                    "specimenOnly=true; productAcceptance=false; baselineStatus=proposed\n\n" +
                    "| Gate | Status | Evidence |\n|---|---|---|\n| G1 间距/构图 | ${if (layoutFailed) "FAIL" else "BASELINE_PENDING"} | anchors.json / window.png / layoutIssues |\n" +
                    "| G2 字体 | BASELINE_PENDING | visual.json / font SHA |\n| G3 字号/行高 | BASELINE_PENDING | candidateRoles / Canvas bounds |\n" +
                    "| G4 背景/状态色 | BASELINE_PENDING | Drawable未知保留；Canvas Paint；整窗PNG待Astra逐图审查 |\n\n" +
                    "原稿锚点由host finalizer附入；不把测量结果倒写成设计标准。库存使用入口仅展示，真实操作需先确认投粉才扣减。\n")
            } finally {
                main { activity.finish() }; test.waitForIdleSync()
                check(storage(context) == baselineStorage) { "Specimen changed persistent app state; no automatic reset" }
                check(services(context) == baselineServices) { "Specimen changed running service owners" }
            }
            val manifestFile = File(directory, "manifest.json")
            val manifest = JSONObject(manifestFile.readText()).put("storageUnchanged", true)
                .put("storageAfterSha256", sha(JSONObject(storage(context)).toString().toByteArray()))
                .put("servicesBefore", JSONArray(baselineServices)).put("servicesAfter", JSONArray(services(context)))
            manifestFile.writeText(manifest.toString(2))
        }
        return StrictVisualSampleFixtures.ids.size
    }
}
