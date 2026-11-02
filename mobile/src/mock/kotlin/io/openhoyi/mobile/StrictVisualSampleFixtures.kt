package io.openhoyi.mobile

import org.json.JSONArray
import org.json.JSONObject

/** Immutable display-only inputs. Never opened by a repository or a device-command owner. */
internal object StrictVisualSampleFixtures {
    const val VERSION = "strict-visual-draft-2026-10-18"
    val ids = listOf("preparation-confirmed-four-stage", "share-four-stage-not-exported", "inventory-three-beans-selected")
    data class Bean(val name: String, val remainingGrams: Int)
    data class Fixture(val id: String, val title: String, val original: String,
        val targetsTenthsBar: List<Int> = emptyList(), val waterMl: List<Int> = emptyList()) {
        fun json(): JSONObject = JSONObject().put("id", id).put("version", VERSION)
            .put("specimenOnly", true).put("productAcceptance", false).put("title", title)
            .put("original", original).put("recipeName", "经典浓缩")
            .put("temperatureC", 93).put("targetWeightGrams", 36.0)
            .put("targetsTenthsBar", JSONArray(targetsTenthsBar)).put("waterMl", JSONArray(waterMl))
            .put("beans", JSONArray(beans.map { JSONObject().put("name", it.name).put("remainingGrams", it.remainingGrams) }))
            .put("selectedBean", "哥伦比亚 水洗").put("confirmedDoseGrams", 18.0)
            .put("machineDisplayState", "simulated_ready").put("scaleDisplayState", "simulated_live")
            .put("batch", 1).put("initialGrams", 100).put("remainingGrams", 90).put("percentage", 90)
            .put("purchaseDate", "2026-10-02").put("roastDate", "2026-09-28").put("openDate", "2026-10-03")
            .put("ledger", JSONArray(listOf(JSONObject().put("type", "调整").put("changeGrams", -10).put("reason", "盘点修正"),
                JSONObject().put("type", "入库").put("changeGrams", 100).put("reason", "—"))))
            .put("recentRecipes", JSONArray(listOf(
                JSONObject().put("name", "经典浓缩").put("temperatureC", 93).put("targetGrams", 36).put("stages", 4),
                JSONObject().put("name", "浅烘长萃").put("temperatureC", 91).put("targetGrams", 40).put("stages", 3),
                JSONObject().put("name", "柔和降压").put("temperatureC", 92).put("targetGrams", 36).put("stages", 3))))
            .put("exportUri", JSONObject.NULL).put("businessCallbacks", false)
    }
    val beans = listOf(Bean("哥伦比亚 水洗", 90), Bean("耶加雪菲 日晒", 160), Bean("巴西 拼配", 250))
    fun get(id: String, version: String): Fixture {
        require(version == VERSION) { "Unknown specimen version" }
        return when (id) {
            ids[0] -> Fixture(id, "冲煮准备", "brew-preparation-v2.png", listOf(20, 90, 80, 60))
            ids[1] -> Fixture(id, "分享曲线", "curve-share-v1.png", listOf(30, 90, 70, 50), listOf(15, 25, 15, 5))
            ids[2] -> Fixture(id, "咖啡豆", "bean-inventory-v3.png")
            else -> error("Unknown specimen fixture: $id")
        }
    }
}
