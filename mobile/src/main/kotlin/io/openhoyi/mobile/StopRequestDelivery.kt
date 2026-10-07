package io.openhoyi.mobile

/** Optional display failures must never prevent an already qualified stop submission. */
internal object StopRequestDelivery {
    fun request(dispatch:()->Unit,report:()->Unit) {
        dispatch()
        try { report() } catch (_:Exception) { /* No retries or recovery mutations for display. */ }
    }
}
