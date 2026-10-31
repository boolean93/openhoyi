package io.openhoyi.mobile

import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import io.openhoyi.session.DeviceRole

/** Connection management shares the existing owner; it never creates a second BLE session. */
class DeviceConnectionActivity : ScaleOwnerActivity() {
    private val visual by lazy { SettingsVisualStyle(this) }
    val productLayoutEvidence get() = visual.evidence
    private var restoreScrollY: Int? = null
    private lateinit var status: TextView
    private lateinit var rows: LinearLayout
    private var scanPending = false
    private var passwordDialog: AlertDialog? = null
    private var candidatesKey = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        restoreScrollY = savedInstanceState?.getInt("devicesScrollY")
        val body = visual.page(this, R.string.home_visual_panel_title, R.string.scale_connection_subtitle, pageId = "devices")
        status = HoyiUi.label(this, body, "", 16)
        HoyiUi.button(this, body, getString(R.string.home_scan)) { scanDevices() }
        HoyiUi.button(this, body, getString(R.string.home_coffee_disconnect)) {
            if (changesAllowed()) owner?.disconnect(DeviceRole.COFFEE) else message(getString(R.string.scale_active_guard))
        }
        HoyiUi.button(this, body, getString(R.string.home_scale_disconnect)) {
            if (changesAllowed()) owner?.disconnect(DeviceRole.BOOKOO) else message(getString(R.string.scale_active_guard))
        }
        rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(rows)
        renderScale()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("devicesScrollY", findViewById<android.widget.ScrollView>(R.id.m8_settings_scroll)?.scrollY ?: 0)
        super.onSaveInstanceState(outState)
    }
    override fun onStop() {
        passwordDialog?.dismiss(); passwordDialog = null
        super.onStop()
    }
    private fun scanDevices() {
        val permissions = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        val missing = permissions.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (!BuildConfig.MOCK_MODE && missing.isNotEmpty()) { requestPermissions(missing.toTypedArray(), 71); return }
        if (!BuildConfig.MOCK_MODE) {
            if (Build.VERSION.SDK_INT < 31) {
                val location = getSystemService(android.location.LocationManager::class.java)
                if (if (Build.VERSION.SDK_INT >= 28) !location.isLocationEnabled else !location.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) && !location.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)) { message(getString(R.string.scale_owner_required)); return }
            }
            val adapter = getSystemService(BluetoothManager::class.java)?.adapter
            if (adapter == null) { message(getString(R.string.home_bluetooth_unsupported)); return }
            if (!adapter.isEnabled) { startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), 72); return }
        }
        if (owner != null && !changesAllowed()) { message(getString(R.string.scale_active_guard)); return }
        scanPending = true
        if (owner?.running != true) {
            try { startForegroundService(Intent(this, MobileService::class.java)); bindOwner() }
            catch (_: RuntimeException) { scanPending = false; message(getString(R.string.scale_owner_failed)) }
        }
        renderScale()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 71 && results.isNotEmpty() && results.all { it == PackageManager.PERMISSION_GRANTED }) scanDevices()
        else if (code == 71) message(getString(R.string.home_permission_required))
    }
    @Deprecated("Platform activity results")
    override fun onActivityResult(code: Int, result: Int, data: Intent?) {
        super.onActivityResult(code, result, data)
        if (code == 72 && result == RESULT_OK) scanDevices()
    }
    override fun renderScale() {
        if (!::status.isInitialized) return
        val service = owner
        if (scanPending && service?.running == true) {
            scanPending = false
            if (changesAllowed()) service.scan()
        }
        val snapshot = service?.snapshot
        val devices = getSharedPreferences("devices", MODE_PRIVATE)
        val coffeeIdentity = getString(R.string.device_remembered_identity, devices.getString("coffee", "—"))
        val scaleIdentity = getString(R.string.device_remembered_identity, devices.getString("scale", "—"))
        status.text = "${getString(R.string.machine_settings_title)} · $coffeeIdentity: ${snapshot?.coffeeState?.let { DeviceStatusText.label(this, it) } ?: "—"}\n${getString(R.string.scale_title)} · $scaleIdentity: ${snapshot?.scaleState?.let { DeviceStatusText.label(this, it) } ?: "—"}\n${snapshot?.messageForDisplay { id, args -> getString(id, *args) }.orEmpty()}"
        val candidates = snapshot?.candidates.orEmpty().sortedBy { it.candidateRole.name }
        val key = candidates.toString() + changesAllowed()
        if (key == candidatesKey) return
        candidatesKey = key
        rows.removeAllViews()
        candidates.forEach { candidate ->
            val button = HoyiUi.button(this, rows, "${getString(if (candidate.candidateRole == DeviceRole.COFFEE) R.string.machine_settings_title else R.string.scale_title)} · ${candidate.advertisedName}\n${candidate.address}${if (candidate.scaleProtocolId == io.openhoyi.session.FelicitaReadOnlyScaleProtocolAdapter.id) "\n" + getString(R.string.scale_read_only_live) else ""}") {
                if (!changesAllowed()) { message(getString(R.string.scale_active_guard)); return@button }
                if (candidate.candidateRole == DeviceRole.BOOKOO) (candidate.scaleProtocolId ?: if (BuildConfig.MOCK_MODE) io.openhoyi.session.BookooScaleProtocolAdapter.id else null)?.let { service?.connectScale(candidate.address, it) }
                else if (service?.connectRememberedCoffee(candidate.address) != true) {
                    val input = EditText(this).apply {
                        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
                        filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                        isSaveEnabled = false
                    }
                    input.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                    val dialog = AlertDialog.Builder(this).setTitle(R.string.home_connect_coffee_title).setView(input)
                        .setNegativeButton(android.R.string.cancel, null).setPositiveButton(R.string.home_connect, null).create()
                    dialog.setOnShowListener {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                            val password = input.text.toString()
                            if (!password.matches(Regex("[0-9]{6}"))) input.error = getString(R.string.home_password_error)
                            else if (changesAllowed()) { service?.connectCoffee(candidate.address, password); input.text.clear(); dialog.dismiss() }
                        }
                    }
                    dialog.setOnDismissListener { input.text.clear() }
                    passwordDialog = dialog
                    dialog.show()

                }
            }
            button.tag = "product/devices/candidate/${candidate.address}/connect"
        }
        restoreScrollY?.takeIf { service != null && candidates.isNotEmpty() }?.let { saved ->
            findViewById<android.widget.ScrollView>(R.id.m8_settings_scroll)?.post {
                findViewById<android.widget.ScrollView>(R.id.m8_settings_scroll)?.scrollTo(0, saved)
            }
            restoreScrollY = null
        }
    }
}
