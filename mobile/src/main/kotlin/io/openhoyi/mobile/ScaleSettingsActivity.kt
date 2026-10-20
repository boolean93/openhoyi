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
        val visual=SettingsVisualStyle(this)
        val style=visual.palette
        // This page retains ScaleOwner's original binding/refresh lifecycle; only its shell is opt-in.
        val body=visual.page(this,R.string.scale_settings,R.string.scale_settings_subtitle,688,"scale")
        val prefs=getSharedPreferences("scale_tool",MODE_PRIVATE)
        val display = settingsGroup(this, body, getString(R.string.scale_display_precision),visual)
        display.addView(Switch(this).apply {
            text=getString(R.string.scale_precision_short);
            contentDescription=getString(R.string.scale_precision);isChecked=prefs.getBoolean("one_decimal",false)
            textSize=TaskVisualTokens.BODY_SP.toFloat();typeface=TaskTypography.face(this@ScaleSettingsActivity,400);setTextColor(getColor(R.color.mobile_text));minimumHeight=HoyiUi.dp(this@ScaleSettingsActivity,56)
            visual.switch(this);tag="product/settings/scale/precision"
            setOnCheckedChangeListener {_,checked->prefs.edit().putBoolean("one_decimal",checked).apply()}
        })
        settingsDivider(this,display,visual)
        style.add(display,style.text(getString(R.string.scale_precision_off_hint),TaskProductVisualStyle.auxiliary,secondary=true))
        val card=settingsGroup(this,body,getString(R.string.scale_capabilities_title),visual)
        capabilities=style.text("",TaskProductVisualStyle.body,"product/settings/scale/capabilities")
        val capabilityRow=style.row().apply {gravity=android.view.Gravity.CENTER_VERTICAL}
        capabilityRow.addView(android.widget.ImageView(this).apply {
            setImageDrawable(HomeVisualIcon.drawable(this@ScaleSettingsActivity,"scale",style.muted))
            importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        },android.widget.LinearLayout.LayoutParams(style.dp(24),style.dp(24)).apply {marginEnd=style.dp(16)})
        capabilityRow.addView(capabilities,android.widget.LinearLayout.LayoutParams(0,-2,1f));style.add(card,capabilityRow)
        val help=settingsGroup(this,body,getString(R.string.scale_usage_instructions),visual)
        listOf(R.string.scale_timer_title to R.string.scale_timer_help,
            R.string.scale_operation_limits to R.string.scale_active_guard,
            R.string.scale_protocol_support to R.string.scale_offline_evidence).forEachIndexed { index, (title, detail) ->
            if(index>0) settingsDivider(this,help,visual)
            style.add(help,style.text(getString(title),TaskProductVisualStyle.action,"product/settings/scale/help/$index/title"))
            style.add(help,style.text(getString(detail),TaskProductVisualStyle.auxiliary,"product/settings/scale/help/$index/body",true),8)
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
