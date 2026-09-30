package io.openhoyi.session

/** Identity reported after authentication, separate from permission to control a machine. */
data class CoffeeFirmware(val major: Int, val minor: Int, val patch: Int) {
    init { require(major in 0..255 && minor in 0..15 && patch in 0..15) }
    override fun toString(): String = "$major.$minor.$patch"
}
