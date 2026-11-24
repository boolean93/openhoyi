package io.openhoyi.mobile

import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionState
import org.junit.Assert.*
import org.junit.Test

class SafetyNotificationPresentationTest {
    @Test fun existingWarningPriorityAndDestinationRemainStableForEveryState() {
        for (shot in ExtractionState.entries) for (coffee in DeviceState.entries) {
            val shotWarning = ShotSafetyAlert.resource(shot, coffee)
            for (manual in listOf<Int?>(null, R.string.service_event_manual_disconnect_warning)) {
                for (machine in listOf<Int?>(null, R.string.machine_recovery_setting)) {
                    val presentation = SafetyNotificationPresentation.from(shotWarning, manual, machine)
                    assertEquals(shotWarning ?: manual ?: machine, presentation.warningResource)
                    val oldDestination = if (machine != null && manual == null && shotWarning == null)
                        SafetyNotificationPresentation.Destination.HOME else SafetyNotificationPresentation.Destination.EXTRACTION
                    assertEquals(oldDestination, presentation.destination)
                    val original = presentation.warning(DefaultStringResources::resolve)
                    repeat(3) {
                        assertEquals(presentation.warningResource?.let { "translated $it" },
                            presentation.warning { id, _ -> "translated $id" })
                    }
                    assertEquals(original, presentation.warning(DefaultStringResources::resolve))
                    assertEquals(shotWarning ?: manual ?: machine, presentation.warningResource)
                }
            }
        }
    }

    @Test fun routineMachineProgressKeepsItsSafetyNotificationQuietUntilActionIsNeeded() {
        val split=R.string.service_v2_split_pending
        val setting=R.string.machine_recovery_setting
        fun warning(resource:Int,settingsProgress:Boolean=false,splitRecovery:Boolean=false) =
            SafetyNotificationPresentation.machineAttention(resource,settingsProgress,splitRecovery)

        assertNull(warning(split))
        assertEquals(split,warning(split,splitRecovery=true))
        assertNull(warning(setting,settingsProgress=true))
        assertEquals(setting,warning(setting))
        for (resource in listOf(R.string.service_v2_split_storage_failed,
            R.string.service_v2_split_recovery_blocked,R.string.service_v2_split_restart_required,
            R.string.machine_recovery_unknown)) {
            assertEquals(resource,warning(resource,settingsProgress=true))
        }
    }

    @Test fun absenceAndEmptyTranslationHaveDifferentWarningIdentity() {
        val none = SafetyNotificationPresentation.from(null, null, null)
        assertNull(none.warning { _, _ -> error("No warning must not resolve text") })
        val manual = SafetyNotificationPresentation.from(null, R.string.machine_recovery_shot_restart, null)
        assertEquals("", manual.warning { _, _ -> "" })
        assertNotNull(manual.warningResource)
        assertEquals(SafetyNotificationPresentation.Destination.EXTRACTION, manual.destination)
        val machine = SafetyNotificationPresentation.from(null, null, R.string.machine_recovery_unknown)
        assertEquals("", machine.warning { _, _ -> "" })
        assertEquals(SafetyNotificationPresentation.Destination.HOME, machine.destination)
    }
}
