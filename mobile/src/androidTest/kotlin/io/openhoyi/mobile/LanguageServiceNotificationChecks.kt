package io.openhoyi.mobile

import android.app.Instrumentation
import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.os.SystemClock

/** Tests original Service methods on a detached object, never the live BLE/Mock owner. */
internal class LanguageServiceNotificationChecks(private val test: Instrumentation, private val home: HomeActivity) {
    private class DisplayContext(base: Context, private val app: MobileApplication) : ContextWrapper(base) {
        var lookups = 0
        var failLookup = false
        override fun getApplicationContext(): Context = this
        override fun getResources(): Resources = app.resources
        override fun getSystemService(name: String): Any? {
            check(name != BLUETOOTH_SERVICE) { "Detached notification test must never access Bluetooth" }
            if (name == NOTIFICATION_SERVICE) {
                lookups++
                if (failLookup) throw SecurityException("Intentional display-context failure")
            }
            return super.getSystemService(name)
        }
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            check(name == "machine_write_safety") { "Unexpected detached Service preference: $name" }
            return super.getSharedPreferences("notification_refresh_fixture", mode)
        }
        override fun startService(service: Intent): ComponentName? = error("No component dispatch in notification test")
        override fun startForegroundService(service: Intent): ComponentName? = error("No component dispatch in notification test")
        override fun bindService(service: Intent, connection: ServiceConnection, flags: Int): Boolean = error("No component dispatch in notification test")
        override fun startActivity(intent: Intent) = error("No component dispatch in notification test")
        override fun startActivity(intent: Intent, options: android.os.Bundle?) = error("No component dispatch in notification test")
        override fun stopService(service: Intent): Boolean = error("No component dispatch in notification test")
    }

    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }

    private fun field(name: String) = MobileService::class.java.getDeclaredField(name).apply { isAccessible = true }
    private fun awaitState(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Detached Service notification state timed out" }
            Thread.sleep(50)
        }
    }

    fun run() {
        val context = test.targetContext
        check(BuildConfig.MOCK_MODE && context.packageName == "io.openhoyi.mobile.mock")
        val app = context.applicationContext as MobileApplication
        val manager = context.getSystemService(NotificationManager::class.java)
        check(manager.areNotificationsEnabled())
        fun notes() = manager.activeNotifications.filter { it.tag == null && it.id in listOf(1, 2) }
        check(notes().isEmpty()) { "Must not overwrite preexisting Mock Service notifications" }
        val fixturePrefs = context.getSharedPreferences("notification_refresh_fixture", Context.MODE_PRIVATE)
        check(fixturePrefs.all.isEmpty()) { "Preexisting detached fixture preferences" }
        val originalChannels = manager.notificationChannels.associateBy { it.id }
        val oldLanguage = app.languagePreferences.current
        val languagePrefs = context.getSharedPreferences("app_language", Context.MODE_PRIVATE)
        val oldLanguageStorage = languagePrefs.all.toMap()
        val preserved = listOf("devices", "curves", "presets", "shot_safety", "machine_write_safety", "shot_history")
            .associateWith { context.getSharedPreferences(it, Context.MODE_PRIVATE).all.toMap() }
        val displayContext = DisplayContext(context, app)
        lateinit var subject: MobileService
        lateinit var owner: MobileService
        var subjectCreated = false
        var ownerAvailable = false
        var liveMock: Any? = null
        var subjectMock: Any? = null
        val normalRefresh = MobileService::class.java.getDeclaredMethod("refreshSafetyNotification", Boolean::class.javaPrimitiveType)
            .apply { isAccessible = true }
        var failure: Throwable? = null
        try {
            onMain {
                owner = HomeActivity::class.java.getDeclaredField("service").apply { isAccessible = true }.get(home) as MobileService
                liveMock = field("mock").get(owner)
                ownerAvailable = true
                check(liveMock != null && field("hub").get(owner) == null)
                subject = MobileService()
                subjectCreated = true
                subjectMock = field("mock").get(subject)
                check(subject !== owner)
                // Protected SDK Context attachment only; never Service.attach/onCreate/onStartCommand.
                ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                    .apply { isAccessible = true }.invoke(subject, displayContext)
                check(field("hub").get(subject) == null)
                check(subjectMock != null)
                subject.refreshNotificationDisplay()
                check(displayContext.lookups == 0)
                field("running").setBoolean(subject, true)
                subject.refreshNotificationDisplay()
                check(displayContext.lookups == 0) { "Mock eligibility gate was bypassed" }
                // Remove only this detached object's Mock projection, leaving hub=null and no lifecycle.
                field("mock").set(subject, null)
                check(field("mock").get(subject) == null && field("hub").get(subject) == null)
                field("running").setBoolean(subject, false)
                subject.refreshNotificationDisplay()
                check(displayContext.lookups == 0) { "Stopped eligibility gate was bypassed" }
                field("running").setBoolean(subject, true)
                field("manualSafetyResource").set(subject, R.string.machine_recovery_shot_restart)
            }
            val snapshot = subject.snapshot
            var keys: Set<String>? = null
            for (language in AppLanguage.entries) {
                var expectedWarning = ""
                var expectedAction = ""
                onMain {
                    check(app.languagePreferences.select(language) != AppLanguagePreference.Selection.SAVE_FAILED)
                    val expected = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(language.locale) })
                    expectedWarning = expected.getString(R.string.machine_recovery_shot_restart)
                    expectedAction = expected.getString(R.string.notification_disconnect)
                    check(subject.resources.configuration.locales[0] == language.locale)
                    subject.refreshNotificationDisplay()
                    check(subject.snapshot === snapshot && field("hub").get(subject) == null)
                }
                awaitState {
                    val active = notes()
                    active.size == 2 && active.all { it.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString() == expectedWarning } &&
                        active.single { it.id == 1 }.notification.actions.single().title.toString() == expectedAction &&
                        (active.single { it.id == 2 }.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0
                }
                val active = notes()
                check(active.all { it.packageName == context.packageName })
                val current = active.map { it.key }.toSet()
                if (keys == null) keys = current else check(keys == current)
                onMain {
                    val before = displayContext.lookups
                    normalRefresh.invoke(subject, false)
                    check(displayContext.lookups == before) { "Same-warning regular refresh did not deduplicate" }
                }
                val previousPosts = notes().associate { it.id to it.postTime }
                check(previousPosts.size == 2)
                Thread.sleep(50) // Distinguish platform timestamps for an otherwise identical warning.
                onMain {
                    val before = displayContext.lookups
                    subject.refreshNotificationDisplay()
                    check(displayContext.lookups >= before + 2) { "Display-only refresh skipped its inner manager lookup" }
                    displayContext.failLookup = true
                    subject.refreshNotificationDisplay() // The production public method must catch this failure.
                    displayContext.failLookup = false
                    check(subject.snapshot === snapshot && field("mock").get(owner) === liveMock && owner.running)
                }
                awaitState {
                    val refreshed = notes()
                    refreshed.size == 2 && refreshed.all { it.postTime > requireNotNull(previousPosts[it.id]) }
                }
                preserved.forEach { (name, values) -> check(context.getSharedPreferences(name, Context.MODE_PRIVATE).all == values) }
            }
            onMain {
                field("manualSafetyResource").set(subject, null)
                subject.refreshNotificationDisplay()
            }
            awaitState { notes().map { it.id } == listOf(1) }
            check(fixturePrefs.all.isEmpty()) { "Display refresh mutated detached recovery preferences" }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            val cleanupErrors = mutableListOf<Throwable>()
            fun attempt(action: () -> Unit) { runCatching(action).exceptionOrNull()?.let { cleanupErrors += it } }
            attempt { manager.cancel(1) }
            attempt { manager.cancel(2) }
            attempt { awaitState { notes().isEmpty() } }
            onMain {
                attempt { check(app.languagePreferences.select(oldLanguage) != AppLanguagePreference.Selection.SAVE_FAILED) }
                attempt { restoreMockPreferences(languagePrefs, oldLanguageStorage) }
                if (subjectCreated) {
                    attempt { field("running").setBoolean(subject, false); field("mock").set(subject, subjectMock) }
                    attempt { check(field("hub").get(subject) == null) }
                }
                if (ownerAvailable) attempt { check(field("mock").get(owner) === liveMock && owner.running) }
            }
            attempt { check(context.deleteSharedPreferences("notification_refresh_fixture")) }
            attempt { manager.notificationChannels.filter { it.id !in originalChannels }.forEach { channel -> attempt { manager.deleteNotificationChannel(channel.id) } } }
            originalChannels.values.forEach { channel -> attempt { manager.createNotificationChannel(channel) } }
            if (cleanupErrors.isNotEmpty()) {
                val target = failure ?: IllegalStateException("Detached Service notification cleanup failed")
                cleanupErrors.forEach(target::addSuppressed)
                if (failure == null) throw target
            }
        }
    }
}
