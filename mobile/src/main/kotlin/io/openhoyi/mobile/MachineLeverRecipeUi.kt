package io.openhoyi.mobile

import android.app.AlertDialog
import android.widget.Toast

/** The only product entry into a stored-machine recipe write is an explicit confirmation. */
internal object MachineLeverRecipeUi {
    fun confirm(host: MainActivity, item: CurveLibraryItem) {
        val service = host.currentService ?: return toast(host, host.getString(R.string.service_unavailable))
        service.leverRecipeWriteBlock()?.let { return toast(host, it) }
        val library = (host.application as MobileApplication).curves
        val profile = library.resolve(item.id, false, 7)
            ?: return toast(host, host.getString(R.string.lever_changed))
        val address = service.leverDeviceAddress ?: return toast(host, host.getString(R.string.start_block_coffee_not_ready))
        val firmware = service.coffeeFirmware?.toString() ?: return toast(host, host.getString(R.string.settings_missing))
        val store = (host.application as MobileApplication).leverRecipes
        val previousAttempt = store.attemptFor(address)?.token
        AppDialog.show(AlertDialog.Builder(host).setTitle(host.getString(R.string.lever_set_title))
            .setMessage(host.getString(R.string.lever_confirm, profile.name))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.lever_set_action) { _, _ ->
                if (host.currentService !== service || store.attemptFor(address)?.token != previousAttempt) toast(host, host.getString(R.string.lever_changed))
                else service.sendLeverRecipe(profile, address, firmware) { toast(host, it) }
                    ?.let { toast(host, it) }
            }.create())
    }

    fun details(host: MainActivity) {
        val service = host.currentService
        val store = (host.application as MobileApplication).leverRecipes
        val address = service?.leverDeviceAddress
        val record = address?.let(store::recordFor)
        val attempt = address?.let(store::attemptFor)
        val message = buildString {
            append(host.getString(R.string.lever_local_record))
            record?.let { append("\n\n"); append(it.snapshot.name) }
            if (attempt?.state == MachineLeverRecipeStore.State.UNKNOWN) {
                append("\n\n"); append(host.getString(R.string.lever_result_unknown))
            }
        }
        val builder = AlertDialog.Builder(host).setTitle(R.string.lever_title).setMessage(message)
            .setNegativeButton(android.R.string.cancel, null)
        if (attempt?.state == MachineLeverRecipeStore.State.UNKNOWN && !attempt.acknowledged) {
            builder.setPositiveButton(R.string.lever_ack_action) { _, _ ->
                service?.acknowledgeLeverRecipeUnknown()?.let { toast(host, it) }
            }
        } else builder.setPositiveButton(R.string.lever_choose_curve) { _, _ ->
            host.select(TaskDestination.CURVES)
        }
        AppDialog.show(builder.create())
    }
    private fun toast(host: MainActivity, message: String) =
        Toast.makeText(host.applicationContext, message, Toast.LENGTH_LONG).show()
}

/** Uses the immutable transmitted frame, never a potentially edited/deleted source draft. */
internal object MachineLeverRecipePreview {
    fun from(hex: String): CurveStageParameters.Preview? = runCatching {
        require(hex.length == 40)
        val b = hex.chunked(2).map { it.toInt(16) }
        require(b[0] == 8 && b.fold(0) { a, v -> a xor v } == 0)
        val count = b[1] and 15
        require(count in 1..4)
        CurveStageParameters.Preview((0 until count).map { index ->
            val flow = b[1] and 64 != 0 && (index > 0 || b[5] and 128 != 0)
            CurveStageParameters.Stage(b[6 + index], if (flow) CurveStageParameters.Unit.FLOW_ML_PER_SECOND
                else CurveStageParameters.Unit.PRESSURE_BAR)
        })
    }.getOrNull()
}
