package edu.cnu.mdi.chaos.model;

public record LorenzState(double x, double y, double z) {
    public LorenzState {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new ArithmeticException("Lorenz integration produced a nonfinite value. Check parameters and reduce dt.");
        }
    }

    LorenzState plusScaled(LorenzState other, double scale) {
        return new LorenzState(x + scale * other.x, y + scale * other.y, z + scale * other.z);
    }

    public double distanceTo(LorenzState other) {
        return Math.hypot(Math.hypot(x - other.x, y - other.y), z - other.z);
    }
}
