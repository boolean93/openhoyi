package io.openhoyi.mobile

import android.content.Context

/** Ordinal stage identity, separate from the live chart's physical-quantity palette. */
internal object CurveStagePalette {
    private val colors = listOf(R.color.stage_one, R.color.stage_two, R.color.stage_three, R.color.stage_four)
    fun color(context: Context, index: Int): Int = context.getColor(colors[index])
}
