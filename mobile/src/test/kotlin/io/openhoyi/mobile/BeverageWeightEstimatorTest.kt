package io.openhoyi.mobile

import org.junit.Assert.*
import org.junit.Test

class BeverageWeightEstimatorTest {
    @Test fun firstFreshFallbackIsBaselineNotCupMass() {
        val estimator=BeverageWeightEstimator()
        assertNull(estimator.observe(1000,20000,1,null))
        assertNull(estimator.observe(2000,null,1,null))
        assertEquals(BeverageWeightSample(0,BeverageWeightSource.ESTIMATED),estimator.observe(2500,20000,1,null))
        assertEquals(350,estimator.observe(3000,20350,1,null)?.hundredthsGram)
    }
    @Test fun confirmedBaselineHasPriorityAndNeverDependsOnTwoSecondDelay() {
        val estimator=BeverageWeightEstimator()
        assertEquals(BeverageWeightSample(50,BeverageWeightSource.CONFIRMED),estimator.observe(100,65,1,50))
        assertNull(estimator.observe(200,null,1,50))
        assertNull(estimator.observe(500,100,1,null))
    }
    @Test fun lateTareAndReconnectBreakCoordinatesInsteadOfConnectingCupMass() {
        val estimator=BeverageWeightEstimator()
        estimator.observe(2000,20000,1,null)
        assertEquals(500,estimator.observe(2500,20500,1,null)?.hundredthsGram)
        assertNull(estimator.observe(3000,10,1,null))
        assertEquals(100,estimator.observe(3500,110,1,null)?.hundredthsGram)
        assertNull(estimator.observe(4000,null,2,null))
        assertEquals(0,estimator.observe(4500,30000,2,null)?.hundredthsGram)
        assertEquals(200,estimator.observe(5000,30200,2,null)?.hundredthsGram)
    }
    @Test fun missingFreshSampleNeverBecomesZeroAndNegativeDeltaClamps() {
        val estimator=BeverageWeightEstimator()
        assertNull(estimator.observe(2000,null,1,null))
        estimator.observe(2500,20,1,null)
        assertEquals(0,estimator.observe(3000,-20,1,null)?.hundredthsGram)
        assertNull(estimator.observe(3500,null,1,null))
    }
    @Test fun onlyConfirmedReadoutProducesTrustedSample() {
        assertNull(ExtractionScaleReadout.Value(500).confirmedBeverageSample())
        assertNull(ExtractionScaleReadout.Value(500,ended=true,source=ExtractionScaleReadout.Source.BEVERAGE).confirmedBeverageSample())
        assertEquals(500,ExtractionScaleReadout.Value(500,source=ExtractionScaleReadout.Source.BEVERAGE).confirmedBeverageSample())
    }
}
