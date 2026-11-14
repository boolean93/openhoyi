package io.openhoyi.mobile

import android.content.Context
import android.content.res.Configuration

/** A semantic palette: stage index never determines whether a target is pressure or flow. */
internal class CurveEditorColors(private val dark: Boolean) {
    constructor(context: Context) : this(context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    val pressureFill = if (dark) 0xFF245C9D.toInt() else 0xFF176BD1.toInt()
    val flowFill = if (dark) 0xFF176A43.toInt() else 0xFF087C45.toInt()
    val pressureInk = if (dark) 0xFFA9CDFF.toInt() else 0xFF125FB8.toInt()
    val flowInk = if (dark) 0xFF85E3B2.toInt() else 0xFF086D3D.toInt()
    val pressureTint = if (dark) 0xFF1B314C.toInt() else 0xFFEAF2FF.toInt()
    val flowTint = if (dark) 0xFF193B2C.toInt() else 0xFFE6F5EC.toInt()
    fun fill(flow: Boolean) = if (flow) flowFill else pressureFill
    fun ink(flow: Boolean) = if (flow) flowInk else pressureInk
    fun tint(flow: Boolean) = if (flow) flowTint else pressureTint
}
