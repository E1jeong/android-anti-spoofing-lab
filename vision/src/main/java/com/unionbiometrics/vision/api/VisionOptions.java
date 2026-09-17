package com.unionbiometrics.vision.api;

public final class VisionOptions {
    public static final long DEFAULT_IR_SETTLE_MS = 400L;
    public static final int DEFAULT_SAMPLE_COUNT = 3;
    public static final long DEFAULT_MAX_PAIR_DELTA_NS = 150_000_000L;

    private final long irSettleMs;
    private final int sampleCount;
    private final long maxPairDeltaNs;

    public VisionOptions(long irSettleMs, int sampleCount, long maxPairDeltaNs) {
        if (irSettleMs < 0L) throw new IllegalArgumentException("irSettleMs must be >= 0");
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        if (maxPairDeltaNs < 0L) throw new IllegalArgumentException("maxPairDeltaNs must be >= 0");
        this.irSettleMs = irSettleMs;
        this.sampleCount = sampleCount;
        this.maxPairDeltaNs = maxPairDeltaNs;
    }

    public static VisionOptions defaults() {
        return new VisionOptions(DEFAULT_IR_SETTLE_MS, DEFAULT_SAMPLE_COUNT, DEFAULT_MAX_PAIR_DELTA_NS);
    }

    public long irSettleMs() {
        return irSettleMs;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public long maxPairDeltaNs() {
        return maxPairDeltaNs;
    }
}
