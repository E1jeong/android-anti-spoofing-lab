package com.unionbiometrics.vision.api;

public final class AntiSpoofingOptions {
    public static final int DEFAULT_IR_SETTLE_FRAME_COUNT = 10;
    public static final int DEFAULT_SAMPLE_COUNT = 3;
    private final int irSettleFrameCount;
    private final int sampleCount;

    public AntiSpoofingOptions(int irSettleFrameCount, int sampleCount) {
        if (irSettleFrameCount < 0) {
            throw new IllegalArgumentException("irSettleFrameCount must be >= 0");
        }
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
        this.irSettleFrameCount = irSettleFrameCount;
        this.sampleCount = sampleCount;
    }

    public int irSettleFrameCount() {
        return irSettleFrameCount;
    }

    public int sampleCount() {
        return sampleCount;
    }

    public static AntiSpoofingOptions defaults() {
        return new AntiSpoofingOptions(DEFAULT_IR_SETTLE_FRAME_COUNT, DEFAULT_SAMPLE_COUNT);
    }
}
