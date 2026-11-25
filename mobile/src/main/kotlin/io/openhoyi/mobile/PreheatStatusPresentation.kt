package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation

/** Display only. Normal preheat still owns its exclusive control intent, even when at target. */
internal object PreheatStatusPresentation {
    fun waitingLabel(correctedHundredthsC: Int?, targetC: Int?): Int = when {
        correctedHundredthsC == null -> R.string.extraction_prepare_waiting
        targetC != null && correctedHundredthsC > targetC * 100 + 100 -> R.string.auto_preheat_cooling
        else -> R.string.task_shell_machine_preheating
    }

    fun normal(state:BrewPreparation.State):Boolean = state in setOf(
        BrewPreparation.State.WRITING,BrewPreparation.State.WAITING_TEMP,BrewPreparation.State.READY)
    fun abnormal(state:BrewPreparation.State):Boolean = state!=BrewPreparation.State.IDLE && !normal(state)
    fun boilerOccupied(state:BrewPreparation.State,connectedReady:Boolean,freshSafeAwakeIdle:Boolean,
        anotherBlock:Boolean):Boolean = normal(state) && connectedReady && freshSafeAwakeIdle && !anotherBlock
}
