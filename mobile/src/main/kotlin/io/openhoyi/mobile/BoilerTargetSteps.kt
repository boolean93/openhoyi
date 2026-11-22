package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.session.SettingsWriteTracker

/** A Settings-only queue: one transport/readback owner, with only unsent intents coalesced. */
internal class BoilerTargetSteps {
    enum class Boiler(val limits: IntRange) {
        BREW(75..105), STEAM(110..145);
        fun change(value: Int): MachineSettingChange = when (this) {
            BREW -> MachineSettingChange.BrewTemperature(value)
            STEAM -> MachineSettingChange.SteamTemperature(value)
        }
    }
    data class Request(val boiler: Boiler, val value: Int)
    enum class Feedback { QUEUED, CONFIRMING, FAILED, UNKNOWN, UNKNOWN_WITH_UNSENT, FAILED_WITH_UNSENT, NOT_SENT }
    private var session: Any? = null
    private var uncertain = false
    private var refreshedAfterUnknown = false
    private val queued = linkedMapOf<Boiler, Int>()
    private val notices = mutableMapOf<Boiler, Feedback>()
    var inFlight: Request? = null
        private set
    val active get() = inFlight != null || queued.isNotEmpty()
    /** Do not supersede a compensating or standby request's legitimate observer. */
    fun acceptsAlongsideTemperature(state: SettingsWriteTracker.State): Boolean =
        !needsReadback(state) && (active || state != SettingsWriteTracker.State.WAITING_READBACK)
    fun needsReadback(state: SettingsWriteTracker.State): Boolean =
        uncertain || (!active && state == SettingsWriteTracker.State.UNKNOWN && !refreshedAfterUnknown)
    fun proveNewBaseline(key: Any, serialBefore: Long, serialAfter: Long): Boolean {
        if (active || key != session || serialAfter <= serialBefore) return false
        uncertain = false
        refreshedAfterUnknown = true
        return true
    }
    fun invalidateProof() { refreshedAfterUnknown = false }
    fun sessionChanged(key: Any?) {
        if (key == session) return
        if (active) stop(Feedback.UNKNOWN) else if (key != null && !uncertain) notices.clear()
        invalidateProof()
        session = key
    }
    fun target(boiler: Boiler, observed: Int?): Int? =
        queued[boiler] ?: inFlight?.takeIf { it.boiler == boiler }?.value ?: observed
    fun feedback(boiler: Boiler): Feedback? =
        if (queued.containsKey(boiler)) Feedback.QUEUED
        else if (inFlight?.boiler == boiler) Feedback.CONFIRMING else notices[boiler]

    fun step(boiler: Boiler, delta: Int, observed: Int?): Boolean {
        if (session == null || uncertain || delta !in listOf(-1, 1)) return false
        val value = target(boiler, observed)?.takeIf { it in boiler.limits }?.plus(delta)?.takeIf { it in boiler.limits } ?: return false
        notices.remove(boiler)
        queued[boiler] = value
        return true
    }
    fun next(): Request? {
        if (inFlight != null) return null
        val next = queued.entries.firstOrNull()?.let { Request(it.key, it.value) } ?: return null
        queued.remove(next.boiler)
        inFlight = next
        return next
    }
    fun observe(change: MachineSettingChange?, state: SettingsWriteTracker.State) {
        val request = inFlight ?: return
        if (request.boiler.change(request.value) != change) {
            stop(Feedback.UNKNOWN)
            return
        }
        when (state) {
            SettingsWriteTracker.State.CONFIRMED -> inFlight = null
            SettingsWriteTracker.State.FAILED -> stop(Feedback.FAILED)
            SettingsWriteTracker.State.UNKNOWN, SettingsWriteTracker.State.RECONCILED -> stop(Feedback.UNKNOWN)
            // IDLE after the same initiated operation is not transport evidence.
            SettingsWriteTracker.State.IDLE -> stop(Feedback.UNKNOWN)
            else -> Unit
        }
    }
    fun stop(reason: Feedback) {
        queued.keys.forEach { notices[it] = Feedback.NOT_SENT }
        if (reason == Feedback.UNKNOWN) { uncertain = true; invalidateProof() }
        inFlight?.let {
            notices[it.boiler] = when {
                it.boiler in queued && reason == Feedback.UNKNOWN -> Feedback.UNKNOWN_WITH_UNSENT
                it.boiler in queued && reason == Feedback.FAILED -> Feedback.FAILED_WITH_UNSENT
                else -> reason
            }
        }
        inFlight = null
        queued.clear()
    }
}
