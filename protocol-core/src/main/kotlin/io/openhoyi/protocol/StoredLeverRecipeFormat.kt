package io.openhoyi.protocol

/** Stored recipe wire format. Not a readback decoder.
 * Firmware 1.1.4/1.1.5 persist one 18-byte recipe; no complete readback/ACK is proven.
 * Transport authorization is separate in DeviceSession and the application transaction journal.
 */
object StoredLeverRecipeFormat {
    /** No slot or target-weight field exists. Does not itself authorize transmission. */
    fun candidate(parameters: StartParameters): ByteFrame {
        // Reuse range validation and StartParameters' valid-slot precondition.
        // The validated slot is not encoded; the stored count header is independent.
        // Preserve press/chart exactly as the legacy copy encoder does. `press` is a
        // control-logic flag (fixed/feedback, or flow strategy), not an "auto enabled" bit.
        // Forcing it true would change a custom pressure recipe's controller behavior.
        val start = CoffeeCommands.start(parameters).frame.toByteArray()
        val bytes = start.copyOf()
        bytes[0] = 0x08
        bytes[1] = ((if (parameters.pressureLogic) 0x80 else 0) or
            (if (parameters.variableFlowLogic) 0x40 else 0) or parameters.segmentCount).toByte()
        bytes[19] = bytes.take(19).fold(0) { acc, byte -> acc xor (byte.toInt() and 255) }.toByte()
        return ByteFrame(bytes)
    }
}
