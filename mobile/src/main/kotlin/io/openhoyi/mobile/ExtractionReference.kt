package io.openhoyi.mobile

/** A display projection only. Alignment cannot create phase evidence or change raw samples. */
internal object ExtractionReference {
    data class Timeline(val points:List<ShotPoint>,val observedOrigin:Boolean)
    fun align(raw:List<ShotPoint>):Timeline {
        require(raw.all {it.elapsedMs>=0} && raw.zipWithNext().all {(a,b)->a.elapsedMs<b.elapsedMs})
        if(raw.isEmpty()) return Timeline(emptyList(),false)
        val actual=raw.firstOrNull {it.brewing==true}
        val origin=actual?.elapsedMs ?: raw.firstOrNull {it.brewing==null}?.elapsedMs
            ?: return Timeline(emptyList(),false)
        return Timeline(raw.filter {it.elapsedMs>=origin}.map {it.copy(elapsedMs=it.elapsedMs-origin)},actual!=null)
    }
}
