package io.openhoyi.mobile

import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.SplitMachineWriteJournal
import org.json.JSONArray
import org.json.JSONObject

/** Wire-compatible candidate for the existing pending_machine_write.json marker; not wired. */
internal object SplitMachineWriteMarkerCodec {
    private const val EXTENSION = "ownership_v2"
    private const val VERSION = 1

    fun encode(entries: List<SplitMachineWriteJournal.Entry>): JSONObject {
        require(SplitMachineWriteJournal.valid(entries))
        return JSONObject().put("kind", MachineWriteRecoveryState.Kind.UNKNOWN.name)
            .put("address", JSONObject.NULL)
            .put(EXTENSION, JSONObject().put("version", VERSION).put("entries", JSONArray(entries.map {
                JSONObject().put("operation", it.kind.name).put("address", it.address)
            })))
    }

    /** An absent marker alone cannot prove Empty; the legacy preferences may still be pending. */
    fun decode(json: JSONObject): SplitMachineWriteJournal.Snapshot = runCatching {
        require(json.getString("kind") == MachineWriteRecoveryState.Kind.UNKNOWN.name &&
            json.has("address") && json.isNull("address"))
        val extension = json.getJSONObject(EXTENSION)
        require(extension.get("version") == VERSION)
        val values = extension.getJSONArray("entries")
        require(values.length() in 1..SplitMachineWriteJournal.Kind.entries.size)
        val entries = List(values.length()) { index ->
            val entry = values.getJSONObject(index)
            SplitMachineWriteJournal.Entry(SplitMachineWriteJournal.Kind.valueOf(entry.getString("operation")), entry.getString("address"))
        }
        require(SplitMachineWriteJournal.valid(entries))
        SplitMachineWriteJournal.Snapshot.Pending(entries)
    }.getOrDefault(SplitMachineWriteJournal.Snapshot.Blocked)

}
