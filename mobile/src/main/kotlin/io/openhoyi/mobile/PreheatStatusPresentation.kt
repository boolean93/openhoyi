package io.openhoyi.mobile

import io.openhoyi.session.BrewPreparation

/** Display only. Normal preheat still owns its exclusive control intent, even when at target. */
internal object PreheatStatusPresentation {
    fun normal(state:BrewPreparation.State):Boolean = state in setOf(
        BrewPreparation.State.WRITING,BrewPreparation.State.WAITING_TEMP,BrewPreparation.State.READY)
    fun abnormal(state:BrewPreparation.State):Boolean = state!=BrewPreparation.State.IDLE && !normal(state)
    fun boilerOccupied(state:BrewPreparation.State,connectedReady:Boolean,freshSafeAwakeIdle:Boolean,
        anotherBlock:Boolean):Boolean = normal(state) && connectedReady && freshSafeAwakeIdle && !anotherBlock
}
