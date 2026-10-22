package io.openhoyi.mobile

import io.openhoyi.protocol.StartParameters

/** Lossless pressure-only mapping, independently compared with the legacy encoder.
 * Constructing a profile does not register a frame or authorize a device command.
 */
object CustomPressureCurveAdapter {
    fun profile(document:CustomCurveDocument,scaleConnected:Boolean,slot:Int=7):CurveProfile? {
        try {document.validate()} catch(_:IllegalArgumentException) {return null}
        if(document.controlMode!=CustomCurveDocument.ControlMode.PRESSURE ||
            (slot !in 1..5 && slot!=7) || document.targetHundredthsGram%10!=0 ||
            document.stages.any {it.waterTenthsMl%10!=0})return null
        val stages=document.stages.toList()
        val totalMl=stages.sumOf {it.waterTenthsMl/10}
        val maximumMl=if(scaleConnected && document.targetHundredthsGram>0)
            maxOf(totalMl,(document.targetHundredthsGram+24)/25) else totalMl
        val targets=(stages.map {it.target}+List(4-stages.size){0})
        val water=(stages.map {it.waterTenthsMl}+List(4-stages.size){0})
        return CurveProfile(document.id,document.name,"",if(scaleConnected)document.targetHundredthsGram else 0,
            StartParameters(false,false,stages.size,slot,document.temperatureC,maximumMl,false,0,
                targets[0],targets[1],targets[2],targets[3],water[0],0,water[1],water[2],water[3]),
            scaleMode=scaleConnected)
    }
}
