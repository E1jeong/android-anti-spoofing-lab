package com.virditech.ac7000.model;

public final class SlotClassificationResult {
    public final ClassificationResult result;
    public final ClassificationResult rgbResult;
    public final ClassificationResult irResult;
    public final long preprocessMs;
    public final long inferenceMs;

    SlotClassificationResult(ClassificationResult result, ClassificationResult rgbResult,
                             ClassificationResult irResult) {
        this.result = result;
        this.rgbResult = rgbResult;
        this.irResult = irResult;
        long preprocessTotal = 0L;
        long inferenceTotal = 0L;
        if (result != null) {
            preprocessTotal += result.preprocessMs;
            inferenceTotal += result.inferenceMs;
        }
        if (rgbResult != null) {
            preprocessTotal += rgbResult.preprocessMs;
            inferenceTotal += rgbResult.inferenceMs;
        }
        if (irResult != null) {
            preprocessTotal += irResult.preprocessMs;
            inferenceTotal += irResult.inferenceMs;
        }
        preprocessMs = preprocessTotal;
        inferenceMs = inferenceTotal;
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
