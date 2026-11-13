package io.openhoyi.mobile

import android.content.res.Resources
import android.os.Build
import android.util.TypedValue

/** One conversion path for text, including Android 14 nonlinear font scaling. Never for geometry. */
internal object AppTextUnits {
    fun pixels(resources: Resources, sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)
    fun sp(resources: Resources, pixels: Float): Float =
        if (Build.VERSION.SDK_INT >= 34) TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, pixels, resources.displayMetrics)
        else pixels / resources.displayMetrics.scaledDensity
}
