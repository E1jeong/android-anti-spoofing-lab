package com.unionbiometrics.vision;

final class SessionResult {
    enum Status {
        PENDING,
        LIVE,
        SPOOF,
        ERROR
    }

    private final Status status;
    private final ProbabilityResult result;
    private final Long inferenceMs;
    private final String errorMessage;

    private SessionResult(Status status, ProbabilityResult result, Long inferenceMs, String errorMessage) {
        this.status = status;
        this.result = result;
        this.inferenceMs = inferenceMs;
        this.errorMessage = errorMessage;
    }

    static SessionResult pending(ProbabilityResult result) {
        return pending(result, null);
    }

    static SessionResult pending(ProbabilityResult result, Long inferenceMs) {
        return new SessionResult(Status.PENDING, result, inferenceMs, null);
    }

    static SessionResult decision(ProbabilityResult result, long inferenceMs) {
        return new SessionResult(result.isAccepted() ? Status.LIVE : Status.SPOOF, result, inferenceMs, null);
    }

    static SessionResult error(String message) {
        return new SessionResult(Status.ERROR, null, null, message == null ? "Unknown Vision error" : message);
    }

    Status status() {
        return status;
    }

    /**
     * Returns the running average after an accepted sample, or null before the first sample and on error.
     */
    ProbabilityResult result() {
        return result;
    }

    /**
     * Returns the TFLite invocation time for the current accepted sample, or null when no inference ran.
     */
    Long inferenceMs() {
        return inferenceMs;
    }

    String errorMessage() {
        return errorMessage;
    }
}
