package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

/** Raw single-frame result used by diagnostic hosts such as the lab app. */
public final class InferenceResult {
    private final ProbabilityResult result;
    private final ProbabilityResult rgbResult;
    private final ProbabilityResult irResult;
    private final long preprocessMs;
    private final long inferenceMs;
    private final String errorMessage;

    private InferenceResult(ProbabilityResult result, ProbabilityResult rgbResult,
                                  ProbabilityResult irResult, long preprocessMs,
                                  long inferenceMs, String errorMessage) {
        this.result = result;
        this.rgbResult = rgbResult;
        this.irResult = irResult;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static InferenceResult success(ProbabilityResult result,
                                                ProbabilityResult rgbResult,
                                                ProbabilityResult irResult,
                                                long preprocessMs, long inferenceMs) {
        boolean single = result != null && rgbResult == null && irResult == null;
        boolean paired = result == null && rgbResult != null && irResult != null;
        if (!single && !paired) {
            throw new IllegalArgumentException("Success requires one result or a complete RGB/IR pair");
        }
        return new InferenceResult(result, rgbResult, irResult,
                preprocessMs, inferenceMs, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static InferenceResult error(String message) {
        return new InferenceResult(null, null, null, 0L, 0L,
                message == null ? "Unknown Vision inference error" : message);
    }

    public boolean successful() {
        return errorMessage == null;
    }

    public String errorMessage() {
        return errorMessage;
    }

    public ProbabilityResult result() {
        return result;
    }

    public ProbabilityResult rgbResult() {
        return rgbResult;
    }

    public ProbabilityResult irResult() {
        return irResult;
    }

    public ProbabilityResult primaryResult() {
        if (result != null) return result;
        if (rgbResult != null) return rgbResult;
        return irResult;
    }

    public boolean hasPairedResults() {
        return rgbResult != null || irResult != null;
    }

    public long preprocessMs() {
        return preprocessMs;
    }

    public long inferenceMs() {
        return inferenceMs;
    }
}
