package io.openhoyi.mobile

import android.text.TextUtils
import android.view.View
import android.widget.TextView

/** Small plain text above the CTA; no new card, border, control or independent font scale. */
internal class PendingRecipeLabel(style: TaskProductVisualStyle) : TextView(style.context) {
    init {
        style.apply(this, TaskProductVisualStyle.Type(12, 400, 18), true)
        tag = "product/extraction/pending-recipe"
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    fun bind(name: String?) {
        val label = name?.let { context.getString(R.string.extraction_pending_recipe, it) }.orEmpty()
        if (text.toString() != label) text = label
        contentDescription = label
        visibility = if (name == null) View.GONE else View.VISIBLE
    }
}
