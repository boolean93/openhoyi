package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Factory checks only: never posts a notification or sends a PendingIntent, and never refreshes BLE. */
internal class LanguageNotificationChecks(private val test: Instrumentation) {
    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val oldLanguage = app.languagePreferences.current
        val prefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldStorage = prefs.all.toMap()
        val home = test.startActivitySync(Intent(context, HomeActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
        test.waitForIdleSync()
        var owner: MobileService? = null
        val deadline = SystemClock.elapsedRealtime() + 10000
        while (owner == null) {
            test.runOnMainSync {
                owner = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService
            }
            check(owner != null || SystemClock.elapsedRealtime() < deadline) { "Notification factory service unavailable" }
            if (owner == null) Thread.sleep(50)
        }
        val service = requireNotNull(owner)
        val factory = MobileService::class.java.getDeclaredMethod("connectionNotification", String::class.java)
            .apply { isAccessible = true }
        val channels = MobileService::class.java.getDeclaredMethod("createNotificationChannels", NotificationManager::class.java)
            .apply { isAccessible = true }
        val manager = context.getSystemService(NotificationManager::class.java)
        val originalChannels = manager.notificationChannels.associateBy { it.id }
        var channelIds: Set<String>? = null
        try {
            for (language in AppLanguage.entries) test.runOnMainSync {
                check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(language.locale) }
                val expected = context.createConfigurationContext(config)
                val initialMessage = service.snapshot.message
                channels.invoke(service, manager)
                val current = manager.notificationChannels
                if (channelIds == null) channelIds = current.map { it.id }.toSet()
                check(current.map { it.id }.toSet() == channelIds)
                check(current.any { it.name.toString() == expected.getString(R.string.home_device_connection) })
                check(current.any { it.name.toString() == expected.getString(R.string.notification_safety_channel) })
                val running = factory.invoke(service, null) as Notification
                val warning = expected.getString(R.string.notification_attention)
                val alert = factory.invoke(service, warning) as Notification
                check(running.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == expected.getString(R.string.notification_running))
                check(alert.extras.getCharSequence(Notification.EXTRA_TITLE).toString() == warning)
                check(alert.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == warning)
                for (notification in listOf(running, alert)) {
                    check(notification.actions.size == 1)
                    check(notification.actions[0].title.toString() == expected.getString(R.string.notification_disconnect))
                    check(notification.actions[0].actionIntent.creatorPackage == context.packageName)
                    check(notification.contentIntent.creatorPackage == context.packageName)
                }
                val display = MobileNotificationDisplay(service)
                val direct = display.connection(null, "connections")
                check(direct.channelId == running.channelId && direct.flags == running.flags &&
                    direct.extras.getCharSequence(Notification.EXTRA_TEXT).toString() ==
                        running.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
                for (destination in listOf(HomeActivity::class.java, ExtractionActivity::class.java))
                    for (displayOnly in listOf(false, true)) {
                        val safety = display.safety(warning, destination, "shot_safety", displayOnly)
                        check(safety.channelId == "shot_safety" && safety.category == Notification.CATEGORY_ALARM)
                        check(safety.extras.getCharSequence(Notification.EXTRA_TITLE).toString() == expected.getString(R.string.notification_check_machine))
                        check(safety.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == warning &&
                            safety.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString() == warning)
                        check((safety.flags and Notification.FLAG_ONGOING_EVENT) != 0)
                        check(((safety.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0) == displayOnly)
                        val expectedOpen = android.app.PendingIntent.getActivity(service, 2, Intent(service, destination),
                            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT)
                        check(safety.contentIntent == expectedOpen && safety.contentIntent.creatorPackage == context.packageName)
                    }
                check(service.snapshot.message === initialMessage)
            }
        } finally {
            test.runOnMainSync {
                check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED)
                restoreMockPreferences(prefs, oldStorage)
                manager.notificationChannels.filter { it.id !in originalChannels }.forEach { manager.deleteNotificationChannel(it.id) }
                originalChannels.values.forEach { manager.createNotificationChannel(it) }
            }
        }
    }
}
