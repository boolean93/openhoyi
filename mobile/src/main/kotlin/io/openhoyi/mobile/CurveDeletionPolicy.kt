package io.openhoyi.mobile

/** Guard only the recipe owned by an active start/tare/shot/preparation, not the whole library. */
internal object CurveDeletionPolicy {
    fun inUse(id: String, dispatching: String?, shotCurve: String?, shotActive: Boolean,
              preparingCurve: String?, preparing: Boolean): Boolean =
        id == dispatching || (shotActive && id == shotCurve) || (preparing && id == preparingCurve)
}
