package io.openhoyi.session

/** A completed GATT write callback cannot prove that the device ignored the bytes on failure. */
object GattCallbackResult {
    fun fromStatus(operation: GattOperation, status: Int,
        characteristics: List<CharacteristicInfo> = emptyList()): OperationResult = when {
        status == 0 -> OperationResult.Success(characteristics)
        operation is GattOperation.Write -> OperationResult.Unknown("GATT write status $status")
        else -> OperationResult.Failed("GATT status $status")
    }
}
