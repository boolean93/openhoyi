package io.openhoyi.mobile

import android.os.Looper
import android.view.View
import android.widget.LinearLayout

internal object MockBleSameIdQaRow {
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
        val button = HoyiUi.button(activity, card, action) {
            check(Looper.myLooper() === Looper.getMainLooper())
            result.text = MockBleSameIdFixture.run(activity.applicationContext).render(chinese)
            result.visibility = View.VISIBLE
        }
        button.tag = "settings/maintenance/ble-v2-qa"
        style.add(card, result, 4)
    }
}
