package io.openhoyi.mobile

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject

/** Stable field parents, a single scroll surface and responsive, simultaneously visible stage cards. */
internal class TaskCurveEditorForm(private val activity: ThemedActivity, body: LinearLayout,
    private val style: TaskProductFormStyle) {
    private fun dp(value: Int) = style.dp(value)
    private fun cell() = style.visual.column("bean-full-field")
    private fun panel() = cell().apply {
        background = style.visual.shape(style.visual.surface, true)
        setPadding(dp(16), dp(12), dp(16), dp(12))
    }
    private inner class MetadataCell : LinearLayout(activity) {
        init { orientation = HORIZONTAL; isBaselineAligned = false; gravity = android.view.Gravity.CENTER_VERTICAL
            background = style.visual.shape(style.visual.surface, true)
            setPadding(dp(16), dp(10), dp(16), dp(10))
        }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val vertical = MeasureSpec.getSize(widthMeasureSpec) < dp((210 * resources.configuration.fontScale).toInt())
            orientation = if (vertical) VERTICAL else HORIZONTAL
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                (child.layoutParams as LayoutParams).apply {
                    width = if (vertical) -1 else if (i == 0) -2 else 0
                    weight = if (!vertical && i > 0) 1f else 0f
                    marginStart = if (!vertical && i > 0) dp(10) else 0
                }
                if (i == 0) child.setPadding(0, 0, 0, if (vertical) dp(6) else 0)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    val metadata: List<LinearLayout> = List(3) { MetadataCell() }
    val stageRows = List(4) { panel() }
    val stageHeader = style.visual.row()
    val stageModes = List(4) { style.visual.text("", TaskProductVisualStyle.body).apply {
        gravity = android.view.Gravity.CENTER; setPadding(dp(8), dp(6), dp(8), dp(6))
        background = style.visual.shape(style.visual.selected, false, 8)
        tag = "curve-stage-mode-$it"
    } }
    val firstModeHost = cell()
    val waterLimit = cell()
    val modeControls = cell()
    val configurationFields = cell()
    val supportingBody = panel()
    private val settingsBody = panel()
    private val column = cell()
    private val contentScroll = ScrollView(activity).apply {
        tag = "product/form/curve-editor/content-scroll"; isFillViewport = true; addView(column)
    }
    // Historical restore keys map to the same single content surface; the final key is authoritative.
    private val stages = contentScroll
    private val metadataScroll = contentScroll
    private val host = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL; tag = "task-inline-form"
        addView(contentScroll, LinearLayout.LayoutParams(-1, -1))
    }
    var productLayoutEvidence = JSONObject()
        private set
    private val add = style.visual.action(LinearLayout(activity), "+\n" + activity.getString(R.string.curve_editor_add_stage),
        "curve-stage-add", top = 0) {}.apply {
        (parent as ViewGroup).removeView(this)
        textSize = 20f; gravity = android.view.Gravity.CENTER
        minimumHeight = dp(180)
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(style.visual.surface); cornerRadius = dp(14).toFloat()
            setStroke(dp(1), style.visual.accent, dp(5).toFloat(), dp(4).toFloat())
        }
    }
    private val removes = mutableListOf<android.widget.Button>()
    private var activeCount = 2
    private var changeCount: (Int) -> Unit = {}
    /** A measured grid: wraps cards rather than shrinking type or making stages a carousel. */
    private inner class CardGrid(private val minimumDp: Int, private val maxColumns: Int) : ViewGroup(activity) {
        private var columns = 1
        private var rowHeights = emptyList<Int>()
        override fun generateDefaultLayoutParams() = LayoutParams(-1, -2)
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val gap = dp(12)
            val min = dp((minimumDp * activity.resources.configuration.fontScale.coerceAtLeast(1f)).toInt())
            val visible = (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }
            columns = ((width + gap) / (min + gap)).coerceIn(1, maxColumns).coerceAtMost(visible.size.coerceAtLeast(1))
            val cellWidth = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
            visible.forEach { it.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
            rowHeights = visible.chunked(columns).map { row -> row.maxOf { it.measuredHeight } }
            visible.forEachIndexed { index, child -> child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeights[index / columns], MeasureSpec.EXACTLY)) }
            setMeasuredDimension(width, rowHeights.sum() + gap * (rowHeights.size - 1).coerceAtLeast(0))
        }
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            val gap = dp(12); var top = 0
            (0 until childCount).map(::getChildAt).filter { it.visibility != GONE }.chunked(columns).forEachIndexed { rowIndex, row ->
                row.forEachIndexed { index, child ->
                    val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - (index + 1) * child.measuredWidth - index * gap else index * (child.measuredWidth + gap)
                    child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
                }
                top += rowHeights[rowIndex] + gap
            }
        }
    }
    init {
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        val metaGrid = CardGrid(205, 3)
        metadata.forEach(metaGrid::addView)
        column.addView(metaGrid, LinearLayout.LayoutParams(-1, -2))
        modeControls.background = style.visual.shape(style.visual.surface, true)
        modeControls.setPadding(dp(16), dp(8), dp(16), dp(8))
        column.addView(modeControls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        column.addView(stageHeader, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
        val grid = CardGrid(188, 4).apply { tag = "curve-stage-cards" }
        stageRows.forEachIndexed { index, card ->
            card.tag = "curve-editor-stage-$index"
            val heading = style.visual.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            heading.addView(View(activity).apply { background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(style.visual.stageColors[index])
            } }, LinearLayout.LayoutParams(dp(10), dp(10)))
            heading.addView(style.visual.text(activity.getString(R.string.profiles_stage, index + 1), TaskProductVisualStyle.section), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
            val remove = style.visual.action(heading, "−", "curve-stage-remove-$index", top = 0) {
                if (activeCount > 1 && index == activeCount - 1) changeCount(activeCount - 1)
            }.apply { contentDescription = activity.getString(R.string.curve_editor_remove_stage, index + 1); minWidth = dp(48); minimumWidth = dp(48); setPadding(0, 0, 0, 0) }
            removes += remove
            card.addView(heading, LinearLayout.LayoutParams(-1, -2))
            card.addView(stageModes[index], LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) })
            if (index == 0) card.addView(firstModeHost, LinearLayout.LayoutParams(-1, -2))
            grid.addView(card)
        }
        add.setOnClickListener { if (activeCount < 4) changeCount(activeCount + 1) }
        grid.addView(add)
        column.addView(grid, LinearLayout.LayoutParams(-1, -2))
        val lower = CardGrid(320, 2)
        lower.addView(supportingBody)
        style.visual.add(settingsBody, style.visual.text(activity.getString(R.string.curve_editor_more_settings), TaskProductVisualStyle.section))
        settingsBody.addView(waterLimit, LinearLayout.LayoutParams(-1, -2))
        settingsBody.addView(configurationFields, LinearLayout.LayoutParams(-1, -2))
        lower.addView(settingsBody)
        column.addView(lower, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
    }
    fun onStageCountChanged(action: (Int) -> Unit) { changeCount = action }
    fun setStageCount(count: Int) {
        activeCount = count
        stageRows.forEachIndexed { index, row -> row.visibility = if (index < count) View.VISIBLE else View.GONE }
        removes.forEachIndexed { index, button -> button.visibility = if (index == count - 1 && count > 1) View.VISIBLE else View.INVISIBLE }
        add.visibility = if (count < 4) View.VISIBLE else View.GONE
    }
    val productRestoreEvidence = JSONObject()
    fun save(state: Bundle, fields: Map<String, EditText>) {
        if (android.os.Build.VERSION.SDK_INT >= 30) host.rootWindowInsets?.let { insets ->
            state.putBoolean("editor-ime-visible", insets.isVisible(android.view.WindowInsets.Type.ime()))
        }
        state.putInt("form-scroll", stages.scrollY); state.putInt("metadata-scroll", metadataScroll.scrollY); state.putInt("editor-content-scroll", contentScroll.scrollY)
        fields.entries.firstOrNull { it.value.hasFocus() }?.let { (key, field) ->
            state.putString("form-focus", key); state.putInt("form-start", field.selectionStart); state.putInt("form-end", field.selectionEnd)
        }
    }
    fun restore(state: Bundle?, fields: Map<String, EditText>) {
        if (state == null) return
        val field = fields[state.getString("form-focus")]
        val savedIme = if (state.containsKey("editor-ime-visible")) state.getBoolean("editor-ime-visible") else null
        productRestoreEvidence.put("savedImeVisible", savedIme ?: JSONObject.NULL)
            .put("focusedFieldKey", state.getString("form-focus") ?: JSONObject.NULL).put("requestedIme", false)
            .put("status", "WAITING_FOR_NATIVE_WINDOW")
        var boundObserver: android.view.ViewTreeObserver? = null
        var pending = true
        var focusRestored = false
        var restorationStarted = false
        var hadWindowFocus = false
        val inputMethod = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        var showRequested = false
        var resultCode: Int? = null
        var preDrawSequence = 0L
        var resultPreDrawSequence = -1L
        val sessionEvents = org.json.JSONArray()
        productRestoreEvidence.put("imeSessionEvents", sessionEvents)
        fun identity(value: Any?) = value?.let(System::identityHashCode) ?: JSONObject.NULL
        fun causalIdentity(): JSONObject {
            val token = field?.windowToken
            return JSONObject().put("elapsedRealtimeNanos", SystemClock.elapsedRealtimeNanos())
                .put("pid", Process.myPid()).put("tid", Process.myTid())
                .put("activityIdentity", identity(activity)).put("fieldIdentity", identity(field))
                .put("decorIdentity", identity(activity.window.decorView)).put("fieldId", field?.id ?: JSONObject.NULL)
                .put("windowTokenIdentity", identity(token)).put("windowTokenText", token?.toString() ?: JSONObject.NULL)
        }
        fun sessionEvent(phase: String, active: Boolean? = null, result: Int? = null) {
            sessionEvents.put(causalIdentity().put("phase", phase).put("preDrawSequence", preDrawSequence)
                .put("callerSite", "TaskCurveEditorForm.restore")
                .put("resultCode", result ?: JSONObject.NULL)
                .put("fieldHasFocus", field?.hasFocus() ?: false).put("windowHasFocus", activity.hasWindowFocus())
                .put("fieldAttached", field?.isAttachedToWindow ?: false)
                .put("fieldWindowTokenIdentity", field?.windowToken?.let { System.identityHashCode(it) } ?: JSONObject.NULL)
                .put("inputMethodActive", active ?: JSONObject.NULL)
                .put("imeVisible", if (android.os.Build.VERSION.SDK_INT >= 30) host.rootWindowInsets?.isVisible(android.view.WindowInsets.Type.ime()) ?: JSONObject.NULL else JSONObject.NULL)
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
        fun removeTree(tree: android.view.ViewTreeObserver?) {
            tree?.takeIf { it.isAlive }?.let {
                it.removeOnPreDrawListener(drawListener); it.removeOnGlobalFocusChangeListener(focusListener)
                it.removeOnWindowFocusChangeListener(windowListener)
            }
        }
        fun unbindTree() {
            removeTree(boundObserver)
            if (host.viewTreeObserver !== boundObserver) removeTree(host.viewTreeObserver)
            boundObserver = null
        }
        fun bindTree() {
            val current = host.viewTreeObserver
            if (boundObserver === current) return
            unbindTree()
            if (current.isAlive) {
                current.addOnPreDrawListener(drawListener); current.addOnGlobalFocusChangeListener(focusListener)
                current.addOnWindowFocusChangeListener(windowListener)
                boundObserver = current
            }
        }
        fun finish(status: String) {
            if (!pending) return
            sessionEvent(status)
            pending = false
            productRestoreEvidence.put("status", status).put("activeImeAnimationsAtFinish", activeImeAnimations.size)
            if (android.os.Build.VERSION.SDK_INT >= 30) host.setWindowInsetsAnimationCallback(null)
            activeImeAnimations.clear()
            unbindTree()
            host.removeOnAttachStateChangeListener(attachListener)
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
        }
        focusListener = android.view.ViewTreeObserver.OnGlobalFocusChangeListener { _, next ->
            if (focusRestored && field != null && next !== field) finish("CANCELLED_FOCUS_CHANGED")
        }
        windowListener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            sessionEvent(if (focused) "window_focus_gained" else "window_focus_lost")
            if (focused) { hadWindowFocus = true; host.invalidate() }
            else if (hadWindowFocus && restorationStarted) finish("CANCELLED_WINDOW_FOCUS_LOST")
        }
        attachListener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                if (pending) { bindTree(); view.invalidate() }
            }
            override fun onViewDetachedFromWindow(view: View) {
                // TaskPageFrame reparents content while choosing its initial rail/bottom layout.
                // No restored input session exists before the first stable draw. Await its real attach
                // instead of losing the saved focus; genuine later detach still cancels restoration.
                if (pending && !restorationStarted && !showRequested &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    sessionEvent("waiting_initial_reattach")
                    productRestoreEvidence.put("status", "WAITING_FOR_INITIAL_REATTACH")
                    unbindTree()
                } else finish("CANCELLED_DETACHED")
            }
        }
        lifecycle = object : android.app.Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(owner: android.app.Activity, state: Bundle?) = Unit
            override fun onActivityStarted(owner: android.app.Activity) = Unit
            override fun onActivityResumed(owner: android.app.Activity) = Unit
            override fun onActivityPaused(owner: android.app.Activity) = Unit
            override fun onActivityStopped(owner: android.app.Activity) { if (owner === activity) finish("CANCELLED_STOPPED") }
            override fun onActivitySaveInstanceState(owner: android.app.Activity, state: Bundle) = Unit
            override fun onActivityDestroyed(owner: android.app.Activity) { if (owner === activity) finish("CANCELLED_DESTROYED") }
        }
        drawListener = android.view.ViewTreeObserver.OnPreDrawListener {
            preDrawSequence++
            if (pending && host.isAttachedToWindow && activity.hasWindowFocus() && host.isLaidOut &&
                !host.isLayoutRequested && listOf(stages, metadataScroll, contentScroll).none { it.isLayoutRequested }) {
                restorationStarted = true
                hadWindowFocus = true
                if (!focusRestored) {
                    if (field != null && field.isEnabled && field.isShown) {
                        focusRestored = true
                        if (!field.requestFocus()) finish("CANCELLED_FOCUS_REJECTED")
                        else {
                            field.setSelection(state.getInt("form-start").coerceIn(0, field.length()), state.getInt("form-end").coerceIn(0, field.length()))
                        }
                    } else if (savedIme == true) finish("CANCELLED_SAVED_FOCUS_UNAVAILABLE")
                    else focusRestored = true
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
                productRestoreEvidence.put("observedImeVisible", observedIme ?: JSONObject.NULL)
                    .put("activeImeAnimationCount", activeImeAnimations.size)
                // An inherited visible inset is not confirmation for this new editor.
                // Wait for its active native input connection, one processed show request,
                // then a later real draw after any observed animation and pending layout.
                if (pending && activeImeAnimations.isEmpty() && (savedIme != true || (resultCode in setOf(android.view.inputmethod.InputMethodManager.RESULT_SHOWN,
                        android.view.inputmethod.InputMethodManager.RESULT_UNCHANGED_SHOWN) && preDrawSequence > resultPreDrawSequence &&
                        observedIme == true && field != null && inputMethod.isActive(field))) && !host.isLayoutRequested &&
                    listOf(stages, metadataScroll, contentScroll).none { it.isLayoutRequested }) {
                    sessionEvent(if (savedIme == true) "apply_scroll_after_native_result" else "apply_scroll_without_ime_request", if (field != null) inputMethod.isActive(field) else null)
                    val positions = org.json.JSONArray()
                    listOf(stages to "form-scroll", metadataScroll to "metadata-scroll", contentScroll to "editor-content-scroll").forEach { (scroll, key) ->
                        val viewport = (scroll.height.toLong() - scroll.paddingTop - scroll.paddingBottom).coerceAtLeast(0)
                        val range = ((scroll.getChildAt(0)?.height?.toLong() ?: 0L) - viewport).coerceIn(0, Int.MAX_VALUE.toLong()).toInt()
                        val saved = state.getInt(key)
                        val before = scroll.scrollY
                        scroll.scrollTo(0, saved.coerceIn(0, range))
                        positions.put(JSONObject().put("tag", scroll.tag).put("savedY", saved).put("beforeY", before)
                            .put("rangePx", range).put("appliedY", scroll.scrollY))
                    }
                    productRestoreEvidence.put("scrolls", positions)
                    finish(if (savedIme == true) "RESTORED_AFTER_NATIVE_IME_RESULT_AND_LAYOUT" else "RESTORED_NATIVE_LAYOUT_WITHOUT_IME_REQUEST")
                }
            }
            true
        }
        activity.application.registerActivityLifecycleCallbacks(lifecycle)
        host.addOnAttachStateChangeListener(attachListener)
        bindTree()
        if (android.os.Build.VERSION.SDK_INT >= 30) host.setWindowInsetsAnimationCallback(
            object : android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                private fun ime(animation: android.view.WindowInsetsAnimation) =
                    animation.typeMask and android.view.WindowInsets.Type.ime() != 0
                private fun event(phase: String) {
                    animationEvents.put(causalIdentity().put("phase", phase).put("callerSite", "TaskCurveEditorForm.restore")
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
