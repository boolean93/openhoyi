package io.openhoyi.mobile

/** Field-specific draft errors. Preview validity never gates unrelated valid stage targets. */
internal object CurveEditorSettings {
    enum class Error { BLOOM, DURATION, EXIT, COMPENSATION, COMPENSATION_TARGET, TOTAL }
    fun compensation(text: String): Int {
        val trimmed = text.trim()
        return if (trimmed.startsWith("-")) -CurveDraftNumber.parse(trimmed.drop(1), 2) else CurveDraftNumber.parse(trimmed, 2)
    }
    fun errors(bloom: String, duration: String, compensation: String, total: String,
        totalWaterTenths: Int?, flow: Boolean, firstWaterTenths: Int?, targetWeight: String): Map<String, Error?> {
        fun number(text: String, decimals: Int) = runCatching { CurveDraftNumber.parse(text, decimals) }.getOrNull()
        val bloomValue = number(bloom, 0)
        val durationValue = number(duration, 0)
        val compensationValue = runCatching { this.compensation(compensation) }.getOrNull()
        val target = number(targetWeight, 2)
        val totalValue = number(total, 0)
        val maximum = maxOf(((totalWaterTenths ?: 0) + 9) / 10, 800)
        return linkedMapOf(
            "preinfusion" to if (bloomValue == null || bloomValue !in 0..127) Error.BLOOM else null,
            "first-duration" to when {
                durationValue == null || durationValue !in 0..254 -> Error.DURATION
                flow && durationValue == 0 && firstWaterTenths == 0 -> Error.EXIT
                else -> null
            },
            "compensation" to when {
                compensationValue == null || compensationValue !in -10000..10000 -> Error.COMPENSATION
                target != null && target > 0 && compensationValue >= target -> Error.COMPENSATION_TARGET
                else -> null
            },
            "total-water-limit" to if (total.isBlank() || totalValue != null && totalValue > 0 && (totalWaterTenths == null || totalValue <= maximum)) null else Error.TOTAL)
    }
}
