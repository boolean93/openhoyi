package io.openhoyi.mobile

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.widget.*
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceConnectionGate

/** Inline connection controls, with the MainActivity remaining the single service owner. */
internal class SettingsDeviceControls(private val host:MainActivity, private val visual:SettingsVisualStyle,
    private val requestPermissions:(Array<String>)->Unit, private val enableBluetooth:()->Unit) {
    private val style=visual.palette
    private data class Placement(val parent: android.view.ViewGroup, val index: Int,
        val params: android.view.ViewGroup.LayoutParams)
    private var tablet = false
    private val cards = mutableMapOf<DeviceRole, LinearLayout>()
    private val roleWords = mutableMapOf<DeviceRole, LinearLayout>()
    private val roleActions = mutableMapOf<DeviceRole, SettingsWrappingActions>()
    private val changeButtons = mutableListOf<Button>()
    private val disconnectLabels = mutableMapOf<Button, CharSequence>()
    private val placements = mutableMapOf<View, Placement>()
    private lateinit var scanTitle: TextView
    private lateinit var scanSpinner: ProgressBar
    private lateinit var scanProgress: TextView
    private val phoneBackgrounds = mutableMapOf<Button, android.graphics.drawable.Drawable?>()

    private fun returnToPhone(view: View) {
        val original = placements[view] ?: return
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        original.parent.addView(view, original.index.coerceAtMost(original.parent.childCount), original.params)
    }
    fun presentTablet(wide: Boolean, scaleHeading: View, precision: View, guide: Button, tool: Button) {
        if (tablet == wide) return
        tablet = wide
        val scaleCard = cards.getValue(DeviceRole.BOOKOO)
        val helpers = listOf(precision, guide, tool)
        (listOf(scaleHeading) + helpers).forEach { view ->
            if (view !in placements) (view.parent as? android.view.ViewGroup)?.let { parent ->
                placements[view] = Placement(parent, parent.indexOfChild(view), view.layoutParams)
            }
        }
        val words = roleWords.getValue(DeviceRole.BOOKOO)
        listOf(guide, tool).forEach { button ->
            if (button !in phoneBackgrounds) phoneBackgrounds[button] = button.background
            button.background = if (wide) null else phoneBackgrounds[button]
        }
        roleActions.values.forEach { it.centerRows = wide }
        if (wide) {
            val actions = roleActions.getValue(DeviceRole.BOOKOO)
            helpers.forEachIndexed { index, view ->
                (view.parent as android.view.ViewGroup).removeView(view)
                actions.addView(view, index, android.view.ViewGroup.LayoutParams(-2, -2))
            }
            (scaleHeading.parent as android.view.ViewGroup).removeView(scaleHeading)
            scaleHeading.visibility = View.VISIBLE
            style.apply(scaleHeading as TextView, TaskProductVisualStyle.body)
            scaleHeading.setPadding(0, 0, 0, 0)
            words.getChildAt(0).visibility = View.GONE
            words.addView(scaleHeading, 1, LinearLayout.LayoutParams(-1, -2))
            guide.minWidth = style.dp(48)
            guide.minimumWidth = guide.minWidth
            guide.tag = "settings/details/scale/action"
        } else {
            (listOf(scaleHeading) + helpers).sortedBy { placements[it]?.index }.forEach(::returnToPhone)
            style.apply(scaleHeading as TextView, TaskProductVisualStyle.settingsChapter)
            scaleHeading.setPadding(0, style.dp(8), 0, style.dp(8))
            words.getChildAt(0).visibility = View.VISIBLE
        }
        tool.text = host.getString(if (wide) R.string.settings_device_scale_tool_short else R.string.settings_category_scale_tool)
        scaleCard.findViewWithTag<View>("settings/devices/scale-separator")?.visibility = if (wide) View.GONE else View.VISIBLE
        cards.values.forEach { card ->
            // The containing group owns the single shared surface on tablet.
            card.background = if (wide) null else style.shape(style.surface, true, 16)
            val inset = style.dp(if (wide) 4 else 16)
            card.setPadding(style.dp(if (wide) 8 else 16), inset, style.dp(if (wide) 8 else 16), inset)
        }
        disconnectLabels.forEach { (button, original) ->
            button.text = if (wide) host.getString(R.string.settings_device_disconnect_short) else original
            button.contentDescription = original
        }
        rendered = ""
        render(owner)
    }
    private var owner:MobileService?=null
    private lateinit var message:TextView
    private lateinit var candidates:LinearLayout
    private val disconnectActions=mutableListOf<Button>()
    private val states=mutableMapOf<DeviceRole,TextView>()
    private val statusDotColors = mutableMapOf<DeviceRole, Int>()
    private val linkedActions=mutableMapOf<DeviceRole, MutableList<Button>>()
    private var scaleConnect: Button? = null
    private var scanButton: Button? = null
    private var coffeeRetry:Button?=null
    private var dialog:AlertDialog?=null
    private var localError:String?=null
    private var rendered=""
    private val selection=DeviceScanSelection()
    var errorChanged:(Boolean)->Unit={}
    private fun connectedActionAllowed()=owner!=null && allowed()
    private fun allowed()=owner?.let { !it.manualShotActive && DeviceConnectionGate.mayChangeScale(it.shotState) } ?: true
    fun create(body:LinearLayout, scanBody:LinearLayout = body, scaleBody:LinearLayout = body): LinearLayout {
        val card=visual.card(body,host.getString(R.string.home_device_connection),0)
        card.removeViewAt(0)
        card.markSettingsGroupMaterial(style)
        val scaleCard = if (scaleBody === body) card else
            visual.card(scaleBody, host.getString(R.string.scale_title), 0).also { it.removeViewAt(0); it.markSettingsGroupMaterial(style) }
        listOf(DeviceRole.COFFEE to R.string.home_coffee_disconnect,DeviceRole.BOOKOO to R.string.home_scale_disconnect).forEachIndexed { index,(role,label) ->
            val ownerCard = if (role == DeviceRole.COFFEE) card else scaleCard
            cards[role] = ownerCard
            if(index > 0 && ownerCard === card) visual.divider(card)
            val words=style.column().also { roleWords[role] = it }
            style.add(words,
                style.text(host.getString(if (role == DeviceRole.COFFEE) R.string.home_coffee else R.string.scale_title), TaskProductVisualStyle.body))
            states[role]=style.text("—",TaskProductVisualStyle.auxiliary,secondary=true).also {style.add(words,it,4)}
            val buttons=LinearLayout(host)
            val actions=SettingsWrappingActions(host, style.dp(6)).also { roleActions[role] = it }
            if(role==DeviceRole.COFFEE)coffeeRetry=HoyiUi.button(host,buttons,host.getString(R.string.coffee_connection_retry)) {
                host.reconnectCoffee()
            }.apply {visual.action(this,outline=true);tag="product/devices/coffee/retry"}
            val linked = linkedActions.getOrPut(role) { mutableListOf() }
            if (role != DeviceRole.COFFEE) scaleConnect = HoyiUi.button(host, buttons, host.getString(R.string.home_connect)) {
                owner?.beginDeviceSelection(); selection.showRole(role); rendered = ""; render(owner)
                resumeScan()
                revealScan()
            }.apply { visual.action(this); tag = "settings/devices/scale/connect" }
            HoyiUi.button(host,buttons,host.getString(R.string.home_visual_change)) {
                owner?.beginDeviceSelection();selection.showRole(role);rendered="";render(owner)
                revealScan()
            }.apply {visual.action(this,outline=true); linked += this; changeButtons += this}
            HoyiUi.button(host,buttons,host.getString(label)) {
                if(connectedActionAllowed())owner?.disconnect(role) else error(host.getString(R.string.scale_active_guard))
            }.apply {visual.action(this);asTaskTextAction();disconnectActions+=this;linked+=this;disconnectLabels[this] = text}
            while (buttons.childCount > 0) {
                val button = buttons.getChildAt(0)
                buttons.removeViewAt(0)
                actions.addView(button)
            }
            style.add(ownerCard,visual.pair(words,actions,"settings/devices/${role.name}"))
        }
        val scanCard = if (scanBody === body) card.also { visual.divider(it) }
            else visual.card(scanBody, host.getString(R.string.home_device_connection), 0).also { it.removeViewAt(0); it.markSettingsGroupMaterial(style) }
        val scan=HoyiUi.button(host,LinearLayout(host),host.getString(R.string.home_scan)) {scan()}.apply {
            visual.action(this,outline=true); scanButton = this
        }
        scanTitle = style.text(host.getString(R.string.settings_category_scan_nearby),TaskProductVisualStyle.body)
        scanSpinner = ProgressBar(host).apply { visibility = View.GONE }
        scanProgress = style.text(host.getString(R.string.settings_hig_scanning), TaskProductVisualStyle.action,
            "settings/devices/scan-progress").apply { setTextColor(style.accent); visibility = View.GONE }
        val scanWords = style.row().apply {
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(scanTitle, LinearLayout.LayoutParams(-2, -2))
            addView(scanSpinner, LinearLayout.LayoutParams(style.dp(22), style.dp(22)).apply { marginStart = style.dp(12) })
            addView(scanProgress, LinearLayout.LayoutParams(-2, -2).apply { marginStart = style.dp(8) })
        }
        (scan.parent as android.view.ViewGroup).removeView(scan)
        style.add(scanCard,visual.pair(scanWords,scan,"settings/devices/scan"))
        message=style.text("",TaskProductVisualStyle.auxiliary,"settings/devices/message",true).apply {accessibilityLiveRegion=View.ACCESSIBILITY_LIVE_REGION_POLITE}
        style.add(scanCard,message,4)
        candidates=style.column("settings/devices/candidates");style.add(scanCard,candidates,4)
        return scaleCard
    }
    private fun revealScan() {
        val row = scanButton?.parent as? View ?: return
        row.post {
            // The scan trigger stays reachable even when a permission prompt or
            // delayed result has not yet given the result list any height.
            if (row.isShown) row.requestRectangleOnScreen(
                android.graphics.Rect(0, 0, row.width, row.height), true)
        }
    }
    fun addShutdown(body:LinearLayout) {
        HoyiUi.button(host,body,host.getString(R.string.home_shutdown)) {
            val service=owner
            if(service==null || !allowed()){error(host.getString(R.string.home_shutdown_busy));return@button}
            service.shutdown()
            if(service.running)error(host.getString(R.string.home_shutdown_busy)) else host.releaseServiceBinding()
        }.apply {visual.maintenanceAction(this);disconnectActions+=this}
    }
    fun error(value:String) {errorChanged(true);localError=value;if(::message.isInitialized)message.text=value}
    /** The shared scan action always shows both device types, including after a scoped change. */
    fun scan() {
        selection.showAll();rendered="";render(owner)
        resumeScan()
    }
    /** Permission/Bluetooth callbacks resume the current intent, not a new unfiltered search. */
    fun resumeScan() {
        localError=null;errorChanged(false)
        if(!allowed()) {error(host.getString(R.string.scale_active_guard));return}
        val permissions=if(Build.VERSION.SDK_INT>=31)arrayOf(Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT) else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if(!BuildConfig.MOCK_MODE) {
            val missing=permissions.filter {host.checkSelfPermission(it)!=PackageManager.PERMISSION_GRANTED}
            if(missing.isNotEmpty()){requestPermissions(missing.toTypedArray());return}
            try {
                if(Build.VERSION.SDK_INT<31) {
                    val location=host.getSystemService(android.location.LocationManager::class.java)
                    val enabled=if(Build.VERSION.SDK_INT>=28)location.isLocationEnabled else location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)||location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
                    if(!enabled){error(host.getString(R.string.device_scan_enable_location));return}
                }
                val adapter=host.getSystemService(BluetoothManager::class.java)?.adapter
                if(adapter==null){error(host.getString(R.string.home_bluetooth_unsupported));return}
                if(!adapter.isEnabled){enableBluetooth();return}
            }catch(_:SecurityException){error(host.getString(R.string.home_permission_expired));return}
        }
        if(!host.ensureServiceForScan())error(host.getString(R.string.home_bind_failed))
    }
    fun render(service:MobileService?, snapshot: MobileSnapshot? = service?.snapshot) {
        owner=service
        if(!::message.isInitialized)return
        disconnectActions.forEach {it.isEnabled=connectedActionAllowed()}
        scanButton?.apply {
            val scanning = snapshot?.scanning == true
            val caption = host.getString(when {
                scanning -> R.string.settings_hig_scanning
                tablet && snapshot?.nearbyScanPhase == NearbyScanPhase.COMPLETE -> R.string.settings_device_scan_again
                else -> R.string.home_scan
            })
            tag = "settings/devices/scan-button"
            if (text.toString() != caption) text = caption
            isEnabled = !scanning && allowed()
            visibility = if (tablet && scanning) View.INVISIBLE else View.VISIBLE
        }
        val showProgress = tablet && snapshot?.scanning == true
        scanSpinner.visibility = if (showProgress) View.VISIBLE else View.GONE
        scanProgress.visibility = if (showProgress) View.VISIBLE else View.GONE
        scanTitle.text = host.getString(if (tablet) R.string.settings_device_nearby else R.string.settings_category_scan_nearby)
        val coffeeState=snapshot?.coffeeState ?: io.openhoyi.session.DeviceState.DISCONNECTED
        coffeeRetry?.apply {
            visibility=if(coffeeState==io.openhoyi.session.DeviceState.READY)View.GONE else View.VISIBLE
            isEnabled=allowed() && !TaskShellDeviceStatus.connecting(coffeeState)
            text=host.getString(if(TaskShellDeviceStatus.connecting(coffeeState))R.string.task_shell_action_connecting else R.string.coffee_connection_retry)
        }
        val prefs=host.getSharedPreferences("devices",android.content.Context.MODE_PRIVATE)
        states.forEach {(role,text)->
            val state=if(role==DeviceRole.COFFEE)snapshot?.coffeeState else snapshot?.scaleState
            val connected = state == io.openhoyi.session.DeviceState.READY
            val connecting = state != null && TaskShellDeviceStatus.connecting(state)
            linkedActions[role].orEmpty().forEach {
                it.visibility = if (connected && (!tablet || it !in changeButtons)) View.VISIBLE else View.GONE
                it.isEnabled = connected && connectedActionAllowed()
            }
            if (role != DeviceRole.COFFEE) scaleConnect?.apply {
                visibility = if (!connected && !connecting) View.VISIBLE else View.GONE
                isEnabled = allowed()
            }
            val dotColor = if (connected) style.accent else style.muted
            if (statusDotColors.put(role, dotColor) != dotColor) {
                val dot = android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.OVAL
                    setColor(dotColor)
                    val size = style.dp(7); setBounds(0, 0, size, size)
                }
                text.setCompoundDrawablesRelative(dot, null, null, null)
                text.compoundDrawablePadding = style.dp(6)
            }
            val identity = if (BuildConfig.MOCK_MODE) "" else
                host.getString(R.string.device_remembered_identity, prefs.getString(if (role == DeviceRole.COFFEE) "coffee" else "scale", "—"))
            val status=state?.let {if(role==DeviceRole.COFFEE)DeviceStatusText.coffeeLabel(host,it,snapshot?.coffeeFailure) else DeviceStatusText.label(host,it)} ?: "—"
            // Saved identity may belong to a different device until the new one reaches READY.
            val value = if (identity.isEmpty()) status else DeviceStatusText.settingsSummary(status, identity)
            if(text.text.toString()!=value)text.text=value
        }
        val display = localError ?: if (tablet) when {
            snapshot?.scanning == true -> host.getString(R.string.settings_device_nearby_hint)
            snapshot?.nearbyScanPhase == NearbyScanPhase.COMPLETE && snapshot.candidates.isEmpty() ->
                host.getString(R.string.settings_device_nearby_empty)
            snapshot?.nearbyScanPhase == NearbyScanPhase.FAILED -> snapshot.nearbyScanFailure?.render { id,args -> host.getString(id,*args) }.orEmpty()
            else -> ""
        } else snapshot?.messageForDisplay {id,args->host.getString(id,*args)}.orEmpty()
        if(message.text.toString()!=display)message.text=display
        message.visibility=if(message.text.isNullOrEmpty())View.GONE else View.VISIBLE
        val visibleCandidates = if (tablet && localError != null) emptyList() else snapshot?.candidates.orEmpty()
        val values=visibleCandidates.filter {selection.includes(it.candidateRole)}.sortedBy {it.candidateRole.name}
        val key=values.toString()+allowed()
        if(key==rendered)return
        rendered=key;candidates.removeAllViews()
        var renderedRole: DeviceRole? = null
        values.sortedBy { if (!tablet || it.candidateRole == DeviceRole.COFFEE) 0 else 1 }.forEach {candidate->
            if (tablet && renderedRole != candidate.candidateRole) {
                renderedRole = candidate.candidateRole
                style.add(candidates, style.text(host.getString(if (renderedRole == DeviceRole.COFFEE)
                    R.string.home_coffee else R.string.scale_title), TaskProductVisualStyle.action,
                    "settings/devices/candidate-group/${renderedRole!!.name}").apply {
                    background = style.shape(style.selected, radius = 8)
                    setPadding(style.dp(8), style.dp(3), style.dp(8), style.dp(3))
                    androidx.core.view.ViewCompat.setAccessibilityHeading(this, true)
                }, 4)
            }
            val words=style.column()
            style.add(words,style.text(candidate.advertisedName,TaskProductVisualStyle.body))
            val identity = if (tablet) host.getString(R.string.settings_device_address_suffix,
                candidate.address.filter(Char::isLetterOrDigit).takeLast(4)) else candidate.address
            if (!tablet || candidate.candidateRole == DeviceRole.COFFEE)
                style.add(words,style.text(identity,TaskProductVisualStyle.auxiliary,secondary=true),4)
            ScaleCandidateStatus.resource(candidate.scaleProtocolId)?.let { status ->
                style.add(words,style.text(host.getString(status),TaskProductVisualStyle.auxiliary,secondary=true),4)
            }
            val connect=HoyiUi.button(host,LinearLayout(host),host.getString(R.string.home_connect)) {
                if(!connectedActionAllowed()){error(host.getString(R.string.scale_active_guard));return@button}
                localError=null
                if(candidate.candidateRole==DeviceRole.BOOKOO) {
                    (candidate.scaleProtocolId ?: if(BuildConfig.MOCK_MODE)io.openhoyi.session.BookooScaleProtocolAdapter.id else null)?.let {owner?.connectScale(candidate.address,it)}
                } else if(owner?.connectRememberedCoffee(candidate.address)==RememberedCoffeeConnection.PASSWORD_REQUIRED) {
                    showPassword(candidate.address)
                }
            }.apply {visual.action(this,outline=true);isEnabled=connectedActionAllowed();tag="product/devices/candidate/${candidate.address}/connect"}
            (connect.parent as android.view.ViewGroup).removeView(connect)
            val actions=if (tablet) style.row() else style.column()
            if (tablet && candidate.candidateRole == DeviceRole.COFFEE && !BuildConfig.MOCK_MODE) {
                HoyiUi.button(host, actions, "…") { }.apply {
                    contentDescription = host.getString(R.string.settings_device_candidate_more)
                    visual.action(this)
                    setOnClickListener { button ->
                        PopupMenu(host, button).apply {
                            menu.add(R.string.home_connect_with_password)
                            setOnMenuItemClickListener {
                                if (connectedActionAllowed()) showPassword(candidate.address)
                                else error(host.getString(R.string.scale_active_guard))
                                true
                            }
                        }.show()
                    }
                }
            }
            if (tablet) actions.addView(connect, LinearLayout.LayoutParams(-2, -2))
            else style.add(actions,connect)
            if(!tablet && candidate.candidateRole==DeviceRole.COFFEE && !BuildConfig.MOCK_MODE) {
                HoyiUi.button(host,actions,host.getString(R.string.home_connect_with_password)) {
                    if(connectedActionAllowed())showPassword(candidate.address) else error(host.getString(R.string.scale_active_guard))
                }.apply {visual.action(this,outline=true);isEnabled=connectedActionAllowed();tag="product/devices/candidate/${candidate.address}/password"}
            }
            style.add(candidates,visual.pair(words,actions,"settings/devices/candidate/${candidate.address}"));visual.divider(candidates)
        }
    }
    private fun showPassword(address:String) {
        val input=EditText(host).apply {
            inputType=android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters=arrayOf(android.text.InputFilter.LengthFilter(6));hint=host.getString(R.string.home_password_hint)
            isSaveEnabled=false;importantForAutofill=View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        dialog=AlertDialog.Builder(host).setTitle(R.string.home_connect_coffee_title).setMessage(R.string.home_password_message)
            .setView(input).setPositiveButton(R.string.home_connect,null).setNegativeButton(android.R.string.cancel,null).create().also {window->
                window.setOnShowListener {window.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val password=input.text.toString()
                    if(!password.matches(Regex("[0-9]{6}")))input.error=host.getString(R.string.home_password_error)
                    else if(connectedActionAllowed()){owner?.connectCoffee(address,password);window.dismiss()}
                }}
                window.setOnDismissListener {input.text.clear()};AppDialog.show(window)
            }
    }
    fun close(){dialog?.dismiss();dialog=null;owner?.endDeviceSelection();owner=null}
}
