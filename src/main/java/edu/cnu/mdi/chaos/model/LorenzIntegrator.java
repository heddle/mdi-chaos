package edu.cnu.mdi.chaos.model;

public final class LorenzIntegrator {
    private LorenzIntegrator() {
    }

    public static LorenzState step(LorenzState state, LorenzParameters parameters) {
        double dt = parameters.dt();
        LorenzState k1 = derivative(state, parameters);
        LorenzState k2 = derivative(state.plusScaled(k1, dt / 2), parameters);
        LorenzState k3 = derivative(state.plusScaled(k2, dt / 2), parameters);
        LorenzState k4 = derivative(state.plusScaled(k3, dt), parameters);
        return new LorenzState(
                state.x() + dt / 6 * (k1.x() + 2 * k2.x() + 2 * k3.x() + k4.x()),
                state.y() + dt / 6 * (k1.y() + 2 * k2.y() + 2 * k3.y() + k4.y()),
                state.z() + dt / 6 * (k1.z() + 2 * k2.z() + 2 * k3.z() + k4.z()));
    }

    private static LorenzState derivative(LorenzState state, LorenzParameters parameters) {
        double x = state.x();
        double y = state.y();
        double z = state.z();
        return new LorenzState(parameters.sigma() * (y - x),
                x * (parameters.rho() - z) - y, x * y - parameters.beta() * z);
    }
}
