package com.unionbiometrics.vision.internal;

/** Builds internal results for public facade tests without widening the AAR API. */
public final class ResultFixture {
    private ResultFixture() {}

    public static SessionResult decision(float[] probabilities) {
        return SessionResult.decision(new ProbabilityResult(probabilities), 4L);
    }
}
