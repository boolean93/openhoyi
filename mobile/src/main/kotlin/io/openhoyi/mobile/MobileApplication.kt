package io.openhoyi.mobile

import android.app.Application
import android.content.Context
import android.content.res.Resources
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.openhoyi.trace.TraceStore
import java.io.File

/** Process-owned writer, independent of Activity and service restarts. */
class MobileApplication : Application() {
    internal lateinit var languagePreferences: AppLanguagePreference
        private set
    private lateinit var languageContext: AppLanguageContextProvider
    internal val visibility = AppVisibility()
    internal val feedbackPreferences by lazy {
        BrewFeedbackPreference(object : BrewFeedbackPreference.Storage {
            override fun read(): Boolean = getSharedPreferences("brew_feedback", MODE_PRIVATE).getBoolean("enabled", false)
            override fun write(enabled: Boolean): Boolean = getSharedPreferences("brew_feedback", MODE_PRIVATE)
                .edit().putBoolean("enabled", enabled).commit()
        })
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MOCK_MODE) visibility.observe("mock-lifecycle-diagnostics") {
            android.util.Log.i("OpenHoyiLifecycle", if (it) "visible" else "hidden")
        }
        registerActivityLifecycleCallbacks(AppVisibilityCallbacks(visibility))
    }

    override fun attachBaseContext(base: Context) {
        val prefs = base.getSharedPreferences("app_language", MODE_PRIVATE)
        languagePreferences = AppLanguagePreference(object : AppLanguagePreference.Storage {
            override fun read(): String? = prefs.getString("tag", null)
            override fun write(tag: String): Boolean = prefs.edit().putString("tag", tag).commit()
        })
        languageContext = AppLanguageContextProvider(base) { languagePreferences.current }
        super.attachBaseContext(base)
    }
    override fun getResources(): Resources =
        if (::languageContext.isInitialized) languageContext.context().resources else super.getResources()

    val logs: TraceStore by lazy { TraceStore(File(filesDir, "traces")) }
    val curves: CurveLibrary by lazy {
        val factory = FactoryCurveCatalog.load(assets.open("factory_curves_v3.tsv"))
        val proof = runCatching {
            FactoryWireProof.load(assets.open("factory_wire_v1.tsv"), factory,
                assets.open("factory_slot_wire_v1.tsv"))
        }
            .onFailure { logs.record("factory.wire_proof_failed", mapOf("type" to it.javaClass.simpleName)) }
            .getOrNull()
        CurveLibrary(factory, proof)
    }
    val samples: ShotSamplesRepository by lazy {
        ShotSamplesRepository(File(filesDir, "shot_samples")) { error ->
            logs.record("shot.samples_error", mapOf("type" to error.javaClass.simpleName))
        }
    }
    val history: ShotHistory by lazy {
        val prefs = getSharedPreferences("shot_history", MODE_PRIVATE)
        ShotHistory(object : ShotHistory.Storage {
            override fun read(): String = prefs.getString("entries_v1", "") ?: ""
            override fun write(value: String) {
                check(prefs.edit().putString("entries_v1", value).commit()) { "Cannot save shot history" }
            }
        }).also { samples.prune(it.entries.map(ShotHistory.Entry::id).toSet()) }
    }
    val legacyHistory: LegacyHistoryStore by lazy { LegacyHistoryStore(File(filesDir, "legacy_history.json")) }
    val legacyCurves: LegacyCurveStore by lazy { LegacyCurveStore(File(filesDir, "legacy_curves.json")) }
    fun importLegacyHistory(uri: Uri) {
        Thread({
            val result = runCatching {
                val input = contentResolver.openInputStream(uri) ?: error("No input stream")
                legacyHistory.import(input)
            }
            logs.record(if (result.isSuccess) "legacy.import_finished" else "legacy.import_failed",
                mapOf("result" to result.fold({ "${it.added} added, ${it.total} total" },
                    { it.javaClass.simpleName })))
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, result.fold(
                    { getString(R.string.legacy_history_import_result, it.added.toString(), it.total.toString()) },
                    { getString(R.string.legacy_import_error, it.message ?: it.javaClass.simpleName) }),
                    Toast.LENGTH_LONG).show()
            }
        }, "legacy-history-import").start()
    }
    fun export(uri: Uri) {
        logs.record("ui.export")
        try {
            logs.export({ contentResolver.openOutputStream(uri, "wt") ?: error("No output stream") }) { error ->
                val message = if (error == null) "操作记录已导出" else "导出失败：${error.javaClass.simpleName}"
                logs.record("export.finished", mapOf("message" to message))
                Handler(Looper.getMainLooper()).post {
                    // Keep diagnostic log text stable; resolve the UI text using current resources.
                    val displayMessage = if (error == null) getString(R.string.export_trace_success)
                        else getString(R.string.export_trace_failed, error.javaClass.simpleName)
                    Toast.makeText(this, displayMessage, Toast.LENGTH_LONG).show()
                }
            }
        } catch (error: RuntimeException) {
            Toast.makeText(this, getString(R.string.export_trace_not_started, error.javaClass.simpleName), Toast.LENGTH_LONG).show()
        }
    }
    fun exportHistory(uri: Uri) {
        val entries = history.entries
        Thread({
            val result = runCatching {
                val stream = contentResolver.openOutputStream(uri, "wt") ?: error("No output stream")
                ShotHistoryArchive.write(entries, samples::load, stream)
            }
            logs.record(if (result.isSuccess) "history.export_finished" else "history.export_failed",
                mapOf("result" to result.fold({ "${it.records} records, ${it.sampleFiles} sample files, ${it.unavailable} unavailable" },
                    { it.javaClass.simpleName })))
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, result.fold(
                    { if (it.unavailable > 0) getString(R.string.export_history_partial,
                        it.records.toString(), it.sampleFiles.toString(), it.unavailable.toString())
                    else getString(R.string.export_history_success, it.records.toString(), it.sampleFiles.toString()) },
                    { getString(R.string.export_history_failed, it.javaClass.simpleName) }), Toast.LENGTH_LONG).show()
            }
        }, "history-export").start()
    }
}
