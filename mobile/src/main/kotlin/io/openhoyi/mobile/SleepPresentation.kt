package io.openhoyi.mobile

import io.openhoyi.session.SleepNowTracker

/** A request is a separate state from telemetry; raw sleep values must already be fresh. */
internal object SleepPresentation {
    /** The 104dp navigation rail uses short labels; full process text remains on the page and in accessibility. */
    fun compactStatus(resource:Int):Int=when(resource){
        R.string.sleep_status_writing -> R.string.task_shell_sleep_sending
        R.string.sleep_status_waiting -> R.string.task_shell_sleep_pending
        R.string.sleep_status_unknown, R.string.task_shell_machine_unconfirmed -> R.string.task_shell_sleep_check
        R.string.sleep_status_last_confirmed -> R.string.task_shell_machine_asleep
        R.string.sleep_status_failed -> R.string.task_shell_sleep_unsent
        else -> resource
    }
    fun compactHint(resource:Int?):Int?=when(resource){
        R.string.sleep_status_unknown_hint -> null // The short status already says to check the machine.
        R.string.sleep_status_last_confirmed_hint, R.string.task_shell_sleep_wake_hint -> null // Guidance lives in the dedicated action, not a second hint line.
        else -> resource
    }

    fun status(state:SleepNowTracker.State?,freshSleepRaw:Int?,lastSleepRaw:Int?=freshSleepRaw):Int = when(state){
        SleepNowTracker.State.WRITING -> R.string.sleep_status_writing
        SleepNowTracker.State.WAITING_ASLEEP -> R.string.sleep_status_waiting
        SleepNowTracker.State.UNKNOWN -> R.string.sleep_status_unknown
        else -> when(freshSleepRaw){
            1 -> R.string.task_shell_machine_asleep
            0 -> if(state==SleepNowTracker.State.FAILED)R.string.sleep_status_failed else R.string.task_shell_machine_awake
            else -> if(state==SleepNowTracker.State.CONFIRMED && lastSleepRaw==1) R.string.sleep_status_last_confirmed else R.string.task_shell_machine_unconfirmed
        }
    }
    fun hint(state:SleepNowTracker.State?,freshSleepRaw:Int?,lastSleepRaw:Int?=freshSleepRaw):Int? = when {
        state==SleepNowTracker.State.UNKNOWN -> R.string.sleep_status_unknown_hint
        state==SleepNowTracker.State.CONFIRMED && freshSleepRaw==null && lastSleepRaw==1 -> R.string.sleep_status_last_confirmed_hint
        state !in setOf(SleepNowTracker.State.WRITING,SleepNowTracker.State.WAITING_ASLEEP) && freshSleepRaw==1 -> R.string.task_shell_sleep_wake_hint
        else -> null
    }
}
