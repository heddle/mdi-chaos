package edu.cnu.mdi.chaos.io;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

import edu.cnu.mdi.chaos.model.ChaosSnapshot;
import edu.cnu.mdi.chaos.model.DivergenceFit;
import edu.cnu.mdi.chaos.model.LorenzParameters;
import edu.cnu.mdi.chaos.model.LyapunovEstimate;

/** Writes a rectangular CSV with one row per retained diagnostic sample. */
public final class DiagnosticsCsv {
    private DiagnosticsCsv() {
    }

    public record FitSelection(double start, double end, DivergenceFit estimate) {
    }

    public static void write(Writer output, List<ChaosSnapshot> samples, LorenzParameters parameters,
                             FitSelection fit, LyapunovEstimate lyapunov) throws IOException {
        output.write("step,time,separation,log_divergence,sigma,rho,beta,dt,delta0,"
                + "fit_start,fit_end,fit_slope,fit_sample_count,"
                + "lyapunov_status,lyapunov_exponent,lyapunov_averaging_time,"
                + "lyapunov_interval,lyapunov_warmup_time,lyapunov_measured_intervals\r\n");
        String settings = parameters.sigma() + "," + parameters.rho() + "," + parameters.beta()
                + "," + parameters.dt() + "," + parameters.delta0();
        String fitColumns = fit == null ? ",,," : fit.start() + "," + fit.end()
                + "," + fit.estimate().slope() + "," + fit.estimate().sampleCount();
        String metadata = "," + settings + "," + fitColumns + "," + lyapunovColumns(lyapunov) + "\r\n";
        for (ChaosSnapshot sample : samples) {
            output.write(sample.step() + "," + sample.time() + "," + finite(sample.separation())
                    + "," + finite(sample.logDivergence()) + metadata);
        }
    }

    private static String lyapunovColumns(LyapunovEstimate estimate) {
        if (estimate == null) {
            return "disabled,,,,,";
        }
        String status = estimate.failed() ? "failed" : estimate.measuredIntervals() == 0 ? "warming_up" : "estimated";
        String exponent = estimate.failed() ? "" : finite(estimate.exponent());
        return status + "," + exponent + "," + estimate.averagingTime() + "," + estimate.interval()
                + "," + estimate.warmupTime() + "," + estimate.measuredIntervals();
    }

    private static String finite(double value) {
        return Double.isFinite(value) ? Double.toString(value) : "";
    }
}
