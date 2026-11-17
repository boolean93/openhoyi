package io.openhoyi.mobile

import android.app.Instrumentation
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import org.json.JSONObject

/** Only fixed Mock fixtures and detached Views; never starts an Activity or reads user stores. */
internal object AiCurveCardConstructionChecks {
    fun run(test: Instrumentation) {
        check(BuildConfig.MOCK_MODE && test.targetContext.packageName == "io.openhoyi.mobile.mock")
        test.runOnMainSync {
            for (language in listOf(AppLanguage.CHINESE, AppLanguage.ENGLISH)) {
                val context = AppLanguageContext.wrap(test.targetContext, language)
                val style = TaskProductVisualStyle(context)
                for (original in cards()) {
                    for (card in listOf(original, AiCurveCard.decode(JSONObject(original.encode().toString())))) {
                        var opens = 0
                        var reviews = 0
                        var browses = 0
                        val savedSource = card.kind != AiCurveCard.Kind.CREATE
                        val view = AiCurveCardView(context, card, style, language == AppLanguage.ENGLISH,
                            onOpen = { opens++ }, onReview = { reviews++ }, referenceAvailable = true,
                            onViewCurve = { browses++ }, libraryReferenceAvailable = savedSource)
                        val children = descendants(view)
                        check(children.filterIsInstance<TextView>().none { it.isTextSelectable })
                        check(children.none { it.tag == "ai/curve-card/${card.id}/details" })
                        val buttons = children.filterIsInstance<Button>()
                        check(buttons.size == 2)
                        val browse = buttons.single { it.tag == "ai/curve-card/${card.id}/view-curve" }
                        check(browse.isEnabled == savedSource)
                        if (savedSource) {
                            check(browse.performClick())
                            check(browses == 1 && opens == 0 && reviews == 0)
                        }
                        val primary = buttons.single { it.tag == "ai/curve-card/${card.id}/primary" }
                        check(primary.gravity == android.view.Gravity.CENTER)
                        check(primary.currentTextColor == android.graphics.Color.WHITE)
                        check(primary.performClick())
                        check(if (card.kind == AiCurveCard.Kind.RECOMMENDATION) opens == 1 && reviews == 0 else reviews == 1 && opens == 0)
                        check(browses == if (savedSource) 1 else 0)
                        check(buttons.all { it.minimumHeight >= style.dp(48) })
                        if (card.kind == AiCurveCard.Kind.RECOMMENDATION) {
                            val unavailable = AiCurveCardView(context, card, style, language == AppLanguage.ENGLISH,
                                onOpen = { error("Unavailable extraction must not run") }, referenceAvailable = false,
                                onViewCurve = { error("Unavailable library source must not run") }, libraryReferenceAvailable = false)
                            val unavailableButtons = descendants(unavailable).filterIsInstance<Button>()
                            check(unavailableButtons.size == 2 && unavailableButtons.none { it.isEnabled })
                            // Disabled-state assertions only: performClick bypasses touch dispatch.
                        }
                    }
                }
            }
        }
    }
    private fun cards(): List<AiCurveCard> {
        val before = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000142", "合成配方", 92, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(25, 180), CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 140)),
            72, CustomCurveDocument.Configuration(preinfusionSeconds = 5, firstDurationSeconds = 6,
                inactiveStages = listOf(CustomCurveDocument.Stage(0, 0))))
        val item = CurveLibraryItem(before.id, before.name, "", "", null, customDocument = before)
        val after = before.copy(name = "合成修改", targetHundredthsGram = 4000)
        return listOf(AiCurveCard.recommendation(item), AiCurveCard.proposal(AiCurveWriteCoordinator.Proposal(
            "test-not-a-permit", AiCurveWriteCoordinator.Epoch("detached-card-test", 0), "replace", before.encode(), after.encode(), 0)),
            AiCurveCard.proposal(AiCurveWriteCoordinator.Proposal(
                "test-not-a-permit", AiCurveWriteCoordinator.Epoch("detached-card-test", 0), "create", null, after.encode(), 0)))
    }
    private fun descendants(root: View): List<View> = listOf(root) +
        if (root is ViewGroup) (0 until root.childCount).flatMap { descendants(root.getChildAt(it)) } else emptyList()
}
