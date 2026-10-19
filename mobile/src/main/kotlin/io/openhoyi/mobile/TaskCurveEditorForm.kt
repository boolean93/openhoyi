package io.openhoyi.mobile

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.json.JSONObject

/** Permanent field hierarchy; only viewport measurement switches for a genuinely tight budget. */
internal class TaskCurveEditorForm(private val activity: ThemedActivity, body: LinearLayout,
    private val style: TaskProductFormStyle) {
    private fun dp(value: Int) = style.dp(value)
    private fun cell() = style.visual.column("bean-full-field")
    /** Fields keep the same parent. Only label/input measurement changes when all cards fit. */
    private inner class MetadataCell : LinearLayout(activity) {
        var inline = false
            set(value) { if (field != value) { field = value; forceLayout() } }
        init { orientation = VERTICAL; tag = "bean-full-field"; isBaselineAligned = false }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            orientation = if (inline) HORIZONTAL else VERTICAL
            gravity = if (inline) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.TOP
            for (index in 0 until childCount) {
                val child = getChildAt(index)
                (child.layoutParams as LayoutParams).apply {
                    width = if (!inline) -1 else if (index == 0) -2 else 0
                    weight = if (inline && index > 0) 1f else 0f
                    marginStart = if (inline && index > 0) dp(8) else 0; marginEnd = 0
                    topMargin = 0; bottomMargin = 0; resolveLayoutDirection(this@MetadataCell.layoutDirection)
                }
                if (index == 0) child.setPaddingRelative(child.paddingStart, child.paddingTop, child.paddingEnd, if (inline) 0 else dp(8))
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    val metadata: List<LinearLayout> = List(3) { MetadataCell() }
    val stageRows = List(4) { cell() }
    val stageHeader = style.visual.row()
    val supportingBody = cell()
    private val stageContent = cell()
    private val metadataContent = style.visual.row()
    private val stagePane = cell()
    private var compact = false
    private val stages = ScrollView(activity).apply { tag = "product/form/curve-editor/stage-scroll"; addView(stageContent) }
    private val metadataScroll = ScrollView(activity).apply { tag = "product/form/curve-editor/metadata-scroll"; addView(metadataContent) }
    private val previewScroll = object : ScrollView(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            supportingBody.findViewWithTag<View>("task-form-stage-preview")?.let { chart ->
                val width = (View.MeasureSpec.getSize(widthMeasureSpec) - supportingBody.paddingLeft - supportingBody.paddingRight).coerceAtLeast(0)
                val fixed = fixedHeight(supportingBody, chart, width)
                chart.layoutParams.height = if (compact || View.MeasureSpec.getMode(heightMeasureSpec) == View.MeasureSpec.UNSPECIFIED) dp(160)
                    else (View.MeasureSpec.getSize(heightMeasureSpec) - fixed).coerceIn(dp(160), dp(280))
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { tag = "product/form/curve-editor/preview-scroll"; isFillViewport = true; addView(supportingBody) }
    private val lower = style.visual.row()
    private val column = style.visual.column("product/form/curve-editor/content")
    private val contentScroll = object : ScrollView(activity) {
        override fun measureChildWithMargins(child: View, parentWidthMeasureSpec: Int, widthUsed: Int,
            parentHeightMeasureSpec: Int, heightUsed: Int) {
            if (compact) { super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed); return }
            val params = child.layoutParams as ViewGroup.MarginLayoutParams
            child.measure(getChildMeasureSpec(parentWidthMeasureSpec, paddingLeft + paddingRight + params.leftMargin + params.rightMargin + widthUsed, params.width),
                View.MeasureSpec.makeMeasureSpec((View.MeasureSpec.getSize(parentHeightMeasureSpec) - paddingTop - paddingBottom - params.topMargin - params.bottomMargin - heightUsed).coerceAtLeast(0), View.MeasureSpec.EXACTLY))
        }
    }.apply { tag = "product/form/curve-editor/content-scroll"; addView(column) }
    var productLayoutEvidence = JSONObject()
        private set
    private val host = object : LinearLayout(activity) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = MeasureSpec.getSize(heightMeasureSpec)
            val free = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            fun forceMetadataLayout(view: View) {
                if (view is ViewGroup) for (index in 0 until view.childCount) forceMetadataLayout(view.getChildAt(index))
                view.forceLayout()
            }
            fun measureMetadata(inline: Boolean): Int {
                metadata.forEach { (it as MetadataCell).inline = inline }
                forceMetadataLayout(metadataContent)
                metadataContent.measure(widthMeasureSpec, free)
                return metadataContent.measuredHeight
            }
            val metadataVertical = measureMetadata(false)
            val inlineCards = org.json.JSONArray()
            val inlineFits = metadata.mapIndexed { index, panel ->
                val label = panel.getChildAt(0) as? android.widget.TextView
                val inputHost = if (panel.childCount == 2) panel.getChildAt(1) else null
                fun field(view: View?): EditText? = when (view) {
                    is EditText -> view
                    is ViewGroup -> (0 until view.childCount).firstNotNullOfOrNull { field(view.getChildAt(it)) }
                    else -> null
                }
                val input = field(inputHost)
                label?.measure(free, free)
                val labelWidth = label?.measuredWidth ?: 0
                // Name text may scroll horizontally, but never receives a token-sized viewport.
                val inputText = if (index == 0) "国".repeat(8) else input?.text?.toString().orEmpty()
                val textWidth = input?.let { kotlin.math.ceil(android.text.Layout.getDesiredWidth(inputText, it.paint).toDouble()).toInt() } ?: 0
                // unit() has already reserved its real unit + gap in compoundPadding.
                // Do not count that unit a second time merely because inputHost is a wrapper.
                val compoundPadding = input?.let { it.compoundPaddingLeft + it.compoundPaddingRight } ?: 0
                val inputMinimum = textWidth + compoundPadding
                val innerWidth = (panel.measuredWidth - panel.paddingLeft - panel.paddingRight).coerceAtLeast(0)
                val fits = label != null && input != null && labelWidth + dp(8) + inputMinimum <= innerWidth
                inlineCards.put(JSONObject().put("index", index).put("cardWidthPx", panel.measuredWidth)
                    .put("innerWidthPx", innerWidth).put("labelNaturalWidthPx", labelWidth)
                    .put("inputTextAdvancePx", textWidth).put("inputCompoundPaddingPx", compoundPadding)
                    .put("inputMinimumWidthPx", inputMinimum).put("gapPx", dp(8)).put("fits", fits)
                    .put("inputWidthBasis", if (index == 0) "same-Paint eight CJK advances plus actual compound padding" else "actual input text advances plus actual compound padding, including unit reserve"))
                fits
            }.all { it }
            val stageWidth = ((width - dp(24)).coerceAtLeast(0) * 1.1f / 2.1f).toInt()
            val previewWidth = (width - dp(24) - stageWidth).coerceAtLeast(0)
            val stageInner = (stageWidth - stagePane.paddingLeft - stagePane.paddingRight).coerceAtLeast(0)
            stageHeader.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            columns.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            val stageMinimum = stageHeader.measuredHeight + columns.measuredHeight +
                stageRows.filter { it.visibility != View.GONE }.maxOfOrNull { shortMinimum(it) }.orZero() + stagePane.paddingTop + stagePane.paddingBottom
            stageContent.measure(MeasureSpec.makeMeasureSpec(stageInner, MeasureSpec.EXACTLY), free)
            val stageNatural = stageContent.measuredHeight + stagePane.paddingTop + stagePane.paddingBottom
            val chart = supportingBody.findViewWithTag<View>("task-form-stage-preview")
            val previewMinimum = fixedHeight(supportingBody, chart, (previewWidth - supportingBody.paddingLeft - supportingBody.paddingRight).coerceAtLeast(0)) + dp(160)
            val fullVertical = metadataVertical + dp(16) + maxOf(stageNatural, previewMinimum)
            val metadataInline = if (inlineFits) measureMetadata(true) else null
            val inlineSelected = fullVertical > height && inlineFits
            val metadataNatural = measureMetadata(inlineSelected)
            val metadataMinimum = metadata.maxOf { shortMinimum(it) + it.paddingTop + it.paddingBottom }
            val lowerMinimum = maxOf(stageMinimum, previewMinimum)
            compact = height < metadataMinimum + dp(16) + lowerMinimum
            (metadataScroll.layoutParams as LayoutParams).height = if (compact) -2 else minOf(metadataNatural, height / 3).coerceAtLeast(minOf(metadataMinimum, height))
            (lower.layoutParams as LayoutParams).apply { this.height = if (compact) -2 else 0; weight = if (compact) 0f else 1f }
            (stagePane.layoutParams as LayoutParams).height = if (compact) -2 else -1
            (previewScroll.layoutParams as LayoutParams).height = if (compact) -2 else -1
            (stages.layoutParams as LayoutParams).apply { this.height = if (compact) -2 else 0; weight = if (compact) 0f else 1f }
            productLayoutEvidence = JSONObject().put("availablePx", height).put("metadataNaturalPx", metadataNatural)
                .put("metadataVerticalNaturalPx", metadataVertical).put("metadataInlineNaturalPx", metadataInline ?: JSONObject.NULL)
                .put("fullVerticalNaturalPx", fullVertical).put("metadataInlineFits", inlineFits)
                .put("metadataInlineSelected", inlineSelected).put("metadataCards", inlineCards)
                .put("metadataMinimumPx", metadataMinimum).put("stageMinimumPx", stageMinimum).put("previewMinimumPx", previewMinimum)
                .put("compactSingleContentScroll", compact).put("stageNaturalPx", stageNatural)
                .put("fullNaturalPx", metadataNatural + dp(16) + maxOf(stageNatural, previewMinimum))
            // shortMinimum measures the EditText independently; replay the real wrapper/card
            // constraints even when their incoming MeasureSpecs match the previous pass.
            forceMetadataLayout(metadataContent)
            column.forceLayout(); metadataScroll.forceLayout(); lower.forceLayout(); stagePane.forceLayout(); previewScroll.forceLayout(); contentScroll.forceLayout()
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }.apply { orientation = LinearLayout.VERTICAL; tag = "task-inline-form" }
    private val columns = style.visual.row()
    init {
        (metadata + listOf(stagePane, supportingBody)).forEach { panel ->
            panel.background = style.visual.shape(style.visual.surface, true)
            panel.setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        body.addView(host, LinearLayout.LayoutParams(-1, 0, 1f))
        host.addView(contentScroll, LinearLayout.LayoutParams(-1, -1))
        column.addView(metadataScroll, LinearLayout.LayoutParams(-1, -2))
        metadata.forEachIndexed { index, panel -> metadataContent.addView(panel, LinearLayout.LayoutParams(0, -2, if (index == 0) 1.3f else 1f).apply { if (index > 0) marginStart = dp(16) }) }
        column.addView(lower, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(16) })
        lower.addView(stagePane, LinearLayout.LayoutParams(0, -1, 1.1f))
        lower.addView(previewScroll, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = dp(24) })
        stageContent.addView(stageHeader, LinearLayout.LayoutParams(-1, -2))
        stageContent.addView(columns, LinearLayout.LayoutParams(-1, -2))
        val firstWidth = maxOf(dp(80), dp(20) + kotlin.math.ceil(android.text.Layout.getDesiredWidth(activity.getString(R.string.profiles_stage, 4),
            style.visual.text("", TaskProductVisualStyle.body).paint).toDouble()).toInt())
        listOf(R.string.import_table_stage, R.string.import_table_pressure, R.string.import_table_water).forEachIndexed { index, label ->
            val heading = style.visual.text(activity.getString(label), TaskProductVisualStyle.auxiliary, secondary = true).apply { setPadding(0, 0, 0, dp(8)) }
            columns.addView(heading, if (index == 0) LinearLayout.LayoutParams(firstWidth, -2)
                else LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(8) })
        }
        stagePane.addView(stages, LinearLayout.LayoutParams(-1, 0, 1f))
        stageContent.foreground = object : android.graphics.drawable.Drawable() {
            private val paint = android.graphics.Paint().apply { color = this@TaskCurveEditorForm.style.visual.border; strokeWidth = activity.resources.displayMetrics.density }
            override fun draw(canvas: android.graphics.Canvas) {
                fun rule(y: Float) { canvas.drawLine(0f, y, bounds.right.toFloat(), y, paint) }
                rule(stageHeader.bottom.toFloat())
                rule((columns.bottom - dp(4)).toFloat())
                stageRows.filter { it.visibility != View.GONE }.zipWithNext().forEach { (row, next) ->
                    rule((row.bottom + next.top) / 2f)
                }
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
            @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
        }
        stageRows.forEachIndexed { index, row ->
            row.tag = "curve-editor-stage-$index"; row.orientation = LinearLayout.HORIZONTAL; row.isBaselineAligned = false; row.gravity = android.view.Gravity.CENTER_VERTICAL
            stageContent.addView(row, LinearLayout.LayoutParams(-1, -2).apply { if (index > 0) topMargin = dp(8) })
            val identity = style.visual.row().apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            identity.addView(View(activity).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(style.visual.stageColors[index]) } }, LinearLayout.LayoutParams(dp(12), dp(12)))
            identity.addView(style.visual.text(activity.getString(R.string.profiles_stage, index + 1), TaskProductVisualStyle.body), LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
            row.addView(identity, LinearLayout.LayoutParams(firstWidth, -2))
        }
    }
    private fun Int?.orZero() = this ?: 0
    private fun shortMinimum(view: View): Int {
        if (view is EditText) {
            view.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val naturalHeight = view.measuredHeight
            view.forceLayout()
            return naturalHeight
        }
        if (view !is ViewGroup) return 0
        val minimum = (0 until view.childCount).filter { view.getChildAt(it).visibility != View.GONE }.maxOfOrNull { shortMinimum(view.getChildAt(it)) } ?: 0
        view.forceLayout()
        return minimum
    }
    private fun fixedHeight(parent: LinearLayout, except: View?, width: Int): Int {
        var total = parent.paddingTop + parent.paddingBottom
        for (index in 0 until parent.childCount) {
            val child = parent.getChildAt(index)
            if (child !== except && child.visibility != View.GONE) {
                child.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val params = child.layoutParams as LinearLayout.LayoutParams
                total += child.measuredHeight + params.topMargin + params.bottomMargin
            }
        }
        return total
    }
    fun setStageCount(count: Int) { stageRows.forEachIndexed { index, row -> row.visibility = if (index < count) View.VISIBLE else View.GONE } }
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
        val observer = host.viewTreeObserver
        var pending = true
        var focusRestored = false
        var hadWindowFocus = false
        val inputMethod = activity.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        var showRequested = false
        var resultCode: Int? = null
        var preDrawSequence = 0L
        var resultPreDrawSequence = -1L
        val sessionEvents = org.json.JSONArray()
        productRestoreEvidence.put("imeSessionEvents", sessionEvents)
        fun sessionEvent(phase: String, active: Boolean? = null) {
            sessionEvents.put(JSONObject().put("phase", phase).put("preDrawSequence", preDrawSequence)
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
            activity.application.unregisterActivityLifecycleCallbacks(lifecycle)
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
            override fun onViewAttachedToWindow(view: View) { view.invalidate() }
            override fun onViewDetachedFromWindow(view: View) { finish("CANCELLED_DETACHED") }
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
                                if (!pending) return
                                resultCode = code; resultPreDrawSequence = preDrawSequence
                                productRestoreEvidence.put("imeResultCode", code).put("imeResultAtPreDrawSequence", resultPreDrawSequence)
                                sessionEvent("show_result")
                                if (code != android.view.inputmethod.InputMethodManager.RESULT_SHOWN &&
                                    code != android.view.inputmethod.InputMethodManager.RESULT_UNCHANGED_SHOWN) finish("IME_SHOW_RESULT_NOT_SHOWN")
                                else host.invalidate()
                            }
                        }
                        val accepted = inputMethod.showSoftInput(field, 0, receiver)
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
        observer.addOnWindowFocusChangeListener(windowListener)
        observer.addOnGlobalFocusChangeListener(focusListener)
        observer.addOnPreDrawListener(drawListener)
        if (android.os.Build.VERSION.SDK_INT >= 30) host.setWindowInsetsAnimationCallback(
            object : android.view.WindowInsetsAnimation.Callback(android.view.WindowInsetsAnimation.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE) {
                private fun ime(animation: android.view.WindowInsetsAnimation) =
                    animation.typeMask and android.view.WindowInsets.Type.ime() != 0
                private fun event(phase: String) {
                    animationEvents.put(JSONObject().put("phase", phase).put("activeImeAnimations", activeImeAnimations.size))
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
