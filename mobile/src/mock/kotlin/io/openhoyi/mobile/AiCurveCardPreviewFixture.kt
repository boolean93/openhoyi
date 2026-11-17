package io.openhoyi.mobile

/** Fixed synthetic data only. No store, credentials, controller, network or executable approval. */
internal object AiCurveCardPreviewFixture {
    fun cards(): List<AiCurveCard> {
        val before = CustomCurveDocument(
            "draft-00000000-0000-0000-0000-000000000142", "合成 · 中烘均衡", 92, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(25, 180), CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 140)),
            72, CustomCurveDocument.Configuration(preinfusionSeconds = 5, firstDurationSeconds = 6,
                inactiveStages = listOf(CustomCurveDocument.Stage(0, 0))))
        val after = before.copy(name = "合成 · 花果配方", targetHundredthsGram = 4000,
            stages = listOf(before.stages[0], before.stages[1].copy(waterTenthsMl = 380), before.stages[2].copy(waterTenthsMl = 160)))
        val recommendation = AiCurveCard.recommendation(CurveLibraryItem(before.id, before.name, "", "", null, customDocument = before))
        // This inert proposal is not registered with any coordinator. It can never authorize a write.
        val proposal = AiCurveWriteCoordinator.Proposal("offline-fixture-not-a-permit",
            AiCurveWriteCoordinator.Epoch("offline-card-fixture", 0), "replace", before.encode(), after.encode(), 0)
        return listOf(recommendation, AiCurveCard.proposal(proposal))
    }
    fun lines(index: Int): List<AiChatLine> = listOf(AiChatLine(false,
        "离线合成卡片 / Synthetic offline cards", curveCards = listOf(cards()[index])))
}
