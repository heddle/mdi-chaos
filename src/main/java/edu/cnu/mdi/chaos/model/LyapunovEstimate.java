package edu.cnu.mdi.chaos.model;

/** Immutable status of the running largest Lyapunov exponent estimate. */
public record LyapunovEstimate(double exponent, double averagingTime, double interval,
                               double warmupTime, long measuredIntervals, boolean failed) {
}
