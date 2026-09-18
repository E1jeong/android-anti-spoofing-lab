package com.unionbiometrics.vision.api;

import androidx.annotation.RestrictTo;

/** Raw single-frame result used by diagnostic hosts such as the lab app. */
public final class InferenceResult {
    private final ProbabilityResult result;
    private final long preprocessMs;
    private final long inferenceMs;
    private final String errorMessage;

    private InferenceResult(ProbabilityResult result, long preprocessMs,
                            long inferenceMs, String errorMessage) {
        this.result = result;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static InferenceResult success(ProbabilityResult result,
                                          long preprocessMs, long inferenceMs) {
        if (result == null) throw new IllegalArgumentException("Success requires a result");
        return new InferenceResult(result, preprocessMs, inferenceMs, null);
    }

    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public static InferenceResult error(String message) {
        return new InferenceResult(null, 0L, 0L,
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

    public long preprocessMs() {
        return preprocessMs;
    }

    public long inferenceMs() {
        return inferenceMs;
    }
}
