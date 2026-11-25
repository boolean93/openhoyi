package io.openhoyi.mobile

/** An already-started native gesture cannot choose a new source/bit or upgrade a stale touch. */
internal object BoilerSwitchGestureGate {
    fun allows(rendered:Any?,current:Any?,down:Any?,touching:Boolean):Boolean =
        rendered!=null && rendered==current && (!touching || down==rendered)
}
