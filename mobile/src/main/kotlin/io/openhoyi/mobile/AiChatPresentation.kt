package io.openhoyi.mobile

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.text.InputFilter
import android.text.InputType
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView

/** Shared, display-only chat shell. The host owns all storage, service and request behavior. */
internal class AiChatPresentation(
    private val context: Context,
    private val visual: SettingsVisualStyle,
    initialDraft: String,
    selectionStart: Int,
    selectionEnd: Int,
    private val onBack: () -> Unit,
    private val onNewChat: () -> Unit,
    private val onKey: () -> Unit,
    private val onSendOrStop: () -> Unit,
    private val onOpenLink: ((String) -> Unit)? = null,
    private val keyEnabled: Boolean = true,
    private val onUsage: (() -> Unit)? = null,
    private val onAttachment: (() -> Unit)? = null,
    private val onRemoveAttachment: ((AiAttachment) -> Unit)? = null,
    private val onModel: (() -> Unit)? = null,
    private val onReasoning: (() -> Unit)? = null,
    private val onOpenCurve: ((AiCurveCard) -> Unit)? = null,
    private val curveAvailable: (AiCurveCard) -> Boolean = { false },
    private val onReviewCurve: ((AiCurveCard) -> Unit)? = null,
    private val curveReviewable: (AiCurveCard) -> Boolean = { false },
    private val onViewCurve: ((AiCurveCard) -> Unit)? = null,
    private val curveLibraryAvailable: (AiCurveCard) -> Boolean = { false },
    private val onChooseAttachment: ((Boolean) -> Unit)? = null,
) {
    private val style = visual.palette
    private val assistantTextInset = style.dp(14)
    val root: LinearLayout
    val transcript: LinearLayout
    val input: EditText
    val send: Button
    private var attachmentButton: Button? = null
    private lateinit var draftFiles: LinearLayout
    private lateinit var draftImages: LinearLayout
    private lateinit var draftImageScroll: HorizontalScrollView
    private var imagePreviews: AiImagePreviews? = null
    val status: TextView
    val scroll: ScrollView
    var options: PopupWindow? = null
        private set
    private var currentModelId = AiModelPolicy.FLASH
    private var busy = false
    private var lastEnterDownTime = Long.MIN_VALUE
    private fun composing(): Boolean = BaseInputConnection.getComposingSpanStart(input.text) >= 0
    private fun hardwareEnter(event: KeyEvent): Boolean {
        val decision = AiChatKeyboardPolicy.hardware(event.keyCode == KeyEvent.KEYCODE_ENTER,
            event.action == KeyEvent.ACTION_DOWN, event.action == KeyEvent.ACTION_UP,
            event.isShiftPressed, event.repeatCount > 0, input.text.isNotBlank(), busy, composing())
        if (decision == AiChatKeyboardPolicy.Decision.SEND && event.downTime != lastEnterDownTime) {
            lastEnterDownTime = event.downTime
            onSendOrStop()
        }
        return decision != AiChatKeyboardPolicy.Decision.PASS
    }
    init {
        (context as? android.app.Activity)?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        root = style.column("ai/chat/root").apply {
            setBackgroundColor(style.page)
            setOnApplyWindowInsetsListener { view, insets ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val area = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                    view.setPadding(area.left, area.top, area.right, area.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        val toolbar = style.row("ai/chat/toolbar").apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(style.dp(12), style.dp(4), style.dp(12), style.dp(4))
        }
        toolbar.addView(ImageView(context).apply {
            setImageDrawable(HomeVisualIcon.drawable(context, "back", style.accent))
            contentDescription = context.getString(R.string.ui_back)
            setPadding(style.dp(12), style.dp(12), style.dp(12), style.dp(12))
            background = style.shape((style.page and 0x00FFFFFF) or (128 shl 24), radius = 24)
            isFocusable = true; setOnClickListener { onBack() }
        }, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        // Measure the previous stacked brand as the height budget; keep the chat viewport
        // unchanged in supported windows without fixing a height that could crop scaled text.
        val previousBrandTitle = style.text(context.getString(R.string.ai_title), TaskProductVisualStyle.section)
        val brandTitle = style.text(context.getString(R.string.ai_title), TaskProductVisualStyle.Type(18, 700, 23)).apply {
            gravity = Gravity.CENTER
            tag = "ai/chat/brand-title"
            setShadowLayer(style.dp(2).toFloat(), 0f, 0f, style.page)
        }
        val brand = object : LinearLayout(context) {
            init { orientation = HORIZONTAL; gravity = Gravity.CENTER; isBaselineAligned = false }
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = View.MeasureSpec.getSize(widthMeasureSpec)
                val freeHeight = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                previousBrandTitle.measure(View.MeasureSpec.makeMeasureSpec(available, View.MeasureSpec.AT_MOST), freeHeight)
                minimumHeight = style.dp(32) + previousBrandTitle.measuredHeight
                brandTitle.maxWidth = (available - style.dp(60)).coerceAtLeast(1)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            addView(ImageView(context).apply {
                tag = "ai/chat/machine-brand"
                setImageResource(R.drawable.hoyi_machine)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(style.dp(2), style.dp(2), style.dp(2), style.dp(2))
                background = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(style.surface)
                }
                clipToOutline = true
                elevation = style.dp(2).toFloat()
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(style.dp(52), style.dp(52)).apply { marginEnd = style.dp(8) })
            addView(brandTitle, LinearLayout.LayoutParams(-2, -2))
        }
        toolbar.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val menuButton = style.button(object : Button(context) {
            private val icon = HomeVisualIcon.drawable(context, "more", style.accent)
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val left = (width - icon.intrinsicWidth) / 2
                val top = (height - icon.intrinsicHeight) / 2
                icon.setBounds(left, top, left + icon.intrinsicWidth, top + icon.intrinsicHeight)
                icon.draw(canvas)
            }
        }).apply {
            text = ""; contentDescription = context.getString(R.string.ai_options)
            background = RippleDrawable(ColorStateList.valueOf(style.selected),
                style.shape((style.page and 0x00FFFFFF) or (128 shl 24), radius = 24), null)
            setOnClickListener { showOptions(this) }
        }
        toolbar.addView(menuButton, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        val header = style.column("ai/chat/header-overlay").apply {
            protectBackdropContent(leaveBottomPaddingVisible = true)
            val fade = style.dp(12)
            // Preserve the former 1dp separator plus 12dp transcript gap in the
            // scrollable leading space, without a hard divider below the overlay.
            setPadding(0, 0, 0, fade + style.dp(1))
            // The lower action-bar body is translucent, not just its trailing gap.
            background = object : Drawable() {
                private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                override fun onBoundsChange(bounds: android.graphics.Rect) {
                    val rgb = style.page and 0x00FFFFFF
                    paint.shader = android.graphics.LinearGradient(0f, bounds.top.toFloat(),
                        0f, bounds.bottom.toFloat(), intArrayOf(style.page, rgb or (242 shl 24),
                            rgb or (128 shl 24), rgb), floatArrayOf(0f, .45f, .72f, 1f),
                        android.graphics.Shader.TileMode.CLAMP)
                }
                override fun draw(canvas: Canvas) { canvas.drawRect(bounds, paint) }
                override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
                override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter; invalidateSelf() }
                @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
            }
            // Blank header pixels consume taps instead of activating links beneath it.
            isClickable = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        header.addView(toolbar, LinearLayout.LayoutParams(-1, -2))
        transcript = style.column("ai/chat/transcript").apply {
            setPadding(style.dp(16), 0, style.dp(16), style.dp(16))
        }
        val transcriptFrame = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                (transcript.layoutParams as FrameLayout.LayoutParams).width = minOf(width, style.dp(760))
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { addView(transcript, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL)) }
        scroll = ScrollView(context).apply { isFillViewport = true; addView(transcriptFrame) }
        val conversation = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                // This spacer belongs to scroll content, not the viewport. It scrolls away
                // under the header while preserving the old initial/restored text positions.
                header.measure(widthMeasureSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                if (transcriptFrame.paddingTop != header.measuredHeight)
                    transcriptFrame.setPadding(0, header.measuredHeight, 0, 0)
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            tag = "ai/chat/conversation-overlay"
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
            addView(header, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        }
        root.addView(conversation, LinearLayout.LayoutParams(-1, 0, 1f))
        // Transcript alone scrolls. The composer never follows the growing message history.
        val composer = style.row("ai/chat/composer").apply {
            protectBackdropContent()
            gravity = Gravity.CENTER_VERTICAL
            setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
        }
        val inputFrame = style.row("ai/chat/input-frame").apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(style.dp(2), 0, style.dp(2), 0)
            background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_activated), style.shape(style.surface, radius = 24).apply {
                    setStroke(style.dp(1).coerceAtLeast(1), style.accent)
                })
                addState(intArrayOf(), style.shape(style.surface, outline = true, radius = 24))
            }
        }
        composer.addView(inputFrame, LinearLayout.LayoutParams(-1, -2))
        status = style.text("", TaskProductVisualStyle.auxiliary, "ai/chat/status", true).apply {
            setPadding(assistantTextInset, 0, assistantTextInset, 0)
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        input = object : EditText(context) {
            override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
                val connection = super.onCreateInputConnection(outAttrs)
                // AOSP TextView adds NO_ENTER_ACTION to every multiline editor. Keep multiline
                // editing while letting the IME present Send; Shift+Enter remains a newline.
                outAttrs.imeOptions = (outAttrs.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION.inv()
                    and EditorInfo.IME_MASK_ACTION.inv()) or EditorInfo.IME_ACTION_SEND
                return connection
            }
        }.apply {
            hint = context.getString(R.string.ai_input); contentDescription = hint
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
            filters = arrayOf(InputFilter.LengthFilter(AiChatPolicy.INPUT_CHARS))
            minLines = 1; maxLines = if (context.resources.configuration.screenHeightDp < 480) 2 else 3
            isSaveEnabled = false; isSaveFromParentEnabled = false
            importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
            visual.input(this)
            background = null
            minHeight = style.dp(48); minimumHeight = style.dp(48)
            setOnFocusChangeListener { _, focused -> inputFrame.isActivated = focused }
            setText(initialDraft)
            setSelection(selectionStart.coerceIn(0, length()), selectionEnd.coerceIn(0, length()))
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER) hardwareEnter(event) else false
            }
            setOnEditorActionListener { _, actionId, event ->
                if (event?.keyCode == KeyEvent.KEYCODE_ENTER) hardwareEnter(event)
                else when (AiChatKeyboardPolicy.ime(actionId == EditorInfo.IME_ACTION_SEND,
                    text.isNotBlank(), busy, composing())) {
                    AiChatKeyboardPolicy.Decision.SEND -> { onSendOrStop(); true }
                    AiChatKeyboardPolicy.Decision.CONSUME -> true
                    AiChatKeyboardPolicy.Decision.PASS -> false
                }
            }
        }
        if (onAttachment != null || onChooseAttachment != null) {
            attachmentButton = style.button(Button(context)).apply {
                text = ""; compoundDrawablePadding = 0
                setPadding(0, 0, 0, 0)
                setCompoundDrawablesWithIntrinsicBounds(null, null, null, null)
                // Keep the full 48dp hit area; only feedback paints inside the frame stroke.
                background = android.graphics.drawable.InsetDrawable(
                    RippleDrawable(ColorStateList.valueOf(style.selected), null, style.shape(Color.WHITE, radius = 20)), style.dp(4))
                foreground = ChatPlusIcon(style.accent, style.dp(28))
                contentDescription = context.getString(R.string.ai_attachment_add)
                setOnClickListener { showAttachmentMenu(this) }
            }.also { inputFrame.addView(it, LinearLayout.LayoutParams(style.dp(48), style.dp(48))) }
        }
        inputFrame.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        send = object : Button(context) {
            private val sendIcon = ChatActionIcon(false, style.dp(20))
            private val stopIcon = ChatActionIcon(true, style.dp(20))
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val icon = if (busy) stopIcon else sendIcon
                val left = (width - icon.intrinsicWidth) / 2
                val top = (height - icon.intrinsicHeight) / 2
                icon.setBounds(left, top, left + icon.intrinsicWidth, top + icon.intrinsicHeight)
                icon.draw(canvas)
            }
        }.apply {
            text = ""; contentDescription = context.getString(R.string.ai_send)
            backgroundTintList = null; stateListAnimator = null; elevation = 0f
            // A 40dp visual circle inside the unchanged 48dp touch target leaves the
            // shared input outline visible above and below, without widening the composer.
            background = android.graphics.drawable.InsetDrawable(
                RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), style.shape(style.primary, radius = 20), null), style.dp(4))
            setPadding(0, 0, 0, 0)
            minWidth = style.dp(48); minHeight = style.dp(48)
            isAllCaps = false
            gravity = Gravity.CENTER
            setOnClickListener { onSendOrStop() }
        }
        inputFrame.addView(send, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
        root.addView(View(context).apply { setBackgroundColor(style.border) }, LinearLayout.LayoutParams(-1, style.dp(1)))
        val composerFrame = object : FrameLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                (composer.layoutParams as FrameLayout.LayoutParams).width = minOf(width, style.dp(760))
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply { addView(composer, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_HORIZONTAL)) }
        draftFiles = style.column("ai/chat/attachments").apply {
            setPadding(style.dp(16), style.dp(4), style.dp(16), style.dp(4))
        }
        draftImages = style.row("ai/chat/draft-images").apply {
            setPadding(style.dp(16), style.dp(4), style.dp(16), style.dp(4))
        }
        draftImageScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(draftImages, FrameLayout.LayoutParams(-2, -2))
        }
        root.addView(draftImageScroll, LinearLayout.LayoutParams(-1, -2))
        root.addView(draftFiles, LinearLayout.LayoutParams(-1, -2))
        root.addView(composerFrame, LinearLayout.LayoutParams(-1, -2))
    }
    fun dismissOptions() { options?.dismiss() }
    private var waitingDotsFrame = 0
    private var waitingStatus: String? = null
    private val waitingDots = object : Runnable {
        override fun run() {
            val text = waitingStatus ?: return
            if (!status.isAttachedToWindow || status.windowVisibility != View.VISIBLE) {
                stopWaitingDots()
                return
            }
            waitingDotsFrame = (waitingDotsFrame + 1) % 3
            status.text = AiWaitingDots.frame(text, waitingDotsFrame)
            status.postDelayed(this, 500L)
        }
    }
    private fun stopWaitingDots() {
        waitingStatus = null
        status.removeCallbacks(waitingDots)
    }
    private fun updateStatus(text: String, busy: Boolean) {
        stopWaitingDots()
        val waiting = busy && text.lineSequence().firstOrNull() == context.getString(R.string.ai_waiting)
        // Keep assistive output semantic and stable, never announce decorative ticks.
        status.accessibilityLiveRegion = if (waiting) View.ACCESSIBILITY_LIVE_REGION_NONE else View.ACCESSIBILITY_LIVE_REGION_POLITE
        status.contentDescription = if (waiting) text else null
        status.text = if (waiting) AiWaitingDots.frame(text, 0) else text
        status.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        if (waiting) {
            waitingStatus = text
            waitingDotsFrame = 0
            status.postDelayed(waitingDots, 500L)
        }
    }
    fun closeImagePreviews() {
        stopWaitingDots()
        imagePreviews?.close()
        imagePreviews = null
        renderedContent = null
    }
    private fun showAttachmentMenu(anchor: View) {
        if (!anchor.isEnabled || !anchor.isAttachedToWindow) return
        options?.dismiss()
        val list = style.column("ai/chat/attachment-menu").apply {
            setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
            background = style.shape(style.surface, outline = true, radius = 16)
        }
        fun choice(label: Int, action: () -> Unit) {
            list.addView(style.button(Button(context)).apply {
                text = context.getString(label)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(style.dp(12), style.dp(8), style.dp(12), style.dp(8))
                setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
                setOnClickListener {
                    options?.dismiss()
                    if (anchor.isEnabled) action()
                }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        choice(R.string.ai_attachment_image) { onChooseAttachment?.invoke(true) ?: onAttachment?.invoke() }
        choice(R.string.ai_attachment_document) { onChooseAttachment?.invoke(false) ?: onAttachment?.invoke() }
        val width = minOf(style.dp(208), (root.width - style.dp(24)).coerceAtLeast(style.dp(48)))
        list.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        options = PopupWindow(list, width, -2, true).apply {
            isOutsideTouchable = true
            isClippingEnabled = true
            inputMethodMode = PopupWindow.INPUT_METHOD_NEEDED
            elevation = style.dp(8).toFloat()
            setBackgroundDrawable(style.shape(Color.TRANSPARENT))
            setOnDismissListener { options = null }
            // Anchor above +, including while the IME is present; never a screen-centred dialog.
            showAsDropDown(anchor, 0, -anchor.height - list.measuredHeight - style.dp(4), Gravity.START)
        }
    }
    private fun showOptions(anchor: View) {
        options?.dismiss()
        val list = style.column("ai/chat/options").apply {
            setPadding(style.dp(4), style.dp(4), style.dp(4), style.dp(4))
            background = style.shape(style.surface, outline = true, radius = 16)
        }
        fun option(label: Int, enabled: Boolean = true, textOverride: String? = null, action: () -> Unit) {
            list.addView(style.button(Button(context), outline = false).apply {
                text = textOverride ?: context.getString(label)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                setPadding(style.dp(16), style.dp(8), style.dp(16), style.dp(8))
                setSingleLine(false); maxLines = Int.MAX_VALUE; ellipsize = null
                isEnabled = enabled
                setOnClickListener { options?.dismiss(); action() }
            }, LinearLayout.LayoutParams(-1, -2))
        }
        option(R.string.ai_new) { onNewChat() }
        onModel?.let { action -> option(R.string.ai_model_title, textOverride = context.getString(R.string.ai_model_title, AiModelPolicy.known(currentModelId).label)) { action() } }
        onReasoning?.let { action -> option(R.string.ai_reasoning_title, !busy, context.getString(R.string.ai_reasoning_current, context.getString(AiReasoningPreference.read(context).label))) { action() } }
        onUsage?.let { show -> option(R.string.ai_usage_title) { show() } }
        option(R.string.ai_key_title, keyEnabled) { onKey() }
        val available = (anchor.parent as View).width - style.dp(24)
        options = PopupWindow(list, minOf(style.dp(208), available.coerceAtLeast(style.dp(48))), -2, true).apply {
            isOutsideTouchable = true
            elevation = style.dp(8).toFloat()
            setBackgroundDrawable(style.shape(Color.TRANSPARENT))
            setOnDismissListener { options = null }
            showAsDropDown(anchor, 0, style.dp(4), Gravity.END)
        }
    }
    private class MessageLinkMovement : LinkMovementMethod() {
        var copiedByLongPress = false
        override fun onTouchEvent(widget: TextView, buffer: android.text.Spannable, event: android.view.MotionEvent): Boolean {
            when (event.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> copiedByLongPress = false
                android.view.MotionEvent.ACTION_CANCEL -> copiedByLongPress = false
                android.view.MotionEvent.ACTION_UP -> if (copiedByLongPress) {
                    copiedByLongPress = false
                    android.text.Selection.removeSelection(buffer)
                    return true // A copied link must not also open on finger-up.
                }
            }
            return super.onTouchEvent(widget, buffer, event)
        }
    }
    private fun bindMessageCopy(view: View, body: () -> String) {
        view.setOnLongClickListener {
            val text = body()
            if (text.isBlank()) return@setOnLongClickListener false
            ((view as? TextView)?.movementMethod as? MessageLinkMovement)?.copiedByLongPress = true
            copyMessage(text)
            true
        }
    }
    private fun copyMessage(text: String) {
        if (text.isBlank()) return
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                context.getString(R.string.ai_title), text))
            // Android 13+ supplies its own copy confirmation.
            if (Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(
                context, R.string.ai_message_copied, android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: RuntimeException) {
            android.widget.Toast.makeText(context, R.string.ai_message_copy_failed,
                android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    private fun bubble(text: String, user: Boolean, images: List<AiAttachment> = emptyList()) {
        val row = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = View.MeasureSpec.getSize(widthMeasureSpec)
                val content = getChildAt(0) as LinearLayout
                (content.getChildAt(0) as TextView).maxWidth = minOf(style.dp(520), (width * .78f).toInt())
                content.minimumWidth = 0
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            tag = if (user) "ai/chat/user" else "ai/chat/assistant"
            gravity = if (user) Gravity.END else Gravity.START
        }
        val fill = if (user) style.primary else style.surface
        val message = style.text(text, TaskProductVisualStyle.body).apply {
            setTextColor(if (user) Color.WHITE else style.ink)
            visibility = if (text.isBlank()) View.GONE else View.VISIBLE
            if (!user) {
                val document = AiMarkdown.parse(text)
                setText(AiMarkdownSpans.style(document, style.accent, style.selected, onOpenLink))
                if (onOpenLink != null && document.marks.any { it.kind == AiMarkdown.Kind.LINK })
                    movementMethod = MessageLinkMovement()
            }
            contentDescription = context.getString(if (user) R.string.ai_user else R.string.ai_assistant) + ": " + this.text
            setHorizontallyScrolling(false)
            bindMessageCopy(this) { this.text.toString() }
        }
        val content = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val available = View.MeasureSpec.getSize(widthMeasureSpec)
                val limit = minOf(style.dp(520), (available * .78f).toInt()).coerceAtLeast(1)
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST), heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = style.shape(fill, radius = 18)
            setPadding(assistantTextInset, style.dp(9), assistantTextInset, style.dp(9))
        }
        // Padding belongs to this message, not the surrounding transcript or cards.
        if (text.isNotBlank() && images.isEmpty()) bindMessageCopy(content) { message.text.toString() }
        content.addView(message, LinearLayout.LayoutParams(-2, -2))
        if (images.isNotEmpty()) {
            val strip = style.row("ai/chat/message-images")
            images.forEach { item -> strip.addView(imagePreview(item, false), LinearLayout.LayoutParams(style.dp(240), style.dp(180)).apply {
                if (strip.childCount > 0) marginStart = style.dp(8)
            }) }
            content.addView(object : HorizontalScrollView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val available = View.MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
                    // A single image fits even a narrow bubble; only multiple images scroll.
                    for (index in 0 until strip.childCount)
                        strip.getChildAt(index).layoutParams.width = minOf(style.dp(240), available)
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                }
            }.apply {
                isHorizontalScrollBarEnabled = false
                addView(strip, FrameLayout.LayoutParams(-2, -2))
            }, LinearLayout.LayoutParams(-2, style.dp(180)).apply { if (text.isNotBlank()) topMargin = style.dp(8) })
        }
        row.addView(content, LinearLayout.LayoutParams(-2, -2))
        style.add(transcript, row, 10)
    }
    private var streamBaseStatus = ""
    private var streamExpanded = false
    private var streamSnapshot = AiStreamPreview.Snapshot()
    private val streamHost by lazy {
        style.column("ai/chat/stream-preview").apply {
            setPadding(assistantTextInset, 0, assistantTextInset, 0)
        }
    }
    private val thinkingBody by lazy {
        style.text("", TaskProductVisualStyle.auxiliary, "ai/chat/thinking-text", true).apply {
            // Untrusted provider text: no Markdown, URL spans, movement method or actions.
            setTextIsSelectable(true)
        }
    }
    private val thinkingToggle by lazy {
        style.button(Button(context), outline = false).apply {
            tag = "ai/chat/thinking-toggle"
            minHeight = style.dp(48); minimumHeight = style.dp(48)
            // The host supplies the same text inset as assistant bubbles.
            setPadding(0, style.dp(8), 0, style.dp(8))
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setOnClickListener { streamExpanded = !streamExpanded; updateStreamPreview(streamSnapshot) }
        }
    }
    private val partialAnswer by lazy {
        style.text("", TaskProductVisualStyle.body, "ai/chat/partial-answer").apply {
            // One long-press action, without selection menus or appended status labels.
            bindMessageCopy(this) { streamSnapshot.content }
        }
    }
    private val toolProgress by lazy { style.text(context.getString(R.string.ai_stream_tool), TaskProductVisualStyle.auxiliary, secondary = true) }
    fun resetStreamPreview() { streamExpanded = false; updateStreamPreview(AiStreamPreview.Snapshot()) }
    fun updateStreamPreview(snapshot: AiStreamPreview.Snapshot) {
        streamSnapshot = snapshot
        if (thinkingToggle.parent == null) {
            style.add(streamHost, thinkingToggle)
            style.add(streamHost, thinkingBody)
            style.add(streamHost, toolProgress)
            style.add(streamHost, partialAnswer)
        }
        val hasThinking = snapshot.reasoning.isNotBlank()
        thinkingToggle.visibility = if (hasThinking) View.VISIBLE else View.GONE
        thinkingToggle.text = context.getString(if (streamExpanded) R.string.ai_stream_thinking_hide else R.string.ai_stream_thinking_show)
        thinkingBody.text = snapshot.reasoning
        thinkingBody.visibility = if (hasThinking && streamExpanded) View.VISIBLE else View.GONE
        toolProgress.visibility = View.GONE
        val interrupted = snapshot.phase == AiStreamPreview.Phase.INTERRUPTED
        val showContent = (busy || interrupted) && snapshot.content.isNotBlank()
        partialAnswer.text = if (showContent) snapshot.content +
            (if (snapshot.contentTruncated) "\n\n" + context.getString(R.string.ai_stream_preview_truncated) else "") +
            (if (interrupted) "\n\n" + context.getString(R.string.ai_stream_incomplete) else "") else ""
        partialAnswer.visibility = if (showContent) View.VISIBLE else View.GONE
        streamHost.visibility = if (hasThinking || showContent || (busy && snapshot.toolStarted)) View.VISIBLE else View.GONE
        val phase = when {
            snapshot.phase == AiStreamPreview.Phase.TOOL -> R.string.ai_stream_tool
            snapshot.phase == AiStreamPreview.Phase.CONTENT -> R.string.ai_stream_answering
            snapshot.phase == AiStreamPreview.Phase.REASONING -> R.string.ai_stream_thinking
            else -> null
        }
        val text = if (busy && phase != null && streamBaseStatus.substringBefore('\n') == context.getString(R.string.ai_waiting)) {
            context.getString(phase) + streamBaseStatus.indexOf('\n').let { if (it < 0) "" else streamBaseStatus.substring(it) }
        } else streamBaseStatus
        updateStatus(text, busy)

    }
    private fun imagePreview(item: AiAttachment, draft: Boolean): FrameLayout {
        val frame = FrameLayout(context).apply {
            tag = if (draft) "ai/chat/draft-image" else "ai/chat/message-image"
            background = style.shape(style.surface, outline = true, radius = 12)
            clipToOutline = true
        }
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = context.getString(R.string.ai_image_preview_description)
        }
        val placeholder = style.text(context.getString(R.string.ai_image_preview_loading), TaskProductVisualStyle.auxiliary, secondary = true).apply {
            gravity = Gravity.CENTER
            setPadding(style.dp(4), 0, style.dp(4), 0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        frame.addView(image, FrameLayout.LayoutParams(-1, -1))
        frame.addView(placeholder, FrameLayout.LayoutParams(-1, -1))
        if (imagePreviews == null) placeholder.setText(R.string.ai_image_preview_unavailable)
        imagePreviews?.bind(item, image, placeholder)
        return frame
    }
    private fun renderDraftImages(items: List<AiAttachment>, enabled: Boolean) {
        draftImages.removeAllViews()
        items.filter { it.kind == AiAttachmentKind.IMAGE }.forEach { item ->
            val tile = style.row("ai/chat/draft-image-tile")
            tile.addView(imagePreview(item, true), LinearLayout.LayoutParams(style.dp(72), style.dp(72)))
            tile.addView(style.button(Button(context), outline = false).apply {
                text = "×"
                contentDescription = context.getString(R.string.ai_attachment_remove, item.name)
                isEnabled = enabled && onRemoveAttachment != null
                setPadding(0, 0, 0, 0)
                setOnClickListener { onRemoveAttachment?.invoke(item) }
            }, LinearLayout.LayoutParams(style.dp(48), style.dp(72)))
            draftImages.addView(tile, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = style.dp(8) })
        }
        draftImageScroll.visibility = if (draftImages.childCount == 0) View.GONE else View.VISIBLE
    }
    private data class CardActions(val open: Boolean, val review: Boolean, val library: Boolean)
    private data class ContentRender(
        val lines: List<AiChatLine>,
        val busy: Boolean,
        val pendingDraft: String?,
        val attachments: List<AiAttachment>,
        val draftImagesEnabled: Boolean,
        val ownerEpoch: String?,
        val previewSession: String?,
        val cardActions: List<CardActions>,
    )

    private var renderedContent: ContentRender? = null

    private fun renderContent(state: ContentRender) {
        val (lines, busy, draft, attachments, draftImagesEnabled, ownerEpoch, previewSession) = state
        val cardActions = state.cardActions.iterator()
        // Synthetic offline shells pass no owner, so they never open storage or create a loader.
        if (ownerEpoch != null && imagePreviews == null) imagePreviews = AiImagePreviews(context, root)
        imagePreviews?.begin(ownerEpoch, previewSession, lines.flatMap { it.attachments } + attachments)
        transcript.removeAllViews()
        if (lines.isEmpty() && !busy) style.add(transcript, style.text(context.getString(R.string.ai_empty), TaskProductVisualStyle.body, secondary = true).apply {
            gravity = Gravity.CENTER; setPadding(style.dp(12), style.dp(32), style.dp(12), style.dp(16))
        })
        lines.forEach { line ->
            bubble(AiImagePreviewPolicy.visibleText(line.text, line.attachments), line.user,
                line.attachments.filter { it.kind == AiAttachmentKind.IMAGE })
            if (!line.user) line.curveCards.forEach { card ->
                val actions = cardActions.next()
                val cardView = AiCurveCardView(context, card, style,
                    context.resources.configuration.locales[0].language != "zh",
                    onOpen = onOpenCurve?.let { callback -> { callback(card) } },
                    onReview = if (actions.review) onReviewCurve?.let { callback -> { callback(card) } } else null,
                    referenceAvailable = actions.open,
                    onViewCurve = onViewCurve?.let { callback -> { callback(card) } },
                    libraryReferenceAvailable = actions.library)
                val cardHost = object : FrameLayout(context) {
                    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                        val available = View.MeasureSpec.getSize(widthMeasureSpec)
                        getChildAt(0).layoutParams.width = if (card.kind == AiCurveCard.Kind.RECOMMENDATION)
                            AiCurveCardLayoutPolicy.recommendationWidth(available, style.dp(400))
                        else AiCurveCardLayoutPolicy.cardWidth(available, style.dp(520))
                        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    }
                }
                cardHost.addView(cardView, FrameLayout.LayoutParams(-2, -2, Gravity.START))
                style.add(transcript, cardHost, 8)
            }
        }
        if (busy) bubble(AiImagePreviewPolicy.visibleText(requireNotNull(draft), attachments), true,
            attachments.filter { it.kind == AiAttachmentKind.IMAGE })
        style.add(transcript, streamHost, 8)
        style.add(transcript, status, 12)
        renderDraftImages(if (busy) emptyList() else attachments, draftImagesEnabled)
    }

    fun render(lines: List<AiChatLine>, draft: String, busy: Boolean, statusText: String, attachments: List<AiAttachment> = emptyList(), attachmentBusy: Boolean = false, modelId: String = AiModelPolicy.FLASH, ownerEpoch: String? = null, previewSession: String? = null) {
        currentModelId = AiModelPolicy.known(modelId).id
        this.busy = busy
        val cardActions = lines.filterNot { it.user }.flatMap { line ->
            line.curveCards.map { CardActions(curveAvailable(it), !busy && curveReviewable(it), curveLibraryAvailable(it)) }
        }
        // A status refresh must not detach the view that owns an in-flight long press.
        // Snapshot the mutable host lists; actual content, ownership or card action changes still rebuild.
        val content = ContentRender(lines.toList(), busy, draft.takeIf { busy }, attachments.toList(),
            !busy && !attachmentBusy, ownerEpoch, previewSession, cardActions)
        if (content != renderedContent) {
            renderContent(content)
            renderedContent = content
        }
        val files = attachments.filter { it.kind != AiAttachmentKind.IMAGE }
        draftFiles.removeAllViews()
        files.forEach { item ->
            val row = style.row("ai/chat/draft-file")
            row.gravity = Gravity.CENTER_VERTICAL
            row.addView(style.text(item.name, TaskProductVisualStyle.auxiliary), LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(style.button(Button(context)).apply {
                text = "×"
                contentDescription = context.getString(R.string.ai_attachment_remove, item.name)
                isEnabled = !busy && !attachmentBusy && onRemoveAttachment != null
                setPadding(0, 0, 0, 0)
                setOnClickListener { onRemoveAttachment?.invoke(item) }
            }, LinearLayout.LayoutParams(style.dp(48), style.dp(48)))
            draftFiles.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        draftFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
        attachmentButton?.isEnabled = !busy && !attachmentBusy
        streamBaseStatus = statusText
        updateStreamPreview(streamSnapshot)
        input.isEnabled = !busy && !attachmentBusy
        send.isEnabled = !attachmentBusy
        send.contentDescription = context.getString(if (busy) R.string.ai_stop else R.string.ai_send)
        send.invalidate()
    }
}

private class ChatActionIcon(private val stop: Boolean, private val size: Int) : Drawable() {
    private val arrow = Path().apply {
        moveTo(10f, 17f); lineTo(10f, 3f)
        moveTo(4f, 9f); lineTo(10f, 3f); lineTo(16f, 9f)
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2.2f
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    override fun getIntrinsicWidth() = size
    override fun getIntrinsicHeight() = size
    override fun draw(canvas: Canvas) {
        canvas.save()
        canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        canvas.scale(bounds.width() / 20f, bounds.height() / 20f)
        if (stop) {
            paint.style = Paint.Style.FILL
            canvas.drawRoundRect(5f, 5f, 15f, 15f, 2f, 2f, paint)
        } else {
            paint.style = Paint.Style.STROKE
            canvas.drawPath(arrow, paint)
        }
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Drawable opacity") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}

private class ChatPlusIcon(color: Int, private val size: Int) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color; strokeWidth = size / 11f; strokeCap = Paint.Cap.ROUND
    }
    override fun draw(canvas: Canvas) {
        val x = bounds.exactCenterX(); val y = bounds.exactCenterY(); val half = size * .36f
        canvas.drawLine(x - half, y, x + half, y, paint)
        canvas.drawLine(x, y - half, x, y + half, paint)
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(filter: android.graphics.ColorFilter?) { paint.colorFilter = filter }
    @Deprecated("Deprecated in Java") override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
}
