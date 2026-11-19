package edu.cnu.mdi.chaos.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChaosModelTest {
    private static final LorenzParameters DEFAULT = LorenzParameters.DEFAULT;

    @Test
    void rk4ConvergesAtFourthOrderOverShortInterval() {
        LorenzState reference = integrate(0.0001, 2000);
        double coarseError = integrate(0.01, 20).distanceTo(reference);
        double fineError = integrate(0.005, 40).distanceTo(reference);
        assertTrue(coarseError / fineError > 14 && coarseError / fineError < 18);
        assertTrue(fineError < 1e-5);
    }

    @Test
    void resetReplaysIdenticalTrajectoryAndHistory() {
        var model = new ChaosModel(DEFAULT, 100);
        for (int i = 0; i < 300; i++) {
            model.advance(false);
        }
        var expected = model.samplesAfter(-1);
        var finalState = model.current();
        model.reset();
        assertEquals(1, model.samplesAfter(-1).size());
        assertEquals(0, model.current().time());
        for (int i = 0; i < 300; i++) {
            model.advance(false);
        }
        assertEquals(expected, model.samplesAfter(-1));
        assertEquals(finalState, model.current());
    }

    @Test
    void zeroPerturbationRemainsZero() {
        var model = new ChaosModel(new LorenzParameters(10, 28, 8.0 / 3, 0.005, 0), 10);
        for (int i = 0; i < 2000; i++) {
            var sample = model.advance(false);
            assertEquals(sample.primary(), sample.perturbed());
            assertEquals(0, sample.separation());
            assertTrue(Double.isNaN(sample.logDivergence()));
        }
    }

    @Test
    void defaultPerturbationStartsAlongX() {
        var sample = new ChaosModel(DEFAULT, 10).current();
        assertEquals(new LorenzState(1, 1, 1), sample.primary());
        assertEquals(new LorenzState(1 + DEFAULT.delta0(), 1, 1), sample.perturbed());
        assertEquals(DEFAULT.delta0(), sample.separation(), 1e-16);
        assertEquals(0, sample.logDivergence(), 1e-8);
    }

    @Test
    void advancesFixedTimeAndRetainsOnlyNewestSamples() {
        var model = new ChaosModel(DEFAULT, 3);
        for (int i = 0; i < 13; i++) {
            model.advance(false);
        }
        assertEquals(13 * DEFAULT.dt(), model.current().time());
        var samples = model.samplesAfter(-1);
        assertEquals(List.of(4L, 8L, 12L), samples.stream().map(ChaosSnapshot::step).toList());
        assertEquals(List.of(12L), model.samplesAfter(8).stream().map(ChaosSnapshot::step).toList());
        assertEquals(14, model.advance(true).step());
        assertEquals(List.of(14L), model.samplesAfter(12).stream().map(ChaosSnapshot::step).toList());
        assertEquals(4, samples.get(0).step());
        assertThrows(UnsupportedOperationException.class, samples::clear);
    }

    @Test
    void calculatesDistanceAndGuardsLogarithm() {
        assertEquals(13, new LorenzState(0, 0, 0).distanceTo(new LorenzState(3, 4, 12)));
        assertEquals(Math.log(4), ChaosSnapshot.logRatio(2, 0.5), 1e-15);
        assertTrue(Double.isFinite(ChaosSnapshot.logRatio(Double.MAX_VALUE, Double.MIN_VALUE)));
        for (double invalid : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            assertTrue(Double.isNaN(ChaosSnapshot.logRatio(invalid, 1)));
            assertTrue(Double.isNaN(ChaosSnapshot.logRatio(1, invalid)));
        }
    }

    @Test
    void validatesParametersAndRejectsNumericalOverflow() {
        assertThrows(IllegalArgumentException.class, () -> new LorenzParameters(0, 28, 3, 0.005, 0));
        assertThrows(IllegalArgumentException.class, () -> new LorenzParameters(10, -1, 3, 0.005, 0));
        assertThrows(IllegalArgumentException.class, () -> new LorenzParameters(10, 28, Double.NaN, 0.005, 0));
        assertThrows(IllegalArgumentException.class, () -> new LorenzParameters(10, 28, 3, 0.1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LorenzParameters(10, 28, 3, 0.005, 1e-20));
        assertThrows(IllegalArgumentException.class, () -> new ChaosModel(DEFAULT, 0));
        assertThrows(ArithmeticException.class, () -> new LorenzState(Double.NaN, 0, 0));
        assertThrows(ArithmeticException.class, () ->
                LorenzIntegrator.step(new LorenzState(Double.MAX_VALUE, 1, 1), DEFAULT));
    }

    private static LorenzState integrate(double dt, int steps) {
        var parameters = new LorenzParameters(10, 28, 8.0 / 3, dt, 0);
        var state = new LorenzState(1, 1, 1);
        for (int i = 0; i < steps; i++) {
            state = LorenzIntegrator.step(state, parameters);
        }
        return state;
    }
}
