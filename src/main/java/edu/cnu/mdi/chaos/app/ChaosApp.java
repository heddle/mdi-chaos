package edu.cnu.mdi.chaos.app;

import java.awt.BorderLayout;
import java.beans.PropertyVetoException;
import java.util.concurrent.ExecutionException;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;

import com.jogamp.opengl.GLProfile;

import edu.cnu.mdi.app.BaseMDIApplication;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.chaos.sim.ChaosSimulation;
import edu.cnu.mdi.chaos.view.ChaosControlPanel;
import edu.cnu.mdi.chaos.view.ChaosPlotView;
import edu.cnu.mdi.chaos.view.Lorenz3DView;
import edu.cnu.mdi.chaos.view.LorenzView;
import edu.cnu.mdi.desktop.Desktop;
import edu.cnu.mdi.sim.SimulationContext;
import edu.cnu.mdi.sim.SimulationListener;
import edu.cnu.mdi.sim.SimulationState;
import edu.cnu.mdi.util.PropertyUtils;

public final class ChaosApp extends BaseMDIApplication implements SimulationListener {
    private LorenzView trajectories;
    private Lorenz3DView trajectories3D;
    private LorenzParameters activeParameters;
    private ChaosPlotView timeSeries;
    private ChaosPlotView diagnostics;
    private ChaosControlPanel controls;
    private ChaosSimulation simulation;
    private long lastSample;
    private boolean stepping;

    private ChaosApp() {
        super(PropertyUtils.TITLE, "MDI Chaos Lab", PropertyUtils.FRACTION, 0.94);
        controls = new ChaosControlPanel(this::runSimulation, this::pauseSimulation, this::singleStep,
                this::reset, this::open3D);
        add(controls, BorderLayout.SOUTH);
        reset(LorenzParameters.DEFAULT, false);
    }

    public static void main(String[] args) {
        BaseMDIApplication.launch(ChaosApp::new);
    }

    @Override
    protected void addInitialViews() {
        trajectories = new LorenzView();
        timeSeries = ChaosPlotView.timeSeries();
        diagnostics = ChaosPlotView.diagnostics();
    }

    @Override
    protected void onVirtualDesktopReady() {
        super.onVirtualDesktopReady();
        // MDI 1.2.4 calls defaultViewLayout itself only with a virtual desktop.
        defaultViewLayout();
        validate();
    }

    @Override
    protected void defaultViewLayout() {
        var desktop = Desktop.getInstance();
        int width = desktop.getWidth();
        int height = desktop.getHeight();
        int split = (int) (width * 0.55);
        int midpoint = height / 2;
        if (!hasSavedLayout(trajectories)) {
            trajectories.setBounds(5, 5, split - 10, height - 10);
        }
        if (!hasSavedLayout(timeSeries)) {
            timeSeries.setBounds(split, 5, width - split - 5, midpoint - 7);
        }
        if (!hasSavedLayout(diagnostics)) {
            diagnostics.setBounds(split, midpoint + 2, width - split - 5, height - midpoint - 7);
        }
    }

    private void open3D() {
        if (trajectories3D != null) {
            show3D();
            return;
        }
        controls.setOpening3D(true);
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                // On macOS JOGL needs the native main thread during its first profile discovery.
                GLProfile.initSingleton();
                return null;
            }

            @Override
            protected void done() {
                if (simulation == null) {
                    return;
                }
                controls.setOpening3D(false);
                try {
                    get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException error) {
                    JOptionPane.showMessageDialog(ChaosApp.this, "OpenGL initialization failed. "
                            + "The 2D views are still available.", "3D view unavailable", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                create3D();
                show3D();
            }
        }.execute();
    }

    private void create3D() {
        trajectories3D = new Lorenz3DView(activeParameters);
        var desktop = Desktop.getInstance();
        int width = Math.min(700, (int) (desktop.getWidth() * 0.50));
        int height = Math.min(590, (int) (desktop.getHeight() * 0.80));
        trajectories3D.setBounds(22, 22, width, height);
        if (lastSample >= 0) {
            trajectories3D.update(simulation.readSince(-1));
        }
    }

    private void show3D() {
        try {
            trajectories3D.setIcon(false);
        } catch (PropertyVetoException ignored) {
            return;
        }
        trajectories3D.setVisible(true);
        trajectories3D.moveToFront();
    }

    private void runSimulation() {
        controls.pending("Starting…");
        simulation.engine().requestRun();
    }

    private void pauseSimulation() {
        controls.pending("Pausing…");
        simulation.engine().requestPause();
    }

    private void singleStep() {
        stepping = true;
        controls.pending("Stepping…");
        simulation.requestSingleStep();
    }

    private void reset(LorenzParameters parameters, boolean estimateLyapunov) {
        if (simulation != null) {
            simulation.engine().removeListener(this);
            simulation.engine().requestCancel();
        }
        controls.pending("Resetting…");
        stepping = false;
        lastSample = -1;
        activeParameters = parameters;
        trajectories.clear(parameters);
        if (trajectories3D != null) {
            trajectories3D.clear(parameters);
        }
        timeSeries.clear();
        diagnostics.clear();
        diagnostics.resetDiagnostic(parameters.delta0() == 0);
        simulation = new ChaosSimulation(parameters, estimateLyapunov, this);
        simulation.engine().start();
    }

    @Override
    public void onStateChange(SimulationContext context, SimulationState from, SimulationState to, String reason) {
        if (!isCurrent(context)) {
            return;
        }
        if (to == SimulationState.READY || to == SimulationState.PAUSED) {
            stepping = false;
            onRefresh(context);
        }
        controls.showState(to, stepping);
    }

    @Override
    public void onRefresh(SimulationContext context) {
        if (!isCurrent(context)) {
            return;
        }
        var frame = simulation.readSince(lastSample);
        trajectories.update(frame);
        if (trajectories3D != null) {
            trajectories3D.update(frame);
        }
        timeSeries.append(frame.samples());
        diagnostics.append(frame.samples());
        diagnostics.showLyapunovEstimate(frame.lyapunovEstimate());
        if (!frame.samples().isEmpty()) {
            lastSample = frame.samples().get(frame.samples().size() - 1).step();
        }
    }

    @Override
    public void onFail(SimulationContext context, Throwable error) {
        if (isCurrent(context)) {
            controls.pending(error.getMessage() == null ? "Integration failed. Reset to try again." : error.getMessage());
        }
    }

    private boolean isCurrent(SimulationContext context) {
        return simulation != null && simulation.engine().getContext() == context;
    }

    @Override
    protected void prepareForShutdown() {
        simulation.engine().removeListener(this);
        simulation.engine().requestCancel();
        simulation = null;
        super.prepareForShutdown();
    }

    @Override
    protected boolean exitOnClose() {
        return true;
    }
}
