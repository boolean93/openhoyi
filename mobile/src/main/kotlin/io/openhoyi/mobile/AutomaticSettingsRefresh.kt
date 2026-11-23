package io.openhoyi.mobile

/** One request per authenticated owner and stale baseline. No queued switch intent. */
internal class AutomaticSettingsRefresh {
    private var hub:Any?=null
    private var generation:Long?=null
    private var observation:Long?=null
    private var firstSafePoll:Long?=null
    private var spent=false
    fun hasSpent(owner:Any,gen:Long,observationId:Long):Boolean =
        hub===owner && generation==gen && observation==observationId && spent

    fun claim(owner:Any,gen:Long,observationId:Long?,now:Long,safe:Boolean):Boolean {
        if(hub!==owner || generation!=gen || observation!=observationId) {
            hub=owner;generation=gen;observation=observationId;firstSafePoll=null;spent=false
        }
        if(spent)return false
        if(!safe){firstSafePoll=null;return false}
        val first=firstSafePoll
        if(first==null){firstSafePoll=now;return false}
        if(now-first<350L)return false
        spent=true
        return true
    }
}
