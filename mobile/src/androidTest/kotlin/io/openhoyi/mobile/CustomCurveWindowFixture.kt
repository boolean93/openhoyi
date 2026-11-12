package io.openhoyi.mobile

/** Seed through the public writer in memory; retain the complete archive/shortcut state and never touch disk. */
internal class CustomCurveWindowFixture(private val store: CustomCurveStore) {
    private val state = CustomCurveStore::class.java.getDeclaredField("state").apply { isAccessible = true }
    private val original = state.get(store)

    fun replace(document: CustomCurveDocument?) {
        if (document == null) { restore(); return }
        val isolated = CustomCurveStore(object : CustomCurveStore.Storage {
            override fun read(): String? = null
            override fun write(value: String) = Unit
        })
        // State snapshots are immutable; save copies the document map before publishing a new state.
        state.set(isolated, original)
        isolated.save(document)
        state.set(store, state.get(isolated))
    }

    fun restore() { state.set(store, original) }
}
