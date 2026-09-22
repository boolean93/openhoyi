package io.openhoyi.session

data class WeightReading(val hundredthsGram:Int,val receivedAtMs:Long)
enum class StopReason { TARGET_WEIGHT, SCALE_UNAVAILABLE, MANUAL }
/** Policy only: caller must transmit the returned stop and separately observe device state. */
class ExtractionPolicy(private val maxSampleAgeMs:Long=1500,private val minimumBrewMs:Long=7000) {
    private var shot:Long?=null
    private var started=0L
    private var target=0
    private var compensation=0
    private var baseline:Int?=null
    private var tareAt=0L
    private var lastWeightAt:Long?=null
    private var stopIssued=false
    fun begin(id:Long,targetHundredthsGram:Int,compensationHundredthsGram:Int,now:Long) {
        require(targetHundredthsGram in 0..600_000)
        require(compensationHundredthsGram in -10_000..10_000)
        require(targetHundredthsGram==0 || compensationHundredthsGram<targetHundredthsGram)
        check(shot==null){"previous extraction not ended"}
        shot=id;started=now;target=targetHundredthsGram;compensation=compensationHundredthsGram
        baseline=null;tareAt=0;lastWeightAt=null;stopIssued=false
    }
    fun confirmTare(id:Long,baselineHundredthsGram:Int,atMs:Long) {
        if(shot!=id||stopIssued||baseline!=null||atMs<started)return
        require(baselineHundredthsGram in -50_000..600_000)
        baseline=baselineHundredthsGram;tareAt=atMs;lastWeightAt=atMs
    }
    fun sample(id:Long,reading:WeightReading,now:Long):StopReason? {
        if(shot!=id||stopIssued||target==0||baseline==null)return null
        if(reading.hundredthsGram !in -50_000..600_000)return null
        if(reading.receivedAtMs<=tareAt||reading.receivedAtMs>now||now-reading.receivedAtMs>maxSampleAgeMs)return null
        if(lastWeightAt!=null&&reading.receivedAtMs<=lastWeightAt!!)return null
        lastWeightAt=reading.receivedAtMs
        val net=reading.hundredthsGram.toLong()-baseline!!+compensation
        return if(now-started>=minimumBrewMs&&net>=target) issue(StopReason.TARGET_WEIGHT) else null
    }
    fun checkHealth(id:Long,now:Long):StopReason? {
        if(shot!=id||stopIssued||target==0)return null
        val last=lastWeightAt
        // Initial tare/start grace is explicit; no normal target stop during this window.
        if((last==null&&now-started>4000)||(last!=null&&now-last>maxSampleAgeMs))return issue(StopReason.SCALE_UNAVAILABLE)
        return null
    }
    fun manualStop(id:Long):StopReason?=if(shot==id&&!stopIssued)issue(StopReason.MANUAL)else null
    private fun issue(reason:StopReason):StopReason {stopIssued=true;return reason}
    fun end(id:Long){if(shot==id)shot=null}
}
