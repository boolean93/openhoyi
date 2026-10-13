package io.openhoyi.mobile

import io.openhoyi.bean.*
import org.junit.Assert.*
import org.junit.Test

class BeanPreparationTest {
    private class Memory : BeanPreparation.Storage {
        var value: String? = null
        var fail = false
        var writes = 0
        var failOn = Int.MAX_VALUE
        override fun read() = value
        override fun write(value: String) { writes++; check(!fail && writes!=failOn); this.value = value }
    }
    private fun inventory(): BeanInventory = BeanInventory(object : InventoryStorage {
        var bytes: ByteArray? = null
        override fun read() = bytes
        override fun write(bytes: ByteArray) { this.bytes = bytes }
    }).also { it.addBatch("purchase",BeanBatch("batch",Bean("bean","Coffee"),100_000)) }

    @Test fun failedInitialJournalObservationCannotReassignDispatchedDoseAfterRestart() {
        val memory = Memory()
        val stock = inventory()
        var prep = BeanPreparation(memory)
        prep.select("dose-1", "batch", "bean", "Coffee", 18_000)
        prep.confirm(stock)
        prep.claim("shot-1") // Required durable boundary before start dispatch.
        var journalText: String? = null
        var journalFailed = true
        val journal = BrewJournal(object : BrewJournal.Storage {
            override fun read() = journalText
            override fun write(value: String) { check(!journalFailed); journalText = value }
        })
        assertThrows(IllegalStateException::class.java) {
            journal.observe(BrewJournal.Observation("shot-1", "curve", 100, null, null, "STARTING", null, null))
        }
        prep = BeanPreparation(memory)
        assertEquals("shot-1", prep.current()!!.shotId)
        assertNull(prep.claim("shot-2"))
        journalFailed = false
        journal.observe(BrewJournal.Observation("shot-1", "curve", 100, 200, 100, "ENDED", null, 3500))
        prep.associatePending(journal, stock)
        assertEquals(18_000L, journal.find("shot-1")!!.notes.doseMg)
        assertEquals(82_000L, stock.batches().single().balanceMg)
    }

    @Test fun selectionAndCancellationDoNotConsumeStock() {
        val memory=Memory(); val stock=inventory(); val prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000)
        assertEquals(100_000,stock.batches().single().balanceMg)
        prep.cancel()
        assertNull(prep.current())
        assertEquals(100_000,stock.batches().single().balanceMg)
    }
    @Test fun retryAfterPreparationSaveFailureCannotConsumeTwice() {
        val memory=Memory(); val stock=inventory(); var prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000)
        memory.failOn=3
        try { prep.confirm(stock); fail() } catch (_:IllegalStateException) { }
        assertEquals(82_000,stock.batches().single().balanceMg)
        memory.failOn=Int.MAX_VALUE; prep=BeanPreparation(memory)
        prep.confirm(stock); prep.confirm(stock)
        assertEquals(82_000,stock.batches().single().balanceMg)
        assertTrue(prep.current()!!.confirmed)
        val claimed=prep.claim("shot-1")!!
        assertEquals("shot-1",claimed.shotId)
        assertNull(prep.claim("shot-2"))
        assertEquals(claimed,prep.claim("shot-1"))
    }
    @Test fun failedStockCommitDoesNotConfirmPreparation() {
        val prep=BeanPreparation(Memory()); val stock=inventory()
        prep.select("dose-1","batch","bean","Coffee",101_000)
        try { prep.confirm(stock); fail() } catch (_:IllegalArgumentException) { }
        assertFalse(prep.current()!!.confirmed)
        assertEquals(100_000,stock.batches().single().balanceMg)
    }
    @Test fun cannotAbandonAnAmbiguousConsumptionOrReuseItsId() {
        val memory=Memory(); val stock=inventory(); val prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000)
        memory.failOn=3
        try { prep.confirm(stock); fail() } catch (_:IllegalStateException) { }
        memory.failOn=Int.MAX_VALUE
        try { prep.cancel(); fail() } catch (_:IllegalStateException) { }
        try { prep.select("dose-2","batch","bean","Coffee",17_000); fail() } catch (_:IllegalStateException) { }
        assertEquals(82_000,stock.batches().single().balanceMg)
    }
    @Test fun failedIntentSaveStopsBeforeInventoryAndExplicitDiscardKeepsConsumption() {
        val memory=Memory(); val stock=inventory(); val prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000)
        memory.fail=true
        try {prep.confirm(stock);fail()} catch (_:IllegalStateException) {}
        assertEquals(100_000,stock.batches().single().balanceMg)
        memory.fail=false;prep.confirm(stock);prep.discard(stock)
        assertNull(prep.current())
        assertEquals(82_000,stock.batches().single().balanceMg)
        try {prep.select("dose-1","batch","bean","Coffee",18_000);fail()} catch (_:IllegalArgumentException) {}
    }
    @Test fun corruptPreparationIsNotOverwrittenOrRebuilt() {
        val memory=Memory(); memory.value="damaged"
        try {BeanPreparation(memory);fail()} catch (_:IllegalStateException) {}
        assertEquals("damaged",memory.value)
        assertEquals(0,memory.writes)
    }
    @Test fun pendingJournalAssociationCannotBeReplacedByTheNextDose() {
        val memory=Memory();val stock=inventory();var prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000);prep.confirm(stock);prep.claim("shot-1")
        prep=BeanPreparation(memory)
        try {prep.select("dose-2","batch","bean","Coffee",17_000);fail()} catch (_:IllegalStateException) {}
        prep.markAssociated("shot-1")
        prep.select("dose-2","batch","bean","Coffee",17_000)
        assertEquals("dose-2",prep.current()!!.eventId)
        assertEquals(82_000,stock.batches().single().balanceMg)
    }
    @Test fun confirmedDoseMustMatchTheAuthoritativeInventoryLedger() {
        val prep=BeanPreparation(Memory());val stock=inventory()
        prep.select("dose-1","batch","bean","Coffee",18_000);prep.confirm(stock)
        try {prep.confirm(inventory());fail()} catch (_:IllegalArgumentException) {}
        assertTrue(prep.current()!!.confirmed)
        assertEquals(82_000,stock.batches().single().balanceMg)
    }
    @Test fun failedJournalWriteCanRecoverAfterRestartWithoutAnotherConsumption() {
        val memory=Memory();val stock=inventory();var prep=BeanPreparation(memory)
        prep.select("dose-1","batch","bean","Coffee",18_000);prep.confirm(stock);prep.claim("shot-1")
        val journalMemory=object:BrewJournal.Storage {
            var text:String?=null;var fail=false
            override fun read()=text
            override fun write(value:String) {check(!fail);text=value}
        }
        var journal=BrewJournal(journalMemory)
        journal.observe(BrewJournal.Observation("shot-1","curve",100,200,100,"ENDED",null,3500))
        journal.edit("shot-1",BrewJournal.Notes(taste="Sweet"))
        journalMemory.fail=true
        try {prep.associatePending(journal,stock);fail()} catch (_:IllegalStateException) {}
        assertFalse(prep.current()!!.associated)
        journalMemory.fail=false;prep=BeanPreparation(memory);journal=BrewJournal(journalMemory)
        prep.associatePending(journal,stock)
        assertTrue(prep.current()!!.associated)
        assertEquals("bean",journal.find("shot-1")!!.notes.beanId)
        assertEquals(18_000L,journal.find("shot-1")!!.notes.doseMg)
        assertEquals("Sweet",journal.find("shot-1")!!.notes.taste)
        assertEquals(82_000,stock.batches().single().balanceMg)
    }
}
