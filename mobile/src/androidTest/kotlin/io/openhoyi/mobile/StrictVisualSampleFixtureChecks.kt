package io.openhoyi.mobile

/** Fail closed on fixture/version drift, unknown ids, synthetic timestamps, or lost stage data. */
internal object StrictVisualSampleFixtureChecks {
    fun run() {
        val version = StrictVisualSampleFixtures.VERSION
        val preparation = StrictVisualSampleFixtures.get("preparation-confirmed-four-stage", version)
        check(preparation.targetsTenthsBar == listOf(20, 90, 80, 60))
        val share = StrictVisualSampleFixtures.get("share-four-stage-not-exported", version)
        check(share.targetsTenthsBar == listOf(30, 90, 70, 50) && share.waterMl == listOf(15, 25, 15, 5))
        val inventory = StrictVisualSampleFixtures.get("inventory-three-beans-selected", version).json()
        check(StrictVisualSampleFixtures.beans.sumOf { it.remainingGrams } == 500)
        check(inventory.getJSONArray("ledger").length() == 2)
        check(!inventory.getJSONArray("ledger").getJSONObject(0).has("timestamp"))
        check(inventory.isNull("exportUri") && !inventory.getBoolean("businessCallbacks"))
        check(inventory.getBoolean("specimenOnly") && !inventory.getBoolean("productAcceptance"))
        check(runCatching { StrictVisualSampleFixtures.get("unknown", version) }.isFailure)
        check(runCatching { StrictVisualSampleFixtures.get(preparation.id, "unknown") }.isFailure)
        check(org.json.JSONObject(inventory.toString()).getString("openDate") == "2026-10-03")
    }
}
