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
    fun machine(context:Context):MachineWriteRecoveryState.Storage {
        val prefs=context.getSharedPreferences("machine_write_safety",Context.MODE_PRIVATE)
        fun kind(raw:String?)=raw?.let { runCatching { MachineWriteRecoveryState.Kind.valueOf(it) }.getOrDefault(MachineWriteRecoveryState.Kind.UNKNOWN) }
        val pending:(MachineWriteRecoveryState.Record)->Boolean={ it.pending || it.address!=null }
        val barrier=RecoveryPersistenceBarrier(object:RecoveryPersistenceBarrier.RecordStorage<MachineWriteRecoveryState.Record> {
            override fun read()=MachineWriteRecoveryState.Record(kind(prefs.getString("pending_kind",null)),prefs.getString("pending_address",null))
            override fun write(value:MachineWriteRecoveryState.Record)=prefs.edit().putString("pending_kind",value.kind?.name)
                .putString("pending_address",value.address).commit()
        },JsonMarker(File(context.noBackupFilesDir,"pending_machine_write.json"),
            { value->JSONObject().put("kind",value.kind?.name ?: JSONObject.NULL).put("address",value.address ?: JSONObject.NULL) },
            { json->MachineWriteRecoveryState.Record(kind(if(json.isNull("kind"))null else json.getString("kind")),json.address()) },pending),pending)
        return object:MachineWriteRecoveryState.Storage {
            override fun read()=barrier.read()
            override fun write(record:MachineWriteRecoveryState.Record)=barrier.write(record)
        }
    }
}
