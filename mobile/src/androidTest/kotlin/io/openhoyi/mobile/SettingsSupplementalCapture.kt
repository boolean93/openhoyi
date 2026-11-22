package io.openhoyi.mobile

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.io.File

/** Native lower panels and safe preview-only dialogs from the same already-proven Settings activity. */
internal class SettingsSupplementalCapture(
    private val test: Instrumentation, private val page: MainPageTestHost, private val out: File,
) {
    private val activity = page.activity
    private fun <T> main(block: () -> T): T = BoundedInstrumentationMain.run("Supplemental Settings capture", 4500, block)
    private fun await(label: String, condition: () -> Boolean) {
        val limit = SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < limit) { "Missing supplemental Settings view: $label" }
            SystemClock.sleep(30)
        }
        SystemClock.sleep(150)
    }
    private fun document() = page.active("root:SETTINGS").requireView() as SettingsDocumentLayout
    private fun view(tag: String): View = requireNotNull(document().findViewWithTag(tag))
    private fun shown(view: View) = view.isShown && view.getGlobalVisibleRect(Rect())
    private fun reveal(view: View) {
        main { view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true) }
        await("visible ${view.tag}") { main { shown(view) } }
    }
    private fun section(key: String) {
        main { (page.active("root:SETTINGS") as AppSettingsFragment).openSection(key) }
        await("heading $key") { main { view("settings/heading/$key").let { shown(it) && it.hasFocus() } } }
    }
    private val index = JSONObject()
    private fun capture(key: String, description: String) {
        val bitmap = requireNotNull(test.uiAutomation.takeScreenshot())
        try {
            File(out, "extra-$key.png").outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally { bitmap.recycle() }
        index.put("extra-$key.png", description)
    }

    fun run(): JSONObject {
        section("schedule")
        capture("standby", "机器：待机时间与温度")
        val day = main {
            val week = view("settings/machine/weekly-grid") as ViewGroup
            check(week.childCount == 7 && (0..6).all {
                val setting = week.getChildAt(it)
                setting.tag == "product/settings/machine/schedule/day/$it" && setting.isShown && setting.isClickable &&
                    setting.width >= HoyiUi.dp(activity, 48) && setting.height >= HoyiUi.dp(activity, 48)
            }) { "The wide weekly grid must expose all seven original days, once each" }
            week.getChildAt(6)
        }
        reveal(day)
        capture("weekly", "机器：每周睡眠计划与七天编辑入口")
        if (main { day.isEnabled }) {
            main { check(day.performClick()) }
            try {
                await("weekly editor") { accessibility { it.className.toString().endsWith("EditText") }?.let { it.recycle(); true } == true }
                capture("weekly-editor", "机器：单日睡眠与唤醒时间（未保存）")
            } finally {
                if (!main { activity.hasWindowFocus() }) back()
                await("weekly editor cancelled") { main { activity.hasWindowFocus() } }
            }
        }
        section("scale")
        val capabilities = main { view("settings/details/scale/action") }
        reveal(capabilities)
        main {
            check(capabilities.performClick())
            val list = view("settings/devices/scale-capabilities") as TextView
            check(list.text.toString().lines().size == 5 &&
                list.text.toString().lines().all { activity.getString(R.string.settings_unknown) in it }) {
                "Dashboard with no real observed device must show each capability as unknown"
            }
        }
        reveal(main { view("settings/details/scale/content") })
        capture("scale-capabilities", "设备：秤精度、能力和使用说明")
        main { check(capabilities.performClick()) }

        section("appearance")
        val language = main { view("product/settings/app/language/current") }
        reveal(language)
        capture("appearance-lower", "外观：应用语言与字号")
        section("maintenance")
        val cups = main { view("settings/details/cups/action") }
        reveal(cups)
        main { check(cups.performClick()) }
        reveal(main { view("product/settings/machine/cup-count") })
        capture("cups", "维护：累计杯数与原重置入口（未重置）")
        main { check(cups.performClick()) }

        val details = main { view("settings/details/maintenance/action") }
        reveal(details)
        main { check(details.performClick()) }
        reveal(main { view("product/settings/app/diagnostic-storage/clear") })
        capture("diagnostics", "维护：导出、诊断存储和停止服务（均未执行）")
        main { check(details.performClick()) }

        section("about")
        reveal(main { view("product/settings/app/update/check") })
        capture("updates", "维护：当前版本、检查更新及自动检查（未触发）")
        captureAi()
        return index
    }

    private fun nodeMatches(root: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (root.isVisibleToUser && predicate(root)) return root
        for (i in 0 until root.childCount) {
            val child = root.getChild(i) ?: continue
            val match = nodeMatches(child, predicate)
            if (match != null) {
                if (match !== child) child.recycle()
                return match
            }
            child.recycle()
        }
        return null
    }
    private fun accessibility(predicate: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val root = test.uiAutomation.rootInActiveWindow ?: return null
        val result = nodeMatches(root, predicate)
        if (result !== root) root.recycle()
        return result
    }
    private fun visibleText(label: String) = accessibility { it.text?.contains(label) == true }
        ?.let { it.recycle(); true } == true
    private fun back() = test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
    private fun tree(root: View): Sequence<View> = sequence {
        yield(root)
        if (root is ViewGroup) for (i in 0 until root.childCount) yieldAll(tree(root.getChildAt(i)))
    }

    private fun captureAi() {
        section("ai")
        check(main { AiKeyStore(activity).status() == AiKeyStatus.MISSING })
        fun captureChat(route: String, title: String?, shot: String, description: String) {
            val monitor = test.addMonitor(AiChatActivity::class.java.name, null, false)
            var chat: AiChatActivity? = null
            try {
                val action = main { view(route) }
                reveal(action)
                main { check(action.performClick()) }
                val screen = test.waitForMonitorWithTimeout(monitor, 10_000) as? AiChatActivity
                    ?: error("Original HOYI.AI screen did not open: $route")
                chat = screen
                if (title == null) await("AI chat") { main { screen.hasWindowFocus() } }
                else await(title) { visibleText(title) }
                capture(shot, description)
                if (title != null) back()
            } finally {
                test.removeMonitor(monitor)
                main { chat?.finish() }
            }
            await("returned to original Settings host") { main { activity.hasWindowFocus() } }
        }
        captureChat("settings/ai-assistant", null, "ai-chat", "HOYI.AI：真实聊天与菜单入口（未发消息）")
        captureChat("settings/ai/model", activity.getString(R.string.ai_model_choose_title), "ai-model",
            "HOYI.AI：模型选择（未更改）")
        captureChat("settings/ai/reasoning", activity.getString(R.string.ai_reasoning_title), "ai-reasoning",
            "HOYI.AI：思考强度（未更改）")
        captureChat("settings/ai/usage", activity.getString(R.string.ai_usage_title), "ai-usage",
            "HOYI.AI：用量统计（空数据）")
        val keyMonitor = test.addMonitor(AiKeyActivity::class.java.name, null, false)
        var key: AiKeyActivity? = null
        try {
            val action = main { view("settings/ai/key") }
            reveal(action)
            main { check(action.performClick()) }
            val keyPage = test.waitForMonitorWithTimeout(keyMonitor, 10_000) as? AiKeyActivity
                ?: error("Original AI Key screen did not open")
            key = keyPage
            await("AI Key has no credential") { main {
                keyPage.hasWindowFocus() && AiKeyStore(keyPage).status() == AiKeyStatus.MISSING &&
                    tree(keyPage.window.decorView).filterIsInstance<EditText>().toList().let { inputs ->
                        inputs.isNotEmpty() && inputs.all { it.text.isEmpty() }
                    }
            } }
            capture("ai-key", "HOYI.AI：API Key 未配置；自动评价为非 Mock 版")
        } finally {
            test.removeMonitor(keyMonitor)
            main { key?.finish() }
        }
        await("returned from AI Key") { main { activity.hasWindowFocus() } }
    }
}
