package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation

/** Temperature evidence is presentation only, never a device acknowledgement or start grant. */
internal object AutoPreheatPresentation {
    enum class Phase { HIDDEN, SENDING, WAITING, COOLING, TEMPERATURE_MISSING, FAILED, UNKNOWN, DISCONNECTED, CANCELLING, CANCEL_WRITTEN, TARGET_CHANGED, NOT_READY }
    data class Facts(
        val studio: Boolean,
        val connected: Boolean,
        val state: BrewPreparation.State,
        val profileId: String?,
        val targetC: Int?,
        val preparationProfileId: String?,
        val preparationTargetC: Int?,
        val current: Int?,
        val initial: Int?,
    )
    data class Model(val phase: Phase, val progress: Float? = null)
    /** A lost settings frame must not erase an existing automatic preparation notice. */
    fun showStudioStatus(settingsFlags: Int?, hasAutoSession: Boolean, message: String?): Boolean =
        if (settingsFlags != null) settingsFlags and 0x04 == 0x04
        else hasAutoSession || !message.isNullOrBlank()

    fun describe(f: Facts): Model {
        if (!f.studio || f.targetC == null || f.profileId == null) return Model(Phase.HIDDEN)
        if (!f.connected) return Model(Phase.DISCONNECTED)
        val phase = when (f.state) {
            BrewPreparation.State.WRITING -> Phase.SENDING
            BrewPreparation.State.CANCELLING -> Phase.CANCELLING
            BrewPreparation.State.CANCEL_WRITTEN -> Phase.CANCEL_WRITTEN
            BrewPreparation.State.FAILED -> Phase.FAILED
            BrewPreparation.State.UNKNOWN -> Phase.UNKNOWN
            else -> null
        }
        if (phase != null) return Model(phase)
        if (f.state != BrewPreparation.State.IDLE &&
            (f.preparationProfileId != f.profileId || f.preparationTargetC != f.targetC)) return Model(Phase.TARGET_CHANGED)
        val current = f.current ?: return Model(Phase.TEMPERATURE_MISSING)
        if (BrewPreparation.isAtTarget(current, f.targetC)) return Model(Phase.HIDDEN)
        val cooling = current > f.targetC * 100 + 100
        if (f.state == BrewPreparation.State.IDLE) return Model(if(cooling) Phase.COOLING else Phase.NOT_READY)
        val initial = f.initial
        val target = f.targetC * 100
        // A changed direction or absent baseline cannot support a numeric progress claim.
        val progress = if (initial != null && initial != target &&
            (initial - target).toLong() * (current - target) > 0)
            ((current - initial).toFloat() / (target - initial)).coerceIn(0f, .98f) else null
        return Model(if(cooling) Phase.COOLING else Phase.WAITING, progress)
    }
}
