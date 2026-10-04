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

    fun observe(sampleSerial: Long, settings: Settings): Boolean {
        if (state !in setOf(State.WAITING_READBACK, State.UNKNOWN) || sampleSerial <= afterSample)
            return false
        afterSample = sampleSerial
        if (change?.matches(settings) != true) {
            if (state == State.UNKNOWN) state = State.RECONCILED
            return false
        }
        state = State.CONFIRMED
        return true
    }

    fun timeout(token: Long, sampleSerial: Long): Boolean {
        if (token != serial || state != State.WAITING_READBACK) return false
        afterSample = maxOf(afterSample, sampleSerial)
        state = State.UNKNOWN
        return true
    }

    fun disconnected(sampleSerial: Long) {
        if (state == State.WRITING || state == State.WAITING_READBACK) {
            afterSample = maxOf(afterSample, sampleSerial)
            state = State.UNKNOWN
        }
    }
}
