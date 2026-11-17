package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.security.KeyStore

/** Explicit, disposable-emulator-only branch of the existing Mock runner. Never uses real credentials. */
internal class AiAndroidAcceptance(private val test: Instrumentation) {
    fun run(arguments: Bundle) {
        val report = Bundle()
        var stage = "preconditions"
        try {
            check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
            check(arguments.getString("disposableEmulator") == "true")
            check(Build.HARDWARE in setOf("ranchu", "goldfish"))
            val id = requireNotNull(arguments.getString("aiFixtureId"))
            check(id.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")))
            val phase = arguments.getString("aiPhase")
            check(phase in setOf("seed", "verify", "cleanup", "logs", "ui"))
            val credentials = AiAndroidCredentialChecks(test.targetContext)
            stage = phase ?: "missing-phase"
            val checks = when (phase) {
                "seed" -> credentials.seed(id)
                "verify" -> credentials.verifyAfterRestart(id)
                "cleanup" -> credentials.cleanup(id) + cleanupLogScope(id)
                "logs" -> logChecks(id)
                "ui" -> {
                    val sandbox = AiAndroidHttpSandbox.install()
                    val language = arguments.getString("aiLanguage") ?: "zh-Hans"
                    val font = arguments.getString("aiFont") ?: "medium"
                    val dark = arguments.getString("aiDark") ?: "false"
                    check(language in setOf("zh-Hans", "en") && font in setOf("small", "medium", "large"))
                    check(dark in setOf("true", "false"))
                    AiAndroidUiChecks(test, sandbox).run(id, language, font, dark == "true", arguments.getString("aiFlow") == "true")
                }
                else -> error("Unknown AI fixture phase")
            }
            report.putString("aiFixture", "passed")
            report.putString("phase", phase)
            report.putInt("checks", checks)
            report.putInt("appPid", Process.myPid())
            report.putString("stream", "AI_ANDROID_CHECKS_PASSED phase=$phase checks=$checks noNetwork=true noBle=true\n")
            test.finish(Activity.RESULT_OK, report)
        } catch (_: Throwable) {
            // Never send an assertion's original message/cause into logcat, am output or uploaded evidence.
            report.putString("aiFixture", "failed")
            report.putString("stage", stage)
            report.putInt("appPid", Process.myPid())
            report.putString("stream", "AI_ANDROID_CHECKS_FAILED stage=$stage\n")
            test.finish(Activity.RESULT_CANCELED, report)
        }
    }

    private fun logChecks(id: String): Int {
        val sandbox = AiAndroidHttpSandbox.install() // If interception cannot be installed, do not send.
        val context = test.targetContext
        val name = "ai_log_fixture_$id"
        val alias = "io.openhoyi.mobile.deepseek_api_key.log_fixture.$id"
        val storage = PreferencesAiCiphertextStorage(context, name)
        val keys = AndroidAiKeySource(alias)
        check(!storage.contains() && keys.existing() == null)
        val store = AiKeyStore(storage, keys)
        try {
            check(store.save("PRIVATE_KEY_SENTINEL-$id"))
            val revision = store.revision()
            AiAndroidLogChecks.run(sandbox) { store.readForRequest(revision) }
            return 5
        } finally {
            val deleted = store.delete()
            cleanupLogScope(id)
            check(deleted)
        }
    }
    private fun cleanupLogScope(id: String): Int {
        val context = test.targetContext
        val name = "ai_log_fixture_$id"
        val alias = "io.openhoyi.mobile.deepseek_api_key.log_fixture.$id"
        check(context.getSharedPreferences(name, android.content.Context.MODE_PRIVATE).edit().clear().commit())
        check(context.deleteSharedPreferences(name))
        KeyStore.getInstance("AndroidKeyStore").run { load(null); deleteEntry(alias); check(!containsAlias(alias)) }
        return 2
    }

}
