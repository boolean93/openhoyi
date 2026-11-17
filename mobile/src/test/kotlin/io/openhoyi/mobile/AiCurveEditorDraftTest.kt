package io.openhoyi.mobile

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiCurveEditorDraftTest {
    private class Disk : CustomCurveStore.Storage {
        var value: String? = null; var writes = 0
        override fun read() = value
        override fun write(value: String) { writes++; this.value = value }
    }
    private val epoch = AiCurveWriteCoordinator.Epoch("synthetic", 1)
    private fun doc() = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000001", "Synthetic", 93, 3600,
        CustomCurveDocument.ControlMode.PRESSURE, listOf(CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 100)))
    private fun args(operation: String = "create", document: CustomCurveDocument = doc(), id: String? = null) =
        JSONObject().put("operation", operation).put("curve", JSONObject(document.encode()).also { it.remove("id") })
            .also { if (id != null) it.put(if (operation == "copy_from_factory") "sourceId" else "targetId", id) }.toString()
    private fun factory() = FactoryCurve("factory-v3-001", "light", "Factory", "Notes", "", 30, 360, 10,
        3, 93, false, true, 2, listOf(30, 25, 70, 60), listOf(10, 20, 0, 0), 4)
    private fun factoryItem(f: FactoryCurve = factory()) = CurveLibraryItem(f.id, f.name, "", "", null, f)
    private fun rejects(block: () -> Unit) = assertTrue(runCatching(block).isFailure)
    @Test fun cardConfirmAndEditorCancelNeverWriteButExplicitCreateSavesExactlyOnce() {
        val disk = Disk(); val store = CustomCurveStore(disk)
        val p = AiCurveWriteCoordinator(store).propose(args(), epoch)
        val card = AiCurveCard.proposal(p)
        val draft = AiCurveEditorDraft.open(card, store)
        val token = AiCurveEditorDrafts.put(draft)
        assertEquals(p.after, AiCurveEditorDrafts.get(token)!!.seed)
        // Recreating an Activity reuses typed data, rather than consuming the token again.
        assertSame(draft, AiCurveEditorDrafts.get(token)); assertEquals(0, disk.writes)
        AiCurveEditorDrafts.remove(token); assertNull(AiCurveEditorDrafts.get(token))
        rejects { draft.saveFromEditor(draft.seed, store) }; assertEquals(0, disk.writes)
        val fresh = AiCurveEditorDraft.open(card, store)
        fresh.saveFromEditor(fresh.seed.copy(name = "User edited draft"), store)
        assertEquals(1, disk.writes); assertEquals("User edited draft", store.find(card.id)!!.name)
        rejects { fresh.saveFromEditor(fresh.seed, store) }; assertEquals(1, disk.writes)
        rejects { AiCurveEditorDraft.open(card, store) } // Create replay cannot overwrite its prior save.
    }
    @Test fun customReplacePrefillsAfterAndRequiresExactCurrentBeforeAndNativeSave() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc())
        val proposal = AiCurveWriteCoordinator(store).propose(args("replace", doc().copy(name = "AI draft"), doc().id), epoch)
        val card = AiCurveCard.proposal(proposal); val draft = AiCurveEditorDraft.open(card, store)
        assertEquals("AI draft", draft.seed.name); assertEquals(1, disk.writes)
        assertEquals(doc(), store.find(doc().id))
        draft.saveFromEditor(draft.seed.copy(temperatureC = 94), store)
        assertEquals(2, disk.writes); assertEquals(doc().id, store.list().single().id)
        assertEquals(94, store.find(doc().id)!!.temperatureC)
        rejects { AiCurveEditorDraft.open(card, store) }
        store.delete(doc().id, emptyMap()); rejects { AiCurveEditorDraft.open(card, store) }
    }
    @Test fun editorRevisionABAInUseAndOriginChangesFailClosedWithoutWrites() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc())
        fun card() = AiCurveCard.proposal(AiCurveWriteCoordinator(store).propose(args("replace", doc().copy(name = "After"), doc().id), epoch))
        val stale = AiCurveEditorDraft.open(card(), store)
        store.save(doc().copy(name = "Temporary")); store.save(doc()); val writes = disk.writes
        rejects { stale.saveFromEditor(stale.seed, store) }; assertEquals(writes, disk.writes)
        val blocked = AiCurveEditorDraft.open(card(), store)
        rejects { blocked.saveFromEditor(blocked.seed, store) { false } }; assertEquals(writes, disk.writes)
        rejects { blocked.saveFromEditor(blocked.seed, store) }; assertEquals(writes, disk.writes)
        var current = true
        val origin = AiCurveEditorDraft.open(card(), store, originCurrent = { current })
        current = false; rejects { origin.saveFromEditor(origin.seed, store) }; assertEquals(writes, disk.writes)
    }
    @Test fun factoryCopyUsesTrustedCompleteSourceAndNewCustomIdWithoutChangingBuiltin() {
        val disk = Disk(); val store = CustomCurveStore(disk); val source = factoryItem()
        val template = CustomCurveDocument.fromLibraryItem(source)
        val coordinator = AiCurveWriteCoordinator(store, resolveFactory = { source })
        val p = coordinator.propose(args("copy_from_factory", template.copy(name = "My modified copy"), source.id), epoch)
        val card = AiCurveCard.decode(AiCurveCard.proposal(p).encode())
        assertEquals(AiCurveCard.Kind.COPY_FACTORY, card.kind)
        assertEquals(factory(), card.factorySource!!.item.factoryCurve)
        assertEquals(CustomCurveDocument.fromLibraryItem(source, card.id), card.before)
        assertTrue(card.id.startsWith("draft-")); assertNotEquals(source.id, card.id)
        val draft = AiCurveEditorDraft.open(card, store, { source })
        assertEquals(0, disk.writes); assertEquals(4, draft.seed.allStages.size)
        draft.saveFromEditor(draft.seed, store)
        assertEquals(1, disk.writes); assertEquals(card.id, store.list().single().id)
        assertEquals(factory(), source.factoryCurve); assertNull(store.find(source.id))
        rejects { coordinator.confirm(p.token, epoch) }
    }
    @Test fun forgedFactoryIdsModelChosenSaveIdsAndChangedFactorySourcesAreRejected() {
        val disk = Disk(); val store = CustomCurveStore(disk); var source = factoryItem()
        val resolver: (String) -> CurveLibraryItem? = { id -> source.takeIf { it.id == id } }
        val coordinator = AiCurveWriteCoordinator(store, resolveFactory = resolver)
        rejects { coordinator.propose(args("replace", id = source.id), epoch) }
        rejects { coordinator.propose(args("copy_from_factory", id = "factory-v3-999"), epoch) }
        val withId = JSONObject(args("copy_from_factory", id = source.id)).also { it.getJSONObject("curve").put("id", source.id) }
        rejects { coordinator.propose(withId.toString(), epoch) }
        val p = coordinator.propose(args("copy_from_factory", id = source.id), epoch)
        val card = AiCurveCard.proposal(p)
        val draft = AiCurveEditorDraft.open(card, store, resolver)
        source = factoryItem(factory().copy(targets = listOf(30, 26, 70, 60)))
        rejects { AiCurveEditorDraft.open(card, store, resolver) }
        rejects { draft.saveFromEditor(draft.seed, store) }
        assertEquals(0, disk.writes)
    }
    @Test fun restoredCardsNeedFreshChecksAndMissingHandoffCannotBecomeNormalCreate() {
        val disk = Disk(); val store = CustomCurveStore(disk); store.save(doc())
        val p = AiCurveWriteCoordinator(store).propose(args("replace", doc().copy(name = "After"), doc().id), epoch)
        val card = AiCurveCard.decode(AiCurveCard.proposal(p).encode())
        val draft = AiCurveEditorDraft.open(card, store)
        val token = AiCurveEditorDrafts.put(draft); val seed = draft.seed
        AiCurveEditorDrafts.remove(token)
        assertNull(AiCurveEditorDrafts.get(token)); assertNull(AiCurveEditorDrafts.get("unknown"))
        assertEquals("After", seed.name) // Display-only source/raw fields remain available for saved state.
        rejects { draft.saveFromEditor(seed, store) }; assertEquals(1, disk.writes)
        val copy = store.saveCopy(doc().copy(name = "Legacy copy"))
        assertNotEquals(doc().id, copy.id); assertEquals(doc(), store.find(doc().id))
    }
}
