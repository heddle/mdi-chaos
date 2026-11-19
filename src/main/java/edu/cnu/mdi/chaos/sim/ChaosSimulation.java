package edu.cnu.mdi.chaos.sim;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import edu.cnu.mdi.chaos.model.ChaosModel;
import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.sim.Simulation;
import edu.cnu.mdi.sim.SimulationContext;
import edu.cnu.mdi.sim.SimulationEngine;
import edu.cnu.mdi.sim.SimulationEngineConfig;
import edu.cnu.mdi.sim.SimulationListener;

public final class ChaosSimulation implements Simulation {
    public static final int HISTORY_CAPACITY = 6000;
    private static final int BATCH_PERIOD_MS = 10;

    private final LorenzParameters parameters;
    private final SimulationEngine engine;
    private final AtomicBoolean singleStep = new AtomicBoolean();
    private final int stepsPerBatch;
    private ChaosModel model;

    public ChaosSimulation(LorenzParameters parameters, SimulationListener listener) {
        this.parameters = parameters;
        stepsPerBatch = Math.max(1, (int) Math.round(0.04 / parameters.dt()));
        engine = new SimulationEngine(this, new SimulationEngineConfig(40, 0, 0, false));
        engine.addListener(listener);
    }

    public SimulationEngine engine() {
        return engine;
    }

    /** Called on the EDT only when the engine is READY or PAUSED. */
    public void requestSingleStep() {
        singleStep.set(true);
        engine.requestRun();
    }

    @Override
    public synchronized void init(SimulationContext context) {
        model = new ChaosModel(parameters, HISTORY_CAPACITY);
    }

    @Override
    public boolean step(SimulationContext context) throws InterruptedException {
        boolean oneStep = singleStep.getAndSet(false);
        if (!oneStep) {
            Thread.sleep(BATCH_PERIOD_MS);
        }
        synchronized (this) {
            int count = oneStep ? 1 : stepsPerBatch;
            for (int i = 0; i < count && !context.isCancelRequested(); i++) {
                model.advance(oneStep);
            }
        }
        if (oneStep) {
            engine.requestPause();
        }
        return true;
    }

    /** Reads the exact current state and only the retained samples the EDT has yet to consume. */
    public synchronized Frame readSince(long step) {
        return new Frame(model.current(), model.samplesAfter(step));
    }

    public record Frame(ChaosSnapshot current, List<ChaosSnapshot> samples) {
        public Frame {
            samples = List.copyOf(samples);
        }
    }
}
