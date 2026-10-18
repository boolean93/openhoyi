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
        val body=scaleBody(R.string.scale_settings,R.string.scale_settings_subtitle,scrollable=true)
        if (HoyiUi.wide(this)) body.layoutParams = android.widget.FrameLayout.LayoutParams(
            HoyiUi.dp(this, 688), -2, android.view.Gravity.CENTER_HORIZONTAL)
        val prefs=getSharedPreferences("scale_tool",MODE_PRIVATE)
        val display = settingsGroup(this, body, getString(R.string.scale_display_precision))
        display.addView(Switch(this).apply {
            text=getString(R.string.scale_precision_short);
            contentDescription=getString(R.string.scale_precision);isChecked=prefs.getBoolean("one_decimal",false)
            textSize=TaskVisualTokens.BODY_SP.toFloat();typeface=TaskTypography.face(this@ScaleSettingsActivity,400);setTextColor(getColor(R.color.mobile_text));minimumHeight=HoyiUi.dp(this@ScaleSettingsActivity,56)
            setOnCheckedChangeListener {_,checked->prefs.edit().putBoolean("one_decimal",checked).apply()}
        })
        HoyiUi.label(this,display,getString(R.string.scale_precision_off_hint),TaskVisualTokens.SUPPORTING_SP,muted=true)
        val card=settingsGroup(this,body,getString(R.string.scale_capabilities_title))
        capabilities=HoyiUi.label(this,card,"",TaskVisualTokens.BODY_SP)
        val help=settingsGroup(this,body,getString(R.string.scale_usage_instructions))
        listOf(R.string.scale_timer_title to R.string.scale_timer_help,
            R.string.scale_operation_limits to R.string.scale_active_guard,
            R.string.scale_protocol_support to R.string.scale_offline_evidence).forEachIndexed { index, (title, detail) ->
            if(index>0) settingsDivider(this,help)
            HoyiUi.label(this,help,getString(title),TaskVisualTokens.BODY_SP,true).apply {
                setPadding(0,0,0,HoyiUi.dp(this@ScaleSettingsActivity,TaskVisualTokens.RELATED_GAP_DP))
            }
            HoyiUi.label(this,help,getString(detail),TaskVisualTokens.SUPPORTING_SP,muted=true)
        }
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
                getString(R.string.scale_capability_format,getString(name),getString(if(!available)R.string.scale_capability_no else if(snapshot?.scaleObservation?.evidence==io.openhoyi.protocol.ScaleEvidence.LIVE_READ_ONLY)R.string.scale_capability_read_only else R.string.scale_capability_yes))
            }
        }
    }
}
