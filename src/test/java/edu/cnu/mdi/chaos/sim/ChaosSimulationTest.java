package edu.cnu.mdi.chaos.sim;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.sim.SimulationContext;
import edu.cnu.mdi.sim.SimulationListener;
import edu.cnu.mdi.sim.SimulationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChaosSimulationTest {
    @Test
    void engineSingleStepsFromReadyAndPausedAndSupportsContinuousRun() throws Exception {
        var states = new LinkedBlockingQueue<SimulationState>();
        var simulation = new ChaosSimulation(LorenzParameters.DEFAULT, new SimulationListener() {
            @Override
            public void onStateChange(SimulationContext context, SimulationState from,
                                      SimulationState to, String reason) {
                assertTrue(SwingUtilities.isEventDispatchThread());
                states.add(to);
            }
        });
        var engine = simulation.engine();
        engine.start();
        try {
            await(states, SimulationState.READY);
            assertEquals(0, simulation.readSince(-1).current().step());

            SwingUtilities.invokeAndWait(simulation::requestSingleStep);
            await(states, SimulationState.PAUSED);
            var first = simulation.readSince(0);
            assertEquals(1, first.current().step());
            assertEquals(1, first.samples().size());

            SwingUtilities.invokeAndWait(simulation::requestSingleStep);
            await(states, SimulationState.PAUSED);
            assertEquals(2, simulation.readSince(1).current().step());
            assertEquals(1, first.current().step());

            engine.requestResume();
            await(states, SimulationState.RUNNING);
            engine.requestPause();
            await(states, SimulationState.PAUSED);
            assertTrue(simulation.readSince(2).current().step() > 2);
        } finally {
            engine.requestCancel();
            assertTrue(engine.awaitTermination(3000));
        }
    }

    private static void await(LinkedBlockingQueue<SimulationState> states, SimulationState expected)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (states.poll(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS) == expected) {
                return;
            }
        }
        fail("Engine did not reach " + expected);
    }
}
