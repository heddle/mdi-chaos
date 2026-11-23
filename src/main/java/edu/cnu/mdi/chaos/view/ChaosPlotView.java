package edu.cnu.mdi.chaos.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;

import edu.cnu.mdi.chaos.io.DiagnosticsCsv.FitSelection;
import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.LyapunovEstimate;
import edu.cnu.mdi.chaos.sim.ChaosSimulation;
import edu.cnu.mdi.graphics.style.SymbolType;
import edu.cnu.mdi.splot.fit.CurveDrawingMethod;
import edu.cnu.mdi.splot.pdata.Curve;
import edu.cnu.mdi.splot.pdata.PlotData;
import edu.cnu.mdi.splot.pdata.PlotDataException;
import edu.cnu.mdi.splot.pdata.PlotDataType;
import edu.cnu.mdi.splot.plot.PlotCanvas;
import edu.cnu.mdi.splot.plot.PlotPanel;
import edu.cnu.mdi.util.PropertyUtils;
import edu.cnu.mdi.view.BaseView;

public final class ChaosPlotView extends BaseView {
    private static final int TRIM_SIZE = 500;
    private final PlotCanvas canvas;
    private final Curve[] curves;
    private final List<Series> series;
    private final DivergenceFitPanel fitPanel;
    private final JLabel lyapunov = new JLabel();

    public static ChaosPlotView timeSeries() {
        return new ChaosPlotView("Time Series", "Primary trajectory", "Coordinate", null,
                new Series("x", new Color(26, 106, 157), sample -> sample.primary().x()),
                new Series("y", new Color(178, 73, 33), sample -> sample.primary().y()),
                new Series("z", new Color(40, 121, 88), sample -> sample.primary().z()));
    }

    public static ChaosPlotView diagnostics(Runnable onExport) {
        return new ChaosPlotView("Chaos Diagnostics", "Illustrative finite-time divergence", "ln(δ / δ₀)", onExport,
                new Series("Divergence", new Color(121, 58, 147), ChaosSnapshot::logDivergence));
    }

    private ChaosPlotView(String title, String plotTitle, String yLabel, Runnable onExport, Series... series) {
        super(PropertyUtils.TITLE, title, PropertyUtils.WIDTH, 510, PropertyUtils.HEIGHT, 300,
                PropertyUtils.USECONTAINER, false, PropertyUtils.VISIBLE, true);
        boolean includeFit = onExport != null;
        this.series = List.of(series);
        curves = new Curve[series.length];
        try {
            String[] names = new String[series.length + (includeFit ? 1 : 0)];
            for (int i = 0; i < series.length; i++) {
                names[i] = series[i].name();
            }
            if (includeFit) {
                names[series.length] = "Selected interval fit";
            }
            var data = new PlotData(PlotDataType.XYEXYE, names, null);
            for (int i = 0; i < series.length; i++) {
                Curve curve = (Curve) data.getCurve(series[i].name());
                curve.setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
                curve.getStyle().setSymbolType(SymbolType.NOSYMBOL);
                curve.getStyle().setLineColor(series[i].color());
                curves[i] = curve;
            }
            if (includeFit) {
                Curve fitLine = (Curve) data.getCurve(names[series.length]);
                fitLine.setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
                fitLine.getStyle().setSymbolType(SymbolType.NOSYMBOL);
                fitLine.getStyle().setLineColor(new Color(185, 82, 24));
                fitLine.getStyle().setLineWidth(2f);
                fitPanel = new DivergenceFitPanel(curves[0], fitLine, onExport);
            } else {
                fitPanel = null;
            }
            canvas = new PlotCanvas(data, plotTitle, "Simulation time", yLabel);
        } catch (PlotDataException error) {
            throw new IllegalStateException("Could not initialize sPlot", error);
        }
        canvas.getParameters().setLegendDrawing(series.length > 1).setNumDecimalY(1);
        canvas.getPlotTicks().setNumMajorTickY(4);
        add(new PlotPanel(canvas, PlotPanel.BARE), BorderLayout.CENTER);
        if (fitPanel != null) {
            var diagnosticControls = new JPanel(new BorderLayout());
            lyapunov.setBorder(BorderFactory.createEmptyBorder(2, 8, 6, 8));
            lyapunov.setVisible(false);
            diagnosticControls.add(lyapunov, BorderLayout.NORTH);
            diagnosticControls.add(fitPanel, BorderLayout.SOUTH);
            add(diagnosticControls, BorderLayout.SOUTH);
        }
        canvas.standUp();
    }

    public FitSelection selectedFit() {
        return fitPanel.selectedFit();
    }

    public void setExportEnabled(boolean enabled) {
        fitPanel.setExportEnabled(enabled);
    }

    public void resetDiagnostic(boolean zeroPerturbation) {
        fitPanel.reset(zeroPerturbation);
        showLyapunovEstimate(null);
    }

    public void showLyapunovEstimate(LyapunovEstimate estimate) {
        lyapunov.setVisible(estimate != null);
        if (estimate == null) {
            return;
        }
        if (estimate.failed()) {
            lyapunov.setText("<html>Lyapunov estimate unavailable.<br>The nearby trajectory lost numerical resolution.</html>");
        } else if (estimate.measuredIntervals() == 0) {
            lyapunov.setText(String.format(Locale.ROOT,
                    "Lyapunov estimate warming up (first sample at t ≈ %.3f)",
                    estimate.warmupTime() + estimate.interval()));
        } else {
            lyapunov.setText(String.format(Locale.ROOT,
                    "<html>Largest Lyapunov exponent ≈ %.4f per time unit<br>Averaged over %.2f time units</html>",
                    estimate.exponent(), estimate.averagingTime()));
        }
    }

    public void append(List<ChaosSnapshot> samples) {
        if (samples.isEmpty()) {
            return;
        }
        for (int i = 0; i < curves.length; i++) {
            Curve curve = curves[i];
            double[] x = new double[samples.size()];
            double[] y = new double[samples.size()];
            int count = 0;
            for (ChaosSnapshot sample : samples) {
                double value = series.get(i).value().applyAsDouble(sample);
                if (Double.isFinite(value)) {
                    x[count] = sample.time();
                    y[count++] = value;
                }
            }
            if (count == 0) {
                continue;
            }
            int capacity = ChaosSimulation.HISTORY_CAPACITY;
            if (curve.length() + count > capacity) {
                var previous = curve.snapshot();
                int keep = Math.min(Math.max(0, previous.length() - TRIM_SIZE), capacity - count);
                curve.clearData();
                if (fitPanel != null) {
                    fitPanel.clearIfTrimmed(keep == 0 ? x[0] : previous.x[previous.length() - keep]);
                }
                if (keep > 0) {
                    curve.addAll(Arrays.copyOfRange(previous.x, previous.length() - keep, previous.length()),
                            Arrays.copyOfRange(previous.y, previous.length() - keep, previous.length()));
                }
            }
            curve.addAll(Arrays.copyOf(x, count), Arrays.copyOf(y, count));
        }
    }

    public void clear() {
        for (Curve curve : curves) {
            curve.clearData();
        }
    }

    @Override
    public void prepareForExit() {
        canvas.shutDown();
        super.prepareForExit();
    }

    private record Series(String name, Color color, ToDoubleFunction<ChaosSnapshot> value) {
    }
}
