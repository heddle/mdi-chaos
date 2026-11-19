package edu.cnu.mdi.chaos.model;

public record LorenzParameters(double sigma, double rho, double beta, double dt, double delta0) {
    public static final LorenzParameters DEFAULT = new LorenzParameters(10, 28, 8.0 / 3, 0.005, 1e-8);

    public LorenzParameters {
        requireRange("σ", sigma, 0.01, 100);
        requireRange("ρ", rho, 0, 100);
        requireRange("β", beta, 0.01, 100);
        requireRange("dt", dt, 0.0001, 0.01);
        if (delta0 != 0) {
            requireRange("δ₀", delta0, 1e-15, 0.1);
        }
    }

    private static void requireRange(String name, double value, double min, double max) {
        if (!(value >= min && value <= max)) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
    }
}
