package io.openhoyi.mobile

import io.openhoyi.session.DeviceRole

/** Display intent is independent of asynchronous BLE results and permission callbacks. */
internal class DeviceScanSelection {
    private var role: DeviceRole? = null
    fun showAll() { role = null }
    fun showRole(value: DeviceRole) { role = value }
    fun includes(value: DeviceRole): Boolean = role == null || role == value
}
