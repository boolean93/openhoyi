package io.openhoyi.session

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings

/** BLE write completion is transport evidence; a newer matching 0x83 frame confirms application. */
class SettingsWriteTracker {
    enum class State { IDLE, WRITING, WAITING_READBACK, CONFIRMED, FAILED, UNKNOWN, RECONCILED }
    var state = State.IDLE
        private set
    var change: MachineSettingChange? = null
        private set
    private var serial = 0L
    private var afterSample = 0L

    fun begin(value: MachineSettingChange): Long? {
        if (state == State.WRITING || state == State.WAITING_READBACK || state == State.UNKNOWN) return null
        change = value
        afterSample = 0L
        state = State.WRITING
        return ++serial
    }

    /** An explicit new temperature intent replaces observation of an older one, never its write.
     * This is used only by the isolated non-blocking temperature controller. */
    internal fun beginTemperature(value: MachineSettingChange): Long? {
        if (!SettingsReadbackPolicy.isTemperature(value) || state == State.WRITING) return null
        if (state in setOf(State.WAITING_READBACK, State.UNKNOWN)) {
            if (!SettingsReadbackPolicy.isTemperature(change)) return null
            // Invalidate the old callbacks before the new intent is dispatched.
            state = State.IDLE
        }
        return begin(value)
    }

    /** Only the original operation in WRITING owns dispatch permission. */
    fun permitsWrite(token:Long):Boolean = token == serial && state == State.WRITING

    /** Only an eligible original successful write may request authenticated settings refresh. */
    fun permitsBoilerRefresh(token:Long):Boolean = token == serial && state == State.WAITING_READBACK &&
        SettingsReadbackPolicy.supports(change)

    fun boilerRecoveryToken():Long? = serial.takeIf {state==State.UNKNOWN &&
        SettingsReadbackPolicy.supports(change)}
    fun permitsBoilerRecovery(token:Long):Boolean = boilerRecoveryToken()==token

    fun written(token: Long, result: OperationResult, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WRITING) return false
        state = when (result) {
            is OperationResult.Success -> {
                afterSample = sampleSerial
                State.WAITING_READBACK
            }
            is OperationResult.Failed, is OperationResult.Cancelled -> State.FAILED
            is OperationResult.Unknown -> {
                afterSample = sampleSerial
                State.UNKNOWN
            }
        }
        return true
    }

    fun observe(sampleSerial: Long, settings: Settings, reconcileBoilerMismatch:Boolean=false): Boolean {
        if (state !in setOf(State.WAITING_READBACK, State.UNKNOWN) || sampleSerial <= afterSample)
            return false
        afterSample = sampleSerial
        if (change?.matches(settings) != true) {
            if (state == State.UNKNOWN || reconcileBoilerMismatch &&
                (change is MachineSettingChange.BrewHeating || change is MachineSettingChange.SteamHeating)) state = State.RECONCILED
            return false
        }
        state = State.CONFIRMED
        return true
    }

    fun ownsBoilerObservation(token:Long,target:MachineSettingChange?):Boolean =
        serial==token && change==target && SettingsReadbackPolicy.isBoiler(target) &&
            state in setOf(State.WAITING_READBACK,State.UNKNOWN,State.RECONCILED)

    fun observeReboundBoiler(token:Long,target:MachineSettingChange?,sampleSerial:Long):Boolean {
        if (!ownsBoilerObservation(token,target) || state==State.RECONCILED || sampleSerial<=afterSample) return false
        afterSample=sampleSerial
        // A new authenticated owner only tells us the configured value now.
        state=State.RECONCILED
        return true
    }

    fun timeout(token: Long, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_READBACK) return false
        afterSample = maxOf(afterSample, sampleSerial)
        state = State.UNKNOWN
        return true
    }

    /** User acknowledged a specific unknown operation after an independent fresh baseline;
     * this does not confirm the requested value or grant automatic retry. */
    fun unknownAcknowledgementToken():Long? = serial.takeIf { state==State.UNKNOWN }
    fun acknowledgeUnknown(token:Long):Boolean {
        if (serial!=token || state!=State.UNKNOWN) return false
        serial++
        change=null
        afterSample=0
        state=State.IDLE
        return true
    }

    fun disconnected(sampleSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_READBACK) {
            afterSample = maxOf(afterSample, sampleSerial)
            state = State.UNKNOWN
        }
    }
}
