package io.openhoyi.mobile

import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout

internal object MockBleSameIdQaRow {
    private var running = false

    fun attach(activity: MainActivity, card: LinearLayout, style: TaskProductVisualStyle) {
        if (!BuildConfig.MOCK_MODE || !BuildConfig.BLE_SAME_ID_QA ||
            activity.packageName != "io.openhoyi.mobile.mock") return
        val chinese = activity.resources.configuration.locales[0].language == "zh"
        val action = if (chinese) "蓝牙 V2 模拟验收" else "Bluetooth V2 Mock check"
        val result = style.text("", TaskProductVisualStyle.auxiliary, "settings/maintenance/ble-v2-qa-result").apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setTextIsSelectable(true)
        }
        val button = HoyiUi.button(activity, card, action) {}
        button.tag = "settings/maintenance/ble-v2-qa"
        button.setOnClickListener {
            check(Looper.myLooper() === Looper.getMainLooper())
            if (running) return@setOnClickListener
            val cases = MockBleSameIdFixture.cases(activity.applicationContext)
            val completed = mutableListOf<MockBleSameIdFixture.Outcome>()
            val handler = Handler(Looper.getMainLooper())
            running = true
            button.isEnabled = false
            result.visibility = View.VISIBLE
            fun finish() {
                running = false
                button.isEnabled = true
            }
            val task = object : Runnable {
                override fun run() {
                    if (!button.isAttachedToWindow || activity.isFinishing || activity.isDestroyed) {
                        finish()
                        return
                    }
                    if (completed.size == cases.size) {
                        try {
                            result.text = MockBleSameIdFixture.Result(
                                completed, MockBleSameIdFixture.boundaryChecks()
                            ).render(chinese)
                        } finally {
                            finish()
                        }
                        return
                    }
                    try {
                        completed += cases[completed.size]()
                    } catch (failure: Error) {
                        finish()
                        throw failure
                    }
                    result.text = if (chinese) "隔离模拟中 ${completed.size}/${cases.size}"
                        else "Running detached fixture ${completed.size}/${cases.size}"
                    if (!handler.post(this)) finish()
                }
            }
            if (!handler.post(task)) finish()
        }
        style.add(card, result, 4)
    }
}
