package io.openhoyi.mobile

import android.os.Bundle
import android.widget.Switch
import android.widget.TextView
import io.openhoyi.session.DeviceState

/** App-local display preferences only. No fictional device feature toggles. */
class ScaleSettingsActivity:ScaleOwnerActivity() {
    private lateinit var capabilities:TextView
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        val body=scaleBody(R.string.scale_settings,R.string.scale_settings_subtitle)
        val prefs=getSharedPreferences("scale_tool",MODE_PRIVATE)
        body.addView(Switch(this).apply {
            text=getString(R.string.scale_precision);isChecked=prefs.getBoolean("one_decimal",false)
            textSize=16f;setTextColor(getColor(R.color.mobile_text));minimumHeight=HoyiUi.dp(this@ScaleSettingsActivity,56)
            setOnCheckedChangeListener {_,checked->prefs.edit().putBoolean("one_decimal",checked).apply()}
        })
        val card=HoyiUi.card(this,body,getString(R.string.scale_capabilities_title))
        capabilities=HoyiUi.label(this,card,"",15)
        HoyiUi.label(this,body,getString(R.string.scale_timer_help),14,muted=true)
        HoyiUi.label(this,body,getString(R.string.scale_active_guard),14,muted=true)
        HoyiUi.label(this,body,getString(R.string.scale_offline_evidence),14,muted=true)
        renderScale()
    }
    override fun renderScale() {
        if(!::capabilities.isInitialized)return
        val snapshot=owner?.snapshot
        val actual=snapshot?.scaleCapabilities?.takeIf {snapshot.scaleState==DeviceState.READY && !BuildConfig.MOCK_MODE}
        capabilities.text=if(actual==null)getString(R.string.scale_capability_no_device) else {
            listOf(R.string.scale_capability_weight to actual.weight,R.string.scale_capability_tare to actual.tare,
                R.string.scale_capability_flow to actual.deviceFlow,R.string.scale_capability_battery to actual.battery,
                R.string.scale_capability_timer to actual.deviceTimer).joinToString("\n") {(name,available)->
                getString(R.string.scale_capability_format,getString(name),getString(if(available)R.string.scale_capability_yes else R.string.scale_capability_no))
            }
        }
    }
}
