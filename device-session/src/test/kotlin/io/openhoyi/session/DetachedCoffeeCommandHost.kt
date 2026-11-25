package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.WeeklySleepSchedule

/**
 * Detached acceptance host only. No production or normal Mock class links this coordinator.
 * Commands still go through the selected DeviceSession and its one real GATT queue.
 */
internal class DetachedCoffeeCommandHost(
    private val optIn: Boolean,
    private val session: DeviceSession,
    private val journal: SplitMachineWriteJournal,
    private val clock: () -> Long,
    private val activeLeaseAndSafeEnvironment: () -> Boolean,
) {
    enum class Phase { IDLE, WRITING, WAITING, OBSERVED, NOT_SENT, UNKNOWN }
    private class Slot(val owner: SplitMachineWriteJournal.Ownership, val source: CoffeeReadbackSource,
        val startedAtMs: Long) {
        var phase = Phase.WRITING
        var passedDispatchGuard = false
        var weeklyEvidence: CoffeeReadbackEvidence.Weekly? = null
        var boilerEvidence: CoffeeReadbackEvidence.Boiler? = null
    }

    private var schedule: Slot? = null
    private var boiler: Slot? = null
    private var revoked = !optIn || journal.pending != SplitMachineWriteJournal.Snapshot.Empty
    val schedulePhase get() = schedule?.phase ?: Phase.IDLE
    val boilerPhase get() = boiler?.phase ?: Phase.IDLE

    fun requestWeekly(expected: WeeklySleepSchedule, target: WeeklySleepSchedule): Boolean {
        poll()
        if (schedule != null || expected.days == target.days) return false
        val slot = reserve(SplitMachineWriteJournal.Kind.SCHEDULE) ?: return false
        schedule = slot
        session.writeSleepSchedule(target, expected, beforeDispatch = {
            permit(slot) && slot.phase == Phase.WRITING && schedule === slot && boiler == null
                && run { slot.passedDispatchGuard = true; true }
        }) { result ->
            transportResult(slot, result) { cut -> slot.weeklyEvidence = CoffeeReadbackEvidence.Weekly(target, cut); true }
        }
        return slot.phase != Phase.NOT_SENT && slot.phase != Phase.UNKNOWN
    }

    /** Only one experimental target: brew heating OFF. No steam/temperature/other action. */
    fun requestBrewHeatOff(): Boolean {
        poll()
        if (schedulePhase != Phase.WAITING || boiler != null) return false
        val target = MachineSettingChange.BrewHeating(false)
        val latest = session.latestCoffeeReadbackFrame as? CoffeeReadbackFrame.OrdinarySettings ?: return false
        if (target.matches(latest.value) || !SettingsFreshness.isFresh(latest.receivedAtMs, clock())) return false
        val slot = reserve(SplitMachineWriteJournal.Kind.BOILER) ?: return false
        boiler = slot
        var original: CoffeeReadbackFrame.OrdinarySettings? = null
        var before: CoffeeReadbackCursor? = null
        var dispatchedAt = -1L
        session.writeSetting(target, beforeDispatch = {
            val current = session.coffeeReadbackCursor
            val currentSettings = session.latestCoffeeReadbackFrame as? CoffeeReadbackFrame.OrdinarySettings
            val now = clock()
            if (!permit(slot) || schedulePhase != Phase.WAITING || schedule?.let { permit(it) } != true ||
                boiler !== slot || slot.phase != Phase.WRITING || current == null || currentSettings == null ||
                currentSettings.cursor.source !== current.source || currentSettings.cursor.sequence != current.sequence ||
                !SettingsFreshness.isFresh(currentSettings.receivedAtMs, now) || target.matches(currentSettings.value)) false
            else {
                original = currentSettings
                before = current
                dispatchedAt = now
                slot.passedDispatchGuard = true
                true
            }
        }) { result ->
            transportResult(slot, result) { cut ->
                slot.boilerEvidence = original?.let { observed -> before?.let { dispatch ->
                    CoffeeReadbackEvidence.Boiler.start(target, observed, dispatch, cut, dispatchedAt)
                } }
                slot.boilerEvidence != null
            }
        }
        return slot.phase != Phase.NOT_SENT && slot.phase != Phase.UNKNOWN
    }

    /** Called directly after fake transport notifications; no reconstruction from the old callback. */
    fun poll() {
        val active = listOfNotNull(schedule, boiler).filter { it.phase == Phase.WRITING || it.phase == Phase.WAITING }
        if (active.any { !permit(it) }) { revoke(); return }
        val current = session.coffeeReadbackCursor ?: return
        val actual = session.latestCoffeeReadbackFrame ?: return
        val now = clock()
        schedule?.takeIf { it.phase == Phase.WAITING }?.let {
            if (it.weeklyEvidence?.observe(actual, current, now) == true) settle(it, Phase.OBSERVED)
        }
        boiler?.takeIf { it.phase == Phase.WAITING }?.let {
            if (it.boilerEvidence?.matches(actual, current, now) == true) settle(it, Phase.OBSERVED)
        }
    }

    fun revoke() {
        revoked = true
        listOfNotNull(schedule, boiler).forEach {
            if (it.phase == Phase.WRITING || it.phase == Phase.WAITING) it.phase = Phase.UNKNOWN
            it.weeklyEvidence = null
            it.boilerEvidence = null
        }
        // The durable markers survive; no rehydration, reset or automatic write on reconnect.
    }

    private fun reserve(kind: SplitMachineWriteJournal.Kind): Slot? {
        if (revoked || !activeLeaseAndSafeEnvironment()) return null
        val source = session.coffeeReadbackCursor?.source ?: return null
        val at = clock()
        if (at < 0) return null
        val owner = journal.reserve(SplitMachineWriteJournal.Entry(kind, source.address)) ?: return null
        return Slot(owner, source, at)
    }

    private fun permit(slot: Slot): Boolean {
        val now = clock()
        return !revoked && activeLeaseAndSafeEnvironment() && journal.owns(slot.owner) &&
            session.coffeeReadbackCursor?.source === slot.source && now >= slot.startedAtMs && now - slot.startedAtMs < TIMEOUT_MS
    }

    private fun transportResult(slot: Slot, result: OperationResult, acceptCut: (CoffeeReadbackCursor) -> Boolean) {
        if (slot.phase != Phase.WRITING) return
        if (!permit(slot)) { revoke(); return }
        when (result) {
            is OperationResult.Success -> {
                val cut = session.coffeeReadbackCursor
                if (cut == null || !acceptCut(cut)) revoke() else slot.phase = Phase.WAITING
            }
            is OperationResult.Unknown -> revoke()
            is OperationResult.Cancelled, is OperationResult.Failed ->
                if (slot.passedDispatchGuard) revoke() else settle(slot, Phase.NOT_SENT)
        }
    }

    private fun settle(slot: Slot, terminal: Phase) {
        if (!permit(slot) || !journal.settle(slot.owner)) revoke()
        else {
            slot.phase = terminal
            slot.weeklyEvidence = null
            slot.boilerEvidence = null
        }
    }

    companion object { const val TIMEOUT_MS = 6_000L }
}
