package com.unionbiometrics.vision;

/** Internal raw single-frame result converted by the public engine. */
final class FrameResult {
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

    static FrameResult success(ProbabilityResult result,
                                          long preprocessMs, long inferenceMs) {
        if (result == null) throw new IllegalArgumentException("Success requires a result");
        return new FrameResult(result, preprocessMs, inferenceMs, null);
    }

    static FrameResult error(String message) {
        return new FrameResult(null, 0L, 0L,
                message == null ? "Unknown Vision inference error" : message);
    }

    boolean successful() {
        return errorMessage == null;
    }

    String errorMessage() {
        return errorMessage;
    }

    ProbabilityResult result() {
        return result;
    }

    long preprocessMs() {
        return preprocessMs;
    }

    long inferenceMs() {
        return inferenceMs;
    }
}
