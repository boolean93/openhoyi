package io.openhoyi.mobile

import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Process
import io.openhoyi.session.ExtractionState
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest

/** Data-only fixture across an actual OS update. Never starts an Activity, Service or BLE owner. */
internal class MockUpgradeChecks(private val test: Instrumentation) {
    private val app get() = test.targetContext.applicationContext as MobileApplication
    private val address = "AA:BB:CC:DD:EE:01"
    private val preferenceNames = listOf("appearance", "app_language", "brew_feedback", "curves", "presets",
        "devices", "coffee_credentials", "coffee_credential_failures", "shot_safety", "machine_write_safety", "shot_history", "safety")
    private val manifest get() = File(app.filesDir, "upgrade-fixture.json")
    private fun prefs(name: String) = app.getSharedPreferences(name, Context.MODE_PRIVATE)
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private fun preferenceDigests() = preferenceNames.associateWith { name ->
        digest(prefs(name).all.toSortedMap().entries.joinToString("\n") { (key, value) ->
            "$key:${value?.javaClass?.name}:$value"
        }.toByteArray(Charsets.UTF_8))
    }
    private fun fileDigests() = app.filesDir.walkTopDown().filter { it.isFile && it != manifest }
        .associate { it.relativeTo(app.filesDir).invariantSeparatorsPath to digest(it.readBytes()) }
    private fun onMain(action: () -> Unit) {
        var failure: Throwable? = null
        test.runOnMainSync { try { action() } catch (error: Throwable) { failure = error } }
        failure?.let { throw it }
    }
    fun run(phase: String) {
        check(BuildConfig.MOCK_MODE && app.packageName == "io.openhoyi.mobile.mock")
        val version = app.packageManager.getPackageInfo(app.packageName, 0).longVersionCode
        when (phase) {
            "seed" -> { check(version == 1L); seed() }
            "verify" -> { check(version == 2L); verify() }
            else -> error("Unknown Mock upgrade phase")
        }
    }
    private fun seed() {
        check(preferenceNames.all { prefs(it).all.isEmpty() }) { "Upgrade seed requires empty Mock preferences" }
        check(app.filesDir.listFiles().orEmpty().isEmpty()) { "Upgrade seed must not overwrite existing files" }
        check(prefs("appearance").edit().putBoolean("dark", true).commit())
        check(app.languagePreferences.select(AppLanguage.SPANISH) == AppLanguagePreference.Selection.APPLIED)
        check(app.feedbackPreferences.setEnabled(true))
        check(prefs("curves").edit().putString("selected", "factory-v3-001").commit())
        check(prefs("presets").edit().putString("slot_1", "factory-v3-002").commit())
        check(prefs("devices").edit().putString("scale", "AA:BB:CC:DD:EE:02").commit())
        check(prefs("safety").edit().putBoolean("asked_for_notifications", true).commit())
        check(CoffeeCredentialStore(app).save(address, "123456")) { "Fixture credential could not be stored" }
        check(prefs("coffee_credentials").all.values.none { it == "123456" }) { "Credential must be encrypted" }
        SharedPreferencesCoffeeFailureStore(app).write(address, 1)
        check(prefs("shot_safety").edit().putBoolean("unresolved_shot", true).putString("unresolved_shot_address", address).commit())
        check(prefs("machine_write_safety").edit().putString("pending_kind", "SETTING").putString("pending_address", address).commit())
        val now = System.currentTimeMillis()
        val ids = listOf("upgrade-ended", "upgrade-active").iterator()
        val history = ShotHistory(object : ShotHistory.Storage {
            override fun read() = prefs("shot_history").getString("entries_v1", "")!!
            override fun write(value: String) { check(prefs("shot_history").edit().putString("entries_v1", value).commit()) }
        }, newId = { ids.next() })
        history.begin("factory-v3-001", now - 40000, 1)
        history.transition(ExtractionState.ENDED_OBSERVED, "manual_stop", 3570, now - 20000)
        history.begin("factory-v3-002", now - 10000, 2)
        history.transition(ExtractionState.RUNNING, null, null, now - 9000)
        val points = listOf(ShotPoint(0, 10, 20, 0, 9100, 0, 0), ShotPoint(1000, 60, 30, 40, 9100, 3570, 100))
        val samples = ShotSamplesStore(File(app.filesDir, "shot_samples"))
        samples.save("upgrade-ended", points)
        samples.save("upgrade-active", points)
        File(app.filesDir, "shot_samples/samples-upgrade-orphan.tsv")
            .writeText("# openhoyi-shot-points-v1\n0\t10\t20\t0\t9100\t0\n1000\t60\t30\t40\t9100\t3570\n")
        app.legacyCurves.import(ByteArrayInputStream("""{"format":"openhoyi-legacy-curves-v1","factoryVersion":3,"items":[{"name":"Upgrade fixture","category":"mine","factory":false,"temp":93,"flow":70,"weight":360,"seg":2,"press1":9,"flow1":30,"press2":6,"flow2":40,"futureField":true}]}""".toByteArray()))
        app.legacyHistory.import(ByteArrayInputStream("""{"version":1,"items":[{"id":"upgrade-legacy","createdAt":$now,"durationSec":18,"chartSlot":6,"profileName":"Upgrade fixture","points":{"t":[0,1],"press":[0,2],"flow":[0,1],"wFlow":[0,1],"wTrend":[0,1]}}]}""".toByteArray()))
        manifest.writeText(JSONObject().put("schema", 1).put("uid", Process.myUid())
            .put("preferences", JSONObject(preferenceDigests())).put("files", JSONObject(fileDigests())).toString())
        android.util.Log.i("OpenHoyiUpgrade", "SEED preferences=12 encryptedCredential=true filesPreservedFixture=true pendingSafety=true")
    }
    private fun verify() {
        check(manifest.isFile) { "Upgrade seed manifest is missing" }
        val expected = JSONObject(manifest.readText())
        check(expected.getInt("schema") == 1 && expected.getInt("uid") == Process.myUid()) { "Upgrade UID changed" }
        fun compare(field: String, actual: Map<String, String>) {
            val prior = expected.getJSONObject(field)
            val keys = prior.keys().asSequence().toSet()
            check(keys.isNotEmpty() && actual.keys.containsAll(keys)) { "Upgrade state disappeared" }
            keys.forEach { key -> check(prior.getString(key) == actual[key]) { "Upgrade $field content changed: $key" } }
        }
        // Inspect original bytes before loading models that intentionally reconcile interrupted history.
        compare("preferences", preferenceDigests())
        compare("files", fileDigests())
        check(CoffeeCredentialStore(app).read(address.lowercase()) == "123456") { "Encrypted fixture credential did not survive" }
        check(CoffeeCredentialRetryGate(store = SharedPreferencesCoffeeFailureStore(app)).mayUse(address))
        check(SharedPreferencesCoffeeFailureStore(app).read(address) == 1)
        check(app.languagePreferences.current == AppLanguage.SPANISH && app.feedbackPreferences.enabled)
        val entries = app.history.entries
        check(entries.size == 2)
        check(entries.single { it.id == "upgrade-ended" }.let { it.status == ShotHistory.Status.ENDED && it.weightHundredthsGram == 3570 })
        check(entries.single { it.id == "upgrade-active" }.let { it.status == ShotHistory.Status.UNKNOWN && it.endedAtMs == null })
        check(app.samples.load("upgrade-ended").last().weightHundredthsGram == 3570)
        check(app.samples.load("upgrade-active").size == 2 && app.samples.load("upgrade-orphan").size == 2)
        check(app.legacyCurves.load()?.curves?.single()?.name == "Upgrade fixture")
        check(app.legacyHistory.list().single().id == "upgrade-legacy")
        onMain {
            val guardedContext = object : ContextWrapper(app) {
                override fun getSystemService(name: String): Any? {
                    check(name != BLUETOOTH_SERVICE) { "Upgrade check may not access Bluetooth" }
                    return super.getSystemService(name)
                }
                override fun startService(intent: Intent) = error("No Service dispatch in upgrade checks")
                override fun startForegroundService(intent: Intent) = error("No Service dispatch in upgrade checks")
                override fun bindService(intent: Intent, connection: ServiceConnection, flags: Int) = error("No component dispatch in upgrade checks")
                override fun startActivity(intent: Intent) = error("No Activity dispatch in upgrade checks")
                override fun startActivity(intent: Intent, options: android.os.Bundle?) = error("No Activity dispatch in upgrade checks")
                override fun stopService(intent: Intent) = error("No Service dispatch in upgrade checks")
            }
            val subject = MobileService()
            ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java)
                .apply { isAccessible = true }.invoke(subject, guardedContext)
            val mockField = MobileService::class.java.getDeclaredField("mock").apply { isAccessible = true }
            val originalMock = mockField.get(subject)
            check(originalMock != null)
            try {
                // A detached Mock Service constructs a runtime, but never owns a BLE/session lifecycle.
                mockField.set(subject, null)
                for (field in listOf("hub", "mock")) check(MobileService::class.java.getDeclaredField(field)
                    .apply { isAccessible = true }.get(subject) == null)
                check(subject.machineControlSafetyMessage != null && subject.machineWriteSafetyMessage != null)
            } finally { mockField.set(subject, originalMock) }
        }
        check(prefs("shot_safety").getBoolean("unresolved_shot", false))
        check(prefs("machine_write_safety").getString("pending_kind", null) == "SETTING")
        android.util.Log.i("OpenHoyiUpgrade", "VERIFY uid=true encryptedCredential=true history=true samplesV1V2=true importedFiles=true pendingControlBlocked=true noOwner=true")
    }
}
