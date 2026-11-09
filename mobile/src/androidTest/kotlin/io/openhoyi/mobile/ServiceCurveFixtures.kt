package io.openhoyi.mobile

/** Use executable factory profiles while keeping flow/weight dispatch coverage explicit. */
internal object ServiceCurveFixtures {
    fun profile(app: MobileApplication, weighted: Boolean = true, excluding: String? = null): CurveProfile =
        app.curves.items.asSequence().filter { it.factoryCurve != null && it.id != excluding }
            .mapNotNull { app.curves.resolve(it.id, true, 7) }
            .first { (it.targetHundredthsGram > 0) == weighted }
}
