package com.example.simulation;

public class ContinuousRunResult {
    private final int[] infectedTimeSeries;
    private final double dt;
    private final SISEvent[] events;

    public ContinuousRunResult(int[] infectedTimeSeries, double dt, SISEvent[] events) {
        this.infectedTimeSeries = infectedTimeSeries;
        this.dt = dt;
        this.events = events;
    }

    public int[] infectedTimeSeries() { return infectedTimeSeries; }
    public double dt() { return dt; }
    public SISEvent[] events() { return events; }
}
