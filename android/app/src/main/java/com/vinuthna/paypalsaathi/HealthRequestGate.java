package com.vinuthna.paypalsaathi;

final class HealthRequestGate {
    private long generation;
    private boolean closed;

    long beginRequest() {
        return closed ? -1L : ++generation;
    }

    boolean isCurrent(long requestId) {
        return !closed && requestId == generation;
    }

    void invalidate() {
        generation++;
    }

    void close() {
        closed = true;
        generation++;
    }
}
