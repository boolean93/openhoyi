package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle

/** Retained entry point forwards to the single connection manager. */
class ScaleConnectionActivity : ThemedActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, DeviceConnectionActivity::class.java))
        finish()
    }
}
