package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.Window
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** Extra raw measurements after a specific, already-persisted mapping failure.
 * This never supplies a source rectangle or pixels to the acceptance protocol. */
internal class WindowOriginDiagnostic(private val test: Instrumentation) {
    private fun main(action: () -> Unit) {
        var error: Throwable? = null
        test.runOnMainSync { try { action() } catch (failure: Throwable) { error = failure } }
        error?.let { throw it }
    }
    private fun rect(value: Rect) = JSONArray(listOf(value.left, value.top, value.right, value.bottom))
    private fun point(value: IntArray) = JSONArray(listOf(value[0], value[1]))
    private fun sha(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = ByteArray(8192)
        while (true) {
            val count = input.read(bytes)
            if (count < 0) break
            digest.update(bytes, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    private fun actualSurfaceInsets(decor: View): JSONObject = try {
        val rootMethod = View::class.java.getDeclaredMethod("getViewRootImpl").apply { isAccessible = true }
        val viewRoot = requireNotNull(rootMethod.invoke(decor))
        val attributes = viewRoot.javaClass.getDeclaredField("mWindowAttributes")
            .apply { isAccessible = true }.get(viewRoot)
        val insets = requireNotNull(attributes.javaClass.getField("surfaceInsets").get(attributes) as? Rect)
        JSONObject().put("status", "READ_FROM_ACTUAL_VIEW_ROOT_ATTRIBUTES").put("rectPx", rect(insets))
    } catch (error: Throwable) {
        JSONObject().put("status", "UNKNOWN_REFLECTION_UNAVAILABLE")
            .put("failureType", error.javaClass.name).put("message", error.message ?: JSONObject.NULL)
    }
    private fun save(bitmap: Bitmap, folder: File, role: String, artifacts: JSONArray) {
        val file = File(folder, "diagnostic-window-origin-$role.png")
        file.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        artifacts.put(JSONObject().put("role", role).put("file", file.name).put("sha256", sha(file))
            .put("rawFullBitmap", true).put("sizePx", JSONArray(listOf(bitmap.width, bitmap.height)))
            .put("config", bitmap.config?.name ?: JSONObject.NULL)
            .put("colorSpace", bitmap.colorSpace?.name ?: JSONObject.NULL))
    }

    fun capture(window: Window, root: View, folder: File, reason: String, source: JSONArray?,
        screen: JSONArray?, expectedDecor: View?, expectedToken: IBinder?, expectedDisplayId: Int?) {
        val started = SystemClock.elapsedRealtime()
        val deadline = started + 5000
        val steps = JSONArray()
        val artifacts = JSONArray()
        val proof = JSONObject().put("status", "DIAGNOSTIC_NOT_ACCEPTANCE")
            .put("accepted", false).put("requiredProof", false).put("reason", reason)
            .put("independentDeadlineMs", 5000).put("clock", "SystemClock.elapsedRealtime")
            .put("originalFailureProofUnaffected", "window-frame-sync.json")
            .put("steps", steps).put("artifacts", artifacts)
        fun remaining(stage: String): Long {
            val now = SystemClock.elapsedRealtime()
            val left = deadline - now
            steps.put(JSONObject().put("stage", stage).put("elapsedMs", now - started).put("remainingMs", left))
            check(left > 0) { "DIAGNOSTIC_DEADLINE_EXHAUSTED before $stage" }
            return left
        }
        var pixelCopy: Bitmap? = null
        var layer: Bitmap? = null
        var display: Bitmap? = null
        try {
            check(reason == "EXPLICIT_WINDOW_PIXELCOPY_SOURCE_RECT_OFFSET" ||
                reason == "ACTIVITY_DECOR_DIFFERS_FROM_HOST_METRICS") { "Undeclared diagnostic reason" }
            val originalSource = requireNotNull(source).let {
                check(it.length() == 4)
                Rect(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3))
            }
            val originalScreen = requireNotNull(screen).let {
                check(it.length() == 4)
                Rect(it.getInt(0), it.getInt(1), it.getInt(2), it.getInt(3))
            }
            check(!originalSource.isEmpty && originalSource.width() == originalScreen.width() &&
                originalSource.height() == originalScreen.height()) { "Invalid original source/screen pair" }
            val geometry = JSONObject()
            main {
                val decor = window.decorView
                val token = decor.windowToken
                check(decor === expectedDecor && token != null && token === expectedToken &&
                    root.rootView === decor && root.windowToken === token && root.isAttachedToWindow &&
                    root.display?.displayId == expectedDisplayId && decor.display?.displayId == expectedDisplayId) {
                    "Original Window/root/decor/token/display identity changed; diagnostic unavailable"
                }
                val windowPosition = IntArray(2).also(root::getLocationInWindow)
                val screenPosition = IntArray(2).also(root::getLocationOnScreen)
                val decorWindow = IntArray(2).also(decor::getLocationInWindow)
                val decorScreen = IntArray(2).also(decor::getLocationOnScreen)
                val measuredSource = Rect(windowPosition[0], windowPosition[1],
                    windowPosition[0] + root.width, windowPosition[1] + root.height)
                val measuredScreen = Rect(screenPosition[0], screenPosition[1],
                    screenPosition[0] + root.width, screenPosition[1] + root.height)
                check(measuredSource == originalSource && measuredScreen == originalScreen) {
                    "Original Window source/screen geometry changed; diagnostic unavailable"
                }
                val metrics = requireNotNull(window.windowManager).currentWindowMetrics.bounds
                val attributeInsets = try {
                    rect(window.attributes.javaClass.getField("surfaceInsets").get(window.attributes) as Rect)
                } catch (_: Throwable) { JSONObject.NULL }
                geometry.put("windowIdentity", System.identityHashCode(window))
                    .put("rootIdentity", System.identityHashCode(root))
                    .put("decorIdentity", System.identityHashCode(decor))
                    .put("rootTokenIdentity", System.identityHashCode(root.windowToken))
                    .put("decorTokenIdentity", System.identityHashCode(token))
                    .put("displayId", root.display?.displayId ?: JSONObject.NULL)
                    .put("windowAttributesType", window.attributes.type)
                    .put("windowAttributesSurfaceInsetsPx_diagnosticMirror", attributeInsets)
                    .put("actualViewRootSurfaceInsets", actualSurfaceInsets(decor))
                    .put("rootLocationInWindowPx", point(windowPosition))
                    .put("rootLocationOnScreenPx", point(screenPosition))
                    .put("rootSizePx", JSONArray(listOf(root.width, root.height)))
                    .put("decorLocationInWindowPx", point(decorWindow))
                    .put("decorLocationOnScreenPx", point(decorScreen))
                    .put("decorSizePx", JSONArray(listOf(decor.width, decor.height)))
                    .put("hostCurrentWindowMetricsBoundsPx", rect(metrics))
                    .put("originalWindowSourceRectPx", rect(originalSource))
                    .put("originalDisplayAppRectPx", rect(originalScreen))
            }
            proof.put("geometry", geometry)
            remaining("pixel_copy_request")
            val destination = Bitmap.createBitmap(originalSource.width(), originalSource.height(), Bitmap.Config.ARGB_8888)
            val requested = Rect(originalSource)
            val callback = CountDownLatch(1)
            val code = AtomicInteger(-1)
            val abandoned = AtomicBoolean(false)
            val recycled = AtomicBoolean(false)
            fun recycleOnce() {
                if (recycled.compareAndSet(false, true)) destination.recycle()
            }
            try {
                main {
                    PixelCopy.request(window, requested, destination, { result ->
                        code.set(result)
                        callback.countDown()
                        if (abandoned.get()) recycleOnce()
                    }, Handler(Looper.getMainLooper()))
                }
            } catch (failure: Throwable) {
                recycleOnce()
                throw failure
            }
            proof.put("pixelCopySourceRectAfterSdkPx", rect(requested))
                .put("pixelCopySourceOffsetPx", JSONArray(listOf(
                    requested.left - originalSource.left, requested.top - originalSource.top)))
            val completed = try {
                callback.await(remaining("pixel_copy_callback"), TimeUnit.MILLISECONDS)
            } catch (failure: Throwable) {
                abandoned.set(true)
                if (callback.count == 0L) recycleOnce()
                throw failure
            }
            if (!completed) {
                // A late callback still owns destination. The callback takes over
                // recycling; the latch check closes a timeout/completion race.
                abandoned.set(true)
                if (callback.count == 0L) recycleOnce()
                error("DIAGNOSTIC_PIXEL_COPY_CALLBACK_TIMEOUT")
            }
            if (code.get() != PixelCopy.SUCCESS) {
                recycleOnce()
                error("Diagnostic PixelCopy result ${code.get()}")
            }
            pixelCopy = destination
            save(destination, folder, "surface", artifacts)
            remaining("window_layer_capture")
            layer = requireNotNull(test.uiAutomation.takeScreenshot(window)) { "Diagnostic Window layer returned null" }
            save(requireNotNull(layer), folder, "layer", artifacts)
            remaining("display_capture")
            display = requireNotNull(test.uiAutomation.takeScreenshot()) { "Diagnostic display returned null" }
            save(requireNotNull(display), folder, "display", artifacts)
            remaining("identity_and_geometry_recheck")
            main {
                val decor = window.decorView
                val token = decor.windowToken
                val windowPosition = IntArray(2).also(root::getLocationInWindow)
                val screenPosition = IntArray(2).also(root::getLocationOnScreen)
                check(decor === expectedDecor && token != null && token === expectedToken &&
                    root.rootView === decor && root.windowToken === token && root.isAttachedToWindow &&
                    root.display?.displayId == expectedDisplayId && decor.display?.displayId == expectedDisplayId &&
                    Rect(windowPosition[0], windowPosition[1], windowPosition[0] + root.width,
                        windowPosition[1] + root.height) == originalSource &&
                    Rect(screenPosition[0], screenPosition[1], screenPosition[0] + root.width,
                        screenPosition[1] + root.height) == originalScreen) {
                    "Window identity or geometry changed during diagnostic; raw captures cannot be mapped"
                }
            }
            proof.put("originalWindowIdentityAndGeometryStableAfterRaw", true)
            remaining("complete")
            proof.put("result", "RAW_CAPTURED_ORIGIN_NOT_CERTIFIED")
        } catch (failure: Throwable) {
            proof.put("result", "INCOMPLETE_OR_UNKNOWN")
                .put("failure", failure.stackTraceToString())
        } finally {
            proof.put("elapsedMs", SystemClock.elapsedRealtime() - started)
            File(folder, "diagnostic-window-origin.json").writeText(proof.toString(2))
            pixelCopy?.recycle(); layer?.recycle(); display?.recycle()
        }
    }
}
