package io.openhoyi.mobile

import android.content.Intent

/** Legacy explicit Activity intents enter the single task host without building a second root UI. */
internal fun ThemedActivity.redirectRoot(destination: TaskDestination, extraction: Boolean = false): Boolean {
    redirectedRoot = true
    val route = Intent(this, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(MainActivity.EXTRA_INITIAL_DESTINATION, destination.name)
    if (extraction) route.putExtra(MainActivity.EXTRA_OPEN_EXTRACTION, true)
    intent.getStringExtra(HistoryActivity.EXTRA_HISTORY_SOURCE)?.let {
        route.putExtra(HistoryActivity.EXTRA_HISTORY_SOURCE, it)
    }
    intent.getIntExtra(PresetSlots.EXTRA_SLOT, -1).takeIf { it >= 0 }?.let {
        route.putExtra(PresetSlots.EXTRA_SLOT, it)
    }
    startActivity(route)
    finish()
    return true
}
