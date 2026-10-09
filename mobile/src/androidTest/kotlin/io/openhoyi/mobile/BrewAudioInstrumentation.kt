package io.openhoyi.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.Context
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.session.ExtractionState
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Built-in Android instrumentation, no external test framework. Mock only, no BLE. */
class BrewAudioInstrumentation : Instrumentation() {
    private var documentContracts = false
    private var idleEvents = false
    private var advancedFlows = false
    private var advancedPageProfile: String? = null
    private var pageProfile: String? = null
    private var languageChecks = false
    private var upgradeChecks: String? = null
    override fun onCreate(arguments: Bundle?) {
        documentContracts = arguments?.getString("documentContracts") == "true"
        idleEvents = arguments?.getString("idleEvents") == "true"
        advancedFlows = arguments?.getString("advancedFlows") == "true"
        advancedPageProfile = arguments?.getString("advancedPageChecks")
        pageProfile = arguments?.getString("pageChecks")
        languageChecks = arguments?.getString("languageChecks") == "true"
        upgradeChecks = arguments?.getString("upgradeChecks")
        super.onCreate(arguments); start()
    }
    override fun onStart() {
        val report = Bundle()
        try {
            check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
            if (documentContracts) {
                val result = CurveDocumentContractChecks(this).run()
                report.putString("stream", "CURVE_DOCUMENT_CONTRACT_CHECKS_PASSED paths=${result.paths.size} pickerContracts=${result.pickerContracts} shareContracts=${result.shareContracts} fileFixtures=${result.fileFixtures} newDrafts=${result.newDrafts} realResolver=true signaturePermission=true systemPicker=false externalSend=false noBle=true\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (idleEvents) {
                IdleAccessibilityDiagnostics(this).run()
                report.putString("stream", "IDLE_ACCESSIBILITY_DIAGNOSTIC_DONE\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (upgradeChecks != null) {
                val phase = requireNotNull(upgradeChecks)
                MockUpgradeChecks(this).run(phase)
                report.putString("stream", "MOCK_UPGRADE_${phase.uppercase(java.util.Locale.ROOT)}_PASSED\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (advancedFlows) {
                val result = AdvancedBusinessUiChecks(this).run()
                report.putString("stream", "ADVANCED_BUSINESS_UI_CHECKS_PASSED paths=${result.paths.size} inventoryEvents=${result.inventoryEvents} newDrafts=${result.newDrafts} language=zh-Hans theme=light sameService=true preservedMachinePrefs=true noBle=true\n" +
                    "ADVANCED_BUSINESS_UI_PATHS ${result.paths.joinToString(",")}\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (advancedPageProfile != null) {
                val profile = requireNotNull(advancedPageProfile)
                val result = AdvancedPageChecks(this).run(profile)
                report.putString("stream", "ADVANCED_PAGE_LAYOUT_CHECKS_PASSED profile=$profile languages=8 themes=2 pages=14 fixtures=${result.fixtures} preservedState=true noBle=true\n" +
                    "ADVANCED_PAGE_SCREENSHOTS profile=$profile count=${result.screenshots} directory=${result.directory.absolutePath}\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (pageProfile != null) {
                val profile = requireNotNull(pageProfile)
                LanguagePageChecks(this).run(profile)
                report.putString("stream", "LANGUAGE_PAGE_LAYOUT_CHECKS_PASSED profile=$profile languages=8 themes=2 pages=6 fixtures=96 settingsSections=5 scroll=true fixedStart=true preservedState=true\n" +
                    "LANGUAGE_DETAIL_DIALOG_CHECKS_PASSED profile=$profile languages=8 themes=2 curveKinds=3 manualWarnings=3 cancelledStart=true\n" +
                    "LANGUAGE_SELECTOR_UI_CHECKS_PASSED profile=$profile languages=8 themes=2 choices=8 switched=true restored=true unchanged=true saveFailure=true sameService=true\n" +
                    "TREND_CHART_TEXT_CHECKS_PASSED profile=$profile languages=8 themes=2 metrics=2 cases=3 fontScale=true viewport=true noBle=true\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            if (languageChecks) {
                NotificationFailureChecks(this).run()
                HubCleanupChecks(this).run()
                ServiceOwnerCleanupChecks(this).run()
                ServiceShutdownChecks(this).run()
                ServiceRecoveryGateChecks(this).run()
                ServiceRecoveryPersistenceChecks(this).run()
                ServiceRecoveryCacheFailureChecks(this).run()
                RecoveryMarkerReadChecks(this).run()
                ServiceStopFeedbackChecks(this).run()
                ServiceWriteRegistrationChecks(this).run()
                ServiceTransportUnknownChecks(this).run()
                ServiceWriteWatchdogChecks(this).run()
                ServiceOrdinaryDispatchChecks(this).run()
                ServiceOrdinaryDispatchChecks(this).runOwnership()
                ServiceStandaloneTareDispatchChecks(this).run()
                ServiceStandaloneTareDispatchChecks(this).runPersistence()
                ServiceShotDispatchChecks(this).run()
                ServiceShotDispatchChecks(this).runCompletion()
                ServiceWriteDisconnectionChecks(this).run()
                ServiceWriteDisconnectionChecks(this).runReplacement()
                ServicePreheatCancellationChecks(this).run()
                ServicePreheatCancellationChecks(this).runBlocked()
                ServicePreheatCancellationChecks(this).runQueuedManual()
                ServicePreheatCancellationChecks(this).runQueuedOwner()
                ServicePreheatCancellationChecks(this).runQueuedPrepare()
                ServicePreheatCancellationChecks(this).runTimeoutOwner()
                ServiceWriteReadbackChecks(this).run()
                ServiceWriteReadbackChecks(this).runClearFailure(false)
                ServiceWriteReadbackChecks(this).runClearFailure(true)
                fun <T> stage(name: String, checkStage: () -> T): T {
                    android.util.Log.i("OpenHoyiLanguage", "START $name")
                    sendStatus(0, Bundle().apply { putString("stream", "LANGUAGE_STAGE_START $name\n") })
                    val result = checkStage()
                    android.util.Log.i("OpenHoyiLanguage", "PASS $name")
                    sendStatus(0, Bundle().apply { putString("stream", "LANGUAGE_STAGE_PASS $name\n") })
                    return result
                }
                val home = stage("context") { LanguageContextChecks(this).run() }
                stage("activeCup") { LanguageActiveCupChecks(this).run() }
                stage("charts") { LanguageChartChecks(this).run() }
                stage("chart-legends") { ChartLegendChecks(this).run() }
                // Home remains the task root after Extraction finishes. NEW_TASK would reuse it,
                // while startActivitySync waits indefinitely for a new onCreate callback.
                stage("notificationFactory") { LanguageNotificationChecks(this, home).run() }
                stage("notificationPosting") { LanguageNotificationPostingChecks(this, home).run() }
                stage("serviceNotificationRefresh") { LanguageServiceNotificationChecks(this, home).run() }
                report.putString("stream", "SERVICE_PREHEAT_TIMEOUT_OWNER_CHECKS_PASSED fixtures=6 fakeWrites=8 delayMs=600000 phases=2 allowedCancels=2 blockedCancels=4 reloadedRecords=6 actualScheduledCallback=true simulatedExpiry=true lateCallbackIgnored=true noBle=true detached=true\n" +
                    "SERVICE_STOP_FEEDBACK_CHECKS_PASSED fixtures=2 fakeWrites=4 retainedRecords=2 missingJournal=true exactStopWire=true repeatedStopIgnored=true noBle=true detached=true\n" +
                    "RECOVERY_MARKER_READ_CHECKS_PASSED recordFixtures=20 blockedEntries=108 absentClear=2 tareFixtures=4 realDisk=true noBle=true detached=true\n" +
                    "SERVICE_RECOVERY_CACHE_FAILURE_CHECKS_PASSED fixtures=12 cacheCleared=12 fullRecordsRetained=12 blockedEntries=72 serviceRebuilt=true successfulClears=12 throwsAndFalse=true noBle=true detached=true\n" +
                    "SERVICE_DURABLE_TARE_CHECKS_PASSED fixtures=4 fakeWrites=4 fakeBarriers=4 restoredUnknown=4 explicitRetryConfirmed=1 pendingRetained=3 lateCallbackIgnored=true noBle=true detached=true\n" +
                    "SERVICE_STANDALONE_TARE_DISPATCH_CHECKS_PASSED fixtures=10 fakeWrites=2 fakeBarriers=10 blocked=8 newDecodedZero=2 coffeeOfflineAllowed=true lateCallbackIgnored=true noBle=true detached=true\n" +
                    "SERVICE_QUEUED_PREHEAT_OWNER_CHECKS_PASSED fixtures=2 fakeWrites=0 fakeBarriers=2 sameAddressRearm=true curveChanged=true retainedPending=true noBle=true detached=true\n" +
                    "SERVICE_QUEUED_CANCEL_OWNER_CHECKS_PASSED phases=2 fakePreheatWrites=2 fakeBarriers=2 sameAddressRearm=true noCancelDispatch=true unknownRetained=true blockedEntries=24 reloadedRecords=2 lateSuccessIgnored=true noBle=true detached=true\n" +
                    "SERVICE_ORDINARY_OWNER_CHECKS_PASSED fixtures=12 fakeWrites=9 fakeBarriers=12 retainedReloads=12 phases=queued,failed,readback sameAddressRearm=true lateSuccessIgnored=true noBle=true detached=true\n" +
                    "SERVICE_SHOT_COMPLETION_OWNER_CHECKS_PASSED fixtures=21 fakeWrites=35 fakeBarriers=21 clearedShots=9 retainedShots=12 clearedPreheat=3 retainedPreheat=3 reloadedRecords=21 actualTicker=true syntheticCompletion=true noBle=true detached=true\n" +
                    "SERVICE_SHOT_DISPATCH_CHECKS_PASSED fixtures=39 fakeWrites=21 fakeBarriers=39 blocked=33 reloadedRecords=39 exactWire=true durableBeforeWrite=true lateSuccessIgnored=true syntheticReady=true noBle=true detached=true\n" +
                    "SERVICE_ORDINARY_DISPATCH_CHECKS_PASSED fixtures=39 fakeWrites=12 fakeBarriers=39 firstFrameBlocks=28 partialUnknown=7 clearedReloads=28 retainedReloads=11 durableBeforeWrite=true lateSuccessIgnored=true noBle=true detached=true\n" +
                    "SERVICE_QUEUED_CANCEL_MANUAL_CHECKS_PASSED phases=2 fakePreheatWrites=2 fakeBarriers=2 noCancelDispatch=true unknownRetained=true blockedEntries=24 reloadedRecords=2 lateSuccessIgnored=true noBle=true detached=true\n" +
                    "SERVICE_PREHEAT_CANCEL_BLOCKS_CHECKS_PASSED phases=2 blocks=20 fakePreheatWrites=2 noCancelDispatch=true exactResources=true preservedToken=true preservedBaseline=true retainedPending=true noBle=true detached=true\n" +
                    "SERVICE_PREHEAT_CANCELLATION_CHECKS_PASSED fixtures=10 phases=2 fakeWrites=20 blockedEntries=120 recoveredAcknowledgements=8 retainedDisconnected=2 durableBeforeCancel=true lateSuccessIgnored=true freshIdleNotProof=true noBle=true detached=true\n" +
                    "SERVICE_WRITE_REPLACEMENT_CHECKS_PASSED fixtures=8 fakeWrites=9 fakeConnects=4 wrongDeviceBlocks=8 busyReconnectBlocks=4 blockedEntries=96 reloadedRecords=8 oldGenerationIgnored=true newOwnerPreserved=true noBle=true detached=true\n" +
                    "SERVICE_READBACK_CLEAR_FAILURE_CHECKS_PASSED fixtures=8 storageModes=2 fakeDispatches=10 failedClears=8 blockedEntries=96 retainedReloads=8 recoveredClears=8 failureLast=true exactFailureTrace=true noWireRetry=true noBle=true detached=true\n" +
                    "SERVICE_WRITE_READBACK_CHECKS_PASSED fixtures=4 fakeDispatches=5 confirmations=4 clearedReloads=4 preWriteIgnored=true staleOwnerIgnored=true incompleteReadbackBlocked=true oldWatchdogIgnored=true exactConfirmation=true noBle=true detached=true\n" +
                    "SERVICE_WRITE_DISCONNECTION_CHECKS_PASSED fixtures=8 fakeDispatches=9 phases=2 blockedEntries=96 reloadedRecords=8 realStateCallback=true lateSuccessIgnored=true oldWatchdogIgnored=true retainedPending=true noBle=true detached=true\n" +
                    "SERVICE_WRITE_WATCHDOG_CHECKS_PASSED fixtures=4 fakeDispatches=5 blockedEntries=48 reloadedRecords=4 exactScheduledDelay=true liveBaselines=true repeatedExpiryIgnored=true retainedPending=true noBle=true detached=true\n" +
                    "SERVICE_TRANSPORT_UNKNOWN_CHECKS_PASSED fixtures=5 fakeDispatches=5 blockedEntries=60 reloadedRecords=5 exactUnknownEvent=true durableBeforeDispatch=true retainedPending=true guardedFakeTransport=true noBle=true detached=true\n" +
                    "SERVICE_WRITE_REGISTRATION_CHECKS_PASSED fixtures=10 failedRegistrations=20 storageModes=2 exactFailureResource=true retainedState=true noTransport=true syntheticReady=true\n" +
                    "SERVICE_RECOVERY_PERSISTENCE_CHECKS_PASSED fixtures=5 wrongIdentityAcknowledgements=5 disconnectedAcknowledgements=5 missingTimestampAcknowledgements=5 unchangedReadbackAcknowledgements=5 incompleteEvidenceAcknowledgements=5 busyStages=2 preheatReady=true preheatCancelling=true blockedAcknowledgements=12 blockedEntries=69 failedWrites=5 retries=5 retainedGate=true exactFailureEvent=true noTransport=true syntheticReady=true\n" +
                    "SERVICE_RECOVERY_GATE_CHECKS_PASSED fixtures=18 entries=108 acknowledgementAttempts=18 availabilityChecks=18 lazyShotRead=true preservedPending=true detached=true noBle=true\n" +
                    "SERVICE_SHUTDOWN_CHECKS_PASSED fixtures=4 allowed=1 blocked=3 pendingPreserved=true localManager=true noBle=true\n" +
                    "SERVICE_OWNER_CLEANUP_CHECKS_PASSED detached=true fakeTransports=true callbacksRemoved=true pendingPreserved=true noBle=true ownerThread=true\n" +
                    "HUB_CLEANUP_CHECKS_PASSED fixtures=4 detached=true fakeTransports=true bothOwnersClosed=true tickersRemoved=true noBle=true\n" +
                    "NOTIFICATION_FAILURE_CHECKS_PASSED lookup=true detachedCleanup=true preservedControl=true noOwner=true rendering=true errorReporting=true failedDeliveryRetried=true\n" +
                    "LANGUAGE_CONTEXT_CHECKS_PASSED languages=8 themes=2 sameService=true preservedState=true\n" +
                    "LANGUAGE_ACTIVE_CUP_CHECKS_PASSED languages=8 sameCup=true retainedSamples=true translatedStop=true\n" +
                    "LANGUAGE_CHART_CHECKS_PASSED languages=8 themes=2 charts=3 scientificOrdering=true\n" +
                    "CHART_LEGEND_CHECKS_PASSED languages=8 themes=2 widths=4 fixtures=64 fullLabels=true withinBounds=true noOverlap=true plotPreserved=true nativeCanvas=true noBle=true\n" +
                    "LANGUAGE_NOTIFICATION_FACTORY_CHECKS_PASSED languages=8 stableChannels=true translatedActions=true\n" +
                    "LANGUAGE_UI_COMPONENT_LAYOUT_CHECKS_PASSED languages=8 themes=2 compactWidths=320,360,600 selections=5 fixedStop=true\n" +
                    "LANGUAGE_UI_WIDE_FONT_LAYOUT_CHECKS_PASSED languages=8 themes=2 widths=320,360,600,700,1000 fontScales=1.0,1.3 selections=5\n" +
                    "LANGUAGE_SERVICE_NOTIFICATION_CHECKS_PASSED languages=8 detached=true eligibility=true dedup=true forcedRefresh=true contextFailure=true retryPublished=true partialRepairSynthetic=true\n" +
                    "LANGUAGE_NOTIFICATION_POSTING_CHECKS_PASSED languages=8 stableKeys=true translatedUpdates=true removed=true\n")
                finish(Activity.RESULT_OK, report)
                return
            }
            val home = startActivitySync(Intent(targetContext, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as HomeActivity
            waitForIdleSync()
            var driver: AndroidBrewFeedbackAudio? = null
            runOnMainSync { driver = AndroidBrewFeedbackAudio(targetContext) }
            val cancelledCallbacks = AtomicInteger()
            runOnMainSync {
                driver!!.start("brew-feedback/1.mp3") { cancelledCallbacks.incrementAndGet() }.cancel()
            }
            Thread.sleep(300)
            check(cancelledCallbacks.get() == 0) { "Cancelled prepare delivered a callback" }
            val clips = BrewFeedbackClips.Level.entries.flatMap { level ->
                (0..3).flatMap { BrewFeedbackClips.sequence(level, it) }
            }.toSet()
            for(path in clips) {
                val latch = CountDownLatch(1)
                val result = AtomicReference<BrewFeedbackAudio.Result>()
                val callbacks = AtomicInteger()
                var cancel: BrewFeedbackAudio.Cancel? = null
                runOnMainSync {
                    cancel = driver!!.start(path) { result.set(it); callbacks.incrementAndGet(); latch.countDown() }
                }
                val completed = latch.await(20, TimeUnit.SECONDS)
                runOnMainSync { cancel?.cancel() }
                check(completed && callbacks.get() == 1 && result.get() == BrewFeedbackAudio.Result.COMPLETED) {
                    "$path decode/playback: completed=$completed callbacks=${callbacks.get()} result=${result.get()}"
                }
            }
            val latch = CountDownLatch(1)
            val results = mutableListOf<BrewFeedbackAudio.Result>()
            var audio: BrewFeedbackAudio? = null
            runOnMainSync {
                audio = BrewFeedbackAudio(object : BrewFeedbackAudio.Driver {
                    override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit) =
                        driver!!.start(path) { result ->
                            results.add(result); done(result)
                            if (audio?.playing == false) latch.countDown()
                        }
                })
                audio!!.play(BrewFeedbackClips.Level.BRAVO, 0)
            }
            val finished = latch.await(30, TimeUnit.SECONDS)
            runOnMainSync { audio?.stop() }
            check(finished && results == listOf(BrewFeedbackAudio.Result.COMPLETED, BrewFeedbackAudio.Result.COMPLETED))
            verifyFocusInterruption()
            verifyFeedbackService(home)
            report.putString("stream", "LOCAL_AUDIO_CHECKS_PASSED clips=${clips.size} cancelledPrepare=true sequence=true\n" +
                "LOCAL_FEEDBACK_SERVICE_CHECKS_PASSED observedEnd=true telemetryPhase=8 clearedOnNextCup=true earlyStopSuppressed=true\n" +
                "LOCAL_FEEDBACK_UI_CHECKS_PASSED switchPersisted=true recreatedWithoutReplay=true newCupDismissed=true\n" +
                "LOCAL_AUDIO_FOCUS_CHECKS_PASSED interrupted=true sequenceSuppressed=true\n" +
                "LOCAL_FEEDBACK_EXIT_CHECKS_PASSED previewDisabled=true previewPageExit=true extractionPageExit=true noBackfill=true\n")
            finish(Activity.RESULT_OK, report)
        } catch (error: Throwable) {
            report.putString("stream", "${if (documentContracts) "CURVE_DOCUMENT_CONTRACT_CHECKS_FAILED" else if (idleEvents) "IDLE_ACCESSIBILITY_DIAGNOSTIC_FAILED" else if (upgradeChecks != null) "MOCK_UPGRADE_CHECKS_FAILED" else if (advancedFlows) "ADVANCED_BUSINESS_UI_CHECKS_FAILED" else if (advancedPageProfile != null) "ADVANCED_PAGE_CHECKS_FAILED" else if (pageProfile != null) "LANGUAGE_PAGE_CHECKS_FAILED" else if (languageChecks) "LANGUAGE_CHECKS_FAILED" else "LOCAL_AUDIO_CHECKS_FAILED"} ${error.stackTraceToString()}\n")
            finish(Activity.RESULT_CANCELED, report)
        }
    }
    /** The isolated emulator has no other media owner; require a stable native audio state. */
    private fun awaitMusic(active: Boolean, timeoutMs: Long = 5000) {
        val manager = targetContext.getSystemService(android.media.AudioManager::class.java)
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        var stableSince: Long? = null
        while (true) {
            val now = SystemClock.elapsedRealtime()
            if (manager.isMusicActive == active) {
                if (stableSince == null) stableSince = now
                if (now - stableSince >= 100) return
            } else stableSince = null
            check(now < deadline) { "Native audio did not stabilize active=$active" }
            Thread.sleep(20)
        }
    }
    /** Real Android focus loss must cancel the local sequence, including its pending voice. */
    private fun verifyFocusInterruption() {
        val manager = targetContext.getSystemService(android.media.AudioManager::class.java)
        val lost = CountDownLatch(1)
        val starts = mutableListOf<String>()
        val results = mutableListOf<BrewFeedbackAudio.Result>()
        var audio: BrewFeedbackAudio? = null
        var competitor: android.media.AudioFocusRequest? = null
        try {
            awaitMusic(false)
            runOnMainSync {
                val driver = AndroidBrewFeedbackAudio(targetContext)
                audio = BrewFeedbackAudio(object : BrewFeedbackAudio.Driver {
                    override fun start(path: String, done: (BrewFeedbackAudio.Result) -> Unit): BrewFeedbackAudio.Cancel {
                        starts.add(path)
                        return driver.start(path) { result ->
                            results.add(result); done(result); lost.countDown()
                        }
                    }
                })
                audio!!.play(BrewFeedbackClips.Level.BRAVO, 0)
            }
            awaitMusic(true)
            runOnMainSync {
                competitor = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA).build())
                    .setOnAudioFocusChangeListener { }.build()
                check(manager.requestAudioFocus(competitor!!) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
            }
            check(lost.await(5, TimeUnit.SECONDS)) { "Focus loss was not delivered" }
            awaitMusic(false)
            runOnMainSync {
                check(!audio!!.playing && starts == listOf("brew-feedback/1.mp3") &&
                    results == listOf(BrewFeedbackAudio.Result.INTERRUPTED)) { "Focus loss advanced local voice: $starts $results" }
            }
        } finally {
            runOnMainSync { audio?.stop(); competitor?.let { manager.abandonAudioFocusRequest(it) } }
        }
    }
    /** Inspect the existing local request state without adding a production test API. Main thread only. */
    private fun playback(owner: Any): BrewFeedbackAudio? {
        val field = owner.javaClass.getDeclaredField("audioOwner").apply { isAccessible = true }
        val lazy = field.get(owner) as Lazy<*>
        return if (lazy.isInitialized()) lazy.value as BrewFeedbackAudio else null
    }
    private fun button(root: android.view.View, label: String): android.widget.Button? {
        if (root is android.widget.Button && root.text.toString() == label) return root
        if (root is android.view.ViewGroup) for (index in 0 until root.childCount)
            button(root.getChildAt(index), label)?.let { return it }
        return null
    }
    /** Calls only the guarded Mock service's real product start/stop APIs. */
    private fun verifyFeedbackService(initialHome: HomeActivity) {
        var home = initialHome
        check(BuildConfig.MOCK_MODE && targetContext.packageName == "io.openhoyi.mobile.mock")
        val connected = CountDownLatch(1)
        val owner = AtomicReference<MobileService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                owner.set((binder as MobileService.LocalBinder).service); connected.countDown()
            }
            override fun onServiceDisconnected(name: ComponentName?) { owner.set(null) }
        }
        var bound = false
        val prefs = targetContext.getSharedPreferences("curves", Context.MODE_PRIVATE)
        val previous = prefs.getString("selected", null)
        val feedbackPrefs = (targetContext.applicationContext as MobileApplication).feedbackPreferences
        val previouslyEnabled = feedbackPrefs.enabled
        fun autoplayCount(): Int = android.os.ParcelFileDescriptor.AutoCloseInputStream(
            uiAutomation.executeShellCommand("logcat -d -v brief -s OpenHoyiFeedback:I '*:S'"))
            .bufferedReader().use { it.readLines().count { line -> line.contains("dialog.autoplay:") } }
        fun awaitMain(timeoutMs: Long, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + timeoutMs
            while (true) {
                var success = false
                runOnMainSync { success = condition() }
                if (success) return
                check(SystemClock.elapsedRealtime() < deadline) { "Mock feedback condition timed out" }
                Thread.sleep(100)
            }
        }
        try {
            runOnMainSync { check(feedbackPrefs.setEnabled(false)) }
            val settings = startActivitySync(Intent(targetContext, AppSettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as AppSettingsActivity
            waitForIdleSync()
            runOnMainSync {
                check(!settings.feedbackCard.enabledSwitch.isChecked)
                settings.feedbackCard.enabledSwitch.performClick()
                check(feedbackPrefs.enabled)
                check(targetContext.getSharedPreferences("brew_feedback", Context.MODE_PRIVATE).getBoolean("enabled", false))
                requireNotNull(button(settings.window.decorView, settings.getString(R.string.feedback_preview))).performClick()
                check(playback(settings.feedbackCard)?.playing == true)
            }
            awaitMusic(true)
            runOnMainSync {
                settings.feedbackCard.enabledSwitch.performClick()
                check(!feedbackPrefs.enabled && playback(settings.feedbackCard)?.playing == false)
            }
            awaitMusic(false, 1000)
            runOnMainSync {
                settings.feedbackCard.enabledSwitch.performClick()
                requireNotNull(button(settings.window.decorView, settings.getString(R.string.feedback_preview))).performClick()
                check(playback(settings.feedbackCard)?.playing == true)
            }
            awaitMusic(true)
            val temporaryMonitor = addMonitor(ExtractionActivity::class.java.name, null, false)
            runOnMainSync { settings.startActivity(Intent(settings, ExtractionActivity::class.java)) }
            // Check immediately after requesting navigation; waiting for Activity startup first could hide natural completion.
            awaitMusic(false, 1000)
            val temporaryExtraction = waitForMonitorWithTimeout(temporaryMonitor, 10000) as? ExtractionActivity
                ?: error("Temporary Extraction navigation not observed")
            removeMonitor(temporaryMonitor)
            waitForIdleSync()
            runOnMainSync {
                check(playback(settings.feedbackCard)?.playing == false) { "App settings exit left preview active" }
                temporaryExtraction.finish()
            }
            waitForIdleSync()
            runOnMainSync { settings.finish() }
            waitForIdleSync()
            runOnMainSync {
                bound = targetContext.bindService(Intent(targetContext, MobileService::class.java),
                    connection, Context.BIND_AUTO_CREATE)
            }
            check(bound && connected.await(10, TimeUnit.SECONDS))
            check(prefs.edit().putString("selected", "capture-2").commit())
            awaitMain(5000) { owner.get()?.machineSettingsFresh == true }
            runOnMainSync { check(owner.get()!!.startShot("capture-2", null, 7) == null) }
            awaitMain(5000) { (owner.get()?.snapshot?.coffee as? ExtractionTelemetry)?.slotOrPhase == 8 }
            awaitMain(40000) {
                owner.get()?.shotState == ExtractionState.ENDED_OBSERVED && owner.get()?.brewFeedbackResult != null
            }
            awaitMain(5000) { home.feedbackUi.isShowing }
            val beforeRecreation = autoplayCount()
            check(beforeRecreation > 0)
            val monitor = addMonitor(HomeActivity::class.java.name, null, false)
            runOnMainSync { home.recreate() }
            home = waitForMonitorWithTimeout(monitor, 10000) as? HomeActivity
                ?: error("Home recreation not observed")
            removeMonitor(monitor)
            awaitMain(5000) { home.feedbackUi.isShowing }
            check(autoplayCount() == beforeRecreation) { "Recreation replayed feedback" }
            runOnMainSync {
                val result = requireNotNull(owner.get()!!.brewFeedbackResult)
                check(result.machineSeconds > 14 && result.earlyWaterTenthsMl <= 60)
                check(result.level == BrewFeedbackClips.Level.LOW_FLOW)
                check(owner.get()!!.startShot("capture-2", null, 7) == null)
                check(owner.get()!!.brewFeedbackResult == null)
            }
            awaitMain(5000) { owner.get()?.snapshot?.coffee is ExtractionTelemetry && !home.feedbackUi.isShowing }
            Thread.sleep(250)
            runOnMainSync { owner.get()!!.stopShot() }
            awaitMain(5000) { owner.get()?.shotState == ExtractionState.ENDED_OBSERVED &&
                owner.get()?.snapshot?.coffee !is ExtractionTelemetry }
            runOnMainSync { check(owner.get()!!.brewFeedbackResult == null) }
            val extraction = startActivitySync(Intent(targetContext, ExtractionActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as ExtractionActivity
            waitForIdleSync()
            runOnMainSync { check(owner.get()!!.startShot("capture-2", null, 7) == null) }
            awaitMain(40000) { extraction.feedbackUi.isShowing }
            awaitMusic(true)
            runOnMainSync {
                check(playback(extraction.feedbackUi)?.playing == true)
                extraction.finish()
            }
            awaitMusic(false, 1000)
            waitForIdleSync()
            runOnMainSync {
                check(!extraction.feedbackUi.isShowing && playback(extraction.feedbackUi)?.playing == false)
                home.feedbackUi.update(owner.get(), true)
                check(!home.feedbackUi.isShowing) { "Completed cup was delivered twice across pages" }
                check(feedbackPrefs.setEnabled(false))
                check(feedbackPrefs.setEnabled(true))
                home.feedbackUi.update(owner.get(), true)
                check(!home.feedbackUi.isShowing) { "Re-enabling feedback resurrected an old cup" }
            }
        } finally {
            runOnMainSync {
                owner.get()?.stopShot()
                check(feedbackPrefs.setEnabled(previouslyEnabled))
                if (bound) targetContext.unbindService(connection)
            }
            val edit = prefs.edit()
            if (previous == null) edit.remove("selected") else edit.putString("selected", previous)
            check(edit.commit())
        }
    }

}
