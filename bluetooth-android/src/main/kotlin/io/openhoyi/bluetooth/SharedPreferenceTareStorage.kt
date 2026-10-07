package io.openhoyi.bluetooth

import android.content.Context
import io.openhoyi.session.StandaloneTare
import java.io.File
import java.io.FileOutputStream

/** Sync crash reminder. The separate marker survives commit(false) changing only cached preferences. */
class SharedPreferenceTareStorage(context:Context,
    private val marker:File=File(context.noBackupFilesDir,"scale_tare_pending")):StandaloneTare.Storage {
    private val preferences=context.getSharedPreferences("scale_tare_safety",Context.MODE_PRIVATE)
    private val barrier=io.openhoyi.session.TarePersistenceBarrier(object:StandaloneTare.Storage {
        override fun read()=preferences.getBoolean("unresolved_tare",false)
        override fun write(pending:Boolean)=preferences.edit().putBoolean("unresolved_tare",pending).commit()
    },object:io.openhoyi.session.TarePersistenceBarrier.Marker {
        override fun exists()=marker.exists()
        override fun arm():Boolean {
            val parent=requireNotNull(marker.parentFile)
            if(!parent.isDirectory && !parent.mkdirs())return false
            FileOutputStream(marker).use { it.write(1);it.fd.sync() }
            return true
        }
        override fun clear()=marker.delete()
    })
    override fun read():Boolean=barrier.read()
    override fun write(pending:Boolean):Boolean=barrier.write(pending)
}
