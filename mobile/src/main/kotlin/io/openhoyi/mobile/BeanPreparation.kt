package io.openhoyi.mobile

import io.openhoyi.bean.BeanInventory
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Durable preparation intent, separate from both stock and machine state. Never rolls stock back. */
internal class BeanPreparation(private val storage: Storage) {
    interface Storage { fun read(): String?; fun write(value: String) }
    data class Dose(val eventId:String,val batchId:String,val beanId:String,val beanName:String,
        val amountMg:Long,val attempted:Boolean=false,val confirmed:Boolean=false,val shotId:String?=null,
        val associated:Boolean=false)
    private data class State(val dose:Dose?,val usedIds:Set<String>)
    private var state = storage.read()?.let(::decode) ?: State(null,emptySet())
    @Synchronized fun current():Dose? = state.dose
    @Synchronized fun select(eventId:String,batchId:String,beanId:String,beanName:String,amountMg:Long) {
        check(state.dose?.let { it.attempted && !it.associated } != true) { "Resolve existing consumption first" }
        require(eventId !in state.usedIds) { "Preparation ID already used" }
        val dose=Dose(eventId,batchId,beanId,beanName,amountMg)
        validate(dose)
        commit(State(dose,state.usedIds+eventId))
    }
    @Synchronized fun cancel() {
        check(state.dose?.let {it.attempted && !it.confirmed} != true) { "Consumption outcome needs reconciliation" }
        check(state.dose?.let {it.shotId!=null && !it.associated} != true) { "Journal association still pending" }
        commit(state.copy(dose=null)) // A confirmed dose remains in the stock ledger; no refund.
    }
    /** Explicitly discard only after checking the authoritative ledger; never creates a refund. */
    @Synchronized fun discard(inventory:BeanInventory) {
        val dose=state.dose ?: return
        check(dose.shotId==null || dose.associated) { "Journal association still pending" }
        inventory.events().firstOrNull {it.eventId==dose.eventId}?.let {
            require(it.batchId==dose.batchId && it.deltaMg == -dose.amountMg &&
                it.kind == io.openhoyi.bean.InventoryEventKind.CONSUME) { "Conflicting consumption event" }
        }
        commit(state.copy(dose=null))
    }
    @Synchronized fun confirm(inventory:BeanInventory):Dose {
        var dose=requireNotNull(state.dose)
        check(dose.shotId==null) { "Dose already assigned to a shot" }
        if(dose.confirmed) return verifyConfirmed(inventory)
        require(inventory.batches().any {it.batch.id==dose.batchId && it.batch.bean.id==dose.beanId}) {
            "Selected bean batch unavailable"
        }
        if(!dose.attempted) {
            dose=dose.copy(attempted=true)
            commit(state.copy(dose=dose)) // Persist stable ID before touching inventory.
        }
        inventory.consume(dose.eventId,dose.batchId,dose.amountMg)
        val confirmed=dose.copy(confirmed=true)
        commit(state.copy(dose=confirmed))
        return confirmed
    }
    @Synchronized fun verifyConfirmed(inventory:BeanInventory):Dose {
        val dose=requireNotNull(state.dose)
        require(dose.confirmed)
        require(inventory.events().any {it.eventId==dose.eventId && it.batchId==dose.batchId &&
            it.deltaMg == -dose.amountMg && it.kind==io.openhoyi.bean.InventoryEventKind.CONSUME}) {
            "Confirmed preparation has no matching consumption"
        }
        return dose
    }
    @Synchronized fun claim(shotId:String):Dose? {
        require(shotId.isNotBlank())
        val dose=state.dose?.takeIf {it.confirmed} ?: return null
        if(dose.shotId!=null) return dose.takeIf {it.shotId==shotId}
        val assigned=dose.copy(shotId=shotId)
        commit(state.copy(dose=assigned))
        return assigned
    }
    @Synchronized fun markAssociated(shotId:String) {
        val dose=requireNotNull(state.dose)
        require(dose.shotId==shotId && dose.confirmed)
        if(!dose.associated) commit(state.copy(dose=dose.copy(associated=true)))
    }
    @Synchronized fun associatePending(journal:BrewJournal,inventory:BeanInventory) {
        val dose=state.dose?.takeIf {it.shotId!=null && !it.associated} ?: return
        verifyConfirmed(inventory)
        val shotId=requireNotNull(dose.shotId)
        synchronized(journal) {
            val entry=requireNotNull(journal.find(shotId)) { "Cannot invent a shot through preparation" }
            val notes=entry.notes
            journal.edit(shotId,notes.copy(beanId=notes.beanId ?: dose.beanId,
                beanName=notes.beanName ?: dose.beanName,doseMg=notes.doseMg ?: dose.amountMg))
            markAssociated(shotId)
        }
    }
    private fun validate(dose:Dose) {
        require(listOf(dose.eventId,dose.batchId,dose.beanId).all {it.isNotBlank() && it.length<=1000})
        require(dose.beanName.isNotBlank() && dose.beanName.length<=4096)
        require(dose.amountMg>0)
        require(!dose.confirmed || dose.attempted)
        require(dose.shotId==null || (dose.confirmed && dose.shotId.isNotBlank()))
        require(!dose.associated || dose.shotId!=null)
    }
    private fun commit(next:State) {
        val data=JSONObject().put("usedIds",JSONArray(next.usedIds.sorted()))
        val dose=next.dose
        data.put("dose",dose?.let { JSONObject().put("eventId",it.eventId).put("batchId",it.batchId)
            .put("beanId",it.beanId).put("beanName",it.beanName).put("amountMg",it.amountMg)
            .put("attempted",it.attempted).put("confirmed",it.confirmed).put("shotId",it.shotId ?: JSONObject.NULL)
            .put("associated",it.associated) } ?: JSONObject.NULL)
        val payload=data.toString()
        storage.write(JSONObject().put("version",1).put("payload",payload).put("sha256",hash(payload)).toString())
        state=next
    }
    private fun decode(value:String):State {
        try {
            val root=JSONObject(value)
            require(root.getInt("version")==1)
            val payload=root.getString("payload")
            require(root.getString("sha256")==hash(payload))
            val data=JSONObject(payload)
            val ids=data.getJSONArray("usedIds")
            val used=(0 until ids.length()).map {ids.getString(it)}
            require(used.toSet().size==used.size && used.all {it.isNotBlank()})
            val dose=if(data.isNull("dose")) null else data.getJSONObject("dose").let {
                Dose(it.getString("eventId"),it.getString("batchId"),it.getString("beanId"),it.getString("beanName"),
                    it.getLong("amountMg"),it.getBoolean("attempted"),it.getBoolean("confirmed"),
                    if(it.isNull("shotId")) null else it.getString("shotId"),it.optBoolean("associated",false))
            }
            dose?.let {validate(it);require(it.eventId in used)}
            return State(dose,used.toSet())
        } catch(error:Exception) { throw IllegalStateException("Preparation unavailable; original data retained",error) }
    }
    private fun hash(value:String):String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") {"%02x".format(it)}
}
