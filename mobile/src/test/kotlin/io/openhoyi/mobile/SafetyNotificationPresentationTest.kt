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
        fun warning(resource:Int,writeProgress:Boolean=false,splitRecovery:Boolean=false) =
            SafetyNotificationPresentation.machineAttention(resource,writeProgress,splitRecovery)

        assertNull(warning(split))
        assertEquals(split,warning(split,splitRecovery=true))
        for (resource in listOf(R.string.machine_recovery_setting,R.string.machine_recovery_cup_reset,
            R.string.machine_recovery_sleep_schedule,R.string.machine_recovery_sleep_now)) {
            assertNull(warning(resource,writeProgress=true))
            assertEquals(resource,warning(resource))
        }
        for (resource in listOf(R.string.service_v2_split_storage_failed,
            R.string.service_v2_split_recovery_blocked,R.string.service_v2_split_restart_required,
            R.string.machine_recovery_unknown)) {
            assertEquals(resource,warning(resource,writeProgress=true))
        }
    }

    @Test fun leverSuppressionRequiresTheExactPendingTokenAndAnAvailableJournal() {
        val storage=object:MachineLeverRecipeStore.Storage {
            var value:String?=null
            override fun read()=value
            override fun write(value:String):Boolean { this.value=value;return true }
        }
        val journal=MachineLeverRecipeStore(storage)
        val snapshot=MachineLeverRecipeStore.Snapshot("AA:BB:CC:DD:EE:FF","draft-one","Recipe",
            "08825c0046031e5a0000009608019000000000d8",1234L,"1.1.5")
        val token=requireNotNull(journal.begin(snapshot))
        val warning=R.string.lever_unresolved
        assertNull(SafetyNotificationPresentation.leverAttention(journal,token))
        assertEquals(warning,SafetyNotificationPresentation.leverAttention(journal,null))
        assertEquals(warning,SafetyNotificationPresentation.leverAttention(journal,"different-token"))
        assertEquals(warning,SafetyNotificationPresentation.leverAttention(MachineLeverRecipeStore(storage),token))
        assertTrue(journal.complete(token,MachineLeverRecipeStore.State.UNKNOWN))
        assertEquals(warning,SafetyNotificationPresentation.leverAttention(journal,token))
        storage.value="damaged"
        assertEquals(warning,SafetyNotificationPresentation.leverAttention(MachineLeverRecipeStore(storage),token))
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
