package io.openhoyi.session

data class Endpoint(val service: String, val characteristic: String)
data class CharacteristicInfo(val endpoint: Endpoint, val write: Boolean, val writeWithoutResponse: Boolean, val notify: Boolean, val indicate: Boolean)
sealed interface GattOperation {
    data class Connect(val address: String) : GattOperation
    data object Discover : GattOperation
    data class Subscribe(val endpoint: Endpoint, val indication: Boolean = false) : GattOperation
    class Write(val endpoint: Endpoint, bytes: ByteArray, val withResponse: Boolean = true) : GattOperation {
        private val payload=bytes.copyOf()
        val bytes: ByteArray get()=payload.copyOf()
        override fun toString()="Write(endpoint=$endpoint, length=${payload.size}, withResponse=$withResponse)"
    }
}
sealed interface OperationResult {
    data class Success(val characteristics: List<CharacteristicInfo> = emptyList()) : OperationResult
    data class Failed(val reason: String) : OperationResult
    data class Unknown(val reason: String) : OperationResult
    data class Cancelled(val reason: String) : OperationResult
}
/** One driver instance belongs to exactly one device connection owner. Callbacks are marshalled to the owner's thread. */
interface GattDriver {
    fun execute(generation: Long, token: Long, operation: GattOperation): Boolean
    fun close(generation: Long)
}
