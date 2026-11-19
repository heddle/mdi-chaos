package edu.cnu.mdi.chaos.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.function.BiConsumer;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.sim.SimulationState;

public final class ChaosControlPanel extends JPanel {
    private final JButton run = new JButton("Start");
    private final JButton pause = new JButton("Pause");
    private final JButton step = new JButton("Single step");
    private final JButton open3D = new JButton("3D view");
    private final JLabel status = new JLabel("Initializing…");
    private final JTextField sigma = new JTextField(5);
    private final JTextField rho = new JTextField(5);
    private final JTextField beta = new JTextField(16);
    private final JTextField dt = new JTextField(6);
    private final JTextField delta = new JTextField(7);

    public ChaosControlPanel(Runnable onRun, Runnable onPause, Runnable onStep, BiConsumer<LorenzParameters, Boolean> onReset,
                             Runnable onOpen3D) {
        super(new GridLayout(0, 1));
        setBorder(BorderFactory.createEmptyBorder(3, 8, 5, 8));

        var actions = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        var reset = new JButton("Reset");
        actions.add(run);
        actions.add(pause);
        actions.add(step);
        actions.add(reset);
        var lyapunov = new JCheckBox("Estimate Lyapunov exponent");
        lyapunov.setToolTipText("Use periodic renormalization on the next reset");
        actions.add(lyapunov);
        open3D.addActionListener(event -> onOpen3D.run());
        actions.add(open3D);
        var actionRow = new JPanel(new BorderLayout(10, 0));
        actionRow.add(actions, BorderLayout.WEST);
        actionRow.add(status, BorderLayout.CENTER);
        add(actionRow);

        var fields = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        var presets = new JComboBox<>(Preset.values());
        presets.getAccessibleContext().setAccessibleName("Parameter presets");
        presets.setToolTipText("Load parameter values into the fields");
        fields.add(presets);
        addField(fields, "σ", sigma);
        addField(fields, "ρ", rho);
        addField(fields, "β", beta);
        addField(fields, "dt", dt);
        addField(fields, "δ₀", delta);
        fields.add(new JLabel("Reset applies edits"));
        add(fields);

        fill(LorenzParameters.DEFAULT);
        presets.addActionListener(event -> fill(presets.getItemAt(presets.getSelectedIndex()).parameters));
        run.addActionListener(event -> onRun.run());
        pause.addActionListener(event -> onPause.run());
        step.addActionListener(event -> onStep.run());
        reset.addActionListener(event -> {
            try {
                onReset.accept(new LorenzParameters(value(sigma), value(rho), value(beta), value(dt), value(delta)),
                        lyapunov.isSelected());
            } catch (NumberFormatException error) {
                status.setText("Enter a number in each parameter field.");
            } catch (IllegalArgumentException error) {
                status.setText(error.getMessage());
            }
        });
        pending("Initializing…");
    }

    public void showState(SimulationState state, boolean stepping) {
        boolean idle = state == SimulationState.READY || state == SimulationState.PAUSED;
        run.setText(state == SimulationState.PAUSED ? "Resume" : "Start");
        run.setEnabled(idle);
        step.setEnabled(idle);
        pause.setEnabled(state == SimulationState.RUNNING && !stepping);
        status.setText(switch (state) {
            case NEW, INITIALIZING -> "Initializing…";
            case READY -> "Ready";
            case RUNNING -> stepping ? "Stepping…" : "Running";
            case PAUSED -> "Paused";
            case TERMINATING -> "Stopping…";
            case TERMINATED -> "Stopped";
            case FAILED -> "Simulation stopped. Reset to start again.";
        });
    }

    public void setOpening3D(boolean opening) {
        open3D.setText(opening ? "Opening 3D…" : "3D view");
        open3D.setEnabled(!opening);
    }

    public void pending(String message) {
        run.setEnabled(false);
        pause.setEnabled(false);
        step.setEnabled(false);
        status.setText(message);
    }

    private void fill(LorenzParameters parameters) {
        sigma.setText(Double.toString(parameters.sigma()));
        rho.setText(Double.toString(parameters.rho()));
        beta.setText(Double.toString(parameters.beta()));
        dt.setText(Double.toString(parameters.dt()));
        delta.setText(Double.toString(parameters.delta0()));
    }

    private static void addField(JPanel panel, String name, JTextField field) {
        var label = new JLabel(name);
        label.setLabelFor(field);
        panel.add(label);
        panel.add(field);
    }

    private static double value(JTextField field) {
        return Double.parseDouble(field.getText().trim());
    }

    private enum Preset {
        CLASSIC("Classic chaotic", LorenzParameters.DEFAULT),
        FIXED_POINT("Fixed point", new LorenzParameters(10, 0.5, 8.0 / 3, 0.005, 1e-8)),
        STABLE_LOBE("Stable lobe", new LorenzParameters(10, 14, 8.0 / 3, 0.005, 1e-8));

        private final String label;
        private final LorenzParameters parameters;

        Preset(String label, LorenzParameters parameters) {
            this.label = label;
            this.parameters = parameters;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
