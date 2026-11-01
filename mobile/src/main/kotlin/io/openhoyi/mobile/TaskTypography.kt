package io.openhoyi.mobile

import android.content.Context
import android.graphics.Typeface
import android.os.Build

/** Fixed Chinese glyphs/weights; other languages keep their existing system fallback. */
internal object TaskTypography {
    private val chinese = mutableMapOf<Int, Typeface>()
    private var chineseFamily: Typeface? = null
    @Synchronized fun face(context: Context, weight: Int = 400): Typeface {
        require(weight in setOf(300, 400, 500, 700))
        if (context.resources.configuration.locales[0].language != "zh") return Typeface.create(
            when (weight) { 300 -> "sans-serif-light"; 500 -> "sans-serif-medium"; else -> "sans-serif" },
            if (weight == 700) Typeface.BOLD else Typeface.NORMAL)
        return chinese.getOrPut(weight) {
            if (Build.VERSION.SDK_INT >= 28) {
                // Reuse the same resource family as the theme. Loading each weight XML
                // independently duplicates buffers for the 17 MB variable font.
                val family = chineseFamily ?: context.resources.getFont(R.font.task_chinese_family)
                    .also { chineseFamily = it }
                Typeface.create(family, weight, false)
            } else context.resources.getFont(when (weight) {
                // API 26–27 has no exact-weight Typeface.create overload.
                300 -> R.font.task_chinese_light
                500 -> R.font.task_chinese_medium
                700 -> R.font.task_chinese_bold
                else -> R.font.task_chinese_regular
            })
        }
    }
}
