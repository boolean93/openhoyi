package io.openhoyi.session

/** Expiry decisions only; host owns timer, gated cancellation and localized feedback. */
object BrewPreparationWatchdog {
    const val TIMEOUT_MS=600_000L
    enum class Outcome { CANCEL_REQUESTED, CANCEL_BLOCKED }
    data class Result<T>(val outcome:Outcome,val blocked:T?)
    fun <T> expire(tracker:BrewPreparation,token:Long,beforeCancel:()->Boolean,
        authorizationFailure:T,requestCancel:()->T?):Result<T>? {
        if(!tracker.isActive(token))return null
        val authorized=runCatching(beforeCancel).getOrDefault(false)
        // Caller authorization can reenter the host; never cancel or expire a newer request.
        if(!tracker.isActive(token))return null
        if(!authorized)return if(tracker.timedOut(token))Result(Outcome.CANCEL_BLOCKED,authorizationFailure) else null
        return expire(tracker,token,requestCancel)
    }
    fun <T> expire(tracker:BrewPreparation,token:Long,requestCancel:()->T?):Result<T>? {
        if(!tracker.isActive(token))return null
        val blocked=requestCancel()
        if(blocked==null && tracker.state!=BrewPreparation.State.UNKNOWN)
            return Result(Outcome.CANCEL_REQUESTED,null)
        if(tracker.timedOut(token))return Result(Outcome.CANCEL_BLOCKED,blocked)
        return null
    }
}
