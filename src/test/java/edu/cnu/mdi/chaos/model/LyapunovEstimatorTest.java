package edu.cnu.mdi.chaos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LyapunovEstimatorTest {
    @Test
    void estimatesClassicLorenzExponentWithoutChangingTheOriginalPair() {
        var parameters = LorenzParameters.DEFAULT;
        var baseline = new ChaosModel(parameters, 16);
        var enabled = new ChaosModel(parameters, 16, true);
        assertNull(baseline.lyapunovEstimate());
        for (int step = 0; step < 60_000; step++) {
            assertEquals(baseline.advance(false), enabled.advance(false));
        }
        assertEquals(baseline.samplesAfter(-1), enabled.samplesAfter(-1));
        var estimate = enabled.lyapunovEstimate();
        assertFalse(estimate.failed());
        assertEquals(290, estimate.averagingTime());
        assertEquals(0.90563, estimate.exponent(), 0.06);
    }

    @Test
    void measuresOnlyCompleteIntervalsAfterWarmupAndClearsOnReset() {
        var model = new ChaosModel(LorenzParameters.DEFAULT, 8, true);
        advance(model, 2099);
        var warming = model.lyapunovEstimate();
        assertEquals(0.5, warming.interval());
        assertEquals(10, warming.warmupTime());
        assertEquals(0, warming.measuredIntervals());
        assertTrue(Double.isNaN(warming.exponent()));
        model.advance(true);
        var first = model.lyapunovEstimate();
        assertEquals(1, first.measuredIntervals());
        assertEquals(0.5, first.averagingTime());
        assertTrue(Double.isFinite(first.exponent()));
        model.advance(true);
        assertEquals(first, model.lyapunovEstimate());

        model.reset();
        assertEquals(0, model.current().step());
        assertEquals(warming, model.lyapunovEstimate());
        advance(model, 2100);
        assertEquals(first, model.lyapunovEstimate());
    }

    @Test
    void estimatesContractionEvenWhenTheDisplayedPerturbationIsZero() {
        var parameters = new LorenzParameters(10, 0.5, 8.0 / 3, 0.005, 0);
        var model = new ChaosModel(parameters, 8, true);
        advance(model, 20_000);
        assertEquals(0, model.current().separation());
        assertTrue(Double.isNaN(model.current().logDivergence()));
        double slowEigenvalueAtOrigin = (Math.sqrt(101) - 11) / 2;
        assertFalse(model.lyapunovEstimate().failed());
        assertEquals(slowEigenvalueAtOrigin, model.lyapunovEstimate().exponent(), 0.01);
    }

    private static void advance(ChaosModel model, int steps) {
        for (int i = 0; i < steps; i++) {
            model.advance(false);
        }
    }
}
