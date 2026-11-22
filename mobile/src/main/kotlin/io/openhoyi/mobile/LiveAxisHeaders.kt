package io.openhoyi.mobile

/** Whole name/unit pairs, anchored to their tick columns when space allows. */
internal object LiveAxisHeaders {
    data class Caption(val center: Float, val width: Float)
    data class Placement(val center: Float, val band: Int)

    fun pack(captions: List<Caption>, left: Float, right: Float, gap: Float): List<Placement> {
        if (captions.isEmpty()) return emptyList()
        require(right >= left && gap >= 0)
        val result = mutableListOf<Placement>()
        val available = right - left
        var from = 0
        var band = 0
        while (from < captions.size) {
            var until = from + 1
            var used = captions[from].width
            while (until < captions.size && used + gap + captions[until].width <= available) {
                used += gap + captions[until].width
                until++
            }
            val row = captions.subList(from, until)
            val starts = FloatArray(row.size)
            var next = right
            for (i in row.indices.reversed()) {
                val item = row[i]
                starts[i] = minOf(item.center - item.width / 2, next - item.width)
                next = starts[i] - gap
            }
            next = left
            row.forEachIndexed { i, item ->
                val start = maxOf(next, starts[i])
                result += Placement(start + item.width / 2, band)
                next = start + item.width + gap
            }
            from = until
            band++
        }
        return result
    }
}
