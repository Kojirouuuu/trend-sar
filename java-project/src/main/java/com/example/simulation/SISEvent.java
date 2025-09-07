package com.example.simulation;

public final class SISEvent {
    public final double time;
    public final SISEventType type;
    public final int node;

    public SISEvent(double time, SISEventType type, int node) {
        this.time = time;
        this.type = type;
        this.node = node;
    }

    public double time() { return time; }
    public SISEventType type() { return type; }
    public int node() { return node; }
}
