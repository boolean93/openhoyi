package io.openhoyi.mobile

import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.LinearLayout
import android.view.View
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class CurveImportActivity : ThemedActivity() {
    companion object { private const val OPEN = 62 }
    private lateinit var text: EditText
    private lateinit var error: TextView
    private var formScroll: android.widget.ScrollView? = null
    val productLayoutEvidence = org.json.JSONObject()
    val productRestoreEvidence = org.json.JSONObject()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val style = TaskProductFormStyle(this)
        val body = style.visual.page(this, getString(R.string.profiles_import), getString(R.string.profiles_import_hint))
        profileStore(body) ?: return
        val host = beanWorkspace(body).apply { tag = "task-inline-form" }
        // beanInput inspects its attached ancestor chain synchronously, before
        // middle is attached to host below. Keep the inline contract here too.
        val workspace = style.visual.column("task-inline-form")
        style.visual.add(workspace, style.visual.text(getString(R.string.profiles_import_intro), TaskProductVisualStyle.body))
        val sources = style.visual.row("product/curve-import/sources").also {
            workspace.addView(it, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(16) })
        }
        val editor = beanWorkspace(workspace).apply { tag = "bean-full-field" }
        text = beanInput(editor, R.string.profiles_json).apply {
            setHint(R.string.profiles_import_placeholder)
            style.input(this, "curve-import", "raw")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine(false); minLines = 1
            gravity = android.view.Gravity.TOP
            layoutParams = android.widget.LinearLayout.LayoutParams(-1, 0, 1f)
            setText(savedInstanceState?.getString("text").orEmpty())
        }
        val dataLabel = (0 until editor.childCount).map(editor::getChildAt).filterIsInstance<TextView>().single { it.labelFor == text.id }
        style.visual.apply(dataLabel, TaskProductVisualStyle.section); dataLabel.setPadding(0, 0, 0, 0)
        editor.removeView(dataLabel)
        val format = style.visual.text(getString(R.string.profiles_compatibility), TaskProductVisualStyle.auxiliary,
            "product/curve-import/format", true)
        val dataHeader = object : LinearLayout(this) {
            init { isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL; addView(dataLabel); addView(format) }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val natural = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                dataLabel.measure(natural, natural); format.measure(natural, natural)
                val inline = dataLabel.measuredWidth + format.measuredWidth + style.dp(16) <= View.MeasureSpec.getSize(widthMeasureSpec)
                orientation = if (inline) HORIZONTAL else VERTICAL
                val direction = this.layoutDirection
                (dataLabel.layoutParams as LayoutParams).apply { width = if (inline) -2 else -1; weight = 0f }
                (format.layoutParams as LayoutParams).apply {
                    width = if (inline) 0 else -1; weight = if (inline) 1f else 0f
                    marginStart = if (inline) style.dp(16) else 0; marginEnd = 0
                    topMargin = if (inline) 0 else style.dp(8); resolveLayoutDirection(direction)
                }
                format.gravity = if (inline) android.view.Gravity.END or android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.START
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }
        editor.addView(dataHeader, 0, LinearLayout.LayoutParams(-1, -2).apply { topMargin = style.dp(16); bottomMargin = style.dp(8) })
        error = style.error(beanError(body))
        HoyiUi.button(this, sources, getString(R.string.profiles_paste)) {
            // Clipboard is read only after this explicit user action, never on resume/start.
            val value = runCatching {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).primaryClip?.takeIf { it.itemCount > 0 }
                    ?.getItemAt(0)?.coerceToText(this)?.toString()
            }.getOrNull()
            if (value == null || value.toByteArray(Charsets.UTF_8).size > CustomCurveDocument.MAX_BYTES) error.setText(R.string.profiles_import_invalid)
            else { text.setText(value); error.text = "" }
        }.apply {
            style.visual.button(this, outline = true); HomeVisualIcon.buttonIcon(this, "copy"); tag = "product/curve-import/paste"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = style.dp(8) }
        }
        HoyiUi.button(this, sources, getString(R.string.profiles_open_file)) {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/json", "text/plain")), OPEN)
        }.apply {
            style.visual.button(this, outline = true); HomeVisualIcon.buttonIcon(this, "export"); tag = "product/curve-import/open-file"
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = style.dp(8) }
        }
        val actions = style.visual.row("product/curve-import/actions")
        style.visual.action(actions, getString(R.string.beans_cancel), "product/curve-import/cancel", outline = true, top = 0) { finish() }
            .layoutParams = LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) }
        val previewAction = style.visual.action(actions, getString(R.string.profiles_import_preview), "product/curve-import/preview",
            primary = true, top = 0) { preview(text.text.toString()) }.apply {
            layoutParams = LinearLayout.LayoutParams(-2, -2)
            isEnabled = this@CurveImportActivity.text.text.isNotEmpty()
        }
        text.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(value: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(value: CharSequence?, start: Int, before: Int, count: Int) {
                // Only empty input is unavailable. Every nonempty value still uses the original decode on click.
                previewAction.isEnabled = !value.isNullOrEmpty()
            }
            override fun afterTextChanged(value: android.text.Editable?) = Unit
        })
        beanActions(body).addView(style.footer(style.visual.text(getString(R.string.curve_visual_import_scope),
            TaskProductVisualStyle.auxiliary, "product/curve-import/scope", true), actions, "curve-import", topRule = true),
            LinearLayout.LayoutParams(-1, -2))
        // The same input and chrome stay attached in both height modes. Only this
        // middle region scrolls; the page header and confirmation footer stay fixed.
        val middle = object : android.widget.ScrollView(this) {
            private var naturalContent = false
            override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
                parentHeightMeasureSpec: Int, heightUsed: Int) {
                if (naturalContent) {
                    super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
                    return
                }
                val params = child.layoutParams as android.view.ViewGroup.MarginLayoutParams
                val childWidth = getChildMeasureSpec(parentWidthMeasureSpec,
                    paddingLeft + paddingRight + params.leftMargin + params.rightMargin + widthUsed, params.width)
                val childHeight = (View.MeasureSpec.getSize(parentHeightMeasureSpec) - paddingTop - paddingBottom -
                    params.topMargin - params.bottomMargin - heightUsed).coerceAtLeast(0)
                child.measure(childWidth, View.MeasureSpec.makeMeasureSpec(childHeight, View.MeasureSpec.EXACTLY))
            }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = (View.MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight).coerceAtLeast(0)
                val available = (View.MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom).coerceAtLeast(0)
                val exact = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY)
                val free = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                fun chrome(parent: LinearLayout, except: View): Int {
                    var height = parent.paddingTop + parent.paddingBottom
                    for (index in 0 until parent.childCount) {
                        val child = parent.getChildAt(index)
                        if (child !== except && child.visibility != View.GONE) {
                            child.forceLayout(); child.measure(exact, free)
                            val params = child.layoutParams as LinearLayout.LayoutParams
                            height += child.measuredHeight + params.topMargin + params.bottomMargin
                        }
                    }
                    return height
                }
                val outerChrome = chrome(workspace, editor)
                val editorChrome = chrome(editor, text)
                val fixedChrome = outerChrome + editorChrome
                val currentLayout = text.layout
                val oneLine = if (currentLayout != null && currentLayout.lineCount > 0)
                    currentLayout.getLineBottom(0) - currentLayout.getLineTop(0)
                    else text.paint.fontMetricsInt.let { it.descent - it.ascent }
                val rawMinimum = maxOf(style.dp(52), text.minimumHeight, text.minHeight,
                    oneLine + text.compoundPaddingTop + text.compoundPaddingBottom)
                val minimumContent = fixedChrome + rawMinimum
                naturalContent = minimumContent > available
                (editor.layoutParams as LinearLayout.LayoutParams).apply {
                    height = if (naturalContent) editorChrome + rawMinimum else 0
                    weight = if (naturalContent) 0f else 1f
                }
                (text.layoutParams as LinearLayout.LayoutParams).apply {
                    // Bound the editor, never UNSPEC-measure its 92-line document as the viewport.
                    height = if (naturalContent) rawMinimum else 0
                    weight = if (naturalContent) 0f else 1f
                }
                text.forceLayout(); editor.forceLayout(); workspace.forceLayout()
                productLayoutEvidence.put("availableViewportPx", available).put("fixedChromeNaturalPx", fixedChrome)
                    .put("outerChromeNaturalPx", outerChrome).put("editorChromeNaturalPx", editorChrome)
                    .put("rawMinimumPx", rawMinimum).put("minimumContentNaturalPx", minimumContent)
                    .put("naturalContentScroll", naturalContent)
                    .put("rawViewportRequestedPx", if (naturalContent) rawMinimum else (available - fixedChrome).coerceAtLeast(0))
                    .put("minimumLineSource", if (currentLayout == null) "same-Paint font metrics before first Layout" else "existing Layout first line")
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            tag = "product/curve-import/content-scroll"; isFillViewport = false
            addView(workspace, android.widget.FrameLayout.LayoutParams(-1, -2))
        }
        formScroll = middle
        host.addView(middle, LinearLayout.LayoutParams(-1, -1))
        if (savedInstanceState?.containsKey("text") == true) restoreRawState(savedInstanceState, host)
    }

    private fun preview(value: String) {
        if (runCatching { CustomCurveDocument.decode(value) }.isFailure) { error.setText(R.string.profiles_import_invalid); return }
        startActivity(Intent(this, CurveImportPreviewActivity::class.java).putExtra(CurveImportPreviewActivity.DOCUMENT, value))
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != OPEN || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        Thread({
            val result = runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("No document input")
                val bytes = ByteArrayOutputStream()
                input.use { stream ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = stream.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= CustomCurveDocument.MAX_BYTES)
                        bytes.write(buffer, 0, count)
                    }
                }
                Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { text.setText(it); preview(it) }.onFailure { error.setText(R.string.profiles_import_invalid) }
            }
        }, "curve-document-import").start()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::text.isInitialized) {
            val value = text.text.toString()
            if (value.toByteArray(Charsets.UTF_8).size <= CustomCurveDocument.MAX_BYTES) {
                if (android.os.Build.VERSION.SDK_INT >= 30) text.rootWindowInsets?.let { insets ->
                    outState.putBoolean("textImeVisible", insets.isVisible(android.view.WindowInsets.Type.ime()))
                }
                outState.putString("text", value)
                outState.putInt("textStart", text.selectionStart.coerceAtLeast(0))
                outState.putInt("textEnd", text.selectionEnd.coerceAtLeast(0))
                outState.putBoolean("textFocused", text.hasFocus())
                outState.putInt("textScrollX", text.scrollX)
                outState.putInt("textScrollY", text.scrollY)
                outState.putInt("formScrollY", formScroll?.scrollY ?: 0)
            }
        }
    }
    private fun restoreRawState(state: Bundle?, host: View) {
        if (state == null) return
        val field = text.takeIf { state.getBoolean("textFocused") }
        val savedIme = if (state.containsKey("textImeVisible")) state.getBoolean("textImeVisible") else null
        productRestoreEvidence.put("savedImeVisible", savedIme ?: org.json.JSONObject.NULL)
            .put("savedRawFocused", state.getBoolean("textFocused")).put("requestedIme", false)
            .put("status", "WAITING_FOR_NATIVE_WINDOW")
        val observer = host.viewTreeObserver
        var pending = true
        var focusRestored = false
        var hadWindowFocus = false
        val inputMethod = this@CurveImportActivity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        var showRequested = false
        var resultCode: Int? = null
        var preDrawSequence = 0L
        var resultPreDrawSequence = -1L
        val sessionEvents = org.json.JSONArray()
        productRestoreEvidence.put("imeSessionEvents", sessionEvents)
        fun identity(value: Any?) = value?.let { System.identityHashCode(it) } ?: org.json.JSONObject.NULL
        fun tokenText(value: Any?) = value?.toString() ?: org.json.JSONObject.NULL
        fun causalIdentity(): org.json.JSONObject {
            val token = field?.windowToken
            return org.json.JSONObject().put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
                .put("pid", Process.myPid()).put("tid", Process.myTid())
                .put("activityIdentity", identity(this@CurveImportActivity))
                .put("fieldIdentity", identity(field)).put("decorIdentity", identity(window.decorView))
                .put("fieldId", field?.id ?: org.json.JSONObject.NULL)
                .put("windowTokenIdentity", identity(token)).put("windowTokenText", tokenText(token))
        }
        fun sessionEvent(phase: String, active: Boolean? = null, result: Int? = null) {
            sessionEvents.put(causalIdentity().put("phase", phase).put("preDrawSequence", preDrawSequence)
                .put("callerSite", "CurveImportActivity.restoreRawState")
                .put("resultCode", result ?: org.json.JSONObject.NULL)
                .put("fieldHasFocus", field?.hasFocus() ?: false).put("windowHasFocus", this@CurveImportActivity.hasWindowFocus())
                .put("fieldAttached", field?.isAttachedToWindow ?: false)
                .put("fieldWindowTokenIdentity", field?.windowToken?.let { System.identityHashCode(it) } ?: org.json.JSONObject.NULL)
                .put("inputMethodActive", active ?: org.json.JSONObject.NULL)
                .put("imeVisible", if (android.os.Build.VERSION.SDK_INT >= 30) host.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) ?: org.json.JSONObject.NULL else org.json.JSONObject.NULL)
                .put("hostHeightPx", host.height).put("hostLayoutRequested", host.isLayoutRequested))
        }
        val activeImeAnimations = mutableSetOf<android.view.WindowInsetsAnimation>()
        val animationEvents = org.json.JSONArray()
        productRestoreEvidence.put("imeAnimationEvents", animationEvents)
        lateinit var drawListener: android.view.ViewTreeObserver.OnPreDrawListener
        lateinit var focusListener: android.view.ViewTreeObserver.OnGlobalFocusChangeListener
        lateinit var windowListener: android.view.ViewTreeObserver.OnWindowFocusChangeListener
        lateinit var attachListener: View.OnAttachStateChangeListener
        lateinit var lifecycle: android.app.Application.ActivityLifecycleCallbacks
        fun finish(status: String) {
            if (!pending) return
            sessionEvent(status)
            pending = false
            productRestoreEvidence.put("status", status).put("activeImeAnimationsAtFinish", activeImeAnimations.size)
            if (android.os.Build.VERSION.SDK_INT >= 30) host.setWindowInsetsAnimationCallback(null)
            activeImeAnimations.clear()
            fun remove(from: android.view.ViewTreeObserver) {
                if (!from.isAlive) return
                from.removeOnPreDrawListener(drawListener); from.removeOnGlobalFocusChangeListener(focusListener)
                from.removeOnWindowFocusChangeListener(windowListener)
            }
            remove(observer)
            if (host.viewTreeObserver !== observer) remove(host.viewTreeObserver)
            host.removeOnAttachStateChangeListener(attachListener)
            this@CurveImportActivity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        }
        focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, next ->
            if (focusRestored && field != null && next !== field) finish("CANCELLED_FOCUS_CHANGED")
        }
        windowListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            sessionEvent(if (focused) "window_focus_gained" else "window_focus_lost")
            if (focused) { hadWindowFocus = true; host.invalidate() }
            else if (hadWindowFocus) finish("CANCELLED_WINDOW_FOCUS_LOST")
        }
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) { sessionEvent("host_attached"); view.invalidate() }
            override fun onViewDetachedFromWindow(view: View) { finish("CANCELLED_DETACHED") }
        }
        lifecycle = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(owner: android.app.Activity, state: Bundle?) = Unit
            override fun onActivityStarted(owner: android.app.Activity) = Unit
            override fun onActivityResumed(owner: android.app.Activity) = Unit
            override fun onActivityPaused(owner: android.app.Activity) = Unit
            override fun onActivityStopped(owner: android.app.Activity) { if (owner === this@CurveImportActivity) finish("CANCELLED_STOPPED") }
            override fun onActivitySaveInstanceState(owner: android.app.Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(owner: android.app.Activity) { if (owner === this@CurveImportActivity) finish("CANCELLED_DESTROYED") }
        }
        drawListener = android.view.ViewTreeObserver.OnPreDrawListener {
            preDrawSequence++
            if (pending && host.isAttachedToWindow && this@CurveImportActivity.hasWindowFocus() && host.isLaidOut &&
                !host.isLayoutRequested && listOfNotNull(text, formScroll).none { it.isLayoutRequested }) {
                hadWindowFocus = true
                if (!focusRestored) {
                    if (field != null && field.isEnabled && field.isShown) {
                        focusRestored = true
                        if (!field.requestFocus()) finish("CANCELLED_FOCUS_REJECTED")
                        else {
                            field.setSelection(state.getInt("textStart").coerceIn(0, field.length()), state.getInt("textEnd").coerceIn(0, field.length()))
                        }
                    } else if (savedIme == true) finish("CANCELLED_SAVED_FOCUS_UNAVAILABLE")
                    else {
                        // Restoring a selection does not require opening or focusing the editor.
                        text.setSelection(state.getInt("textStart").coerceIn(0, text.length()), state.getInt("textEnd").coerceIn(0, text.length()))
                        focusRestored = true
                    }
                }
                if (pending && savedIme == true && field != null && focusRestored && !showRequested) {
                    val active = inputMethod.isActive(field)
                    productRestoreEvidence.put("inputMethodActiveBeforeRequest", active)
                    if (active && field.hasFocus() && field.hasWindowFocus() && field.windowToken != null) {
                        showRequested = true
                        productRestoreEvidence.put("requestedIme", true).put("requestCount", 1)
                            .put("requestMethod", "InputMethodManager.showSoftInput(field,0,ResultReceiver)")
                        sessionEvent("show_request", true)
                        val receiver = object : android.os.ResultReceiver(android.os.Handler(android.os.Looper.getMainLooper())) {
                            override fun onReceiveResult(code: Int, data: android.os.Bundle?) {
                                sessionEvent("show_result_observed", result = code)
                                if (!pending) return
                                resultCode = code; resultPreDrawSequence = preDrawSequence
                                productRestoreEvidence.put("imeResultCode", code).put("imeResultAtPreDrawSequence", resultPreDrawSequence)
                                sessionEvent("show_result", result = code)
                                if (code != android.view.inputmethod.InputMethodManager.RESULT_SHOWN &&
                                    code != android.view.inputmethod.InputMethodManager.RESULT_UNCHANGED_SHOWN) finish("IME_SHOW_RESULT_NOT_SHOWN")
                                else host.invalidate()
                            }
                        }
                        val accepted = inputMethod.showSoftInput(field, 0, receiver)
                        sessionEvent("show_returned")
                        productRestoreEvidence.put("showSoftInputReturned", accepted)
                        if (!accepted) finish("IME_SHOW_REQUEST_REJECTED")
                    }
                }
                val observedIme = if (android.os.Build.VERSION.SDK_INT >= 30)
                    host.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) else null
                productRestoreEvidence.put("observedImeVisible", observedIme ?: org.json.JSONObject.NULL)
                    .put("activeImeAnimationCount", activeImeAnimations.size)
                // An inherited visible inset is not confirmation for this new editor.
                // Wait for its active native input connection, one processed show request,
                // then a later real draw after any observed animation and pending layout.
                if (pending && activeImeAnimations.isEmpty() && (savedIme != true || (resultCode in setOf(android.view.inputmethod.InputMethodManager.RESULT_SHOWN,
                        android.view.inputmethod.InputMethodManager.RESULT_UNCHANGED_SHOWN) && preDrawSequence > resultPreDrawSequence &&
                        observedIme == true && field != null && inputMethod.isActive(field))) && !host.isLayoutRequested &&
                    listOfNotNull(text, formScroll).none { it.isLayoutRequested }) {
                    sessionEvent(if (savedIme == true) "apply_scroll_after_native_result" else "apply_scroll_without_ime_request", if (field != null) inputMethod.isActive(field) else null)
                    val layout = text.layout
                    if (layout == null) finish("UNKNOWN_RAW_LAYOUT")
                    else {
                        val contentWidth = (text.width - text.compoundPaddingLeft - text.compoundPaddingRight).coerceAtLeast(0)
                        val contentHeight = (text.height - text.compoundPaddingTop - text.compoundPaddingBottom).coerceAtLeast(0)
                        val longest = (0 until layout.lineCount).maxOfOrNull { index -> layout.getLineRight(index) - layout.getLineLeft(index) } ?: 0f
                        val maxX = (kotlin.math.ceil(longest.toDouble()).toLong() - contentWidth).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                        val maxY = (layout.height.toLong() - contentHeight).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                        text.scrollTo(state.getInt("textScrollX").coerceIn(0, maxX), state.getInt("textScrollY").coerceIn(0, maxY))
                        productRestoreEvidence.put("rawLayoutHeightPx", layout.height).put("rawContentWidthPx", contentWidth).put("rawContentHeightPx", contentHeight)
                            .put("rawRangeXPx", maxX).put("rawRangeYPx", maxY).put("rawSavedX", state.getInt("textScrollX")).put("rawSavedY", state.getInt("textScrollY"))
                            .put("rawAppliedX", text.scrollX).put("rawAppliedY", text.scrollY)
                        formScroll?.let { scroll ->
                            val viewport = (scroll.height.toLong() - scroll.paddingTop - scroll.paddingBottom).coerceAtLeast(0)
                            val range = ((scroll.getChildAt(0)?.height?.toLong() ?: 0L) - viewport).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                            scroll.scrollTo(0, state.getInt("formScrollY").coerceIn(0, range))
                            productRestoreEvidence.put("middleRangePx", range).put("middleSavedY", state.getInt("formScrollY")).put("middleAppliedY", scroll.scrollY)
                        }
                    }
                    if (pending) finish(if (savedIme == true) "RESTORED_AFTER_NATIVE_IME_RESULT_AND_LAYOUT" else "RESTORED_NATIVE_LAYOUT_WITHOUT_IME_REQUEST")
                }
            }
            true
        }
        this@CurveImportActivity.application.registerActivityLifecycleCallbacks(lifecycle)
        host.addOnAttachStateChangeListener(attachListener)
        observer.addOnWindowFocusChangeListener(windowListener)
        observer.addOnGlobalFocusChangeListener(focusListener)
        observer.addOnPreDrawListener(drawListener)
        if (android.os.Build.VERSION.SDK_INT >= 30) host.setWindowInsetsAnimationCallback(
            object : android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                private fun ime(animation: android.view.WindowInsetsAnimation) =
                    animation.typeMask and android.view.WindowInsets.Type.ime() != 0
                private fun event(phase: String) {
                    animationEvents.put(causalIdentity().put("phase", phase).put("callerSite", "CurveImportActivity.restoreRawState")
                        .put("activeImeAnimations", activeImeAnimations.size))
                }
                override fun onPrepare(animation: android.view.WindowInsetsAnimation) {
                    if (pending && ime(animation)) { activeImeAnimations.add(animation); event("prepare") }
                }
                override fun onStart(animation: android.view.WindowInsetsAnimation,
                    bounds: android.view.WindowInsetsAnimation.Bounds): android.view.WindowInsetsAnimation.Bounds {
                    if (pending && ime(animation)) { activeImeAnimations.add(animation); event("start") }
                    return bounds
                }
                override fun onProgress(insets: android.view.WindowInsets,
                    runningAnimations: MutableList<android.view.WindowInsetsAnimation>): android.view.WindowInsets {
                    if (pending) runningAnimations.filter(::ime).forEach { animation ->
                        if (activeImeAnimations.add(animation)) event("observed_running")
                    }
                    return insets
                }
                override fun onEnd(animation: android.view.WindowInsetsAnimation) {
                    if (pending && ime(animation)) {
                        activeImeAnimations.remove(animation); event("end")
                        // Invalidate schedules the next real draw; this callback never applies scroll.
                        host.invalidate()
                    }
                }
            })
        host.invalidate()
    }
}
