package io.openhoyi.mobile

/** Main-thread process state. Display reconstruction is not a new foreground session.
 * Owners are logical page IDs restored across configuration replacement, never Activity objects. */
internal class AppVisibility {
    private val started = mutableSetOf<String>()
    private val rebuilding = mutableSetOf<String>()
    private val observers = mutableMapOf<String, (Boolean) -> Unit>()
    val visible: Boolean get() = started.isNotEmpty() || rebuilding.isNotEmpty()

    fun observe(owner: String, observer: (Boolean) -> Unit) {
        observers[owner] = observer
        observer(visible)
    }
    fun unobserve(owner: String) { observers.remove(owner) }

    fun started(owner: String) = change {
        rebuilding.remove(owner)
        started.add(owner)
    }
    fun stopped(owner: String, changingConfiguration: Boolean) = change {
        val wasStarted = started.remove(owner)
        if (changingConfiguration) {
            if (wasStarted) rebuilding.add(owner)
        } else rebuilding.remove(owner)
    }
    fun destroyed(owner: String, changingConfiguration: Boolean) = stopped(owner, changingConfiguration)

    private inline fun change(update: () -> Unit) {
        val before = visible
        update()
        val after = visible
        if (before != after) observers.values.toList().forEach { it(after) }
    }
}
