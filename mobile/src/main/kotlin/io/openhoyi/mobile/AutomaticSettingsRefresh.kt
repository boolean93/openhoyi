package io.openhoyi.mobile

/** One automatic read per stale observation. A user can request another read after cooldown. */
internal class AutomaticSettingsRefresh {
    private var hub:Any?=null
    private var generation:Long?=null
    private var observation:Long?=null
    private var firstSafePoll:Long?=null
    private var spent=false
    private var lastReadAt: Long? = null
    fun hasSpent(owner:Any,gen:Long,observationId:Long):Boolean =
        hub===owner && generation==gen && observation==observationId && spent

    fun claim(owner:Any,gen:Long,observationId:Long?,now:Long,safe:Boolean,explicit:Boolean=false):Boolean {
        if(hub!==owner || generation!=gen || observation!=observationId) {
            hub=owner;generation=gen;observation=observationId;firstSafePoll=null;spent=false;lastReadAt=null
        }
        if(!safe){firstSafePoll=null;return false}
        if (explicit) {
            if (lastReadAt?.let { now - it < 30_000L } == true) return false
        } else {
            if (spent) return false
            val first = firstSafePoll
            if (first == null) { firstSafePoll = now; return false }
            if (now - first < 350L) return false
        }
        lastReadAt = now
        spent=true
        return true
    }
}

/** A temporary settings notice follows only the source that owned the refresh. */
internal class AutomaticSettingsSyncNotice(
    private val source: Any,
    private val sequenceAtStart: Long,
    private val startedAt: Long,
    private val expiresAt: Long,
) {
    enum class Status { WAITING_FOR_NEW_REPORT, OBSERVED }

    fun status(currentSource: Any?, reportSource: Any?, reportSequence: Long?, reportReceivedAt: Long?,
        reportCurrentAndFresh: Boolean, now: Long): Status? {
        if (source !== currentSource || now >= expiresAt) return null
        return if (reportSource === source && reportSequence != null && reportSequence > sequenceAtStart &&
            reportReceivedAt != null && reportReceivedAt > startedAt && reportCurrentAndFresh) Status.OBSERVED
            else Status.WAITING_FOR_NEW_REPORT
    }
}
