package io.openhoyi.mobile

import android.os.Bundle
import androidx.fragment.app.DialogFragment

/** Compatibility only: Android may restore an old saved Fragment after an app upgrade. */
class ManualLiveDialog : DialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showsDialog = false
    }

    override fun onStart() {
        super.onStart()
        dismissAllowingStateLoss()
    }
}
