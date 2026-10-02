package io.openhoyi.mobile
import org.junit.Assert.*
import org.junit.Test
class BrewFeedbackPreferenceTest {
    private class Storage : BrewFeedbackPreference.Storage {
        var saved: Boolean? = null; var success = true; var writes = 0
        override fun read() = saved
        override fun write(enabled: Boolean): Boolean { writes++; if(success) saved=enabled; return success }
    }
    @Test fun startsDisabledAndSuccessfulChangesSurviveReload() {
        val storage=Storage(); val prefs=BrewFeedbackPreference(storage)
        assertFalse(prefs.enabled)
        assertTrue(prefs.setEnabled(true)); assertTrue(prefs.enabled)
        assertTrue(BrewFeedbackPreference(storage).enabled)
        assertTrue(prefs.setEnabled(false)); assertFalse(prefs.enabled)
    }
    @Test fun failedWriteNeverChangesAuthorityOrRetriesUnchangedValue() {
        val storage=Storage(); val prefs=BrewFeedbackPreference(storage); storage.success=false
        assertFalse(prefs.setEnabled(true)); assertFalse(prefs.enabled)
        assertTrue(prefs.setEnabled(false)); assertEquals(1,storage.writes)
    }
    @Test fun storageExceptionsAreLocalFailures() {
        val prefs=BrewFeedbackPreference(object:BrewFeedbackPreference.Storage {
            override fun read(): Boolean? = throw IllegalStateException()
            override fun write(enabled:Boolean):Boolean = throw IllegalStateException()
        })
        assertFalse(prefs.enabled); assertFalse(prefs.setEnabled(true)); assertFalse(prefs.enabled)
    }
    @Test fun disablingNotifiesEveryLocalOwnerAndUnsubscribeStopsCallbacks() {
        val prefs=BrewFeedbackPreference(Storage()); val first=mutableListOf<Boolean>(); val second=mutableListOf<Boolean>()
        prefs.observe("first") { first.add(it) }; prefs.observe("second") { second.add(it) }
        prefs.setEnabled(true); prefs.unobserve("second"); prefs.setEnabled(false)
        assertEquals(listOf(false,true,false),first); assertEquals(listOf(false,true),second)
    }
    @Test fun observerFailureCannotReverseSuccessfulDiskWriteOrBlockOtherOwners() {
        val prefs=BrewFeedbackPreference(Storage()); val values=mutableListOf<Boolean>()
        prefs.observe("throws") { throw IllegalStateException() }; prefs.observe("normal") { values.add(it) }
        assertTrue(prefs.setEnabled(true)); assertTrue(prefs.enabled); assertEquals(listOf(false,true),values)
    }
    @Test fun reentrantChangeDoesNotSendStaleStateToLaterOwners() {
        val prefs=BrewFeedbackPreference(Storage()); val values=mutableListOf<Boolean>()
        prefs.observe("changes") { if(it) prefs.setEnabled(false) }; prefs.observe("later") { values.add(it) }
        assertTrue(prefs.setEnabled(true)); assertFalse(prefs.enabled); assertEquals(listOf(false,false),values)
    }

}
