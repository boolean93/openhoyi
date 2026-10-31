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
import android.view.Window
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
    private enum class OriginMode { DEFAULT, MEASURED_WINDOW_BUFFER_TRANSLATION, MEASURED_SHORT_ACTIVITY_WINDOW }
    private object Timing {
        const val GEOMETRY_BEFORE=0; const val GEOMETRY_AFTER=1
        const val PIXEL_COPY_S1=2; const val WINDOW_LAYER_W1=3; const val DISPLAY_D1=4
        const val DISPLAY_D2=5; const val WINDOW_LAYER_W2=6; const val PIXEL_COPY_S2=7
        const val RAW_COPY_W1=8; const val RAW_COPY_D1=9; const val RAW_COPY_D2=10; const val RAW_COPY_W2=11
        const val SHA_S1=12; const val SHA_W1=13; const val SHA_D1=14; const val SHA_D2=15
        const val SHA_W2=16; const val SHA_S2=17
        const val DIFF_S1_W1=18; const val DIFF_S1_D1=19; const val DIFF_D1_D2=20
        const val DIFF_S1_W2=21; const val DIFF_S1_S2=22
        const val APP_DISPLAY_CROP=23; const val GEOMETRY_JSON_COMPARE=24
        val names=arrayOf("geometry_before", "geometry_after", "pixel_copy_S1", "window_layer_W1", "display_D1",
            "display_D2", "window_layer_W2", "pixel_copy_S2", "raw_copy_W1", "raw_copy_D1", "raw_copy_D2",
            "raw_copy_W2", "pixel_sha_S1", "pixel_sha_W1", "pixel_sha_D1", "pixel_sha_D2", "pixel_sha_W2",
            "pixel_sha_S2", "pixel_diff_S1_W1", "pixel_diff_S1_D1_diagnostic", "pixel_diff_D1_D2",
            "pixel_diff_S1_W2", "pixel_diff_S1_S2", "app_display_crop", "geometry_json_string_comparison")
    }
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

    private fun copyWindow(window: Window, source: Rect, timeoutMs: Long): WindowCopy {
        val bitmap = Bitmap.createBitmap(source.width(), source.height(), Bitmap.Config.ARGB_8888)
        val copied = CountDownLatch(1)
        val code = AtomicInteger(-1)
        val requested = Rect(source)
        main {
            PixelCopy.request(window, requested, bitmap, { result ->
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
        observe: () -> JSONObject = { JSONObject() }): CapturedFrame =
        screenshotWindow(activity.window, root, file, observe, activity, OriginMode.DEFAULT)

    fun screenshotMeasuredShortActivity(activity: android.app.Activity, root: View, file: File,
        observe: () -> JSONObject): CapturedFrame =
        screenshotWindow(activity.window, root, file, observe, activity, OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW)

    /** Explicit target only. A modal does not borrow its owner's focus or screen origin. */
    fun screenshot(window: Window, root: View, file: File,
        observe: () -> JSONObject = { JSONObject() }): CapturedFrame =
        screenshotWindow(window, root, file, observe, null, OriginMode.DEFAULT)

    fun screenshotMeasuredWindowBufferTranslation(window: Window, root: View, file: File,
        observe: () -> JSONObject): CapturedFrame =
        screenshotWindow(window, root, file, observe, null, OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION)

    /** Both public entries share this exact protocol, deadline, comparisons and failure retention. */
    private fun screenshotWindow(window: Window, root: View, file: File, observe: () -> JSONObject,
        activity: android.app.Activity?, originMode: OriginMode): CapturedFrame {
        check((originMode != OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW || activity != null) &&
            (originMode != OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION || activity == null))
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 5000
        val attempts = JSONArray()
        val artifacts = JSONArray()
        val captureTimings = mutableListOf<LongArray>()
        val proofTimings = mutableListOf<LongArray>()
        var currentTiming: LongArray? = null
        fun <T> timed(slot: Int, action: () -> T): T {
            val timing = currentTiming
            if (timing == null) return action()
            timing[slot * 2] = SystemClock.elapsedRealtimeNanos()
            try { return action() } finally { timing[slot * 2 + 1] = SystemClock.elapsedRealtimeNanos() }
        }
        data class Candidate(val attempt: Int, val images: Map<String, Bitmap>) {
            fun recycle() { images.values.forEach { it.recycle() } }
        }
        var retained: Candidate? = null
        val mappingMode = when (originMode) {
            OriginMode.DEFAULT -> if (activity == null) "EXPLICIT_WINDOW_MEASURED_DECOR" else "ACTIVITY_STRICT_METRICS_DECOR"
            else -> originMode.name
        }
        var targetDecor: View? = null
        var targetToken: android.os.IBinder? = null
        var targetDisplayId: Int? = null
        var mappingDiagnosticReason: String? = null
        fun imageName(role: String) = when (role) {
            "display" -> file.name
            else -> "${file.nameWithoutExtension}-$role.png"
        }
        fun persist(candidate: Candidate, failure: Boolean) {
            candidate.images.forEach { (role, bitmap) ->
                // On success retain both unmodified full displays. All six raw
                // inputs are retained on failure, outside the matching budget.
                if (failure || role == "display" || role == "display-after" ||
                    (originMode == OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION && role in setOf("layer-before", "layer-after"))) {
                    val output = File(file.parentFile, imageName(role))
                    bitmap.output(output)
                    artifacts.put(JSONObject().put("attempt", candidate.attempt).put("role", role)
                        .put("file", output.name).put("fileSha256", sha(output)).put("bitmap", bitmapDescription(bitmap)))
                }
            }
        }
        val proofFile = File(file.parentFile, "${file.nameWithoutExtension}-frame-sync.json")
        val timingFile = File(file.parentFile, "${file.nameWithoutExtension}-frame-timings-diagnostic.json")
        fun writeProof(accepted: Boolean, error: String? = null): JSONObject {
            val proofTiming = LongArray(6)
            proofTimings.add(proofTiming)
            proofTiming[0] = SystemClock.elapsedRealtimeNanos()
            val proof = JSONObject().put("protocolVersion", 2)
                .put("protocol", "commit_S1_WindowLayer1_Display1_Display2_WindowLayer2_S2")
                .put("accepted", accepted).put("timeoutMs", 5000).put("attempts", attempts)
                .put("captureWindowKind", if (activity == null) "EXPLICIT_WINDOW" else "ACTIVITY")
                .put("windowIdentity", System.identityHashCode(window)).put("rootIdentity", System.identityHashCode(root))
                .put("decorIdentity", targetDecor?.let(System::identityHashCode) ?: JSONObject.NULL)
                .put("windowTokenIdentity", targetToken?.let(System::identityHashCode) ?: JSONObject.NULL)
                .put("displayId", targetDisplayId ?: JSONObject.NULL).put("mappingMode", mappingMode)
                .put("acceptedTargetScope", "only_measured_root_screen_rect; owner_background_not_certified")
                .put("status", if (accepted) "MATCHED_WINDOW_LAYER_AND_STABLE_COMPOSITE_NOT_VISUAL_ACCEPTANCE" else "FAIL")
                .put("image", file.name).put("secondDisplayImage", imageName("display-after"))
                .put("wholeDisplayUiAutomation", true).put("displayComparisonRegion", "entire_measured_app_rect")
                .put("pixelComparison", "each_equivalent_domain_all_app_pixels_no_tolerance_no_mask")
                .put("crossDomainComparison", "DIAGNOSTIC_ONLY_PIPELINES_NOT_EQUIVALENT")
                .put("captureArtifacts", artifacts).put("elapsedMs", SystemClock.elapsedRealtime() - started)
                .put("timingDiagnosticFile", timingFile.name)
                .put("acceptedAttempt", if (accepted) attempts.length() else JSONObject.NULL)
                .put("retainedFailureAttempt", retained?.attempt ?: JSONObject.NULL)
            if (attempts.length() > 0) {
                val last = attempts.getJSONObject(attempts.length() - 1)
                proof.put("windowLayerMatch", last.opt("windowLayerMatch") ?: JSONObject.NULL)
                    .put("displayStable", last.opt("displayStable") ?: JSONObject.NULL)
            }
            if (file.exists()) proof.put("imageSha256", sha(file))
            error?.let { proof.put("failure", it) }
            proofTiming[1] = SystemClock.elapsedRealtimeNanos()
            proofTiming[2] = SystemClock.elapsedRealtimeNanos()
            try { WindowProofStreamingWriter.write(proof, proofFile) }
            finally { proofTiming[5] = SystemClock.elapsedRealtimeNanos() }
            return proof
        }
        try {
            check(Build.VERSION.SDK_INT >= 34) { "Window-layer bridge requires public UiAutomation.takeScreenshot(Window), API 34" }
            while (SystemClock.elapsedRealtime() < deadline) {
                val attempt = JSONObject()
                attempts.put(attempt)
                currentTiming = LongArray(Timing.names.size * 2).also { captureTimings.add(it) }
                var measuredTranslation: android.graphics.Point? = null
                var measuredLayerSize: android.graphics.Point? = null
                val budget = JSONArray()
                attempt.put("budget", budget)
                fun remaining(stage: String): Long {
                    val now = SystemClock.elapsedRealtime()
                    val left = deadline - now
                    budget.put(JSONObject().put("stage", stage).put("elapsedMs", now - started).put("remainingMs", left))
                    check(left > 0) { "CAPTURE_DEADLINE_EXHAUSTED before $stage; no new request issued" }
                    return left
                }
                fun validateExplicitWindow() {
                        val decor=window.decorView
                        if(targetDecor==null) {targetDecor=decor;targetToken=decor.windowToken;targetDisplayId=decor.display?.displayId}
                        check(targetDecor===decor && targetToken!=null && decor.windowToken===targetToken) { "Explicit Window decor/token identity changed" }
                        check(root.rootView===decor && root.windowToken===targetToken && root.display?.displayId==targetDisplayId &&
                            decor.display?.displayId==targetDisplayId && targetDisplayId==android.view.Display.DEFAULT_DISPLAY) { "Wrong Window/root/token/display owner" }
                        var current:View?=root
                        while(current!=null) {
                            val view=current
                            check(view.isAttachedToWindow && view.isHardwareAccelerated && view.isLaidOut && view.width>0 && view.height>0 &&
                                view.hasWindowFocus() && !view.isLayoutRequested && view.windowToken===targetToken) { "Explicit Window target/ancestor is not a ready focused attached surface" }
                            check(view.matrix.isIdentity && view.scaleX==1f && view.scaleY==1f && view.rotation==0f && view.rotationX==0f && view.rotationY==0f &&
                                view.translationX==0f && view.translationY==0f && view.translationZ==0f) { "Explicit Window ancestor transform is unsupported" }
                            if(view===decor)break
                            current=view.parent as? View
                        }
                        check(current===decor) { "Explicit Window root ancestor chain did not reach target decor" }
                }
                if(activity==null || originMode==OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW) main {validateExplicitWindow()}
                attempt.put("frame", committedFrame(root, remaining("hardware_commit")))
                lateinit var source: Rect
                lateinit var screen: Rect
                lateinit var before: JSONObject
                fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
                fun geometry(): JSONObject {
                            val windowLocation = IntArray(2); root.getLocationInWindow(windowLocation)
                    val screenLocation = IntArray(2); root.getLocationOnScreen(screenLocation)
                    val decor = window.decorView
                    val decorWindow = IntArray(2); decor.getLocationInWindow(decorWindow)
                    val decorScreen = IntArray(2); decor.getLocationOnScreen(decorScreen)
                    val metrics = if (activity == null) requireNotNull(window.windowManager).currentWindowMetrics else activity.windowManager.currentWindowMetrics
                    val insets = requireNotNull(root.rootWindowInsets) { "Missing actual root WindowInsets" }
                    fun corners(value: WindowInsets) = JSONArray((0..3).map { position ->
                        val corner = value.getRoundedCorner(position)
                        JSONObject().put("position", position).put("radiusPx", corner?.radius ?: JSONObject.NULL)
                            .put("centerPx", corner?.center?.let { JSONArray(listOf(it.x, it.y)) } ?: JSONObject.NULL)
                    })
                    val display = requireNotNull(if (activity == null) root.display else activity.display)
                    val systemBars = insets.getInsets(WindowInsets.Type.systemBars())
                    return JSONObject().put("frameCallbackSequence", frameSequence)
                        .put("hasWindowFocus", if (activity == null) decor.hasWindowFocus() else activity.hasWindowFocus())
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
                        .apply { if (activity == null || originMode == OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW) {
                            val attributes=window.attributes
                            val displaySize=android.graphics.Point().also(display::getRealSize)
                            put("windowIdentity",System.identityHashCode(window)).put("rootIdentity",System.identityHashCode(root))
                                .put("decorIdentity",System.identityHashCode(decor)).put("rootViewIdentity",System.identityHashCode(root.rootView))
                                .put("rootTokenIdentity",root.windowToken?.let(System::identityHashCode) ?: JSONObject.NULL)
                                .put("decorTokenIdentity",decor.windowToken?.let(System::identityHashCode) ?: JSONObject.NULL)
                                .put("displayId",display.displayId).put("displaySizePx",JSONArray(listOf(displaySize.x,displaySize.y)))
                                .put("mappingMode",mappingMode).put("metricsRole","diagnostic_host_container_not_assumed_dialog_bounds")
                                .put("windowAttributes",JSONObject().put("type",attributes.type).put("flags",attributes.flags)
                                    .put("format",attributes.format).put("width",attributes.width).put("height",attributes.height)
                                    .put("x",attributes.x).put("y",attributes.y).put("gravity",attributes.gravity)
                                    .put("alpha",attributes.alpha).put("dimAmount",attributes.dimAmount).put("softInputMode",attributes.softInputMode)
                                    .put("tokenIdentity",attributes.token?.let(System::identityHashCode) ?: JSONObject.NULL))
                                .put("actualWindowInsets",JSONObject().put("imeVisible",insets.isVisible(WindowInsets.Type.ime()))
                                    .put("imeInsetsPx",insets.getInsets(WindowInsets.Type.ime()).let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) })
                                    .put("systemBarsVisible",insets.isVisible(WindowInsets.Type.systemBars()))
                                    .put("displayCutoutInsetsPx",insets.getInsets(WindowInsets.Type.displayCutout()).let { JSONArray(listOf(it.left,it.top,it.right,it.bottom)) }))
                            val ancestors=JSONArray(); var current:View?=root
                            while(current!=null) {
                                val view=current
                                ancestors.put(JSONObject().put("identity",System.identityHashCode(view)).put("class",view.javaClass.name)
                                    .put("attached",view.isAttachedToWindow).put("hardwareAccelerated",view.isHardwareAccelerated)
                                    .put("laidOut",view.isLaidOut).put("layoutRequested",view.isLayoutRequested).put("hasWindowFocus",view.hasWindowFocus())
                                    .put("tokenIdentity",view.windowToken?.let(System::identityHashCode) ?: JSONObject.NULL)
                                    .put("displayId",view.display?.displayId ?: JSONObject.NULL)
                                    .put("matrixIdentity",view.matrix.isIdentity)
                                    .put("transform",JSONArray(listOf(view.scaleX,view.scaleY,view.rotation,view.rotationX,view.rotationY,
                                        view.translationX,view.translationY,view.translationZ))))
                                if(view===decor)break
                                current=view.parent as? View
                            }
                            put("targetAncestorChain",ancestors)
                        } }
                        .put("systemBarsInsetsPx", JSONArray(listOf(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)))
                        .put("scrolls", JSONArray(nodes(root).filterIsInstance<ScrollView>().map {
                            JSONObject().put("semanticId", it.tag).put("scrollX", it.scrollX).put("scrollY", it.scrollY)
                        })).put("target", observe())
                }
                main {
                    if(targetDecor==null) { targetDecor=window.decorView; targetToken=window.decorView.windowToken; targetDisplayId=window.decorView.display?.displayId }
                    if (activity != null) check(activity.hasWindowFocus()) { "Specimen capture lost window focus" }
                    if(activity==null || originMode==OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW) validateExplicitWindow()
                    before = timed(Timing.GEOMETRY_BEFORE) { geometry() }
                            val decor = window.decorView
                    val wl = before.getJSONArray("appLocationInWindowPx")
                    val sl = before.getJSONArray("appLocationOnScreenPx")
                    source = Rect(wl.getInt(0), wl.getInt(1), wl.getInt(0) + root.width, wl.getInt(1) + root.height)
                    screen = Rect(sl.getInt(0), sl.getInt(1), sl.getInt(0) + root.width, sl.getInt(1) + root.height)
                    attempt.put("windowSourceRectPx", rect(source)).put("displayAppRectPx", rect(screen)).put("geometryBefore", before)
                    val dw = before.getJSONArray("decorLocationInWindowPx")
                    val ds = before.getJSONArray("decorLocationOnScreenPx")
                    if (activity != null) {
                        val metrics = activity.windowManager.currentWindowMetrics.bounds
                        val common = dw.getInt(0) == 0 && dw.getInt(1) == 0 &&
                            screen.left - source.left == ds.getInt(0) && screen.top - source.top == ds.getInt(1)
                        val mapped = if (originMode == OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW) {
                            val displaySize = android.graphics.Point().also(requireNotNull(root.display)::getRealSize)
                            val decorRect = Rect(ds.getInt(0), ds.getInt(1), ds.getInt(0) + decor.width, ds.getInt(1) + decor.height)
                            common && metrics.contains(decorRect) && Rect(0, 0, displaySize.x, displaySize.y).contains(decorRect)
                        } else common && metrics.left == ds.getInt(0) && metrics.top == ds.getInt(1) &&
                            metrics.width() == decor.width && metrics.height() == decor.height
                        if (!mapped && dw.getInt(0) == 0 && dw.getInt(1) == 0 &&
                            screen.left - source.left == ds.getInt(0) && screen.top - source.top == ds.getInt(1) &&
                            (metrics.left != ds.getInt(0) || metrics.top != ds.getInt(1) ||
                                metrics.width() != decor.width || metrics.height() != decor.height) && originMode == OriginMode.DEFAULT) {
                            mappingDiagnosticReason = "ACTIVITY_DECOR_DIFFERS_FROM_HOST_METRICS"
                        }
                        check(mapped) {
                            "Unknown Window-layer/decor/metrics origin mapping"
                        }
                    } else {
                        check(dw.getInt(0)==0 && dw.getInt(1)==0 && screen.left-source.left==ds.getInt(0) &&
                            screen.top-source.top==ds.getInt(1)) { "Unknown explicit Window-layer/decor origin mapping" }
                    }
                    check(listOf(root, decor).all { it.scaleX == 1f && it.scaleY == 1f && it.rotation == 0f && it.rotationX == 0f && it.rotationY == 0f }) {
                        "Scaled or rotated Window-layer mapping is unsupported"
                    }
                    check(source.left >= 0 && source.top >= 0 && source.right <= decor.width && source.bottom <= decor.height)
                    attempt.put("windowLayerMapping", when(originMode) {
                        OriginMode.DEFAULT -> "SDK34_measured_zero_surfaceInsets_unscaled_Window_local"
                        OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW -> "measured_short_decor_within_host_metrics_and_display"
                        OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION -> "SDK34_measured_source_translation_symmetric_layer_extension"
                    })
                    if(activity==null) attempt.put("explicitWindowMappingMode",mappingMode)
                }
                val images = linkedMapOf<String, Bitmap>()
                var appDisplay: Bitmap? = null
                var acceptedAttempt = false
                try {
                    fun copy(role: String): Bitmap {
                        val timeout = remaining(role)
                        val result = timed(if (role == "surface-before") Timing.PIXEL_COPY_S1 else Timing.PIXEL_COPY_S2) {
                            copyWindow(window, source, timeout)
                        }
                        result.bitmap?.let { images[role] = it }
                        attempt.put("${role}Code", if (result.callbackCompleted) result.resultCode else JSONObject.NULL)
                            .put("${role}Callback", result.callbackCompleted).put("${role}SourceRectAfterSdk", rect(result.sourceRectAfterSdk))
                        check(result.callbackCompleted) { "PIXEL_COPY_CALLBACK_TIMEOUT $role; no Android result received within capture budget" }
                        check(result.resultCode == PixelCopy.SUCCESS) { "PixelCopy $role failed: ${result.resultCode}" }
                        // SDK34 offsets this input Rect by surfaceInsets. A fresh
                        // Rect prevents accumulated offsets. Only the explicit opt-in
                        // mode accepts a measured nonnegative translation.
                        if (originMode == OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION) {
                            val actual = result.sourceRectAfterSdk
                            val dx = actual.left - source.left; val dy = actual.top - source.top
                            check(dx >= 0 && dy >= 0 && actual.right == source.right + dx && actual.bottom == source.bottom + dy) {
                                "Unsupported SDK source Rect translation"
                            }
                            val first = measuredTranslation
                            if (first == null) measuredTranslation = android.graphics.Point(dx, dy)
                            else check(first.x == dx && first.y == dy) { "S1/S2 SDK source translation changed" }
                            attempt.put("measuredSourceTranslationPx", JSONArray(listOf(dx,dy)))
                                .put("windowLayerRoiPx", rect(actual))
                        } else {
                            if (activity == null && result.sourceRectAfterSdk != source) {
                                mappingDiagnosticReason = "EXPLICIT_WINDOW_PIXELCOPY_SOURCE_RECT_OFFSET"
                            }
                            check(result.sourceRectAfterSdk == source) { "Nonzero surfaceInsets: Window-layer buffer origin is unsupported" }
                        }
                        val bitmap = requireNotNull(result.bitmap)
                        attempt.put(role, bitmapDescription(bitmap)).put("${role}AppArgbSha256",
                            timed(if (role == "surface-before") Timing.SHA_S1 else Timing.SHA_S2) { pixelSha(bitmap) })
                        return bitmap
                    }
                    fun capture(role: String, windowLayer: Boolean): Bitmap {
                        remaining(role)
                        val slot = when (role) {
                            "layer-before" -> Timing.WINDOW_LAYER_W1
                            "display" -> Timing.DISPLAY_D1
                            "display-after" -> Timing.DISPLAY_D2
                            else -> Timing.WINDOW_LAYER_W2
                        }
                        val raw = requireNotNull(timed(slot) {
                            if (windowLayer) test.uiAutomation.takeScreenshot(window) else test.uiAutomation.takeScreenshot()
                        }) {
                            "UiAutomation $role returned null"
                        }
                        images[role] = raw
                        attempt.put(role, bitmapDescription(raw))
                        if (windowLayer) {
                            val size = before.getJSONArray("windowDecorSizePx")
                            if (originMode == OriginMode.MEASURED_WINDOW_BUFFER_TRANSLATION) {
                                val shift = requireNotNull(measuredTranslation)
                                val prior = measuredLayerSize
                                check(raw.width == size.getInt(0) + 2 * shift.x && raw.height == size.getInt(1) + 2 * shift.y &&
                                    source.left + shift.x >= 0 && source.top + shift.y >= 0 &&
                                    source.right + shift.x <= raw.width && source.bottom + shift.y <= raw.height &&
                                    (prior == null || (prior.x == raw.width && prior.y == raw.height))) {
                                    "Unknown or asymmetric Window-layer buffer extension"
                                }
                                measuredLayerSize = android.graphics.Point(raw.width, raw.height)
                                attempt.put("measuredWindowLayerSizePx", JSONArray(listOf(raw.width,raw.height)))
                                    .put("surfaceInsetsMeasurement", "UNKNOWN_NOT_READ; layer extension inferred only from actual SDK source translation")
                            } else check(raw.width == size.getInt(0) && raw.height == size.getInt(1)) {
                                "Unknown Window-layer bitmap origin/size; no resizing allowed"
                            }
                        } else {
                            check(screen.left >= 0 && screen.top >= 0 && screen.right <= raw.width && screen.bottom <= raw.height)
                        }
                        return raw
                    }
                    fun <T> readable(raw: Bitmap, copySlot: Int, action: (Bitmap) -> T): T {
                        val software = requireNotNull(timed(copySlot) { raw.copy(Bitmap.Config.ARGB_8888, false) })
                        try { return action(software) } finally { software.recycle() }
                    }
                    val s1 = copy("surface-before")
                    val w1 = capture("layer-before", true)
                    fun layerRoi(): Rect = Rect(source).also { measuredTranslation?.let { shift -> it.offset(shift.x, shift.y) } }
                    var windowColors = s1.colorSpace != null && s1.colorSpace == w1.colorSpace
                    val w1Difference = readable(w1, Timing.RAW_COPY_W1) { bitmap ->
                        attempt.put("layer-beforeAppArgbSha256", timed(Timing.SHA_W1) { pixelSha(bitmap, layerRoi()) })
                        timed(Timing.DIFF_S1_W1) { pixelDifferenceEvidence(s1, bitmap, layerRoi(), screen) }
                    }
                    val d1 = capture("display", false)
                    val crossDifference = readable(d1, Timing.RAW_COPY_D1) { bitmap ->
                        attempt.put("displayAppArgbSha256", timed(Timing.SHA_D1) { pixelSha(bitmap, screen) })
                        appDisplay = timed(Timing.APP_DISPLAY_CROP) {
                            Bitmap.createBitmap(bitmap, screen.left, screen.top, screen.width(), screen.height())
                        }
                        timed(Timing.DIFF_S1_D1) { pixelDifferenceEvidence(s1, bitmap, screen, screen) }
                    }
                    val d2 = capture("display-after", false)
                    val displayColors = d1.colorSpace != null && d1.colorSpace == d2.colorSpace
                    val displayDifference = readable(d2, Timing.RAW_COPY_D2) { bitmap ->
                        attempt.put("display-afterAppArgbSha256", timed(Timing.SHA_D2) { pixelSha(bitmap, screen) })
                        timed(Timing.DIFF_D1_D2) { pixelDifferenceEvidence(requireNotNull(appDisplay), bitmap, screen, screen) }
                    }
                    appDisplay?.recycle(); appDisplay = null
                    val w2 = capture("layer-after", true)
                    windowColors = windowColors && s1.colorSpace == w2.colorSpace
                    val w2Difference = readable(w2, Timing.RAW_COPY_W2) { bitmap ->
                        attempt.put("layer-afterAppArgbSha256", timed(Timing.SHA_W2) { pixelSha(bitmap, layerRoi()) })
                        timed(Timing.DIFF_S1_W2) { pixelDifferenceEvidence(s1, bitmap, layerRoi(), screen) }
                    }
                    val s2 = copy("surface-after")
                    windowColors = windowColors && s1.colorSpace == s2.colorSpace
                    val surfaceDifference = timed(Timing.DIFF_S1_S2) {
                        pixelDifferenceEvidence(s1, s2, Rect(0, 0, s2.width, s2.height), screen)
                    }
                    lateinit var after: JSONObject
                    main {
                        after = timed(Timing.GEOMETRY_AFTER) { geometry() }
                        if(activity==null || originMode==OriginMode.MEASURED_SHORT_ACTIVITY_WINDOW) validateExplicitWindow()
                    }
                    val geometryStable = timed(Timing.GEOMETRY_JSON_COMPARE) { WindowGeometryJsonEquality.same(before, after) }
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
            try { writeProof(false, failure.stackTraceToString()) }
            catch (proofFailure: Throwable) { failure.addSuppressed(proofFailure) }
            mappingDiagnosticReason?.let { reason ->
                // Separate clock and files. This does not change the original proof or result.
                try {
                    WindowOriginDiagnostic(test).capture(window, root, requireNotNull(file.parentFile), reason,
                        source = attempts.optJSONObject(attempts.length() - 1)?.optJSONArray("windowSourceRectPx"),
                        screen = attempts.optJSONObject(attempts.length() - 1)?.optJSONArray("displayAppRectPx"),
                        expectedDecor = targetDecor, expectedToken = targetToken, expectedDisplayId = targetDisplayId)
                } catch (_: Throwable) {
                    // The diagnostic records its own error and must never replace the original failure.
                }
            }
            throw failure
        } finally {
            retained?.recycle()
            // Terminal diagnostic only: never feeds acceptance or rewrites its proof.
            try {
                val attemptRows = JSONArray()
                captureTimings.forEachIndexed { index, times ->
                    val stages = JSONObject()
                    Timing.names.forEachIndexed { slot, name ->
                        val first = times[slot * 2]; val last = times[slot * 2 + 1]
                        stages.put(name, if (first > 0L && last >= first) last - first else JSONObject.NULL)
                    }
                    attemptRows.put(JSONObject().put("attempt", index + 1).put("stageDurationNanos", stages))
                }
                val proofRows = JSONArray()
                proofTimings.forEachIndexed { index, times ->
                    proofRows.put(JSONObject().put("writeOrdinal", index + 1)
                        .put("proofBuildNanos", if (times[1] >= times[0] && times[0] > 0L) times[1] - times[0] else JSONObject.NULL)
                        .put("proofSerializationNanos", JSONObject.NULL)
                        .put("proofFileWriteNanos", JSONObject.NULL)
                        .put("proofStreamedWriteNanos", if (times[5] >= times[2] && times[2] > 0L) times[5] - times[2] else JSONObject.NULL))
                }
                timingFile.writeText(JSONObject().put("scope", "DIAGNOSTIC_ONLY_NOT_ACCEPTANCE")
                    .put("clock", "SystemClock.elapsedRealtimeNanos")
                    .put("proofWriteTimingDefinition", "proofStreamedWriteNanos includes UTF-8 serialization, flush, close, and atomic publication; legacy split fields unavailable")
                    .put("attempts", attemptRows).put("proofWrites", proofRows).toString(2))
            } catch (_: Throwable) {
                // Timing diagnostics cannot replace the original accepted/failed result.
            }
        }
    }
    private fun Bitmap.output(file: File) {
        file.outputStream().use { check(compress(Bitmap.CompressFormat.PNG, 100, it)) }
    }
}
