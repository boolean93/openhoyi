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
import android.content.SharedPreferences
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
    private var pendingShow = false
    private var pageId = View.generateViewId()
    private var service: MobileService? = null
    private val bound: Boolean get() = serviceBinding.bound
    private val serviceBinding: VisibleServiceBinding by lazy {
        VisibleServiceBinding((application as MobileApplication).serviceAvailability,
            bind = { create ->
                val accepted = bindService(Intent(this, MobileService::class.java), connection,
                    if (create) BIND_AUTO_CREATE else 0)
                // Android retains the dispatcher even when no existing service accepted the bind.
                if (!accepted) unbindService(connection)
                accepted
            },
            unbind = { unbindService(connection) },
            onReleased = { service = null; publishService() },
            onAvailable = {
                // Binding can precede onStartCommand. Re-evaluate once the owner really runs.
                if (service?.running == true) {
                    service?.ensureKnownDevices()
                    if(reconnectCoffeeAfterBind){reconnectCoffeeAfterBind=false;reconnectCoffee()}
                    publishService()
                }
            })
    }
    private var scanAfterBind = false
    private var reconnectScaleAfterBind: Pair<String, String>? = null
    private var reconnectCoffeeAfterBind=false
    private var lastPresentedManualShotId: String? = null
    private var foreground = false
    private val handler = Handler(Looper.getMainLooper())
    private val manualPresentation = object : Runnable {
        override fun run() {
            shortcutStart?.allows()
            presentManualShotIfNew()
            if (::shellStatus.isInitialized) shellStatus.present(service)
            if (foreground) handler.postDelayed(this, 250)
        }
    }
    private var shortcutStart: ShortcutStartPermit? = null
    private val shortcutPreferences by lazy { ShortcutStartPreferences(this) }
    private var stopShortcutPreferenceObserver: (() -> Unit)? = null
    private val shortcutPreferenceFiles by lazy {
        listOf("presets", "curves", "devices").map { getSharedPreferences(it, MODE_PRIVATE) }
    }
    private val shortcutPreferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        revokeShortcutStart()
    }
    internal fun revokeShortcutStart() { shortcutStart?.revoke(); shortcutStart = null }
    private val subscribers = mutableSetOf<(MobileService?) -> Unit>()
    private val transientDialogs = mutableListOf<AlertDialog>()
    internal fun trackDialog(dialog: AlertDialog) { transientDialogs += dialog }
    private fun dismissTransientDialogs() { transientDialogs.forEach { it.dismiss() }; transientDialogs.clear() }
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (!bound) return // Ignore a delivery after the visible binding lease was released.
            service = (binder as MobileService.LocalBinder).service
            service?.ensureKnownDevices()
            if(reconnectCoffeeAfterBind && service?.running==true){reconnectCoffeeAfterBind=false;reconnectCoffee()}
            reconnectScaleAfterBind?.let { (address, protocol) ->
                reconnectScaleAfterBind = null
                service?.connectScale(address, protocol)
            }
            if (scanAfterBind) { scanAfterBind = false; service?.scan() }
            publishService()
        }
        override fun onServiceDisconnected(name: ComponentName) { service = null; publishService() }
        override fun onBindingDied(name: ComponentName) { serviceBinding.release() }
        override fun onNullBinding(name: ComponentName) { serviceBinding.release() }
    }

    internal fun observeService(observer: (MobileService?) -> Unit) {
        subscribers += observer
        observer(service)
    }
    internal fun removeServiceObserver(observer: (MobileService?) -> Unit) { subscribers -= observer }
    private fun publishService() {
        shortcutStart?.allows()
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
    override fun onUserInteraction() {
        super.onUserInteraction()
        if(foreground)service?.requestCoffeeRecoverySoon()
    }
    internal fun reconnectCoffee() {
        val address=getSharedPreferences("devices",MODE_PRIVATE).getString("coffee",null)
            ?.takeIf(BluetoothAdapter::checkBluetoothAddress)
        if(address==null){openSettingsSection("devices");return}
        if(missingBle().isNotEmpty()){
            openSettingsSection("devices")
            Toast.makeText(this,getString(R.string.coffee_connection_permission_hint),Toast.LENGTH_LONG).show();return
        }
        val bluetoothReady=runCatching {getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled==true}.getOrDefault(false)
        if(!bluetoothReady){
            openSettingsSection("devices")
            Toast.makeText(this,getString(R.string.coffee_connection_bluetooth_hint),Toast.LENGTH_LONG).show();return
        }
        val owner=service
        if(owner==null || !owner.running){
            reconnectCoffeeAfterBind=true
            try {
                startForegroundService(Intent(this,MobileService::class.java))
                bindCreated()
                if(!bound)reconnectCoffeeAfterBind=false
            }catch(_:RuntimeException){reconnectCoffeeAfterBind=false;openSettingsSection("devices")}
            return
        }
        when(owner.connectRememberedCoffee(address)){
            RememberedCoffeeConnection.PASSWORD_REQUIRED -> {
                openSettingsSection("devices")
                Toast.makeText(this,getString(R.string.coffee_connection_password_hint),Toast.LENGTH_LONG).show()
            }
            RememberedCoffeeConnection.BLOCKED -> {
                val reason=owner.snapshot.messageForDisplay {id,args->getString(id,*args)}
                    .ifBlank {getString(R.string.coffee_connection_blocked_hint)}
                Toast.makeText(this,reason,Toast.LENGTH_LONG).show()
            }
            RememberedCoffeeConnection.STARTED -> Unit
        }
        shellStatus.present(service)
    }
    private fun sleepNow() {
        service?.enterSleepNow()?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
        shellStatus.present(service)
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
    internal fun releaseServiceBinding() { serviceBinding.release() }

    private fun bindExisting() { serviceBinding.bindExisting() }
    private fun bindCreated() { serviceBinding.bindCreated() }
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
        val auto=(application as MobileApplication).autoConnect
        val knownScale=selection!=null && (reconnectScaleAfterBind!=null || auto.enabled(DeviceRole.BOOKOO)&&!auto.paused(DeviceRole.BOOKOO))
        if (!knownScale && !knownCoffee) return false
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
        shortcutPreferenceFiles.forEach { it.registerOnSharedPreferenceChangeListener(shortcutPreferenceListener) }
        stopShortcutPreferenceObserver = shortcutPreferences.observe(::revokeShortcutStart)
        destination = (savedInstanceState?.getString(STATE_DESTINATION)
            ?: intent.getStringExtra(EXTRA_INITIAL_DESTINATION))?.let { runCatching { TaskDestination.valueOf(it) }.getOrNull() }
            ?: TaskDestination.BREW
        pageId = savedInstanceState?.getInt(STATE_PAGE_ID) ?: pageId
        extractionVisible = savedInstanceState?.getBoolean(STATE_EXTRACTION)
            ?: intent.getBooleanExtra(EXTRA_OPEN_EXTRACTION, false)
        extractionOrigin=savedInstanceState?.getString("extractionOrigin")?.let {runCatching {TaskDestination.valueOf(it)}.getOrNull()}
        extractionSlot = savedInstanceState?.getInt(STATE_EXTRACTION_SLOT)
            ?: intent.getIntExtra(PresetSlots.EXTRA_SLOT, 7)
        scaleSettingsReturn = savedInstanceState?.getBoolean("settingsScaleReturn") ?: false
        settingsOrigin=savedInstanceState?.getString("settingsOrigin")?.let { runCatching {TaskDestination.valueOf(it)}.getOrNull() }
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
        shellStatus = TaskShellStatusView(this, ::sleepNow, ::openScale, ::reconnectScale, ::disconnectScale, ::reconnectCoffee)
        frame.installShellStatus(shellStatus)
        root.addView(frame, LinearLayout.LayoutParams(-1, 0, 1f))
        val container = FrameLayout(this).apply { id = pageId; tag = "task-tab-content" }
        frame.pageContent.addView(container, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        check(findViewById<View>(pageId) === container) { "Task page container must be attached before Fragment restoration" }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { navigateBack() }
        })
        if (savedInstanceState == null && extractionVisible) {
            val routed = routeShortcut(extractionSlot)
            if (routed == null) { extractionVisible = false; destination = TaskDestination.BREW }
            else extractionSlot = routed
        }
        showCurrent()
        if (savedInstanceState == null) {
            routeHistorySource(intent)
            intent.getStringExtra("settingsSection")?.let(::openSettingsSection)
        }
    }

    override fun onStart() {
        super.onStart()
        serviceBinding.start()
        if (!startRememberedScaleService()) bindExisting()
    }

    override fun onStop() {
        serviceBinding.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        (application as MobileApplication).autoConnect.refresh()
        if(service==null) { if(!startRememberedScaleService())bindExisting() }
        foreground = true
        handler.post(manualPresentation)
    }
    override fun onPause() {
        revokeShortcutStart()
        foreground = false
        handler.removeCallbacks(manualPresentation)
        dismissTransientDialogs()
        super.onPause()
    }

    override fun onDestroy() {
        revokeShortcutStart()
        shortcutPreferenceFiles.forEach { it.unregisterOnSharedPreferenceChangeListener(shortcutPreferenceListener) }
        stopShortcutPreferenceObserver?.invoke(); stopShortcutPreferenceObserver = null
        super.onDestroy()
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

    private var extractionOrigin:TaskDestination?=null
    private var settingsOrigin:TaskDestination?=null
    private var scaleSettingsReturn = false
    internal fun openSettingsSection(key: String) {
        val origin=destination.takeIf {it!=TaskDestination.SETTINGS}
        select(TaskDestination.SETTINGS)
        if(origin!=null)settingsOrigin=origin
        (supportFragmentManager.primaryNavigationFragment as? AppSettingsFragment)?.openSection(key)
    }
    internal fun openScaleFromSettings() {
        select(TaskDestination.SCALE)
        scaleSettingsReturn = true
    }
    internal fun select(next: TaskDestination) {
        revokeShortcutStart()
        if (next == destination && !extractionVisible) return
        scaleSettingsReturn = false
        settingsOrigin = null
        extractionOrigin = null
        dismissTransientDialogs()
        destination = next
        extractionVisible = false
        showCurrent()
    }

    /** Navigation only: custom shortcuts select the saved recipe and retain wire slot 7. */
    private fun routeShortcut(slot: Int): Int? {
        if (slot !in 1..5) return 7
        val stored = getSharedPreferences("presets", MODE_PRIVATE).getString(PresetSlots.key(slot), null)
        return when (val route = PresetSlots.route(slot, stored, (application as MobileApplication).curves)) {
            is PresetSlots.Route.Ready -> {
                if (route.selectCurve) getSharedPreferences("curves", MODE_PRIVATE).edit()
                    .putString("selected", route.curveId).apply()
                route.wireSlot
            }
            PresetSlots.Route.Missing, PresetSlots.Route.Unavailable -> {
                Toast.makeText(this, getString(R.string.curve_slot_unavailable), Toast.LENGTH_LONG).show()
                null
            }
        }
    }

    /** Explicit Home shortcut taps only. All other navigation remains inert. */
    internal fun openShortcut(slot: Int) {
        PresetSlots.requireSlot(slot)
        if (!foreground || supportFragmentManager.isStateSaved || extractionVisible || destination != TaskDestination.BREW) return
        revokeShortcutStart()
        val stored = getSharedPreferences("presets", MODE_PRIVATE).getString(PresetSlots.key(slot), null)
        val library = (application as MobileApplication).curves
        if (PresetSlots.curveId(slot, stored, library) == PresetSlots.EMPTY) {
            select(TaskDestination.CURVES)
            return
        }
        val route = PresetSlots.route(slot, stored, library) as? PresetSlots.Route.Ready
            ?: run { Toast.makeText(this, R.string.curve_slot_unavailable, Toast.LENGTH_LONG).show(); return }
        launchHomeCurve(slot, route) {
            getSharedPreferences("presets", MODE_PRIVATE).getString(PresetSlots.key(slot), null) == stored &&
                PresetSlots.route(slot, stored, library) == route
        }
    }

    internal fun lastCurveReference(): HomeLastCurve.Reference? {
        val app = application as MobileApplication
        // Loading history migrates positively observed uses into the permanent ledger first.
        val history = runCatching { app.history }.getOrNull() ?: return null
        val ledger = app.curveUsageResult.getOrNull() ?: return null
        val recent = history.entries.firstOrNull { it.curveId != "manual" && it.observedRunning }
            ?.let { CurveUsage(it.id, it.curveId, it.startedAtMs) }
        return HomeLastCurve.Reference(ledger.latest(), recent)
    }

    internal fun resolveLastCurve(reference: HomeLastCurve.Reference? = lastCurveReference()): HomeLastCurve {
        val app = application as MobileApplication
        return reference?.let { HomeLastCurve.from(it.latest, app.curves.items, it.recentObserved, app.curves::canStart) }
            ?: HomeLastCurve.DataUnavailable
    }

    internal fun openLastCurve(ready: HomeLastCurve.Ready, reference: HomeLastCurve.Reference) {
        if (!foreground || supportFragmentManager.isStateSaved || extractionVisible || destination != TaskDestination.BREW) return
        revokeShortcutStart()
        if (lastCurveReference() != reference || resolveLastCurve(reference) != ready) return
        // Slot 7 re-resolves the committed selection in the fragment and service.
        if (!getSharedPreferences("curves", MODE_PRIVATE).edit().putString("selected", ready.item.id).commit()) {
            Toast.makeText(this, R.string.task_brew_usage_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        launchHomeCurve(7, PresetSlots.Route.Ready(ready.item.id, 7, true)) {
            lastCurveReference() == reference && resolveLastCurve(reference) == ready
        }
    }

    /** Shared one-tap path: the source must remain the exact curve/history reference tapped. */
    private fun launchHomeCurve(sourceSlot: Int, route: PresetSlots.Route.Ready, sourceValid: () -> Boolean) {
        val requestedAt = MobileMonotonicClock.elapsedRealtime()
        val owner = service
        val library = (application as MobileApplication).curves
        val expected = owner?.let { library.resolve(route.curveId, it.snapshot.scaleState == DeviceState.READY, route.wireSlot) }
        val expectedItem = library.find(route.curveId)
        val app = application as MobileApplication
        val curveRevision = app.customCurvesResult.getOrNull()?.revision
        val configurationRevision = owner?.shortcutConfigurationRevision
        val settings = owner?.snapshot?.settings
        val device = owner?.shortcutDeviceIdentity
        val preference = shortcutPreferences.read()
        val preferenceRevision = shortcutPreferences.revision
        showExtraction(sourceSlot)
        if (!preference.startsOnTap) return
        val fragment = supportFragmentManager.primaryNavigationFragment as? ExtractionFragment ?: return
        if (owner == null || expected == null) {
            Toast.makeText(this, if (owner == null) R.string.service_unavailable else R.string.extraction_choose_startable, Toast.LENGTH_LONG).show()
            return
        }
        val permit = ShortcutStartPermit(requestedAt, MobileMonotonicClock::elapsedRealtime) {
            foreground && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                !supportFragmentManager.isStateSaved && extractionVisible &&
                supportFragmentManager.primaryNavigationFragment === fragment && fragment.shortcutStartVisible &&
                service === owner && owner.running && owner.shortcutDeviceIdentity == device &&
                device != null && owner.snapshot.settings == settings &&
                owner.shortcutConfigurationRevision == configurationRevision &&
                app.customCurvesResult.getOrNull()?.revision == curveRevision &&
                preference.startsOnTap && shortcutPreferences.read() == preference &&
                shortcutPreferences.revision == preferenceRevision &&
                sourceValid() &&
                (route.wireSlot != 7 || getSharedPreferences("curves", MODE_PRIVATE).getString("selected", null) == route.curveId) &&
                library.resolve(route.curveId, owner.snapshot.scaleState == DeviceState.READY, route.wireSlot) == expected &&
                library.find(route.curveId) == expectedItem && library.validated(expected) &&
                owner.studioStartBlock(expected) == null
        }
        shortcutStart = permit
        fragment.tryShortcutStart(expected, permit)
    }

    fun showExtraction(slot: Int = 7) {
        revokeShortcutStart()
        val wireSlot = routeShortcut(slot) ?: return
        if(!extractionVisible)extractionOrigin=destination
        dismissTransientDialogs()
        destination = TaskDestination.BREW
        extractionSlot = wireSlot
        extractionVisible = true
        showCurrent()
    }

    private fun showCurrent() {
        (application as MobileApplication).releaseUpdates.interruptInstallLease()
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        revokeShortcutStart()
        outState.putString("extractionOrigin",extractionOrigin?.name)
        outState.putString("settingsOrigin",settingsOrigin?.name)
        outState.putBoolean("settingsScaleReturn", scaleSettingsReturn)
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
        else if(!intent.hasExtra("settingsSection")) intent.getStringExtra(EXTRA_INITIAL_DESTINATION)?.let { name ->
            runCatching { TaskDestination.valueOf(name) }.getOrNull()?.let(::select)
        }
        routeHistorySource(intent)
        intent.getStringExtra("settingsSection")?.let(::openSettingsSection)
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
        revokeShortcutStart()
        val current = supportFragmentManager.primaryNavigationFragment
        val consumed = when (current) {
            is CurveFragment -> current.consumeBack()
            is HistoryFragment -> current.consumeBack()
            is BeanInventoryFragment -> current.consumeBack()
            else -> false
        }
        if (consumed) return
        if (!extractionVisible && scaleSettingsReturn && destination == TaskDestination.SCALE) { select(TaskDestination.SETTINGS); return }
        if (extractionVisible) { extractionVisible = false; destination=extractionOrigin ?: TaskDestination.BREW; extractionOrigin=null; showCurrent() }
        else if(destination==TaskDestination.SETTINGS && settingsOrigin!=null) select(requireNotNull(settingsOrigin))
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
