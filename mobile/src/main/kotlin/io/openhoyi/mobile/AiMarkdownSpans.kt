package io.openhoyi.mobile

import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ClickableSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.UnderlineSpan
import android.graphics.Typeface
import android.view.View

/** Android styling over the pure, no-HTML/no-image Markdown document. */
internal object AiMarkdownSpans {
    fun style(document: AiMarkdown.Document, accent: Int, codeSurface: Int,
        openLink: ((String) -> Unit)?): SpannableStringBuilder = SpannableStringBuilder(document.text).apply {
        document.marks.forEach { mark ->
            if (mark.start < 0 || mark.end > length || mark.start >= mark.end) return@forEach
            fun span(value: Any) = setSpan(value, mark.start, mark.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            when (mark.kind) {
                AiMarkdown.Kind.HEADING -> {
                    span(StyleSpan(Typeface.BOLD))
                    span(RelativeSizeSpan(when (mark.value) { "1" -> 1.25f; "2" -> 1.15f; else -> 1.07f }))
                }
                AiMarkdown.Kind.BOLD -> span(StyleSpan(Typeface.BOLD))
                AiMarkdown.Kind.CODE, AiMarkdown.Kind.CODE_BLOCK -> {
                    span(TypefaceSpan("monospace"))
                    span(BackgroundColorSpan(codeSurface))
                }
                AiMarkdown.Kind.LINK -> {
                    span(UnderlineSpan())
                    mark.value?.takeIf(AiMarkdown::safeHttpUrl)?.let { url ->
                        val opener = openLink
                        if (opener != null) span(object : ClickableSpan() {
                            override fun onClick(widget: View) = opener(url)
                            override fun updateDrawState(ds: TextPaint) { ds.color = accent; ds.isUnderlineText = true }
                        })
                    }
                }
            }
        }
    }
}
