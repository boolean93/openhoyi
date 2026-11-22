package io.openhoyi.mobile

import android.content.Context
import io.openhoyi.session.MachineWriteRecoveryState
import io.openhoyi.session.RecoveryPersistenceBarrier
import io.openhoyi.session.ShotRecoveryState
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

/** Private complete intent snapshots; no protocol or acknowledgement decision lives here. */
internal object RecoveryPreferenceStorage {
    private class JsonMarker<T>(private val file:File,private val encode:(T)->JSONObject,
        private val decode:(JSONObject)->T,private val pending:(T)->Boolean):RecoveryPersistenceBarrier.Marker<T> {
        override fun exists()=file.exists()
        override fun read():T? {
            if(!file.exists())return null
            require(file.isFile && file.length() in 1..4096) { "Invalid recovery marker" }
            val value=decode(JSONObject(file.readText(Charsets.UTF_8)))
            require(pending(value)) { "Cleared record cannot be a pending marker" }
            return value
        }
        override fun write(value:T):Boolean {
            require(pending(value))
            val parent=requireNotNull(file.parentFile)
            if(!parent.isDirectory && !parent.mkdirs())return false
            FileOutputStream(file).use { it.write(encode(value).toString().toByteArray(Charsets.UTF_8));it.fd.sync() }
            return true
        }
        override fun clear()=!file.exists() || file.delete()
    }
    private fun JSONObject.address()=if(isNull("address"))null else getString("address")
    fun shot(context:Context):ShotRecoveryState.Storage {
        val prefs=context.getSharedPreferences("shot_safety",Context.MODE_PRIVATE)
        val pending:(ShotRecoveryState.Record)->Boolean={ it.pending || it.address!=null }
        val barrier=RecoveryPersistenceBarrier(object:RecoveryPersistenceBarrier.RecordStorage<ShotRecoveryState.Record> {
            override fun read()=ShotRecoveryState.Record(prefs.getBoolean("unresolved_shot",false),prefs.getString("unresolved_shot_address",null))
            override fun write(value:ShotRecoveryState.Record)=prefs.edit().putBoolean("unresolved_shot",value.pending)
                .putString("unresolved_shot_address",value.address).commit()
        },JsonMarker(File(context.noBackupFilesDir,"pending_shot.json"),
            { value->JSONObject().put("pending",value.pending).put("address",value.address ?: JSONObject.NULL) },
            { json->ShotRecoveryState.Record(json.getBoolean("pending"),json.address()) },pending),pending)
        return object:ShotRecoveryState.Storage {
            override fun read()=barrier.read()
            override fun write(record:ShotRecoveryState.Record)=barrier.write(record)
        }
    }
    private fun machineKind(raw:String?)=raw?.let {
        runCatching { MachineWriteRecoveryState.Kind.valueOf(it) }.getOrDefault(MachineWriteRecoveryState.Kind.UNKNOWN)
    }

    // Keep using the legacy top-level fields only. Unknown extensions are never authorization.
    internal fun decodeMachineMarker(json: JSONObject): MachineWriteRecoveryState.Record =
        MachineWriteRecoveryState.Record(machineKind(if (json.isNull("kind")) null else json.getString("kind")), json.address())

    private const val MachineMarker="pending_machine_write.json"

    /** Exclusive, process-local service writer; contention is an UNKNOWN fence, not empty state. */
    internal class PinnedMachine internal constructor(private val lease:MachineRecoveryMarkerLease.Lease?,
        private val delegate:MachineWriteRecoveryState.Storage?,
        private val cachedLegacyPending:()->Boolean={true}) : java.io.Closeable {
        private enum class Phase { LEGACY, LEGACY_READING, LEGACY_WRITING, OPENING_SPLIT, SPLIT, CLOSED }
        private var phase=Phase.LEGACY
        private var split:SplitMachineWriteFileStore?=null
        val held:Boolean get()=lease?.let {it.isOwnerThread && it.held}==true
        val storage=object:MachineWriteRecoveryState.Storage {
            override fun read():MachineWriteRecoveryState.Record {
                if(!held || phase!=Phase.LEGACY)return fenced()
                phase=Phase.LEGACY_READING
                return try { lease?.readWhileHeld { delegate?.read() } ?: fenced() }
                finally { phase=Phase.LEGACY }
            }
            override fun write(record:MachineWriteRecoveryState.Record):Boolean {
                if(!held || phase!=Phase.LEGACY)return false
                phase=Phase.LEGACY_WRITING
                return try { lease?.writeLegacy { delegate?.write(record)==true }==true }
                finally { phase=Phase.LEGACY }
            }
        }
        private fun fenced()=MachineWriteRecoveryState.Record(MachineWriteRecoveryState.Kind.UNKNOWN,null)
        /** Irreversible for this owner epoch. Do not mix old writers and a split view. */
        fun openSplitViewForV2(legacyDispatchIdle:()->Boolean,
            ops:SplitMachineWriteFileStore.FileOps?=null):SplitMachineWriteFileStore? {
            val owner=lease ?: return null
            if(!held || phase!=Phase.LEGACY)return null
            phase=Phase.OPENING_SPLIT
            try {
                val verifyEmpty={ !cachedLegacyPending() }
                if (!runCatching {legacyDispatchIdle() && verifyEmpty()}.getOrDefault(false)) return null
                // A typed split has no reason to overwrite an unrelated legacy intent cache.
                val view=if(ops!=null) SplitMachineWriteFileStore.fromPinnedLease(owner,cachedLegacyPending,verifyEmpty,ops)
                    else SplitMachineWriteFileStore.fromPinnedLease(owner,cachedLegacyPending,verifyEmpty)
                if(view==null)return null
                if(view.read()==io.openhoyi.session.SplitMachineWriteJournal.Snapshot.Blocked) {
                    view.close()
                    return null
                }
                split=view
                phase=Phase.SPLIT
                return view
            } finally {
                if(phase==Phase.OPENING_SPLIT)phase=Phase.LEGACY
            }
        }
        override fun close(){
            if(lease!=null)check(lease.isOwnerThread) { "Pinned writer must retire on its service owner thread" }
            if(phase==Phase.CLOSED)return
            check(phase==Phase.LEGACY || phase==Phase.SPLIT) { "Writer transition is still in progress" }
            split?.close() // throws while a split CAS is on this stack; retain the lease in that case
            phase=Phase.CLOSED
            lease?.close()
        }
    }

    fun pinMachine(context:Context):PinnedMachine {
        val lease=MachineRecoveryMarkerLease.tryAcquire(File(context.noBackupFilesDir,MachineMarker))
            ?: return PinnedMachine(null,null)
        return try {
            val prefs=context.getSharedPreferences("machine_write_safety",Context.MODE_PRIVATE)
            PinnedMachine(lease,machineBarrier(context,false,lease.marker)) {
                prefs.getString("pending_kind",null)!=null || prefs.getString("pending_address",null)!=null
            }
        } catch (error:Exception) {
            lease.close()
            throw error
        }
    }

    fun machine(context:Context, backgroundTemperature:Boolean=false):MachineWriteRecoveryState.Storage {
        val markerFile=File(context.noBackupFilesDir,
            if(backgroundTemperature) "pending_temperature_confirmation.json" else MachineMarker)
        val barrier=machineBarrier(context,backgroundTemperature,markerFile)
        return if(backgroundTemperature) barrier else object:MachineWriteRecoveryState.Storage {
            override fun read()=barrier.read()
            override fun write(record:MachineWriteRecoveryState.Record) =
                MachineRecoveryMarkerLease.withLegacyWrite(markerFile) { barrier.write(record) }
        }
    }

    private fun machineBarrier(context:Context,backgroundTemperature:Boolean,markerFile:File):MachineWriteRecoveryState.Storage {
        val namespace=if(backgroundTemperature) "temperature_confirmation" else "machine_write_safety"
        val prefs=context.getSharedPreferences(namespace,Context.MODE_PRIVATE)
        val pending:(MachineWriteRecoveryState.Record)->Boolean={ it.pending || it.address!=null }
        val barrier=RecoveryPersistenceBarrier(object:RecoveryPersistenceBarrier.RecordStorage<MachineWriteRecoveryState.Record> {
            override fun read()=MachineWriteRecoveryState.Record(machineKind(prefs.getString("pending_kind",null)),prefs.getString("pending_address",null))
            override fun write(value:MachineWriteRecoveryState.Record)=prefs.edit().putString("pending_kind",value.kind?.name)
                .putString("pending_address",value.address).commit()
        },JsonMarker(markerFile,
            { value->JSONObject().put("kind",value.kind?.name ?: JSONObject.NULL).put("address",value.address ?: JSONObject.NULL) },
            ::decodeMachineMarker,pending),pending)
        return object:MachineWriteRecoveryState.Storage {
            override fun read()=barrier.read()
            override fun write(record:MachineWriteRecoveryState.Record) = barrier.write(record)
        }
    }
}
