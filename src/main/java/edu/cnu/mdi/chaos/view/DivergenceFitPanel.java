package edu.cnu.mdi.chaos.view;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import edu.cnu.mdi.chaos.model.DivergenceFit;
import edu.cnu.mdi.splot.pdata.Curve;

final class DivergenceFitPanel extends JPanel {
    private static final String INSTRUCTIONS = "Fit a rising interval before separation saturates.";
    private final JTextField start = new JTextField(5);
    private final JTextField end = new JTextField(5);
    private final JButton fit = new JButton("Fit");
    private final JButton clear = new JButton("Clear fit");
    private final JLabel result = new JLabel(INSTRUCTIONS);
    private final Curve data;
    private final Curve line;
    private DivergenceFit estimate;

    DivergenceFitPanel(Curve data, Curve line) {
        super(new BorderLayout());
        this.data = data;
        this.line = line;
        var controls = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        addField(controls, "Fit start", start);
        addField(controls, "Fit end", end);
        controls.add(fit);
        controls.add(clear);
        add(controls, BorderLayout.NORTH);
        result.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
        add(result, BorderLayout.SOUTH);
        clear.setEnabled(false);
        fit.addActionListener(event -> fit());
        clear.addActionListener(event -> clearResult(INSTRUCTIONS));
        start.addActionListener(event -> fit.doClick());
        end.addActionListener(event -> fit.doClick());
    }

    private static void addField(JPanel panel, String text, JTextField field) {
        var label = new JLabel(text);
        label.setLabelFor(field);
        panel.add(label);
        panel.add(field);
    }

    private void fit() {
        clearResult(INSTRUCTIONS);
        try {
            double startTime = Double.parseDouble(start.getText().trim());
            double endTime = Double.parseDouble(end.getText().trim());
            var samples = data.snapshot();
            if (samples.length() == 0) {
                clearResult("Run the simulation to record divergence samples.");
                return;
            }
            if (startTime < samples.x[0] || endTime > samples.x[samples.length() - 1]) {
                clearResult(String.format(Locale.ROOT, "Choose times within the recorded range [%s, %s].",
                        formatTime(samples.x[0]), formatTime(samples.x[samples.length() - 1])));
                return;
            }
            estimate = DivergenceFit.estimate(samples.x, samples.y, startTime, endTime);
            double first = estimate.firstTime();
            double last = estimate.lastTime();
            line.addAll(new double[] {first, last},
                    new double[] {estimate.valueAt(first), estimate.valueAt(last)});
            clear.setEnabled(true);
            result.setText(String.format(Locale.ROOT, "<html>Fit interval [%s, %s] (%d samples)<br>"
                            + "Finite-time slope estimate: %.5g per time unit</html>",
                    formatTime(startTime), formatTime(endTime), estimate.sampleCount(), estimate.slope()));
        } catch (NumberFormatException error) {
            clearResult("Enter a numeric start and end time.");
        } catch (IllegalArgumentException error) {
            clearResult(error.getMessage());
        }
    }

    private static String formatTime(double time) {
        return java.math.BigDecimal.valueOf(time).setScale(8, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }

    void clearIfTrimmed(double oldestTime) {
        if (estimate != null && estimate.firstTime() < oldestTime) {
            clearResult("Fit cleared because its samples aged out of the plot.");
        }
    }

    void reset(boolean zeroPerturbation) {
        start.setEnabled(!zeroPerturbation);
        end.setEnabled(!zeroPerturbation);
        fit.setEnabled(!zeroPerturbation);
        clearResult(zeroPerturbation ? "Log divergence is undefined when δ₀ is zero." : INSTRUCTIONS);
    }

    private void clearResult(String message) {
        if (estimate != null) {
            line.clearData();
        }
        estimate = null;
        clear.setEnabled(false);
        result.setText(message);
    }
}
