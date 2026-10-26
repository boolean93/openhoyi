package io.openhoyi.mobile

import android.content.ComponentName
import android.content.Intent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.Manifest
import android.content.pm.PackageManager
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowInsets
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import android.app.AlertDialog
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.openhoyi.session.DeviceRole
import io.openhoyi.session.DeviceState

/** One window owns task navigation and the existing process-wide device service connection. */
class MainActivity : ThemedActivity() {
    private lateinit var frame: TaskPageFrame
    private lateinit var shellStatus: TaskShellStatusView
    private var destination = TaskDestination.BREW
    private var extractionVisible = false
    private var extractionSlot = 7
    private var pendingStartConfirmation = false
    private var pendingShow = false
    private var pageId = View.generateViewId()
    private var service: MobileService? = null
    private var bound = false
    private var scanAfterBind = false
    private var reconnectScaleAfterBind: Pair<String, String>? = null
    private var lastPresentedManualShotId: String? = null
    private var foreground = false
    private val handler = Handler(Looper.getMainLooper())
    private val manualPresentation = object : Runnable {
        override fun run() {
            presentManualShotIfNew()
            if (::shellStatus.isInitialized) shellStatus.present(service)
            if (foreground) handler.postDelayed(this, 250)
        }
    }
    private val subscribers = mutableSetOf<(MobileService?) -> Unit>()
    private val transientDialogs = mutableListOf<AlertDialog>()
    internal fun trackDialog(dialog: AlertDialog) { transientDialogs += dialog }
    private fun dismissTransientDialogs() { transientDialogs.forEach { it.dismiss() }; transientDialogs.clear() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = (binder as MobileService.LocalBinder).service
            service?.ensureKnownDevices()
            reconnectScaleAfterBind?.let { (address, protocol) ->
                reconnectScaleAfterBind = null
                service?.connectScale(address, protocol)
            }
            if (scanAfterBind) { scanAfterBind = false; service?.scan() }
            publishService()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; publishService() }
        override fun onBindingDied(name: ComponentName) {
            if (bound) { unbindService(this); bound = false }
            service = null
            publishService()
        }
    }

    internal fun observeService(observer: (MobileService?) -> Unit) {
        subscribers += observer
        observer(service)
    }
    internal fun removeServiceObserver(observer: (MobileService?) -> Unit) { subscribers -= observer }
    private fun publishService() {
        subscribers.toList().forEach { it(service) }
        if (::shellStatus.isInitialized) shellStatus.present(service)
        presentManualShotIfNew()
    }
    private fun presentManualShotIfNew() {
        if (!foreground) return
        val id = service?.manualShotPresentationId ?: return
        if (id == lastPresentedManualShotId) return
        lastPresentedManualShotId = id
        showExtraction()
    }
    internal val currentService get() = service
    private fun sleepNow() {
        val owner = service ?: return
        trackDialog(AppDialog.confirm(this, getString(R.string.home_sleep_confirm_title),
            getString(R.string.home_sleep_confirm_message), AppDialog.Action(getString(R.string.home_sleep_send)) {
                owner.enterSleepNow()?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                shellStatus.present(service)
            }, getString(R.string.machine_settings_cancel)))
    }
    private fun openScale() {
        if (service?.snapshot?.scaleState == DeviceState.READY) select(TaskDestination.SCALE)
        else startActivity(Intent(this, ScaleConnectionActivity::class.java))
    }
    private fun disconnectScale() {
        service?.disconnect(DeviceRole.BOOKOO)
        shellStatus.present(service)
    }
    private fun reconnectScale() {
        if (service?.snapshot?.scaleState == DeviceState.UNSUPPORTED) {
            openScale()
            return
        }
        val prefs = getSharedPreferences("devices", MODE_PRIVATE)
        val remembered = io.openhoyi.session.ScaleSelectionPolicy.remembered(
            prefs.getString("scale", null), prefs.getString("scaleProtocol", null))
            ?.takeIf { BluetoothAdapter.checkBluetoothAddress(it.address) }
        if (remembered == null) { openScale(); return }
        if (missingBle().isNotEmpty()) { openScale(); return }
        val owner = service
        if (owner != null) owner.connectScale(remembered.address, remembered.protocolId)
        else {
            reconnectScaleAfterBind = remembered.address to remembered.protocolId
            if (!startRememberedScaleService()) reconnectScaleAfterBind = null
        }
    }
    internal fun releaseServiceBinding() {
        if (bound) { unbindService(connection); bound = false }
        service = null
        publishService()
    }

    private fun bindExisting() { if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, 0) }
    private fun bindCreated() { if (!bound) bound = bindService(Intent(this, MobileService::class.java), connection, BIND_AUTO_CREATE) }
    private fun startRememberedScaleService(): Boolean {
        if (BuildConfig.MOCK_MODE) {
            startService(Intent(this, MobileService::class.java))
            bindCreated()
            return bound
        }
        val prefs = getSharedPreferences("devices", MODE_PRIVATE)
        val selection = io.openhoyi.session.ScaleSelectionPolicy.remembered(
            prefs.getString("scale", null), prefs.getString("scaleProtocol", null))
            ?.takeIf { BluetoothAdapter.checkBluetoothAddress(it.address) }
        val knownCoffee = MobileService.hasRememberedCoffee(this)
        if (selection == null && !knownCoffee) return false
        if (missingBle().isNotEmpty()) return false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return try {
            if (!adapter.isEnabled) false else {
                startForegroundService(Intent(this, MobileService::class.java).setAction(
                    if (knownCoffee) MobileService.AUTO_KNOWN_DEVICES else MobileService.AUTO_SCALE))
                bindCreated(); bound
            }
        } catch (_: RuntimeException) { false }
    }
    internal fun missingBle(): List<String> = (if (android.os.Build.VERSION.SDK_INT >= 31)
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else listOf(Manifest.permission.ACCESS_FINE_LOCATION)).filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
    internal fun ensureServiceForScan(): Boolean {
        if (service?.running == true) { service?.scan(); return true }
        return try {
            if (BuildConfig.MOCK_MODE) startService(Intent(this, MobileService::class.java))
            else startForegroundService(Intent(this, MobileService::class.java))
            scanAfterBind = true
            bindCreated()
            if (!bound) scanAfterBind = false
            bound
        } catch (_: RuntimeException) { scanAfterBind = false; false }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destination = (savedInstanceState?.getString(STATE_DESTINATION)
            ?: intent.getStringExtra(EXTRA_INITIAL_DESTINATION))?.let { runCatching { TaskDestination.valueOf(it) }.getOrNull() }
            ?: TaskDestination.BREW
        pageId = savedInstanceState?.getInt(STATE_PAGE_ID) ?: pageId
        extractionVisible = savedInstanceState?.getBoolean(STATE_EXTRACTION)
            ?: intent.getBooleanExtra(EXTRA_OPEN_EXTRACTION, false)
        extractionSlot = savedInstanceState?.getInt(STATE_EXTRACTION_SLOT)
            ?: intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7)
        lastPresentedManualShotId = savedInstanceState?.getString(STATE_MANUAL_SHOT_ID)
        val style = TaskProductVisualStyle(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(style.page)
            setOnApplyWindowInsetsListener { view, insets ->
                if (android.os.Build.VERSION.SDK_INT >= 30) {
                    val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                    view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                } else {
                    @Suppress("DEPRECATION")
                    view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                        insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                }
                insets
            }
        }
        frame = TaskPageFrame.host(this, style, { destination }, ::select)
        shellStatus = TaskShellStatusView(this, ::sleepNow, ::openScale, ::reconnectScale, ::disconnectScale)
        frame.installShellStatus(shellStatus)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val container = FrameLayout(this).apply { id = pageId; tag = "task-tab-content" }
        frame.pageContent.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        check(findViewById<View>(pageId) === container) { "Task page container must be attached before Fragment restoration" }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })
        showCurrent()
        if (savedInstanceState == null) routeHistorySource(intent)
    }

    override fun onStart() {
        super.onStart()
        if (!startRememberedScaleService()) bindExisting()
    }

    override fun onStop() {
        if (bound) { unbindService(connection); bound = false }
        service = null
        publishService()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        handler.post(manualPresentation)
    }
    override fun onPause() {
        foreground = false
        handler.removeCallbacks(manualPresentation)
        dismissTransientDialogs()
        super.onPause()
    }

    private fun tag(destination: TaskDestination) = "root:${destination.name}"
    private fun create(destination: TaskDestination): Fragment = when (destination) {
        TaskDestination.BREW -> HomeFragment()
        TaskDestination.CURVES -> CurveFragment()
        TaskDestination.HISTORY -> HistoryFragment()
        TaskDestination.BEANS -> BeanInventoryFragment()
        TaskDestination.SCALE -> ScaleFragment()
        TaskDestination.SETTINGS -> AppSettingsFragment()
    }

    internal fun select(next: TaskDestination) {
        if (next == destination && !extractionVisible) return
        dismissTransientDialogs()
        destination = next
        extractionVisible = false
        pendingStartConfirmation = false
        showCurrent()
    }

    fun showExtraction(slot: Int = 7, confirmStart: Boolean = false) {
        dismissTransientDialogs()
        destination = TaskDestination.BREW
        extractionSlot = slot
        extractionVisible = true
        pendingStartConfirmation = confirmStart
        showCurrent()
    }

    private fun showCurrent() {
        if (supportFragmentManager.isStateSaved) { pendingShow = true; return }
        pendingShow = false
        val chosen = if (extractionVisible) "extraction" else tag(destination)
        val transaction = supportFragmentManager.beginTransaction()
        val selected = supportFragmentManager.findFragmentByTag(chosen)
            ?: (if (extractionVisible) ExtractionFragment().apply {
                arguments = Bundle().apply { putInt(PresetSlots.EXTRA_SLOT, extractionSlot) }
            } else create(destination)).also { transaction.add(pageId, it, chosen) }
        if (extractionVisible) (selected as? ExtractionFragment)?.let {
            it.setPresetSlot(extractionSlot)
            if (!pendingStartConfirmation) it.cancelStartConfirmation()
        }
        supportFragmentManager.fragments.forEach { fragment ->
            if (fragment.tag == chosen) {
                transaction.show(fragment).setMaxLifecycle(fragment, Lifecycle.State.RESUMED)
            } else {
                transaction.hide(fragment).setMaxLifecycle(fragment, Lifecycle.State.STARTED)
            }
        }
        transaction.setPrimaryNavigationFragment(selected)
        transaction.commitNow()
        frame.navigation.refreshSelection()
        if (pendingStartConfirmation && selected is ExtractionFragment) {
            pendingStartConfirmation = false
            selected.requestStartConfirmation()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_DESTINATION, destination.name)
        outState.putInt(STATE_PAGE_ID, pageId)
        outState.putBoolean(STATE_EXTRACTION, extractionVisible)
        outState.putInt(STATE_EXTRACTION_SLOT, extractionSlot)
        outState.putString(STATE_MANUAL_SHOT_ID, lastPresentedManualShotId)
        super.onSaveInstanceState(outState)
    }

    override fun onPostResume() { super.onPostResume(); if (pendingShow) showCurrent() }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_EXTRACTION, false)) showExtraction(intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7))
        else intent.getStringExtra(EXTRA_INITIAL_DESTINATION)?.let { name ->
            runCatching { TaskDestination.valueOf(name) }.getOrNull()?.let(::select)
        }
        routeHistorySource(intent)
    }

    private fun routeHistorySource(intent: Intent) {
        val journal = when (intent.getStringExtra(HistoryActivity.EXTRA_HISTORY_SOURCE)) {
            HistoryActivity.SOURCE_JOURNAL -> true
            HistoryActivity.SOURCE_LOCAL -> false
            else -> return
        }
        (supportFragmentManager.findFragmentByTag(tag(TaskDestination.HISTORY)) as? HistoryFragment)?.showSource(journal)
    }

    private fun navigateBack() {
        val current = supportFragmentManager.primaryNavigationFragment
        val consumed = when (current) {
            is CurveFragment -> current.consumeBack()
            is HistoryFragment -> current.consumeBack()
            is BeanInventoryFragment -> current.consumeBack()
            else -> false
        }
        if (consumed) return
        if (extractionVisible) { extractionVisible = false; showCurrent() }
        else if (destination != TaskDestination.BREW) select(TaskDestination.BREW)
        else finish()
    }

    companion object {
        const val EXTRA_INITIAL_DESTINATION = "io.openhoyi.mobile.initial_destination"
        const val EXTRA_OPEN_EXTRACTION = "io.openhoyi.mobile.open_extraction"
        private const val STATE_DESTINATION = "task.destination"
        private const val STATE_PAGE_ID = "task.page_id"
        private const val STATE_EXTRACTION = "task.extraction"
        private const val STATE_EXTRACTION_SLOT = "task.extraction_slot"
        private const val STATE_MANUAL_SHOT_ID = "task.manual_shot_id"
    }
}
