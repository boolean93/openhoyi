package io.openhoyi.mobile

import io.openhoyi.protocol.MachineSettingChange
import io.openhoyi.protocol.Settings
import io.openhoyi.session.OperationResult

/** BLE write completion is transport evidence; a newer matching 0x83 frame confirms application. */
class SettingsWriteTracker {
    enum class State { IDLE, WRITING, WAITING_READBACK, CONFIRMED, FAILED, UNKNOWN }
    var state = State.IDLE
        private set
    var change: MachineSettingChange? = null
        private set
    private var serial = 0L
    private var afterSample = 0L

    fun begin(value: MachineSettingChange): Long? {
        if (state == State.WRITING || state == State.WAITING_READBACK) return null
        change = value
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
            is OperationResult.Unknown -> State.UNKNOWN
        }
        return true
    }

    fun observe(sampleSerial: Long, settings: Settings): Boolean {
        if (state != State.WAITING_READBACK || sampleSerial <= afterSample ||
            change?.matches(settings) != true) return false
        state = State.CONFIRMED
        return true
    }

    fun timeout(token: Long): Boolean {
        if (token != serial || state != State.WAITING_READBACK) return false
        state = State.UNKNOWN
        return true
    }

    fun disconnected() {
        if (state == State.WRITING || state == State.WAITING_READBACK) state = State.UNKNOWN
    }
}
