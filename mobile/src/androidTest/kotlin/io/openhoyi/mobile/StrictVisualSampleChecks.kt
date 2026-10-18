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
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/** Whole native windows only, on the existing disposable CI emulator. No product Activity/services. */
internal class StrictVisualSampleChecks(private val test: Instrumentation) {
    private var frameSequence = 0L
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
    private fun committedFrame(activity: StrictVisualSampleActivity, timeoutMs: Long = 5000): JSONObject {
        test.waitForIdleSync()
        val frames = CountDownLatch(1)
        lateinit var commit: JSONObject
        val callback = Runnable {
            frameSequence++
            commit = JSONObject().put("callbackSequence", frameSequence).put("committedAtElapsedNanos", SystemClock.elapsedRealtimeNanos())
            frames.countDown()
        }
        main {
            val root = activity.sampleRoot
            check(root.isAttachedToWindow && root.isHardwareAccelerated) { "Specimen requires a hardware-rendered attached root" }
            root.viewTreeObserver.registerFrameCommitCallback(callback)
            // Animation callbacks and queue-idle are not render submissions. Force a
            // real root draw after the scroll mutation, then await its commit callback.
            root.invalidate()
        }
        if (!frames.await(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
            main { activity.sampleRoot.viewTreeObserver.unregisterFrameCommitCallback(callback) }
            error("Specimen hardware frame was not committed")
        }
        return commit
    }
    private fun settled(activity: StrictVisualSampleActivity) { committedFrame(activity) }

    private data class WindowCopy(val bitmap: Bitmap?, val resultCode: Int, val callbackCompleted: Boolean, val sourceRectAfterSdk: Rect)
    private data class CapturedFrame(val target: JSONObject, val proof: JSONObject)

    private fun copyWindow(activity: StrictVisualSampleActivity, source: Rect, timeoutMs: Long): WindowCopy {
        val bitmap = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        val code = AtomicInteger(-1)
        val requested = Rect(source)
        main {
            PixelCopy.request(activity.window, requested, bitmap, { result ->
                code.set(result)
                copied.countDown()
            }, Handler(Looper.getMainLooper()))
        }
        val completed = copied.await(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)
        // A timed-out asynchronous copy may still own its bitmap. Do not recycle a
        // buffer still being written; the failed request/Activity is discarded.
        return WindowCopy(if (completed) bitmap else null, code.get(), completed, Rect(requested))
    }

    private fun pixelSha(bitmap: Bitmap, rect: Rect = Rect(0, 0, bitmap.width, bitmap.height)): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val row = IntArray(rect.width())
        val bytes = ByteArray(rect.width() * 4)
        for (y in rect.top until rect.bottom) {
            bitmap.getPixels(row, 0, row.size, rect.left, y, row.size, 1)
            row.forEachIndexed { index, color ->
                bytes[index * 4] = (color ushr 24).toByte()
                bytes[index * 4 + 1] = (color ushr 16).toByte()
                bytes[index * 4 + 2] = (color ushr 8).toByte()
                bytes[index * 4 + 3] = color.toByte()
            }
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun pixelDifferenceEvidence(first: Bitmap, second: Bitmap, secondRect: Rect, screenRect: Rect): JSONObject {
        check(first.width == secondRect.width() && first.height == secondRect.height())
        val a = IntArray(first.width); val b = IntArray(first.width)
        var differences = 0L
        var left = first.width; var top = first.height; var right = 0; var bottom = 0
        val samples = JSONArray()
        for (y in 0 until first.height) {
            first.getPixels(a, 0, a.size, 0, y, a.size, 1)
            second.getPixels(b, 0, b.size, secondRect.left, secondRect.top + y, b.size, 1)
            for (x in a.indices) if (a[x] != b[x]) {
                differences++
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                if (samples.length() < 16) samples.put(JSONObject()
                    .put("surfacePx", JSONArray(listOf(x, y)))
                    .put("comparisonPx", JSONArray(listOf(secondRect.left + x, secondRect.top + y)))
                    .put("displayPx", JSONArray(listOf(screenRect.left + x, screenRect.top + y)))
                    .put("firstArgb", "%08x".format(a[x])).put("secondArgb", "%08x".format(b[x])))
            }
        }
        return JSONObject().put("count", differences).put("sampleLimit", 16).put("firstDifferences", samples)
            .put("differenceBoundsPx", if (differences == 0L) JSONObject.NULL else JSONArray(listOf(left, top, right, bottom)))
            .put("displayDifferenceBoundsPx", if (differences == 0L) JSONObject.NULL else JSONArray(listOf(
                screenRect.left + left, screenRect.top + top, screenRect.left + right, screenRect.top + bottom)))
    }

    private fun bitmapDescription(bitmap: Bitmap) = JSONObject().put("config", bitmap.config?.name)
        .put("colorSpace", bitmap.colorSpace?.name).put("hasAlpha", bitmap.hasAlpha())
        .put("sizePx", JSONArray(listOf(bitmap.width, bitmap.height)))

    /** Synchronize equivalent domains, never raw surface == composed display.
     * API 34 Window capture flushes SurfaceFlinger before captureLayers; bracket
     * display captures with that bridge and PixelCopy. Original display PNGs are
     * still the visual evidence, including every system-composited corner. */
    private fun screenshot(activity: StrictVisualSampleActivity, file: File,
        observe: () -> JSONObject = { JSONObject() }): CapturedFrame {
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 5000
        val attempts = JSONArray()
        val artifacts = JSONArray()
        data class Candidate(val attempt: Int, val images: Map<String, Bitmap>) {
            fun recycle() { images.values.forEach { it.recycle() } }
        }
        var retained: Candidate? = null
        fun imageName(role: String) = when (role) {
            "display" -> file.name
            else -> "${file.nameWithoutExtension}-$role.png"
        }
        fun persist(candidate: Candidate, failure: Boolean) {
            candidate.images.forEach { (role, bitmap) ->
                // On success retain both unmodified full displays. All six raw
                // inputs are retained on failure, outside the matching budget.
                if (failure || role == "display" || role == "display-after") {
                    val output = File(file.parentFile, imageName(role))
                    bitmap.output(output)
                    artifacts.put(JSONObject().put("attempt", candidate.attempt).put("role", role)
                        .put("file", output.name).put("fileSha256", sha(output)).put("bitmap", bitmapDescription(bitmap)))
                }
            }
        }
        val proofFile = File(file.parentFile, "${file.nameWithoutExtension}-frame-sync.json")
        fun writeProof(accepted: Boolean, error: String? = null): JSONObject {
            val proof = JSONObject().put("protocolVersion", 2)
                .put("protocol", "commit_S1_WindowLayer1_Display1_Display2_WindowLayer2_S2")
                .put("accepted", accepted).put("timeoutMs", 5000).put("attempts", attempts)
                .put("status", if (accepted) "MATCHED_WINDOW_LAYER_AND_STABLE_COMPOSITE_NOT_VISUAL_ACCEPTANCE" else "FAIL")
                .put("image", file.name).put("secondDisplayImage", imageName("display-after"))
                .put("wholeDisplayUiAutomation", true).put("displayComparisonRegion", "entire_measured_app_rect")
                .put("pixelComparison", "each_equivalent_domain_all_app_pixels_no_tolerance_no_mask")
                .put("crossDomainComparison", "DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT")
                .put("captureArtifacts", artifacts).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("acceptedAttempt", if (accepted) attempts.length() else JSONObject.NULL)
                .put("retainedFailureAttempt", retained?.attempt ?: JSONObject.NULL)
            if (attempts.length() > 0) {
                val last = attempts.getJSONObject(attempts.length() - 1)
                proof.put("windowLayerMatch", last.opt("windowLayerMatch") ?: JSONObject.NULL)
                    .put("displayStable", last.opt("displayStable") ?: JSONObject.NULL)
            }
            if (file.exists()) proof.put("imageSha256", sha(file))
            error?.let { proof.put("failure", it) }
            proofFile.writeText(proof.toString(2))
            return proof
        }
        try {
            check(Build.VERSION.SDK_INT >= 34) { "Window-layer bridge requires public UiAutomation.takeScreenshot(Window), API 34" }
            while (SystemClock.elapsedRealtime() < deadline) {
                val attempt = JSONObject()
                attempts.put(attempt)
                val budget = JSONArray()
                attempt.put("budget", budget)
                fun remaining(stage: String): Long {
                    val now = SystemClock.elapsedRealtime()
                    val left = deadline - now
                    budget.put(JSONObject().put("stage", stage).put("elapsedMs", now - started).put("remainingMs", left))
                    check(left > 0) { "CAPTURE_DEADLINE_EXHAUSTED before $stage; no new request issued" }
                    return left
                }
                attempt.put("frame", committedFrame(activity, remaining("hardware_commit")))
                lateinit var source: Rect
                lateinit var screen: Rect
                lateinit var before: JSONObject
                fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
                fun geometry(): JSONObject {
                    val root = activity.sampleRoot
                    val windowLocation = IntArray(2); root.getLocationInWindow(windowLocation)
                    val screenLocation = IntArray(2); root.getLocationOnScreen(screenLocation)
                    val decor = activity.window.decorView
                    val decorWindow = IntArray(2); decor.getLocationInWindow(decorWindow)
                    val decorScreen = IntArray(2); decor.getLocationOnScreen(decorScreen)
                    val metrics = activity.windowManager.currentWindowMetrics
                    val insets = requireNotNull(root.rootWindowInsets) { "Missing actual root WindowInsets" }
                    fun corners(value: WindowInsets) = JSONArray((0..3).map { position ->
                        val corner = value.getRoundedCorner(position)
                        JSONObject().put("position", position).put("radiusPx", corner?.radius ?: JSONObject.NULL)
                            .put("centerPx", corner?.center?.let { JSONArray(listOf(it.x, it.y)) } ?: JSONObject.NULL)
                    })
                    val display = requireNotNull(activity.display)
                    val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                    return JSONObject().put("frameCallbackSequence", frameSequence)
                        .put("hasWindowFocus", activity.hasWindowFocus())
                        .put("rootLayoutRequested", root.isLayoutRequested).put("decorLayoutRequested", decor.isLayoutRequested)
                        .put("appLocationInWindowPx", JSONArray(windowLocation.toList()))
                        .put("appLocationOnScreenPx", JSONArray(screenLocation.toList()))
                        .put("appSizePx", JSONArray(listOf(root.width, root.height)))
                        .put("windowDecorSizePx", JSONArray(listOf(decor.width, decor.height)))
                        .put("decorLocationInWindowPx", JSONArray(decorWindow.toList()))
                        .put("decorLocationOnScreenPx", JSONArray(decorScreen.toList()))
                        .put("windowMetricsBoundsPx", rect(metrics.bounds))
                        .put("rootTransform", JSONArray(listOf(root.scaleX, root.scaleY, root.rotation, root.rotationX, root.rotationY)))
                        .put("decorTransform", JSONArray(listOf(decor.scaleX, decor.scaleY, decor.rotation, decor.rotationX, decor.rotationY)))
                        .put("rootRoundedCorners", corners(insets)).put("metricsRoundedCorners", corners(metrics.windowInsets))
                        .put("displayRoundedCorners", JSONArray((0..3).map { position ->
                            val corner = display.getRoundedCorner(position)
                            JSONObject().put("position", position).put("radiusPx", corner?.radius ?: JSONObject.NULL)
                                .put("centerPx", corner?.center?.let { JSONArray(listOf(it.x, it.y)) } ?: JSONObject.NULL)
                        })).put("roundedCornersAreApproximateNotMasks", true)
                        .put("systemBarsInsetsPx", JSONArray(listOf(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)))
                        .put("scrolls", JSONArray(nodes(root).filterIsInstance<ScrollView>().map {
                            JSONObject().put("semanticId", it.tag).put("scrollX", it.scrollX).put("scrollY", it.scrollY)
                        })).put("target", observe())
                }
                main {
                    check(activity.hasWindowFocus()) { "Specimen capture lost window focus" }
                    before = geometry()
                    val root = activity.sampleRoot
                    val decor = activity.window.decorView
                    val wl = before.getJSONArray("appLocationInWindowPx")
                    val sl = before.getJSONArray("appLocationOnScreenPx")
                    source = Rect(wl.getInt(0), wl.getInt(1), wl.getInt(0) + root.width, wl.getInt(1) + root.height)
                    screen = Rect(sl.getInt(0), sl.getInt(1), sl.getInt(0) + root.width, sl.getInt(1) + root.height)
                    attempt.put("windowSourceRectPx", rect(source)).put("displayAppRectPx", rect(screen)).put("geometryBefore", before)
                    val dw = before.getJSONArray("decorLocationInWindowPx")
                    val ds = before.getJSONArray("decorLocationOnScreenPx")
                    val metrics = activity.windowManager.currentWindowMetrics.bounds
                    check(dw.getInt(0) == 0 && dw.getInt(1) == 0 && metrics.left == ds.getInt(0) && metrics.top == ds.getInt(1) &&
                        metrics.width() == decor.width && metrics.height() == decor.height &&
                        screen.left - source.left == ds.getInt(0) && screen.top - source.top == ds.getInt(1)) {
                        "Unknown Window-layer/decor/metrics origin mapping"
                    }
                    check(listOf(root, decor).all { it.scaleX == 1f && it.scaleY == 1f && it.rotation == 0f && it.rotationX == 0f && it.rotationY == 0f }) {
                        "Scaled or rotated Window-layer mapping is unsupported"
                    }
                    check(source.left >= 0 && source.top >= 0 && source.right <= decor.width && source.bottom <= decor.height)
                    attempt.put("windowLayerMapping", "SDK34_measured_zero_surfaceInsets_unscaled_Window_local")
                }
                val images = linkedMapOf<String, Bitmap>()
                var appDisplay: Bitmap? = null
                var acceptedAttempt = false
                try {
                    fun copy(role: String): Bitmap {
                        val result = copyWindow(activity, source, remaining(role))
                        result.bitmap?.let { images[role] = it }
                        attempt.put("${role}Code", if (result.callbackCompleted) result.resultCode else JSONObject.NULL)
                            .put("${role}Callback", result.callbackCompleted).put("${role}SourceRectAfterSdk", rect(result.sourceRectAfterSdk))
                        check(result.callbackCompleted) { "PIXEL_COPY_CALLBACK_TIMEOUT $role; no Android result received within capture budget" }
                        check(result.resultCode == PixelCopy.SUCCESS) { "PixelCopy $role failed: ${result.resultCode}" }
                        // SDK34 offsets this input Rect by surfaceInsets. A fresh
                        // Rect prevents accumulated offsets; nonzero offsets FAIL.
                        check(result.sourceRectAfterSdk == source) { "Nonzero surfaceInsets: Window-layer buffer origin is unsupported" }
                        val bitmap = requireNotNull(result.bitmap)
                        attempt.put(role, bitmapDescription(bitmap)).put("${role}AppArgbSha256", pixelSha(bitmap))
                        return bitmap
                    }
                    fun capture(role: String, windowLayer: Boolean): Bitmap {
                        remaining(role)
                        val raw = requireNotNull(if (windowLayer) test.uiAutomation.takeScreenshot(activity.window) else test.uiAutomation.takeScreenshot()) {
                            "UiAutomation $role returned null"
                        }
                        images[role] = raw
                        attempt.put(role, bitmapDescription(raw))
                        if (windowLayer) {
                            val size = before.getJSONArray("windowDecorSizePx")
                            check(raw.width == size.getInt(0) && raw.height == size.getInt(1)) { "Unknown Window-layer bitmap origin/size; no resizing allowed" }
                        } else {
                            check(screen.left >= 0 && screen.top >= 0 && screen.right <= raw.width && screen.bottom <= raw.height)
                        }
                        return raw
                    }
                    fun <T> readable(raw: Bitmap, action: (Bitmap) -> T): T {
                        val software = requireNotNull(raw.copy(Bitmap.Config.ARGB_8888, false))
                        try { return action(software) } finally { software.recycle() }
                    }
                    val s1 = copy("surface-before")
                    val w1 = capture("layer-before", true)
                    var windowColors = s1.colorSpace != null && s1.colorSpace == w1.colorSpace
                    val w1Difference = readable(w1) { bitmap ->
                        attempt.put("layer-beforeAppArgbSha256", pixelSha(bitmap, source))
                        pixelDifferenceEvidence(s1, bitmap, source, screen)
                    }
                    val d1 = capture("display", false)
                    val crossDifference = readable(d1) { bitmap ->
                        attempt.put("displayAppArgbSha256", pixelSha(bitmap, screen))
                        appDisplay = Bitmap.createBitmap(bitmap, screen.left, screen.top, screen.width(), screen.height())
                        pixelDifferenceEvidence(s1, bitmap, screen, screen)
                    }
                    val d2 = capture("display-after", false)
                    val displayColors = d1.colorSpace != null && d1.colorSpace == d2.colorSpace
                    val displayDifference = readable(d2) { bitmap ->
                        attempt.put("display-afterAppArgbSha256", pixelSha(bitmap, screen))
                        pixelDifferenceEvidence(requireNotNull(appDisplay), bitmap, screen, screen)
                    }
                    appDisplay?.recycle(); appDisplay = null
                    val w2 = capture("layer-after", true)
                    windowColors = windowColors && s1.colorSpace == w2.colorSpace
                    val w2Difference = readable(w2) { bitmap ->
                        attempt.put("layer-afterAppArgbSha256", pixelSha(bitmap, source))
                        pixelDifferenceEvidence(s1, bitmap, source, screen)
                    }
                    val s2 = copy("surface-after")
                    windowColors = windowColors && s1.colorSpace == s2.colorSpace
                    val surfaceDifference = pixelDifferenceEvidence(s1, s2, Rect(0, 0, s2.width, s2.height), screen)
                    lateinit var after: JSONObject
                    main { after = geometry() }
                    val geometryStable = before.toString() == after.toString()
                    val windowReady = listOf(before, after).all {
                        it.getBoolean("hasWindowFocus") && !it.getBoolean("rootLayoutRequested") && !it.getBoolean("decorLayoutRequested")
                    }
                    val windowMatch = windowColors && listOf(w1Difference, w2Difference, surfaceDifference).all { it.getLong("count") == 0L }
                    val displayStable = displayColors && displayDifference.getLong("count") == 0L
                    val withinBudget = SystemClock.elapsedRealtime() <= deadline
                    val accepted = windowReady && geometryStable && windowMatch && displayStable && withinBudget
                    attempt.put("geometryAfter", after).put("geometryUnchanged", geometryStable)
                        .put("windowReadyAtBothEnds", windowReady).put("windowLayerMatch", windowMatch).put("displayStable", displayStable)
                        .put("windowDomainColorSpacesEqual", windowColors).put("displayDomainColorSpacesEqual", displayColors)
                        .put("S1_W1", w1Difference).put("S1_W2", w2Difference).put("S1_S2", surfaceDifference)
                        .put("D1_D2_app", displayDifference).put("S1_D1_diagnosticOnly", crossDifference)
                        .put("withinMatchingBudget", withinBudget).put("accepted", accepted)
                    budget.put(JSONObject().put("stage", "comparison_complete").put("elapsedMs", SystemClock.elapsedRealtime() - started)
                        .put("remainingMs", deadline - SystemClock.elapsedRealtime()))
                    if (accepted) {
                        acceptedAttempt = true
                        persist(Candidate(attempts.length(), images), false)
                        return CapturedFrame(before.getJSONObject("target"), writeProof(true))
                    }
                    retained?.recycle()
                    retained = Candidate(attempts.length(), images.toMap())
                    images.clear()
                    writeProof(false)
                } finally {
                    // Retain a first partial failure only if no complete candidate
                    // exists; a deadline-tail partial cannot overwrite a full set.
                    if (!acceptedAttempt && retained == null && images.isNotEmpty()) {
                        retained = Candidate(attempts.length(), images.toMap())
                        images.clear()
                    }
                    images.values.forEach { it.recycle() }
                    appDisplay?.recycle()
                }
            }
            error("Equivalent Window/display domains did not synchronize within 5000ms")
        } catch (failure: Throwable) {
            try { retained?.let { persist(it, true) } } catch (diagnosticFailure: Throwable) { failure.addSuppressed(diagnosticFailure) }
            writeProof(false, failure.stackTraceToString())
            throw failure
        } finally {
            retained?.recycle()
        }
    }
    private fun Bitmap.output(file: File) {
        file.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
    private fun auditTouchTargets(activity: StrictVisualSampleActivity, directory: File): JSONArray {
        lateinit var measurements: JSONArray
        var violations = emptyList<String>()
        main {
            val all = nodes(activity.sampleRoot)
            measurements = JSONArray()
            val issues = mutableListOf<String>()
            val navigationIds = mutableListOf<String>()
            val minimum = StrictVisualSampleTokens.dp(activity, 48f)
            for (view in all) {
                val registered = activity.actionViews.any { it === view }
                val id = view.tag?.toString() ?: if (registered) "unlabelled_registered_action" else continue
                val kind = StrictVisualSampleTouchPolicy.kind(id, registered)
                if (kind == StrictVisualSampleTouchPolicy.Kind.NAVIGATION) navigationIds.add(id)
                StrictVisualSampleTouchPolicy.violation(id, registered, view is ViewGroup,
                    view.width, view.height, minimum)?.let(issues::add)
                val location = IntArray(2); view.getLocationOnScreen(location)
                val visibleBounds = Rect()
                val visible = view.getGlobalVisibleRect(visibleBounds)
                measurements.put(JSONObject().put("semanticId", id).put("targetKind", kind.name)
                    .put("registeredActionIdentity", registered).put("container", view is ViewGroup)
                    .put("touchTarget", kind != StrictVisualSampleTouchPolicy.Kind.NONE)
                    .put("enabled", view.isEnabled).put("clickable", view.isClickable)
                    .put("sizePx", JSONArray(listOf(view.width, view.height)))
                    .put("rectPx", JSONArray(listOf(location[0], location[1], location[0] + view.width, location[1] + view.height)))
                    .put("visible", visible).put("visibleRectPx", JSONArray(listOf(
                        visibleBounds.left, visibleBounds.top, visibleBounds.right, visibleBounds.bottom))))
            }
            if (navigationIds.size != 6 || navigationIds.toSet().size != 6 ||
                !navigationIds.containsAll(listOf("sample/nav/selected", "sample/nav/scale", "sample/nav/settings"))) {
                issues.add("Expected six exact navigation touch containers: $navigationIds")
            }
            if (activity.actionViews.any { action -> all.none { it === action } }) {
                issues.add("Registered specimen action is absent from the rendered tree")
            }
            violations = issues.toList()
            val config = activity.resources.configuration
            val geometry = JSONObject().put("validationPhase", "before_touch_assertions")
                .put("fontScale", config.fontScale).put("densityDpi", config.densityDpi)
                .put("appSizePx", JSONArray(listOf(activity.sampleRoot.width, activity.sampleRoot.height)))
                .put("themeFontDiagnostics", activity.themeFontDiagnostics)
                .put("minimumTouchPx", minimum).put("nodes", measurements).put("issues", JSONArray(issues))
            // Persist the observed tree and actual text/Drawable evidence before any
            // touch-size assertion. A genuine undersized container still fails below.
            File(directory, "touch-geometry.json").writeText(geometry.toString(2))
            TaskTypographyEvidence.capture(activity, directory)
            val measured = JSONObject(File(directory, "StrictVisualSampleActivity-typography.json").readText())
                .put("validationPhase", "before_touch_assertions_not_complete_visual_validation")
                .put("touchTargets", measurements).put("touchTargetIssues", JSONArray(issues))
                .put("gates", JSONObject().put("G1", if (issues.isEmpty()) "BASELINE_PENDING" else "FAIL")
                    .put("G2", "BASELINE_PENDING").put("G3", "BASELINE_PENDING").put("G4", "BASELINE_PENDING"))
            File(directory, "visual.json").writeText(measured.toString(2))
        }
        check(violations.isEmpty()) { violations.joinToString("; ") }
        return measurements
    }
    /** Each observation starts from the original viewport; screenshots are after actual frame commits. */
    private fun auditReachability(activity: StrictVisualSampleActivity, directory: File, scale: Float): JSONArray {
        val result = JSONArray()
        lateinit var targets: List<View>
        main {
            val all = nodes(activity.sampleRoot)
            val ids = when (activity.fixture.id) {
                StrictVisualSampleFixtures.ids[0] -> listOf("sample/preparation/machineReady", "sample/preparation/scaleReady", "sample/preparation/confirmation")
                StrictVisualSampleFixtures.ids[1] -> listOf("sample/share/table/header") + (1..4).map { "sample/share/table/row$it" } + "sample/share/privacy"
                else -> (1..2).map { "sample/inventory/ledgerRow$it" }
            }
            targets = ids.map { id -> all.single { it.tag == id } }
        }
        fun observation(view: View): JSONObject {
            val visible = Rect(); val origin = IntArray(2); view.getLocationOnScreen(origin)
            val shown = view.getGlobalVisibleRect(visible)
            val complete = shown && visible == Rect(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)
            val lines = nodes(view).filterIsInstance<TextView>().map { text ->
                val box = Rect(); val local = IntArray(2); text.getLocationOnScreen(local)
                val full = text.getGlobalVisibleRect(box) && box == Rect(local[0], local[1], local[0] + text.width, local[1] + text.height)
                JSONObject().put("semanticId", text.tag).put("fullViewVisible", full)
                    .put("lineCount", text.layout?.lineCount ?: 0)
                    .put("layoutPresent", text.layout != null)
                    .put("ellipsisCount", text.layout?.let { layout -> (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) } } ?: JSONObject.NULL)
                    .put("layoutHeightPx", text.layout?.height ?: JSONObject.NULL)
                    .put("contentHeightPx", text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            }
            return JSONObject().put("complete", complete).put("sizePx", JSONArray(listOf(view.width, view.height)))
                .put("rectPx", JSONArray(listOf(origin[0], origin[1], origin[0] + view.width, origin[1] + view.height)))
                .put("visibleRectPx", JSONArray(listOf(visible.left, visible.top, visible.right, visible.bottom)))
                .put("textLineBoxes", JSONArray(lines))
        }
        fun wholeTarget(observation: JSONObject): Boolean = observation.getBoolean("complete") &&
            observation.getJSONArray("textLineBoxes").let { lines ->
                (0 until lines.length()).all { i -> val line = lines.getJSONObject(i)
                    line.getBoolean("fullViewVisible") && line.getBoolean("layoutPresent") && line.getInt("lineCount") > 0 &&
                        line.getInt("ellipsisCount") == 0 && line.getInt("layoutHeightPx") <= line.getInt("contentHeightPx") }
            }
        targets.forEachIndexed { index, view ->
            lateinit var scrolls: List<Pair<ScrollView, Pair<Int, Int>>>
            lateinit var before: JSONObject
            lateinit var after: JSONObject
            lateinit var frameProof: JSONObject
            var restored = false
            main {
                scrolls = generateSequence(view.parent) { it.parent }.filterIsInstance<ScrollView>()
                    .map { it to (it.scrollX to it.scrollY) }.toList()
                before = observation(view)
            }
            val image = "after-scroll-${index + 1}.png"
            try {
                main { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
                val captured = screenshot(activity, File(directory, image)) { observation(view) }
                after = captured.target
                frameProof = captured.proof
            } finally {
                main { scrolls.forEach { (scroll, position) -> scroll.scrollTo(position.first, position.second) } }
                settled(activity)
                main { restored = scrolls.all { (scroll, position) -> scroll.scrollX == position.first && scroll.scrollY == position.second } }
            }
            val initialRequired = view.tag == "sample/preparation/confirmation" || scale == 1f &&
                (activity.fixture.id != StrictVisualSampleFixtures.ids[1] || view.tag != "sample/share/privacy")
            val complete = wholeTarget(after)
            val initialComplete = wholeTarget(before)
            val pass = complete && restored && (!initialRequired || initialComplete)
            result.put(JSONObject().put("semanticId", view.tag).put("status", if (pass) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
                .put("initialRequired", initialRequired).put("initialCompleteIncludingText", initialComplete).put("initial", before).put("afterScroll", after)
                .put("initialPng", "window.png").put("afterScrollPng", image).put("scrollRestored", restored)
                .put("frameSync", frameProof)
                .put("reason", "whole_target_and_actual_text_line_boxes_must_be_visible; no_ellipsis_or_font_reduction"))
        }
        File(directory, "scroll-reachability.json").writeText(result.toString(2))
        return result
    }
    private fun auditBaselines(activity: StrictVisualSampleActivity): JSONArray {
        val all = nodes(activity.sampleRoot)
        val pairs = when (activity.fixture.id) {
            StrictVisualSampleFixtures.ids[0] -> (0..2).map { "sample/preparation/summary/$it/value" to "sample/preparation/summary/$it/unit" } +
                ("sample/preparation/doseValue/number" to "sample/preparation/doseValue/unit")
            StrictVisualSampleFixtures.ids[1] -> (0..1).map { "sample/share/summary/$it/value" to "sample/share/summary/$it/unit" }
            else -> listOf("sample/inventory/weight/value" to "sample/inventory/weight/unit")
        }
        return JSONArray(pairs.map { (number, unit) ->
            fun baseline(id: String): Int { val view = all.single { it.tag == id } as TextView
                val point = IntArray(2); view.getLocationOnScreen(point); return point[1] + view.baseline }
            val numberY = baseline(number); val unitY = baseline(unit)
            JSONObject().put("semanticId", number).put("unitSemanticId", unit)
                .put("numberBaselineScreenPx", numberY).put("unitBaselineScreenPx", unitY)
                .put("baselineDeltaPx", unitY - numberY).put("requiredDeltaPx", 0)
                .put("status", if (numberY == unitY) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
        })
    }
    private fun auditVisibleGrid(chart: StrictVisualSampleChart, targets: Int, share: Boolean): JSONObject {
        val items = (0 until chart.evidence.length()).map { chart.evidence.getJSONObject(it) }
        val protections = items.filter { it.getString("operation") == "targetLabelProtection" }
        val plans = items.filter { it.getString("operation") == "gridSegmentPlan" }
        val lines = items.filter { it.getString("operation") == "line" }
        val grid = lines.filter { it.getString("role") == "grid" }
        val labels = items.filter { it.getString("operation") == "text" && it.getString("role") == "targetLabel" }
        val issues = JSONArray()
        fun expect(condition: Boolean, detail: String) { if (!condition) issues.put(detail) }
        fun values(array: JSONArray) = (0 until array.length()).map { array.getDouble(it) }
        val expectedIndices = (0 until targets).toSet()
        expect(protections.size == targets && protections.map { it.getInt("targetIndex") }.toSet() == expectedIndices,
            "Missing or duplicate target-label protections")
        val padding = 2f * chart.resources.displayMetrics.density
        for (protection in protections) {
            val index = protection.getInt("targetIndex")
            val glyph = values(protection.getJSONArray("glyphBoundsPx"))
            val box = values(protection.getJSONArray("protectedBoundsPx"))
            val label = labels.singleOrNull { it.getInt("targetIndex") == index }
            expect(label != null && values(label.getJSONArray("glyphBoundsPx")) == glyph,
                "Protection $index differs from its actual target-label Paint glyph box")
            val expected = listOf((glyph[0].toFloat() - padding).toDouble(), (glyph[1].toFloat() - padding).toDouble(),
                (glyph[2].toFloat() + padding).toDouble(), (glyph[3].toFloat() + padding).toDouble())
            expect(protection.getInt("paddingDp") == 2 && protection.getDouble("paddingPx") == padding.toDouble() && box == expected,
                "Protection $index is not exactly actual glyph ink plus 2dp using the recorded Float conversion")
        }
        val expectedGridIds = setOf("horizontal-0", "horizontal-6", "horizontal-12") + (1 until targets).map { "stage-$it" }
        expect(plans.size == expectedGridIds.size && plans.map { it.getString("gridId") }.toSet() == expectedGridIds,
            "Missing or duplicate horizontal/stage grid plans")
        expect(grid.all { it.getString("gridId") in expectedGridIds }, "Drawn grid has no matching plan")
        val intersections = JSONArray()
        for (plan in plans) {
            val id = plan.getString("gridId")
            val original = values(plan.getJSONArray("originalEndpointsPx"))
            val visible = plan.getJSONArray("visibleSegmentsPx")
            val drawn = grid.filter { it.getString("gridId") == id }
            val width = plan.getDouble("strokeWidthPx")
            expect(width > 0 && plan.getString("strokeCap") == "BUTT" && plan.getDouble("strokeGuardPx") == width / 2,
                "$id must record positive stroke, BUTT cap and exact half-stroke guard")
            expect(visible.length() == drawn.size && (0 until minOf(visible.length(), drawn.size)).all { index ->
                values(visible.getJSONArray(index)) == values(drawn[index].getJSONArray("endpointsPx")) &&
                    drawn[index].getJSONObject("paint").getDouble("strokeWidthPx") == width &&
                    drawn[index].getJSONObject("paint").getString("strokeCap") == "BUTT"
            }, "$id visible plan does not match every actual drawLine segment")
            // Only actual visible drawLine segments are checked. The original full
            // line is retained for audit and must not be mistaken for rendered ink.
            for (line in drawn) {
                val segment = values(line.getJSONArray("endpointsPx"))
                val horizontal = segment[1] == segment[3] && segment[0] < segment[2]
                val vertical = segment[0] == segment[2] && segment[1] < segment[3]
                expect(horizontal || vertical, "$id has a non-axis-aligned or empty visible segment")
                expect(segment[0] >= minOf(original[0], original[2]) && segment[2] <= maxOf(original[0], original[2]) &&
                    segment[1] >= minOf(original[1], original[3]) && segment[3] <= maxOf(original[1], original[3]),
                    "$id visible segment extends outside its original grid line")
                // BUTT caps do not extend along the segment; half-stroke extends
                // perpendicular to it. No tolerance or theoretical full-line test.
                val ink = if (horizontal) listOf(segment[0], segment[1] - width / 2, segment[2], segment[3] + width / 2)
                    else listOf(segment[0] - width / 2, segment[1], segment[2] + width / 2, segment[3])
                for (protection in protections) {
                    val box = values(protection.getJSONArray("protectedBoundsPx"))
                    if (maxOf(ink[0], box[0]) < minOf(ink[2], box[2]) && maxOf(ink[1], box[1]) < minOf(ink[3], box[3])) {
                        intersections.put(JSONObject().put("gridId", id).put("targetIndex", protection.getInt("targetIndex"))
                            .put("actualEndpointsPx", line.getJSONArray("endpointsPx")).put("strokeInkBoundsPx", JSONArray(ink))
                            .put("protectedBoundsPx", protection.getJSONArray("protectedBoundsPx")))
                    }
                }
            }
        }
        expect(intersections.length() == 0, "Actual visible grid strokes intersect protected target-label ink")
        expect(lines.count { it.getString("role") == "axis" } == 2, "Grid exclusion removed an axis")
        expect(lines.count { it.getString("role") == "data" } == targets && items.count { it.getString("operation") == "targetPoint" } == targets,
            "Grid exclusion removed target data strokes/points")
        expect(items.count { it.getString("operation") == "stageBackground" } == if (share) targets else 0,
            "Grid exclusion changed stage backgrounds")
        return JSONObject().put("semanticId", chart.tag).put("check", "actualVisibleGridAvoidsTargetInk")
            .put("status", if (issues.length() == 0) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
            .put("issues", issues).put("intersections", intersections).put("protectionCount", protections.size)
            .put("gridPlanCount", plans.size).put("actualVisibleGridSegmentCount", grid.size)
            .put("originalLinesAuditOnly", true).put("excludedRoles", JSONArray(listOf("grid")))
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
                // The original and every scrolled whole-window image use the same
                // submitted-surface/display contract; all original PNGs are retained.
                val initialFrame = screenshot(activity, File(directory, "window.png"))
                val touchTargets = auditTouchTargets(activity, directory)
                val anchors = JSONArray()
                val canvas = JSONArray()
                val layoutIssues = auditReachability(activity, directory, expectedScale)
                var baselines = JSONArray()
                lateinit var window: JSONObject
                lateinit var tokens: JSONObject
                lateinit var roles: JSONArray
                main {
                    val config = activity.resources.configuration
                    check(config.locales[0].language == "zh" && config.fontScale == expectedScale)
                    check(activity.resources.displayMetrics.densityDpi == 360)
                    check(activity.palette.dark == (expectedTheme == "dark"))
                    val all = nodes(activity.sampleRoot)
                    baselines = auditBaselines(activity)
                    for (i in 0 until baselines.length()) layoutIssues.put(baselines.getJSONObject(i))
                    check(all.none { it.hasOnClickListeners() }) { "Specimen contains callback-bearing actions" }
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
                            val layout = view.layout
                            val contentWidth = view.width - view.compoundPaddingLeft - view.compoundPaddingRight
                            val contentHeight = view.height - view.compoundPaddingTop - view.compoundPaddingBottom
                            val ellipsisCount = (0 until layout.lineCount).sumOf { layout.getEllipsisCount(it) }
                            // TextView can increase a non-ellipsizing BoringLayout's width
                            // without updating its cached ellipsizedWidth (Android 34).
                            // Validate the actual layout; the ellipsis width is relevant
                            // only when the TextView is configured to ellipsize.
                            val widthMatches = layout.width == contentWidth &&
                                (view.ellipsize == null || layout.ellipsizedWidth == contentWidth)
                            val complete = widthMatches && ellipsisCount == 0 && layout.height <= contentHeight
                            // Retain hard checks as FAIL records so all profiles
                            // and their exact measurements survive instead of aborting at one node.
                            layoutIssues.put(JSONObject().put("semanticId", tag).put("check", "textLayoutIntegrity")
                                .put("status", if (complete) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
                                .put("layoutClass", layout.javaClass.name).put("layoutWidthPx", layout.width)
                                .put("ellipsizedWidthPx", layout.ellipsizedWidth).put("contentWidthPx", contentWidth)
                                .put("ellipsizeMode", view.ellipsize?.name ?: JSONObject.NULL)
                                .put("widthMatches", widthMatches).put("ellipsisCount", ellipsisCount)
                                .put("layoutHeightPx", layout.height).put("contentHeightPx", contentHeight))
                        }
                    }
                    activity.charts.forEach { chart ->
                        check(chart.evidence.length() > 0)
                        layoutIssues.put(auditVisibleGrid(chart, activity.fixture.targetsTenthsBar.size,
                            fixtureId == StrictVisualSampleFixtures.ids[1]))
                        val labels = (0 until chart.evidence.length()).map { chart.evidence.getJSONObject(it) }
                            .filter { it.getString("operation") == "text" }
                        val collisions = JSONArray()
                        labels.forEachIndexed { firstIndex, first -> labels.drop(firstIndex + 1).forEach { second ->
                            val a = first.getJSONArray("glyphBoundsPx"); val b = second.getJSONArray("glyphBoundsPx")
                            if (maxOf(a.getDouble(0), b.getDouble(0)) < minOf(a.getDouble(2), b.getDouble(2)) &&
                                maxOf(a.getDouble(1), b.getDouble(1)) < minOf(a.getDouble(3), b.getDouble(3))) {
                                collisions.put(JSONObject().put("first", first.getString("text")).put("second", second.getString("text"))
                                    .put("firstInkRectPx", a).put("secondInkRectPx", b))
                            }
                        } }
                        val bands = (0 until chart.evidence.length()).map { chart.evidence.getJSONObject(it) }
                            .single { it.getString("operation") == "labelBands" }
                        val gap = bands.getDouble("requiredGapPx")
                        val pass = collisions.length() == 0 && chart.height >= bands.getInt("minimumChartHeightPx") &&
                            bands.getDouble("titleToHighestTickGapPx") >= gap && bands.getDouble("plotToStageInkGapPx") >= gap &&
                            labels.filter { it.getString("role") == "axisTick" }.map { it.getString("text") }.toSet() == setOf("0", "6", "12")
                        layoutIssues.put(JSONObject().put("semanticId", chart.tag).put("status", if (pass) "PASS_LAYOUT_ONLY_NOT_VISUAL_ACCEPTANCE" else "FAIL")
                            .put("reason", "actual_Paint_glyph_boxes_nonintersection_and_8dp_label_bands_three_ticks_minimum160dp")
                            .put("glyphCollisions", collisions).put("labelBands", bands))
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
                }
                val measured = JSONObject(File(directory, "StrictVisualSampleActivity-typography.json").readText())
                val layoutFailed = (0 until layoutIssues.length()).any { layoutIssues.getJSONObject(it).getString("status") == "FAIL" }
                measured.put("candidateRoles", roles).put("canvas", canvas).put("numericUnitBaselines", baselines)
                    .put("validationPhase", "complete_normal_capture").put("touchTargets", touchTargets)
                    .put("touchTargetIssues", JSONArray())
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
                    .put("ciJob", arguments.getString("ciJob")).put("stableFrame", "hardware_commit_Window_layer_match_and_independent_display_stability")
                    .put("initialFrameSyncFile", "window-frame-sync.json").put("initialFrameSyncAccepted", initialFrame.proof.getBoolean("accepted"))
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
