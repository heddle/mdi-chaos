package edu.cnu.mdi.chaos.model;

/** Worker-owned estimate based on periodic rescaling of a nearby trajectory. */
final class LyapunovEstimator {
    private static final double PERTURBATION = 1e-7;
    private static final double TARGET_INTERVAL = 0.5;
    private static final double WARMUP_TIME = 10;

    private final LorenzParameters parameters;
    private final long stepsPerInterval;
    private final long warmupIntervals;
    private final double interval;
    private LorenzState nearby;
    private double initialDistance;
    private double logGrowthSum;
    private long completedIntervals;
    private long measuredIntervals;
    private boolean failed;

    LyapunovEstimator(LorenzParameters parameters, LorenzState initial) {
        this.parameters = parameters;
        stepsPerInterval = Math.round(TARGET_INTERVAL / parameters.dt());
        interval = stepsPerInterval * parameters.dt();
        warmupIntervals = (long) Math.ceil(WARMUP_TIME / interval);
        nearby = new LorenzState(initial.x() + PERTURBATION, initial.y(), initial.z());
        initialDistance = initial.distanceTo(nearby);
    }

    void advance(LorenzState reference, long step) {
        if (failed) {
            return;
        }
        try {
            nearby = LorenzIntegrator.step(nearby, parameters);
            if (step % stepsPerInterval == 0) {
                renormalize(reference);
            }
        } catch (ArithmeticException error) {
            // Numerical failure in the estimator must not interrupt the original pair.
            failed = true;
        }
    }

    private void renormalize(LorenzState reference) {
        double distance = reference.distanceTo(nearby);
        double growth = ChaosSnapshot.logRatio(distance, initialDistance);
        if (!Double.isFinite(growth)) {
            failed = true;
            return;
        }

        double scale = PERTURBATION / distance;
        nearby = new LorenzState(reference.x() + scale * (nearby.x() - reference.x()),
                reference.y() + scale * (nearby.y() - reference.y()),
                reference.z() + scale * (nearby.z() - reference.z()));
        initialDistance = reference.distanceTo(nearby);
        if (!(initialDistance > 0 && Double.isFinite(initialDistance))) {
            failed = true;
            return;
        }

        completedIntervals++;
        if (completedIntervals > warmupIntervals) {
            logGrowthSum += growth;
            measuredIntervals++;
        }
    }

    LyapunovEstimate estimate() {
        double duration = measuredIntervals * interval;
        double exponent = measuredIntervals == 0 ? Double.NaN : logGrowthSum / duration;
        return new LyapunovEstimate(exponent, duration, interval, warmupIntervals * interval,
                measuredIntervals, failed);
    }
}
