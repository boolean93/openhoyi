package io.openhoyi.mobile

/** TextView hierarchy only. SP conversion and chart Canvas typography stay untouched. */
internal object PortraitTypography {
    fun resolve(size: Int, line: Int, weight: Int, portrait: Boolean, narrow: Boolean): Pair<Int, Int> {
        if (!portrait) return size to line
        return when (size to line) {
            24 to 36 -> if (narrow) 20 to 28 else 22 to 30
            20 to 30 -> if (narrow) 18 to 26 else if (weight >= 500) 19 to 28 else 20 to 28
            40 to 60 -> if (narrow) 30 to 38 else 32 to 42
            48 to 72 -> if (narrow) 32 to 40 else 36 to 46
            64 to 96 -> if (narrow) 40 to 52 else 44 to 58
            22 to 34 -> if (narrow) 16 to 24 else 18 to 26
            else -> size to line
        }
    }
}
