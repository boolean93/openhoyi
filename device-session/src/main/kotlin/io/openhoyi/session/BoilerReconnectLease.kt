package io.openhoyi.session

/** In-memory, transaction-scoped suppression; never modifies the user's automatic preferences. */
class BoilerReconnectLease {
    var held=false
        private set
    fun claim():Boolean {
        if(held)return false
        held=true;return true
    }
    /** Only confirmed/reconciled readback or an explicit user connection releases suppression. */
    fun release(){held=false}
}
