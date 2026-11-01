package io.openhoyi.mobile

import android.content.Intent
import android.os.Bundle

/** Compatibility route: connection management now belongs to the settings document. */
class DeviceConnectionActivity : ThemedActivity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, TaskDestination.SETTINGS.name)
            .putExtra("settingsSection", "devices"))
        finish()
    }
}
