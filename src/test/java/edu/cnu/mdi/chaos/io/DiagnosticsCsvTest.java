package edu.cnu.mdi.chaos.io;

import java.io.StringWriter;
import java.util.List;

import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.DivergenceFit;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.chaos.model.LorenzState;
import edu.cnu.mdi.chaos.model.LyapunovEstimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DiagnosticsCsvTest {
    @Test
    void exportsSelectedFitBoundsAndRunMetadataWithEachSample() throws Exception {
        var output = new StringWriter();
        var state = new LorenzState(1, 1, 1);
        var samples = List.of(
                new ChaosSnapshot(8, 0.04, state, state, 1e-7, 2.3),
                new ChaosSnapshot(12, 0.06, state, state, 2e-7, 3.0));
        var fit = new DiagnosticsCsv.FitSelection(15.001, 30.009,
                new DivergenceFit(15.02, 30, 22, 5, 0.98328, 750));
        var lyapunov = new LyapunovEstimate(0.8312, 40, 0.5, 10, 80, false);
        DiagnosticsCsv.write(output, samples, LorenzParameters.DEFAULT, fit, lyapunov);

        var lines = output.toString().lines().toList();
        assertEquals(3, lines.size());
        var first = lines.get(1).split(",", -1);
        var second = lines.get(2).split(",", -1);
        assertEquals(19, lines.get(0).split(",").length);
        assertEquals(19, first.length);
        assertEquals(19, second.length);
        assertEquals("8,0.04,1.0E-7,2.3", String.join(",", List.of(first).subList(0, 4)));
        assertEquals("12,0.06,2.0E-7,3.0", String.join(",", List.of(second).subList(0, 4)));
        assertEquals("10.0,28.0,2.6666666666666665,0.005,1.0E-8,15.001,30.009,0.98328,750,"
                + "estimated,0.8312,40.0,0.5,10.0,80", String.join(",", List.of(first).subList(4, 19)));
        assertEquals(List.of(first).subList(4, 19), List.of(second).subList(4, 19));
    }

    @Test
    void keepsUndefinedSamplesAndEmptiesUnavailableValues() throws Exception {
        var state = new LorenzState(1, 1, 1);
        var samples = List.of(new ChaosSnapshot(0, 0, state, state, 0, Double.NaN));
        var output = new StringWriter();
        DiagnosticsCsv.write(output, samples, LorenzParameters.DEFAULT, null, null);
        String[] row = output.toString().lines().toList().get(1).split(",", -1);
        assertEquals(19, row.length);
        assertEquals("", row[3]);
        assertEquals("", row[9]);
        assertEquals("disabled", row[13]);
        assertEquals("", row[14]);

        output = new StringWriter();
        var failed = new LyapunovEstimate(0.5, 20, 0.5, 10, 40, true);
        DiagnosticsCsv.write(output, samples, LorenzParameters.DEFAULT, null, failed);
        row = output.toString().lines().toList().get(1).split(",", -1);
        assertEquals("failed", row[13]);
        assertEquals("", row[14]);
        assertEquals("20.0", row[15]);
    }
}
