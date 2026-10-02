package com.unionbiometrics.vision.internal;

/** Builds internal results for public facade tests without widening the AAR API. */
public final class ResultFixture {
    private ResultFixture() {}

    public static SessionResult decision(float[] probabilities) {
        return SessionResult.decision(new ProbabilityResult(probabilities), 4L);
    }

    public static FrameResult frame(float[] probabilities) {
        return FrameResult.success(new ProbabilityResult(probabilities), 2L, 4L);
    }

    public static FrameResult frameError(String message) {
        return FrameResult.error(message);
    }
}
