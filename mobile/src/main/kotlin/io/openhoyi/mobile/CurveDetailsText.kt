package io.openhoyi.mobile

import android.content.Context
import java.util.Locale

/** Displays immutable metadata; it does not decide eligibility or construct machine commands. */
internal class CurveDetailsText(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: Context) : this({ id, args -> context.getString(id, *args) })

    fun render(item: CurveLibraryItem, factoryProofAvailable: Boolean): String {
        item.controlProfile?.let { profile ->
            return text(R.string.curve_details_captured, profile.endMode,
                profile.temperatureC.toString(), profile.maximumWaterMl.toString(),
                profile.parameters.segmentCount.toString(), weight(profile.targetHundredthsGram, 100.0))
        }
        item.factoryCurve?.let { curve ->
            return text(R.string.curve_details_factory,
                text(if (factoryProofAvailable) R.string.curve_details_proof_available else R.string.curve_details_proof_missing),
                curve.temperatureC.toString(), curve.flowMl.toString(), curve.segmentCount.toString(),
                weight(curve.weightTenthsGram, 10.0),
                curve.targets.take(curve.segmentCount).joinToString(" / "),
                curve.segmentFlowMl.take(curve.segmentCount).joinToString(" / "), curve.tips)
        }
        return item.details
    }

    private fun weight(raw: Int, divisor: Double): String = if (raw == 0)
        text(R.string.curve_details_no_weight) else String.format(Locale.ROOT, "%.1f g", raw / divisor)

    private fun text(id: Int, vararg args: Any): String = resolve(id, args)
}
