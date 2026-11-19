package edu.cnu.mdi.chaos.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import java.util.function.ToDoubleFunction;

import javax.swing.BorderFactory;
import javax.swing.JLabel;

import edu.cnu.mdi.chaos.model.ChaosSnapshot;
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
    private final JLabel note;

    public static ChaosPlotView timeSeries() {
        return new ChaosPlotView("Time Series", "Primary trajectory", "Coordinate", "",
                new Series("x", new Color(26, 106, 157), sample -> sample.primary().x()),
                new Series("y", new Color(178, 73, 33), sample -> sample.primary().y()),
                new Series("z", new Color(40, 121, 88), sample -> sample.primary().z()));
    }

    public static ChaosPlotView diagnostics() {
        return new ChaosPlotView("Chaos Diagnostics", "Illustrative finite-time divergence", "ln(δ / δ₀)",
                "<html>Early growth can be approximately exponential.<br>"
                        + "Separation eventually saturates at the scale of the attractor.</html>",
                new Series("Divergence", new Color(121, 58, 147), ChaosSnapshot::logDivergence));
    }

    private ChaosPlotView(String title, String plotTitle, String yLabel, String explanation, Series... series) {
        super(PropertyUtils.TITLE, title, PropertyUtils.WIDTH, 510, PropertyUtils.HEIGHT, 300,
                PropertyUtils.USECONTAINER, false, PropertyUtils.VISIBLE, true);
        this.series = List.of(series);
        curves = new Curve[series.length];
        try {
            String[] names = this.series.stream().map(Series::name).toArray(String[]::new);
            var data = new PlotData(PlotDataType.XYEXYE, names, null);
            for (int i = 0; i < series.length; i++) {
                Curve curve = (Curve) data.getCurve(series[i].name());
                curve.setCurveDrawingMethod(CurveDrawingMethod.CONNECT);
                curve.getStyle().setSymbolType(SymbolType.NOSYMBOL);
                curve.getStyle().setLineColor(series[i].color());
                curves[i] = curve;
            }
            canvas = new PlotCanvas(data, plotTitle, "Simulation time", yLabel);
        } catch (PlotDataException error) {
            throw new IllegalStateException("Could not initialize sPlot", error);
        }
        canvas.getParameters().setLegendDrawing(series.length > 1).setNumDecimalY(1);
        canvas.getPlotTicks().setNumMajorTickY(4);
        add(new PlotPanel(canvas, PlotPanel.BARE), BorderLayout.CENTER);
        note = new JLabel(explanation);
        if (!explanation.isEmpty()) {
            note.setBorder(BorderFactory.createEmptyBorder(4, 12, 7, 8));
            add(note, BorderLayout.SOUTH);
        }
        canvas.standUp();
    }

    public void showUndefinedDiagnostic(boolean zeroPerturbation) {
        note.setText(zeroPerturbation ? "Log divergence is undefined when δ₀ is zero."
                : "<html>Early growth can be approximately exponential.<br>"
                        + "Separation eventually saturates at the scale of the attractor.</html>");
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
