package io.openhoyi.mobile

import android.os.Bundle

/** Standalone entry retains the same controller as the inline settings editor. */
class MachineSettingsActivity : ThemedActivity() {
    private lateinit var controls: MachineSettingsControls
    private val visual by lazy { SettingsVisualStyle(this) }
    val productLayoutEvidence get() = visual.evidence
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val body = visual.page(this, R.string.machine_settings_title, R.string.machine_settings_subtitle,
            pageId = "machine", persistentScrollIndicator = true)
        controls = MachineSettingsControls(this, visual)
        controls.create(body, savedInstanceState)
    }
    override fun onStart() { super.onStart(); controls.start() }
    override fun onStop() { controls.stop(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { controls.save(outState); super.onSaveInstanceState(outState) }
}
