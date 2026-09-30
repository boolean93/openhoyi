package io.openhoyi.mobile

import io.openhoyi.session.CoffeeAlarmPolicy

/** Resource-aware display of legacy bit0..bit14 C1..C14,C16 and the unknown bit15.
 * Whether control is permitted is decided by CoffeeAlarmPolicy, never by translated text.
 */
class MachineAlarms(private val resolve: (Int, Array<out Any>) -> String) {
    constructor(context: android.content.Context) : this({ id, args -> context.getString(id, *args) })
    private fun text(id: Int, vararg args: Any): String = resolve(id, args)

    data class Alarm(val code: String, val description: String)
    data class Banner(val message: String, val blocking: Boolean)

    private val known = listOf(
        "C1" to R.string.alarm_c1,
        "C2" to R.string.alarm_c2,
        "C3" to R.string.alarm_c3,
        "C4" to R.string.alarm_c4,
        "C5" to R.string.alarm_c5,
        "C6" to R.string.alarm_c6,
        "C7" to R.string.alarm_c7,
        "C8" to R.string.alarm_c8,
        "C9" to R.string.alarm_c9,
        "C10" to R.string.alarm_c10,
        "C11" to R.string.alarm_c11,
        "C12" to R.string.alarm_c12,
        "C13" to R.string.alarm_c13,
        "C14" to R.string.alarm_c14,
        "C16" to R.string.alarm_c16,
    )

    fun active(bits: Int): List<Alarm> = buildList {
        known.forEachIndexed { index, (code, resource) ->
            if (bits and (1 shl index) != 0) add(Alarm(code, text(resource)))
        }
        if (bits and 0x8000 != 0) add(Alarm("bit15",
            text(R.string.alarm_unknown_bit, "0x%04X".format(bits and 0xFFFF))))
    }

    /** C16 explicitly permits the last cup; all faults and the unknown bit require inspection. */
    fun startBlock(bits: Int): String? {
        val bit = CoffeeAlarmPolicy.firstBlockingBit(bits) ?: return null
        val alarm = if (bit == 15) active(bits).last()
            else known[bit].let { (code, resource) -> Alarm(code, text(resource)) }
        return text(R.string.alarm_start_block, alarm.code, alarm.description)
    }

    fun banner(bits: Int?, receivedAt: Long?, now: Long): Banner? {
        if (bits == null || receivedAt == null || receivedAt > now || now - receivedAt > 1500) return null
        val alarms = active(bits)
        if (alarms.isEmpty()) return null
        val blocking = !CoffeeAlarmPolicy.permitsNewControl(bits)
        val heading = text(if (blocking) R.string.alarm_blocking_heading else R.string.alarm_advisory_heading)
        return Banner(text(R.string.alarm_banner, heading, rows(alarms)), blocking)
    }

    fun describe(bits: Int?, receivedAt: Long?, now: Long): String {
        if (bits == null) return text(R.string.home_alarm_initial)
        val fresh = receivedAt != null && receivedAt <= now && now - receivedAt <= 1500
        val alarms = active(bits)
        if (!fresh) return if (alarms.isEmpty()) text(R.string.alarm_expired)
            else text(R.string.alarm_expired_values, rows(alarms))
        return if (alarms.isEmpty()) text(R.string.alarm_clear)
            else text(R.string.alarm_current_values, alarms.size.toString(), rows(alarms))
    }

    private fun rows(alarms: List<Alarm>): String =
        alarms.joinToString("\n") { text(R.string.alarm_row, it.code, it.description) }
}
