package io.openhoyi.mobile

import java.security.MessageDigest
import java.util.Base64

/** App-private evidence only. A row can annotate a matching shot, never create or change one. */
internal object ShotTimingSidecar {
    const val KEY = "machine_timing_evidence_v1"
    data class Identity(val id: String, val curveId: String, val startedAtMs: Long)
    data class Evidence(val interrupted: Boolean, val seconds: Int?)

    fun encode(rows: Map<Identity, Evidence>): String {
        val body = rows.entries.joinToString("\n") { (identity, evidence) ->
            listOf(pack(identity.id), pack(identity.curveId), identity.startedAtMs.toString(),
                evidence.interrupted.toString(), evidence.seconds?.toString().orEmpty()).joinToString("\t")
        }
        return "1\t${checksum(body)}\n$body"
    }

    fun decode(raw: String?): Map<Identity, Evidence> {
        if (raw == null) return emptyMap()
        val header = raw.substringBefore('\n')
        val body = raw.substringAfter('\n', "")
        require(header == "1\t${checksum(body)}") { "Invalid timing evidence checksum or version" }
        return buildMap {
            if (body.isNotEmpty()) body.lineSequence().forEach { line ->
                val (id, curve, at, interrupted, seconds) = line.split('\t').also { require(it.size == 5) }
                val identity = Identity(unpack(id), unpack(curve), at.toLong())
                require(identity.id.isNotBlank() && identity.curveId.isNotBlank() && identity.startedAtMs >= 0)
                val evidence = Evidence(interrupted.toBooleanStrict(), seconds.takeUnless(String::isEmpty)?.toInt())
                require(evidence.seconds == null || evidence.interrupted && evidence.seconds in 0..65535)
                require(put(identity, evidence) == null)
            }
        }
    }

    fun ShotHistory.Entry.identity() = Identity(id, curveId, startedAtMs)
    fun BrewJournal.Observation.identity() = Identity(id, curveId, startedAtMs)

    private fun checksum(body: String) = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(MessageDigest.getInstance("SHA-256").digest(body.toByteArray(Charsets.UTF_8)))
    private fun pack(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun unpack(value: String) = String(Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
}

/** Read-only consumers never initialize or recover the mutable application stores. */
internal fun readPersistedShotTimingEvidence(context: android.content.Context) = runCatching {
    val application = context.applicationContext
    val raw = if (application is MobileApplication) application.shotHistoryStorage.readTiming()
        else context.getSharedPreferences("shot_history", android.content.Context.MODE_PRIVATE)
            .getString(ShotTimingSidecar.KEY, null)
    ShotTimingSidecar.decode(raw)
}.getOrDefault(emptyMap())

/**
 * SharedPreferences may publish a failed commit into its own process memory. Keep
 * successful evidence separately and explicitly reassert it on every main write.
 */
internal class CommittedShotHistoryStorage(
    private val readMain: () -> String,
    readSidecar: () -> String?,
    private val commitBoth: (String, String?) -> Boolean,
) : ShotHistory.AtomicTimingStorage {
    private var committedSidecar = runCatching(readSidecar)

    override fun read(): String = readMain()

    @Synchronized
    override fun readTiming(): String? = committedSidecar.getOrThrow()

    @Synchronized
    override fun write(value: String) = commit(value, readTiming())

    @Synchronized
    override fun writeWithTiming(value: String, timing: String) = commit(value, timing)

    private fun commit(value: String, timing: String?) {
        check(commitBoth(value, timing)) { "Cannot save shot history and timing evidence" }
        committedSidecar = Result.success(timing)
    }
}
