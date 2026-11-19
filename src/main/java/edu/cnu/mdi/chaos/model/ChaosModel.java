package edu.cnu.mdi.chaos.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Mutable numerical state and a bounded, decimated ring of immutable samples. */
public final class ChaosModel {
    private final LorenzParameters parameters;
    private final ChaosSnapshot[] history;
    private final int sampleStride;
    private final boolean estimateLyapunov;
    private LyapunovEstimator lyapunov;
    private ChaosSnapshot current;
    private int next;
    private int size;

    public ChaosModel(LorenzParameters parameters, int capacity) {
        this(parameters, capacity, false);
    }

    public ChaosModel(LorenzParameters parameters, int capacity, boolean estimateLyapunov) {
        if (capacity < 1) {
            throw new IllegalArgumentException("History capacity must be positive");
        }
        this.parameters = parameters;
        this.estimateLyapunov = estimateLyapunov;
        history = new ChaosSnapshot[capacity];
        sampleStride = (int) Math.ceil(0.02 / parameters.dt());
        reset();
    }

    public void reset() {
        Arrays.fill(history, null);
        next = 0;
        size = 0;
        current = ChaosSnapshot.of(0, parameters, new LorenzState(1, 1, 1),
                new LorenzState(1 + parameters.delta0(), 1, 1));
        lyapunov = estimateLyapunov ? new LyapunovEstimator(parameters, current.primary()) : null;
        record();
    }

    public ChaosSnapshot advance(boolean singleStep) {
        LorenzState primary = LorenzIntegrator.step(current.primary(), parameters);
        LorenzState perturbed = LorenzIntegrator.step(current.perturbed(), parameters);
        current = ChaosSnapshot.of(current.step() + 1, parameters, primary, perturbed);
        if (lyapunov != null) {
            lyapunov.advance(primary, current.step());
        }
        if (singleStep || current.step() % sampleStride == 0) {
            record();
        }
        return current;
    }

    public ChaosSnapshot current() {
        return current;
    }

    /** Returns null when periodic renormalization is disabled. */
    public LyapunovEstimate lyapunovEstimate() {
        return lyapunov == null ? null : lyapunov.estimate();
    }

    /** Returns a detached, chronological list of retained samples newer than the given step. */
    public List<ChaosSnapshot> samplesAfter(long step) {
        var samples = new ArrayList<ChaosSnapshot>();
        for (int offset = 1; offset <= size; offset++) {
            ChaosSnapshot sample = history[Math.floorMod(next - offset, history.length)];
            if (sample.step() <= step) {
                break;
            }
            samples.add(sample);
        }
        Collections.reverse(samples);
        return List.copyOf(samples);
    }

    private void record() {
        history[next] = current;
        next = (next + 1) % history.length;
        size = Math.min(size + 1, history.length);
    }
}
