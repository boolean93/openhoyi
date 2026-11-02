package io.openhoyi.mobile

import io.openhoyi.bluetooth.AndroidDevice
import io.openhoyi.bluetooth.NativeDeviceHub

/** Returns the two real owners in a detached hub used by service instrumentation. */
internal fun hubDevice(hub: NativeDeviceHub, name: String): AndroidDevice {
    val fieldName = when (name) {
        "coffee" -> "coffee\$delegate" // Coffee owner is a Kotlin lazy property.
        "scale" -> "scale"
        else -> error("Unknown NativeDeviceHub owner: $name")
    }
    val field = NativeDeviceHub::class.java.getDeclaredField(fieldName).apply { isAccessible = true }
    val value = field.get(hub)
    return (if (name == "coffee") (value as Lazy<*>).value else value) as AndroidDevice
}
