package io.openhoyi.mobile

/** Main-thread process signal, separate from device intent/state callbacks.
 * Creation/binding alone is NOT availability: only a fully started owner announces itself.
 */
internal class ServiceAvailability {
    private var owner: String? = null
    private val observers = mutableSetOf<(Boolean) -> Unit>()
    val available get() = owner != null

    fun started(ownerId: String) {
        if (owner == ownerId) return
        owner = ownerId
        observers.toList().forEach { it(true) }
    }

    fun stopped(ownerId: String) {
        if (owner != ownerId) return
        owner = null
        observers.toList().forEach { it(false) }
    }

    fun observe(observer: (Boolean) -> Unit) {
        observers += observer
        observer(available)
    }

    fun unobserve(observer: (Boolean) -> Unit) { observers -= observer }
}

/** Actual Activity binding lease; notifications never create/start a service or retry in a loop. */
internal class VisibleServiceBinding(
    private val availability: ServiceAvailability,
    private val bind: (create: Boolean) -> Boolean,
    private val unbind: () -> Unit,
    private val onReleased: () -> Unit,
    private val onAvailable: () -> Unit
) {
    private var active = false
    var bound = false
        private set
    private val changed: (Boolean) -> Unit = { available ->
        if (active) {
            if (available) {
                bindExisting()
                onAvailable()
            } else release()
        }
    }

    fun start() {
        if (active) return
        active = true
        availability.observe(changed)
    }

    fun bindExisting() = request(create = false)
    fun bindCreated() = request(create = true)
    private fun request(create: Boolean): Boolean {
        if (!active) return false
        if (!bound) bound = bind(create)
        return bound
    }

    fun release() {
        if (bound) {
            bound = false
            unbind()
        }
        onReleased()
    }

    fun stop() {
        active = false
        availability.unobserve(changed)
        release()
    }
}
