package io.openhoyi.session

import java.time.LocalDateTime

internal class CoffeeSessionFixture {
    var now = 0L
    val calls = mutableListOf<Triple<Long, Long, GattOperation>>()
    var executeFailure: Exception? = null
    var closes = 0
    val driver = object : GattDriver {
        override fun execute(generation: Long, token: Long, operation: GattOperation): Boolean {
            calls += Triple(generation, token, operation)
            executeFailure?.let { throw it }
            return true
        }
        override fun close(generation: Long) { closes++ }
    }
    val session = DeviceSession(DeviceRole.COFFEE, driver, { now })
    fun hex(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    fun complete(result: OperationResult = OperationResult.Success()) {
        val (generation, token, _) = calls.last()
        session.onComplete(generation, token, result)
    }
    fun idle(at: Long) {
        now = at
        session.onNotification(session.generation, KnownGatt.coffeeNotify,
            hex("400024BF2F1C770B00000000000000190321AF"))
    }
    init {
        session.connect("device", CoffeeAuthentication(LocalDateTime.of(2026, 9, 20, 12, 0), "123456"))
        complete()
        complete(OperationResult.Success(listOf(
            CharacteristicInfo(KnownGatt.coffeeWrite, true, false, false, false),
            CharacteristicInfo(KnownGatt.coffeeNotify, false, false, true, false))))
        complete(); complete()
        session.onNotification(session.generation, KnownGatt.coffeeNotify, hex("830113FD5C007D0F350019006E"))
        check(session.state == DeviceState.READY)
        idle(0)
    }
}
