package io.openhoyi.mobile

/** Legacy home.vue maps alarmBits bit0..bit14 to C1..C14,C16; bit15 has no known label. */
object MachineAlarms {
    data class Alarm(val code: String, val description: String)

    private val known = listOf(
        Alarm("C1", "蒸汽锅炉缺水"),
        Alarm("C2", "蒸汽锅炉缺水（补水阀超时）"),
        Alarm("C3", "蒸汽温度异常"),
        Alarm("C4", "蒸汽/热水档位异常"),
        Alarm("C5", "冲泡温度异常"),
        Alarm("C6", "冲泡档位异常；复原拨杆并重启机器"),
        Alarm("C7", "冲泡锅炉压力异常"),
        Alarm("C8", "流量计异常"),
        Alarm("C9", "水泵工作超时"),
        Alarm("C10", "进水压力过低或缺水"),
        Alarm("C11", "水箱缺水"),
        Alarm("C12", "粉饼压力异常"),
        Alarm("C13", "蒸汽压力异常"),
        Alarm("C14", "蒸汽/热水拨杆误触发；推回 OFF 档"),
        Alarm("C16", "水箱液位预警（尾水允许中）"),
    )

    fun active(bits: Int): List<Alarm> = buildList {
        known.forEachIndexed { index, alarm -> if (bits and (1 shl index) != 0) add(alarm) }
        if (bits and 0x8000 != 0) add(Alarm("bit15", "未知告警位 15（原始 0x%04X）".format(bits and 0xFFFF)))
    }

    /** C16 explicitly permits the last cup; all faults and the unknown bit require inspection. */
    fun startBlock(bits: Int): String? = active(bits).firstOrNull { it.code != "C16" }?.let {
        "机器告警 ${it.code} ${it.description}，暂不启动萃取"
    }

    fun describe(bits: Int?, receivedAt: Long?, now: Long): String {
        if (bits == null) return "尚未收到机器告警状态"
        val fresh = receivedAt != null && receivedAt <= now && now - receivedAt <= 1500
        val alarms = active(bits)
        if (!fresh) return if (alarms.isEmpty()) "告警状态已过期"
            else "告警状态已过期；上次回报：\n" + alarms.joinToString("\n") { "${it.code} ${it.description}" }
        return if (alarms.isEmpty()) "当前无告警"
            else "当前告警（${alarms.size}）\n" + alarms.joinToString("\n") { "${it.code} ${it.description}" }
    }
}
