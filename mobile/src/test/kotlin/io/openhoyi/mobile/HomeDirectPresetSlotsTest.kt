package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class HomeDirectPresetSlotsTest {
    @Test fun onlySameDeviceSentMakesLeverCardVisible() {
        val store = MachineLeverRecipeStore(object : MachineLeverRecipeStore.Storage {
            override fun read(): String? = null
            override fun write(value: String) = true
        })
        val snapshot = MachineLeverRecipeStore.Snapshot("aa:bb", "draft-one", "Recipe",
            "08825c0046031e5a0000009608019000000000d8", 1234L, "1.1.5")
        fun slots(address: String?) = HomeDirectPresetSlots.visible(store.recordFor(address) != null)
        val shortcuts = (1..5).toList()
        assertEquals(shortcuts, slots(snapshot.address))
        for (state in listOf(MachineLeverRecipeStore.State.FAILED, MachineLeverRecipeStore.State.UNKNOWN)) {
            val token = requireNotNull(store.begin(snapshot))
            assertEquals(shortcuts, slots(snapshot.address)) // first pending
            assertTrue(store.complete(token, state))
            assertEquals(shortcuts, slots(snapshot.address))
            if (state == MachineLeverRecipeStore.State.UNKNOWN) assertTrue(store.acknowledge(token))
        }
        assertTrue(store.complete(requireNotNull(store.begin(snapshot)), MachineLeverRecipeStore.State.SENT))
        assertEquals((0..5).toList(), slots(snapshot.address))
        assertEquals(shortcuts, slots("other-device"))
        assertEquals(shortcuts, slots(null))
        assertTrue(store.complete(requireNotNull(store.begin(snapshot)), MachineLeverRecipeStore.State.UNKNOWN))
        assertEquals((0..5).toList(), slots(snapshot.address)) // unknown cannot erase previous SENT
        assertTrue(store.blocked)
    }
}
