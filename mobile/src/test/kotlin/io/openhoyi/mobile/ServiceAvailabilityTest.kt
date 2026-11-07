package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class ServiceAvailabilityTest {
    private class Page(val availability: ServiceAvailability) {
        var serviceExists = false
        var owner: String? = null
        var currentOwner = "service-1"
        var unbinds = 0
        var publications = 0
        val creates = mutableListOf<Boolean>()
        val lease = VisibleServiceBinding(availability,
            bind = { create -> creates += create; create || serviceExists },
            unbind = { unbinds++ },
            onReleased = { owner = null },
            onAvailable = { if (owner != null) publications++ })
        fun connected() {
            // Same lease guard as MainActivity's asynchronous ServiceConnection delivery.
            if (lease.bound) { owner = currentOwner; publications++ }
        }
    }

    @Test fun bluetoothRestoreOrAutomaticEnableBindsTheSameVisiblePageOnlyOnce() {
        val availability = ServiceAvailability()
        val page = Page(availability)
        page.lease.start()
        assertFalse(page.lease.bindExisting()) // Bluetooth off, no service.
        assertNull(page.owner)
        // onCreate alone, or failed startup, is deliberately not a started signal.
        page.serviceExists = true
        assertFalse(availability.available)
        assertEquals(listOf(false), page.creates)
        // Process owner starts successfully without another Activity start/resume.
        availability.started("service-1")
        assertTrue(page.lease.bound)
        page.connected()
        assertEquals("service-1", page.owner)
        repeat(3) { availability.started("service-1"); page.lease.bindExisting() }
        assertEquals(listOf(false, false), page.creates) // Only one accepted bind; never AUTO_CREATE.
        assertEquals(1, page.publications)
        page.lease.stop()
        page.connected() // Late delivery after exit must not restore the page owner.
        assertNull(page.owner)
        assertEquals(1, page.unbinds)
        availability.stopped("service-1")
        availability.started("service-2")
        assertEquals(listOf(false, false), page.creates) // Subscription removed, no hidden binding.
        assertEquals(1, page.publications)
        assertFalse(page.lease.bindCreated()) // Even explicit requests cannot bind a stopped page.
    }

    @Test fun createdBindingBeforeRunningIsReusedAndStopReleasesWithoutRecreating() {
        val availability = ServiceAvailability()
        val page = Page(availability)
        page.lease.start()
        assertTrue(page.lease.bindCreated()) // Explicit UI startup keeps AUTO_CREATE semantics.
        page.connected() // onBind can arrive before startup completion.
        availability.started("service-1")
        assertEquals(2, page.publications) // Existing owner is republished after running becomes true.
        assertEquals(listOf(true), page.creates)
        availability.stopped("service-1")
        assertNull(page.owner)
        assertFalse(page.lease.bound)
        assertEquals(1, page.unbinds)
        assertEquals(listOf(true), page.creates) // A stop never creates a replacement.
        page.serviceExists = true
        availability.started("service-2")
        assertEquals(listOf(true, false), page.creates)
        page.lease.stop()
    }

    @Test fun activityRecreationReplaysExistingAvailabilityAndDetachesOldPage() {
        val availability = ServiceAvailability()
        availability.started("service-1")
        val old = Page(availability).apply { serviceExists = true; lease.start(); connected() }
        old.lease.stop()
        val replacement = Page(availability).apply { serviceExists = true; lease.start(); connected() }
        replacement.lease.start()
        replacement.lease.bindCreated()
        assertNull(old.owner)
        assertEquals("service-1", replacement.owner)
        assertEquals(listOf(false), old.creates)
        assertEquals(listOf(false), replacement.creates)
        availability.stopped("service-1")
        assertNull(replacement.owner)
        assertEquals(1, old.unbinds)
        assertEquals(1, replacement.unbinds)
        replacement.lease.stop()
        assertEquals(1, replacement.unbinds)
    }

    @Test fun staleOwnerStopCannotHideReplacementAndObserversDetachIndependently() {
        val availability = ServiceAvailability()
        val first = mutableListOf<Boolean>()
        val second = mutableListOf<Boolean>()
        val observer: (Boolean) -> Unit = { first += it }
        availability.observe(observer)
        availability.observe { second += it }
        availability.started("old")
        availability.unobserve(observer)
        availability.started("replacement")
        availability.stopped("old")
        assertTrue(availability.available)
        availability.stopped("replacement")
        availability.stopped("replacement")
        assertEquals(listOf(false, true), first)
        assertEquals(listOf(false, true, true, false), second)
    }
}
