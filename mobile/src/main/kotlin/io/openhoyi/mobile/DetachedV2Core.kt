package io.openhoyi.mobile

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.res.Resources
import io.openhoyi.bean.Bean
import io.openhoyi.bean.BeanBatch
import io.openhoyi.bean.BeanInventory
import io.openhoyi.bean.InventoryStorage
import io.openhoyi.bluetooth.DetachedNativeDrivers
import io.openhoyi.session.GattDriver
import io.openhoyi.session.GattOperation
import java.io.File

/** Internal Mock test inputs; there is no exported component or production call site. */
internal class DetachedCoreClock {
    private data class Job(val at:Long,val action:()->Unit)
    private val jobs=mutableListOf<Job>()
    var now=10L
        private set
    fun schedule(delay:Long,action:()->Unit) {
        require(delay in 0L..600_000L && jobs.size<32)
        jobs+=Job(now+delay,action)
    }
    fun advance(delta:Long,tick:()->Unit) {
        require(delta in 0L..60_000L && now+delta<=240_000L)
        now+=delta
        tick()
        repeat(64) {
            val next=jobs.minByOrNull {it.at}?.takeIf {it.at<=now} ?: return
            jobs.remove(next)
            next.action()
        }
        error("Detached clock exceeded callback budget")
    }
    fun close()=jobs.clear()
}

internal class DetachedCoreDriver : GattDriver {
    data class Call(val generation:Long,val token:Long,val operation:GattOperation)
    private val submitted=mutableListOf<Call>()
    val calls:List<Call> get()=submitted.toList()
    override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean {
        check(submitted.size<96)
        submitted+=Call(generation,token,operation)
        return true
    }
    override fun close(generation:Long)=Unit
}

/** No attached platform base. Any non-explicit Android operation fails rather than escalating. */
internal class DetachedCoreContext(
    private val strings:Resources,
    private val root:File,
):ContextWrapper(null) {
    private val stores=mutableMapOf<String,MemoryPreferences>()
    override fun getApplicationContext():Context=this
    override fun getPackageName()="io.openhoyi.mobile.mock"
    override fun getResources():Resources=strings
    override fun getCacheDir()=root
    override fun getNoBackupFilesDir()=File(root,"noBackup")
    override fun getSharedPreferences(name:String,mode:Int):SharedPreferences {
        require(mode==MODE_PRIVATE && name in setOf("shot_safety","machine_write_safety","temperature_confirmation","devices"))
        return stores.getOrPut(name,::MemoryPreferences)
    }
    override fun getSystemService(name:String):Any=error("No system service in detached fixture")
    override fun checkSelfPermission(permission:String):Int=error("No permission access in detached fixture")
    override fun getSystemServiceName(serviceClass:Class<*>):String=error("No system service in detached fixture")
}

private class MemoryPreferences:SharedPreferences {
    private val entries=mutableMapOf<String,Any>()
    override fun getAll():MutableMap<String,*> = entries.toMutableMap()
    override fun contains(key:String)=entries.containsKey(key)
    override fun getString(key:String,defValue:String?)=entries[key] as String? ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key:String,defValues:MutableSet<String>?)=
        (entries[key] as Set<String>?)?.toMutableSet() ?: defValues
    override fun getInt(key:String,defValue:Int)=entries[key] as Int? ?: defValue
    override fun getLong(key:String,defValue:Long)=entries[key] as Long? ?: defValue
    override fun getFloat(key:String,defValue:Float)=entries[key] as Float? ?: defValue
    override fun getBoolean(key:String,defValue:Boolean)=entries[key] as Boolean? ?: defValue
    override fun registerOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener)=
        error("No observer in detached fixture")
    override fun unregisterOnSharedPreferenceChangeListener(listener:SharedPreferences.OnSharedPreferenceChangeListener)=
        error("No observer in detached fixture")

    override fun edit():SharedPreferences.Editor=object:SharedPreferences.Editor {
        private val changed=mutableMapOf<String,Any?>()
        private var clear=false
        override fun putString(key:String,value:String?)=apply { changed[key]=value }
        override fun putStringSet(key:String,values:MutableSet<String>?)=apply { changed[key]=values?.toSet() }
        override fun putInt(key:String,value:Int)=apply { changed[key]=value }
        override fun putLong(key:String,value:Long)=apply { changed[key]=value }
        override fun putFloat(key:String,value:Float)=apply { changed[key]=value }
        override fun putBoolean(key:String,value:Boolean)=apply { changed[key]=value }
        override fun remove(key:String)=apply { changed[key]=null }
        override fun clear()=apply { clear=true }
        override fun commit():Boolean {
            if(clear)entries.clear()
            changed.forEach { (key,value)-> if(value==null)entries.remove(key) else entries[key]=value }
            return true
        }
        override fun apply() { commit() }
    }
}

internal fun detachedDrivers(driver:DetachedCoreDriver,clock:DetachedCoreClock)=DetachedNativeDrivers(driver,
    object:GattDriver {
        override fun execute(generation:Long,token:Long,operation:GattOperation):Boolean=error("Scale operations not in core fixture")
        override fun close(generation:Long)=Unit
    },{clock.now})

/** No FOLLOW_LINKS; deleting a leaf symlink never deletes its target. */
internal fun deleteDetachedCoreRoot(root:File) {
    val tree=java.nio.file.Files.walk(root.toPath()).use { stream -> stream.limit(257).iterator().asSequence().toList() }
    check(tree.size<=256)
    tree.sortedByDescending {it.nameCount}.forEach(java.nio.file.Files::delete)
}

/** Only isolated shot side effects; the real service still owns authorization and readback. */
internal class DetachedShotEffects(private val clock: DetachedCoreClock) {
    private var shotRows = ""
    private var timingRows: String? = null
    private var samplesById = emptyMap<String, Int>()
    private var nextId = 0
    val beans = BeanPreparation(object : BeanPreparation.Storage {
        private var bytes: String? = null
        override fun read() = bytes
        override fun write(value: String) { bytes = value }
    })
    val inventory = BeanInventory(object : InventoryStorage {
        private var data: ByteArray? = null
        override fun read() = data
        override fun write(bytes: ByteArray) { data = bytes }
    })
    val journal = BrewJournal(object : BrewJournal.Storage {
        private var data: String? = null
        override fun read() = data
        override fun write(value: String) { data = value }
    })

    fun prepareDose() {
        inventory.addBatch("synthetic-purchase", BeanBatch("synthetic-batch", Bean("synthetic-bean", "QA coffee"), 100_000))
        beans.select("synthetic-dose", "synthetic-batch", "synthetic-bean", "QA coffee", 18_000)
        beans.confirm(inventory)
    }

    fun reconcileBeans() {
        journal.reconcileRecent(history.entries.map { it.journalObservation() })
        beans.associatePending(journal,inventory)
    }

    val history = ShotHistory(object : ShotHistory.AtomicTimingStorage {
        override fun read() = shotRows
        override fun readTiming() = timingRows
        override fun write(value: String) { shotRows = value }
        override fun writeWithTiming(value: String, timing: String) {
            shotRows = value
            timingRows = timing
        }
    }, now = { 1_700_000_000_000L + clock.now }, newId = {"synthetic-shot-${++nextId}"},
        onEntryChanged = { entry ->
            journal.observe(entry.journalObservation())
            if (beans.current()?.shotId == entry.id) beans.associatePending(journal, inventory)
        })

    fun recordSamples(shotId: String, pointCount: Int) {
        check(history.entries.any { it.id == shotId } && pointCount >= 0)
        samplesById = samplesById + (shotId to pointCount)
    }
    val persistedSampleCount get() = samplesById.values.sum()
}
