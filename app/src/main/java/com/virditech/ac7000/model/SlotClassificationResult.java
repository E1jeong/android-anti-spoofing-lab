package com.virditech.ac7000.model;

public final class SlotClassificationResult {
    public final ClassificationResult result;
    public final ClassificationResult rgbResult;
    public final ClassificationResult irResult;
    public final long inferenceMs;

    SlotClassificationResult(ClassificationResult result, ClassificationResult rgbResult,
                             ClassificationResult irResult) {
        this.result = result;
        this.rgbResult = rgbResult;
        this.irResult = irResult;
        long total = 0L;
        if (result != null) total += result.inferenceMs;
        if (rgbResult != null) total += rgbResult.inferenceMs;
        if (irResult != null) total += irResult.inferenceMs;
        inferenceMs = total;
    }

    public ClassificationResult primaryResult() {
        if (result != null) return result;
        if (rgbResult != null) return rgbResult;
        return irResult;
    }

    public boolean hasPairedResults() {
        return rgbResult != null || irResult != null;
    }
}
