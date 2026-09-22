package com.unionbiometrics.vision.internal.inference;

import com.unionbiometrics.vision.api.ProbabilityResult;

/** Internal raw single-frame result used by the Lab app and product session pipeline. */
public final class FrameResult {
    private final ProbabilityResult result;
    private final long preprocessMs;
    private final long inferenceMs;
    private final String errorMessage;

    private FrameResult(ProbabilityResult result, long preprocessMs,
                            long inferenceMs, String errorMessage) {
        this.result = result;
        this.preprocessMs = preprocessMs;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
    }

    public static FrameResult success(ProbabilityResult result,
                                          long preprocessMs, long inferenceMs) {
        if (result == null) throw new IllegalArgumentException("Success requires a result");
        return new FrameResult(result, preprocessMs, inferenceMs, null);
    }

    public static FrameResult error(String message) {
        return new FrameResult(null, 0L, 0L,
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
