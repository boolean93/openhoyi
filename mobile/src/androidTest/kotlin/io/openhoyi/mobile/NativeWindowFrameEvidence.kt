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

/** Protocol 2 extracted verbatim from the accepted specimen capture; only Activity/root are generalized.
 * It captures real windows, not a specimen renderer. No protocol/budget/cancellation relaxation. */
internal class NativeWindowFrameEvidence(private val test: Instrumentation) {
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
    private fun nodes(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { nodes(view.getChildAt(it)) } else emptyList()
    fun committedFrame(root: View, timeoutMs: Long = 5000): JSONObject {
        test.waitForIdleSync()
        val frames = CountDownLatch(1)
        lateinit var commit: JSONObject
        val callback = Runnable {
            frameSequence++
            commit = JSONObject().put("callbackSequence", frameSequence).put("committedAtElapsedNanos", SystemClock.elapsedRealtimeNanos())
            frames.countDown()
        }
        main {
            check(root.isAttachedToWindow && root.isHardwareAccelerated) { "Specimen requires a hardware-rendered attached root" }
            root.viewTreeObserver.registerFrameCommitCallback(callback)
            // Animation callbacks and queue-idle are not render submissions. Force a
            // real root draw after the scroll mutation, then await its commit callback.
            root.invalidate()
        }
        if (!frames.await(timeoutMs.coerceAtLeast(1), TimeUnit.MILLISECONDS)) {
            main { root.viewTreeObserver.unregisterFrameCommitCallback(callback) }
            error("Specimen hardware frame was not committed")
        }
        return commit
    }

    private data class WindowCopy(val bitmap: Bitmap?, val resultCode: Int, val callbackCompleted: Boolean, val sourceRectAfterSdk: Rect)
    data class CapturedFrame(val target: JSONObject, val proof: JSONObject)

    private fun copyWindow(activity: android.app.Activity, source: Rect, timeoutMs: Long): WindowCopy {
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
    fun screenshot(activity: android.app.Activity, root: View, file: File,
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
                attempt.put("frame", committedFrame(root, remaining("hardware_commit")))
                lateinit var source: Rect
                lateinit var screen: Rect
                lateinit var before: JSONObject
                fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
                fun geometry(): JSONObject {
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
}
