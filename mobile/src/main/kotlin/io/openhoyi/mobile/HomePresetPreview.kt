package io.openhoyi.mobile

internal data class HomePresetPreview(
    val preview: CurveStageParameters.Preview,
    val scale: CurveStageParameters.SharedScale,
) {
    companion object {
        fun from(item: CurveLibraryItem?, scale: CurveStageParameters.SharedScale): HomePresetPreview? {
            val preview = item?.let(CurveStageParameters::preview) ?: return null
            return if (preview.renderable) HomePresetPreview(preview, scale) else null
        }
    }
}
