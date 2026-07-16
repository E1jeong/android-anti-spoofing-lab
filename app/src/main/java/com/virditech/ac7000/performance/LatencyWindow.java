package com.virditech.ac7000.performance;

import java.util.Arrays;

public final class LatencyWindow {
    private final long[] values;
    private int size;
    private int nextIndex;
    private long totalCount;

    public LatencyWindow(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        values = new long[capacity];
    }

    public synchronized long add(long valueMs) {
        values[nextIndex] = Math.max(0L, valueMs);
        nextIndex = (nextIndex + 1) % values.length;
        if (size < values.length) size++;
        totalCount++;
        return totalCount;
    }

    public synchronized Snapshot snapshot() {
        return snapshotLocked();
    }

    private Snapshot snapshotLocked() {
        if (size == 0) return new Snapshot(0L, 0L, 0L);
        long[] sorted = Arrays.copyOf(values, size);
        Arrays.sort(sorted);
        return new Snapshot(totalCount, percentile(sorted, 50), percentile(sorted, 95));
    }

    private static long percentile(long[] sorted, int percentile) {
        int index = (int) Math.ceil(percentile / 100.0 * sorted.length) - 1;
        return sorted[Math.max(0, index)];
    }

    public static final class Snapshot {
        public final long count;
        public final long p50Ms;
        public final long p95Ms;

        Snapshot(long count, long p50Ms, long p95Ms) {
            this.count = count;
            this.p50Ms = p50Ms;
            this.p95Ms = p95Ms;
        }

        public boolean hasSamples() {
            return count > 0L;
        }
    }
}
