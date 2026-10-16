package io.openhoyi.mobile

import android.content.Context
import android.graphics.Typeface

/** Fixed Chinese glyphs/weights; other languages keep their existing system fallback. */
internal object TaskTypography {
    private val chinese = mutableMapOf<Int, Typeface>()
    @Synchronized fun face(context: Context, weight: Int = 400): Typeface {
        require(weight in setOf(300, 400, 500, 700))
        if (context.resources.configuration.locales[0].language != "zh") return Typeface.create(
            when (weight) { 300 -> "sans-serif-light"; 500 -> "sans-serif-medium"; else -> "sans-serif" },
            if (weight == 700) Typeface.BOLD else Typeface.NORMAL)
        return chinese.getOrPut(weight) { context.resources.getFont(when (weight) {
            300 -> R.font.task_chinese_light
            500 -> R.font.task_chinese_medium
            700 -> R.font.task_chinese_bold
            else -> R.font.task_chinese_regular
        }) }
    }
}
