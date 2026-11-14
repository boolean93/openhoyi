package io.openhoyi.mobile

/** A draft is not a saved document. Invalid slots stay visible, never become zero or old data. */
internal object CurveEditorPreview {
    enum class Error { REQUIRED, NUMBER, RANGE }
    data class Stage(val target: Int?, val water: Int?, val flow: Boolean, val targetRange: IntRange, val waterRange: IntRange,
        val targetError: Error?, val waterError: Error?)
    data class Model(val stages: List<Stage>) {
        val invalid get() = stages.any { it.targetError != null || it.waterError != null }
        val totalWater: Int? get() = if (stages.any { it.water == null }) null else stages.sumOf { requireNotNull(it.water) }
    }
    private fun quantity(text: String, range: IntRange): Pair<Int?, Error?> {
        if (text.isBlank()) return null to Error.REQUIRED
        val number = runCatching { CurveDraftNumber.parse(text, 1) }.getOrNull() ?: return null to Error.NUMBER
        return if (number in range) number to null else null to Error.RANGE
    }
    fun parse(raw: List<Pair<String, String>>, flow: Boolean, firstFlow: Boolean,
        complete: Boolean = flow): Model = Model(raw.take(4).mapIndexed { index, (target, water) ->
        val isFlow = flow && (index > 0 || firstFlow)
        val range = when { isFlow -> 10..100; flow -> 5..100; complete && index == 0 -> 5..120; complete -> 10..120; else -> 0..120 }
        val targetValue = quantity(target, range)
        val waterRange = (if (flow && index == 0) 0 else 1)..(if (complete) 9990 else 65535)
        val waterValue = quantity(water, waterRange)
        Stage(targetValue.first, waterValue.first, isFlow, range, waterRange, targetValue.second, waterValue.second)
    })
}
