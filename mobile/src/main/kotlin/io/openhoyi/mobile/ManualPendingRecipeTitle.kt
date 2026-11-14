package io.openhoyi.mobile

import android.text.TextUtils
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Separate one-line labels keep a long observed title from swallowing the pending recipe. */
internal class ManualPendingRecipeTitle(private val title: TextView, style: TaskProductVisualStyle) : LinearLayout(title.context) {
    private val pending = style.text("", TaskProductVisualStyle.auxiliary,
        "product/extraction/pending-recipe", true).apply {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        visibility = View.GONE
    }
    init {
        orientation = VERTICAL
        addView(title, LayoutParams(-1, -2))
        addView(pending, LayoutParams(-1, -2))
    }
    fun show(name: String, pendingName: String?) {
        if (title.text.toString() != name) title.text = name
        title.contentDescription = name
        val label = pendingName?.let { context.getString(R.string.extraction_pending_recipe, it) }
        if (pending.text.toString() != label.orEmpty()) pending.text = label.orEmpty()
        pending.contentDescription = label
        pending.visibility = if (label == null) View.GONE else View.VISIBLE
    }
}
