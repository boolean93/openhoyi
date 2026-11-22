package io.openhoyi.mobile

import android.widget.LinearLayout

/** Normal variants have no QA action, transport runner or fixture. */
internal object MockBleSameIdQaRow {
    @Suppress("UNUSED_PARAMETER")
    fun attach(activity: MainActivity, card: LinearLayout, style: TaskProductVisualStyle) = Unit
}
