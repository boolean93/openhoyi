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
            recommendationWidths(test)
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
    /** Detached measurement only: no installation, screenshots or device-fit claim from compilation. */
    private fun recommendationWidths(test: Instrumentation) {
        data class Case(val hostDp: Int, val language: AppLanguage, val font: AppFontSize)
        for (case in listOf(Case(280, AppLanguage.ENGLISH, AppFontSize.LARGE),
            Case(368, AppLanguage.CHINESE, AppFontSize.MEDIUM),
            Case(728, AppLanguage.ENGLISH, AppFontSize.MEDIUM))) {
            val context = AppLanguageContext.wrap(test.targetContext, case.language, fontSize = case.font)
            val visual = SettingsVisualStyle(context)
            val style = visual.palette
            val presentation = AiChatPresentation(context, visual, "", 0, 0, {}, {}, {}, {},
                onOpenCurve = {}, onReviewCurve = {}, onViewCurve = {})
            // Reuse the fixed recommendation/proposal fixture, never access stores or a live engine.
            val fixtures = cards(longEnglishName = case.font == AppFontSize.LARGE).take(2)
            var previousRecommendationWidth: Int? = null
            for (text in listOf("OK", "A longer explanation of why this recipe fits. ".repeat(8))) {
                presentation.render(listOf(AiChatLine(false, text, curveCards = fixtures)), "", false, "")
                val transcript = presentation.transcript
                // Transcript owns its padding; requested case width is the net card-host space.
                val width = style.dp(case.hostDp) + transcript.paddingLeft + transcript.paddingRight
                transcript.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                transcript.layout(0, 0, width, transcript.measuredHeight)
                val views = descendants(transcript).filterIsInstance<AiCurveCardView>()
                check(views.size == 2)
                val recommendation = views[0]
                val host = recommendation.parent as ViewGroup
                check(host.width == style.dp(case.hostDp))
                check(recommendation.width == minOf(host.width, style.dp(400)))
                check(recommendation.left == host.paddingLeft)
                previousRecommendationWidth?.let { check(recommendation.width == it) }
                previousRecommendationWidth = recommendation.width
                val proposal = views[1]
                check(proposal.width == AiCurveCardLayoutPolicy.cardWidth((proposal.parent as ViewGroup).width, style.dp(520)))
                for (card in views) {
                    for (label in descendants(card).filterIsInstance<TextView>().filter { it.visibility == View.VISIBLE }) {
                        val layout = requireNotNull(label.layout)
                        check((0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
                        check(layout.height <= label.height - label.compoundPaddingTop - label.compoundPaddingBottom)
                        if (label is Button) check(label.height >= style.dp(48))
                    }
                }
            }
        }
    }
    fun cards(longEnglishName: Boolean = false): List<AiCurveCard> {
        val baseline = CustomCurveDocument("draft-00000000-0000-0000-0000-000000000142", "合成配方", 92, 3600,
            CustomCurveDocument.ControlMode.PRESSURE,
            listOf(CustomCurveDocument.Stage(25, 180), CustomCurveDocument.Stage(90, 400), CustomCurveDocument.Stage(60, 140)),
            72, CustomCurveDocument.Configuration(preinfusionSeconds = 5, firstDurationSeconds = 6,
                inactiveStages = listOf(CustomCurveDocument.Stage(0, 0))))
        // One narrow/large-font stress fixture, not another language/size matrix.
        val before = if (longEnglishName) baseline.copy(
            name = "Synthetic extended medium roast recipe with a gentle finishing stage",
            stages = baseline.stages + CustomCurveDocument.Stage(45, 120),
            configuration = requireNotNull(baseline.configuration).copy(inactiveStages = emptyList())) else baseline
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
