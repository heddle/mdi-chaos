package edu.cnu.mdi.chaos.model;

/** Ordinary least-squares line through finite divergence samples in a selected time interval. */
public record DivergenceFit(double firstTime, double lastTime, double meanTime,
                            double meanDivergence, double slope, int sampleCount) {

    public static DivergenceFit estimate(double[] times, double[] divergence, double start, double end) {
        if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || start >= end) {
            throw new IllegalArgumentException("Enter finite times with 0 ≤ start < end.");
        }
        int count = 0;
        double first = Double.POSITIVE_INFINITY;
        double last = Double.NEGATIVE_INFINITY;
        double meanTime = 0;
        double meanValue = 0;
        double timeVariance = 0;
        double covariance = 0;
        for (int i = 0; i < times.length; i++) {
            double time = times[i];
            double value = divergence[i];
            if (!(time >= start && time <= end) || !Double.isFinite(value)) {
                continue;
            }
            count++;
            first = Math.min(first, time);
            last = Math.max(last, time);
            double timeDelta = time - meanTime;
            double valueDelta = value - meanValue;
            meanTime += timeDelta / count;
            meanValue += valueDelta / count;
            timeVariance += timeDelta * (time - meanTime);
            covariance += timeDelta * (value - meanValue);
        }
        if (count < 3 || !(timeVariance > 0)) {
            throw new IllegalArgumentException("Select an interval with at least three finite samples at distinct times.");
        }
        double slope = covariance / timeVariance;
        if (!Double.isFinite(slope)) {
            throw new IllegalArgumentException("The slope is undefined for this interval.");
        }
        return new DivergenceFit(first, last, meanTime, meanValue, slope, count);
    }

    public double valueAt(double time) {
        return meanDivergence + slope * (time - meanTime);
    }
}
