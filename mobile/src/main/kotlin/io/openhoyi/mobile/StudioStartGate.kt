package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation
import io.openhoyi.session.PreheatGate
import io.openhoyi.protocol.Settings

/** Maps shared studio decisions to product text and curve identifiers. */
class StudioStartGate(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })
    private fun text(id: Int): String = resolve(id, emptyArray())

    fun block(settings: Settings?, correctedHundredthsC: Int?, profile: CurveProfile,
        preparation: BrewPreparation): String? = when (PreheatGate.startBlock(
            settings, correctedHundredthsC, profile.id, profile.temperatureC, preparation)) {
        PreheatGate.StartBlock.SETTINGS_MISSING -> text(R.string.studio_block_settings_missing)
        PreheatGate.StartBlock.MODE_CHANGED -> text(R.string.studio_block_mode_changed)
        PreheatGate.StartBlock.PREPARATION_MISMATCH -> text(R.string.studio_block_preparation_mismatch)
        PreheatGate.StartBlock.TEMPERATURE_NOT_READY -> text(R.string.studio_block_temperature_not_ready)
        null -> null
    }
}
