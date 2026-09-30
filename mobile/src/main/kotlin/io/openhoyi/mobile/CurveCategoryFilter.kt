package io.openhoyi.mobile

import android.content.Context

/** Stable UI filter identity. Legacy keys still match unmodified library data, never translated labels. */
enum class CurveCategoryFilter(val legacyKey: String, private val labelResource: Int) {
    ALL("全部",R.string.curve_category_all),
    CAPTURED("已采集验证",R.string.curve_category_captured),
    DARK("深烘",R.string.curve_category_dark),
    MEDIUM("中烘",R.string.curve_category_medium),
    LIGHT("浅烘",R.string.curve_category_light),
    SUPER("超萃",R.string.curve_category_super);

    fun label(context: Context): String = context.getString(labelResource)

    companion object {
        fun restore(saved: String?): CurveCategoryFilter =
            entries.firstOrNull { it.name == saved || it.legacyKey == saved } ?: ALL

        fun display(context: Context, key: String): String = entries.firstOrNull { it.legacyKey == key }
            ?.label(context) ?: if (key == "其它") context.getString(R.string.curve_category_other) else key
    }
}
