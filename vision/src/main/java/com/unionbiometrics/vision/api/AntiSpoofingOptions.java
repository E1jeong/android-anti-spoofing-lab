package com.unionbiometrics.vision.api;

public record AntiSpoofingOptions(int irSettleFrameCount, int sampleCount) {
    public static final int DEFAULT_IR_SETTLE_FRAME_COUNT = 10;
    public static final int DEFAULT_SAMPLE_COUNT = 3;

    public AntiSpoofingOptions {
        if (irSettleFrameCount < 0) {
            throw new IllegalArgumentException("irSettleFrameCount must be >= 0");
        }
        if (sampleCount <= 0) throw new IllegalArgumentException("sampleCount must be > 0");
    }

    public static AntiSpoofingOptions defaults() {
        return new AntiSpoofingOptions(DEFAULT_IR_SETTLE_FRAME_COUNT, DEFAULT_SAMPLE_COUNT);
    }
}
