package edu.cnu.mdi.chaos.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DivergenceFitTest {
    @Test
    void estimatesOnlyFinitePointsInsideInclusiveBounds() {
        double[] times = {0, 1, 2, 3, 4, 5, 6};
        double[] divergence = {100, 5, Double.NaN, 9, 11, Double.POSITIVE_INFINITY, -100};
        var fit = DivergenceFit.estimate(times, divergence, 1, 5);
        assertEquals(3, fit.sampleCount());
        assertEquals(1, fit.firstTime());
        assertEquals(4, fit.lastTime());
        assertEquals(2, fit.slope(), 1e-12);
        assertEquals(5, fit.valueAt(1), 1e-12);
        assertEquals(11, fit.valueAt(4), 1e-12);
    }

    @Test
    void rejectsInvalidIntervalsAndInsufficientData() {
        double[] times = {1, 2, 3};
        double[] divergence = {3, 5, 7};
        assertThrows(IllegalArgumentException.class, () -> DivergenceFit.estimate(times, divergence, 3, 1));
        assertThrows(IllegalArgumentException.class, () -> DivergenceFit.estimate(times, divergence, 1, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> DivergenceFit.estimate(times, divergence, 1, 2));
        assertThrows(IllegalArgumentException.class, () ->
                DivergenceFit.estimate(new double[] {2, 2, 2}, divergence, 1, 3));
    }
}
