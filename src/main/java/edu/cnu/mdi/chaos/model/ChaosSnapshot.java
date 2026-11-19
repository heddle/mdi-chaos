package edu.cnu.mdi.chaos.model;

public record ChaosSnapshot(long step, double time, LorenzState primary, LorenzState perturbed,
                            double separation, double logDivergence) {
    static ChaosSnapshot of(long step, LorenzParameters parameters, LorenzState primary, LorenzState perturbed) {
        double separation = primary.distanceTo(perturbed);
        return new ChaosSnapshot(step, step * parameters.dt(), primary, perturbed,
                separation, logRatio(separation, parameters.delta0()));
    }

    /** Returns NaN when a finite logarithmic ratio is undefined. */
    public static double logRatio(double separation, double initialSeparation) {
        if (!(separation > 0 && Double.isFinite(separation)
                && initialSeparation > 0 && Double.isFinite(initialSeparation))) {
            return Double.NaN;
        }
        return Math.log(separation) - Math.log(initialSeparation);
    }
}
