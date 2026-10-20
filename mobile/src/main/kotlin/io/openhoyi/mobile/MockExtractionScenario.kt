package io.openhoyi.mobile

import io.openhoyi.protocol.ExtractionTelemetry
import io.openhoyi.protocol.HoyiMessage
import io.openhoyi.protocol.IdleTelemetry
import io.openhoyi.protocol.ScaleCapabilities
import io.openhoyi.protocol.StartParameters
import io.openhoyi.session.CoffeeControl
import io.openhoyi.session.DeviceState
import io.openhoyi.session.ExtractionController
import io.openhoyi.session.ExtractionState
import io.openhoyi.session.OperationResult
import io.openhoyi.session.ScaleControl

/** Opt-in synthetic inputs for a single Mock Service. Never a device acknowledgement. */
internal class MockExtractionScenario(
    private val mockMode: Boolean,
    private val isMainThread: () -> Boolean,
    private val clockOwned: (Any) -> Boolean,
    private val hubAbsent: () -> Boolean,
    private val now: () -> Long,
    private val deliverCoffeeFrame: (HoyiMessage) -> Unit,
    private val deliverCoffeeState: (DeviceState) -> Unit,
) {
    private var installedOwner: Any? = null
    val isInstalled get() = installedOwner != null
    private var nextRequestId = 0L
    private var lastFrameAt: Long? = null
    var offeredFrameSerial = 0L
        private set
    private data class Pending(val id: Long, val beforeDispatch: (() -> Boolean)?, val done: (OperationResult) -> Unit)
    private var pendingStart: Pending? = null
    private var pendingStop: Pending? = null
    private var approved: StartParameters? = null
    private var coffeeState = DeviceState.READY
    private val coffee = object : CoffeeControl {
        override val ready get() = coffeeState == DeviceState.READY
        override fun prepareStart(parameters: StartParameters): Boolean {
            approved = parameters.takeIf { ready }
            return approved != null
        }
        override fun startConditionsValid(parameters: StartParameters) = ready && approved == parameters
        override fun start(parameters: StartParameters, beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) {
            check(startConditionsValid(parameters) && pendingStart == null)
            approved = null
            pendingStart = Pending(++nextRequestId, beforeDispatch, done)
        }
        override fun stop(done: (OperationResult) -> Unit) {
            check(pendingStop == null)
            pendingStop = Pending(++nextRequestId, null, done)
        }
    }
    private val scale = object : ScaleControl {
        override val ready get() = false
        override val startAllowed get() = true
        override val capabilities get() = ScaleCapabilities(tare = false, validatedWeightControl = false)
        override fun tare(beforeDispatch: () -> Boolean, done: (OperationResult) -> Unit) {
            done(OperationResult.Failed("synthetic scale control is not a validated tare"))
        }
    }
    private var controller: ExtractionController? = null
    val state get() = requireNotNull(controller) { "Mock extraction scenario is not installed" }.state
    val stopReason get() = controller?.stopReason?.name
    val preparingScale get() = controller?.preparingScale == true
    val pendingStartId get() = pendingStart?.id
    val pendingStopId get() = pendingStop?.id

    private fun requireAccess(owner: Any) {
        check(mockMode) { "Synthetic extraction is unavailable in a real build" }
        check(isMainThread()) { "Synthetic extraction requires the main thread" }
        check(clockOwned(owner)) { "Synthetic extraction requires its owned monotonic clock" }
        check(hubAbsent()) { "Synthetic extraction cannot coexist with a device hub" }
    }
    private fun requireOwner(owner: Any) {
        requireAccess(owner)
        check(installedOwner === owner) { "Synthetic extraction owner mismatch or not installed" }
    }
    fun install(owner: Any) {
        requireAccess(owner)
        check(installedOwner == null) { "Synthetic extraction owner already installed" }
        check(now() >= 0)
        installedOwner = owner
        coffeeState = DeviceState.READY
        controller = ExtractionController(coffee, scale, now)
    }
    fun clear(owner: Any) {
        requireOwner(owner)
        // Destroying the scoped Service drops synthetic callbacks; it does not
        // complete a pending command or invent an acknowledgement.
        installedOwner = null
        controller = null
        approved = null
        pendingStart = null
        pendingStop = null
        lastFrameAt = null
        offeredFrameSerial = 0L
    }
    fun start(owner: Any, parameters: StartParameters, targetHundredthsGram: Int,
        compensationHundredthsGram: Int, beforeDispatch: () -> Boolean): Boolean {
        requireOwner(owner)
        return requireNotNull(controller).start(parameters, targetHundredthsGram,
            compensationHundredthsGram, beforeDispatch)
    }
    fun stop(owner: Any) {
        requireOwner(owner)
        requireNotNull(controller).manualStop()
    }
    fun completeStart(owner: Any, requestId: Long, result: OperationResult) {
        requireOwner(owner)
        val pending = pendingStart
        check(pending != null && pending.id == requestId && state == ExtractionState.STARTING) {
            "Stale synthetic start completion"
        }
        pendingStart = null
        pending.done(if (pending.beforeDispatch?.invoke() == true) result
            else OperationResult.Cancelled("synthetic dispatch guard rejected"))
    }
    fun completeStop(owner: Any, requestId: Long, result: OperationResult) {
        requireOwner(owner)
        val pending = pendingStop
        check(pending != null && pending.id == requestId && state == ExtractionState.STOP_REQUESTED) {
            "Stale synthetic stop completion"
        }
        pendingStop = null
        pending.done(result)
    }
    fun coffeeState(owner: Any, state: DeviceState) {
        requireOwner(owner)
        coffeeState = state
        deliverCoffeeState(state)
        requireNotNull(controller).tick()
    }
    /** A tick without an offer has no telemetry or series side effect. */
    fun tick(owner: Any) {
        requireOwner(owner)
        requireNotNull(controller).tick()
    }
    fun offerCoffeeFrame(owner: Any, frame: HoyiMessage) {
        requireOwner(owner)
        require(frame is ExtractionTelemetry || frame is IdleTelemetry) { "Only typed machine telemetry is accepted" }
        val at = now()
        require(at >= 0 && lastFrameAt?.let { at > it } != false) { "Synthetic frame time must increase" }
        lastFrameAt = at
        // A device-initiated manual cup belongs to the existing passive observer,
        // not to an app-command controller that is still IDLE.
        if (state != ExtractionState.IDLE) requireNotNull(controller).machineFrame(frame, at)
        offeredFrameSerial++
        deliverCoffeeFrame(frame)
    }
}
