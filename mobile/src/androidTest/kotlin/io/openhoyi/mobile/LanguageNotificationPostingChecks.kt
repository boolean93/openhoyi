package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock

/** Real Android posting in the isolated Mock UID; never dispatches a notification action. */
internal class LanguageNotificationPostingChecks(private val test: Instrumentation) {
    private fun awaitCondition(checkState: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!checkState()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Test notification update timed out" }
            Thread.sleep(50)
        }
    }
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
        var service: MobileService? = null
        awaitCondition {
            test.runOnMainSync {
                service = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as? MobileService
            }
            service != null
        }
        val owner = requireNotNull(service)
        val display = MobileNotificationDisplay(owner)
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled()) { "Mock POST_NOTIFICATIONS permission was not granted" }
        val tag = "OpenHoyiLanguageTest"
        val connectionId = 91001
        val safetyId = 91002
        check(manager.activeNotifications.none { it.tag == tag }) { "Existing test-tag notifications must not be overwritten" }
        val channels = manager.notificationChannels.associateBy { it.id }
        var keys: Set<String>? = null
        var primaryFailure: Throwable? = null
        try {
            test.runOnMainSync { display.createChannels(manager, "connections", "shot_safety") }
            for (language in AppLanguage.entries) {
                var runningText = ""
                var warning = ""
                var safetyTitle = ""
                var actionText = ""
                var message: SnapshotMessage? = null
                test.runOnMainSync {
                    check(!ShotGate.active(owner.shotState))
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                    val config = android.content.res.Configuration(context.resources.configuration).apply { setLocale(language.locale) }
                    val expected = context.createConfigurationContext(config)
                    runningText = expected.getString(R.string.notification_running)
                    warning = expected.getString(R.string.notification_attention)
                    safetyTitle = expected.getString(R.string.notification_check_machine)
                    actionText = expected.getString(R.string.notification_disconnect)
                    message = owner.snapshot.message
                    manager.notify(tag, connectionId, display.connection(null, "connections"))
                    manager.notify(tag, safetyId, display.safety(warning, ExtractionActivity::class.java, "shot_safety", true))
                }
                awaitCondition {
                    val posted = manager.activeNotifications.filter { it.tag == tag }
                    val connection = posted.singleOrNull { it.id == connectionId }?.notification
                    val safety = posted.singleOrNull { it.id == safetyId }?.notification
                    posted.size == 2 && connection?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() == runningText &&
                        connection.actions.single().title.toString() == actionText &&
                        safety?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString() == safetyTitle &&
                        safety.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() == warning
                }
                val posted = manager.activeNotifications.filter { it.tag == tag }
                if (keys == null) keys = posted.map { it.key }.toSet()
                check(posted.map { it.key }.toSet() == keys) { "Language update duplicated the notifications" }
                check(posted.all { it.packageName == context.packageName })
                test.runOnMainSync { check(owner.snapshot.message === message) }
            }
            manager.cancel(tag, safetyId)
            awaitCondition { manager.activeNotifications.none { it.tag == tag && it.id == safetyId } }
            check(manager.activeNotifications.any { it.tag == tag && it.id == connectionId })
        } catch (error: Throwable) {
            primaryFailure = error
            throw error
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            fun attempt(action: () -> Unit) {
                try { action() } catch (error: Throwable) { cleanupErrors.add(error) }
            }
            attempt { manager.cancel(tag, connectionId) }
            attempt { manager.cancel(tag, safetyId) }
            attempt { awaitCondition { manager.activeNotifications.none { it.tag == tag } } }
            attempt {
                test.runOnMainSync {
                    attempt { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) }
                    attempt { restoreMockPreferences(prefs, oldStorage) }
                }
            }
            attempt {
                manager.notificationChannels.filter { it.id !in channels }.forEach { channel ->
                    attempt { manager.deleteNotificationChannel(channel.id) }
                }
            }
            channels.values.forEach { channel -> attempt { manager.createNotificationChannel(channel) } }
            if (cleanupErrors.isNotEmpty()) {
                val primary = primaryFailure
                if (primary != null) cleanupErrors.forEach { primary.addSuppressed(it) }
                else throw IllegalStateException("Mock notification cleanup failed").apply {
                    cleanupErrors.forEach(::addSuppressed)
                }
            }
        }
    }
}
